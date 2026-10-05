#!/usr/bin/env python3
"""Build the Ibn Baz fatwa library pack from the official site binbaz.org.sa.

  python3 tools/build_binbaz_pack.py --out packs/sources          # all four collections
  python3 tools/build_binbaz_pack.py --kinds 1 --limit-pages 2      # quick sample

Each fatwa becomes one searchable record: title, the question, the full answer (capped at
--text-cap characters; the reader links to the page for the rest), the printed source the site cites
(e.g. Majmu' Fatawa volume/page) and its topics. The site states: "جميع الحقوق محفوظة والنقل متاح لكل
مسلم بشرط ذكر المصدر" (copying is permitted on condition of citing the source), so every record keeps
the source name and the link to its page. robots.txt allows these pages; requests are rate limited and
parsed results cached in --cache, so a re-run only fetches what is missing.
"""
import argparse
import concurrent.futures as cf
import datetime
import hashlib
import html
import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

SITE = "https://binbaz.org.sa"
KINDS = {1: "مجموع الفتاوى", 2: "نور على الدرب", 3: "فتاوى الدروس", 4: "فتاوى الجامع الكبير"}
UA = "NearMosque-pack-builder/0.1 (+https://github.com/MFvision/nearest-mosque)"
_lock = threading.Lock()
_last = [0.0]


def get(url, min_interval, tries=5):
    """GET with a global minimum interval between requests (polite to the site) and retries."""
    for i in range(tries):
        with _lock:
            wait = _last[0] + min_interval - time.time()
            if wait > 0:
                time.sleep(wait)
            _last[0] = time.time()
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ar"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read().decode("utf-8", errors="replace")
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if i == tries - 1:
                raise
        except Exception:  # noqa: BLE001 - retried, then raised
            if i == tries - 1:
                raise
        time.sleep(2 ** (i + 1))


def text_of(fragment):
    s = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", fragment, flags=re.S | re.I)
    s = re.sub(r"<sup[^>]*>.*?</sup>", "", s, flags=re.S)
    s = re.sub(r"<br\s*/?>|</p>|</div>|</li>|</h\d>", "\n", s, flags=re.I)
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s).replace("\xa0", " ")
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\s*\n\s*", "\n", s)
    return s.strip()


def parse_fatwa(page, url, fid, kind):
    m = re.search(r"<h1[^>]*>(.*?)</h1>", page, re.S)
    title = text_of(m.group(1)) if m else ""
    m = re.search(r'<h2 class="article-title article-title__question[^"]*"[^>]*>(.*?)</h2>', page, re.S)
    question = text_of(m.group(1)) if m else ""
    question = re.sub(r"^س\s*:\s*", "", question).strip()
    m = re.search(r'<div itemprop="articleBody" class="article-content">(.*?)</article>', page, re.S)
    body = m.group(1) if m else ""
    cite = [text_of(c) for c in re.findall(r"<cite>(.*?)</cite>", body, re.S)]
    body = re.sub(r'<section class="footnotes">.*?</section>', "", body, flags=re.S)
    answer = re.sub(r"^ج\s*:\s*", "", text_of(body)).strip()
    cats = [text_of(c) for c in re.findall(r'class="categories__item">(.*?)</a>', page, re.S)]
    if not title or not answer:
        return None
    return {"id": fid, "kind": kind, "url": url, "title": title, "question": question, "answer": answer,
            "source": cite[0] if cite else None, "categories": list(dict.fromkeys(cats))[:3]}


def list_kind(kind, cache, interval, limit_pages=None):
    first = get(f"{SITE}/fatwas/kind/{kind}", interval) or ""
    pages = max([int(p) for p in re.findall(r"page=(\d+)", first)] or [1])
    if limit_pages:
        pages = min(pages, limit_pages)
    out = []

    def one(p):
        path = os.path.join(cache, f"list-{kind}-{p}.json")
        if os.path.exists(path):
            return json.load(open(path, encoding="utf-8"))
        page = first if p == 1 else get(f"{SITE}/fatwas/kind/{kind}?page={p}", interval) or ""
        urls = sorted(set(re.findall(r'href="(https://binbaz\.org\.sa/fatwas/(\d+)/[^"#?]+)"', page)), key=lambda x: int(x[1]))
        items = [{"url": u, "id": int(i)} for u, i in urls]
        json.dump(items, open(path, "w", encoding="utf-8"))
        return items

    with cf.ThreadPoolExecutor(4) as ex:
        for items in ex.map(one, range(1, pages + 1)):
            out += items
    print(f"kind {kind} ({KINDS[kind]}): {pages} pages, {len(out)} links")
    return out


def fetch_all(links, cache, interval):
    os.makedirs(os.path.join(cache, "f"), exist_ok=True)
    done, failed = [], []

    def one(link):
        path = os.path.join(cache, "f", f"{link['id']}.json")
        if os.path.exists(path):
            return json.load(open(path, encoding="utf-8"))
        page = get(link["url"], interval)
        rec = parse_fatwa(page, link["url"], link["id"], link["kind"]) if page else None
        json.dump(rec, open(path, "w", encoding="utf-8"), ensure_ascii=False)
        return rec

    with cf.ThreadPoolExecutor(4) as ex:
        futs = {ex.submit(one, l): l for l in links}
        for n, f in enumerate(cf.as_completed(futs), 1):
            try:
                r = f.result()
                (done if r else failed).append(r or futs[f])
            except Exception as e:  # noqa: BLE001 - reported in the inventory
                failed.append({**futs[f], "error": str(e)[:200]})
            if n % 500 == 0:
                print(f"  {n}/{len(links)} fetched ({len(failed)} failed)", flush=True)
    return done, failed


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def build(records, failed, discovered, out_root, text_cap):
    out = os.path.join(out_root, "binbaz-ar")
    os.makedirs(out, exist_ok=True)
    doc_id = "binbaz-ar"
    records = sorted(records, key=lambda r: (r["kind"], r["id"]))
    with open(os.path.join(out, "chunks.jsonl"), "w", encoding="utf-8") as f:
        for seq, r in enumerate(records, start=1):
            answer = r["answer"]
            truncated = len(answer) > text_cap
            if truncated:
                cut = answer.rfind(" ", 0, text_cap)
                answer = answer[: cut if cut > text_cap * 0.8 else text_cap].rstrip() + " …"
            section = {"type": "fatwa", "publisher": "binbaz", "itemId": r["id"], "title": r["title"],
                       "collection": KINDS[r["kind"]], "question": r["question"]}
            if r.get("source"):
                section["source"] = r["source"]
            if r.get("categories"):
                section["categories"] = r["categories"]
            if truncated:
                section["truncated"] = True
            text = "\n".join(p for p in [r["title"], r["question"], answer] if p)
            f.write(json.dumps({"id": f"bb:{r['id']}", "seq": seq, "anchor": r["title"], "section": section,
                                "original": {"docId": doc_id, "lang": "ar", "text": text}, "url": r["url"]},
                               ensure_ascii=False, separators=(",", ":")) + "\n")
    today = datetime.date.today().isoformat()
    license_ = {
        "id": "binbaz-attribution",
        "name": "binbaz.org.sa: «جميع الحقوق محفوظة والنقل متاح لكل مسلم بشرط ذكر المصدر» (copying permitted with the source cited)",
        "url": SITE + "/", "attribution": "الموقع الرسمي لسماحة الشيخ ابن باز رحمه الله — binbaz.org.sa",
    }
    documents = [{
        "id": doc_id, "kind": "library",
        "title": {"ar": "فتاوى الشيخ ابن باز (الموقع الرسمي)", "en": "Fatwas of Shaykh Ibn Baz (official site)"},
        "edition": f"binbaz.org.sa, retrieved {today}", "publisher": "binbaz.org.sa", "language": "ar",
        "url": SITE + "/fatwas", "license": license_, "retrievedAt": today, "citation": "item", "textType": "selectable",
    }]
    json.dump(documents, open(os.path.join(out, "documents.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    open(os.path.join(out, "NOTICE-binbaz.txt"), "w", encoding="utf-8").write(
        "Fatwas of Shaykh Abd al-Aziz ibn Baz (may Allah have mercy on him) from his official website, binbaz.org.sa.\n"
        "The site states: «جميع الحقوق محفوظة والنقل متاح لكل مسلم بشرط ذكر المصدر» — copying is permitted on condition\n"
        "of citing the source. Every record keeps its source (as printed on the site) and a link to its page.\n"
        f"Long answers are shortened to {text_cap} characters here; the full text is on the linked page.\n")
    inventory = {"discovered": discovered, "indexed": len(records), "failed": len(failed),
                 "truncated": sum(1 for r in records if len(r["answer"]) > text_cap),
                 "byCollection": {KINDS[k]: sum(1 for r in records if r["kind"] == k) for k in KINDS},
                 "failedItems": failed[:200]}
    json.dump(inventory, open(os.path.join(out, "inventory.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    files = []
    for name in ["chunks.jsonl", "documents.json", "NOTICE-binbaz.txt", "inventory.json"]:
        p = os.path.join(out, name)
        files.append({"path": name, "bytes": os.path.getsize(p), "sha256": sha256_file(p)})
    manifest = {
        "id": "sources.binbaz-ar", "kind": "sources", "schemaVersion": 1, "version": 1,
        "title": {"ar": "فتاوى ابن باز", "en": "Ibn Baz fatwas"}, "languages": ["ar"], "recordCount": len(records),
        "categories": inventory["byCollection"],
        "source": {"name": "binbaz.org.sa", "url": SITE + "/", "snapshot": today,
                   "builtAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")},
        "license": license_, "files": files,
    }
    json.dump(manifest, open(os.path.join(out, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"binbaz-ar: {len(records)} records ({inventory['truncated']} shortened), {len(failed)} failed, "
          f"chunks.jsonl {files[0]['bytes'] / 1e6:.1f} MB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--kinds", nargs="+", type=int, default=list(KINDS))
    ap.add_argument("--limit-pages", type=int)
    ap.add_argument("--interval", type=float, default=0.2, help="minimum seconds between requests")
    ap.add_argument("--text-cap", type=int, default=1500)
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "packs", "sources"))
    ap.add_argument("--cache", default=os.path.join(os.path.dirname(__file__), "..", ".cache", "binbaz"))
    a = ap.parse_args()
    os.makedirs(a.cache, exist_ok=True)
    links = {}
    for k in a.kinds:
        for l in list_kind(k, a.cache, a.interval, a.limit_pages):
            links.setdefault(l["id"], {**l, "kind": k})
    records, failed = fetch_all(list(links.values()), a.cache, a.interval)
    build(records, failed, len(links), a.out, a.text_cap)


if __name__ == "__main__":
    main()
