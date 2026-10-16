#!/usr/bin/env python3
"""Builds the Google Play store listing text for every Play listing language from shared/store/play/<lang>.json.

    play_listing_build.py           # write shared/store/play/listing.json
    play_listing_build.py --check   # fail when a text breaks a rule or listing.json is out of date

Each app language (shared/i18n/languages.json) has one file with title, shortDescription, fullDescription
and whatsNew. One app language can feed several Play locales (en → en-US, en-GB, ...); the app languages
Google Play has no listing language for are listed under "unsupported" and are left out of "locales".
The output is what an uploader sends with the Google Play Developer API (edits.listings.update for title,
shortDescription and fullDescription; whatsNew goes into the release's releaseNotes on the track). The
listing's default language is ar (as set in Play Console). docs/12-google-play.md describes the store listing.

Rules checked, all fatal: every enabled app language has a file and is either mapped or listed as
unsupported; each Play code is one of Play's listing languages; the four fields are present, non-empty and
within Play's limits (counted in Unicode code points, as Play counts); no en or em dash (or other dash
characters) and no spaced hyphen used as a dash; no leftover placeholders, HTML, emoji, tabs or trailing
spaces; no Apple-only features in an Android listing; translations other than English and Arabic carry
a "_note" saying they are drafts.
"""
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parent.parent
SRC = REPO / "shared/store/play"
OUT = SRC / "listing.json"
LANGS = REPO / "shared/i18n/languages.json"

# Google Play listing limits (characters, i.e. Unicode code points). whatsNew is the release notes limit.
LIMITS = {"title": 30, "shortDescription": 80, "fullDescription": 4000, "whatsNew": 500}
FIELDS = list(LIMITS)
DEFAULT_LOCALE = "ar"
FINAL = {"en", "ar"}

# Play Console listing languages and codes, from Google's help page "Add translations and localized
# assets" (support.google.com/googleplay/android-developer/answer/9844778), checked October 2026.
PLAY_LANGUAGES = {
    "af", "sq", "am", "ar", "hy-AM", "az-AZ", "bn-BD", "eu-ES", "be", "bg", "my-MM", "ca", "zh-HK", "zh-CN",
    "zh-TW", "hr", "cs-CZ", "da-DK", "nl-NL", "en-AU", "en-CA", "en-US", "en-GB", "en-IN", "en-SG", "en-ZA",
    "et", "fil", "fi-FI", "fr-CA", "fr-FR", "gl-ES", "ka-GE", "de-DE", "el-GR", "gu", "iw-IL", "hi-IN", "hu-HU",
    "is-IS", "id", "it-IT", "ja-JP", "kn-IN", "kk", "km-KH", "ko-KR", "ky-KG", "lo-LA", "lv", "lt", "mk-MK",
    "ms-MY", "ms", "ml-IN", "mr-IN", "mn-MN", "ne-NP", "no-NO", "fa", "fa-AE", "fa-AF", "fa-IR", "pl-PL",
    "pt-BR", "pt-PT", "pa", "ro", "rm", "ru-RU", "sr", "si-LK", "sk", "sl", "es-419", "es-ES", "es-US", "sw",
    "sv-SE", "ta-IN", "te-IN", "th", "tr-TR", "uk", "ur", "vi", "zu",
}

# App language → Play listing locales. The first locale is the language's main market.
PLAY = {
    "en": ["en-US", "en-GB", "en-AU", "en-CA", "en-IN", "en-SG", "en-ZA"],
    "ar": ["ar"],
    "ur": ["ur"],
    "tr": ["tr-TR"],
    "id": ["id"],
    "fr": ["fr-FR", "fr-CA"],
    "es": ["es-ES", "es-419", "es-US"],
    "ru": ["ru-RU"],
    "bn": ["bn-BD"],
    "fa": ["fa", "fa-IR", "fa-AE"],
    "prs": ["fa-AF"],
    "zh": ["zh-CN"],
    "hi": ["hi-IN"],
    "pt": ["pt-BR", "pt-PT"],
    "sw": ["sw"],
    "tl": ["fil"],
    "vi": ["vi"],
    "th": ["th"],
    "km": ["km-KH"],
    "sr": ["sr"],
    "mk": ["mk-MK"],
    "hu": ["hu-HU"],
    "nl": ["nl-NL"],
    "ka": ["ka-GE"],
    "si": ["si-LK"],
    "te": ["te-IN"],
    "kn": ["kn-IN"],
    "ml": ["ml-IN"],
    "mr": ["mr-IN"],
    "gu": ["gu"],
    "pa": ["pa"],
}

# App languages with no Play listing language. Their text is kept for when Play adds them; people with
# these device languages see the default listing (en-US) or Play's own machine translation.
UNSUPPORTED = {
    "ha": "Hausa is not a Play listing language.",
    "ug": "Uyghur is not a Play listing language.",
    "ckb": "Kurdish (Sorani) is not a Play listing language.",
    "bs": "Bosnian is not a Play listing language; Serbian (sr, Cyrillic text here) and Croatian (hr) are.",
    "as": "Assamese is not a Play listing language.",
}

DASHES = re.compile("[‐‒–—―−⸺⸻﹘﹣－]")
SPACED_HYPHEN = re.compile(r"(^|\s)-{1,2}(\s|$)", re.M)
PLACEHOLDER = re.compile(r"%(\d+\$)?[sd@]|\{[^}]*\}|\[[^\]]*\]|<[^>]+>|\b(TODO|TBD|FIXME|XXX)\b|lorem ipsum", re.I)
EMOJI = re.compile("[\U0001F000-\U0001FAFF☀-➿️]")
# Case-sensitive: "siri" and "tafsiri" are ordinary words in some languages.
APPLE_ONLY = re.compile(r"Dynamic Island|Apple Watch|\bSiri\b|SIRI\b|Control Center|Lock Screen|Live Activit|Vision Pro|Apple TV|Apple Intelligence|Apple Maps")


def problems_in(lang, d):
    out = []
    for k in FIELDS:
        v = d.get(k)
        if not isinstance(v, str) or not v.strip():
            out.append(f"{lang}: {k} missing or empty")
            continue
        n = len(v)  # code points
        if n > LIMITS[k]:
            out.append(f"{lang}: {k} is {n} characters, over Play's {LIMITS[k]}")
        if k != "fullDescription" and "\n" in v:
            out.append(f"{lang}: {k} has a line break")
        if DASHES.search(v):
            out.append(f"{lang}: {k} has a dash character ({DASHES.search(v).group()!r}); use a comma, colon or full stop")
        if SPACED_HYPHEN.search(v):
            out.append(f"{lang}: {k} uses a hyphen as a dash")
        if PLACEHOLDER.search(v):
            out.append(f"{lang}: {k} has a placeholder or markup: {PLACEHOLDER.search(v).group()!r}")
        if EMOJI.search(v):
            out.append(f"{lang}: {k} has an emoji")
        if APPLE_ONLY.search(v):
            out.append(f"{lang}: {k} names an Apple-only feature: {APPLE_ONLY.search(v).group()!r}")
        if "\t" in v or "\r" in v or any(line != line.rstrip() for line in v.split("\n")):
            out.append(f"{lang}: {k} has a tab, carriage return or trailing space")
    note = d.get("_note", "")
    if lang not in FINAL and "draft" not in note.lower():
        out.append(f"{lang}: _note must say the translation is a draft")
    extra = set(d) - set(FIELDS) - {"_note"}
    if extra:
        out.append(f"{lang}: unknown keys {sorted(extra)}")
    return out


def build():
    problems = []
    enabled = [l["code"] for l in json.loads(LANGS.read_text())["languages"] if l.get("enabled")]
    for code in enabled:
        if code not in PLAY and code not in UNSUPPORTED:
            problems.append(f"{code}: enabled app language with no Play locale and not listed as unsupported")
    for code in list(PLAY) + list(UNSUPPORTED):
        if code not in enabled:
            problems.append(f"{code}: mapped here but not an enabled app language")
    seen = {}
    for code, locales in PLAY.items():
        for loc in locales:
            if loc not in PLAY_LANGUAGES:
                problems.append(f"{code}: {loc} is not a Play listing language code")
            if loc in seen:
                problems.append(f"{loc} is fed by both {seen[loc]} and {code}")
            seen[loc] = code
    if DEFAULT_LOCALE not in seen:
        problems.append(f"default locale {DEFAULT_LOCALE} has no text")

    texts = {}
    for code in enabled:
        path = SRC / f"{code}.json"
        if not path.exists():
            problems.append(f"missing {path.relative_to(REPO)}")
            continue
        try:
            d = json.loads(path.read_text())
        except ValueError as e:
            problems.append(f"{path.relative_to(REPO)}: not JSON ({e})")
            continue
        problems += problems_in(code, d)
        texts[code] = d

    locales = {}
    for code, locs in PLAY.items():
        if code not in texts:
            continue
        for loc in locs:
            locales[loc] = {"appLanguage": code, **{k: texts[code].get(k, "") for k in FIELDS}}
    unsupported = {code: {"note": note, "file": f"shared/store/play/{code}.json"} for code, note in UNSUPPORTED.items()}
    return locales, unsupported, problems


def render(locales, unsupported):
    return json.dumps({
        "_about": "Google Play store listing text per Play listing language, for Near Mosque on Android. Built by "
                  "tools/store/play_listing_build.py from shared/store/play/<app language>.json; do not edit by hand. "
                  "title, shortDescription and fullDescription go to the store listing (Play Developer API "
                  "edits.listings.update, one call per locale); whatsNew goes to the release notes of the track "
                  "release (releaseNotes: [{language, text}]). The default listing language is en-US. English and "
                  "Arabic are final; the other translations are drafts awaiting native review. Lengths are Unicode "
                  "code points, as Play counts them.",
        "defaultLanguage": DEFAULT_LOCALE,
        "limits": LIMITS,
        "locales": locales,
        "unsupported": unsupported,
    }, ensure_ascii=False, indent=1) + "\n"


def main():
    locales, unsupported, problems = build()
    text = render(locales, unsupported)
    for p in problems:
        print(p)
    if "--check" in sys.argv:
        if problems:
            print(f"{len(problems)} problems; fix shared/store/play/<lang>.json")
            sys.exit(1)
        if not OUT.exists() or OUT.read_text() != text:
            print("shared/store/play/listing.json is out of date; run tools/store/play_listing_build.py")
            sys.exit(1)
        print(f"{len(locales)} Play locales OK, {len(unsupported)} app languages without a Play listing language")
        return
    if problems:
        print(f"{len(problems)} problems; listing.json not written")
        sys.exit(1)
    OUT.write_text(text)
    print(f"{len(locales)} Play locales written to {OUT.relative_to(REPO)}, "
          f"{len(unsupported)} app languages without a Play listing language")
    if "--table" in sys.argv:
        print(f"\n{'Play locale':<12}{'app':<6}{'title':>6}{'short':>7}{'full':>7}{'new':>5}  title")
        for loc, v in locales.items():
            print(f"{loc:<12}{v['appLanguage']:<6}{len(v['title']):>6}{len(v['shortDescription']):>7}"
                  f"{len(v['fullDescription']):>7}{len(v['whatsNew']):>5}  {v['title']}")


if __name__ == "__main__":
    main()
