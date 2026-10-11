#!/usr/bin/env python3
"""Convert the app's recordings into the files the apps ship (needs ffmpeg).

  python3 tools/build_audio.py <Islamic_App_Audio_Assets dir> <Prayer_Voice_Notifications_ElevenLabs dir> <clean Adhan dir>

The clean Adhan dir holds the four Adhan recordings chosen for low noise (one folder each: mk-world-1st,
mkf-world-2nd-fajr, md-col2013-surayhi-isha, mdf-col2021-surayhi, each with audio.<ext>); the owner's pack
supplies the verses. Every Adhan and verse is high-passed (removes DC offset) and loudness-normalised so all
play at the same level.

The source packs are not kept in the repository (see shared/audio/CREDITS.md for where each recording comes
from). Outputs:

  ios/App/Resources/Sounds/   full Adhans as AAC (.m4a, played by the app) and every notification sound as
                              IMA4 .caf, at most 29.5 s (iOS plays the default sound for anything longer)
  android/app/src/main/res/raw/  everything as AAC (.m4a), played by the app's audio service
"""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IOS = os.path.join(ROOT, "ios", "App", "Resources", "Sounds")
ANDROID = os.path.join(ROOT, "android", "app", "src", "main", "res", "raw")

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
# DC offset removed, then one loudness for every recording (EBU R128, -16 LUFS, peaks under -1.5 dBTP).
CLEAN = "highpass=f=40,loudnorm=I=-16:TP=-1.5:LRA=11"


def ff(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def caf(src, dst, start=0.0, end=None):
    """A notification sound: mono IMA4, at most 29.5 s, faded out when cut."""
    length = 29.5 if end is None else min(29.5, end - start)
    af = CLEAN + ("," + "afade=t=out:st=%.2f:d=1.5" % (length - 1.5) if end is None else "")
    ff("-ss", str(start), "-t", str(length), "-i", src, "-map", "0:a:0", "-vn", "-af", af, "-ac", "1", "-ar", "22050", "-c:a", "adpcm_ima_qt", dst)


def m4a(src, dst, start=0.0, end=None, kbps=64, clean=True):
    args = ["-ss", str(start)] + (["-t", str(end - start)] if end else []) + ["-i", src]
    ff(*args, "-map", "0:a:0", "-vn", *(["-af", CLEAN] if clean else []), "-ac", "1", "-ar", "44100", "-c:a", "aac", "-b:a", "%dk" % kbps, "-movflags", "+faststart", dst)


def main(assets, voices, clean):
    os.makedirs(IOS, exist_ok=True)
    os.makedirs(ANDROID, exist_ok=True)
    for name, (src, start, end) in recordings(assets, clean).items():
        caf(src, os.path.join(IOS, name + ".caf"), start, end)
        if name in FULL:
            m4a(src, os.path.join(IOS, name + ".m4a"), start, end)
        m4a(src, os.path.join(ANDROID, name + ".m4a"), start, end)
    for lang, folder in (("en", "01_English"), ("ar", "02_Arabic")):
        for p in PRAYERS:
            for variant, short in VARIANTS.items():
                src = os.path.join(voices, folder, PRAYER_DIRS[p], "%s_%s_%s_take1.mp3" % (lang.upper(), p, variant))
                name = "voice_%s_%s_%s" % (lang, p, short)
                caf(src, os.path.join(IOS, name + ".caf"), 0, 29.5)
                m4a(src, os.path.join(ANDROID, name + ".m4a"), kbps=48)
    print("written", len(os.listdir(IOS)), "iOS files,", len([f for f in os.listdir(ANDROID) if f.endswith(".m4a")]), "Android files")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3])
