"""Writes installed-preview.png from the installed icon files: iOS light, dark and tinted; the Android
adaptive icon under a circle mask; its themed (monochrome) layer; and the logo mark in its disc."""
import os
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../.."))
I = f"{ROOT}/ios/App/Resources/Assets.xcassets/AppIcon.appiconset/"
D = f"{ROOT}/android/app/src/main/res/drawable-nodpi/"
GROUND = "#1d2128"


def android(layer, fill_bg, tint=None):
    bg = fill_bg()
    fg = Image.open(D + layer)
    bg.paste(Image.new("RGB", fg.size, tint) if tint else fg, (0, 0), fg)
    m = Image.new("L", (432, 432), 0)
    ImageDraw.Draw(m).ellipse((72, 72, 360, 360), fill=255)
    out = Image.new("RGB", (432, 432), GROUND)
    out.paste(bg, (0, 0), m)
    return out.crop((72, 72, 360, 360)).resize((1024, 1024), Image.LANCZOS)


def gradient():
    """res/drawable/ic_launcher_background.xml: #E9B96A, #F6DDAE, #FFF8EC from top left to bottom right."""
    stops = [(0xE9, 0xB9, 0x6A), (0xF6, 0xDD, 0xAE), (0xFF, 0xF8, 0xEC)]
    im = Image.new("RGB", (432, 432))
    px = im.load()
    for y in range(432):
        for x in range(432):
            t = (x + y) / 862
            a, b, u = (stops[0], stops[1], t * 2) if t < 0.5 else (stops[1], stops[2], (t - 0.5) * 2)
            px[x, y] = tuple(int(a[i] + (b[i] - a[i]) * u) for i in range(3))
    return im


def disc():
    mark = Image.open(D + "logo_mark.png").resize((350, 350), Image.LANCZOS)
    im = Image.new("RGB", (512, 512), GROUND)
    ImageDraw.Draw(im).ellipse((0, 0, 511, 511), fill="#F7F7F7")
    im.paste(mark, (81, 81), mark)
    return im.resize((1024, 1024), Image.LANCZOS)


if __name__ == "__main__":
    rounded = Image.new("L", (1024, 1024), 0)
    ImageDraw.Draw(rounded).rounded_rectangle((0, 0, 1023, 1023), 230, fill=255)
    tiles = []
    for name in ("AppIcon-1024.png", "AppIcon-Dark-1024.png", "AppIcon-Tinted-1024.png"):
        t = Image.new("RGB", (1024, 1024), GROUND)
        t.paste(Image.open(I + name).convert("RGB"), (0, 0), rounded)
        tiles.append(t)
    tiles += [android("ic_launcher_foreground.png", gradient),
              android("ic_launcher_monochrome.png", lambda: Image.new("RGB", (432, 432), "#2B3A4A"), "#CFE3F7"), disc()]
    out = Image.new("RGB", (len(tiles) * 1064 + 40, 1104), GROUND)
    for i, t in enumerate(tiles):
        out.paste(t, (40 + i * 1064, 40))
    out.resize((out.width // 4, out.height // 4), Image.LANCZOS).save(os.path.join(os.path.dirname(__file__), "installed-preview.png"))
