#!/usr/bin/env python3
"""Captures the App Store screenshots of one platform in every store language (macOS CI runner).

    shoot.py <platform> <path to the built .app> <output folder> [language codes...]
    shoot.py --mark <platform> <output folder> [language codes...]     only the markers (below), nothing captured

The screens, their order and the launch arguments come from tools/store/screens.json. Simulators (iPhone,
iPad, Apple Watch, Apple TV, Apple Vision Pro) are driven with simctl; the Mac app is opened and its window captured.
Files land in <output>/<platform>/<app language>/<NN>-<screen>.png; finish.py checks sizes and writes the
manifest that says where each file goes in App Store Connect.

A simulator screenshot that fails or hangs is tried again (SHOT_ATTEMPTS in all, the app launched afresh before
the last one). A screen this run meant to capture and has no capture of ends as <NN>-<screen>.failed next to where
the capture would be (one line: why): every wanted screen gets that marker before the simulator starts or the Mac
app opens (and in CI before the build, with --mark), and a capture replaces it, so a capture that failed, a launch
that failed, a build that failed or a run stopped part way all leave one. A capture is written under a partial
name first and renamed once complete, so a stopped capture never leaves a <NN>-<screen>.png. collect.py then drops
any older capture of that screen from earlier runs, so finish.py reports it as missing instead of using a stale one.
"""
import json
import os
import pathlib
import re
import subprocess
import sys
import time

BUNDLE = "sa.zood.nearmosque"
# The Apple Watch app has its own bundle id (it ships inside the iPhone app).
BUNDLES = {"watch": BUNDLE + ".watchkitapp"}
HERE = pathlib.Path(__file__).resolve().parent
SPEC = json.loads((HERE / "screens.json").read_text())

# Seconds to wait after launch, per screen (maps and answers take longer to settle).
WAIT = {"mosques-map": 16, "mosques-radar": 14, "ask": 18, "classics": 12, "tv-mosques": 14, "watch-mosques": 16, "watch-ask": 14}
# The Apple Vision Pro simulator renders a whole room and opens windows slowly: extra seconds, more for sheets.
EXTRA = {"vision": 10}
EXTRA_SCREEN = {("vision", "audio"): 14}
# ONLY="ar/01-prayer en/02-prayer-weather" captures just those (to redo a few); empty captures everything.
ONLY = set(os.environ.get("ONLY", "").split())
# `simctl io screenshot` of an animated screen (the onboarding promise, the weather sky) sometimes hangs on the
# iPad and Apple Vision Pro simulators: up to SHOT_ATTEMPTS tries of SHOT_TIMEOUT seconds each, a pause before each
# retry, and the app launched afresh (and waited for again) before the last one. A screenshot takes a second or two;
# one that has not come in 60 seconds has hung, and a fresh try is what helps, not a longer wait.
SHOT_ATTEMPTS = 3
SHOT_TIMEOUT = 60
SHOT_PAUSE = 8
NOT_REACHED = "not captured: the run stopped before this screen"


def wanted(lang, n, screen):
    return not ONLY or f"{lang['app']}/{n:02d}-{screen}" in ONLY


def wait(platform, screen):
    # EXTRA_WAIT adds seconds to every capture (to tell a slow screen from one that never appears).
    return WAIT.get(screen, 11) + EXTRA.get(platform, 0) + EXTRA_SCREEN.get((platform, screen), 0) + int(os.environ.get("EXTRA_WAIT") or 0)


def run(*cmd, check=False, timeout=600):
    # A simulator command that hangs (seen with the iPhone Duo simulator) fails after `timeout` seconds.
    try:
        return subprocess.run(cmd, capture_output=True, text=True, check=check, timeout=timeout)
    except subprocess.TimeoutExpired:
        if check:
            raise
        return subprocess.CompletedProcess(cmd, 124, "", "timed out")


def simctl(*args, check=False, timeout=60):
    return run("xcrun", "simctl", *args, check=check, timeout=timeout)


def find_simulator(pattern, family, runtime=None):
    """Newest-runtime simulator whose name matches `pattern`, the newest model first (created when none exists).
    With `runtime` (e.g. "27.1"), a device of exactly that runtime version, created when there is none."""
    if runtime:
        rts = json.loads(simctl("list", "runtimes", "available", "-j", check=True).stdout)["runtimes"]
        rt = next(r for r in rts if any(f in r["identifier"] for f in family) and r["version"].startswith(runtime))
        types = json.loads(simctl("list", "devicetypes", "-j", check=True).stdout)["devicetypes"]
        kind = next(t for t in types if re.search(pattern, t["name"]))
        return simctl("create", kind["name"], kind["identifier"], rt["identifier"], check=True).stdout.strip()
    devices = json.loads(simctl("list", "devices", "available", "-j", check=True).stdout)["devices"]

    def version(rt):
        tail = rt.rsplit(".", 1)[-1].split("-", 1)[-1]
        return [int(x) for x in tail.split("-") if x.isdigit()]

    runtimes = sorted([rt for rt in devices if any(f in rt for f in family)], key=version)
    for rt in reversed(runtimes):
        for d in sorted(devices[rt], key=lambda d: d["name"], reverse=True):
            if re.search(pattern, d["name"]):
                return d["udid"]
    # No device of that kind yet (e.g. a freshly downloaded visionOS runtime): create one.
    types = json.loads(simctl("list", "devicetypes", "-j", check=True).stdout)["devicetypes"]
    kind = next(t for t in reversed(types) if re.search(pattern, t["name"]))
    name, kind = kind["name"], kind["identifier"]
    rts = json.loads(simctl("list", "runtimes", "available", "-j", check=True).stdout)["runtimes"]
    runtime = [r for r in rts if any(f in r["identifier"] for f in family)][-1]["identifier"]
    return simctl("create", name, kind, runtime, check=True).stdout.strip()


def marker(folder, n, screen):
    """Where a failed capture is recorded: `<NN>-<screen>.failed` next to where `<NN>-<screen>.png` would be."""
    return folder / f"{n:02d}-{screen}.failed"


def partial(target):
    """Where a capture is written first: `partial-<NN>-<screen>.png`, a name collect.py and finish.py never take for
    a capture. It becomes `target` only once it is complete, so a capture cut off by a stopped run leaves no
    `<NN>-<screen>.png` next to the screen's marker."""
    return target.with_name("partial-" + target.name)


def mark_failed(folder, n, screen, reason):
    marker(folder, n, screen).write_text(reason.replace("\n", " ").strip() + "\n")


def mark_wanted(out, platform, order, langs, reason=NOT_REACHED):
    """Marks every screen this run is to capture as not captured yet (each capture then replaces its marker), and
    removes any capture, partial capture or marker of it left in the folder by an earlier run, whatever its number."""
    for lang in langs:
        folder = out / platform / lang["app"]
        folder.mkdir(parents=True, exist_ok=True)
        for n, screen in enumerate(order, 1):
            if not wanted(lang, n, screen):
                continue
            for old in folder.glob(f"*-{screen}.*"):
                if re.fullmatch(r"(partial-)?\d{2}-" + re.escape(screen) + r"\.(png|failed)", old.name):
                    old.unlink()
            mark_failed(folder, n, screen, reason)


def screenshot(udid, display, target, relaunch, log):
    """`simctl io screenshot` into `target`, up to SHOT_ATTEMPTS times. Before a retry: a pause, and before the last
    one `relaunch()` (the app launched afresh and its screen waited for; it returns a reason when that fails).
    Returns (attempts made, None) when a capture was written, (attempts made, reason) when every attempt failed.
    simctl writes to partial(target), which becomes `target` only after it succeeded, so neither a failed attempt
    nor a run stopped mid-capture leaves a `target`. `log(note)` prints a line for each failed attempt that is tried
    again."""
    reason, part = "", partial(target)
    target.unlink(missing_ok=True)
    for attempt in range(1, SHOT_ATTEMPTS + 1):
        if attempt > 1:
            log(f" (attempt {attempt - 1} of {SHOT_ATTEMPTS} failed: {reason}; trying again)")
            time.sleep(SHOT_PAUSE)
            if attempt == SHOT_ATTEMPTS:
                problem = relaunch()
                if problem:
                    reason = problem
                    continue
        part.unlink(missing_ok=True)
        shot = simctl("io", udid, "screenshot", *display, str(part), timeout=SHOT_TIMEOUT)
        if shot.returncode == 0 and part.is_file() and part.stat().st_size > 0:
            os.replace(part, target)
            return attempt, None
        reason = (shot.stderr.strip().replace("\n", " ") or f"exit code {shot.returncode}")[:80] if shot.returncode else "no file written"
        part.unlink(missing_ok=True)
    return SHOT_ATTEMPTS, reason


def launch_args(screen, lang):
    spec = SPEC["screens"][screen]
    args = list(spec["args"])
    if "fallbackArgs" in spec and lang["app"] not in SPEC["askLanguages"]:
        args = list(spec["fallbackArgs"])
    if spec.get("city"):
        args += ["-demoCity", spec["city"]]
    return args + ["-languageOverride", lang["app"], "-AppleLanguages", f"({lang['system']})", "-AppleLocale", lang["system"]]


def shoot_simulator(platform, app, out, langs):
    conf = SPEC["platforms"][platform]
    family = {"tv": ["tvOS"], "vision": ["xrOS", "visionOS"], "watch": ["watchOS"]}.get(platform, ["iOS"])
    bundle = BUNDLES.get(platform, BUNDLE)
    # Before anything that can fail: a screen still marked when the run ends (or is stopped) is reported missing.
    mark_wanted(out, platform, conf["order"], langs)
    udid = find_simulator(conf["simulator"], family, conf.get("runtime"))
    print(f"{platform}: simulator {udid}", flush=True)
    simctl("boot", udid)
    simctl("bootstatus", udid, "-b", check=True, timeout=600)
    # iPhone Duo: the simulator starts folded (outer display 1); unfolded is the inner display (3) on, the outer off.
    display = [f"--display={conf['display']}"] if conf.get("display") else []
    if conf.get("unfold"):
        simctl("io", udid, "screenConfig", "--display=1", "power", "off")
        simctl("io", udid, "screenConfig", f"--display={conf['display']}", "power", "on")
        time.sleep(6)
    # A freshly booted simulator (the iPhone Duo especially) takes minutes to install the app.
    simctl("install", udid, app, check=True, timeout=600)
    simctl("privacy", udid, "grant", "location", bundle)
    if platform in ("iphone", "ipad") or platform.startswith("duo"):
        simctl("status_bar", udid, "override", "--time", "9:41", "--batteryLevel", "100", "--batteryState", "charged",
               "--cellularBars", "4", "--wifiBars", "3")
    look = None
    for lang in langs:
        folder = out / platform / lang["app"]
        folder.mkdir(parents=True, exist_ok=True)
        for n, screen in enumerate(conf["order"], 1):
            if not wanted(lang, n, screen):
                continue
            spec = SPEC["screens"][screen]
            want = spec.get("appearance", "light")
            # The iPhone Duo simulator sometimes misses a change of appearance: it is set before each of its captures.
            if (platform in ("iphone", "ipad") or platform.startswith("duo")) and (want != look or platform.startswith("duo")):
                simctl("ui", udid, "appearance", want)
                look = want
                time.sleep(1)
            if spec.get("city"):
                lat, lon = SPEC["cities"][spec["city"]]
                simctl("location", udid, "set", f"{lat},{lon}")
            simctl("terminate", udid, bundle)
            started = time.time()
            r = simctl("launch", udid, bundle, *launch_args(screen, lang), timeout=120)
            # The iPhone Duo simulator sometimes fails a launch: once more, after a pause.
            for _ in range(2 if r.returncode else 0):
                simctl("terminate", udid, bundle)
                time.sleep(8)
                started = time.time()
                r = simctl("launch", udid, bundle, *launch_args(screen, lang), timeout=120)
                if r.returncode == 0:
                    break
            if r.returncode:
                print(f"::warning::{platform}/{lang['app']}/{screen}: launch failed", flush=True)
                mark_failed(folder, n, screen, "launch failed")
                continue
            time.sleep(wait(platform, screen))
            target = folder / f"{n:02d}-{screen}.png"

            def log(note, target=target, started=started):
                print(f"{time.strftime('%H:%M:%S')} {platform}/{lang['app']}/{target.name} {time.time() - started:.0f}s{note}", flush=True)

            def relaunch(screen=screen, lang=lang):
                # A fresh launch and a fresh wait for the screen, for a simulator that hung on the last captures.
                simctl("terminate", udid, bundle)
                time.sleep(2)
                if simctl("launch", udid, bundle, *launch_args(screen, lang), timeout=120).returncode:
                    return "relaunch failed"
                time.sleep(wait(platform, screen))
                return None

            attempts, problem = screenshot(udid, display, target, relaunch, log)
            if problem:
                why = f"screenshot failed after {attempts} attempts: {problem}"
                mark_failed(folder, n, screen, why)
                log(f" ({why})")
                print(f"::warning::{platform}/{lang['app']}/{target.name}: {why}; marked {marker(folder, n, screen).name}", flush=True)
            else:
                marker(folder, n, screen).unlink(missing_ok=True)
                log(f" (attempt {attempts} of {SHOT_ATTEMPTS})" if attempts > 1 else "")
    simctl("terminate", udid, bundle)
    simctl("shutdown", udid)


MAC_WINDOW = r'''
import CoreGraphics
let all = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID) as? [[String: Any]] ?? []
// The app's process (its windows are owned by the display name "Near Mosque", so match the process ID).
let pid = Int(CommandLine.arguments.dropFirst().first ?? "") ?? -1
let mine = all.filter { ($0[kCGWindowOwnerPID as String] as? Int) == pid && ($0[kCGWindowLayer as String] as? Int) == 0 }
let big = mine.max { a, b in
    let ra = a[kCGWindowBounds as String] as? [String: Double] ?? [:], rb = b[kCGWindowBounds as String] as? [String: Double] ?? [:]
    return (ra["Width"] ?? 0) * (ra["Height"] ?? 0) < (rb["Width"] ?? 0) * (rb["Height"] ?? 0)
}
if let id = big?[kCGWindowNumber as String] as? Int { print(id) }
'''


def shoot_mac(app, out, langs):
    conf = SPEC["platforms"]["mac"]
    # As for the simulators: every screen to capture is marked first, and a capture replaces its marker.
    mark_wanted(out, "mac", conf["order"], langs)
    script = out / "window.swift"
    out.mkdir(parents=True, exist_ok=True)
    script.write_text(MAC_WINDOW)
    for lang in langs:
        folder = out / "mac" / lang["app"]
        folder.mkdir(parents=True, exist_ok=True)
        for n, screen in enumerate(conf["order"], 1):
            if not wanted(lang, n, screen):
                continue
            run("pkill", "-x", "NearMosque")
            time.sleep(2)
            run("open", "-n", app, "--args", *launch_args(screen, lang))
            time.sleep(wait("mac", screen) + 4)
            pid = run("pgrep", "-nx", "NearMosque").stdout.strip()
            wid = run("swift", str(script), pid).stdout.strip() if pid else ""
            target = folder / f"{n:02d}-{screen}.png"
            part = partial(target)
            part.unlink(missing_ok=True)
            # The app's window alone (with its shadow); finish.py places it on a store-sized canvas.
            if wid:
                shot = run("screencapture", "-x", f"-l{wid}", str(part), timeout=120)
            else:
                print(f"::warning::mac/{lang['app']}/{screen}: no window found, captured the screen", flush=True)
                shot = run("screencapture", "-x", str(part), timeout=120)
            if shot.returncode == 0 and part.is_file() and part.stat().st_size > 0:
                os.replace(part, target)
                marker(folder, n, screen).unlink(missing_ok=True)
                print(f"mac/{lang['app']}/{target.name}", flush=True)
            else:
                part.unlink(missing_ok=True)
                why = "screencapture failed: " + ((shot.stderr.strip().replace("\n", " ") or f"exit code {shot.returncode}")[:80]
                                                  if shot.returncode else "no file written")
                mark_failed(folder, n, screen, why)
                print(f"::warning::mac/{lang['app']}/{target.name}: {why}; marked {marker(folder, n, screen).name}", flush=True)
    run("pkill", "-x", "NearMosque")
    script.unlink()


def main():
    if sys.argv[1:2] == ["--mark"]:
        # shoot.py --mark <platform> <output folder> [languages...]: only the markers, as the shard's first step (CI),
        # so a shard whose build or setup fails still uploads them. MARK_REASON: the reason they give.
        platform, out, codes = sys.argv[2], pathlib.Path(sys.argv[3]), set(sys.argv[4:])
        langs = [l for l in SPEC["languages"] if not codes or l["app"] in codes]
        mark_wanted(out, platform, SPEC["platforms"][platform]["order"], langs, os.environ.get("MARK_REASON") or NOT_REACHED)
        print(f"{platform}: {len(list(out.glob(f'{platform}/*/*.failed')))} screen(s) marked as not captured yet", flush=True)
        return
    platform, app, out = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3])
    wanted = set(sys.argv[4:])
    langs = [l for l in SPEC["languages"] if not wanted or l["app"] in wanted]
    if platform == "mac":
        shoot_mac(app, out, langs)
    else:
        shoot_simulator(platform, app, out, langs)


if __name__ == "__main__":
    main()
