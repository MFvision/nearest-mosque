"""Logo options: the traced emblem (emblem.py) with the brand's navigation arrow inside it.
python3 options.py writes option-*.svg and options.html (a sheet with each option at 340, 120, 60 and 40 px)."""
from emblem import emblem, T, B, HIZAM, COURSES, INK

NAVY, GOLD, CREAM, NIGHT = "#1F4E79", "#E7B65A", "#FFF8EC", "#0E2238"
LIGHT = ('<defs><linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="1024" y2="1024">'
         '<stop offset="0" stop-color="#E8B868"/><stop offset="0.5" stop-color="#F6DFB2"/><stop offset="1" stop-color="#FFF9EF"/>'
         '</linearGradient></defs><rect width="1024" height="1024" fill="url(#bg)"/>')
DARK = (f'<defs><linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="1024" y2="1024">'
        f'<stop offset="0" stop-color="#173A5E"/><stop offset="1" stop-color="{NIGHT}"/></linearGradient></defs>'
        '<rect width="1024" height="1024" fill="url(#bg)"/>')
# Trace space (x 35..765, y 40..771) centred in the 1024 icon at about 70% of its width.
SCALE = 0.9
PLACE = f"translate({512 - 400 * SCALE:.1f} {512 - 405 * SCALE:.1f}) scale({SCALE})"
WEIGHT = dict(thin=21, rib_w=19, band=60, course=22)
HEART = (400, (B[1] + HIZAM[1][1] - 30) / 2 + 4)          # the open diamond under the roof's lower point
PANEL = (400, (HIZAM[1][1] + COURSES[0][1][1]) / 2 + 6)   # the panel between the hizam and the first course


def arrow(x, y, h, angle=0.0, fill=NAVY, facet=GOLD, halo=CREAM, halo_w=0.0):
    """The navigation arrow: body and gold inner facet, height h, tip up before rotating by angle degrees."""
    k = h / 112
    ring = f'<path d="M0 -60 L44 52 L0 30 L-44 52 Z" fill="{halo}" stroke="{halo}" stroke-width="{halo_w / k:.1f}" stroke-linejoin="round"/>' if halo_w else ""
    return (f'<g transform="translate({x:.1f} {y:.1f}) rotate({angle}) scale({k:.4f})">{ring}'
            f'<path d="M0 -60 L44 52 L0 30 L-44 52 Z" fill="{fill}" stroke="{fill}" stroke-width="{4 / k:.1f}" stroke-linejoin="round"/>'
            f'<path d="M0 -36 L24 30 L0 18 Z" fill="{facet}"/></g>')


def icon(inner, bg=LIGHT, gid="bg"):
    bg = bg.replace('id="bg"', f'id="{gid}"').replace("url(#bg)", f"url(#{gid})")
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">{bg}<g transform="{PLACE}">{inner}</g></svg>'


def heart():
    return icon(emblem(**WEIGHT) + arrow(400, 352, 178, halo_w=26), gid="g1")


def heart_angled():
    return icon(emblem(**WEIGHT) + arrow(400, 352, 184, angle=32, halo_w=26), gid="g2")


def crown():
    """The arrow is the top of the dome: the ribs spring from under it."""
    return icon(emblem(**WEIGHT) + arrow(400, 128, 205, halo_w=24), gid="g3")


def panel():
    return icon(emblem(**WEIGHT) + arrow(400, 560, 180, halo_w=26), gid="g4")


def golden_hizam():
    base = emblem(**WEIGHT).replace(f'stroke="{INK}" stroke-width="60"', f'stroke="{GOLD}" stroke-width="60"')
    return icon(base + arrow(400, 352, 184, angle=32, halo_w=26), gid="g5")


def dark():
    return icon(emblem(**WEIGHT, ink=GOLD) + arrow(400, 352, 184, angle=32, fill=CREAM, facet=GOLD, halo=NIGHT, halo_w=26), bg=DARK, gid="g6")


OPTIONS = [("1", "Arrow at the heart", heart), ("2", "Arrow at the heart, angled", heart_angled),
           ("3", "Arrow as the dome's crown", crown), ("4", "Arrow in the walls", panel),
           ("5", "Golden hizam, angled arrow", golden_hizam), ("6", "Dark, angled arrow", dark)]

if __name__ == "__main__":
    import glob, os
    for old in glob.glob("option-*.svg"):
        os.remove(old)
    html = '<html><body style="margin:0;background:#1d2128;font-family:-apple-system,Helvetica,sans-serif;color:#eee;width:1560px">'
    for key, label, f in OPTIONS:
        s = f()
        open(f"option-{key}.svg", "w").write(s)
        html += (f'<div style="display:inline-block;width:480px;margin:18px;text-align:center;vertical-align:top">'
                 f'<div style="width:340px;height:340px;margin:auto;border-radius:76px;overflow:hidden;box-shadow:0 8px 24px rgba(0,0,0,.35)">'
                 f'{s.replace("<svg ", "<svg width=340 height=340 ")}</div><div style="margin:12px 0 8px;font-size:21px">{key} · {label}</div>')
        for px in (120, 60, 40):
            html += (f'<div style="display:inline-block;margin:6px;width:{px}px;height:{px}px;border-radius:{px*0.225}px;overflow:hidden;'
                     f'vertical-align:middle">{s.replace("<svg ", f"<svg width={px} height={px} ")}</div>')
        html += '</div>'
    open("options.html", "w").write(html + "</body></html>")
