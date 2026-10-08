"""Apple TV icon and Top Shelf images from the same logo (option 2) as the phone apps.

    python3 install_tv.py

Writes ios/TV/Resources/Assets.xcassets/App Icon & Top Shelf Image.brandassets: the layered app icon
(gold back layer, logo front layer; tvOS moves the layers apart when the icon is focused) at the three
required sizes, and the static Top Shelf images shown until the app has a city (Top Shelf extension)."""
import json, os, subprocess, tempfile
from PIL import Image
from options import behind_inner, BEHIND_EXTENT
from install import CHROME, ROOT, CX

TOP, BOTTOM = BEHIND_EXTENT
OUT = f"{ROOT}/ios/TV/Resources/Assets.xcassets"
BRAND = f"{OUT}/App Icon & Top Shelf Image.brandassets"
INFO = {"author": "xcode", "version": 1}


def mark(w, h, height, cx=None):
    """The logo `height` px tall, centred at cx (default the middle) on a transparent w x h canvas."""
    k = height / (BOTTOM - TOP)
    cx = w / 2 if cx is None else cx
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">'
            f'<g transform="translate({cx - CX * k:.2f} {h / 2 - (TOP + BOTTOM) / 2 * k:.2f}) scale({k:.5f})">{behind_inner()}</g></svg>')


def gold(w, h):
    """The light app icon's background gradient."""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">'
            f'<stop offset="0" stop-color="#E8B868"/><stop offset="0.5" stop-color="#F6DFB2"/><stop offset="1" stop-color="#FFF9EF"/>'
            f'</linearGradient></defs><rect width="{w}" height="{h}" fill="url(#g)"/></svg>')


def shelf(w, h):
    """Night sky with a gold horizon glow and the logo on a cream disc, for the static Top Shelf."""
    d = h * 0.62
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}"><defs>'
            f'<linearGradient id="s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#081B29"/>'
            f'<stop offset="0.65" stop-color="#12304A"/><stop offset="1" stop-color="#2C4C7A"/></linearGradient>'
            f'<radialGradient id="glow" cx="0.5" cy="1" r="0.75"><stop offset="0" stop-color="#D4A843" stop-opacity="0.55"/>'
            f'<stop offset="1" stop-color="#D4A843" stop-opacity="0"/></radialGradient></defs>'
            f'<rect width="{w}" height="{h}" fill="url(#s)"/><rect width="{w}" height="{h}" fill="url(#glow)"/>'
            f'<circle cx="{w / 2}" cy="{h / 2}" r="{d / 2}" fill="#FFF9EF"/></svg>')


def render(svg, out, w, h, opaque):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False) as f:
        f.write(f'<html><body style="margin:0;background:transparent">{svg.replace("<svg ", f"<svg width={w} height={h} ", 1)}</body></html>')
    subprocess.run([CHROME, "--no-sandbox", "--hide-scrollbars", "--default-background-color=00000000",
                    f"--window-size={w},{h}", f"--screenshot={out}", f"file://{f.name}"], check=True, capture_output=True)
    os.unlink(f.name)
    img = Image.open(out)
    (img.convert("RGB") if opaque else img.convert("RGBA")).save(out)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(json.dumps(obj, indent=2) + "\n")


def stack(name, w, h, scales):
    """A two-layer image stack: Front (logo) over Back (gold)."""
    d = f"{BRAND}/{name}.imagestack"
    write(f"{d}/Contents.json", {"info": INFO, "layers": [{"filename": "Front.imagestacklayer"}, {"filename": "Back.imagestacklayer"}]})
    for layer, draw, opaque in (("Front", lambda W, H: mark(W, H, H * 0.62), False), ("Back", gold, True)):
        write(f"{d}/{layer}.imagestacklayer/Contents.json", {"info": INFO})
        images = []
        for s in scales:
            fname = f"{layer.lower()}@{s}x.png"
            render(draw(w * s, h * s), f"{d}/{layer}.imagestacklayer/Content.imageset/{fname}", w * s, h * s, opaque)
            images.append({"filename": fname, "idiom": "tv", "scale": f"{s}x"})
        write(f"{d}/{layer}.imagestacklayer/Content.imageset/Contents.json", {"images": images, "info": INFO})


def shelf_set(name, w, h):
    d = f"{BRAND}/{name}.imageset"
    images = []
    for s in (1, 2):
        fname = f"shelf@{s}x.png"
        W, H = w * s, h * s
        # The disc and the logo in one render: the shelf behind, the mark on top.
        svg = shelf(W, H).replace("</svg>", mark(W, H, H * 0.44).split(">", 1)[1])
        render(svg, f"{d}/{fname}", W, H, True)
        images.append({"filename": fname, "idiom": "tv", "scale": f"{s}x"})
    write(f"{d}/Contents.json", {"images": images, "info": INFO})


if __name__ == "__main__":
    write(f"{OUT}/Contents.json", {"info": INFO})
    write(f"{BRAND}/Contents.json", {"assets": [
        {"filename": "App Icon - App Store.imagestack", "idiom": "tv", "role": "primary-app-icon", "size": "1280x768"},
        {"filename": "App Icon.imagestack", "idiom": "tv", "role": "primary-app-icon", "size": "400x240"},
        {"filename": "Top Shelf Image Wide.imageset", "idiom": "tv", "role": "top-shelf-image-wide", "size": "2320x720"},
        {"filename": "Top Shelf Image.imageset", "idiom": "tv", "role": "top-shelf-image", "size": "1920x720"},
    ], "info": INFO})
    stack("App Icon - App Store", 1280, 768, (1,))
    stack("App Icon", 400, 240, (1, 2))
    shelf_set("Top Shelf Image", 1920, 720)
    shelf_set("Top Shelf Image Wide", 2320, 720)
    print("installed")
