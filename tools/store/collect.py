#!/usr/bin/env python3
"""Gathers App Store screenshot captures from several runs into one raw folder for finish.py (no dependencies).

    collect.py <raw folder> <source> [<source>...]   later sources win
    collect.py --check                               checks screens.json and the merge below (CI)

A source is either
  * a raw folder as shoot.py writes it: <platform>/<app language>/<NN>-<screen>.png (a run's `raw-*`
    artifacts, kept 3 days), or
  * a finished set as finish.py writes it: manifest.json and <display type>/<locale>/<NN>-<screen>.png (a run's
    `app-store-screenshots` artifact, kept 30 days, or a store-screenshots release unzipped into one folder).
    Its files go back to the raw layout from the manifest (the first locale of each language only).

Captures are matched by screen name, never by number: <NN> is the position in the order of the run that made the
file, and the order in screens.json may have changed since. A capture replaces any capture of the same screen in
the same platform and language, whatever its number; finish.py then numbers them in the current order.

A failed capture counts too, so a stale capture is never used in silence. shoot.py leaves <NN>-<screen>.failed
(one line: why) for a screen it meant to capture and has no capture of (every attempt failed, the launch failed, or
the run stopped first); a finished set lists them under "failed" in its manifest. When a source has such a marker
for a platform and language, any capture of that screen already gathered into the raw folder (from an earlier
source) is removed and the marker is copied there instead, unless the same source also has a capture of that
screen (the capture then counts; an empty .png is never a capture). finish.py then reports the screen under
"missing", with the reason under "failed", and the upload tool has no file of it for that set. A later source
with a capture of the screen replaces the marker. To use an older capture after all, give the run that has it
after the failed one. A finished set also lists under "failed" an Apple Vision Pro capture it left out as still
loading, so a later merge from it does not bring back an older capture of that screen either.
"""
import json
import pathlib
import re
import shutil
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
NAME = re.compile(r"(\d{2})-(.+)\.png")
FAILED = re.compile(r"(\d{2})-(.+)\.failed")


def screen_of(name):
    """The screen of a capture's file name (`03-mosques-map.png` → `mosques-map`), or None."""
    m = NAME.fullmatch(name)
    return m.group(2) if m else None


def failed_screen_of(name):
    """The screen of a failed-capture marker's file name (`07-onboarding.failed` → `onboarding`), or None."""
    m = FAILED.fullmatch(name)
    return m.group(2) if m else None


def reason_of(marker):
    """The one-line reason a marker file gives (shoot.py writes it), short."""
    try:
        text = pathlib.Path(marker).read_text(errors="replace")
    except OSError:
        return ""
    return " ".join(text.split())[:200]


def find_failure(folder, screen):
    """The marker of a failed capture of `screen` in a raw <platform>/<language> folder, whatever its number,
    or None."""
    folder = pathlib.Path(folder)
    found = [p for p in folder.glob(f"*-{screen}.failed") if failed_screen_of(p.name) == screen] if folder.is_dir() else []
    return max(found, key=lambda p: p.stat().st_mtime_ns, default=None)


def find_capture(folder, n, screen):
    """The capture of `screen` in a raw <platform>/<language> folder, whatever its number: `<n>-<screen>.png` (the
    current position) if it is there, else the newest `<NN>-<screen>.png`; None when there is none."""
    folder = pathlib.Path(folder)
    found = [p for p in folder.glob(f"*-{screen}.png") if screen_of(p.name) == screen] if folder.is_dir() else []
    exact = folder / f"{n:02d}-{screen}.png"
    if exact in found:
        return exact
    return max(found, key=lambda p: p.stat().st_mtime_ns, default=None)


def captures(source):
    """(platform, app language, screen, file) for every capture in a raw folder or a finished set."""
    source = pathlib.Path(source)
    manifest = source / "manifest.json"
    if manifest.is_file():
        for e in json.loads(manifest.read_text())["screenshots"]:
            f = source / e["file"]
            if e.get("sameFileAs") is None and f.is_file():
                yield e["platform"], e["appLanguage"], e["screen"], f
        return
    for f in sorted(source.glob("*/*/*.png")):
        screen = screen_of(f.name)
        if screen:
            yield f.parent.parent.name, f.parent.name, screen, f


def failures(source):
    """(platform, app language, screen, marker file name, reason) for every failed capture a source records: the
    `<NN>-<screen>.failed` markers of a raw folder, or the "failed" list of a finished set's manifest."""
    source = pathlib.Path(source)
    manifest = source / "manifest.json"
    if manifest.is_file():
        for e in json.loads(manifest.read_text()).get("failed", []):
            yield (e["platform"], e["appLanguage"], e["screen"], f"{int(e.get('position') or 0):02d}-{e['screen']}.failed",
                   " ".join(str(e.get("reason") or "").split())[:200])
        return
    for f in sorted(source.glob("*/*/*.failed")):
        screen = failed_screen_of(f.name)
        if screen:
            yield f.parent.parent.name, f.parent.name, screen, f.name, reason_of(f)


def clear(folder, screen):
    """Removes the captures and failure markers of `screen` from a raw folder; returns the captures' names."""
    gone = []
    for old in sorted(folder.glob(f"*-{screen}.*")) if folder.is_dir() else []:
        if screen_of(old.name) == screen:
            gone.append(old.name)
            old.unlink()
        elif failed_screen_of(old.name) == screen:
            old.unlink()
    return gone


def collect(dest, source, log=print):
    """Copies the captures of `source` into the raw folder `dest`, each replacing that screen's earlier capture or
    failure marker. Then each failed capture of `source` (with no capture of that screen in `source` itself) removes
    that screen's earlier capture from `dest` and leaves its marker there, so finish.py reports it as missing."""
    dest, count = pathlib.Path(dest), 0
    found = set()
    for platform, lang, screen, f in captures(source):
        if f.stat().st_size == 0:
            # A capture cut off part way (an older shoot.py wrote straight to the final name): never a capture, so
            # the screen's marker in the same source, if any, counts instead.
            log(f"::warning::{platform}/{lang}/{f.name} in {source} is empty (a capture cut off); not used.")
            continue
        folder = dest / platform / lang
        folder.mkdir(parents=True, exist_ok=True)
        clear(folder, screen)
        shutil.copyfile(f, folder / f.name)
        found.add((platform, lang, screen))
        count += 1
    for platform, lang, screen, name, reason in failures(source):
        if (platform, lang, screen) in found:
            continue  # the same source also has a capture of it: that one counts
        folder = dest / platform / lang
        folder.mkdir(parents=True, exist_ok=True)
        dropped = clear(folder, screen)
        (folder / name).write_text(reason + "\n")
        where = f"{platform}/{lang}/{screen}: the capture failed in {source}" + (f" ({reason})" if reason else "")
        if dropped:
            log(f"::warning::{where}; the earlier {' '.join(dropped)} is not used, so it is reported as missing.")
        else:
            log(f"{where}; reported as missing.")
    return count


def check_spec():
    spec = json.loads((HERE / "screens.json").read_text())
    errors = []
    for name, s in spec["screens"].items():
        for key in ("title", "shows", "args"):
            if key not in s:
                errors.append(f"screen {name}: no {key}")
        if s.get("city") and s["city"] not in spec["cities"]:
            errors.append(f"screen {name}: unknown city {s['city']}")
        if s.get("appearance", "light") not in ("light", "dark"):
            errors.append(f"screen {name}: appearance must be light or dark")
    for platform, conf in spec["platforms"].items():
        order = conf["order"]
        if len(order) > 10:
            errors.append(f"{platform}: {len(order)} screens (App Store Connect takes at most 10)")
        if len(set(order)) != len(order):
            errors.append(f"{platform}: a screen appears twice")
        errors += [f"{platform}: unknown screen {s}" for s in order if s not in spec["screens"]]
    return errors


def check_merge():
    """An older run in the old order, then a run with only the new screen: every screen is found by name."""
    errors = []
    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        old = tmp / "old"
        (old / "APP_IPHONE_67/en-US").mkdir(parents=True)
        entries = []
        for n, screen in enumerate(["prayer", "qibla", "mosques-map", "prayer-dark"], 1):
            name = f"APP_IPHONE_67/en-US/{n:02d}-{screen}.png"
            (old / name).write_bytes(b"old " + screen.encode())
            for locale, same in (("en-US", None), ("en-GB", "en-US")):
                entries.append({"platform": "iphone", "appLanguage": "en", "locale": locale, "screen": screen,
                                "file": name, "sameFileAs": same})
        (old / "manifest.json").write_text(json.dumps({"screenshots": entries}))
        new = tmp / "new"
        (new / "iphone/en").mkdir(parents=True)
        (new / "iphone/en/02-prayer-weather.png").write_bytes(b"new prayer-weather")
        (new / "iphone/en/03-qibla.png").write_bytes(b"new qibla")
        raw = tmp / "raw"
        collect(raw, old)
        collect(raw, new)
        folder = raw / "iphone/en"
        names = sorted(p.name for p in folder.iterdir())
        if names != ["01-prayer.png", "02-prayer-weather.png", "03-mosques-map.png", "03-qibla.png", "04-prayer-dark.png"]:
            errors.append(f"merge: unexpected files {names}")
        want = {"prayer": b"old prayer", "prayer-weather": b"new prayer-weather", "qibla": b"new qibla",
                "mosques-map": b"old mosques-map"}
        for n, (screen, body) in enumerate(want.items(), 1):
            got = find_capture(folder, n, screen)
            if got is None or got.read_bytes() != body:
                errors.append(f"merge: {screen} at {n} found {got and got.name}")
        if find_capture(folder, 5, "widgets") is not None:
            errors.append("merge: found a capture that does not exist")
        # `prayer` must not pick up `prayer-dark` or `prayer-weather`, nor `widgets` pick up `widgets-qibla`.
        (folder / "07-widgets-qibla.png").write_bytes(b"x")
        if find_capture(folder, 6, "widgets") is not None or find_capture(folder, 1, "prayer").name != "01-prayer.png":
            errors.append("merge: a screen name matched another screen's file")
    return errors + check_failed_merge()


def check_failed_merge():
    """A recapture that failed: the earlier capture of that screen is not used (finish.py reports it missing),
    unless the same source also has a capture of it; a later capture clears it; a finished set carries it on."""
    errors, quiet = [], (lambda *a: None)
    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        old = tmp / "old"   # an earlier run: the old welcome tour as the last Apple Vision Pro screen
        (old / "vision/ar").mkdir(parents=True)
        for name in ("01-prayer.png", "02-mosques-map.png", "06-onboarding.png", "07-widgets-qibla.png"):
            (old / "vision/ar" / name).write_bytes(b"old " + name.encode())
        (old / "vision/en").mkdir(parents=True)
        (old / "vision/en/06-onboarding.png").write_bytes(b"old en onboarding")
        new = tmp / "new"   # this run: onboarding failed in ar; mosques-map has a marker and a capture
        (new / "vision/ar").mkdir(parents=True)
        (new / "vision/ar/01-prayer.png").write_bytes(b"new prayer")
        (new / "vision/ar/07-onboarding.failed").write_text("screenshot failed after 3 attempts: timed out\n")
        (new / "vision/ar/03-mosques-map.failed").write_text("not captured: the run stopped before this screen\n")
        (new / "vision/ar/03-mosques-map.png").write_bytes(b"new mosques-map")
        raw = tmp / "raw"
        collect(raw, old, quiet)
        collect(raw, new, quiet)
        folder = raw / "vision/ar"
        if find_capture(folder, 7, "onboarding") is not None:
            errors.append("failed merge: the older onboarding capture was kept after its recapture failed")
        marker = find_failure(folder, "onboarding")
        if marker is None or marker.name != "07-onboarding.failed" or "timed out" not in reason_of(marker):
            errors.append(f"failed merge: no onboarding marker with its reason ({marker and marker.name})")
        got = find_capture(folder, 3, "mosques-map")
        if got is None or got.read_bytes() != b"new mosques-map" or find_failure(folder, "mosques-map") is not None:
            errors.append("failed merge: a capture and a marker of one screen in one source: the capture must count")
        if find_capture(folder, 1, "prayer").read_bytes() != b"new prayer" or find_capture(folder, 9, "widgets-qibla") is None:
            errors.append("failed merge: other screens changed")
        if find_failure(folder, "qibla") is not None or find_failure(folder, "widgets") is not None:
            errors.append("failed merge: a marker matched another screen")
        if find_capture(raw / "vision/en", 7, "onboarding") is None:
            errors.append("failed merge: a marker of one language removed another language's capture")
        # finish.py lists the failed capture in the manifest; a later merge from that finished set (once the raw
        # captures have expired) still does not use the older capture.
        done = tmp / "done"
        (done / "APP_APPLE_VISION_PRO/ar-SA").mkdir(parents=True)
        (done / "APP_APPLE_VISION_PRO/ar-SA/01-prayer.png").write_bytes(b"new prayer")
        (done / "manifest.json").write_text(json.dumps({
            "screenshots": [{"platform": "vision", "appLanguage": "ar", "locale": "ar-SA", "screen": "prayer",
                             "file": "APP_APPLE_VISION_PRO/ar-SA/01-prayer.png", "sameFileAs": None}],
            "failed": [{"platform": "vision", "appLanguage": "ar", "screen": "onboarding", "position": 7,
                        "reason": "screenshot failed after 3 attempts: timed out"}]}))
        raw2 = tmp / "raw2"
        collect(raw2, old, quiet)
        collect(raw2, done, quiet)
        if find_capture(raw2 / "vision/ar", 7, "onboarding") is not None or find_failure(raw2 / "vision/ar", "onboarding") is None:
            errors.append("failed merge: a finished set's failed capture did not carry on")
        # A capture cut off part way (empty) next to its marker: the marker counts, the older capture is not used.
        cut = tmp / "cut"
        (cut / "vision/en").mkdir(parents=True)
        (cut / "vision/en/07-onboarding.png").write_bytes(b"")
        (cut / "vision/en/07-onboarding.failed").write_text("not captured: the run stopped before this screen\n")
        raw3 = tmp / "raw3"
        collect(raw3, old, quiet)
        collect(raw3, cut, quiet)
        if find_capture(raw3 / "vision/en", 7, "onboarding") is not None or find_failure(raw3 / "vision/en", "onboarding") is None:
            errors.append("failed merge: an empty capture counted, or its marker did not drop the older capture")
        # A later capture of the screen replaces the marker.
        later = tmp / "later"
        (later / "vision/ar").mkdir(parents=True)
        (later / "vision/ar/07-onboarding.png").write_bytes(b"newer onboarding")
        collect(raw, later, quiet)
        got = find_capture(folder, 7, "onboarding")
        if got is None or got.read_bytes() != b"newer onboarding" or find_failure(folder, "onboarding") is not None:
            errors.append("failed merge: a later capture did not replace the marker")
    return errors


def main():
    if sys.argv[1:] == ["--check"]:
        errors = check_spec() + check_merge()
        for e in errors:
            print(f"::error::{e}")
        print(f"screens.json and the capture merge: {len(errors)} problem(s)")
        sys.exit(1 if errors else 0)
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    dest = sys.argv[1]
    for source in sys.argv[2:]:
        print(f"{collect(dest, source)} captures from {source}")


if __name__ == "__main__":
    main()
