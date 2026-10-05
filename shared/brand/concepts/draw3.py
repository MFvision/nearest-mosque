"""Second round, refined: bigger roof arrows and the roof drawn as the arrow. python3 draw3.py"""
from draw2 import geom, block, arrow, svg, opt2, opt3, INK, NAVY, GOLD, CREAM, FACET

WIDE = "M0 -72 L62 56 L0 26 L-62 56 Z"   # wider arrow for the foreshortened roof


def roof_arrow(G, scale, fill=NAVY, inner=GOLD):
    """Arrow lying on the top face in the same isometric view, pointing to the far-right corner."""
    a, dd = G["w"] / 200 * scale, 2 * G["d"] / 200 * scale
    facet = f'<path d="{FACET}" fill="{inner}"/>' if inner else ""
    return (f'<g transform="matrix({a:.4f} 0 0 {dd:.4f} {G["cx"]} {G["ym"]})"><g transform="rotate(90)">'
            f'<path d="{WIDE}" fill="{fill}"/>{facet}</g></g>')


def roof_is_arrow(G, fill=NAVY, facet=GOLD):
    """The roof drawn as the navigation arrow: point at the back corner, wing tips on the side corners and
    the notch between them, as in the brand arrow."""
    cx, x0, x1, yt, ym, sw = (G[k] for k in "cx x0 x1 yt ym sw".split())
    notch = yt + (ym - yt) * 0.62
    kite = (f'<path d="M{cx} {yt} L{x1} {ym} L{cx} {notch:.1f} L{x0} {ym} Z" fill="{fill}" stroke="{INK}" '
            f'stroke-width="{sw}" stroke-linejoin="round"/>')
    inner = (f'<path d="M{cx} {yt + (ym - yt) * 0.28:.1f} L{cx + (x1 - cx) * 0.5:.1f} {ym - (ym - yt) * 0.08:.1f} '
             f'L{cx} {notch - (ym - yt) * 0.1:.1f} Z" fill="{facet}"/>')
    return kite + inner


def without_roof(G, **kw):
    """The block with its plain top face removed (the arrow-shaped roof is drawn instead)."""
    parts = block(G, **kw).split("\n")
    return "\n".join(parts[1:])


def opt1():
    G = geom(); return svg(block(G) + roof_arrow(G, 1.05))

def opt4():
    G = geom(); return svg(block(G, roof_fill=NAVY) + roof_arrow(G, 0.98, fill=CREAM, inner=GOLD))

def opt5():
    """A large upright arrow through the block; the domes break around it."""
    G = geom(); cx, yl, g, foot, sw = (G[k] for k in "cx yl g foot sw".split())
    gap = (f'<rect x="{cx - 70}" y="{yl + g + sw}" width="140" height="{foot - yl - g - sw}" fill="url(#bg)"/>')
    return svg(block(G) + gap + arrow(cx, (yl + g + foot) / 2 + 18, 300, angle=0, halo=None))


def opt6():
    """Option 3 on a dark tile: navy background, gold lines, cream arrow (also a dark-mode icon)."""
    s = opt3()
    s = s.replace('<rect width="1024" height="1024" fill="url(#bg)"/>', '<rect width="1024" height="1024" fill="#0E2238"/>')
    s = s.replace(f'stroke="{INK}"', 'stroke="#E9B96A"').replace(f'fill="{CREAM}" stroke="#E9B96A"', 'fill="#173A5C" stroke="#E9B96A"')
    s = s.replace(f'<path d="M0 -60 L44 52 L0 30 L-44 52 Z" fill="{NAVY}"', '<path d="M0 -60 L44 52 L0 30 L-44 52 Z" fill="#FFF8EC"')
    return s


OPTIONS = [("1", "Arrow on the roof", opt1), ("2", "Arrow at the heart", opt2), ("3", "Mihrab doorway", opt3),
           ("4", "Navy roof, cut-out arrow", opt4), ("5", "Arrow through the domes", opt5),
           ("6", "Mihrab, dark", opt6)]

if __name__ == "__main__":
    html = '<html><body style="margin:0;background:#20242b;font-family:-apple-system,Helvetica,sans-serif;color:#eee;width:1500px">'
    for n, label, f in OPTIONS:
        s = f(); open(f"round2-{n}.svg", "w").write(s)
        html += (f'<div style="display:inline-block;width:460px;margin:20px;text-align:center;vertical-align:top">'
                 f'<div style="width:300px;height:300px;margin:auto;border-radius:68px;overflow:hidden">{s.replace("<svg ", "<svg width=300 height=300 ")}</div>'
                 f'<div style="margin:10px 0;font-size:20px">{n} · {label}</div>')
        for px in (100, 60, 40):
            html += (f'<div style="display:inline-block;margin:6px;width:{px}px;height:{px}px;border-radius:{px*0.22}px;'
                     f'overflow:hidden;vertical-align:middle">{s.replace("<svg ", f"<svg width={px} height={px} ")}</div>')
        html += '</div>'
    open("round2.html", "w").write(html + "</body></html>")
