"""Installs the chosen logo (option 2: the arrow behind the cube) as the app icon on both platforms.

    python3 install.py

Writes ../near-mosque-icon.svg, ../near-mosque-icon-dark.svg and ../near-mosque-mark.svg, then renders
(headless Chromium) the iOS app icon (light, dark and tinted) and logo mark, and the Android adaptive
icon foreground, monochrome layer and logo mark."""
import os, subprocess, tempfile
from options import behind, behind_inner, place, BEHIND_EXTENT, ARROW, FACET, ARROW2, NAVY, GOLD, WEIGHT, arrow_tf
from emblem import emblem, INK

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../.."))
CHROME = "/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell"
IOS = f"{ROOT}/ios/App/Resources/Assets.xcassets"
DRAWABLE = f"{ROOT}/android/app/src/main/res/drawable-nodpi"
TOP, BOTTOM = BEHIND_EXTENT
CX = 400


def fit(inner, canvas, height, cy=None):
    """Option 2 scaled to `height` px and centred in a transparent square canvas."""
    k = height / (BOTTOM - TOP)
    mid = (TOP + BOTTOM) / 2
    cy = canvas / 2 if cy is None else cy
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {canvas} {canvas}">'
            f'<g transform="translate({canvas / 2 - CX * k:.2f} {cy - mid * k:.2f}) scale({k:.5f})">{inner}</g></svg>')


def tinted():
    """iOS tinted icon: grayscale on black; the system applies the user's tint."""
    inner = behind_inner(ink="#FFFFFF", body="#9A9A9A", facet="#FFFFFF")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"><rect width="1024" height="1024" fill="#000"/>'
            f'<g transform="{place(0.72, 528)}">{inner}</g></svg>')


def render(svg, out, size):
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False) as f:
        f.write(f'<html><body style="margin:0;background:transparent">'
                f'{svg.replace("<svg ", f"<svg width={size} height={size} ", 1)}</body></html>')
    subprocess.run([CHROME, "--no-sandbox", "--hide-scrollbars", "--default-background-color=00000000",
                    f"--window-size={size},{size}", f"--screenshot={out}", f"file://{f.name}"],
                   check=True, capture_output=True)
    os.unlink(f.name)


def opaque(path):
    """App Store icons must have no alpha channel."""
    from PIL import Image
    Image.open(path).convert("RGB").save(path)


def guide_layers():
    """The mark split in two for the Qibla guide: the emblem alone, and the arrow alone pointing straight up
    at its place in the mark. The apps turn the arrow about its centre, ARROW_PIVOT (fractions of the image)."""
    body = fit(emblem(**WEIGHT, ink=INK), 512, 488)
    a = ARROW2
    arrow = fit(f'<g transform="{arrow_tf(a["x"], a["y"], a["h"], 0)}"><path d="{ARROW}" fill="{NAVY}" stroke="{NAVY}" '
                f'stroke-width="3" stroke-linejoin="round"/><path d="{FACET}" fill="{GOLD}"/></g>', 512, 488)
    return body, arrow


k = 488 / (BOTTOM - TOP)
ARROW_PIVOT = ((256 - CX * k + ARROW2["x"] * k) / 512, (256 - (TOP + BOTTOM) / 2 * k + ARROW2["y"] * k) / 512)


if __name__ == "__main__":
    light, dark = behind("l"), behind("d", dark=True)
    mark = fit(behind_inner(), 512, 488)
    open(f"{ROOT}/shared/brand/near-mosque-icon.svg", "w").write(light)
    open(f"{ROOT}/shared/brand/near-mosque-icon-dark.svg", "w").write(dark)
    open(f"{ROOT}/shared/brand/near-mosque-mark.svg", "w").write(mark)

    icon_set = f"{IOS}/AppIcon.appiconset"
    for name, svg in (("AppIcon-1024.png", light), ("AppIcon-Dark-1024.png", dark), ("AppIcon-Tinted-1024.png", tinted())):
        render(svg, f"{icon_set}/{name}", 1024)
        opaque(f"{icon_set}/{name}")
    render(mark, f"{IOS}/LogoMark.imageset/logo-mark.png", 512)
    render(mark, f"{DRAWABLE}/logo_mark.png", 512)
    # Adaptive icon: 108 dp canvas (432 px), artwork inside the 66 dp safe circle (264 px); 218 px tall keeps the roof corners and arrow tip inside it.
    render(fit(behind_inner(), 432, 218), f"{DRAWABLE}/ic_launcher_foreground.png", 432)
    render(fit(behind_inner(ink="#FFFFFF", body="#FFFFFF", facet="#FFFFFF"), 432, 218), f"{DRAWABLE}/ic_launcher_monochrome.png", 432)
    body, arrow = guide_layers()
    for name, svg in (("LogoBody", body), ("LogoArrow", arrow)):
        os.makedirs(f"{IOS}/{name}.imageset", exist_ok=True)
        fname = name.lower().replace("logo", "logo-")
        render(svg, f"{IOS}/{name}.imageset/{fname}.png", 512)
        open(f"{IOS}/{name}.imageset/Contents.json", "w").write(
            '{"images":[{"filename":"%s.png","idiom":"universal"}],"info":{"author":"xcode","version":1}}\n' % fname)
    render(body, f"{DRAWABLE}/logo_body.png", 512)
    render(arrow, f"{DRAWABLE}/logo_arrow.png", 512)
    print("arrow pivot", ARROW_PIVOT)
    render(light, f"{ROOT}/shared/brand/app-icon-preview.png", 1024)
    render(mark, f"{ROOT}/shared/brand/mark-512.png", 512)
    print("installed")
