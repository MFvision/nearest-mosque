#!/usr/bin/env python3
"""Reference implementation of library search (IslamHouse, Ibn Baz fatwas). The Kotlin
(LibraryRetriever in android/core) and Swift (NMCore) ports must give the same results for
shared/fixtures/library-retrieval.json. Quran search (reference_search.py) is separate and unchanged.

Differences from Quran search:
  * light Arabic stemming (light10 rules: leading و, then common suffixes) on top of the shared
    normalizer; every indexed word is stored both as written and stemmed;
  * prefix matching for query words of 3+ letters ("pray" finds "prayer", «صلا» finds «صلاته»);
  * the multilingual lexicon (shared/content/lexicon.json) adds the other words of a word's group at
    reduced weight, and a match on a translation counts as covering the original word;
  * gates: score >= 0.5 and coverage >= 0.6.

  python3 tools/library_search.py --gen        # regenerate the fixture
  python3 tools/library_search.py "question"   # inspect against the fixture corpus
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from reference_search import is_arabic_letter, normalize as tokens  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
K1, B = 1.2, 0.75
EXPANSION_WEIGHT = 0.5
CONTEXT_WEIGHT = 0.3
MAX_PASSAGES = 5
MIN_SCORE = 0.5
MIN_COVERAGE = 0.6
PREFIX_MIN = 3
AR_SUFFIXES = ["ها", "ان", "ات", "ون", "ين", "يه", "ه", "ي"]
LATIN_SUFFIXES = ["ing", "ers", "er", "ed"]


def light_stem(tok):
    if not any(is_arabic_letter(c) for c in tok):
        for s in LATIN_SUFFIXES:
            if tok.endswith(s) and len(tok) - len(s) >= 4:
                return tok[: -len(s)]
        return tok
    for s in AR_SUFFIXES:
        if tok.endswith(s) and len(tok) - len(s) >= 3:
            tok = tok[: -len(s)]
    return tok


def variants(tok):
    """Forms a word is indexed and searched under: as written; its stem; for Arabic, the stem without a
    leading و (light10's conjunction rule, kept as an extra form so roots such as وقت survive); and for
    a stem ending in ت after a suffix was removed (صلاته -> صلات) the form without it (صلا, the stem of
    صلاة)."""
    out = [tok]

    def add(x):
        if x not in out:
            out.append(x)

    arabic = any(is_arabic_letter(c) for c in tok)
    bases = [tok] + ([tok[1:]] if arabic and tok.startswith("و") and len(tok) > 3 else [])
    for base in bases:
        st = light_stem(base)
        add(st)
        if arabic and st != base and st.endswith("ت") and len(st) >= 4:
            add(st[:-1])
    return out


def index_tokens(text):
    out = []
    for t in tokens(text):
        out += variants(t)
    return out


def matches(token, v):
    return token.startswith(v) if len(v) >= PREFIX_MIN else token == v


class Lexicon:
    def __init__(self, raw):
        self.groups = []
        for g in raw.get("groups", []):
            members = set()
            for lang in sorted(g):
                for w in g[lang]:
                    t = tokens(w)
                    if len(t) == 1 and len(t[0]) >= 3:
                        members.add(t[0])
            if len(members) > 1:
                self.groups.append(sorted(members))
        self.by_stem = {}
        for i, g in enumerate(self.groups):
            for m in g:
                self.by_stem.setdefault(light_stem(m), []).append(i)

    def expansions(self, word):
        stem = light_stem(word)
        out = []
        for gi in self.by_stem.get(stem, []):
            for m in self.groups[gi]:
                if light_stem(m) != stem and m not in out:
                    out.append(m)
        return out


def library_stopwords(stopwords):
    domain = {t for w in stopwords.get("_domain", []) for t in tokens(w)}
    stop = set()
    for k, words in stopwords.items():
        if k.startswith("_"):
            continue
        for w in words:
            ts = tokens(w)
            if not any(t in domain for t in ts):
                stop.update(ts)
    return stop


class MemoryStore:
    def __init__(self, docs):
        self.docs = [(d["id"], d["seq"], index_tokens(d["text"])) for d in docs]
        self.total = len(self.docs)
        self.avg = sum(len(t) for _, _, t in self.docs) / self.total if self.docs else 0.0

    def df(self, vs):
        return sum(1 for _, _, toks in self.docs if any(matches(t, v) for t in toks for v in vs))

    def candidates(self, vs):
        return [d for d in self.docs if any(matches(t, v) for t in d[2] for v in vs)]


def retrieve(store, stop, lexicon, question, context=()):
    content = []
    for t in tokens(question):
        if t not in stop and t not in content:
            content.append(t)
    terms, seen = [], set()

    def add(word, weight, owner):
        if word in seen:
            return
        seen.add(word)
        terms.append({"word": word, "variants": variants(word), "weight": weight, "owner": owner})

    for i, w in enumerate(content):
        add(w, 1.0, i)
    for i, w in enumerate(content):
        for e in lexicon.expansions(w):
            add(e, EXPANSION_WEIGHT, i)
    if len(content) < 4:
        for prev in context:
            for t in tokens(prev):
                if t not in stop:
                    add(t, CONTEXT_WEIGHT, None)
    if not content or store.total == 0:
        return []
    n = store.total
    for term in terms:
        df = store.df(term["variants"])
        term["idf"] = math.log(1 + (n - df + 0.5) / (df + 0.5))
    all_variants = []
    for term in terms:
        for v in term["variants"]:
            if v not in all_variants:
                all_variants.append(v)
    scored = []
    for cid, seq, toks in store.candidates(all_variants):
        dl = len(toks)
        s, covered = 0.0, set()
        for term in terms:
            tf = sum(1 for t in toks if any(matches(t, v) for v in term["variants"]))
            if tf == 0:
                continue
            s += term["weight"] * term["idf"] * tf * (K1 + 1) / (tf + K1 * (1 - B + B * dl / store.avg))
            if term["owner"] is not None:
                covered.add(term["owner"])
        coverage = len(covered) / len(content)
        s *= coverage
        if s >= MIN_SCORE and coverage >= MIN_COVERAGE:
            scored.append((s, seq, cid, coverage))
    scored.sort(key=lambda x: (-x[0], x[1]))
    return [{"id": cid, "score": round(s, 6), "coverage": round(cov, 6)} for s, _, cid, cov in scored[:MAX_PASSAGES]]


STEM_CASES = ["travelling", "prayer", "ruling", "الصلاة", "صلاته", "والصلاة", "بصلاته", "الوضوء", "وضوئه", "المسلمين", "مسلمون", "المسلمات", "الزكاة",
              "زكاته", "صيامها", "عليه", "وقت", "والدين", "كتابه", "حكم", "أحكام", "الصيام", "prayers", "fasting", "zekât"]

CORPUS = [
    {"id": "d1", "text": "حكم تارك الصلاة\nما حكم من ترك الصلاة متعمدا؟\nمن ترك الصلاة جاحدا لوجوبها كفر، ومن تركها تهاونا فالصحيح أنه يكفر."},
    {"id": "d2", "text": "صفة الوضوء\nكيف أتوضأ؟\nيغسل وجهه ويديه إلى المرفقين ويمسح رأسه ويغسل رجليه، وهذا صفة وضوئه ﷺ."},
    {"id": "d3", "text": "زكاة الذهب\nهل في الذهب المعد للاستعمال زكاة؟\nالصحيح وجوب زكاته إذا بلغ النصاب."},
    {"id": "d4", "text": "صيام الست من شوال\nما فضل صيامها؟\nمن صام رمضان ثم أتبعه ستا من شوال كان كصيام الدهر."},
    {"id": "d5", "text": "The Prayer of the Traveller\nHow does a traveller pray?\nThe traveller shortens the four-unit prayers to two."},
    {"id": "d6", "text": "Fasting in Ramadan\nWho must fast?\nFasting Ramadan is obligatory on every adult Muslim who is able."},
    {"id": "d7", "text": "What is Islam?\nIslam is submission to Allah alone, with obedience and freedom from polytheism."},
    {"id": "d8", "text": "Namaz ve abdest\nAbdest nasıl alınır? Namazdan önce abdest almak farzdır."},
    {"id": "d9", "text": "حكم الموسيقى والغناء\nما حكم سماع الأغاني؟\nسماع الأغاني والموسيقى محرم."},
    {"id": "d10", "text": "مواقيت الصلوات الخمس\nمتى يبدأ وقت صلاة الفجر؟\nيبدأ وقت الفجر بطلوع الفجر الثاني."},
    {"id": "d11", "text": "Zakat on savings\nIs zakat due on money in the bank?\nZakat is due when savings reach the nisab and a year passes."},
    {"id": "d12", "text": "الطلاق في الحيض\nما حكم طلاق الحائض؟\nطلاق الحائض محرم عند أهل العلم."},
]
CASES = [
    {"q": "ما حكم تارك الصلاة؟"},
    {"q": "صلاته"},
    {"q": "كيف الوضوء"},
    {"q": "زكاة الذهب"},
    {"q": "How do I pray when travelling?"},
    {"q": "What is the ruling on prayer?"},
    {"q": "ruling on music"},
    {"q": "fasting ramadan"},
    {"q": "abdest"},
    {"q": "What is Islam?"},
    {"q": "zakat bank"},
    {"q": "طلاق الحائض"},
    {"q": "capital of France"},
    {"q": "وقتها؟", "context": ["صلاة الفجر"]},
    {"q": "How do I perform wudu?"},
]


def load_shared():
    stop = json.load(open(os.path.join(ROOT, "shared", "content", "stopwords.json"), encoding="utf-8"))
    lex = json.load(open(os.path.join(ROOT, "shared", "content", "lexicon.json"), encoding="utf-8"))
    return library_stopwords(stop), Lexicon(lex)


def main():
    stop, lex = load_shared()
    corpus = [dict(d, seq=i + 1) for i, d in enumerate(CORPUS)]
    store = MemoryStore(corpus)
    if len(sys.argv) > 1 and sys.argv[1] != "--gen":
        print(json.dumps(retrieve(store, stop, lex, " ".join(sys.argv[1:])), ensure_ascii=False, indent=1))
        return
    cases = []
    for c in CASES:
        r = retrieve(store, stop, lex, c["q"], c.get("context", []))
        cases.append({**c, "expected": [x["id"] for x in r]})
    out = {
        "_comment": "Generated by tools/library_search.py --gen. Library search must return these ids, in order, on every platform.",
        "params": {"k1": K1, "b": B, "expansionWeight": EXPANSION_WEIGHT, "contextWeight": CONTEXT_WEIGHT,
                   "maxPassages": MAX_PASSAGES, "minScore": MIN_SCORE, "minCoverage": MIN_COVERAGE, "prefixMin": PREFIX_MIN},
        "stem": [[w, tokens(w)[0], light_stem(tokens(w)[0]), variants(tokens(w)[0])] for w in STEM_CASES],
        "corpus": corpus,
        "cases": cases,
    }
    path = os.path.join(ROOT, "shared", "fixtures", "library-retrieval.json")
    json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    open(path, "a").write("\n")
    for c in cases:
        print(f"{c['q'][:40]:40} -> {c['expected']}")


if __name__ == "__main__":
    main()
