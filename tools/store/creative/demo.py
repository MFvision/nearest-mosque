#!/usr/bin/env python3
"""Cuts the App Store demo preview (the first video in the product page gallery) from the recorded tours of one
language (store-previews.yml: header, mosques, search) and writes it in Apple's app preview format.

    demo.py <previews folder> <out folder> [app language codes...]

<previews folder>/<lang>/01-header.mp4, 02-mosques.mp4, 03-search.mp4 -> <out>/<asc locale>.mp4

The tour, about 29 seconds of the app's real screens joined by short crossfades: the prayer header scrolling
into the slim bar and back; the Qibla view turning until it lights up; the mosque compass, then the map; a
question asked and its answer with sources. 886 x 1920, H.264 High at 30 fps, about 11 Mbps, with the soft
soundscape (wind and birds) as its stereo track (Apple requires one).
"""
import json
import os
import pathlib
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
SPEC = json.loads((HERE.parent / "screens.json").read_text())
# (clip, from second, to second): read from the tours' timings (DemoPreview, record.py's lead of 3 seconds).
# Negative times count from the clip's end: the question typed and sent, then the clip's last seconds, which show
# the answer and its sources however long a language takes to answer.
CUTS = [("01-header", 0.3, 8.9), ("01-header", 10.6, 17.6), ("02-mosques", 2.0, 6.0), ("03-search", 3.5, 8.0), ("03-search", -6.5, 0)]
FADE = 0.4
# DUO_FRAME=<folder>: the recordings are the iPhone Duo's outer display (886 x 1288, its side tab bar included);
# they sit whole in Apple's 886 x 1920 preview frame on <folder>/background.png, with rounded corners, and the
# language's phrase (<folder>/<app language>.png, a transparent layer) above them.
DUO = os.environ.get("DUO_FRAME")
if DUO:
    # The Duo simulator settles the city about two seconds into the header recording.
    CUTS = [("01-header", 2.3, 8.9)] + CUTS[1:]


def main():
    src, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    wanted = set(sys.argv[3:])
    out.mkdir(parents=True, exist_ok=True)
    tmp = pathlib.Path(tempfile.mkdtemp())
    total = sum(b - a for _, a, b in CUTS) - FADE * (len(CUTS) - 1)
    amb = tmp / "ambience.wav"
    subprocess.run([sys.executable, str(HERE / "ambience.py"), str(amb), f"{total:.2f}", "--day", "0:1", "--night", "2:2", "--level", "-30"], check=True)
    for lang in SPEC["languages"]:
        if wanted and lang["app"] not in wanted:
            continue
        folder = src / lang["app"]
        clips = [folder / f"{c}.mp4" for c, _, _ in CUTS]
        if not all(c.exists() for c in clips):
            print(f"::warning::demo/{lang['app']}: a recording is missing; skipped.", flush=True)
            continue
        inputs, chain, offset = [], [], 0.0
        for i, (c, a, b) in enumerate(CUTS):
            if a < 0:
                end = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(folder / f"{c}.mp4")],
                                           capture_output=True, text=True, check=True).stdout)
                a, b = end + a - 0.05, end + b - 0.05
            inputs += ["-ss", f"{a}", "-t", f"{b - a}", "-i", str(folder / f"{c}.mp4")]
            chain.append(f"[{i}:v]fps=30,format=yuv420p,setpts=PTS-STARTPTS[v{i}]")
        last = "v0"
        for i in range(1, len(CUTS)):
            offset += (CUTS[i - 1][2] - CUTS[i - 1][1]) - FADE
            chain.append(f"[{last}][v{i}]xfade=transition=fade:duration={FADE}:offset={offset:.3f}[x{i}]")
            last = f"x{i}"
        if DUO:
            frame = pathlib.Path(DUO)
            text = frame / f"{lang['app']}.png"
            n = len(CUTS)  # the frame's inputs come after the clips (and before the soundscape)
            inputs += ["-loop", "1", "-i", str(frame / "background.png"), "-loop", "1", "-i", str(frame / "mask.png"),
                       "-loop", "1", "-i", str(text if text.exists() else frame / "en.png")]
            chain.append(f"[{last}]scale=886:1288,format=rgba[scr];[{n + 1}:v]format=gray[msk];[scr][msk]alphamerge[rounded]")
            chain.append(f"[{n}:v][rounded]overlay=0:{(1920 - 1288) // 2}:shortest=1[on];[on][{n + 2}:v]overlay=0:0:shortest=1[framed]")
            last = "framed"
        chain.append(f"[{last}]format=yuv420p[vout]")
        last = "vout"
        target = out / f"{lang['asc'][0]}.mp4"
        subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", *inputs, "-i", str(amb), "-filter_complex", ";".join(chain),
                        "-map", f"[{last}]", "-map", f"{len(CUTS) + (3 if DUO else 0)}:a", "-t", f"{total:.2f}",
                        "-c:v", "libx264", "-profile:v", "high", "-level:v", "4.0", "-preset", "medium", "-b:v", "11M", "-maxrate", "12M",
                        "-bufsize", "24M", "-r", "30", "-c:a", "aac", "-b:a", "256k", "-ar", "48000", "-ac", "2", "-movflags", "+faststart",
                        str(target)], check=True)
        print(f"demo/{target.name} {total:.1f} s", flush=True)


if __name__ == "__main__":
    main()
