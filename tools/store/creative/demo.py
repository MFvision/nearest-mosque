#!/usr/bin/env python3
"""Cuts the App Store demo preview (the first video in the product page gallery) from the recorded tours of one
language (store-previews.yml: header, mosques, search) and writes it in Apple's app preview format.

    demo.py <previews folder> <out folder> [app language codes...]

<previews folder>/<lang>/01-header.mp4, 02-mosques.mp4, 03-search.mp4 -> <out>/<asc locale>.mp4

The tour, about 27 seconds of the app's real screens joined by short crossfades: the prayer header scrolling
into the slim bar and back; the Qibla view turning until it lights up; the mosque compass, then the map; a
question asked and its answer with sources. 886 x 1920, H.264 High at 30 fps, about 11 Mbps, with the soft
soundscape (wind and birds) as its stereo track (Apple requires one).
"""
import json
import pathlib
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
SPEC = json.loads((HERE.parent / "screens.json").read_text())
# (clip, from second, to second): read from the tours' timings (DemoPreview, record.py's lead of 3 seconds).
CUTS = [("01-header", 0.4, 8.6), ("01-header", 10.3, 15.9), ("02-mosques", 3.4, 9.6), ("03-search", 2.9, 11.6)]
FADE = 0.4


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
            inputs += ["-ss", f"{a}", "-t", f"{b - a}", "-i", str(folder / f"{c}.mp4")]
            chain.append(f"[{i}:v]fps=30,format=yuv420p,setpts=PTS-STARTPTS[v{i}]")
        last = "v0"
        for i in range(1, len(CUTS)):
            offset += (CUTS[i - 1][2] - CUTS[i - 1][1]) - FADE
            chain.append(f"[{last}][v{i}]xfade=transition=fade:duration={FADE}:offset={offset:.3f}[x{i}]")
            last = f"x{i}"
        chain.append(f"[{last}]format=yuv420p[vout]")
        last = "vout"
        target = out / f"{lang['asc'][0]}.mp4"
        subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", *inputs, "-i", str(amb), "-filter_complex", ";".join(chain),
                        "-map", f"[{last}]", "-map", f"{len(CUTS)}:a", "-t", f"{total:.2f}",
                        "-c:v", "libx264", "-profile:v", "high", "-level:v", "4.0", "-preset", "medium", "-b:v", "11M", "-maxrate", "12M",
                        "-bufsize", "24M", "-r", "30", "-c:a", "aac", "-b:a", "256k", "-ar", "48000", "-ac", "2", "-movflags", "+faststart",
                        str(target)], check=True)
        print(f"demo/{target.name} {total:.1f} s", flush=True)


if __name__ == "__main__":
    main()
