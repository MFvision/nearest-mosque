#!/usr/bin/env python3
"""Build library packs from HadeethEnc.com (translated hadiths) and QuranEnc.com (translated meanings
of the Quran), using their official bulk downloads.

  python3 tools/build_enc_packs.py --out packs/sources

Both sites allow downloading and re-publishing their translations on these conditions (quoted in
docs/04-data-and-licenses.md): no modification, addition or deletion; credit the publisher and source
with a link; state the version; keep the version information; send notes to the source; follow new
versions; no inappropriate advertisements. So texts are stored in full and unaltered, every record
carries its version, the downloaded files' version header is kept in NOTICE files, and rebuilding with
this script picks up new versions.

HadeethEnc: https://hadeethenc.com/browse/download/{lang} (Excel). QuranEnc: the SQLite file of one
downloadable translation per language (keys below); the app shows the Arabic verse with it from the
bundled Tanzil Quran pack. The Mukhtasar tafsir is built the same way from its SQLite download, with its
title and version read from QuranEnc's index page.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import sqlite3
import urllib.request

import openpyxl

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
HE_LANGS = ["ar", "en", "ur", "tr", "id", "fr", "es"]
QE_KEYS = {"ur": "urdu_junagarhi", "tr": "turkish_rwwad", "id": "indonesian_affairs", "fr": "french_rashid", "es": "spanish_garcia"}
# Al-Mukhtasar in Interpreting the Noble Quran (Tafsir Center for Quranic Studies), in the languages QuranEnc
# offers it for download. English and Urdu are browsable on the site but not downloadable, so not included.
TAFSIR_KEYS = {"ar": "arabic_mokhtasar", "tr": "turkish_mokhtasar", "id": "indonesian_mokhtasar", "fr": "french_mokhtasar", "es": "spanish_mokhtasar"}
UA = "NearMosque-pack-builder/0.1 (+https://github.com/MFvision/nearest-mosque)"
# Parts of a record's text (title, hadith, explanation...) are stored once, in original.text, separated by
# U+2063 (INVISIBLE SEPARATOR); section.parts lists their kind and language in the same order. The search
# tokenizer drops the separator.
SEP = "\u2063"


def qe_site(lang):
    """QuranEnc's page language for links: the reader's when the site has it, else English."""
    return lang if lang in ("ar", "en", "ur", "tr", "id", "fr", "es") else "en"


def compact(parts):
    """original.text and the parts' kinds/languages for records with several text parts."""
    parts = [p for p in parts if p["text"]]
    return SEP.join(p["text"] for p in parts), [{"kind": p["kind"], "lang": p["lang"]} for p in parts]


def fetch(url, path):
    if not os.path.exists(path):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=300) as r, open(path, "wb") as f:
            f.write(r.read())
    return path


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def clean(s):
    return (str(s).strip() if s is not None else "").replace("\r\n", "\n")


def write_pack(out_root, folder, manifest, documents, rows, notice):
    out = os.path.join(out_root, folder)
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "chunks.jsonl"), "w", encoding="utf-8") as f:
        for seq, r in enumerate(rows, start=1):
            f.write(json.dumps({"id": r["id"], "seq": seq, **{k: v for k, v in r.items() if k != "id"}},
                               ensure_ascii=False, separators=(",", ":")) + "\n")
    json.dump(documents, open(os.path.join(out, "documents.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    notice_name = [n for n in notice][0]
    open(os.path.join(out, notice_name), "w", encoding="utf-8").write(notice[notice_name])
    files = []
    for name in ["chunks.jsonl", "documents.json", notice_name]:
        p = os.path.join(out, name)
        files.append({"path": name, "bytes": os.path.getsize(p), "sha256": sha256_file(p)})
    manifest = {**manifest, "recordCount": len(rows), "files": files}
    json.dump(manifest, open(os.path.join(out, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"{manifest['id']}: {len(rows)} records, chunks.jsonl {files[0]['bytes'] / 1e6:.1f} MB")


def build_hadeethenc(lang, cache, out_root, today, src=None):
    """Hadith pack for the app language `lang`; `src` is HadeethEnc's code for it when different (ckb → ku)."""
    src = src or lang
    path = fetch(f"https://hadeethenc.com/browse/download/{src}", os.path.join(cache, f"he-{src}.xlsx"))
    if open(path, "rb").read(2) != b"PK":
        raise SystemExit(f"hadeethenc {src}: not an Excel file (language not offered)")
    ws = openpyxl.load_workbook(path, read_only=True).active
    it = ws.iter_rows(values_only=True)
    header_text = clean(next(it)[0])
    m = re.search(r"\(v([\d.]+)\)", header_text)
    version = m.group(1) if m else "unknown"
    cols = [clean(c) for c in next(it)]
    doc_id = f"hadeethenc-{lang}"
    rows = []
    for raw in it:
        r = {c: clean(v) for c, v in zip(cols, raw) if c}
        if not r.get("id") or not r.get("title"):
            continue
        text = r.get("hadith_text", "")
        parts = [{"kind": "title", "lang": lang, "text": r["title"]}, {"kind": "hadith", "lang": lang, "text": text}]
        if lang != "ar" and r.get("hadith_text_ar"):
            parts.append({"kind": "hadith", "lang": "ar", "text": r["hadith_text_ar"]})
        for k in ("explanation", "benefits"):
            if r.get(k):
                parts.append({"kind": k, "lang": lang, "text": r[k]})
        if lang == "ar" and r.get("word_meanings") and r["word_meanings"].strip(". ") != "":
            parts.append({"kind": "words", "lang": "ar", "text": r["word_meanings"]})
        grade = r.get("grade", "").strip("[]")
        takhrij = (r.get("takhrij") or "").strip("[]")
        body, kinds = compact(parts)
        section = {"type": "hadith", "publisher": "hadeethenc", "itemId": int(r["id"]),
                   "grade": grade, "source": takhrij, "version": version, "parts": kinds}
        rows.append({"id": f"he:{lang}:{r['id']}", "anchor": r["title"], "section": section,
                     "original": {"docId": doc_id, "lang": lang, "text": body},
                     "url": r.get("link") or f"https://hadeethenc.com/{src}/browse/hadith/{r['id']}"})
    license_ = {"id": "HadeethEnc-terms", "name": "HadeethEnc.com terms: re-publishing permitted unaltered, with source, publisher and version",
                "url": "https://hadeethenc.com/", "attribution": f"HadeethEnc.com (v{version})"}
    documents = [{"id": doc_id, "kind": "library", "title": {"en": "Encyclopedia of Translated Prophetic Hadiths", "ar": "موسوعة الأحاديث النبوية المترجمة"},
                  "edition": f"HadeethEnc.com v{version}", "publisher": "HadeethEnc.com", "language": lang,
                  "url": f"https://hadeethenc.com/{src}", "license": license_, "retrievedAt": today, "citation": "item", "textType": "selectable"}]
    manifest = {"id": f"sources.hadeethenc-{lang}", "kind": "sources", "schemaVersion": 1, "version": 1,
                "title": {"en": f"HadeethEnc hadiths ({lang})", "ar": "موسوعة الأحاديث النبوية المترجمة"}, "languages": [lang],
                "source": {"name": "HadeethEnc.com", "url": f"https://hadeethenc.com/{src}", "snapshot": today, "version": version},
                "license": license_}
    notice = {"NOTICE-hadeethenc.txt": header_text + "\n\n"
              "Translated Prophetic Hadiths from HadeethEnc.com, published unaltered under its terms: no modification,\n"
              "addition or deletion; the publisher and source (HadeethEnc.com) credited; the version number stated; the\n"
              "version information kept; notes sent to the source; new versions followed; no inappropriate advertisements.\n"}
    write_pack(out_root, f"hadeethenc-{lang}", manifest, documents, rows, notice)


def build_quranenc(lang, key, translations, cache, out_root, today):
    meta = next(t for t in translations if t["key"] == key)
    version = meta["version"]
    path = fetch(meta["database_uncompressed_url"], os.path.join(cache, f"{key}.sqlite"))
    db = sqlite3.connect(path)
    doc_id = f"quranenc-{lang}"
    rows = []
    for sura, aya, tr, fn in db.execute("SELECT sura, aya, translation, footnotes FROM translations ORDER BY sura, aya"):
        tr, fn = clean(tr), clean(fn)
        # The Arabic verse is shown from the bundled Quran pack (quran:{sura}:{aya}), not stored again here.
        body, kinds = compact([{"kind": "translation", "lang": lang, "text": tr}, {"kind": "footnotes", "lang": lang, "text": fn}])
        section = {"type": "quran", "publisher": "quranenc", "surah": sura, "ayah": aya, "translationKey": key,
                   "translationTitle": meta["title"], "version": version, "parts": kinds, "verse": f"quran:{sura}:{aya}"}
        rows.append({"id": f"qe:{lang}:{sura}:{aya}", "anchor": f"{sura}:{aya}", "section": section,
                     "original": {"docId": doc_id, "lang": lang, "text": body},
                     "url": f"https://quranenc.com/{qe_site(lang)}/browse/{key}/{sura}#{aya}"})
    license_ = {"id": "QuranEnc-terms", "name": "QuranEnc.com terms: re-publishing permitted unaltered, with source, publisher and version",
                "url": "https://quranenc.com/", "attribution": f"QuranEnc.com — {meta['title']} (v{version})"}
    documents = [{"id": doc_id, "kind": "library", "title": {"en": meta["title"], lang: meta["title"]}, "edition": f"QuranEnc.com {key} v{version}",
                  "publisher": "QuranEnc.com", "language": lang, "url": f"https://quranenc.com/{qe_site(lang)}/browse/{key}",
                  "license": license_, "retrievedAt": today, "citation": "item", "textType": "selectable", "translationOf": "quran-ar-tanzil"}]
    manifest = {"id": f"sources.quranenc-{lang}", "kind": "sources", "schemaVersion": 1, "version": 1,
                "title": {"en": meta["title"], lang: meta["title"]}, "languages": [lang],
                "source": {"name": "QuranEnc.com", "url": f"https://quranenc.com/{qe_site(lang)}/browse/{key}", "snapshot": today,
                           "translationKey": key, "version": version, "lastUpdate": meta.get("last_update")},
                "license": license_}
    notice = {"NOTICE-quranenc.txt": f"# {meta['title']}\n# Source: https://quranenc.com/{qe_site(lang)}/browse/{key}\n# Version: {version}\n\n"
              "Translation of the meanings of the Noble Quran from QuranEnc.com, published unaltered under its terms: no\n"
              "modification, addition or deletion; the publisher and source (QuranEnc.com) credited; the version number\n"
              "stated; the version information kept; notes sent to the source; new versions followed; no inappropriate\n"
              "advertisements. The Arabic verse text shown with it is from Tanzil (see the Quran pack's notice).\n"}
    write_pack(out_root, f"quranenc-{lang}", manifest, documents, rows, notice)


def quranenc_index(cache):
    """Title and version of each QuranEnc work, from the site's index page (the API list has translations only)."""
    import html as htmlmod
    page = open(fetch("https://quranenc.com/en/home", os.path.join(cache, "home.html")), encoding="utf-8").read()
    return page, htmlmod


def tafsir_meta(key, cache):
    page, htmlmod = quranenc_index(cache)
    i = page.rfind(f"/browse/{key}")
    m = re.search(r"(\d\d)/(\d\d)/(\d{4}) - V([\d.]+)", page[i:i + 4000]) if i >= 0 else None
    if not m:
        raise SystemExit(f"{key}: version not found on the QuranEnc index page")
    browse = open(fetch(f"https://quranenc.com/en/browse/{key}", os.path.join(cache, f"{key}.html")), encoding="utf-8").read()
    t = re.search(r"<title>(.*?)</title>", browse, re.S)
    title = htmlmod.unescape(t.group(1)).strip().split(" - Encyclopedia of the Noble Quran")[0] if t else key
    return {"title": title, "version": m.group(4), "issued": f"{m.group(3)}-{m.group(2)}-{m.group(1)}"}


def build_tafsir(lang, key, cache, out_root, today):
    """Al-Mukhtasar tafsir, one record per verse, stored unaltered with its version (QuranEnc terms)."""
    meta = tafsir_meta(key, cache)
    version = meta["version"]
    path = fetch(f"https://quranenc.com/downloads/sqlite/{key}.sqlite", os.path.join(cache, f"{key}.sqlite"))
    with open(path, "rb") as f:
        if f.read(15) != b"SQLite format 3":
            raise SystemExit(f"{key}: QuranEnc did not return a SQLite file (not offered for download)")
    db = sqlite3.connect(path)
    doc_id = f"quranenc-tafsir-{lang}"
    rows = []
    for sura, aya, tr, fn in db.execute("SELECT sura, aya, translation, footnotes FROM translations ORDER BY sura, aya"):
        tr, fn = clean(tr), clean(fn)
        body, kinds = compact([{"kind": "tafsir", "lang": lang, "text": tr}, {"kind": "footnotes", "lang": lang, "text": fn}])
        section = {"type": "tafsir", "publisher": "quranenc", "surah": sura, "ayah": aya, "translationKey": key,
                   "translationTitle": meta["title"], "version": version, "parts": kinds, "verse": f"quran:{sura}:{aya}"}
        rows.append({"id": f"qt:{lang}:{sura}:{aya}", "anchor": f"{sura}:{aya}", "section": section,
                     "original": {"docId": doc_id, "lang": lang, "text": body},
                     "url": f"https://quranenc.com/{qe_site(lang)}/browse/{key}/{sura}#{aya}"})
    if len(rows) != 6236:
        raise SystemExit(f"{key}: expected 6236 verses, found {len(rows)}")
    license_ = {"id": "QuranEnc-terms", "name": "QuranEnc.com terms: re-publishing permitted unaltered, with source, publisher and version",
                "url": "https://quranenc.com/", "attribution": f"QuranEnc.com — {meta['title']} (v{version})"}
    title = {"en": "Al-Mukhtasar in Interpreting the Noble Quran", "ar": "المختصر في تفسير القرآن الكريم"}
    if lang not in title:
        title[lang] = meta["title"]
    documents = [{"id": doc_id, "kind": "library", "title": title, "edition": f"QuranEnc.com {key} v{version}",
                  "publisher": "Tafsir Center for Quranic Studies, via QuranEnc.com", "language": lang,
                  "url": f"https://quranenc.com/{qe_site(lang)}/browse/{key}", "license": license_, "retrievedAt": today,
                  "citation": "item", "textType": "selectable", "tafsirOf": "quran-ar-tanzil"}]
    manifest = {"id": f"sources.quranenc-tafsir-{lang}", "kind": "sources", "schemaVersion": 1, "version": 1,
                "title": title, "languages": [lang],
                "source": {"name": "QuranEnc.com", "url": f"https://quranenc.com/{qe_site(lang)}/browse/{key}", "snapshot": today,
                           "translationKey": key, "version": version, "issued": meta["issued"]},
                "license": license_}
    notice = {"NOTICE-quranenc.txt": f"# {meta['title']}\n# Issued by the Tafsir Center for Quranic Studies\n"
              f"# Source: https://quranenc.com/{qe_site(lang)}/browse/{key}\n# Version: {version} ({meta['issued']})\n\n"
              "Al-Mukhtasar in Interpreting the Noble Quran from QuranEnc.com, published unaltered under its terms: no\n"
              "modification, addition or deletion; the publisher and source (QuranEnc.com) credited; the version number\n"
              "stated; the version information kept; notes sent to the source; new versions followed; no inappropriate\n"
              "advertisements. The Arabic verse text shown with it is from Tanzil (see the Quran pack's notice).\n"}
    write_pack(out_root, f"quranenc-tafsir-{lang}", manifest, documents, rows, notice)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "packs", "sources"))
    ap.add_argument("--cache", default=os.path.join(ROOT, ".cache"))
    ap.add_argument("--only", choices=["hadith", "translations", "tafsir"], help="build one group of packs")
    a = ap.parse_args()
    today = datetime.date.today().isoformat()
    he_cache = os.path.join(a.cache, "hadeethenc")
    qe_cache = os.path.join(a.cache, "quranenc")
    os.makedirs(he_cache, exist_ok=True)
    os.makedirs(qe_cache, exist_ok=True)
    if a.only in (None, "hadith"):
        for lang in HE_LANGS:
            build_hadeethenc(lang, he_cache, a.out, today)
    if a.only in (None, "tafsir"):
        for lang, key in TAFSIR_KEYS.items():
            build_tafsir(lang, key, qe_cache, a.out, today)
    if a.only not in (None, "translations"):
        return
    lst = os.path.join(qe_cache, "list.json")
    fetch("https://quranenc.com/api/v1/translations/list", lst)
    translations = json.load(open(lst, encoding="utf-8"))["translations"]
    for lang, key in QE_KEYS.items():
        build_quranenc(lang, key, translations, qe_cache, a.out, today)


if __name__ == "__main__":
    main()
