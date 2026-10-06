# Candidate mosque tab icons (option C installed as iOS MosqueTab and Android ic_tab_mosque).
import subprocess, sys
CHROME = "/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell"
# Candidate mosque glyphs on a 24 grid (filled, even-odd cutouts).
A = ("M12 1.4a0.75 0.75 0 0 1 0.75 0.75v1.1h-1.5v-1.1A0.75 0.75 0 0 1 12 1.4z"          # finial
     "M7.2 11c0-2.9 2.3-4.6 4.8-6.9 2.5 2.3 4.8 4 4.8 6.9z"                               # onion dome
     "M6.2 11.8h11.6v1.4H6.2z"                                                            # drum
     "M6.8 13.6h10.4V21h-3.3v-3.4a1.9 1.9 0 0 0-3.8 0V21H6.8z"                            # hall with arched door
     "M2 21V9.6l1.15-3.2L4.3 9.6V21z M1.5 11.2h3.6v1H1.5z"                                # left minaret + balcony
     "M19.7 21V9.6l1.15-3.2L22 9.6V21z M18.9 11.2h3.6v1h-3.6z"                            # right minaret + balcony
     "M1 21.2h22v1.3H1z")                                                                 # ground
B = ("M12 1.2l0.55 1.1 1.2 0.15-0.88 0.83 0.2 1.2L12 3.9l-1.07 0.58 0.2-1.2-0.88-0.83 1.2-0.15z"  # star finial
     "M5.6 12.4c0-3.6 3-5.6 6.4-8 3.4 2.4 6.4 4.4 6.4 8z"                                 # wide dome
     "M4.6 13.2h14.8V21.5h-4.6v-3.9a2.8 2.8 0 0 0-5.6 0v3.9H4.6z"                         # hall + big arch
     "M1.2 21.5V10.2l1.3-3.4 1.3 3.4v11.3z"
     "M20.2 21.5V10.2l1.3-3.4 1.3 3.4v11.3z")
C = ("M12.9 1.1a1.5 1.5 0 1 0 0.9 2.7 1.2 1.2 0 1 1-0.9-2.7z"                             # crescent finial
     "M11.6 3.5h0.8v1.3h-0.8z"
     "M6.4 11.6c0-3.3 2.6-5.1 5.6-7.1 3 2 5.6 3.8 5.6 7.1z"
     "M5.6 12.4h12.8v1.2H5.6z"
     "M6.2 14.2h11.6v7.3h-3.6v-3.6a2.2 2.2 0 0 0-4.4 0v3.6H6.2z"
     "M1.6 21.5V9.4l1.1-2.9 1.1 2.9v12.1z M1.2 11.4h3v0.9h-3z"
     "M20.2 21.5V9.4l1.1-2.9 1.1 2.9v12.1z M19.8 11.4h3v0.9h-3z")
OLD = "M11.3,1.6h1.4v3h-1.4z M12,5.2c-3.4,1.9 -6.2,4.4 -6.2,7.6V14h12.4v-1.2c0,-3.2 -2.8,-5.7 -6.2,-7.6z M3.5,15h17v6.5h-5.6V18.2a2.9,2.9 0,0 0,-5.8 0v3.3H3.5z"
def svg(d, size, color="#14181F"):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="{size}" height="{size}"><path fill="{color}" fill-rule="evenodd" d="{d}"/></svg>'
cells = ""
for name, d in (("current", OLD), ("A", A), ("B", B), ("C", C)):
    cells += f'<div style="display:inline-block;text-align:center;margin:14px;font:16px sans-serif">{svg(d,160)}<br>{svg(d,28)} {svg(d,28,"#D4A843")}<br>{name}</div>'
open("p.html","w").write(f"<html><body style='margin:0;background:#fff'>{cells}</body></html>")
subprocess.run([CHROME,"--no-sandbox","--hide-scrollbars","--window-size=820,280","--screenshot=p.png","file://"+__import__('os').path.abspath("p.html")],check=True,capture_output=True)
open("paths.txt","w").write(repr(dict(A=A,B=B,C=C)))
