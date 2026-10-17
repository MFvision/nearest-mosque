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
"""
import json
import pathlib
import re
import shutil
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
NAME = re.compile(r"(\d{2})-(.+)\.png")


def screen_of(name):
    """The screen of a capture's file name (`03-mosques-map.png` → `mosques-map`), or None."""
    m = NAME.fullmatch(name)
    return m.group(2) if m else None


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


def collect(dest, source):
    """Copies the captures of `source` into the raw folder `dest`, each replacing that screen's earlier capture."""
    dest, count = pathlib.Path(dest), 0
    for platform, lang, screen, f in captures(source):
        folder = dest / platform / lang
        folder.mkdir(parents=True, exist_ok=True)
        for old in folder.glob(f"*-{screen}.png"):
            if screen_of(old.name) == screen:
                old.unlink()
        shutil.copyfile(f, folder / f.name)
        count += 1
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
