#!/usr/bin/env python3
"""Classical books for the library: OpenITI texts (CC BY-NC-SA 4.0) turned into PDFs, one per printed volume.

    python3 tools/build_books.py <work dir> [book id ...]
    python3 tools/build_books.py --catalog <work dir>/out/catalog.json

Reads shared/books/books.json (the approved list: which OpenITI version of each book), downloads each text once into
<work dir>/src, and writes <work dir>/out/<book id>-v<NN>.pdf plus <work dir>/out/catalog.json (sizes and checksums for
the app's catalog). The author's text is kept as it is: only the machine markup is turned into pages, headings and
Qur'an brackets. Each printed page of the edition starts a new PDF page and carries its volume and page number, so a
reference to the printed book can be found. Needs Chromium (PLAYWRIGHT_BROWSERS_PATH or CHROME) and the Amiri font.
"""
import hashlib
import html
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIST = os.path.join(ROOT, "shared", "books", "books.json")
FONTS = os.path.join(ROOT, "shared", "books", "fonts")

PAGE = re.compile(r"PageV(\d+)P(\d+)")
MILESTONE = re.compile(r"\bms\d+\b")
TAG = re.compile(r"@[A-Z]{2,4}\d*@?")
ARABIC_DIGITS = str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩")


def source_url(version: str) -> str:
    """OpenITI keeps each author's folder in a repository per 25 years of death date (1206 -> 1225AH)."""
    author_book = version.rsplit(".", 1)[0]
    author = author_book.split(".")[0]
    year = int(author[:4])
    bucket = f"{((year - 1) // 25 + 1) * 25:04d}AH"
    return f"https://raw.githubusercontent.com/OpenITI/{bucket}/master/data/{author}/{author_book}/{version}"


def fetch(version: str, src_dir: str) -> str:
    path = os.path.join(src_dir, version)
    if not os.path.exists(path):
        os.makedirs(src_dir, exist_ok=True)
        # The file name carries the text's state in OpenITI: none, .mARkdown, .completed or .inProgress.
        for ext in ("", ".mARkdown", ".completed", ".inProgress"):
            try:
                with urllib.request.urlopen(source_url(version) + ext, timeout=300) as r:
                    data = r.read()
                break
            except urllib.error.HTTPError as e:
                if e.code != 404:
                    raise
        else:
            raise FileNotFoundError(version)
        with open(path + ".part", "wb") as f:
            f.write(data)
        os.replace(path + ".part", path)
    return open(path, encoding="utf-8").read()


def clean(text: str) -> str:
    text = MILESTONE.sub("", text)
    text = text.replace("@QB@", "﴿").replace("@QE@", "﴾")
    text = TAG.sub("", text)
    text = text.replace("%~%", "   ").replace("~~", " ")
    return re.sub(r"\s+", " ", text).strip()


NOTE_MARK = re.compile(r"\s?\(\d{1,3}\)")
INLINE_PAGE = re.compile(r"^\[ص: ?\d+\]$")
HADITH_NO = re.compile(r"^\d+\s*-\s*$")


# Markup the converter does not draw: the editors' "PageEndV03P064" page ends (Tafsir al-Tabari) and the
# biography field tags "--- teachers/students #" (Tarikh al-Islam). The words around them are kept.
SOURCE_TAG = re.compile(r"PageEndV\d+P\d+|---\s*(?:teachers/students|teachers|students|misc)\b\s*#?")


def repair(blocks):
    """Fixes the source's markup slips without touching the words: a "heading" that is really the rest of a sentence
    (an open quotation, a line starting with ":") goes back into its paragraph; a lone hadith number heads the next
    paragraph; inline page notes like [ص: 6] and footnote marks whose notes the source dropped are removed."""
    out = []
    pending_number = None
    for kind, text in blocks:
        text = SOURCE_TAG.sub(" ", NOTE_MARK.sub("", text))
        text = re.sub(r"\s{2,}", " ", text).strip()
        if not text:
            continue
        if kind != "p":
            t = text.strip()
            if not t or INLINE_PAGE.match(t):
                continue
            if HADITH_NO.match(t):
                pending_number = t
                continue
            prev = out[-1] if out else None
            if prev and prev[0] == "p" and (prev[1].count("«") > prev[1].count("»") or t.startswith(":")):
                prev[1] += " " + t
                continue
        if kind == "p" and out and out[-1][0] == "p" and out[-1][1].count("«") > out[-1][1].count("»"):
            out[-1][1] += " " + text
            continue
        if pending_number and kind == "p":
            text = pending_number + " " + text
            pending_number = None
        out.append([kind, text])
    return out


def parse(raw: str):
    """Returns volumes: {volume: [(page, [(kind, text)])]} in reading order. A page marker closes its page."""
    body = raw.split("#META#Header#End#", 1)[-1]
    # Join continuation lines to their paragraph, then split into blocks.
    blocks, cur = [], None
    for line in body.splitlines():
        if line.startswith("~~"):
            if cur is not None:
                cur[1] += " " + line[2:]
            continue
        if line.startswith("### "):
            m = re.match(r"### (\|+)\s*(.*)", line)
            if m:
                cur = ["h%d" % min(len(m.group(1)), 3), m.group(2)]
                blocks.append(cur)
                continue
        if line.startswith("# "):
            cur = ["p", line[2:]]
            blocks.append(cur)
            continue
        if line.strip() and cur is not None and not line.startswith("#"):
            cur[1] += " " + line
    blocks = repair(blocks)
    volumes, pending = {}, []
    last = (1, 0)

    def close(vol, page):
        nonlocal pending
        if pending:
            volumes.setdefault(vol, []).append((page, pending))
        pending = []

    for kind, text in blocks:
        # A block can hold several page ends: split on them.
        parts = PAGE.split(text)
        pieces = parts[0::3]
        marks = list(zip(parts[1::3], parts[2::3]))
        for i, piece in enumerate(pieces):
            for para in (piece.split(" | ") if kind == "p" else [piece]):
                t = clean(para.lstrip("| "))
                if t:
                    pending.append((kind, t))
            if i < len(marks):
                v, pg = int(marks[i][0]), int(marks[i][1])
                if v == 0:  # a page end whose number the source lost: the text simply flows on
                    continue
                last = (v, pg)
                close(*last)
    if pending:  # text after the last page marker: the next page of the same volume
        close(last[0], last[1] + 1)
    return volumes


CSS = """
@font-face { font-family: Amiri; src: url('%(fonts)s/Amiri-Regular.ttf'); }
@font-face { font-family: Amiri; font-weight: bold; src: url('%(fonts)s/Amiri-Bold.ttf'); }
@page { size: 148mm 210mm; margin: 14mm 13mm 16mm 13mm; }
html { direction: rtl; font-family: Amiri, serif; font-size: 15pt; line-height: 1.75; color: #1b1b1b; }
body { margin: 0; }
section.page { break-before: page; position: relative; }
section.page:first-of-type { break-before: auto; }
p { margin: 0 0 0.45em; text-align: justify; }
h1, h2, h3 { font-weight: bold; color: #0f4c5c; margin: 0.6em 0 0.35em; line-height: 1.5; }
h1 { font-size: 1.25em; } h2 { font-size: 1.12em; } h3 { font-size: 1.04em; }
.folio { font-size: 0.72em; color: #8a8a8a; white-space: nowrap; }
.title { break-after: page; text-align: center; padding-top: 30mm; }
.title .t { font-size: 1.9em; font-weight: bold; color: #0f4c5c; line-height: 1.4; }
.title .a { font-size: 1.2em; margin-top: 0.8em; }
.title .v { font-size: 1.05em; margin-top: 0.6em; color: #444; }
.title .src { font-size: 0.68em; color: #555; margin-top: 30mm; line-height: 1.6; }
"""


def volume_html(book: dict, vol: int, vol_count: int, pages) -> str:
    e = html.escape
    vol_label = f"الجزء {str(vol).translate(ARABIC_DIGITS)}" if vol_count > 1 else ""
    out = [f"<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'><style>{CSS % {'fonts': FONTS}}</style></head><body>"]
    out.append("<div class='title'>")
    out.append(f"<div class='t'>{e(book['title'])}</div><div class='a'>{e(book['author'])}</div>")
    if vol_label:
        out.append(f"<div class='v'>{vol_label}</div>")
    out.append(
        "<div class='src'>"
        f"نص المؤلف كما في طبعة: {e(book.get('edition', ''))}<br>"
        "المصدر: مجموعة OpenITI (KITAB)، برخصة المشاع الإبداعي CC BY-NC-SA 4.0، للاستخدام غير التجاري.<br>"
        "نص المؤلف دون تغيير؛ لا تشمل هذه النسخة حواشي المحققين.<br>"
        "Romanov, M. &amp; Seydi, M. OpenITI. doi:10.5281/zenodo.3082463"
        "</div></div>"
    )
    for page, blocks in pages:
        out.append("<section class='page'>")
        folio = (f"ج {str(vol).translate(ARABIC_DIGITS)} - " if vol_count > 1 else "") + f"ص {str(page).translate(ARABIC_DIGITS)}"
        # The printed page number closes the page's last paragraph, so it can never sit alone on a page of its own.
        last = len(blocks) - 1 if blocks and blocks[-1][0] == "p" else -1
        for i, (kind, text) in enumerate(blocks):
            mark = f" <span class='folio'>[{folio}]</span>" if i == last else ""
            out.append(f"<{kind}>{e(text)}{mark}</{kind}>")
        if last < 0:
            out.append(f"<p><span class='folio'>[{folio}]</span></p>")
        out.append("</section>")
    out.append("</body></html>")
    return "".join(out)


def chrome() -> str:
    if os.environ.get("CHROME"):
        return os.environ["CHROME"]
    base = os.environ.get("PLAYWRIGHT_BROWSERS_PATH", "/opt/pw-browsers")
    for name in sorted(os.listdir(base), reverse=True):
        p = os.path.join(base, name, "chrome-linux", "chrome")
        if name.startswith("chromium-") and os.path.exists(p):
            return p
    raise SystemExit("Chromium not found")


def to_pdf(html_path: str, pdf_path: str) -> None:
    subprocess.run([chrome(), "--headless", "--no-sandbox", "--disable-gpu", "--no-pdf-header-footer",
                    "--generate-pdf-document-outline", f"--print-to-pdf={pdf_path}", "file://" + html_path],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=3600)


def pdf_pages(path: str) -> int:
    out = subprocess.run(["pdfinfo", path], capture_output=True, text=True, check=True).stdout
    return int(re.search(r"^Pages:\s+(\d+)", out, re.M).group(1))


BASE_URL = "https://raw.githubusercontent.com/MFvision/nearest-mosque-content/197bcd33f251f54bf3c1a0b7961342a9b42aa7ad/books/"
APP_CATALOG = os.path.join(ROOT, "shared", "content", "books-catalog.json")


def write_app_catalog(built: str) -> None:
    """shared/content/books-catalog.json, bundled with the apps: the approved list joined with the files built for it."""
    files = json.load(open(built, encoding="utf-8"))
    src = json.load(open(LIST, encoding="utf-8"))
    books = []
    for b in src["books"]:
        if b["id"] not in files:
            raise SystemExit(f"not built: {b['id']}")
        books.append({k: b[k] for k in ("id", "group", "title", "author", "edition")} | {"volumes": files[b["id"]]})
    out = {"schemaVersion": 1, "baseUrl": BASE_URL,
           "source": "OpenITI (KITAB), CC BY-NC-SA 4.0 · doi:10.5281/zenodo.3082463", "books": books}
    with open(APP_CATALOG, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"{len(books)} books, {sum(v['bytes'] for b in books for v in b['volumes']) // 2**20} MB")


def main() -> None:
    if sys.argv[1] == "--catalog":
        return write_app_catalog(sys.argv[2])
    work = sys.argv[1]
    wanted = set(sys.argv[2:])
    books = json.load(open(LIST, encoding="utf-8"))["books"]
    out_dir = os.path.join(work, "out")
    os.makedirs(out_dir, exist_ok=True)
    catalog_path = os.path.join(out_dir, "catalog.json")
    catalog = json.load(open(catalog_path, encoding="utf-8")) if os.path.exists(catalog_path) else {}
    for book in books:
        if wanted and book["id"] not in wanted:
            continue
        done = catalog.get(book["id"])
        if not wanted and done and all(os.path.exists(os.path.join(out_dir, f["file"])) for f in done):
            continue
        try:
            volumes = parse(fetch(book["version"], os.path.join(work, "src")))
        except Exception as e:  # one unreachable text does not stop the others
            print(f"{book['id']}: FAILED {e}", flush=True)
            continue
        vols = sorted(volumes)
        files = []
        for n, vol in enumerate(vols, 1):
            name = f"{book['id']}-v{n:02d}"
            html_path = os.path.join(work, "html", name + ".html")
            os.makedirs(os.path.dirname(html_path), exist_ok=True)
            open(html_path, "w", encoding="utf-8").write(volume_html(book, n, len(vols), volumes[vol]))
            pdf = os.path.join(out_dir, name + ".pdf")
            to_pdf(html_path, pdf)
            data = open(pdf, "rb").read()
            files.append({"file": name + ".pdf", "volume": n, "pages": pdf_pages(pdf), "bytes": len(data),
                          "sha256": hashlib.sha256(data).hexdigest()})
            print(f"{name}: {files[-1]['pages']} pages, {len(data) // 1024} KB", flush=True)
        catalog[book["id"]] = files
        json.dump(catalog, open(catalog_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
