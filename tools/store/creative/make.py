#!/usr/bin/env python3
"""Builds the App Store creative assets (iOS 27) in every store language from the rendered sky loops.

    make.py header <sky 3840x1646.mp4> <ambience.wav> <out folder> [app language codes...]
    make.py search <sky 2880x1920.mp4> <ambience.wav> <phones folder> <out folder> [app language codes...]

header: the product page header (21:9, 3840 x 1646): the living sky with the phrase (shared/store/creative.json).
search: the search results asset (3:2, 2880 x 1920): the sky (mirrored for right-to-left languages, so the sun
travels in the reading direction), the phone with the app's prayer screen floating on one side (its dark-mode
screen while it is night in the sky) and the phrase above the mosque on the other. The phones folder holds
<locale>-light.png and <locale>-dark.png from phone.py.

Both are 20-second seamless loops at 30 fps, H.264 with the soundscape (muted by default on the App Store).
Files land in <out>/<asc locale>.mp4 (the first App Store Connect locale of each language, screens.json).
"""
import json
import os
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SPEC = json.loads((ROOT / "tools/store/screens.json").read_text())
PHRASE = json.loads((ROOT / "shared/store/creative.json").read_text())["phrase"]
RTL = {"ar", "ur"}
ENV = dict(os.environ, PLAYWRIGHT=os.environ.get("PLAYWRIGHT", "playwright"))


def text(out, w, h, lang, size, y, box):
    subprocess.run(["node", str(HERE / "render.mjs"), "text", str(out), str(w), str(h), lang, PHRASE[lang], str(size), str(y), box],
                   check=True, env=ENV)


def encode(args, out):
    subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", *args, "-t", "20", "-r", "30",
                    "-c:v", "libx264", "-preset", "medium", "-crf", "17", "-profile:v", "high", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "256k", "-ar", "48000", "-movflags", "+faststart", str(out)], check=True)


def header(sky, wav, out, langs):
    for lang in langs:
        locale = lang["asc"][0]
        layer = out / f".{locale}.png"
        text(layer, 3840, 1646, lang["app"], 0.07, 0.37, "0.22,0.78")
        encode(["-i", str(sky), "-loop", "1", "-i", str(layer), "-i", str(wav),
                "-filter_complex", "[0:v][1:v]overlay=shortest=1,format=yuv420p[v]", "-map", "[v]", "-map", "2:a"], out / f"{locale}.mp4")
        layer.unlink()
        print(f"header/{locale}.mp4", flush=True)


def search(sky, wav, phones, out, langs):
    for lang in langs:
        locale, rtl = lang["asc"][0], lang["app"] in RTL
        layer = out / f".{locale}.png"
        text(layer, 2880, 1920, lang["app"], 0.085, 0.38, "0.52,0.95" if rtl else "0.05,0.49")
        light, dark = phones / f"{locale}-light.png", phones / f"{locale}-dark.png"
        # The phone: 0.26 or 0.74 of the width, bobbing twice per loop; the dark screen while the sky is at night
        # (the loop starts at Fajr, still dark, and night falls after Maghrib).
        x = f"{0.26 if rtl else 0.74}*W-w/2"
        y = "(H-h)/2+14*sin(2*PI*t/10)"
        graph = ";".join([
            f"[0:v]{'hflip,' if rtl else ''}format=yuv420p[sky]",
            "[1:v]format=rgba[l]",
            "[2:v]format=rgba,fade=t=out:st=0.6:d=1.4:alpha=1[d1]",
            "[3:v]format=rgba,fade=t=in:st=13.4:d=2:alpha=1[d2]",
            f"[sky][l]overlay=x='{x}':y='{y}'[a]",
            f"[a][d1]overlay=x='{x}':y='{y}':enable='lt(t,2.1)'[b]",
            f"[b][d2]overlay=x='{x}':y='{y}':enable='gte(t,13.4)'[c]",
            "[c][4:v]overlay,format=yuv420p[v]",
        ])
        encode(["-i", str(sky), "-loop", "1", "-i", str(light), "-loop", "1", "-i", str(dark), "-loop", "1", "-i", str(dark),
                "-loop", "1", "-i", str(layer), "-i", str(wav), "-filter_complex", graph, "-map", "[v]", "-map", "5:a"], out / f"{locale}.mp4")
        layer.unlink()
        print(f"search/{locale}.mp4", flush=True)


def main():
    kind = sys.argv[1]
    if kind == "header":
        sky, wav, out, codes = sys.argv[2], sys.argv[3], pathlib.Path(sys.argv[4]), set(sys.argv[5:])
    else:
        sky, wav, phones, out, codes = sys.argv[2], sys.argv[3], pathlib.Path(sys.argv[4]), pathlib.Path(sys.argv[5]), set(sys.argv[6:])
    out.mkdir(parents=True, exist_ok=True)
    langs = [l for l in SPEC["languages"] if not codes or l["app"] in codes]
    if kind == "header":
        header(sky, wav, out, langs)
    else:
        search(sky, wav, phones, out, langs)


if __name__ == "__main__":
    main()
