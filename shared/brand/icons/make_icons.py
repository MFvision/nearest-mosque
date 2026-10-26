"""Draws the app icons people can choose in Settings › Menu and start › App icon: the app's own mark (option 2,
the arrow behind the cube) in the colours of the app styles. Gold is the app's own icon (emblem/install.py).

    python3 make_icons.py

Writes, for each one, the alternate icon set ios/App/Resources/Assets.xcassets/AppIcon-<Name>.appiconset
(1024 px, no alpha, as the App Store asks), its small picture for the picker (AppIconPreview-<name>.imageset)
and icons-preview.png, all of them side by side. The names match AppIconChoice (NMCore/StartScreen.swift)."""
import json, os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "../emblem"))
from options import behind_inner, place, NAVY, GOLD, CREAM  # noqa: E402
from emblem import INK  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
IOS = f"{ROOT}/ios/App/Resources/Assets.xcassets"
CHROME = os.environ.get("CHROME", "/opt/pw-browsers/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell")

# name: (background top-left to bottom-right, cube lines, arrow, arrow's lit half). Gold is the app's own icon.
ICONS = {
    "gold": (("#E8B868", "#F6DFB2", "#FFF9EF"), INK, NAVY, GOLD),
    "night": (("#081B29", "#0E2238", "#1A4D6E"), GOLD, CREAM, GOLD),
    "green": (("#0B3326", "#0F3D2E", "#1D6B47"), "#F6DFB2", CREAM, GOLD),
    "teal": (("#2F6E69", "#3E7D78", "#5E9C95"), "#FFF8EC", "#123F3B", "#F2D27A"),
    "sky": (("#1C3F7A", "#3E6AA8", "#8FB6E0"), "#FFF8EC", "#0A1430", GOLD),
    "black": (("#000000", "#0B0B0B", "#1C1C1C"), GOLD, "#FFFFFF", GOLD),
}


def svg(stops, ink, body, facet, key="bg"):
    grad = "".join(f'<stop offset="{i / 2}" stop-color="{c}"/>' for i, c in enumerate(stops))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"><defs><linearGradient id="{key}" '
            f'gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="1024" y2="1024">{grad}</linearGradient></defs>'
            f'<rect width="1024" height="1024" fill="url(#{key})"/>'
            f'<g transform="{place(0.72, 528)}">{behind_inner(ink, body, facet)}</g></svg>')


def render(html, out, w, h):
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False) as f:
        f.write(html)
    subprocess.run([CHROME, "--no-sandbox", "--hide-scrollbars", "--default-background-color=00000000",
                    f"--window-size={w},{h}", f"--screenshot={out}", f"file://{f.name}"], check=True, capture_output=True)
    os.unlink(f.name)


def page(s, size):
    return f'<html><body style="margin:0;background:transparent">{s.replace("<svg ", f"<svg width={size} height={size} ", 1)}</body></html>'


def contents(images):
    return json.dumps({"images": images, "info": {"author": "xcode", "version": 1}}, indent=2) + "\n"


if __name__ == "__main__":
    from PIL import Image
    for name, colours in ICONS.items():
        s = svg(*colours)
        if name != "gold":
            title = name.capitalize()
            d = f"{IOS}/AppIcon-{title}.appiconset"
            os.makedirs(d, exist_ok=True)
            png = f"{d}/AppIcon-{title}-1024.png"
            render(page(s, 1024), png, 1024, 1024)
            Image.open(png).convert("RGB").save(png)
            open(f"{d}/Contents.json", "w").write(contents([
                {"filename": f"AppIcon-{title}-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}]))
        d = f"{IOS}/AppIconPreview-{name}.imageset"
        os.makedirs(d, exist_ok=True)
        png = f"{d}/AppIconPreview-{name}.png"
        render(page(s, 180), png, 180, 180)
        Image.open(png).convert("RGB").save(png)
        open(f"{d}/Contents.json", "w").write(contents([{"filename": f"AppIconPreview-{name}.png", "idiom": "universal"}]))
    row = "".join(f'<div style="text-align:center;font:600 22px -apple-system,sans-serif;color:#333">'
                  f'<div style="width:180px;height:180px;border-radius:40px;overflow:hidden">{svg(*c, key=n).replace("<svg ", "<svg width=180 height=180 ", 1)}</div>'
                  f'<div style="margin-top:10px">{n.capitalize()}</div></div>' for n, c in ICONS.items())
    render(f'<html><body style="margin:0;background:#F2F2F7;display:flex;gap:28px;padding:28px">{row}</body></html>',
           f"{HERE}/icons-preview.png", 28 + len(ICONS) * 208, 290)
    print("icons written")
