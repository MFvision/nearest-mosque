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
bundled Tanzil Quran pack.
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
UA = "NearMosque-pack-builder/0.1 (+https://github.com/MFvision/nearest-mosque)"
# Parts of a record's text (title, hadith, explanation...) are stored once, in original.text, separated by
# U+2063 (INVISIBLE SEPARATOR); section.parts lists their kind and language in the same order. The search
# tokenizer drops the separator.
SEP = "\u2063"


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


def build_hadeethenc(lang, cache, out_root, today):
    path = fetch(f"https://hadeethenc.com/browse/download/{lang}", os.path.join(cache, f"he-{lang}.xlsx"))
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
                     "url": r.get("link") or f"https://hadeethenc.com/{lang}/browse/hadith/{r['id']}"})
    license_ = {"id": "HadeethEnc-terms", "name": "HadeethEnc.com terms: re-publishing permitted unaltered, with source, publisher and version",
                "url": "https://hadeethenc.com/", "attribution": f"HadeethEnc.com (v{version})"}
    documents = [{"id": doc_id, "kind": "library", "title": {"en": "Encyclopedia of Translated Prophetic Hadiths", "ar": "موسوعة الأحاديث النبوية المترجمة"},
                  "edition": f"HadeethEnc.com v{version}", "publisher": "HadeethEnc.com", "language": lang,
                  "url": f"https://hadeethenc.com/{lang}", "license": license_, "retrievedAt": today, "citation": "item", "textType": "selectable"}]
    manifest = {"id": f"sources.hadeethenc-{lang}", "kind": "sources", "schemaVersion": 1, "version": 1,
                "title": {"en": f"HadeethEnc hadiths ({lang})", "ar": "موسوعة الأحاديث النبوية المترجمة"}, "languages": [lang],
                "source": {"name": "HadeethEnc.com", "url": f"https://hadeethenc.com/{lang}", "snapshot": today, "version": version},
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
                     "url": f"https://quranenc.com/{lang}/browse/{key}/{sura}#{aya}"})
    license_ = {"id": "QuranEnc-terms", "name": "QuranEnc.com terms: re-publishing permitted unaltered, with source, publisher and version",
                "url": "https://quranenc.com/", "attribution": f"QuranEnc.com — {meta['title']} (v{version})"}
    documents = [{"id": doc_id, "kind": "library", "title": {"en": meta["title"], lang: meta["title"]}, "edition": f"QuranEnc.com {key} v{version}",
                  "publisher": "QuranEnc.com", "language": lang, "url": f"https://quranenc.com/{lang}/browse/{key}",
                  "license": license_, "retrievedAt": today, "citation": "item", "textType": "selectable", "translationOf": "quran-ar-tanzil"}]
    manifest = {"id": f"sources.quranenc-{lang}", "kind": "sources", "schemaVersion": 1, "version": 1,
                "title": {"en": meta["title"], lang: meta["title"]}, "languages": [lang],
                "source": {"name": "QuranEnc.com", "url": f"https://quranenc.com/{lang}/browse/{key}", "snapshot": today,
                           "translationKey": key, "version": version, "lastUpdate": meta.get("last_update")},
                "license": license_}
    notice = {"NOTICE-quranenc.txt": f"# {meta['title']}\n# Source: https://quranenc.com/{lang}/browse/{key}\n# Version: {version}\n\n"
              "Translation of the meanings of the Noble Quran from QuranEnc.com, published unaltered under its terms: no\n"
              "modification, addition or deletion; the publisher and source (QuranEnc.com) credited; the version number\n"
              "stated; the version information kept; notes sent to the source; new versions followed; no inappropriate\n"
              "advertisements. The Arabic verse text shown with it is from Tanzil (see the Quran pack's notice).\n"}
    write_pack(out_root, f"quranenc-{lang}", manifest, documents, rows, notice)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "packs", "sources"))
    ap.add_argument("--cache", default=os.path.join(ROOT, ".cache"))
    a = ap.parse_args()
    today = datetime.date.today().isoformat()
    he_cache = os.path.join(a.cache, "hadeethenc")
    qe_cache = os.path.join(a.cache, "quranenc")
    os.makedirs(he_cache, exist_ok=True)
    os.makedirs(qe_cache, exist_ok=True)
    for lang in HE_LANGS:
        build_hadeethenc(lang, he_cache, a.out, today)
    lst = os.path.join(qe_cache, "list.json")
    fetch("https://quranenc.com/api/v1/translations/list", lst)
    translations = json.load(open(lst, encoding="utf-8"))["translations"]
    for lang, key in QE_KEYS.items():
        build_quranenc(lang, key, translations, qe_cache, a.out, today)


if __name__ == "__main__":
    main()
