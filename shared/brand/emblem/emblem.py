"""The customer's emblem, traced from the reference (shared/brand/emblem/reference.png): the Kaaba as the
cube (top face, hizam band, courses) with the ribbed dome of the Prophet's Mosque inside it.
Coordinates follow the 910x840 trace grid; emblem() returns SVG elements in that space."""

INK = "#14181F"

# Trace-space geometry (x 35..765, y 40..770), measured on the thresholded reference.
T, L, R, B = (400, 40), (35, 166), (765, 166), (400, 286)            # top face
HIZAM = ((35, 302), (400, 419), (765, 302))                           # centre line of the thick band
BAND = 58
COURSES = [((35, 428), (400, 544), (765, 428)), ((35, 542), (400, 658), (765, 542)), ((35, 655), (400, 771), (765, 655))]
SIDE_TOP, SIDE_BOTTOM = 250, 655
TRUNK = 205        # the ribs meet on the axis from the top point down to here
# Dome ribs (left half; mirrored for the right), each a cubic fitted to the reference's centre line
# (error 2 to 4 trace units): they leave the axis straight down, sweep out under the roof, cross the
# hizam and swell down the side like the ribs of the green dome, each landing lower than the one before.
RIBS = [
    ((400, 60), (400, 136), (91.5, 204), (35, 250)),
    ((400, 108), (400, 202), (63.6, 245.4), (40, 390)),
    ((400, 157), (400, 259), (53.9, 300.9), (38, 485)),
    ((400, 205), (400, 303), (76.6, 370.7), (46, 540)),
]


def p(pt): return f"{pt[0]:.1f} {pt[1]:.1f}"


def mirror(q): return (800 - q[0], q[1])


def rib(r, side):
    pts = r if side < 0 else [mirror(q) for q in r]
    return f"M{p(pts[0])} C{p(pts[1])}, {p(pts[2])}, {p(pts[3])}"


def emblem(thin=18, rib_w=17, band=BAND, course=19, ink=INK, ribs=True):
    s = []
    stroke = f'fill="none" stroke="{ink}" stroke-linecap="round" stroke-linejoin="round"'
    s.append(f'<path d="M{p(L)} L{p(T)} L{p(R)} L{p(B)} Z" {stroke} stroke-width="{thin}"/>')
    if ribs:
        s.append(f'<path d="M{p(T)} L400 {TRUNK}" {stroke} stroke-width="{rib_w + 2}"/>')
        for r in RIBS:
            for side in (-1, 1):
                s.append(f'<path d="{rib(r, side)}" {stroke} stroke-width="{rib_w}"/>')
    a, b, c = HIZAM
    s.append(f'<path d="M{p(a)} L{p(b)} L{p(c)}" fill="none" stroke="{ink}" stroke-width="{band}" stroke-linejoin="miter" stroke-miterlimit="8"/>')
    for a, b, c in COURSES:
        s.append(f'<path d="M{p(a)} L{p(b)} L{p(c)}" {stroke} stroke-width="{course}"/>')
    for x in (L[0], R[0]):
        s.append(f'<path d="M{x} {SIDE_TOP} L{x} {SIDE_BOTTOM}" {stroke} stroke-width="{thin - 2}"/>')
    return "\n".join(s)


def standalone(extra="", bg=True):
    """The emblem alone on a light ground, in trace space (for comparing with the reference)."""
    ground = '<rect x="-20" y="-20" width="950" height="880" fill="#FBF3E4"/>' if bg else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-20 -20 950 880">{ground}{emblem()}{extra}</svg>'


if __name__ == "__main__":
    open("trace.svg", "w").write(standalone())
