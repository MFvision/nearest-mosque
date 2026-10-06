#!/usr/bin/env python3
"""Copy shared/content/common-questions.json into the Quran pack after editing it.

    python3 tools/refresh_questions.py

Checks that every cited verse is in the Quran pack and every cited hadith (HadeethEnc item id) is in
the Arabic and English HadeethEnc packs (the apps show the reader's language when it has the item,
then English, then Arabic), then updates the pack manifest (file size, hash) and raises its version
so installed apps take the new questions."""
import hashlib
import json
import os
import shutil
import subprocess

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.join(ROOT, "shared", "content", "common-questions.json")
PACK = os.path.join(ROOT, "packs", "sources", "quran-tanzil-pickthall")
HADITH_PACKS = ["hadeethenc-ar", "hadeethenc-en"]
LANGS = {"en", "ar", "ur", "tr", "id", "fr", "es"}


def ids(path, key):
    with open(path, encoding="utf-8") as f:
        return {key(json.loads(line)) for line in f}


def committed_manifest():
    rel = os.path.relpath(os.path.join(PACK, "manifest.json"), ROOT)
    try:
        out = subprocess.run(["git", "-C", ROOT, "show", f"HEAD:{rel}"], capture_output=True, check=True, text=True).stdout
        return json.loads(out)
    except (OSError, subprocess.CalledProcessError, ValueError):
        return None


def main():
    questions = json.load(open(SRC, encoding="utf-8"))["questions"]
    verses = ids(os.path.join(PACK, "chunks.jsonl"), lambda c: c["id"])
    hadith = {p: ids(os.path.join(ROOT, "packs", "sources", p, "chunks.jsonl"), lambda c: c["section"]["itemId"]) for p in HADITH_PACKS}
    seen = set()
    for q in questions:
        if q["id"] in seen:
            raise SystemExit(f"duplicate question id {q['id']}")
        seen.add(q["id"])
        for field in ("question", "summary", "triggers"):
            if set(q[field]) != LANGS:
                raise SystemExit(f"{q['id']}: {field} must have exactly {sorted(LANGS)}")
        missing = [c for c in q["citations"] if c not in verses]
        if missing:
            raise SystemExit(f"{q['id']} cites missing verses {missing}")
        for p, have in hadith.items():
            gone = [h for h in q.get("hadith", []) if h not in have]
            if gone:
                raise SystemExit(f"{q['id']} cites hadith {gone} missing from {p}")
    dst = os.path.join(PACK, "common-questions.json")
    shutil.copyfile(SRC, dst)
    manifest_path = os.path.join(PACK, "manifest.json")
    manifest = json.load(open(manifest_path, encoding="utf-8"))
    data = open(dst, "rb").read()
    entry = next(f for f in manifest["files"] if f["path"] == "common-questions.json")
    entry["bytes"], entry["sha256"] = len(data), hashlib.sha256(data).hexdigest()
    # One version step per release, however often this runs: count from the committed manifest.
    committed = committed_manifest() or manifest
    old = next(f for f in committed["files"] if f["path"] == "common-questions.json")
    manifest["version"] = committed["version"] + (old["sha256"] != entry["sha256"])
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"{len(questions)} common questions -> {dst} (pack version {manifest['version']})")


if __name__ == "__main__":
    main()
