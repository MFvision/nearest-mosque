import math
BG = '''<defs>
<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
 <stop offset="0" stop-color="#E9B96A"/><stop offset="0.45" stop-color="#F6DDAE"/><stop offset="1" stop-color="#FFF8EC"/>
</linearGradient>
</defs>
<rect width="1024" height="1024" fill="url(#bg)"/>'''
INK = "#14181F"

def emblem(cx, top, w, h, sw):
    """Isometric block: top face, thick front edge, sides, base chevron, and nested domes that all rise
    from the base of the sides to crowns at different heights."""
    x0, x1 = cx - w/2, cx + w/2
    d = w * 0.2
    yt, ym, yl = top, top + d, top + 2*d
    yb = top + h
    g = sw * 1.35
    foot = yb - d
    s = [f'<path d="M{cx} {yt} L{x1} {ym} L{cx} {yl} L{x0} {ym} Z" fill="none" stroke="{INK}" stroke-width="{sw}" stroke-linejoin="round"/>',
         f'<path d="M{x0} {ym+g} L{cx} {yl+g} L{x1} {ym+g}" fill="none" stroke="{INK}" stroke-width="{sw*1.6}" stroke-linejoin="round"/>']
    n = 4
    crown_first, crown_last = yl + g + sw * 1.1, foot - d * 0.75
    drop = d * 0.72                       # each arch meets the sides a little below its crown
    for i in range(n):
        c = crown_first + i * (crown_last - crown_first) / (n - 1)
        e = min(c + drop, foot)
        s.append(f'<path d="M{x0:.1f} {e:.1f} C{x0:.1f} {c+drop*0.12:.1f}, {cx-w*0.24:.1f} {c:.1f}, {cx} {c:.1f} C{cx+w*0.24:.1f} {c:.1f}, {x1:.1f} {c+drop*0.12:.1f}, {x1:.1f} {e:.1f}" fill="none" stroke="{INK}" stroke-width="{sw*0.74}" stroke-linecap="round"/>')
    s += [f'<path d="M{x0} {ym+g} L{x0} {foot}" stroke="{INK}" stroke-width="{sw}" stroke-linecap="round"/>',
          f'<path d="M{x1} {ym+g} L{x1} {foot}" stroke="{INK}" stroke-width="{sw}" stroke-linecap="round"/>',
          f'<path d="M{x0} {foot} L{cx} {yb} L{x1} {foot}" fill="none" stroke="{INK}" stroke-width="{sw}" stroke-linejoin="round" stroke-linecap="round"/>']
    return "\n".join(s), (x1, ym)

def arrow(x, y, size, angle=45):
    k = size / 100
    return (f'<g transform="translate({x:.1f} {y:.1f}) rotate({angle}) scale({k})">'
            f'<path d="M0 -60 L44 52 L0 30 L-44 52 Z" fill="#1F4E79" stroke="#FFF8EC" stroke-width="10" stroke-linejoin="round"/>'
            f'<path d="M0 -32 L21 27 L0 17 Z" fill="#F2C35B"/></g>')

def svg(body, bg=True): return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">{BG if bg else ""}{body}</svg>'

def option_d(bg=True):
    body, _ = emblem(cx=488, top=300, w=420, h=560, sw=25)
    cx, cy, r = 488, 575, 360
    a0, a1 = 205, 312
    p = lambda a: (cx + r*math.cos(math.radians(a)), cy + r*math.sin(math.radians(a)))
    (x0, y0), (x1, y1) = p(a0), p(a1)
    arc = f'<path d="M{x0:.1f} {y0:.1f} A{r} {r} 0 0 1 {x1:.1f} {y1:.1f}" fill="none" stroke="#1F4E79" stroke-width="20" stroke-linecap="round" stroke-opacity="0.85"/>'
    ax, ay = p(a1 + 10)
    return svg(arc + body + arrow(ax, ay, 250, angle=a1 + 10 + 90), bg)

def option_e(bg=True):
    body, (rx, ry) = emblem(cx=470, top=300, w=430, h=570, sw=26)
    return svg(body + arrow(rx + 36, ry - 82, 270, angle=45), bg)

if __name__ == "__main__":
    open("icon-D.svg", "w").write(option_d()); open("icon-E.svg", "w").write(option_e())
    html = '<html><body style="margin:0;background:#20242b;font-family:sans-serif;color:#eee">'
    for name, label in [("D", "D · Block + Qibla arc + arrow"), ("E", "E · Arrow fused to the block")]:
        s = open(f"icon-{name}.svg").read()
        html += f'<div style="display:inline-block;margin:24px;text-align:center;vertical-align:top"><div style="width:400px;height:400px;border-radius:90px;overflow:hidden">{s.replace("<svg ", "<svg width=400 height=400 ")}</div><div style="margin:10px 0">{label}</div>'
        for px in (120, 60, 40):
            html += f'<div style="display:inline-block;margin:6px;width:{px}px;height:{px}px;border-radius:{px*0.22}px;overflow:hidden">{s.replace("<svg ", f"<svg width={px} height={px} ")}</div>'
        html += '</div>'
    open("sheet2.html", "w").write(html + "</body></html>")
