#!/usr/bin/env python3
"""Convert the app's recordings into the files the apps ship (needs ffmpeg).

  python3 tools/build_audio.py <Islamic_App_Audio_Assets dir> <Prayer_Voice_Notifications_ElevenLabs dir>

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

# name -> (pack file, start seconds, end seconds or None)
def recordings(assets):
    a = lambda p: os.path.join(assets, p)
    return {
        "adhan_makkah": (a("01_Adhan/01_Adhan_Makkah_Standard_Zuhr__Muadhin-Unnamed(Gemini-says-Ali-Ahmed-Mulla).mp3"), 0, None),
        "adhan_madinah": (a("01_Adhan/03_Adhan_Madinah_Standard__Muadhin-Unnamed(Gemini-says-Essam-Khan).mp3"), 5.6, None),
        "fajr_makkah": (a("01_Adhan/02_Adhan_Makkah_Fajr__Sheikh-Ahmed-Ali-Mullah.mp3"), 0, None),
        "salawat_alafasy": (a("02_Friday_Verse/05_Quran_33-56_Al-Ahzab__Mishary-Rashid-Alafasy.mp3"), 0, None),
        # The recitation ends at 28.9 s; the rest is silence (and would push it past iOS's 30 s).
        "salawat_husary": (a("02_Friday_Verse/06_Quran_33-56_Al-Ahzab__Mahmoud-Khalil-Al-Husary.mp3"), 0, 29.2),
        "evening_alafasy": (a("03_Evening_Verse/07_Quran_13-28_Ar-Rad__Mishary-Rashid-Alafasy.mp3"), 0, None),
        "evening_abdulbasit": (a("03_Evening_Verse/08_Quran_13-28_Ar-Rad__Abdul-Basit-Abdul-Samad-Murattal.mp3"), 0.6, None),
    }

FULL = {"adhan_makkah", "adhan_madinah", "fajr_makkah"}


def ff(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def caf(src, dst, start=0.0, end=None):
    """A notification sound: mono IMA4, at most 29.5 s, faded out when cut."""
    length = 29.5 if end is None else min(29.5, end - start)
    af = "afade=t=out:st=%.2f:d=1.5" % (length - 1.5) if end is None else "anull"
    ff("-ss", str(start), "-t", str(length), "-i", src, "-af", af, "-ac", "1", "-ar", "22050", "-c:a", "adpcm_ima_qt", dst)


def m4a(src, dst, start=0.0, end=None, kbps=64):
    args = ["-ss", str(start)] + (["-t", str(end - start)] if end else []) + ["-i", src]
    ff(*args, "-ac", "1", "-ar", "44100", "-c:a", "aac", "-b:a", "%dk" % kbps, "-movflags", "+faststart", dst)


def main(assets, voices):
    os.makedirs(IOS, exist_ok=True)
    os.makedirs(ANDROID, exist_ok=True)
    for name, (src, start, end) in recordings(assets).items():
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
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
