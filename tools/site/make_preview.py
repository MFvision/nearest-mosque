#!/usr/bin/env python3
"""Copies website/ into a preview folder for a host that wraps the page itself: the page without its own
<html>/<head>/<body> tags (the other pages keep theirs), and the scripts and stylesheet under names carrying a version, so a browser that
cached an earlier preview loads the new files.

    make_preview.py <out folder> <version>
"""
import json
import pathlib
import re
import shutil
import sys

SITE = pathlib.Path(__file__).resolve().parents[2] / "website"
NAMES = ["app.js", "story.js", "globe.js", "places.js", "sky.js", "style.css", "legal.js", "legal.css"]


def main():
    out, v = pathlib.Path(sys.argv[1]), sys.argv[2]
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(SITE, out, ignore=shutil.ignore_patterns("README.md", "*.LICENSE"))
    ren = {n: f"{pathlib.Path(n).stem}.{v}{pathlib.Path(n).suffix}" for n in NAMES}
    for f in [out / n for n in NAMES] + sorted(out.glob("*.html")):
        s = f.read_text()
        for a, b in ren.items():
            s = re.sub(rf"(['\"(/]|\./){re.escape(a)}(['\")])", lambda m: m.group(1) + b + m.group(2), s)
        if f.name == "index.html":
            for t in ["<!doctype html>\n", '<html lang="en" dir="ltr">\n', "<head>\n", "</head>\n", "<body>\n", "</body>\n", "</html>\n",
                      '<meta charset="utf-8">\n', '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n']:
                s = s.replace(t, "")
        f.write_text(s)
    for a, b in ren.items():
        (out / a).rename(out / b)
    files = sorted(str(p.relative_to(out)) for p in out.rglob("*") if p.is_file() and p.name != "index.html")
    (out.parent / f"{out.name}-files.json").write_text(json.dumps([{"path": p} for p in files]))
    print(len(files), "files;", ", ".join(ren.values()))


if __name__ == "__main__":
    main()
