#!/usr/bin/env python3
"""Check shared/design/tokens.json: WCAG contrast of text on the header skies and key surfaces (also with
every "Sky follows the weather" veil mixed in), and that the Android (Theme.kt) and iOS (Theme.swift,
Sky.swift) themes use exactly the token colours (the weather sky files only token colours)."""
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def lum(h):
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    c = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def ratio(a, b):
    la, lb = sorted([lum(a), lum(b)], reverse=True)
    return (la + 0.05) / (lb + 0.05)


def main():
    t = json.load(open(os.path.join(ROOT, "shared/design/tokens.json")))
    col = {k: v.lstrip("#").upper() for k, v in t["color"].items()}
    errors = []
    checks = [
        ("navy text on card", col["navy"], col["cardLight"], 4.5),
        ("gold text on card", col["goldText"], col["cardLight"], 4.5),
        ("primary text light", col["textLight"], col["surfaceLight"], 4.5),
        ("secondary text light", col["textSecondaryLight"], col["cardLight"], 4.5),
        ("primary text dark", col["textDark"], col["cardDark"], 4.5),
        ("secondary text dark", col["textSecondaryDark"], col["cardDark"], 4.5),
        ("gold on dark card", col["gold"], col["cardDark"], 4.5),
    ]
    def blend(a, b, f):
        return "".join("%02X" % round(int(a[i:i + 2], 16) * (1 - f) + int(b[i:i + 2], 16) * f) for i in (0, 2, 4))

    # Sky follows the weather: every stop mixed toward the condition's veil (also at the lighter showers mix),
    # the horizon glow scaled. "clear" (mix 0) is today's sky.
    w = t.get("weather", {})
    variants = [("", None, 0.0, 1.0)]
    for cond, mix in w.get("mix", {}).items():
        for label, m in ((cond, mix), (f"{cond} showers", mix * w.get("showersMix", 1.0))):
            if m > 0:
                variants.append((f" {label}", cond, m, w["glow"][cond]))

    def veiled(sky, veil, cond, m):
        s = {k: v.lstrip("#").upper() for k, v in sky.items()}
        if cond is None:
            return s
        v = veil[cond].lstrip("#").upper()
        return {k: blend(c, v, m) for k, c in s.items()}

    for name, sky in t["sky"].items():
        if name.startswith("_"):
            continue
        for label, cond, m, g in variants:
            s = veiled(sky, w.get("veilDark", {}), cond, m)
            for stop in ("top", "mid", "low"):
                checks.append((f"white on sky {name}{label} {stop}", "FFFFFF", s[stop], 4.5))
            checks.append((f"white on sky {name}{label} mid + 20% glow", "FFFFFF", blend(s["mid"], s["glow"], 0.2 * g), 4.5))
            checks.append((f"white on sky {name}{label} low + 25% glow", "FFFFFF", blend(s["low"], s["glow"], 0.25 * g), 4.5))
    for name, sky in t.get("skyLight", {}).items():
        if name.startswith("_"):
            continue
        for label, cond, m, g in variants:
            s = veiled(sky, w.get("veilLight", {}), cond, m)
            for ink in ("textLight", "goldInk", "textSecondaryLight"):
                for stop in ("top", "mid", "low"):
                    checks.append((f"{ink} on light sky {name}{label} {stop}", col[ink], s[stop], 4.5))
                checks.append((f"{ink} on light sky {name}{label} mid + 20% glow", col[ink], blend(s["mid"], s["glow"], 0.2 * g), 4.5))
                checks.append((f"{ink} on light sky {name}{label} low + 25% glow", col[ink], blend(s["low"], s["glow"], 0.25 * g), 4.5))
    for name, a, b, need in checks:
        r = ratio(a, b)
        if r < need:
            errors.append(f"{name}: {r:.2f} < {need}")

    sky_hex = {c.lstrip("#").upper() for group in ("sky", "skyLight") for k, v in t.get(group, {}).items() if not k.startswith("_") for c in v.values()}
    kt = open(os.path.join(ROOT, "android/app/src/main/kotlin/sa/zood/nearmosque/ui/theme/Theme.kt")).read()
    sw = "".join(open(os.path.join(ROOT, "ios/App/Support", f)).read() for f in ("Theme.swift", "Sky.swift"))
    used_kt = {m.upper() for m in re.findall(r"0xFF([0-9A-Fa-f]{6})", kt)}
    used_sw = {m.upper() for m in re.findall(r"hex: 0x([0-9A-Fa-f]{6})", sw)}
    allowed = set(col.values()) | sky_hex | {"8CC0DE", "F2B8B5", "FFFFFF"}  # dark-mode secondary/error accents
    for label, used in (("Theme.kt", used_kt), ("Theme.swift + Sky.swift", used_sw)):
        stray = used - allowed
        if stray:
            errors.append(f"{label} uses colours not in tokens.json: {sorted(stray)}")
    for label, used in (("Theme.kt", used_kt), ("Theme.swift + Sky.swift", used_sw)):
        if sky_hex - used:
            errors.append(f"{label} is missing sky colours: {sorted(sky_hex - used)}")
    # The weather sky files (once they exist) may use only token colours, the weather ones included.
    weather_hex = {c.lstrip("#").upper() for v in w.values() if not isinstance(v, (int, float))
                   for c in (v.values() if isinstance(v, dict) else [v]) if isinstance(c, str) and c.startswith("#")}
    for rel in ("ios/App/Support/WeatherSky.swift", "android/app/src/main/kotlin/sa/zood/nearmosque/ui/glass/WeatherSky.kt",
                "windows/src/main/kotlin/sa/zood/nearmosque/desktop/SkyStrip.kt"):
        path = os.path.join(ROOT, rel)
        if not os.path.exists(path):
            continue
        text = open(path).read()
        used = {m.upper() for m in re.findall(r"0xFF([0-9A-Fa-f]{6})", text)} | {m.upper() for m in re.findall(r"hex: 0x([0-9A-Fa-f]{6})", text)}
        stray = used - allowed - weather_hex
        if stray:
            errors.append(f"{rel} uses colours not in tokens.json: {sorted(stray)}")
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    print(f"{len(checks)} contrast checks passed; theme colours match tokens")


if __name__ == "__main__":
    main()
