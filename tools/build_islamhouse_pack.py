#!/usr/bin/env python3
"""Build IslamHouse library packs (one per interface language) from the public IslamHouse API.

  python3 tools/build_islamhouse_pack.py --langs en ar ur tr id fr es --out packs/sources

Each item (book, article, fatwa, video, audio lecture) becomes one searchable record: title, description, authors and the
beginning of its text when the API provides it (`full_description`), plus links to the item page and
its attachments (PDF/EPUB/DOC, MP4, MP3). Full books stay on IslamHouse: the app shows the record and opens the
link. Records are cached in --cache so re-runs only fetch what changed.

Licensing: IslamHouse publishes its material for free distribution, but permission to redistribute it
inside an app has not been confirmed in writing; see docs/04-data-and-licenses.md.
"""
import argparse
import datetime
import hashlib
import html
import json
import os
import re
import time
import urllib.request

API = "https://api3.islamhouse.com/v3/paV29H2gm56kvLPy"
TYPES = ["books", "articles", "fatwa", "videos", "audios"]
TEXT_CAP = 1800
# Bump when installed copies must be re-indexed (2: library stemming and variants).
PACK_VERSION = 2
NAMES = {
    "en": "IslamHouse library (English)", "ar": "مكتبة دار الإسلام (العربية)", "ur": "IslamHouse library (اردو)",
    "tr": "IslamHouse kütüphanesi (Türkçe)", "id": "Perpustakaan IslamHouse (Indonesia)",
    "fr": "Bibliothèque IslamHouse (français)", "es": "Biblioteca IslamHouse (español)",
}


def get(url, tries=4):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "NearMosque-pack-builder/0.1"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001 - retried, then raised
            if i == tries - 1:
                raise
            time.sleep(2 ** i)
            print(f"  retry {url}: {e}")


def strip_html(s):
    if not s:
        return ""
    s = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", s, flags=re.S | re.I)
    s = re.sub(r"<br\s*/?>|</p>|</li>|</h\d>", "\n", s, flags=re.I)
    s = re.sub(r"<[^>]+>", " ", s)
    s = html.unescape(s)
    s = re.sub(r"[ \t ]+", " ", s)
    s = re.sub(r"\n\s*\n+", "\n", s)
    return s.strip()


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def fetch_lang(lang, cache_dir):
    os.makedirs(cache_dir, exist_ok=True)
    items = []
    for t in TYPES:
        page, pages = 1, 1
        while page <= pages:
            cache = os.path.join(cache_dir, f"{lang}-{t}-{page}.json")
            if os.path.exists(cache):
                d = json.load(open(cache, encoding="utf-8"))
            else:
                d = get(f"{API}/main/{t}/{lang}/{lang}/{page}/50/json")
                json.dump(d, open(cache, "w", encoding="utf-8"), ensure_ascii=False)
                time.sleep(0.2)
            pages = int(d.get("links", {}).get("pages_number") or 1)
            items += [dict(it, _type=t) for it in d.get("data", [])]
            page += 1
        print(f"{lang} {t}: {pages} pages")
    return items


def library_name(lang):
    if lang in NAMES:
        return NAMES[lang]
    langs = json.load(open(os.path.join(os.path.dirname(__file__), "..", "shared", "i18n", "languages.json"), encoding="utf-8"))
    native = next((l["name"] for l in langs["languages"] if l["code"] == lang), lang)
    return f"IslamHouse ({native})"


def build(lang, items, out_root, src=None):
    """One library pack for the app language `lang`; `src` is IslamHouse's code for it when different (ckb → ku)."""
    src = src or lang
    out = os.path.join(out_root, f"islamhouse-{lang}")
    os.makedirs(out, exist_ok=True)
    doc_id = f"islamhouse-{lang}"
    seen, rows = set(), []
    for it in sorted(items, key=lambda x: (TYPES.index(x["_type"]), -(x.get("add_date") or 0), x["id"])):
        if it["id"] in seen or not (it.get("title") or "").strip():
            continue
        seen.add(it["id"])
        title = it["title"].strip()
        desc = strip_html(it.get("description"))
        body = strip_html(it.get("full_description"))
        if body.startswith(title):
            body = body[len(title):].lstrip()
        text = "\n".join(p for p in [title, desc if desc != title else "", body[:TEXT_CAP]] if p)
        authors = [p["title"] for p in it.get("prepared_by") or [] if p.get("title") and p.get("kind") in ("author", "source", "translator")]
        atts = [{"ext": a.get("extension_type"), "size": a.get("size"), "url": a.get("url")} for a in it.get("attachments") or [] if a.get("url")]
        section = {"type": it["_type"], "itemId": it["id"], "title": title}
        if authors:
            section["authors"] = authors[:3]
        if atts:
            section["attachment"] = atts[0]["url"]
            section["attachmentType"] = atts[0]["ext"]
            section["attachmentSize"] = atts[0]["size"]
        if body:
            section["hasText"] = True
        rows.append({
            "id": f"ih:{lang}:{it['id']}",
            "anchor": title,
            "section": section,
            "original": {"docId": doc_id, "lang": lang, "text": text},
            "url": f"https://islamhouse.com/{src}/{it['_type']}/{it['id']}/",
        })
    with open(os.path.join(out, "chunks.jsonl"), "w", encoding="utf-8") as f:
        for i, r in enumerate(rows, start=1):
            f.write(json.dumps({"id": r["id"], "seq": i, **{k: v for k, v in r.items() if k != "id"}}, ensure_ascii=False, separators=(",", ":")) + "\n")
    today = datetime.date.today().isoformat()
    documents = [{
        "id": doc_id, "kind": "library",
        "title": {"en": "IslamHouse.com", "ar": "دار الإسلام IslamHouse.com", lang: library_name(lang)},
        "edition": f"IslamHouse API v3 catalogue, {today}",
        "publisher": "IslamHouse.com", "language": lang, "url": f"https://islamhouse.com/{src}/",
        "license": {
            "id": "IslamHouse-free-distribution-unconfirmed",
            "name": "IslamHouse material for free distribution; in-app redistribution permission not yet confirmed",
            "url": "https://islamhouse.com/", "attribution": "IslamHouse.com",
        },
        "retrievedAt": today, "citation": "item", "textType": "selectable",
    }]
    json.dump(documents, open(os.path.join(out, "documents.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    open(os.path.join(out, "NOTICE-islamhouse.txt"), "w", encoding="utf-8").write(
        "Catalogue records (titles, descriptions, authors and the beginning of item texts) from IslamHouse.com,\n"
        "retrieved through the public IslamHouse API (api3.islamhouse.com). Full books, articles and fatwas are\n"
        "opened on IslamHouse.com. IslamHouse publishes its material for free distribution; written permission for\n"
        "redistribution inside this app has not yet been confirmed. Remove this pack if permission is refused.\n")
    files = []
    for name in ["chunks.jsonl", "documents.json", "NOTICE-islamhouse.txt"]:
        p = os.path.join(out, name)
        files.append({"path": name, "bytes": os.path.getsize(p), "sha256": sha256_file(p)})
    manifest = {
        "id": f"sources.islamhouse-{lang}", "kind": "sources", "schemaVersion": 1, "version": PACK_VERSION,
        "title": {"en": f"IslamHouse library ({lang})", lang: library_name(lang)},
        "languages": [lang], "recordCount": len(rows),
        "categories": {t: sum(1 for r in rows if r["section"]["type"] == t) for t in TYPES},
        "source": {"name": "IslamHouse.com API v3", "url": "https://islamhouse.com/", "snapshot": today,
                   "builtAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")},
        "license": documents[0]["license"],
        "files": files,
    }
    json.dump(manifest, open(os.path.join(out, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"{lang}: {len(rows)} records, chunks.jsonl {files[0]['bytes'] / 1e6:.1f} MB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--langs", nargs="+", default=["en", "ar", "ur", "tr", "id", "fr", "es"])
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "packs", "sources"))
    ap.add_argument("--cache", default=os.path.join(os.path.dirname(__file__), "..", ".cache", "islamhouse"))
    a = ap.parse_args()
    for lang in a.langs:
        build(lang, fetch_lang(lang, a.cache), a.out)


if __name__ == "__main__":
    main()
