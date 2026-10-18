#!/usr/bin/env python3
"""Builds the website's assets (website/assets): the globe's land dots and city lights, the store screenshots
in English and Arabic as WebP, and short clips of the app preview recordings.

    build_assets.py <screenshots root> <recordings root>

<screenshots root>/<en|ar>/<iphone|ipad|mac|tv|vision|watch>/NN-name.png (the App Store screenshots) and
<en|ar>/android/NN-name.png (Android phone screenshots, from android/app/build/screenshots);
<recordings root>/<en|ar>/01-header.mp4, 03-search.mp4 (store-previews.yml).

Earth: NASA Blue Marble Next Generation (December 2004) and Black Marble 2016 (city lights), both public domain,
resized into assets/earth-day.jpg and earth-night.jpg (done once, by hand). Land: Natural Earth 1:110m land (public domain) as world-atlas TopoJSON (tools/site/land-110m.json), drawn on an
equirectangular grid and sampled on an even spread of points over the sphere. Cities: packs/cities/cities.tsv
(GeoNames, CC BY 4.0), the most populous ones.
"""
import json
import math
import pathlib
import subprocess
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "website" / "assets"


def land_mask(w=4096, h=2048):
    topo = json.loads((ROOT / "tools/site/land-110m.json").read_text())
    (sx, sy), (tx, ty) = topo["transform"]["scale"], topo["transform"]["translate"]
    arcs = []
    for arc in topo["arcs"]:
        x = y = 0
        pts = []
        for dx, dy in arc:
            x += dx; y += dy
            pts.append((x * sx + tx, y * sy + ty))
        arcs.append(pts)

    def ring(ids):
        pts = []
        for i in ids:
            a = arcs[i] if i >= 0 else arcs[~i][::-1]
            pts += a if not pts else a[1:]
        return pts
    img = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(img)
    px = lambda p: ((p[0] + 180) / 360 * w, (90 - p[1]) / 180 * h)
    for geom in topo["objects"]["land"]["geometries"]:
        polys = geom["arcs"] if geom["type"] == "MultiPolygon" else [geom["arcs"]]
        for poly in polys:
            for k, r in enumerate(poly):
                d.polygon([px(p) for p in ring(r)], fill=255 if k == 0 else 0)
    return img


def land_dots(n=90000):
    mask = land_mask()
    w, h = mask.size
    m = mask.load()
    golden = math.pi * (3 - math.sqrt(5))
    out = []
    for i in range(n):
        y = 1 - 2 * (i + 0.5) / n
        lat = math.degrees(math.asin(y))
        lon = math.degrees((golden * i) % (2 * math.pi)) - 180
        if lat < -60:  # no Antarctica: it crowds the bottom of the globe
            continue
        if m[min(w - 1, int((lon + 180) / 360 * w)), min(h - 1, int((90 - lat) / 180 * h))]:
            out.append((round(lat * 100), round(lon * 100)))
    return out


def cities():
    """Every built-in city (they light up at their prayer times), and for each time zone its most populous city
    (where the page places a visitor, from the browser's time zone)."""
    rows = []
    for line in (ROOT / "packs/cities/cities.tsv").read_text().splitlines():
        f = line.split("\t")
        rows.append((int(f[8] or 0), float(f[5]), float(f[6]), f[7], f[2]))
    rows.sort(reverse=True)
    zones = {}
    for pop, la, lo, tz, en in rows:
        zones.setdefault(tz, [round(la, 3), round(lo, 3), en])
    return [(round(la * 100), round(lo * 100), min(255, int(math.log10(max(pop, 1e4)) * 30))) for pop, la, lo, *_ in rows], zones


def main():
    shots, recs = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    OUT.mkdir(parents=True, exist_ok=True)
    dots = land_dots()
    # Flat lists of whole numbers: latitude and longitude in hundredths of a degree (cities add a size, 0 to 255).
    (OUT / "land.json").write_text(json.dumps([v for d in dots for v in d], separators=(",", ":")))
    cs, zones = cities()
    (OUT / "cities.json").write_text(json.dumps([v for c in cs for v in c], separators=(",", ":")))
    (OUT / "zones.json").write_text(json.dumps(zones, ensure_ascii=False, separators=(",", ":")))
    print(f"land {len(dots)} dots, cities {len(cs)}")
    widths = {"iphone": 640, "android": 640, "ipad": 1100, "mac": 1200, "tv": 1200, "vision": 1200, "watch": 360}
    for lang in ("en", "ar"):
        for dev, wd in widths.items():
            src = shots / lang / dev
            if not src.exists():
                continue
            dst = OUT / lang / dev
            dst.mkdir(parents=True, exist_ok=True)
            for f in sorted(src.glob("*.png")):
                im = Image.open(f).convert("RGB")
                im = im.resize((wd, round(im.height * wd / im.width)), Image.LANCZOS)
                im.save(dst / (f.stem + ".webp"), "WEBP", quality=80, method=6)
        for clip in ("01-header", "03-search"):
            src = recs / lang / f"{clip}.mp4"
            if src.exists():
                (OUT / lang / "clips").mkdir(parents=True, exist_ok=True)
                subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", "-i", str(src), "-an", "-vf", "scale=432:-2,fps=30",
                                "-c:v", "libx264", "-profile:v", "main", "-crf", "28", "-preset", "slow", "-pix_fmt", "yuv420p",
                                "-movflags", "+faststart", str(OUT / lang / "clips" / f"{clip}.mp4")], check=True)
                # WebM too, for browsers without H.264.
                subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", "-i", str(OUT / lang / "clips" / f"{clip}.mp4"), "-an",
                                "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "40", "-row-mt", "1", str(OUT / lang / "clips" / f"{clip}.webm")], check=True)
    print("done")


if __name__ == "__main__":
    main()
