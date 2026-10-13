#!/usr/bin/env python3
"""Records the App Store previews (videos) of the iPhone app in every store language (macOS CI runner).

    record.py <path to the built .app> <output folder> [language codes...]

The clips come from the "previews" section of tools/store/screens.json. For each clip the Debug app is launched
with `-demoPreview`, plays its scripted tour and writes the moment the tour began into its Caches folder; the
simulator's screen is recorded meanwhile. ffmpeg then cuts `lead` seconds before that moment to `seconds` after it
and converts the cut to App Store Connect's preview format: 886 x 1920, H.264 High at 30 fps (about 11 Mbps) and a
silent stereo AAC track. Files land in <output>/previews/<app language>/<NN>-<clip>.mp4.
"""
import os
import pathlib
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from shoot import BUNDLE, SPEC, find_simulator, launch_args, run, simctl  # noqa: E402

MARKER = "Library/Caches/demoPreviewStart.txt"


def convert(raw, start, seconds, size, target):
    w, h = size
    return run(
        "ffmpeg", "-nostdin", "-y", "-loglevel", "error", "-ss", f"{max(0, start):.2f}", "-i", str(raw),
        "-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=48000", "-t", str(seconds),
        "-vf", f"scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},fps=30,format=yuv420p",
        "-map", "0:v:0", "-map", "1:a:0",
        "-c:v", "libx264", "-profile:v", "high", "-level:v", "4.0", "-preset", "slow",
        "-b:v", "11M", "-maxrate", "12M", "-bufsize", "24M",
        "-c:a", "aac", "-b:a", "256k", "-ar", "48000", "-ac", "2",
        "-movflags", "+faststart", str(target), timeout=300)


def record(app, out, langs):
    conf = SPEC["previews"]
    udid = find_simulator(conf["simulator"], ["iOS"])
    print(f"previews: simulator {udid}", flush=True)
    simctl("boot", udid)
    simctl("bootstatus", udid, "-b", check=True, timeout=600)
    simctl("install", udid, app, check=True, timeout=600)
    simctl("privacy", udid, "grant", "location", BUNDLE)
    simctl("status_bar", udid, "override", "--time", "9:41", "--batteryLevel", "100", "--batteryState", "charged",
           "--cellularBars", "4", "--wifiBars", "3")
    simctl("ui", udid, "appearance", "light")

    def marker():
        # The app's data folder appears on its first launch.
        c = simctl("get_app_container", udid, BUNDLE, "data")
        return pathlib.Path(c.stdout.strip()) / MARKER if c.returncode == 0 and c.stdout.strip() else None
    for lang in langs:
        folder = out / "previews" / lang["app"]
        folder.mkdir(parents=True, exist_ok=True)
        for n, (name, clip) in enumerate(conf["clips"].items(), 1):
            only = os.environ.get("ONLY", "").split()
            if only and f"{lang['app']}/{n:02d}-{name}" not in only:
                continue
            lat, lon = SPEC["cities"][clip["city"]]
            simctl("location", udid, "set", f"{lat},{lon}")
            simctl("terminate", udid, BUNDLE)
            if (m := marker()) is not None:
                m.unlink(missing_ok=True)
            raw = folder / f"{n:02d}-{name}.raw.mp4"
            rec = subprocess.Popen(["xcrun", "simctl", "io", udid, "recordVideo", "--codec=h264", "--force", str(raw)],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
            time.sleep(1.5)
            began = time.time()
            simctl("launch", udid, BUNDLE, *launch_args_for(clip, lang), timeout=120)
            # The tour marks its start once the app is ready (at least 8 to 9 seconds after launch).
            deadline = time.time() + 60
            m = None
            while time.time() < deadline and not (m and m.exists()):
                time.sleep(0.25)
                m = m or marker()
            if not (m and m.exists()):
                rec.send_signal(signal.SIGINT)
                rec.wait(timeout=30)
                raw.unlink(missing_ok=True)
                print(f"::warning::previews/{lang['app']}/{name}: the tour never started", flush=True)
                continue
            start = float(m.read_text().strip() or time.time())
            time.sleep(max(0, start + clip["seconds"] - clip["lead"] + 1.5 - time.time()))
            rec.send_signal(signal.SIGINT)
            try:
                rec.wait(timeout=30)
            except subprocess.TimeoutExpired:
                rec.kill()
            target = folder / f"{n:02d}-{name}.mp4"
            # The recording's first frame comes shortly after recordVideo starts; `began` is close enough to it.
            c = convert(raw, start - clip["lead"] - began + 1.5, clip["seconds"], conf["size"], target)
            note = "" if c.returncode == 0 else f" (convert failed: {c.stderr.strip()[:120]})"
            raw.unlink(missing_ok=True)
            print(f"{time.strftime('%H:%M:%S')} previews/{lang['app']}/{target.name}{note}", flush=True)
    simctl("terminate", udid, BUNDLE)
    simctl("shutdown", udid)


def launch_args_for(clip, lang):
    # launch_args reads a screen by name; a clip carries its own arguments.
    SPEC["screens"]["_clip"] = {"args": clip["args"], "city": clip["city"]}
    return launch_args("_clip", lang)


def main():
    app, out = sys.argv[1], pathlib.Path(sys.argv[2])
    wanted = set(sys.argv[3:])
    record(app, out, [l for l in SPEC["languages"] if not wanted or l["app"] in wanted])


if __name__ == "__main__":
    main()
