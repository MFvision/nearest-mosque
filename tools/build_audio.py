#!/usr/bin/env python3
"""Convert the app's recordings into the files the apps ship (needs ffmpeg).

  python3 tools/build_audio.py <Islamic_App_Audio_Assets dir> <Prayer_Voice_Notifications_ElevenLabs dir> <clean Adhan dir>
  python3 tools/build_audio.py --short     (only the short Adhans, cut from the shipped full ones)

The clean Adhan dir holds the four Adhan recordings chosen for low noise (one folder each: mk-world-1st,
mkf-world-2nd-fajr, md-col2013-surayhi-isha, mdf-col2021-surayhi, each with audio.<ext>); the owner's pack
supplies the verses. Every Adhan and verse is high-passed (removes DC offset) and loudness-normalised so all
play at the same level.

The source packs are not kept in the repository (see shared/audio/CREDITS.md for where each recording comes
from). Outputs:

  ios/App/Resources/Sounds/   full Adhans as AAC (.m4a, played by the app) and every notification sound as
                              16-bit PCM .caf, at most 29.5 s (iOS plays the default sound for anything longer)
  android/app/src/main/res/raw/  everything as AAC (.m4a), played by the app's audio service
"""
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IOS = os.path.join(ROOT, "ios", "App", "Resources", "Sounds")
ANDROID = os.path.join(ROOT, "android", "app", "src", "main", "res", "raw")
WINDOWS = os.path.join(ROOT, "windows", "src", "main", "sounds")

PRAYERS = ["fajr", "dhuhr", "asr", "maghrib", "isha"]
PRAYER_DIRS = {"fajr": "01_Fajr", "dhuhr": "02_Dhuhr", "asr": "03_Asr", "maghrib": "04_Maghrib", "isha": "05_Isha"}
VARIANTS = {"10min": "10", "5min": "5", "approaching": "near"}

# name -> (file, start seconds, end seconds or None)
def recordings(assets, clean):
    a = lambda p: os.path.join(assets, p)
    c = lambda folder: next(os.path.join(clean, folder, f) for f in sorted(os.listdir(os.path.join(clean, folder))) if f.startswith("audio."))
    return {
        # Masjid al-Haram (noise floor about -73 dBFS; the owner's pack copy was about -34).
        "adhan_makkah": (c("mk-world-1st"), 0.5, None),
        "fajr_makkah": (c("mkf-world-2nd-fajr"), 1.0, None),
        # Al-Masjid an-Nabawi, Sheikh Abdul Majeed Surayhi ('Isha 2013, Fajr 2021).
        "adhan_madinah": (c("md-col2013-surayhi-isha"), 2.2, None),
        "fajr_madinah": (c("mdf-col2021-surayhi"), 7.0, 228.0),
        "salawat_alafasy": (a("02_Friday_Verse/05_Quran_33-56_Al-Ahzab__Mishary-Rashid-Alafasy.mp3"), 0, None),
        # The recitation ends at 28.9 s; the rest is silence (and would push it past iOS's 30 s).
        "salawat_husary": (a("02_Friday_Verse/06_Quran_33-56_Al-Ahzab__Mahmoud-Khalil-Al-Husary.mp3"), 0, 29.2),
        "evening_alafasy": (a("03_Evening_Verse/07_Quran_13-28_Ar-Rad__Mishary-Rashid-Alafasy.mp3"), 0, None),
        "evening_abdulbasit": (a("03_Evening_Verse/08_Quran_13-28_Ar-Rad__Abdul-Basit-Abdul-Samad-Murattal.mp3"), 0.6, None),
    }

FULL = {"adhan_makkah", "adhan_madinah", "fajr_makkah", "fajr_madinah"}
# The short Adhan: the first "Allahu akbar, Allahu akbar" of each recording, to this many seconds (the end of that
# breath and its echo; the next phrase starts at 15.2 s in Makkah and 22.0 s in Madinah), faded out.
SHORT = {"adhan_makkah": 11.6, "adhan_madinah": 13.0}
# DC offset removed, then one loudness for every recording (EBU R128, -16 LUFS, peaks under -1.5 dBTP). The gain
# is linear (two-pass loudnorm): dynamic levelling would lift the pauses between phrases and their hiss with them.
HIGHPASS = "highpass=f=40"


def clean_filter(src, start, length):
    """The filter for one recording: high-pass, then one fixed gain measured on the same span."""
    args = ["ffmpeg", "-hide_banner", "-nostats", "-ss", str(start)] + (["-t", str(length)] if length else []) + [
        "-i", src, "-map", "0:a:0", "-vn", "-af", HIGHPASS + ",loudnorm=I=-16:TP=-1.5:LRA=20:print_format=json", "-f", "null", "-"]
    out = subprocess.run(args, capture_output=True, text=True).stderr
    m = json.loads(out[out.rindex("{"):out.rindex("}") + 1])
    return (HIGHPASS + ",loudnorm=I=-16:TP=-1.5:LRA=20:linear=true:measured_I=%s:measured_TP=%s:measured_LRA=%s:measured_thresh=%s:offset=%s"
            % (m["input_i"], m["input_tp"], m["input_lra"], m["input_thresh"], m["target_offset"]))


def ff(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def caf(src, dst, start=0.0, end=None):
    """A notification sound: mono 16-bit PCM at 24 kHz (IMA4 would add hiss), at most 29.5 s, faded out when cut."""
    length = 29.5 if end is None else min(29.5, end - start)
    af = clean_filter(src, start, length) + ("," + "afade=t=out:st=%.2f:d=1.5" % (length - 1.5) if end is None else "")
    ff("-ss", str(start), "-t", str(length), "-i", src, "-map", "0:a:0", "-vn", "-af", af, "-ac", "1", "-ar", "24000", "-c:a", "pcm_s16le", dst)


def m4a(src, dst, start=0.0, end=None, kbps=64, clean=True):
    args = ["-ss", str(start)] + (["-t", str(end - start)] if end else []) + ["-i", src]
    af = clean_filter(src, start, (end - start) if end else None)
    ff(*args, "-map", "0:a:0", "-vn", *(["-af", af] if clean else []), "-ac", "1", "-ar", "44100", "-c:a", "aac", "-b:a", "%dk" % kbps, "-movflags", "+faststart", dst)


def short_adhans():
    """The short Adhans from the shipped full ones (already cleaned and levelled): a .caf for notifications and an
    .m4a for the app on iOS, an .m4a on Android, an .mp3 on Windows."""
    for name, end in SHORT.items():
        src = os.path.join(IOS, name + ".m4a")
        fade = "afade=t=out:st=%.2f:d=0.8" % (end - 0.8)
        ff("-i", src, "-t", str(end), "-af", fade, "-ac", "1", "-ar", "24000", "-c:a", "pcm_s16le", os.path.join(IOS, name + "_short.caf"))
        for out in (os.path.join(IOS, name + "_short.m4a"), os.path.join(ANDROID, name + "_short.m4a")):
            ff("-i", src, "-t", str(end), "-af", fade, "-ac", "1", "-ar", "44100", "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", out)
        ff("-i", src, "-t", str(end), "-af", fade, "-ac", "1", "-ar", "44100", "-c:a", "libmp3lame", "-b:a", "128k", os.path.join(WINDOWS, name + "_short.mp3"))


def main(assets, voices, clean):
    os.makedirs(IOS, exist_ok=True)
    os.makedirs(ANDROID, exist_ok=True)
    for name, (src, start, end) in recordings(assets, clean).items():
        caf(src, os.path.join(IOS, name + ".caf"), start, end)
        # The whole Adhan at 96 kbps (lower rates smear the reverberant voice).
        if name in FULL:
            m4a(src, os.path.join(IOS, name + ".m4a"), start, end, kbps=96)
        m4a(src, os.path.join(ANDROID, name + ".m4a"), start, end, kbps=96 if name in FULL else 64)
    for lang, folder in (("en", "01_English"), ("ar", "02_Arabic")):
        for p in PRAYERS:
            for variant, short in VARIANTS.items():
                src = os.path.join(voices, folder, PRAYER_DIRS[p], "%s_%s_%s_take1.mp3" % (lang.upper(), p, variant))
                name = "voice_%s_%s_%s" % (lang, p, short)
                caf(src, os.path.join(IOS, name + ".caf"), 0, 29.5)
                m4a(src, os.path.join(ANDROID, name + ".m4a"), kbps=48)
    short_adhans()
    print("written", len(os.listdir(IOS)), "iOS files,", len([f for f in os.listdir(ANDROID) if f.endswith(".m4a")]), "Android files")


if __name__ == "__main__":
    if sys.argv[1:] == ["--short"]:
        short_adhans()
    elif len(sys.argv) != 4:
        sys.exit(__doc__)
    else:
        main(sys.argv[1], sys.argv[2], sys.argv[3])
