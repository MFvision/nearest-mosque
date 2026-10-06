#!/usr/bin/env python3
"""Build the downloadable content packs for the interface languages that have no bundled content, and the
catalog the apps use to download them.

  python3 tools/build_remote_packs.py --out .cache/remote                 # every such language
  python3 tools/build_remote_packs.py --out .cache/remote --langs ka sw   # some of them

For each language, up to four packs from the same sources and terms as the bundled ones: HadeethEnc hadiths,
a QuranEnc translation of the meanings, the Mukhtasar tafsir (QuranEnc) and the IslamHouse library.

Output: <out>/packs/<folder>/ (the packs, as in packs/sources) and <out>/assets/: every pack file
compressed with raw DEFLATE as <folder>.<file>.z, plus remote-packs.json. Upload the assets to one
release (or any static host) and copy remote-packs.json to shared/content/: the apps download only what
that catalog lists, and install a pack only when its manifest's SHA-256 matches the catalog (the manifest
in turn lists each file's SHA-256). Moving to another host means changing --base-url and rebuilding.
"""
import argparse
import datetime
import hashlib
import json
import os
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_enc_packs as enc  # noqa: E402
import build_islamhouse_pack as ih  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BASE_URL = "https://github.com/MFvision/nearest-mosque/releases/download/content-v1/"
# Source codes where they differ from the app's (Sorani Kurdish is "ku" on IslamHouse and HadeethEnc).
SOURCE_CODE = {"ckb": "ku"}
# One downloadable QuranEnc translation per language (none for ru, bn, hu, ka, mr). Dari readers get the
# Persian translation and tafsir.
TRANSLATION = {
    "fa": "persian_ih", "prs": "persian_ih", "zh": "chinese_suliman", "hi": "hindi_omari", "pt": "portuguese_nasr",
    "ha": "hausa_gummi", "sw": "swahili_rwwad", "tl": "tagalog_rwwad", "vi": "vietnamese_rwwad", "th": "thai_rwwad",
    "km": "khmer_rwwad", "ug": "uyghur_saleh", "ckb": "kurdish_bamoki", "bs": "bosnian_rwwad", "sr": "serbian_rwwad",
    "mk": "macedonian_group", "nl": "dutch_center", "si": "sinhalese_mahir", "te": "telugu_muhammad",
    "kn": "kannada_hamza", "ml": "malayalam_kunhi", "gu": "gujarati_omari", "pa": "punjabi_arif", "as": "assamese_rafeeq",
}
TAFSIR = {
    "as": "assamese_mokhtasar", "bn": "bengali_mokhtasar", "bs": "bosnian_mokhtasar", "zh": "chinese_mokhtasar",
    "hi": "hindi_mokhtasar", "km": "khmer_mokhtasar", "ckb": "kurdish_mokhtasar", "ml": "malayalam_mokhtasar",
    "fa": "persian_mokhtasar", "prs": "persian_mokhtasar", "sr": "serbian_mokhtasar", "si": "sinhalese_mokhtasar",
    "tl": "tagalog_mokhtasar", "te": "telugu_mokhtasar", "th": "thai_mokhtasar", "ug": "uyghur_mokhtasar",
    "vi": "vietnamese_mokhtasar",
}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def deflate(data):
    c = zlib.compressobj(9, zlib.DEFLATED, -15)
    return c.compress(data) + c.flush()


def remote_languages():
    langs = json.load(open(os.path.join(ROOT, "shared", "i18n", "languages.json"), encoding="utf-8"))["languages"]
    bundled = {"en", "ar", "ur", "tr", "id", "fr", "es"}
    return [l["code"] for l in langs if l["enabled"] and l["code"] not in bundled]


def build(lang, out, cache, translations, today):
    src = SOURCE_CODE.get(lang, lang)
    enc.build_hadeethenc(lang, os.path.join(cache, "hadeethenc"), out, today, src=src)
    if lang in TRANSLATION:
        enc.build_quranenc(lang, TRANSLATION[lang], translations, os.path.join(cache, "quranenc"), out, today)
    if lang in TAFSIR:
        enc.build_tafsir(lang, TAFSIR[lang], os.path.join(cache, "quranenc"), out, today)
    ih.build(lang, ih.fetch_lang(src, os.path.join(cache, "islamhouse")), out, src=src)


def publish(packs_dir, assets_dir, base_url):
    os.makedirs(assets_dir, exist_ok=True)
    entries = []
    for folder in sorted(os.listdir(packs_dir)):
        d = os.path.join(packs_dir, folder)
        manifest_bytes = open(os.path.join(d, "manifest.json"), "rb").read()
        manifest = json.loads(manifest_bytes)
        files, total, installed = [], 0, 0
        for path in ["manifest.json"] + [f["path"] for f in manifest["files"]]:
            raw = open(os.path.join(d, path), "rb").read()
            z = deflate(raw)
            asset = f"{folder}.{path}.z"
            open(os.path.join(assets_dir, asset), "wb").write(z)
            files.append({"path": path, "asset": asset, "bytes": len(z), "sha256": sha256(z), "size": len(raw)})
            total += len(z)
            installed += len(raw)
        entries.append({
            "id": manifest["id"], "language": manifest["languages"][0], "title": manifest["title"],
            "version": manifest["version"], "recordCount": manifest.get("recordCount"),
            "manifestSha256": sha256(manifest_bytes), "bytes": total, "installedBytes": installed, "files": files,
        })
    catalog = {
        "_comment": "Generated by tools/build_remote_packs.py. Content packs the apps may download; each is installed only "
                    "when its manifest matches manifestSha256 (and every file the manifest lists).",
        "schemaVersion": 1, "baseUrl": base_url,
        "builtAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "packs": entries,
    }
    json.dump(catalog, open(os.path.join(assets_dir, "remote-packs.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    size = sum(e["bytes"] for e in entries)
    print(f"{len(entries)} packs, {size / 1e6:.1f} MB to download in all")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, ".cache", "remote"))
    ap.add_argument("--cache", default=os.path.join(ROOT, ".cache"))
    ap.add_argument("--langs", nargs="+", default=None)
    ap.add_argument("--base-url", default=BASE_URL)
    a = ap.parse_args()
    today = datetime.date.today().isoformat()
    packs_dir = os.path.join(a.out, "packs")
    os.makedirs(packs_dir, exist_ok=True)
    os.makedirs(os.path.join(a.cache, "quranenc"), exist_ok=True)
    os.makedirs(os.path.join(a.cache, "hadeethenc"), exist_ok=True)
    lst = enc.fetch("https://quranenc.com/api/v1/translations/list", os.path.join(a.cache, "quranenc", "list.json"))
    translations = json.load(open(lst, encoding="utf-8"))["translations"]
    for lang in a.langs or remote_languages():
        build(lang, packs_dir, a.cache, translations, today)
    publish(packs_dir, os.path.join(a.out, "assets"), a.base_url)


if __name__ == "__main__":
    main()
