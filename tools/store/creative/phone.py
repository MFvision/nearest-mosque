#!/usr/bin/env python3
"""Puts an iPhone screenshot in a simple phone frame (rounded corners, a thin dark bezel, a soft shadow) on a
transparent layer, for the creative videos.

    phone.py <screenshot.png> <out.png> <height in px>
"""
import sys

from PIL import Image, ImageDraw, ImageFilter


def main():
    src, out, height = sys.argv[1], sys.argv[2], int(sys.argv[3])
    shot = Image.open(src).convert("RGB")
    sw, sh = shot.size
    bezel = round(height * 0.018)
    inner_h = height - 2 * bezel
    inner_w = round(inner_h * sw / sh)
    w = inner_w + 2 * bezel
    pad = round(height * 0.08)
    canvas = Image.new("RGBA", (w + 2 * pad, height + 2 * pad), (0, 0, 0, 0))

    # Soft shadow below the phone.
    shadow = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle((pad, pad + height * 0.03, pad + w, pad + height + height * 0.03), radius=w * 0.16, fill=110)
    shadow = shadow.filter(ImageFilter.GaussianBlur(height * 0.03))
    canvas.paste((6, 14, 30, 255), (0, 0), shadow)

    # The body: a near-black bezel with a faint rim of light.
    body = Image.new("RGBA", (w, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(body)
    d.rounded_rectangle((0, 0, w - 1, height - 1), radius=w * 0.155, fill=(18, 20, 24, 255), outline=(120, 126, 136, 255), width=max(2, bezel // 4))
    screen = shot.resize((inner_w, inner_h), Image.LANCZOS)
    mask = Image.new("L", (inner_w, inner_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, inner_w - 1, inner_h - 1), radius=inner_w * 0.13, fill=255)
    body.paste(screen, (bezel, bezel), mask)
    canvas.alpha_composite(body, (pad, pad))
    canvas.save(out)


if __name__ == "__main__":
    main()
