#!/usr/bin/env python3
"""Copies a few of the app's common questions (English and Arabic, with their Qur'an and HadeethEnc references and
review status) into website/4/questions.json for the "Ask first" version of the landing page. The page shows them
as the app does: the summary, its sources, and the "awaiting scholar review" label while unreviewed.

    build_ask_sample.py
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
IDS = ["qibla", "prayer-times", "wudu", "what-is-islam"]


def main():
    data = json.loads((ROOT / "shared/content/common-questions.json").read_text())
    by_id = {q["id"]: q for q in data["questions"]}
    out = []
    for i in IDS:
        q = by_id[i]
        out.append({
            "id": i,
            "question": {k: q["question"][k] for k in ("en", "ar")},
            "summary": {k: q["summary"][k] for k in ("en", "ar")},
            "quran": [c.split(":", 1)[1] for c in q["citations"] if c.startswith("quran:")],
            "hadith": q.get("hadith") or [],
            "reviewed": q["reviewStatus"] == "reviewed",
        })
    dst = ROOT / "website/4/questions.json"
    dst.write_text(json.dumps(out, ensure_ascii=False, indent=1) + "\n")
    print(dst, len(out))


if __name__ == "__main__":
    main()
