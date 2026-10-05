"""Logo options: the traced emblem (emblem.py) with the navigation arrow combined into it.
python3 options.py writes option-*.svg and options.html (each option at 340, 120, 60 and 40 px).

2: the angled arrow, twice the earlier size, low in the cube and behind it: the cube's lines run over
   the arrow (they turn light where they cross it).
3: the dome's crown is the arrow: concave sides like the tip of the green dome, its base is the
   roof's top edges and its notch is the top point where the ribs begin."""
from emblem import emblem, T, L, INK

NAVY, GOLD, DEEP_GOLD, CREAM, NIGHT = "#1F4E79", "#E7B65A", "#C8922E", "#FFF8EC", "#0E2238"
LIGHT = ('<linearGradient id="{g}" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="1024" y2="1024">'
         '<stop offset="0" stop-color="#E8B868"/><stop offset="0.5" stop-color="#F6DFB2"/><stop offset="1" stop-color="#FFF9EF"/>'
         '</linearGradient>')
DARK = ('<linearGradient id="{g}" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="1024" y2="1024">'
        f'<stop offset="0" stop-color="#173A5E"/><stop offset="1" stop-color="{NIGHT}"/></linearGradient>')
WEIGHT = dict(thin=21, rib_w=19, band=60, course=22)


def place(scale, cy):
    """Trace space (centre x 400) into the 1024 icon, trace point (400, cy) at the icon centre."""
    return f"translate({512 - 400 * scale:.1f} {512 - cy * scale:.1f}) scale({scale})"


def icon(key, inner, dark=False, scale=0.9, cy=405, defs=""):
    g = f"bg{key}"
    grad = (DARK if dark else LIGHT).format(g=g)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"><defs>{grad}{defs}</defs>'
            f'<rect width="1024" height="1024" fill="url(#{g})"/><g transform="{place(scale, cy)}">{inner}</g></svg>')


# ---- Option 2: the arrow behind the cube --------------------------------------------------------
ARROW = "M0 -60 L44 52 L0 30 L-44 52 Z"          # tip up, 112 tall
FACET = "M0 -60 L44 52 L0 30 Z"                   # its right half, lit
ARROW2 = dict(x=372, y=528, h=360, angle=32)       # about twice the first round's 184, low in the cube, inside its walls


def arrow_tf(x, y, h, angle):
    return f"translate({x} {y}) rotate({angle}) scale({h / 112:.4f})"


def behind(key, dark=False):
    tf = arrow_tf(**ARROW2)
    body, facet = (CREAM, GOLD) if dark else (NAVY, GOLD)
    ink, over = (GOLD, NIGHT) if dark else (INK, CREAM)
    clip = f'<clipPath id="a{key}"><path transform="{tf}" d="{ARROW}" stroke-linejoin="round"/></clipPath>'
    inner = (f'<g transform="{tf}"><path d="{ARROW}" fill="{body}" stroke="{body}" stroke-width="3" stroke-linejoin="round"/>'
             f'<path d="{FACET}" fill="{facet}"/></g>'
             f'{emblem(**WEIGHT, ink=ink)}'
             f'<g clip-path="url(#a{key})">{emblem(**WEIGHT, ink=over)}</g>')
    return icon(key, inner, dark, defs=clip)


# ---- Option 3: the dome's crown is the arrow --------------------------------------------------------
TIP = (400, -165)
WING = 165                       # wing half-width; the wings sit on the roof's top edges
SLOPE = (T[1] - L[1]) / (T[0] - L[0])


def crown_paths():
    wl = (T[0] - WING, T[1] - SLOPE * WING)                 # on the left top edge
    wr = (T[0] + WING, wl[1])
    # Concave sides, like the pointed tip of the green dome: they leave the tip steeply and flare out.
    cl = (T[0] - 48, wl[1] - 95)
    cr = (T[0] + 48, cl[1])
    half_l = f"M{TIP[0]} {TIP[1]} Q{cl[0]} {cl[1]} {wl[0]:.1f} {wl[1]:.1f} L{T[0]} {T[1]} Z"
    half_r = f"M{TIP[0]} {TIP[1]} Q{cr[0]} {cr[1]} {wr[0]:.1f} {wr[1]:.1f} L{T[0]} {T[1]} Z"
    return half_l, half_r


def crown(key, dark=False):
    half_l, half_r = crown_paths()
    shade, lit = (CREAM, GOLD) if dark else (NAVY, DEEP_GOLD)
    ink = GOLD if dark else INK
    arrow = (f'<path d="{half_l}" fill="{shade}" stroke="{shade}" stroke-width="4" stroke-linejoin="round"/>'
             f'<path d="{half_r}" fill="{lit}" stroke="{lit}" stroke-width="4" stroke-linejoin="round"/>')
    return icon(key, emblem(**WEIGHT, ink=ink) + arrow, dark, scale=0.8, cy=305)


OPTIONS = [("2-light", "2 · Arrow behind the cube", lambda: behind("2l")),
           ("2-dark", "2 · Arrow behind the cube, dark", lambda: behind("2d", dark=True)),
           ("3-light", "3 · The dome's crown is the arrow", lambda: crown("3l")),
           ("3-dark", "3 · The dome's crown is the arrow, dark", lambda: crown("3d", dark=True))]

if __name__ == "__main__":
    import glob, os
    for old in glob.glob("option-*.svg"):
        os.remove(old)
    html = '<html><body style="margin:0;background:#1d2128;font-family:-apple-system,Helvetica,sans-serif;color:#eee;width:1040px">'
    for key, label, f in OPTIONS:
        s = f()
        open(f"option-{key}.svg", "w").write(s)
        html += (f'<div style="display:inline-block;width:480px;margin:18px 16px;text-align:center;vertical-align:top">'
                 f'<div style="width:340px;height:340px;margin:auto;border-radius:76px;overflow:hidden;box-shadow:0 8px 24px rgba(0,0,0,.35)">'
                 f'{s.replace("<svg ", "<svg width=340 height=340 ")}</div><div style="margin:12px 0 8px;font-size:21px">{label}</div>')
        for px in (120, 60, 40):
            html += (f'<div style="display:inline-block;margin:6px;width:{px}px;height:{px}px;border-radius:{px*0.225}px;overflow:hidden;'
                     f'vertical-align:middle">{s.replace("<svg ", f"<svg width={px} height={px} ")}</div>')
        html += '</div>'
    open("options.html", "w").write(html + "</body></html>")
