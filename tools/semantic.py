#!/usr/bin/env python3
"""Reference implementation of meaning-based (semantic) library search. The Kotlin (android/core
SemanticSearch.kt) and Swift (NMCore SemanticSearch.swift) ports must give the same results for
shared/fixtures/semantic.json.

Model: sentence-transformers/static-similarity-mrl-multilingual-v1 (Apache-2.0), a static embedding
model: a text's vector is the mean of its WordPiece token vectors (BERT multilingual uncased
tokenizer). No neural network runs on the phone. shared/semantic/model.bin keeps the first 256
dimensions (the model was trained to allow this) of the tokens written in Latin or Arabic script (the
app's languages), each row stored as int8 with a float32 scale.

  python3 tools/semantic.py --build-model DIR   # DIR holds model.safetensors + tokenizer.json (needs numpy, safetensors)
  python3 tools/semantic.py --gen               # regenerate shared/fixtures/semantic.json
  python3 tools/semantic.py "question"          # nearest fixture-corpus items

model.bin layout (little-endian): b"NMSE" | u32 version=1 | u32 vocab | u32 dim | vocab words, UTF-8,
each followed by "\\n" | f32 scale x vocab | i8 weights x vocab x dim.

Search: the question and each item (title plus question, hadith or translation; see semantic_text) are
embedded and compared by cosine. Item vectors are stored as int8 (x127). Results only join the word
search (library_search.py) through the gates in merge(): an item must score ASSIST when word search
found something, ALONE when it found nothing; at most K are added.
"""
import json
import math
import os
import struct
import sys
import unicodedata

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
MODEL = os.path.join(ROOT, "shared", "semantic", "model.bin")
DIM = 256
MAX_TOKENS = 128
MAX_WORD_CHARS = 100
ASSIST, ALONE, K = 0.45, 0.55, 3
SEP = "⁣"
SEMANTIC_KINDS = ("title", "question", "hadith", "translation")


# ---- Text -----------------------------------------------------------------------------------------

def _is_control(c):
    if c in "\t\n\r":
        return False
    return unicodedata.category(c).startswith("C")


def _is_whitespace(c):
    return c in " \t\n\r" or unicodedata.category(c) == "Zs"


def _is_punctuation(c):
    o = ord(c)
    if 33 <= o <= 47 or 58 <= o <= 64 or 91 <= o <= 96 or 123 <= o <= 126:
        return True
    return unicodedata.category(c).startswith("P")


def _is_cjk(o):
    return (0x4E00 <= o <= 0x9FFF or 0x3400 <= o <= 0x4DBF or 0x20000 <= o <= 0x2A6DF or 0x2A700 <= o <= 0x2B73F
            or 0x2B740 <= o <= 0x2B81F or 0x2B820 <= o <= 0x2CEAF or 0xF900 <= o <= 0xFAFF or 0x2F800 <= o <= 0x2FA1F)


def normalize(text):
    """BertNormalizer (clean text, CJK spacing, strip accents, lowercase)."""
    out = []
    for c in text:
        o = ord(c)
        if o == 0 or o == 0xFFFD or _is_control(c):
            continue
        if _is_whitespace(c):
            out.append(" ")
        elif _is_cjk(o):
            out.append(" " + c + " ")
        else:
            out.append(c)
    s = unicodedata.normalize("NFD", "".join(out))
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return s.lower()


def pre_tokenize(text):
    """BertPreTokenizer: split on whitespace; every punctuation character is its own word."""
    words, cur = [], []
    for c in text:
        if _is_whitespace(c):
            if cur:
                words.append("".join(cur)); cur = []
        elif _is_punctuation(c):
            if cur:
                words.append("".join(cur)); cur = []
            words.append(c)
        else:
            cur.append(c)
    if cur:
        words.append("".join(cur))
    return words


def keep_token(tok):
    """Vocabulary kept in model.bin: tokens whose letters are all Latin or Arabic script."""
    t = tok[2:] if tok.startswith("##") and len(tok) > 2 else tok
    if t.startswith("[") and t.endswith("]"):
        return False
    for c in t:
        if c.isascii():
            continue
        cat = unicodedata.category(c)
        if not cat.startswith("L") and not cat.startswith("M"):
            continue  # digits, punctuation, symbols
        name = unicodedata.name(c, "")
        if not (name.startswith("LATIN") or name.startswith("ARABIC")):
            return False
    return True


class Model:
    def __init__(self, path=MODEL):
        data = open(path, "rb").read()
        magic, version, n, dim = struct.unpack_from("<4sIII", data, 0)
        assert magic == b"NMSE" and version == 1, "not a model.bin"
        p = 16
        words = []
        for _ in range(n):
            e = data.index(b"\n", p)
            words.append(data[p:e].decode("utf-8")); p = e + 1
        self.scales = struct.unpack_from(f"<{n}f", data, p); p += 4 * n
        self.weights = data[p:p + n * dim]
        self.dim, self.vocab = dim, {w: i for i, w in enumerate(words)}
        self.longest = max(len(w) for w in words)

    def tokenize(self, text):
        """WordPiece ids (greedy longest match, "##" continuation); words with an unknown piece are dropped."""
        ids = []
        for word in pre_tokenize(normalize(text)):
            if len(word) > MAX_WORD_CHARS:
                continue
            pieces, start = [], 0
            while start < len(word):
                end = min(len(word), start + self.longest)
                found = None
                while end > start:
                    sub = word[start:end] if start == 0 else "##" + word[start:end]
                    if sub in self.vocab:
                        found = self.vocab[sub]
                        break
                    end -= 1
                if found is None:
                    pieces = None
                    break
                pieces.append(found)
                start = end
            if pieces:
                ids.extend(pieces)
        return ids

    def embed(self, text):
        """Unit vector of the mean token vector (first MAX_TOKENS tokens); zero vector without tokens."""
        ids = self.tokenize(text)[:MAX_TOKENS]
        v = [0.0] * self.dim
        for i in ids:
            s, row = self.scales[i], i * self.dim
            for d in range(self.dim):
                w = self.weights[row + d]
                v[d] += s * (w - 256 if w > 127 else w)
        n = math.sqrt(sum(x * x for x in v))
        return [x / n for x in v] if n > 0 else v


def quantize(v):
    """Item vectors are stored as int8: round(x * 127)."""
    return [max(-127, min(127, int(round(x * 127)))) for x in v]


def score(q, item_q):
    return sum(a * b for a, b in zip(q, item_q)) / 127.0


def semantic_text(chunk):
    """The part of a record that says what it is about: title plus question / hadith / translation."""
    kinds = chunk.get("section", {}).get("parts")
    text = chunk["original"]["text"]
    if kinds:
        parts = text.split(SEP)
        keep = [t.strip() for k, t in zip(kinds, parts) if k.get("kind") in SEMANTIC_KINDS and t.strip()][:2]
        return " ".join(keep)
    return text[:600]


def search(model, items, question, k=10, floor=0.0):
    """items: [(id, quantized vector)] -> [(score, id)] best first, score >= floor."""
    q = model.embed(question)
    if not any(q):
        return []
    hits = [(round(score(q, v), 6), i) for i, v in items]
    hits = [h for h in hits if h[0] >= floor]
    hits.sort(key=lambda h: (-h[0], h[1]))
    return hits[:k]


def merge(words, semantic, limit=6):
    """Word-search ids, best first, and semantic hits [(score, id)]: semantic hits that pass the gate
    and are new go after the first three word-search results."""
    floor = ASSIST if words else ALONE
    extra = [i for s, i in semantic if s >= floor and i not in words][:K]
    return (words[:3] + extra + words[3:])[:limit]


# ---- Model build (development only) ---------------------------------------------------------------

def build_model(src):
    import numpy as np
    from safetensors.numpy import load_file
    tok = json.load(open(os.path.join(src, "tokenizer.json"), encoding="utf-8"))
    vocab = tok["model"]["vocab"]
    weights = load_file(os.path.join(src, "model.safetensors"))["embedding.weight"][:, :DIM]
    keep = sorted((i, w) for w, i in vocab.items() if keep_token(w))
    rows = weights[[i for i, _ in keep]]
    scales = np.abs(rows).max(axis=1) / 127.0
    scales[scales == 0] = 1.0
    q = np.clip(np.round(rows / scales[:, None]), -127, 127).astype(np.int8)
    with open(MODEL, "wb") as f:
        f.write(struct.pack("<4sIII", b"NMSE", 1, len(keep), DIM))
        for _, w in keep:
            assert "\n" not in w
            f.write(w.encode("utf-8") + b"\n")
        f.write(scales.astype("<f4").tobytes())
        f.write(q.tobytes())
    print(f"{len(keep)} of {len(vocab)} tokens, {DIM} dims -> {MODEL} ({os.path.getsize(MODEL)} bytes)")


# ---- Fixture --------------------------------------------------------------------------------------

TOKEN_CASES = ["كيف أتوضأ؟", "I overslept and missed Fajr", "Namaz nasıl kılınır?", "Qu’est-ce que l’islam ?", "نماز کیسے پڑھوں؟",
               "¿Se puede rezar con zapatos?", "Apakah boleh memelihara anjing?", "İSLAM ve Müslüman", "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
               "Zakāh — ‘Umar (ra) 2:255", "日本語 mixed with English", "supercalifragilisticexpialidocious" * 4, ""]

CORPUS = [
    {"id": "f1", "text": "حكم من نام عن صلاة الفجر حتى طلعت الشمس" + SEP + "أخذني النوم عن صلاة الفجر ولم أستيقظ إلا بعد طلوع الشمس", "parts": ["title", "question"]},
    {"id": "f2", "text": "حكم الاستماع إلى الأغاني" + SEP + "ما حكم الاستماع إلى الأغاني؟" + SEP + "الأغاني محرمة", "parts": ["title", "question", "answer"]},
    {"id": "f3", "text": "حكم الصلاة في النعال" + SEP + "هل تجوز الصلاة في الأحذية؟", "parts": ["title", "question"]},
    {"id": "f4", "text": "حكم شرب الدخان" + SEP + "ما حكم التدخين؟", "parts": ["title", "question"]},
    {"id": "f5", "text": "Supplication is worship" + SEP + "Supplication is worship, then he recited: Call upon Me, I will respond to you.", "parts": ["title", "hadith"]},
    {"id": "f6", "text": "The Prayer of the Traveller: the traveller shortens the four-unit prayers to two.", "parts": None},
    {"id": "f7", "text": "حكم قراءة الحائض للقرآن" + SEP + "هل يجوز للحائض قراءة القرآن؟", "parts": ["title", "question"]},
    {"id": "f8", "text": "Fasting in Ramadan is obligatory on every adult Muslim who is able.", "parts": None},
]
CASES = ["نمت عن صلاة الفجر ولم أستيقظ", "Is it ok to listen to songs?", "Can I pray wearing shoes?", "Sigara içmek haram mı?",
         "Can a woman on her period read Quran?", "How should I make dua?", "What is the capital of France?", "صلاة المسافر"]
MERGE_CASES = [
    {"words": ["a", "b"], "semantic": [[0.6, "c"], [0.5, "a"], [0.46, "d"], [0.44, "e"]]},
    {"words": [], "semantic": [[0.6, "c"], [0.5, "d"]]},
    {"words": ["a", "b", "c", "d", "e", "f"], "semantic": [[0.9, "x"], [0.8, "y"], [0.7, "z"], [0.6, "w"]]},
    {"words": [], "semantic": []},
]


def corpus_items(model):
    items = []
    for d in CORPUS:
        chunk = {"original": {"text": d["text"]}, "section": {"parts": [{"kind": k} for k in d["parts"]]} if d["parts"] else {}}
        items.append((d["id"], quantize(model.embed(semantic_text(chunk)))))
    return items


def generate():
    model = Model()
    items = corpus_items(model)
    tokens = [{"text": t, "ids": model.tokenize(t)} for t in TOKEN_CASES]
    embeds = []
    for t in TOKEN_CASES[:6]:
        v = model.embed(t)
        embeds.append({"text": t, "first": [round(x, 6) for x in v[:8]], "quantized": quantize(v)})
    cases = [{"q": q, "expected": [[s, i] for s, i in search(model, items, q, k=3)]} for q in CASES]
    merges = [dict(c, expected=merge(c["words"], [tuple(x) for x in c["semantic"]])) for c in MERGE_CASES]
    out = {
        "_comment": "Generated by tools/semantic.py --gen. Every platform must tokenize, embed (first 8 dims within 1e-4, quantized exactly) and rank the corpus as here (scores within 1e-4).",
        "dim": DIM, "maxTokens": MAX_TOKENS, "assist": ASSIST, "alone": ALONE, "k": K,
        "tokens": tokens, "embeddings": embeds,
        "corpus": CORPUS, "cases": cases, "merge": merges,
    }
    path = os.path.join(ROOT, "shared", "fixtures", "semantic.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"{len(tokens)} token cases, {len(cases)} search cases -> {path}")


def main():
    if sys.argv[1:2] == ["--build-model"]:
        build_model(sys.argv[2])
    elif sys.argv[1:] == ["--gen"]:
        generate()
    else:
        model = Model()
        for s, i in search(model, corpus_items(model), " ".join(sys.argv[1:]), k=5):
            print(s, i)


if __name__ == "__main__":
    main()
