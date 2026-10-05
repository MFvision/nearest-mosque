import Foundation

/// Library search (IslamHouse, Ibn Baz fatwas): light stemming, prefix matching and the multilingual
/// lexicon. Port of tools/library_search.py; results must match shared/fixtures/library-retrieval.json.
/// Quran search (`Retriever`) is separate and unchanged. Lengths are counted in Unicode scalars, as in
/// the Python and Kotlin versions.
public enum LibraryText {
    public static let prefixMin = 3
    static let arSuffixes = ["ها", "ان", "ات", "ون", "ين", "يه", "ه", "ي"]
    static let latinSuffixes = ["ing", "ers", "er", "ed"]

    public static func len(_ s: String) -> Int { s.unicodeScalars.count }
    static func dropLastScalars(_ s: String, _ n: Int) -> String {
        var u = String.UnicodeScalarView(s.unicodeScalars)
        u.removeLast(n)
        return String(u)
    }
    static func dropFirstScalar(_ s: String) -> String { String(String.UnicodeScalarView(s.unicodeScalars.dropFirst())) }
    static func hasSuffixScalars(_ s: String, _ suffix: String) -> Bool { s.unicodeScalars.reversed().starts(with: suffix.unicodeScalars.reversed()) }
    static func hasPrefixScalars(_ s: String, _ prefix: String) -> Bool { s.unicodeScalars.starts(with: prefix.unicodeScalars) }

    public static func lightStem(_ tok: String) -> String {
        if !TextNormalizer.containsArabicLetter(tok) {
            for s in latinSuffixes where hasSuffixScalars(tok, s) && len(tok) - len(s) >= 4 { return dropLastScalars(tok, len(s)) }
            return tok
        }
        var t = tok
        for s in arSuffixes where hasSuffixScalars(t, s) && len(t) - len(s) >= 3 { t = dropLastScalars(t, len(s)) }
        return t
    }

    /// Forms a word is indexed and searched under (see tools/library_search.py `variants`).
    public static func variants(_ tok: String) -> [String] {
        var out = [tok]
        func add(_ x: String) { if !out.contains(x) { out.append(x) } }
        let arabic = TextNormalizer.containsArabicLetter(tok)
        var bases = [tok]
        if arabic && hasPrefixScalars(tok, "و") && len(tok) > 3 { bases.append(dropFirstScalar(tok)) }
        for base in bases {
            let st = lightStem(base)
            add(st)
            if arabic && st != base && hasSuffixScalars(st, "ت") && len(st) >= 4 { add(dropLastScalars(st, 1)) }
        }
        return out
    }

    public static func indexTokens(_ text: String) -> [String] { TextNormalizer.tokens(text).flatMap(variants) }

    public static func matches(_ token: String, _ v: String) -> Bool {
        len(v) >= prefixMin ? hasPrefixScalars(token, v) : token == v
    }

    /// Query stopwords for the library: the shared list without the `_domain` words ("Islam", "Quran"...).
    public static func stopwords(_ stopwords: [String: [String]]) -> Set<String> {
        let domain = Set((stopwords["_domain"] ?? []).flatMap(TextNormalizer.tokens))
        var out = Set<String>()
        for (k, words) in stopwords where !k.hasPrefix("_") {
            for w in words {
                let ts = TextNormalizer.tokens(w)
                if !ts.contains(where: { domain.contains($0) }) { out.formUnion(ts) }
            }
        }
        return out
    }
}

/// Equivalent words across languages (shared/content/lexicon.json).
public struct Lexicon: Sendable {
    let groups: [[String]]
    let byStem: [String: [Int]]

    public static let empty = Lexicon(groups: [])

    public init(groups: [[String]]) {
        self.groups = groups
        var m: [String: [Int]] = [:]
        for (i, g) in groups.enumerated() { for w in g { m[LibraryText.lightStem(w), default: []].append(i) } }
        byStem = m
    }

    public init(json: Data) {
        var groups: [[String]] = []
        if let root = try? JSONSerialization.jsonObject(with: json) as? [String: Any], let raw = root["groups"] as? [[String: [String]]] {
            for g in raw {
                var members = Set<String>()
                for lang in g.keys.sorted() {
                    for w in g[lang] ?? [] {
                        let t = TextNormalizer.tokens(w)
                        if t.count == 1 && LibraryText.len(t[0]) >= 3 { members.insert(t[0]) }
                    }
                }
                // Same order as Python's sorted() (code point order).
                let sorted = members.sorted { Array($0.unicodeScalars.map(\.value)).lexicographicallyPrecedes($1.unicodeScalars.map(\.value)) }
                if sorted.count > 1 { groups.append(sorted) }
            }
        }
        self.init(groups: groups)
    }

    public func expansions(_ word: String) -> [String] {
        let stem = LibraryText.lightStem(word)
        var out: [String] = []
        for gi in byStem[stem] ?? [] {
            for m in groups[gi] where LibraryText.lightStem(m) != stem && !out.contains(m) { out.append(m) }
        }
        return out
    }
}

/// Library search storage: a word matches a token by prefix (3+ letters) or exactly. The retriever first
/// asks for ids per word (cheap), keeps the chunks that can pass the coverage gate, and only loads those.
public protocol LibraryStore: Sendable {
    var totalChunks: Int { get }
    var averageLength: Double { get }
    /// Ids of the chunks with a token matching any of `variants`.
    func ids(_ variants: [String]) -> Set<String>
    /// Id, order and tokens of the given chunks.
    func rows(_ ids: [String]) -> [CandidateChunk]
    /// Token count (document length) of the given chunks, without loading their text.
    func lengths(_ ids: [String]) -> [String: Int]
}

public struct InMemoryLibraryStore: LibraryStore {
    let items: [CandidateChunk]
    public let totalChunks: Int
    public let averageLength: Double

    public init(_ docs: [(id: String, seq: Int, text: String)]) {
        items = docs.map { CandidateChunk(id: $0.id, seq: $0.seq, tokens: LibraryText.indexTokens($0.text)) }
        totalChunks = items.count
        averageLength = items.isEmpty ? 0 : Double(items.reduce(0) { $0 + $1.tokens.count }) / Double(items.count)
    }

    func hit(_ c: CandidateChunk, _ vs: [String]) -> Bool { c.tokens.contains { t in vs.contains { LibraryText.matches(t, $0) } } }
    public func ids(_ variants: [String]) -> Set<String> { Set(items.filter { hit($0, variants) }.map(\.id)) }
    public func rows(_ ids: [String]) -> [CandidateChunk] { let set = Set(ids); return items.filter { set.contains($0.id) } }
    public func lengths(_ ids: [String]) -> [String: Int] {
        let set = Set(ids)
        return Dictionary(uniqueKeysWithValues: items.filter { set.contains($0.id) }.map { ($0.id, $0.tokens.count) })
    }
}

public final class LibraryRetriever: @unchecked Sendable {
    public static let k1 = 1.2, b = 0.75, expansionWeight = 0.5, contextWeight = 0.3
    public static let maxPassages = 5, minScore = 0.5, minCoverage = 0.6
    /// Candidates scored exactly per library and question (see retrieve).
    public static let maxRows = 400

    final class Term {
        let word: String, variants: [String], weight: Double, owner: Int?
        var idf = 0.0
        var ids = Set<String>()
        init(_ word: String, _ weight: Double, _ owner: Int?) { self.word = word; variants = LibraryText.variants(word); self.weight = weight; self.owner = owner }
    }

    let store: LibraryStore
    let stop: Set<String>
    let lexicon: Lexicon

    public init(store: LibraryStore, stopwords: Set<String>, lexicon: Lexicon) {
        self.store = store; self.stop = stopwords; self.lexicon = lexicon
    }

    /// `limit`: results returned (Ask uses `maxPassages`; search inside a collection asks for more).
    public func retrieve(_ question: String, context: [String] = [], limit: Int = LibraryRetriever.maxPassages) -> RetrievalResult {
        var content: [String] = []
        for t in TextNormalizer.tokens(question) where !stop.contains(t) && !content.contains(t) { content.append(t) }
        var terms: [Term] = [], seen = Set<String>()
        func add(_ w: String, _ weight: Double, _ owner: Int?) { if seen.insert(w).inserted { terms.append(Term(w, weight, owner)) } }
        for (i, w) in content.enumerated() { add(w, 1, i) }
        for (i, w) in content.enumerated() { for e in lexicon.expansions(w) { add(e, Self.expansionWeight, i) } }
        if content.count < 4 {
            for prev in context { for t in TextNormalizer.tokens(prev) where !stop.contains(t) { add(t, Self.contextWeight, nil) } }
        }
        guard !content.isEmpty, store.totalChunks > 0 else {
            return RetrievalResult(kind: .insufficient, commonQuestion: nil, passages: [], contentTerms: content)
        }
        let n = Double(store.totalChunks), avgdl = store.averageLength
        for t in terms {
            t.ids = store.ids(t.variants)
            let df = Double(t.ids.count)
            t.idf = log(1 + (n - df + 0.5) / (df + 0.5))
        }
        // Only chunks covering enough of the question's words can pass the coverage gate: load just those.
        var owners: [String: Set<Int>] = [:]
        for t in terms { if let o = t.owner { for id in t.ids { owners[id, default: []].insert(o) } } }
        let need = max(1, Int((Self.minCoverage * Double(content.count) - 1e-9).rounded(.up)))
        var candidateIds = owners.filter { $0.value.count >= need }.map(\.key)
        // Very common words can leave thousands of candidates: rank them first by an estimate from what the
        // index knows (which words they contain, their length, term frequency taken as 1), then load and
        // score exactly only the best `maxRows`.
        if candidateIds.count > Self.maxRows {
            let lengths = store.lengths(candidateIds)
            var weight: [String: Double] = [:]
            for t in terms { for id in t.ids where lengths[id] != nil { weight[id, default: 0] += t.weight * t.idf } }
            let est: [(String, Double)] = lengths.map { id, dl in
                let cover = Double(owners[id]?.count ?? 0) / Double(content.count)
                return (id, (weight[id] ?? 0) * (Self.k1 + 1) / (1 + Self.k1 * (1 - Self.b + Self.b * Double(dl) / avgdl)) * cover)
            }
            candidateIds = est.sorted { $0.1 > $1.1 }.prefix(Self.maxRows).map(\.0)
        }
        struct S { let id: String; let seq: Int; let score: Double; let coverage: Double }
        let scored: [S] = store.rows(candidateIds).compactMap { c in
            let dl = Double(c.tokens.count)
            var s = 0.0
            var covered = Set<Int>()
            for t in terms {
                let tf = Double(c.tokens.filter { tok in t.variants.contains { LibraryText.matches(tok, $0) } }.count)
                guard tf > 0 else { continue }
                s += t.weight * t.idf * tf * (Self.k1 + 1) / (tf + Self.k1 * (1 - Self.b + Self.b * dl / avgdl))
                if let o = t.owner { covered.insert(o) }
            }
            let coverage = Double(covered.count) / Double(content.count)
            s *= coverage
            return s >= Self.minScore && coverage >= Self.minCoverage ? S(id: c.id, seq: c.seq, score: s, coverage: coverage) : nil
        }.sorted { ($0.score, -$0.seq) > ($1.score, -$1.seq) }
        let passages = scored.prefix(limit).map { ScoredPassage(chunkId: $0.id, score: $0.score, coverage: $0.coverage) }
        return RetrievalResult(kind: passages.isEmpty ? .insufficient : .passages, commonQuestion: nil, passages: Array(passages), contentTerms: content)
    }
}

/// Text parts of a library record (Ibn Baz fatwas, HadeethEnc hadiths, QuranEnc translations): stored once
/// in original.text, separated by U+2063, with kinds and languages in section.parts (see
/// tools/build_enc_packs.py). IslamHouse records have no parts.
public enum LibraryParts {
    public static let separator: Character = "\u{2063}"

    public struct Part: Hashable, Sendable {
        public let kind: String
        public let lang: String
        public let text: String
        public init(kind: String, lang: String, text: String) { self.kind = kind; self.lang = lang; self.text = text }
    }

    public static func parts(_ c: SourceChunk) -> [Part] {
        guard let kinds = c.section?.parts else { return [] }
        let texts = c.original.text.split(separator: separator, omittingEmptySubsequences: false)
        return kinds.enumerated().compactMap { i, k in
            let text = i < texts.count ? texts[i].trimmingCharacters(in: .whitespacesAndNewlines) : ""
            return text.isEmpty ? nil : Part(kind: k.kind, lang: k.lang ?? c.original.lang, text: text)
        }
    }

    /// One-paragraph preview: the question, hadith or translation; otherwise the text after the title.
    public static func summary(_ c: SourceChunk) -> String {
        let p = parts(c)
        if let first = p.first(where: { $0.kind != "title" }) ?? p.first { return first.text }
        let t = c.original.text
        return (t.hasPrefix(c.anchor) ? String(t.dropFirst(c.anchor.count)) : t).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/// Sites the app links to but does not copy (their terms reserve republishing): the question opens in the
/// site's own search, or a web search limited to the site, only when the user taps it.
public enum OtherSources {
    public struct Link: Hashable, Sendable { public let id: String; public let url: URL }

    static let islamQaLanguages: Set<String> = ["ar", "en", "ur", "tr", "id", "fr", "es"]
    /// Same escaping as Java's URLEncoder with "+" as "%20" (letters, digits and . - * _ kept).
    static func encode(_ s: String) -> String {
        var allowed = CharacterSet.alphanumerics.intersection(CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"))
        allowed.insert(charactersIn: ".-*_")
        return s.addingPercentEncoding(withAllowedCharacters: allowed) ?? s
    }

    public static func links(_ question: String, lang: String) -> [Link] {
        let q = encode(question.trimmingCharacters(in: .whitespacesAndNewlines))
        let qa = islamQaLanguages.contains(lang) ? lang : "en"
        func site(_ host: String) -> String { "https://www.google.com/search?q=" + encode("site:\(host) ") + q }
        return [("islamqa", "https://islamqa.info/\(qa)/search?q=\(q)"), ("dorar", "https://dorar.net/hadith/search?q=\(q)"),
                ("binothaimeen", site("binothaimeen.net")), ("alifta", site("alifta.gov.sa"))]
            .compactMap { id, u in URL(string: u).map { Link(id: id, url: $0) } }
    }
}
