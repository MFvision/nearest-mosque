#!/usr/bin/env python3
"""Builds the promo video (docs/11-promo-script.md) from a voiceover take: the voice cut to the version's lines, the
timeline the scenes follow (promo.mjs), the soundscape with a soft click as each element appears, and the final mix.

    promo.py <voice> <wide|tall> <assets folder> <out.mp4> [--voice-map NAME]

The voice map names the take; one ending in "-en" makes the English promo (English titles and screens: the assets
folder holds the English store screenshots and recordings).

The voice maps below give, for each take, where each script line is in the recording (seconds); they were read
from the takes' silences. The wide cut (16:9, 1920 x 1080) keeps all 15 lines with their natural pauses; the tall
cut (9:16, 1080 x 1920) skips the lines marked "wide only" and tightens the pauses.
"""
import json
import os
import pathlib
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent

VOICES = {
    "alghammas": {1: (0.00, 3.78), 2: (5.02, 12.64), 3: (13.74, 21.08), 4: (22.16, 26.85), 5: (27.98, 32.60), 6: (33.74, 40.36),
                  7: (41.37, 47.63), 8: (48.64, 53.32), 9: (54.48, 60.37), 10: (61.44, 68.81), 11: (69.92, 74.94), 12: (76.08, 77.89),
                  13: (79.00, 84.62), 14: (85.63, 86.78), 15: (87.88, 90.60)},
    "ali": {1: (0.00, 3.75), 2: (4.58, 12.14), 3: (12.80, 20.37), 4: (21.12, 25.61), 5: (26.35, 30.80), 6: (31.67, 38.32),
            7: (39.02, 45.45), 8: (46.31, 50.87), 9: (51.60, 57.86), 10: (58.48, 65.86), 11: (66.43, 71.29), 12: (71.86, 73.71),
            13: (74.29, 80.00), 14: (80.49, 81.63), 15: (82.40, 84.97)},
    # English (Near-Mosque-Voiceover-EN): EN_Alghammas_take1, EN_Ali_take1, EN_Raed_take2.
    "alghammas-en": {1: (0.00, 3.67), 2: (5.02, 11.25), 3: (12.38, 18.88), 4: (19.98, 24.00), 5: (25.13, 29.39), 6: (30.54, 36.10),
                     7: (37.15, 42.48), 8: (43.64, 48.23), 9: (49.31, 54.31), 10: (55.28, 63.05), 11: (64.08, 68.57), 12: (69.34, 71.20),
                     13: (71.92, 77.13), 14: (78.17, 79.03), 15: (79.98, 81.87)},
    "ali-en": {1: (0.00, 3.80), 2: (5.22, 11.34), 3: (12.52, 18.97), 4: (20.20, 24.25), 5: (25.31, 29.69), 6: (30.90, 36.35),
               7: (37.46, 42.75), 8: (43.97, 48.64), 9: (49.77, 54.79), 10: (55.90, 63.85), 11: (65.07, 69.52), 12: (70.42, 72.36),
               13: (73.46, 78.56), 14: (79.65, 80.46), 15: (81.56, 83.57)},
    "raed-en": {1: (0.00, 3.83), 2: (5.27, 11.35), 3: (12.64, 18.93), 4: (20.19, 24.21), 5: (25.40, 29.45), 6: (30.68, 36.16),
                7: (37.28, 42.59), 8: (43.92, 48.49), 9: (49.69, 54.57), 10: (55.77, 63.39), 11: (64.48, 68.92), 12: (69.84, 71.70),
                13: (72.76, 77.69), 14: (78.78, 79.59), 15: (80.85, 82.68)},
}
WIDE_ONLY = {7, 11, 13}


def run(*cmd):
    subprocess.run(cmd, check=True)


def main():
    voice, cut, assets, out = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3]), pathlib.Path(sys.argv[4])
    name = sys.argv[sys.argv.index("--voice-map") + 1] if "--voice-map" in sys.argv else "alghammas"
    vmap = VOICES[name]
    lang = "en" if name.endswith("-en") else "ar"
    lines = [n for n in sorted(vmap) if cut == "wide" or n not in WIDE_ONLY]
    gap = 1.05 if cut == "wide" else 0.4
    lead, tail = 0.6, 2.2
    w, h = (1920, 1080) if cut == "wide" else (1080, 1920)
    tmp = pathlib.Path(tempfile.mkdtemp())

    # The voice: each line cut from the take with a few milliseconds of fade, laid on the new timeline.
    timeline, t, parts = [], lead, []
    for i, n in enumerate(lines):
        a, b = vmap[n]
        timeline.append({"line": n, "start": round(t, 3), "end": round(t + b - a, 3)})
        seg = tmp / f"{i:02d}.wav"
        run("ffmpeg", "-nostdin", "-y", "-loglevel", "error", "-ss", f"{a:.3f}", "-to", f"{b + 0.08:.3f}", "-i", voice,
            "-af", "afade=t=in:d=0.02,areverse,afade=t=in:d=0.08,areverse", "-ar", "48000", "-ac", "2", str(seg))
        parts.append((t, seg))
        t += b - a + gap
    total = round(t - gap + tail, 3)
    inputs, filters = [], []
    for i, (start, seg) in enumerate(parts):
        inputs += ["-i", str(seg)]
        filters.append(f"[{i}:a]adelay={int(start * 1000)}|{int(start * 1000)}[v{i}]")
    filters.append("".join(f"[v{i}]" for i in range(len(parts))) + f"amix=inputs={len(parts)}:normalize=0,apad=whole_dur={total}[vo]")
    vo = tmp / "voice.wav"
    run("ffmpeg", "-nostdin", "-y", "-loglevel", "error", *inputs, "-filter_complex", ";".join(filters), "-map", "[vo]", "-t", str(total), str(vo))

    # The pictures: promo.mjs renders the scenes for this timeline and lists when each element appears (the clicks).
    spec = tmp / "timeline.json"
    spec.write_text(json.dumps({"lang": lang, "cut": cut, "width": w, "height": h, "duration": total, "lines": timeline, "assets": str(assets)}))
    video = tmp / "video.mp4"
    clicks = tmp / "clicks.json"
    run("node", str(HERE / "promo.mjs"), str(spec), str(video), str(clicks))
    click_times = json.loads(clicks.read_text())

    # The soundscape: wind throughout, birds now and then, a click per element; well under the voice.
    amb = tmp / "ambience.wav"
    run(sys.executable, str(HERE / "ambience.py"), str(amb), str(total), "--day", "0.05:0.95", "--night", "2:2",
        "--clicks", ",".join(f"{c:.2f}" for c in click_times), "--level", "-33")

    # Voice to -16 LUFS (social video), the soundscape under it, a fade at both ends.
    run("ffmpeg", "-nostdin", "-y", "-loglevel", "error", "-i", str(video), "-i", str(vo), "-i", str(amb), "-filter_complex",
        f"[1:a]loudnorm=I=-16:TP=-1.5:LRA=11[v];[v][2:a]amix=inputs=2:normalize=0,afade=t=in:d=0.5,afade=t=out:st={total - 1.2}:d=1.2[a]",
        "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "256k", "-ar", "48000", "-movflags", "+faststart",
        "-t", str(total), str(out))
    print(f"{out} {total:.1f} s, {len(click_times)} clicks")


if __name__ == "__main__":
    main()
