#!/usr/bin/env python3
"""Google Play graphics: the app icon, a feature graphic per app language and the phone screenshots, checked
against Google Play's limits, with a manifest.

    play_graphics.py [--shots android/app/build/store-play] [--out build/store-play] [--languages "en ar"]
                     [--slots "phone seven ten desktop xr"]

- icon.png: 512 x 512, 32-bit PNG, at most 1 MB, from shared/brand/app-icon-preview.png.
- <lang>/feature.png: 1024 x 500, 24-bit PNG (no alpha). The app's dusk sky over the mosque skyline (the motif of
  tools/store/creative/sky.js) with the app icon, the app name (shared/i18n/strings.json app_name) and the
  localized tagline (shared/store/creative.json "phrase", where there is one). Everything that matters stays in the
  middle (Google crops the edges and lays its own controls over some placements).
- <lang>/phone|seven|ten/NN-name.png: the screenshots of StoreScreenshotTest (android/app/build/store-play/<lang>/
  <slot>) for Google Play's phone, 7-inch and 10-inch slots (SLOTS), flattened to RGB and checked: each side 320 to
  3840 px, the long side at most twice the short one, at most 8 MB, one file per number NN (the store order),
  2 (phone) or 4 (tablets) to 8 per language (each slot folder is emptied first, so no older set stays);
  fewer than the recommended number at 16:9 or 9:16 and 1080 px or more is reported, as it can lose promotion
  eligibility.
- <lang>/desktop/NN-name.png: the Chromebook (desktop) slot, a maximized desktop window at 2880 x 1620: 4 to 8,
  16:9 (or 9:16), each side 1080 px or more.
- <lang>/xr/NN-name.png: the Android XR slot, 3840 x 2160 (16:9: Play Console rejects other ratios there),
  4 to 8: the app's 1024 x 720 dp panel (as Android XR shows a large-screen app) floating in a calm dusk space
  (xr_scene), drawn here and not imitating any headset's own interface.
- manifest.json: the files per language and slot, each API slot's Play image type ("playSlots"), the slots the
  Google Play Developer API cannot upload, to add by hand in Play Console ("consoleSlots": desktop, xr), and any
  problems.

Text is laid out with libraqm (Pillow's RAQM layout: bidi, Arabic joining, Indic, Khmer and Sinhala shaping) in the
Noto font of each script (Debian/Ubuntu: fonts-noto-core and fonts-noto-cjk). Without libraqm, Arabic-script text
falls back to python-bidi + arabic-reshaper, and the other complex scripts are refused (they would be misdrawn).
Exits with status 1 when any check fails.
"""
import argparse
import json
import math
import pathlib
import random
import sys
import unicodedata

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, features

ROOT = pathlib.Path(__file__).resolve().parents[2]
RAQM = features.check("raqm")

FONT_DIRS = [pathlib.Path(p) for p in (
    "/usr/share/fonts/truetype/noto", "/usr/share/fonts/opentype/noto", "/usr/share/fonts/noto",
    "/usr/share/fonts/noto-cjk", "/usr/share/fonts/google-noto", "/usr/local/share/fonts", str(pathlib.Path.home() / ".fonts"),
)]

# Script (from the Unicode character names) -> (bold font file, regular font file, ttc index).
SCRIPT_FONTS = {
    "LATIN": ("NotoSans-Bold.ttf", "NotoSans-Regular.ttf", 0),
    "ARABIC": ("NotoSansArabic-Bold.ttf", "NotoSansArabic-Regular.ttf", 0),
    "URDU": ("NotoNastaliqUrdu-Bold.ttf", "NotoNastaliqUrdu-Regular.ttf", 0),
    "BENGALI": ("NotoSansBengali-Bold.ttf", "NotoSansBengali-Regular.ttf", 0),
    "DEVANAGARI": ("NotoSansDevanagari-Bold.ttf", "NotoSansDevanagari-Regular.ttf", 0),
    "GUJARATI": ("NotoSansGujarati-Bold.ttf", "NotoSansGujarati-Regular.ttf", 0),
    "GURMUKHI": ("NotoSansGurmukhi-Bold.ttf", "NotoSansGurmukhi-Regular.ttf", 0),
    "KANNADA": ("NotoSansKannada-Bold.ttf", "NotoSansKannada-Regular.ttf", 0),
    "MALAYALAM": ("NotoSansMalayalam-Bold.ttf", "NotoSansMalayalam-Regular.ttf", 0),
    "TELUGU": ("NotoSansTelugu-Bold.ttf", "NotoSansTelugu-Regular.ttf", 0),
    "SINHALA": ("NotoSansSinhala-Bold.ttf", "NotoSansSinhala-Regular.ttf", 0),
    "KHMER": ("NotoSansKhmer-Bold.ttf", "NotoSansKhmer-Regular.ttf", 0),
    "THAI": ("NotoSansThai-Bold.ttf", "NotoSansThai-Regular.ttf", 0),
    "GEORGIAN": ("NotoSansGeorgian-Bold.ttf", "NotoSansGeorgian-Regular.ttf", 0),
    # Noto Sans CJK collections: 0 JP, 1 KR, 2 SC, 3 TC, 4 HK.
    "CJK": ("NotoSansCJK-Bold.ttc", "NotoSansCJK-Regular.ttc", 2),
}
COMPLEX = {"ARABIC", "URDU", "BENGALI", "DEVANAGARI", "GUJARATI", "GURMUKHI", "KANNADA", "MALAYALAM", "TELUGU", "SINHALA", "KHMER", "THAI"}
# App language -> the language tag raqm gets (localized glyph forms, e.g. Persian and Urdu digits and letters).
RAQM_LANG = {"prs": "fa", "ckb": "ckb", "tl": "fil", "zh": "zh-Hans"}

# The maghrib sky of shared/design/tokens.json (sky.maghrib) and the brand gold.
SKY_TOP, SKY_MID, SKY_GLOW, SKY_LOW = (0x1B, 0x1F, 0x45), (0x4A, 0x2F, 0x5E), (0xF0, 0x8A, 0x5D), (0x2A, 0x1E, 0x3E)
GOLD_LIGHT = (0xF2, 0xD2, 0x7A)

# Screenshot folders (StoreScreenshotTest's devices) -> (Google Play Developer API image type, or None where the API
# has none and the files go up by hand in Play Console; the slot's name in Play Console; the fewest allowed; the
# number Google recommends at 16:9 or 9:16 and 1080 px or more for promotion; the aspect ratios allowed, if fixed;
# the smallest size (short side, long side)). The 7-inch slot gets the Galaxy Z Fold's inner screen unfolded
# (1811x2176, not 16:9), the 10-inch slot a 10-inch tablet held sideways (2560x1440), the Chromebook slot a desktop
# window (2880x1620) and the Android XR slot the app's panel in a space (3840x2160).
# Limits: Play Console Help, "Add preview assets to showcase your app"
# (https://support.google.com/googleplay/android-developer/answer/9866151); image types: AppImageType of the Google
# Play Developer API v3 (https://developers.google.com/android-publisher/api-ref/rest/v3/AppImageType), which has
# no Chromebook or Android XR type.
SLOTS = {
    "phone": ("phoneScreenshots", "Phone", 2, 4, None, (320, 320)),
    "seven": ("sevenInchScreenshots", "7-inch tablet", 4, 0, None, (320, 320)),
    "ten": ("tenInchScreenshots", "10-inch tablet", 4, 4, None, (320, 320)),
    "desktop": (None, "Chromebook", 4, 4, ((16, 9), (9, 16)), (1080, 1080)),
    "xr": (None, "Android XR", 4, 0, ((16, 9),), (1080, 1920)),
}
MAX_SHOTS, MAX_SIDE, MAX_BYTES = 8, 3840, 8 * 1024 * 1024
# Android XR: 3840 x 2160. Google's help suggests 8:5, but Play Console accepts only 16:9 or 9:16 there.
XW, XH = 3840, 2160

FW, FH = 1024, 500
# Key content stays inside this centred box (about 12% in from each side, 8% from top and bottom).
SAFE = (120, 40, FW - 120, FH - 40)


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def find_font(name):
    for d in FONT_DIRS:
        if d.is_dir():
            hit = next(iter(sorted(d.rglob(name))), None)
            if hit:
                return hit
    return None


def script_of(text, lang):
    counts = {}
    for ch in text:
        if not ch.isalpha():
            continue
        name = unicodedata.name(ch, "")
        for s in ("ARABIC", "BENGALI", "DEVANAGARI", "GUJARATI", "GURMUKHI", "KANNADA", "MALAYALAM", "TELUGU",
                  "SINHALA", "KHMER", "THAI", "GEORGIAN", "CJK", "HIRAGANA", "KATAKANA"):
            if name.startswith(s) or (s == "CJK" and "CJK" in name):
                counts[s] = counts.get(s, 0) + 1
                break
        else:
            counts["LATIN"] = counts.get("LATIN", 0) + 1
    script = max(counts, key=counts.get) if counts else "LATIN"
    if script == "ARABIC" and lang == "ur":
        script = "URDU"
    return script


def is_rtl(text):
    return any(unicodedata.bidirectional(ch) in ("R", "AL") for ch in text)


class Text:
    """A run of text in one script, laid out with raqm (or the Arabic fallback) at a given size."""

    def __init__(self, text, lang, bold, problems):
        self.text, self.lang = text, lang
        self.script = script_of(text, lang)
        bold_file, regular_file, index = SCRIPT_FONTS[self.script]
        self.path = find_font(bold_file if bold else regular_file) or find_font(regular_file)
        if self.path is None:
            raise SystemExit(f"{lang}: no font for {self.script} ({bold_file}); install fonts-noto-core and fonts-noto-cjk")
        self.index = index
        self.shown = text
        self.kwargs = {}
        if RAQM:
            self.kwargs = {"language": RAQM_LANG.get(lang, lang)}
            if is_rtl(text):
                self.kwargs["direction"] = "rtl"
        elif self.script in ("ARABIC", "URDU"):
            import arabic_reshaper  # noqa: PLC0415 (only without raqm)
            from bidi.algorithm import get_display  # noqa: PLC0415
            self.shown = get_display(arabic_reshaper.reshape(text))
        elif self.script in COMPLEX:
            raise SystemExit(f"{lang}: {self.script} text needs libraqm (Pillow built with raqm; apt install libraqm0 libfribidi0)")
        missing = self.missing_glyphs()
        if missing:
            problems.append(f"feature: {self.path.name} has no glyph for {' '.join(f'U+{ord(c):04X}' for c in missing)}")

    def font(self, size):
        engine = ImageFont.Layout.RAQM if RAQM else ImageFont.Layout.BASIC
        return ImageFont.truetype(str(self.path), size, index=self.index, layout_engine=engine)

    def missing_glyphs(self):
        """Characters the font draws as its .notdef box (compared with a code point no font has)."""
        f = ImageFont.truetype(str(self.path), 40, index=self.index, layout_engine=ImageFont.Layout.BASIC)
        notdef = f.getmask("\U0010FFFD")
        ref = (notdef.size, bytes(notdef))
        out = []
        for ch in set(self.text):
            if ch.isspace() or unicodedata.category(ch) in ("Mn", "Mc", "Cf"):
                continue
            m = f.getmask(ch)
            if (m.size, bytes(m)) == ref:
                out.append(ch)
        return sorted(out)

    def bbox(self, size):
        return ImageDraw.Draw(Image.new("L", (1, 1))).textbbox((0, 0), self.shown, font=self.font(size), **self.kwargs)

    def width(self, size):
        b = self.bbox(size)
        return b[2] - b[0]

    def fit(self, start, minimum, max_width):
        size = start
        while size > minimum and self.width(size) > max_width:
            size -= 1
        return size

    def draw(self, img, centre_x, top, size, fill, shadow=True):
        """Draws the text centred on centre_x with its ink starting at top; returns the ink's bottom."""
        font = self.font(size)
        b = self.bbox(size)
        x = centre_x - (b[0] + b[2]) / 2
        y = top - b[1]
        if shadow:
            layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
            ImageDraw.Draw(layer).text((x, y + 2), self.shown, font=font, fill=(10, 8, 30, 150), **self.kwargs)
            img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(4)))
        ImageDraw.Draw(img).text((x, y), self.shown, font=font, fill=fill, **self.kwargs)
        return top + (b[3] - b[1])


def wrap_two(text):
    """Splits a tagline into two lines at the space (or comma) nearest its middle."""
    words = text.split(" ")
    if len(words) < 2:
        return [text]
    best = min(range(1, len(words)), key=lambda i: abs(len(" ".join(words[:i])) - len(" ".join(words[i:]))))
    return [" ".join(words[:best]), " ".join(words[best:])]


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sky(w, h, horizon):
    """The dusk sky: top to mid to the horizon glow, the lake below it, a few stars and a crescent."""
    column = Image.new("RGBA", (1, h))
    for y in range(h):
        if y < horizon:
            t = y / horizon
            c = lerp(SKY_TOP, SKY_MID, min(1, t / 0.62)) if t < 0.62 else lerp(SKY_MID, SKY_GLOW, ((t - 0.62) / 0.38) ** 1.6)
        else:
            c = lerp(lerp(SKY_GLOW, SKY_LOW, 0.55), SKY_LOW, min(1, (y - horizon) / (h - horizon) * 1.4))
        column.putpixel((0, y), c + (255,))
    img = column.resize((w, h), Image.NEAREST)
    # A warm glow above the horizon behind the mosque.
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((w / 2 - 330, horizon - 150, w / 2 + 330, horizon + 90), fill=SKY_GLOW + (120,))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(70)))
    rnd = random.Random(7)
    stars = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(stars)
    for _ in range(90):
        x, y = rnd.uniform(0, w), rnd.uniform(0, horizon * 0.55)
        r = rnd.choice((0.7, 0.9, 1.1, 1.5))
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, rnd.randint(90, 220)))
    img.alpha_composite(stars)
    crescent(img, w - 150, 92, 20)
    return img


def crescent(img, cx, cy, r):
    halo = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse((cx - r * 3, cy - r * 3, cx + r * 3, cy + r * 3), fill=(220, 230, 255, 70))
    img.alpha_composite(halo.filter(ImageFilter.GaussianBlur(r * 1.2)))
    disc = Image.new("L", img.size, 0)
    ImageDraw.Draw(disc).ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    bite = Image.new("L", img.size, 0)
    ox, oy = r * 0.42, -r * 0.22
    ImageDraw.Draw(bite).ellipse((cx + ox - r * 0.86, cy + oy - r * 0.86, cx + ox + r * 0.86, cy + oy + r * 0.86), fill=255)
    mask = ImageChops.subtract(disc, bite)
    img.paste(Image.new("RGBA", img.size, (0xFF, 0xF2, 0xD0, 255)), (0, 0), mask)


def ridge(img, base, amp, seed, fill):
    w, h = img.size
    r = random.Random(seed)
    waves = [(1 + r.randrange(3), r.uniform(0, math.tau), 0.4 + r.random() * 0.6) for _ in range(3)]
    pts = [(0, h)]
    for i in range(201):
        u = i / 200
        v = sum(a * math.sin(math.tau * k * u + ph) for k, ph, a in waves)
        pts.append((u * w, base - amp * (0.6 + 0.4 * v / 2)))
    pts.append((w, h))
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).polygon(pts, fill=fill)
    img.alpha_composite(layer)


def skyline(img, x, bottom, w, h, fill):
    """The mosque silhouette of sky.js (MosqueSkyline in the iOS app) in a w x h box standing on bottom."""
    s = 4  # drawn larger, then scaled down for smooth curves
    layer = Image.new("RGBA", (int(w * s), int(h * s)), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    def p(u, v):
        return (u * w * s, v * h * s)

    def rect(u, v, uw, vh):
        a, b = p(u, v)
        d.rectangle((a, b, a + uw * w * s, b + vh * h * s), fill=fill)

    def bezier(p0, p1, p2, p3, n=40):
        return [tuple((1 - t) ** 3 * p0[i] + 3 * (1 - t) ** 2 * t * p1[i] + 3 * (1 - t) * t ** 2 * p2[i] + t ** 3 * p3[i] for i in range(2))
                for t in (j / n for j in range(n + 1))]

    def dome(cx, hw, base, top):
        left = bezier(p(cx - hw, base), p(cx - hw * 1.05, base - (base - top) * 0.55), p(cx - hw * 0.45, top + (base - top) * 0.2), p(cx, top))
        right = bezier(p(cx, top), p(cx + hw * 0.45, top + (base - top) * 0.2), p(cx + hw * 1.05, base - (base - top) * 0.55), p(cx + hw, base))
        d.polygon(left + right[1:], fill=fill)
        rect(cx - 0.004, top - 0.08, 0.008, 0.09)

    rect(0.16, 0.66, 0.68, 0.34)
    dome(0.5, 0.17, 0.68, 0.2)
    dome(0.27, 0.08, 0.7, 0.46)
    dome(0.73, 0.08, 0.7, 0.46)
    for u in (0.06, 0.9):
        rect(u, 0.12, 0.04, 0.88)
        rect(u - 0.008, 0.34, 0.056, 0.03)
        d.polygon([p(u, 0.12), p(u + 0.02, 0.0), p(u + 0.04, 0.12)], fill=fill)
    layer = layer.resize((int(w), int(h)), Image.LANCZOS)
    img.alpha_composite(layer, (int(x), int(bottom - h)))


def rounded_icon(src, size, radius):
    icon = Image.open(src).convert("RGBA").resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius * 4, fill=255)
    icon.putalpha(mask.resize((size, size), Image.LANCZOS))
    return icon


def feature_graphic(lang, name, phrase, icon_src, problems):
    horizon = 432
    img = sky(FW, FH, horizon)
    ridge(img, horizon + 6, 34, 3, lerp(SKY_MID, SKY_LOW, 0.55) + (255,))
    ridge(img, horizon + 14, 22, 11, lerp(SKY_LOW, (0, 0, 0), 0.25) + (255,))
    mosque_h = 84
    skyline(img, FW / 2 - mosque_h * 1.1, horizon + 16, mosque_h * 2.2, mosque_h, lerp(SKY_LOW, (0, 0, 0), 0.4) + (255,))
    # The lake: the lower edge a little darker.
    lake = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    ImageDraw.Draw(lake).rectangle((0, horizon + 16, FW, FH), fill=lerp(SKY_LOW, (0, 0, 0), 0.3) + (255,))
    img.alpha_composite(lake)

    texts = [Text(name, lang, True, problems)]
    lines = []
    if phrase:
        t = Text(phrase, lang, False, problems)
        max_w = SAFE[2] - SAFE[0] - 40
        size = t.fit(36, 30, max_w)
        if t.width(size) > max_w:
            lines = [Text(part, lang, False, problems) for part in wrap_two(phrase)]
            size = min(line.fit(34, 22, max_w) for line in lines)
        else:
            lines = [t]
        phrase_size = size
    title = texts[0]
    # Nastaliq stands much taller than other scripts at the same size.
    title_start = (64 if phrase else 72) - (14 if title.script == "URDU" else 0)
    title_size = title.fit(title_start, 34, SAFE[2] - SAFE[0] - 40)

    # The block (icon, name, tagline) centred vertically in the space above the skyline.
    icon_size = 128 if phrase else 146
    gap1, gap2 = 22, 16
    title_h = title.bbox(title_size)[3] - title.bbox(title_size)[1]
    line_hs = [ln.bbox(phrase_size)[3] - ln.bbox(phrase_size)[1] for ln in lines] if lines else []
    line_gap = 10
    text_h = title_h + (gap2 + sum(line_hs) + line_gap * (len(lines) - 1) if lines else 0)
    # Between the top of the safe area and the minarets' tips; a tall script (Nastaliq) gets a smaller icon.
    room = (horizon + 16 - mosque_h - 14) - SAFE[1]
    icon_size = max(96, min(icon_size, room - gap1 - text_h))
    block = icon_size + gap1 + text_h
    top = SAFE[1] + max(0, (room - block) / 2)

    shadow = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
    ix = (FW - icon_size) // 2
    ImageDraw.Draw(shadow).rounded_rectangle((ix, top + 8, ix + icon_size, top + 8 + icon_size), icon_size * 0.22, fill=(8, 6, 25, 140))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(12)))
    img.alpha_composite(rounded_icon(icon_src, icon_size, int(icon_size * 0.22)), (ix, int(top)))
    y = top + icon_size + gap1
    y = title.draw(img, FW / 2, y, title_size, (255, 255, 255, 255))
    if lines:
        y += gap2
        for ln, h in zip(lines, line_hs):
            ln.draw(img, FW / 2, y, phrase_size, GOLD_LIGHT + (255,))
            y += h + line_gap
        y -= line_gap
    if y > horizon + 16 - mosque_h - 6:
        problems.append(f"feature: the text reaches y={y:.0f}, into the skyline")
    for t in texts + lines:
        size = title_size if t is title else phrase_size
        if t.width(size) > SAFE[2] - SAFE[0]:
            problems.append(f"feature: '{t.text}' is wider than the safe area")
    return img.convert("RGB")


def check_screenshot(path, ratios=None, smallest=(320, 320)):
    """Problems with one finished screenshot (Google Play: 24-bit, no alpha, 320-3840 px, long side <= 2x short side,
    at most 8 MB; some slots also have a fixed aspect ratio and a larger minimum)."""
    im = Image.open(path)
    w, h = im.size
    out = []
    if im.mode != "RGB":
        out.append(f"{path.name}: {im.mode}, not 24-bit RGB")
    if min(w, h) < 320 or max(w, h) > MAX_SIDE:
        out.append(f"{path.name}: {w}x{h} outside 320-{MAX_SIDE} px")
    if max(w, h) > 2 * min(w, h):
        out.append(f"{path.name}: {w}x{h} long side more than twice the short side")
    if min(w, h) < smallest[0] or max(w, h) < smallest[1]:
        out.append(f"{path.name}: {w}x{h} smaller than {smallest[1]}x{smallest[0]}")
    if ratios and not any(abs(w / h - a / b) < 0.005 for a, b in ratios):
        out.append(f"{path.name}: {w}x{h} not {' or '.join(f'{a}:{b}' for a, b in ratios)}")
    size = path.stat().st_size
    if size > MAX_BYTES:
        out.append(f"{path.name}: {size / 1048576:.1f} MB, more than 8 MB")
    return out


def xr_backdrop():
    """The space an Android XR panel floats in: a calm dusk, far hills on the horizon and still water below it. Plain
    shapes in the app's own colours, softened, so the panel stays what the eye goes to."""
    w, h = XW, XH
    horizon = int(h * 0.66)
    top, mid, glow, low = lerp(SKY_TOP, (0x22, 0x26, 0x38), 0.35), lerp(SKY_MID, (0x3A, 0x3A, 0x4A), 0.35), \
        lerp(SKY_GLOW, (0xC8, 0xA0, 0x8C), 0.35), lerp(SKY_LOW, (0x1A, 0x1C, 0x26), 0.3)
    column = Image.new("RGBA", (1, h))
    for y in range(h):
        if y < horizon:
            t = y / horizon
            c = lerp(top, mid, min(1, t / 0.6)) if t < 0.6 else lerp(mid, glow, ((t - 0.6) / 0.4) ** 1.8)
        else:
            u = (y - horizon) / (h - horizon)
            c = lerp(lerp(glow, low, 0.5), lerp(low, (0, 0, 0), 0.35), min(1, u ** 0.7))
        column.putpixel((0, y), c + (255,))
    img = column.resize((w, h), Image.NEAREST)
    haze = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(haze).ellipse((w * 0.18, horizon - h * 0.2, w * 0.82, horizon + h * 0.08), fill=glow + (90,))
    img.alpha_composite(haze.filter(ImageFilter.GaussianBlur(220)))
    rnd = random.Random(5)
    stars = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(stars)
    for _ in range(160):
        x, y = rnd.uniform(0, w), rnd.uniform(0, horizon * 0.5)
        r = rnd.choice((1.4, 1.8, 2.2, 2.8))
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, rnd.randint(50, 150)))
    img.alpha_composite(stars)
    # Two ranges of far hills, the nearer one darker, then the water, which takes the sky's colour and darkens.
    ridge(img, horizon + 8, 150, 3, lerp(mid, glow, 0.3) + (255,))
    ridge(img, horizon + 22, 80, 11, lerp(low, mid, 0.35) + (255,))
    water = Image.new("RGBA", (1, h), (0, 0, 0, 0))
    for y in range(horizon + 22, h):
        u = (y - horizon - 22) / (h - horizon - 22)
        water.putpixel((0, y), lerp(lerp(lerp(glow, mid, 0.55), low, 0.3), lerp(low, (0, 0, 0), 0.45), u ** 0.6) + (255,))
    img.alpha_composite(water.resize((w, h), Image.NEAREST))
    # A faint shimmer line where the water meets the hills.
    line = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(line).rectangle((w * 0.1, horizon + 24, w * 0.9, horizon + 30), fill=glow + (70,))
    img.alpha_composite(line.filter(ImageFilter.GaussianBlur(10)))
    return img


def xr_scene(app, backdrop):
    """The app window (StoreScreenshotTest's 1024 x 720 dp XR panel) as a panel floating in the backdrop."""
    pw = 2600
    ph = round(pw * app.height / app.width)
    x, y = (XW - pw) // 2, 130
    radius = 80  # about 32 dp at the panel's scale
    img = backdrop.copy()
    panel = app.convert("RGBA").resize((pw, ph), Image.LANCZOS)
    mask = Image.new("L", (pw, ph), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, pw - 1, ph - 1), radius, fill=255)
    # The panel's soft shadow, and its blurred reflection in the water below it.
    shadow = Image.new("RGBA", (XW, XH), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x + 20, y + 50, x + pw - 20, y + ph + 50), radius, fill=(5, 6, 14, 150))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(60)))
    gap = 36
    reflect_h = XH - (y + ph + gap)
    if reflect_h > 0:
        flipped = panel.transpose(Image.FLIP_TOP_BOTTOM).crop((0, 0, pw, reflect_h)).filter(ImageFilter.GaussianBlur(14))
        fade = Image.linear_gradient("L").resize((pw, reflect_h)).point(lambda v: int((255 - v) * 0.22))
        fade = ImageChops.multiply(fade, mask.transpose(Image.FLIP_TOP_BOTTOM).crop((0, 0, pw, reflect_h)))
        flipped.putalpha(fade)
        img.alpha_composite(flipped, (x, y + ph + gap))
    panel.putalpha(mask)
    img.alpha_composite(panel, (x, y))
    # A thin light edge, like glass catching the dusk.
    edge = Image.new("RGBA", (XW, XH), (0, 0, 0, 0))
    ImageDraw.Draw(edge).rounded_rectangle((x, y, x + pw - 1, y + ph - 1), radius, outline=(255, 244, 228, 60), width=3)
    img.alpha_composite(edge)
    return img.convert("RGB")


def flatten(im):
    if im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info):
        im = im.convert("RGBA")
        bg = Image.new("RGB", im.size, (0x0B, 0x16, 0x20))
        bg.paste(im, (0, 0), im)
        return bg
    return im.convert("RGB")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--shots", default=str(ROOT / "android/app/build/store-play"), help="StoreScreenshotTest output")
    ap.add_argument("--out", default=str(ROOT / "build/store-play"))
    ap.add_argument("--languages", default="", help="app language codes, space-separated (default: every enabled one)")
    ap.add_argument("--icon", default=str(ROOT / "shared/brand/app-icon-preview.png"))
    ap.add_argument("--slots", default=" ".join(SLOTS), help="screenshot slots, space-separated (default: all of them)")
    args = ap.parse_args()
    # StoreScreenshotTest's other devices (cover, inner-land, tablet) are for checking layouts, not Play slots.
    wanted_slots = [s for s in args.slots.replace(",", " ").split() if s in SLOTS] or list(SLOTS)
    shots, out = pathlib.Path(args.shots), pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    languages = [l["code"] for l in load_json(ROOT / "shared/i18n/languages.json")["languages"] if l.get("enabled")]
    wanted = args.languages.replace(",", " ").split()
    if wanted:
        unknown = sorted(set(wanted) - set(languages))
        if unknown:
            raise SystemExit(f"not app languages: {' '.join(unknown)}")
        languages = [l for l in languages if l in wanted]
    app_name = load_json(ROOT / "shared/i18n/strings.json")["strings"]["app_name"]
    phrases = load_json(ROOT / "shared/store/creative.json").get("phrase", {})

    failed = False
    backdrop = xr_backdrop() if "xr" in wanted_slots else None
    manifest = {"_about": "Google Play graphics made by tools/store/play_graphics.py: paths relative to this folder.",
                "layout": "raqm" if RAQM else "basic", "icon": None, "languages": {}}

    icon = Image.open(args.icon).convert("RGBA").resize((512, 512), Image.LANCZOS)
    icon.save(out / "icon.png", optimize=True)
    kb = (out / "icon.png").stat().st_size / 1024
    manifest["icon"] = {"file": "icon.png", "size": [512, 512], "kb": round(kb)}
    if kb > 1024:
        print(f"icon.png: {kb:.0f} KB, more than 1024 KB", file=sys.stderr)
        failed = True

    for lang in languages:
        problems = []
        name = app_name.get(lang) or app_name["en"]
        phrase = phrases.get(lang)
        (out / lang).mkdir(parents=True, exist_ok=True)
        feature = feature_graphic(lang, name, phrase, args.icon, problems)
        feature.save(out / lang / "feature.png", optimize=True)

        slots = {}
        warnings = []
        for slot, (play, console, minimum, needed, ratios, smallest) in SLOTS.items():
            if slot not in wanted_slots:
                continue
            sources = sorted((shots / lang / slot).glob("*.png"))
            # Start the slot empty: play_listing.py uploads every PNG in it, so screenshots of an older set
            # (e.g. 02-qibla, 07-iqama) must not stay next to the new ones. Never when it is the source folder.
            if (out / lang / slot).resolve() != (shots / lang / slot).resolve():
                for old in (out / lang / slot).glob("*.png"):
                    old.unlink()
            if not sources and slot != "phone":
                warnings.append(f"{slot}: no screenshots in {shots / lang / slot}")
                continue
            (out / lang / slot).mkdir(parents=True, exist_ok=True)
            files, promo = [], 0
            for src in sources:
                rgb = flatten(Image.open(src))
                if slot == "xr":
                    rgb = xr_scene(rgb, backdrop)
                dst = out / lang / slot / src.name
                rgb.save(dst, optimize=False, compress_level=6)
                problems += [f"{slot}/{i}" for i in check_screenshot(dst, ratios, smallest)]
                w, h = rgb.size
                if min(w, h) >= 1080 and abs(max(w, h) / min(w, h) - 16 / 9) < 0.01:
                    promo += 1
                files.append({"file": f"{lang}/{slot}/{src.name}", "size": [w, h], "kb": round(dst.stat().st_size / 1024)})
            # One file per store position (NN-name.png): a second one with the same number is a leftover of an
            # older set (e.g. 07-iqama next to 07-widgets) and would be uploaded too.
            numbers = [s.name.split("-", 1)[0] for s in sources]
            dupes = sorted({n for n in numbers if numbers.count(n) > 1})
            if dupes:
                problems.append(f"{slot}: more than one screenshot numbered {', '.join(dupes)} in {shots / lang / slot} (left from an older set?)")
            if len(files) > MAX_SHOTS:
                problems.append(f"{len(files)} {slot} screenshots; Google Play takes at most {MAX_SHOTS}")
            if len(files) < minimum:
                problems.append(f"{len(files)} {slot} screenshots; at least {minimum} are needed")
            if promo < needed:
                warnings.append(f"{slot}: {promo} screenshots of 16:9 or 9:16 at 1080 px or more ({needed} are recommended for promotion)")
            slots[slot] = {"play": play, "console": console, "files": files}
        manifest["languages"][lang] = {
            "appName": name, "tagline": phrase, "feature": f"{lang}/feature.png",
            **{slot: v["files"] for slot, v in slots.items()},
            # Uploaded through the Google Play Developer API (tools/store/play_listing.py).
            "playSlots": {slot: v["play"] for slot, v in slots.items() if v["play"]},
            # No API image type: added by hand in Play Console (Main store listing, or the language's translation).
            "consoleSlots": {slot: v["console"] for slot, v in slots.items() if not v["play"]},
            "problems": problems, "warnings": warnings,
        }
        counts = ", ".join(f"{len(v['files'])} {slot}" for slot, v in slots.items())
        status = "ok" if not problems else "PROBLEMS"
        print(f"{lang}: feature.png, screenshots: {counts}, {status}" + "".join(f"\n  - {p}" for p in problems + warnings))
        failed |= bool(problems)

    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{out}/manifest.json ({len(languages)} languages, text layout: {manifest['layout']})")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
