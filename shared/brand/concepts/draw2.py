"""Second round: the arrow inside the customer's emblem (same gold square). python3 draw2.py"""
import math
from draw import BG, INK

NAVY, GOLD, CREAM = "#1F4E79", "#F2C35B", "#FFF8EC"

def geom(cx=512, top=250, w=480, h=600, sw=28):
    d = w * 0.2
    return dict(cx=cx, x0=cx - w/2, x1=cx + w/2, w=w, d=d, yt=top, ym=top + d, yl=top + 2*d, yb=top + h, foot=top + h - d, sw=sw, g=sw * 1.35)

def block(G, arches=4, roof_fill="none", ink=INK, inner_gap=0):
    cx, x0, x1, w, d, yt, ym, yl, yb, foot, sw, g = (G[k] for k in "cx x0 x1 w d yt ym yl yb foot sw g".split())
    s = [f'<path d="M{cx} {yt} L{x1} {ym} L{cx} {yl} L{x0} {ym} Z" fill="{roof_fill}" stroke="{ink}" stroke-width="{sw}" stroke-linejoin="round"/>',
         f'<path d="M{x0} {ym+g} L{cx} {yl+g} L{x1} {ym+g}" fill="none" stroke="{ink}" stroke-width="{sw*1.6}" stroke-linejoin="round"/>']
    crown_first, crown_last = yl + g + sw * 1.1, foot - d * 0.75
    drop = d * 0.72
    for i in range(arches):
        c = crown_first + i * (crown_last - crown_first) / max(1, (4 - 1))
        e = min(c + drop, foot)
        s.append(f'<path d="M{x0:.1f} {e:.1f} C{x0:.1f} {c+drop*0.12:.1f}, {cx-w*0.24:.1f} {c:.1f}, {cx} {c:.1f} C{cx+w*0.24:.1f} {c:.1f}, {x1:.1f} {c+drop*0.12:.1f}, {x1:.1f} {e:.1f}" fill="none" stroke="{ink}" stroke-width="{sw*0.74}" stroke-linecap="round"/>')
    s += [f'<path d="M{x0} {ym+g} L{x0} {foot}" stroke="{ink}" stroke-width="{sw}" stroke-linecap="round"/>',
          f'<path d="M{x1} {ym+g} L{x1} {foot}" stroke="{ink}" stroke-width="{sw}" stroke-linecap="round"/>',
          f'<path d="M{x0} {foot} L{cx} {yb} L{x1} {foot}" fill="none" stroke="{ink}" stroke-width="{sw}" stroke-linejoin="round" stroke-linecap="round"/>']
    return "\n".join(s)

ARROW = "M0 -60 L44 52 L0 30 L-44 52 Z"          # navigation arrow pointing up (local units)
FACET = "M0 -32 L21 27 L0 17 Z"

def arrow(x, y, size, angle=45, fill=NAVY, inner=GOLD, halo=CREAM, halo_w=10):
    k = size / 100
    halo_attr = f' stroke="{halo}" stroke-width="{halo_w}" stroke-linejoin="round"' if halo else ""
    facet = f'<path d="{FACET}" fill="{inner}"/>' if inner else ""
    return f'<g transform="translate({x:.1f} {y:.1f}) rotate({angle}) scale({k})"><path d="{ARROW}" fill="{fill}"{halo_attr}/>{facet}</g>'

def roof_arrow(G, scale=0.62, fill=NAVY, inner=GOLD, halo=None):
    """Arrow lying on the top face in the same isometric view, pointing to the far-right corner."""
    cx, ym, w, d = G["cx"], G["ym"], G["w"], G["d"]
    # Local square (-100..100) mapped onto the rhombus: x -> along the face width, y -> along its depth.
    a, dd = w / 200 * scale, 2 * d / 200 * scale
    m = f"matrix({a:.4f} 0 0 {dd:.4f} {cx} {ym})"
    facet = f'<path d="{FACET}" fill="{inner}"/>' if inner else ""
    halo_attr = f' stroke="{halo}" stroke-width="14" stroke-linejoin="round"' if halo else ""
    return f'<g transform="{m}"><g transform="rotate(90)"><path d="{ARROW}" fill="{fill}"{halo_attr}/>{facet}</g></g>'

def svg(body): return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">{BG}{body}</svg>'

def opt1():
    G = geom(); return svg(block(G) + roof_arrow(G, 0.78))

def opt2():
    G = geom(); cx, yl, foot = G["cx"], G["yl"], G["foot"]
    return svg(block(G, arches=4) + arrow(cx, (yl + foot) / 2 + 30, 230, angle=0, halo_w=14))

def opt3():
    G = geom(); cx, x0, x1, w, d, yl, g, foot, sw = (G[k] for k in "cx x0 x1 w d yl g foot sw".split())
    # Three outer domes; the innermost becomes a doorway (mihrab) holding the arrow.
    body = block(G, arches=2)
    door_top, door_w = yl + g + d * 1.15, w * 0.42
    a, b = cx - door_w / 2, cx + door_w / 2
    door = (f'<path d="M{a:.1f} {foot:.1f} L{a:.1f} {door_top+door_w*0.45:.1f} C{a:.1f} {door_top+door_w*0.1:.1f}, {cx-door_w*0.2:.1f} {door_top:.1f}, {cx} {door_top-20:.1f} '
            f'C{cx+door_w*0.2:.1f} {door_top:.1f}, {b:.1f} {door_top+door_w*0.1:.1f}, {b:.1f} {door_top+door_w*0.45:.1f} L{b:.1f} {foot:.1f}" fill="{CREAM}" stroke="{INK}" stroke-width="{sw*0.8}" stroke-linejoin="round"/>')
    return svg(body + door + arrow(cx, door_top + (foot - door_top) * 0.52, 190, angle=0, halo=None))

def opt4():
    G = geom(); return svg(block(G, roof_fill=NAVY) + roof_arrow(G, 0.72, fill=CREAM, inner=GOLD))

def opt5():
    G = geom(); return svg(block(G) + roof_arrow(G, 0.78, fill=INK, inner=None))

def opt6():
    G = geom(); cx, yt = G["cx"], G["yt"]
    dot = f'<circle cx="{cx}" cy="{yt - 70}" r="34" fill="{GOLD}" stroke="{INK}" stroke-width="12"/>'
    return svg(block(G) + roof_arrow(G, 0.78) + dot)

OPTIONS = [("1", "On the roof", opt1), ("2", "At the heart", opt2), ("3", "Mihrab doorway", opt3),
           ("4", "Navy roof, cut-out arrow", opt4), ("5", "One colour", opt5), ("6", "Roof arrow + Qibla dot", opt6)]

if __name__ == "__main__":
    html = '<html><body style="margin:0;background:#20242b;font-family:-apple-system,Helvetica,sans-serif;color:#eee;width:1500px">'
    for n, label, f in OPTIONS:
        s = f(); open(f"round2-{n}.svg", "w").write(s)
        html += f'<div style="display:inline-block;width:460px;margin:20px;text-align:center;vertical-align:top"><div style="width:300px;height:300px;margin:auto;border-radius:68px;overflow:hidden">{s.replace("<svg ", "<svg width=300 height=300 ")}</div>'
        html += f'<div style="margin:10px 0;font-size:20px">{n} · {label}</div>'
        for px in (100, 60, 40):
            html += f'<div style="display:inline-block;margin:6px;width:{px}px;height:{px}px;border-radius:{px*0.22}px;overflow:hidden;vertical-align:middle">{s.replace("<svg ", f"<svg width={px} height={px} ")}</div>'
        html += '</div>'
    open("round2.html", "w").write(html + "</body></html>")
