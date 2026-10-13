#!/usr/bin/env python3
"""Captures the App Store screenshots of one platform in every store language (macOS CI runner).

    shoot.py <platform> <path to the built .app> <output folder> [language codes...]

The screens, their order and the launch arguments come from tools/store/screens.json. Simulators (iPhone,
iPad, Apple Watch, Apple TV, Apple Vision Pro) are driven with simctl; the Mac app is opened and its window captured.
Files land in <output>/<platform>/<app language>/<NN>-<screen>.png; finish.py checks sizes and writes the
manifest that says where each file goes in App Store Connect.
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
# ONLY="ar/01-prayer en/05-audio" captures just those (to redo a few); empty captures everything.
ONLY = set(os.environ.get("ONLY", "").split())


def wanted(lang, n, screen):
    return not ONLY or f"{lang['app']}/{n:02d}-{screen}" in ONLY


def wait(platform, screen):
    # EXTRA_WAIT adds seconds to every capture (to tell a slow screen from one that never appears).
    return WAIT.get(screen, 11) + EXTRA.get(platform, 0) + EXTRA_SCREEN.get((platform, screen), 0) + int(os.environ.get("EXTRA_WAIT") or 0)


def run(*cmd, check=False):
    return subprocess.run(cmd, capture_output=True, text=True, check=check)


def simctl(*args, check=False):
    return run("xcrun", "simctl", *args, check=check)


def find_simulator(pattern, family):
    """Newest-runtime simulator whose name matches `pattern`, the newest model first (created when none exists)."""
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
    udid = find_simulator(conf["simulator"], family)
    print(f"{platform}: simulator {udid}", flush=True)
    simctl("boot", udid)
    simctl("bootstatus", udid, "-b", check=True)
    simctl("install", udid, app, check=True)
    simctl("privacy", udid, "grant", "location", bundle)
    if platform in ("iphone", "ipad"):
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
            if platform in ("iphone", "ipad") and want != look:
                simctl("ui", udid, "appearance", want)
                look = want
            if spec.get("city"):
                lat, lon = SPEC["cities"][spec["city"]]
                simctl("location", udid, "set", f"{lat},{lon}")
            simctl("terminate", udid, bundle)
            r = simctl("launch", udid, bundle, *launch_args(screen, lang))
            if r.returncode:
                print(f"::warning::{platform}/{lang['app']}/{screen}: launch failed", flush=True)
                continue
            time.sleep(wait(platform, screen))
            target = folder / f"{n:02d}-{screen}.png"
            simctl("io", udid, "screenshot", str(target))
            print(f"{platform}/{lang['app']}/{target.name}", flush=True)
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
    script = out / "window.swift"
    out.mkdir(parents=True, exist_ok=True)
    script.write_text(MAC_WINDOW)
    conf = SPEC["platforms"]["mac"]
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
            # The app's window alone (with its shadow); finish.py places it on a store-sized canvas.
            if wid:
                run("screencapture", "-x", f"-l{wid}", str(target))
            else:
                print(f"::warning::mac/{lang['app']}/{screen}: no window found, captured the screen", flush=True)
                run("screencapture", "-x", str(target))
            print(f"mac/{lang['app']}/{target.name}", flush=True)
    run("pkill", "-x", "NearMosque")
    script.unlink()


def main():
    platform, app, out = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3])
    wanted = set(sys.argv[4:])
    langs = [l for l in SPEC["languages"] if not wanted or l["app"] in wanted]
    if platform == "mac":
        shoot_mac(app, out, langs)
    else:
        shoot_simulator(platform, app, out, langs)


if __name__ == "__main__":
    main()
