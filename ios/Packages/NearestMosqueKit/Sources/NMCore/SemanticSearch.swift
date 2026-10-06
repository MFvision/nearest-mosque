import Foundation

/// Meaning-based (semantic) library search: port of tools/semantic.py; results must match
/// shared/fixtures/semantic.json. A text's vector is the mean of its WordPiece token vectors from a static
/// embedding model (shared/semantic/model.bin); nothing neural runs on the phone.
public final class StaticEmbedder: @unchecked Sendable {
    public static let maxTokens = 128
    static let maxWordChars = 100

    private let vocab: [String: Int]
    public let dim: Int
    private let scales: [Float]
    private let weights: [Int8]
    private let longest: Int

    /// Reads model.bin (layout in tools/semantic.py).
    public init(data: Data) throws {
        let bytes = [UInt8](data)
        func u32(_ p: Int) -> Int { Int(UInt32(bytes[p]) | UInt32(bytes[p + 1]) << 8 | UInt32(bytes[p + 2]) << 16 | UInt32(bytes[p + 3]) << 24) }
        guard bytes.count > 16, bytes[0...3].elementsEqual("NMSE".utf8), u32(4) == 1 else { throw CocoaError(.fileReadCorruptFile) }
        let n = u32(8), dim = u32(12)
        var vocab = [String: Int](minimumCapacity: n * 2)
        var p = 16
        for i in 0..<n {
            var e = p
            while bytes[e] != 0x0A { e += 1 }
            vocab[String(decoding: bytes[p..<e], as: UTF8.self)] = i
            p = e + 1
        }
        var scales = [Float](repeating: 0, count: n)
        for i in 0..<n { scales[i] = Float(bitPattern: UInt32(u32(p + 4 * i))) }
        p += 4 * n
        weights = bytes[p..<(p + n * dim)].map { Int8(bitPattern: $0) }
        self.vocab = vocab
        self.dim = dim
        self.scales = scales
        longest = vocab.keys.map(\.unicodeScalars.count).max() ?? 1
    }

    /// WordPiece ids (greedy longest match, "##" continuation); words with an unknown piece are dropped.
    public func tokenize(_ text: String) -> [Int] {
        var ids: [Int] = []
        for word in SemanticText.preTokenize(SemanticText.normalize(text)) {
            let cps = Array(word.unicodeScalars)
            if cps.count > Self.maxWordChars { continue }
            var pieces: [Int] = []
            var start = 0
            var ok = true
            while start < cps.count {
                var end = min(cps.count, start + longest)
                var found: Int?
                while end > start {
                    var sub = String.UnicodeScalarView()
                    if start > 0 { sub.append(contentsOf: "##".unicodeScalars) }
                    sub.append(contentsOf: cps[start..<end])
                    if let id = vocab[String(sub)] { found = id; break }
                    end -= 1
                }
                guard let id = found else { ok = false; break }
                pieces.append(id)
                start = end
            }
            if ok { ids += pieces }
        }
        return ids
    }

    /// Unit vector of the mean token vector (first `maxTokens` tokens); all zeros without tokens.
    public func embed(_ text: String) -> [Double] {
        var v = [Double](repeating: 0, count: dim)
        for i in tokenize(text).prefix(Self.maxTokens) {
            let s = Double(scales[i]), row = i * dim
            for d in 0..<dim { v[d] += s * Double(weights[row + d]) }
        }
        let n = v.reduce(0) { $0 + $1 * $1 }.squareRoot()
        if n > 0 { for d in 0..<dim { v[d] /= n } }
        return v
    }

    /// Item vectors are stored as int8: round(x * 127), ties to even as in the reference.
    public static func quantize(_ v: [Double]) -> [Int8] {
        v.map { Int8(max(-127, min(127, ($0 * 127).rounded(.toNearestOrEven)))) }
    }
}

/// Text rules shared with tools/semantic.py: BERT normalizer and pre-tokenizer, and what part of a record is embedded.
public enum SemanticText {
    static let semanticKinds: Set<String> = ["title", "question", "hadith", "translation"]
    public static let itemChars = 600

    static func isControl(_ s: Unicode.Scalar) -> Bool {
        if s == "\t" || s == "\n" || s == "\r" { return false }
        switch s.properties.generalCategory {
        case .control, .format, .surrogate, .privateUse, .unassigned: return true
        default: return false
        }
    }

    static func isWhitespace(_ s: Unicode.Scalar) -> Bool {
        s == " " || s == "\t" || s == "\n" || s == "\r" || s.properties.generalCategory == .spaceSeparator
    }

    static func isPunctuation(_ s: Unicode.Scalar) -> Bool {
        let o = s.value
        if (33...47).contains(o) || (58...64).contains(o) || (91...96).contains(o) || (123...126).contains(o) { return true }
        switch s.properties.generalCategory {
        case .connectorPunctuation, .dashPunctuation, .openPunctuation, .closePunctuation, .initialPunctuation, .finalPunctuation, .otherPunctuation: return true
        default: return false
        }
    }

    static func isCjk(_ o: UInt32) -> Bool {
        (0x4E00...0x9FFF).contains(o) || (0x3400...0x4DBF).contains(o) || (0x20000...0x2A6DF).contains(o) || (0x2A700...0x2B73F).contains(o) ||
            (0x2B740...0x2B81F).contains(o) || (0x2B820...0x2CEAF).contains(o) || (0xF900...0xFAFF).contains(o) || (0x2F800...0x2FA1F).contains(o)
    }

    /// Clean text, space out CJK, strip accents (NFD without nonspacing marks), lowercase.
    public static func normalize(_ text: String) -> String {
        var out = String.UnicodeScalarView()
        for s in text.unicodeScalars {
            if s.value == 0 || s.value == 0xFFFD || isControl(s) { continue }
            if isWhitespace(s) { out.append(" ") } else if isCjk(s.value) { out.append(" "); out.append(s); out.append(" ") } else { out.append(s) }
        }
        let nfd = String(out).decomposedStringWithCanonicalMapping
        var kept = String.UnicodeScalarView()
        for s in nfd.unicodeScalars where s.properties.generalCategory != .nonspacingMark { kept.append(s) }
        return String(kept).lowercased()
    }

    /// Split on whitespace; every punctuation character is its own word.
    public static func preTokenize(_ text: String) -> [String] {
        var words: [String] = []
        var cur = String.UnicodeScalarView()
        func flush() { if !cur.isEmpty { words.append(String(cur)); cur = String.UnicodeScalarView() } }
        for s in text.unicodeScalars {
            if isWhitespace(s) { flush() } else if isPunctuation(s) { flush(); words.append(String(s)) } else { cur.append(s) }
        }
        flush()
        return words
    }

    /// The part of a record that says what it is about: title plus question / hadith / translation.
    public static func of(_ chunk: SourceChunk) -> String {
        let text = chunk.original.text
        if let kinds = chunk.section?.parts, !kinds.isEmpty {
            let parts = text.unicodeScalars.split(separator: "\u{2063}", omittingEmptySubsequences: false).map { String(String.UnicodeScalarView($0)) }
            var keep: [String] = []
            for (i, k) in kinds.enumerated() where i < parts.count && semanticKinds.contains(k.kind) {
                let t = parts[i].trimmingCharacters(in: .whitespacesAndNewlines)
                if !t.isEmpty { keep.append(t) }
            }
            return keep.prefix(2).joined(separator: " ")
        }
        return String(String.UnicodeScalarView(text.unicodeScalars.prefix(itemChars)))
    }
}

/// Quantized item vectors of one collection, searched by cosine.
public struct VectorIndex: Sendable {
    public let ids: [String]
    let vectors: [Int8]
    public let dim: Int
    /// The stored vectors, row by row (for caching).
    public var vectorBytes: [Int8] { vectors }

    public init(ids: [String], vectors: [Int8], dim: Int) {
        precondition(vectors.count == ids.count * dim)
        self.ids = ids; self.vectors = vectors; self.dim = dim
    }

    /// (score, id) best first, score >= `floor`; ties by id.
    public func search(_ q: [Double], k: Int, floor: Double) -> [(score: Double, id: String)] {
        guard q.contains(where: { $0 != 0 }) else { return [] }
        var hits: [(score: Double, id: String)] = []
        vectors.withUnsafeBufferPointer { v in
            for n in ids.indices {
                var s = 0.0
                let row = n * dim
                for d in 0..<dim { s += q[d] * Double(v[row + d]) }
                s /= 127
                if s >= floor { hits.append((s, ids[n])) }
            }
        }
        return Array(hits.sorted { $0.score != $1.score ? $0.score > $1.score : $0.id < $1.id }.prefix(k))
    }
}

/// How semantic hits join word search (gates measured in tools/semantic.py).
public enum SemanticMerge {
    public static let assist = 0.45
    public static let alone = 0.55
    public static let k = 3

    /// New semantic hits that pass the gate go after the first three word-search results.
    public static func merge(_ words: [String], _ semantic: [(score: Double, id: String)], limit: Int = 6) -> [String] {
        let floor = words.isEmpty ? alone : assist
        var extra: [String] = []
        for h in semantic where h.score >= floor && !words.contains(h.id) && !extra.contains(h.id) && extra.count < k { extra.append(h.id) }
        return Array((Array(words.prefix(3)) + extra + Array(words.dropFirst(3))).prefix(limit))
    }
}
