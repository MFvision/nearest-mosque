#!/usr/bin/env python3
"""Turns the raw captures into the App Store Connect upload set (needs Pillow).

    finish.py <raw captures folder> <output folder> <git commit>

Output:
    <out>/<display type>/<ASC locale>/<NN>-<screen>.png   one folder per device slot and store language
    <out>/manifest.json                                    every file: slot, locale, position, size, sha256, what it shows;
                                                           "missing" (no capture), "skipped" (still loading) and
                                                           "failed" (why, for every screen with no usable capture)
    <out>/contact-sheets/<platform>.jpg                    one row per language, for a quick look
    <out>/icons/                                           the app icons as built into the apps (for reference)

Each screen's capture is found by its name, whatever its number (the number is the position in the order of the
run that made it; collect.py gathers captures of several runs into the raw folder), and is written with its
position in the current order of screens.json. A screen with no capture is listed under "missing"; when the raw
folder has shoot.py's failure marker for it instead (`<NN>-<screen>.failed`, kept there by collect.py), it is also
listed under "failed" with the reason, so a later merge from this finished set still does not use an older capture.
A capture that is not a readable picture is listed under both too. An Apple Vision Pro capture left out as still
loading is listed under "skipped" and under "failed" (not "missing"), for the same reason.

A capture already at a size App Store Connect accepts is kept as it is (as RGB, no alpha). Anything else
(the Mac window, the Apple Vision Pro view) is placed, never stretched, on a canvas of an accepted size over
a blurred copy of itself.
"""
import datetime
import hashlib
import json
import pathlib
import shutil
import sys

from PIL import Image, ImageEnhance, ImageFilter, ImageStat

from collect import find_capture, find_failure, reason_of

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parent.parent
SPEC = json.loads((HERE / "screens.json").read_text())
# The App Store version (platform) whose localizations hold each device's screenshot sets.
# The Apple Watch screenshots belong to the iOS version (the watch app ships inside the iPhone app).
ASC_PLATFORM = {"iphone": "IOS", "ipad": "IOS", "duo-outer": "IOS", "duo-inner": "IOS", "watch": "IOS", "mac": "MAC_OS", "tv": "TV_OS", "vision": "VISION_OS"}


def fit_on_canvas(im, size):
    w, h = size
    back = im.convert("RGB")
    scale = max(w / back.width, h / back.height)
    back = back.resize((round(back.width * scale), round(back.height * scale)), Image.LANCZOS)
    back = back.crop(((back.width - w) // 2, (back.height - h) // 2, (back.width - w) // 2 + w, (back.height - h) // 2 + h))
    back = ImageEnhance.Brightness(back.filter(ImageFilter.GaussianBlur(w / 40))).enhance(0.55)
    # The picture itself: at most 92% of the canvas, never enlarged more than 1.5x.
    scale = min(0.92 * w / im.width, 0.92 * h / im.height, 1.5)
    front = im.convert("RGBA").resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
    back.paste(front, ((w - front.width) // 2, (h - front.height) // 2), front)
    return back


def loading_screen(im):
    """True when an Apple Vision Pro capture shows the window still loading (the logo on blank glass):
    the middle of the picture then has almost no edges."""
    w, h = im.size
    mid = im.convert("L").crop((int(w * .25), int(h * .25), int(w * .75), int(h * .70))).resize((480, 270))
    return ImageStat.Stat(mid.filter(ImageFilter.FIND_EDGES)).mean[0] < 7


def accepted(im, sizes):
    return any(im.size in ((a, b), (b, a)) for a, b in sizes)


def target_size(im, sizes):
    landscape = im.width >= im.height
    options = [(max(a, b), min(a, b)) if landscape else (min(a, b), max(a, b)) for a, b in sizes]
    # The smallest accepted size that holds the capture without enlarging it; else the largest.
    for s in sorted(options, key=lambda s: s[0] * s[1]):
        if s[0] >= im.width * 0.92 and s[1] >= im.height * 0.92:
            return s
    return max(options, key=lambda s: s[0] * s[1])


def sha256(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def md5(p):
    # App Store Connect's upload commit asks for the file's MD5 (sourceFileChecksum).
    return hashlib.md5(p.read_bytes()).hexdigest()


def contact_sheet(rows, path, thumb=180):
    rows = [r for r in rows if r[1]]
    if not rows:
        return
    first = Image.open(rows[0][1][0])
    th = round(first.height * thumb / first.width)
    cols = max(len(files) for _, files in rows)
    sheet = Image.new("RGB", (cols * (thumb + 6) + 70, len(rows) * (th + 6)), "white")
    from PIL import ImageDraw
    draw = ImageDraw.Draw(sheet)
    for y, (label, files) in enumerate(rows):
        draw.text((6, y * (th + 6) + th // 2), label, fill="black")
        for x, f in enumerate(files):
            im = Image.open(f).convert("RGB")
            im.thumbnail((thumb, th))
            sheet.paste(im, (70 + x * (thumb + 6), y * (th + 6)))
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path, quality=82)


def icons(out):
    d = out / "icons"
    d.mkdir(parents=True, exist_ok=True)
    ios = REPO / "ios/App/Resources/Assets.xcassets/AppIcon.appiconset"
    for name in ("AppIcon-1024.png", "AppIcon-Dark-1024.png", "AppIcon-Tinted-1024.png"):
        if (ios / name).exists():
            shutil.copy(ios / name, d / f"iphone-ipad-mac-{name}")
    tv = REPO / "ios/TV/Resources/Assets.xcassets/App Icon & Top Shelf Image.brandassets"
    store = tv / "App Icon - App Store.imagestack"
    back, front = store / "Back.imagestacklayer/Content.imageset/back@1x.png", store / "Front.imagestacklayer/Content.imageset/front@1x.png"
    if back.exists() and front.exists():
        b, f = Image.open(back).convert("RGBA"), Image.open(front).convert("RGBA")
        b.alpha_composite(f.resize(b.size))
        b.convert("RGB").save(d / "apple-tv-app-store-1280x768.png")
    for p in (tv / "Top Shelf Image Wide.imageset/shelf@2x.png", tv / "Top Shelf Image.imageset/shelf@2x.png"):
        if p.exists():
            shutil.copy(p, d / ("apple-tv-" + p.parent.name.replace(".imageset", "").lower().replace(" ", "-") + ".png"))
    vision = REPO / "ios/Vision/Assets.xcassets/VisionIcon.solidimagestack"
    layers = [vision / f"{n}.solidimagestacklayer/Content.imageset/{n.lower()}.png" for n in ("Back", "Middle", "Front")]
    if all(p.exists() for p in layers):
        base = Image.open(layers[0]).convert("RGBA")
        for p in layers[1:]:
            base.alpha_composite(Image.open(p).convert("RGBA").resize(base.size))
        base.convert("RGB").save(d / "apple-vision-pro-1024.png")
        for p in layers:
            shutil.copy(p, d / f"apple-vision-pro-layer-{p.name}")
    return sorted(str(p.relative_to(out)) for p in d.iterdir())


def main():
    raw, out, commit = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3]
    out.mkdir(parents=True, exist_ok=True)
    entries, missing, failed, skipped, sheets = [], [], [], [], {}
    for platform, conf in SPEC["platforms"].items():
        rows = []
        for lang in SPEC["languages"]:
            primary = lang["asc"][0]
            files = []
            for n, screen in enumerate(conf["order"], 1):
                name = f"{n:02d}-{screen}.png"
                src = find_capture(raw / platform / lang["app"], n, screen)
                if src is None:
                    missing.append(f"{platform}/{lang['app']}/{name}")
                    marker = find_failure(raw / platform / lang["app"], screen)
                    if marker is not None:
                        failed.append({"platform": platform, "appLanguage": lang["app"], "screen": screen,
                                       "position": n, "reason": reason_of(marker)})
                    continue
                try:
                    im = Image.open(src)
                    im.load()
                except OSError:   # not a picture (a capture cut off part way): reported, never a crash
                    missing.append(f"{platform}/{lang['app']}/{name}")
                    failed.append({"platform": platform, "appLanguage": lang["app"], "screen": screen,
                                   "position": n, "reason": f"unreadable capture {src.name}"})
                    continue
                if platform == "vision" and loading_screen(im):
                    skipped.append(f"{platform}/{lang['app']}/{name}: still loading when captured")
                    # Also under "failed": no usable capture of this screen, so a later merge from this finished
                    # set drops an older capture of it instead of bringing that back in silence.
                    failed.append({"platform": platform, "appLanguage": lang["app"], "screen": screen,
                                   "position": n, "reason": "still loading when captured"})
                    continue
                if not accepted(im, conf["sizes"]):
                    im = fit_on_canvas(im, target_size(im, conf["sizes"]))
                dest = out / conf["displayType"] / primary / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                im.convert("RGB").save(dest)
                files.append(dest)
                spec = SPEC["screens"][screen]
                shows = spec["shows"]
                if screen == "ask" and lang["app"] not in SPEC["askLanguages"]:
                    shows = "Ask: the question box and the list of common questions (answers to common questions are not yet in this language)."
                w, h = Image.open(dest).size
                for locale in lang["asc"]:
                    entries.append({
                        "displayType": conf["displayType"], "device": conf["label"], "platform": platform,
                        "ascPlatform": ASC_PLATFORM[platform],
                        "locale": locale, "appLanguage": lang["app"], "position": len(files),
                        "screen": screen, "title": spec["title"], "shows": shows,
                        "file": str(dest.relative_to(out)), "width": w, "height": h,
                        "bytes": dest.stat().st_size, "md5": md5(dest), "sha256": sha256(dest),
                        "sameFileAs": None if locale == primary else primary,
                    })
            rows.append((primary, files))
        contact_sheet(rows, out / "contact-sheets" / f"{platform}.jpg")
        sheets[platform] = f"contact-sheets/{platform}.jpg"
    manifest = {
        "about": "App Store Connect screenshots for Near Mosque. Upload each entry's file to the screenshot set of its "
                 "displayType in its locale, at its position (1 = first on the store page). Entries with sameFileAs reuse "
                 "the file of that locale. See docs/09-app-store.md.",
        "bundleId": "sa.zood.nearmosque",
        "commit": commit,
        "generated": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "displayTypes": {c["displayType"]: {"device": c["label"], "platform": p, "ascPlatform": ASC_PLATFORM[p],
                                            "acceptedSizes": c["sizes"],
                                            "screens": c["order"]} for p, c in SPEC["platforms"].items()},
        "locales": {l["asc"][0]: {"appLanguage": l["app"], "alsoFor": l["asc"][1:]} for l in SPEC["languages"]},
        "contactSheets": sheets,
        "icons": icons(out),
        "missing": missing,
        "failed": failed,
        "skipped": skipped,
        "screenshots": entries,
    }
    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n")
    print(f"{len(entries)} screenshot entries, {len(missing)} missing, {len(skipped)} left out (still loading), "
          f"{len(failed)} failed (no usable capture: a later merge from this set does not use an older one)")
    why = {f"{f['platform']}/{f['appLanguage']}/{f['position']:02d}-{f['screen']}.png": f["reason"] for f in failed}
    for k in skipped:
        print(f"::notice::left out {k}")
    for m in missing:
        print(f"::warning::missing {m}" + (f" (the capture failed: {why[m]})" if m in why else ""))


if __name__ == "__main__":
    main()
