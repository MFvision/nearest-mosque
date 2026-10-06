import Foundation

/// Search-field normalization shared with Android and tools/reference_search.py
/// (shared/fixtures/normalization.json). Displayed quotations are never normalized.
public enum TextNormalizer {
    static let charMap: [UInt32: String] = {
        var m: [UInt32: String] = [
            0x0671: "\u{0627}", 0x0649: "\u{064A}", 0x06CC: "\u{064A}", 0x06D2: "\u{064A}", 0x06A9: "\u{0643}",
            0x0629: "\u{0647}", 0x06C3: "\u{0647}", 0x06C1: "\u{0647}", 0x06BE: "\u{0647}", 0x06D5: "\u{0647}",
            0x0131: "i", 0x0640: "", 0x0621: "",
        ]
        for i in 0..<10 {
            m[UInt32(0x0660 + i)] = String(i)
            m[UInt32(0x06F0 + i)] = String(i)
        }
        return m
    }()
    static let arabicPrefixes = ["\u{0648}\u{0627}\u{0644}", "\u{0641}\u{0627}\u{0644}", "\u{0628}\u{0627}\u{0644}", "\u{0643}\u{0627}\u{0644}", "\u{0644}\u{0644}", "\u{0627}\u{0644}"]
    static let letterCategories: Set<Unicode.GeneralCategory> = [.uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter, .decimalNumber]

    public static func tokens(_ text: String) -> [String] {
        var stripped = String.UnicodeScalarView()
        for s in text.decomposedStringWithCompatibilityMapping.unicodeScalars where s.properties.generalCategory != .nonspacingMark {
            stripped.append(s)
        }
        let lower = String(stripped).lowercased()
        var out: [String] = []
        var cur = String.UnicodeScalarView()
        var run: [Unicode.Scalar] = []
        func flush() {
            if !cur.isEmpty { out.append(String(cur)); cur = String.UnicodeScalarView() }
        }
        func flushRun() {
            if run.count == 1 {
                out.append(String(Character(run[0])))
            } else if run.count > 1 {
                for k in 0..<(run.count - 1) {
                    var piece = String.UnicodeScalarView()
                    piece.append(run[k]); piece.append(run[k + 1])
                    out.append(String(piece))
                }
            }
            run = []
        }
        for s in lower.unicodeScalars {
            let mapped: [Unicode.Scalar] = charMap[s.value].map { Array($0.unicodeScalars) } ?? [s]
            for m in mapped {
                let category = m.properties.generalCategory
                if isUnspaced(m) {
                    if category == .spacingMark { continue }
                    if letterCategories.contains(category) && category != .decimalNumber {
                        flush()
                        run.append(m)
                        continue
                    }
                }
                if !run.isEmpty { flushRun() }
                if letterCategories.contains(category) { cur.append(m) } else { flush() }
            }
        }
        flushRun()
        flush()
        return out.map(stem)
    }

    /// Scripts written without spaces between words (Chinese, Japanese kana, Thai, Lao, Khmer, Myanmar): a run of
    /// their letters is indexed as overlapping two-letter pieces, and their spacing vowel signs are dropped like
    /// the other marks, so a query matches any text that contains it.
    static let unspaced: [ClosedRange<UInt32>] = [0x0E00...0x0EFF, 0x1000...0x109F, 0x1780...0x17FF, 0x19E0...0x19FF, 0x3040...0x30FF,
                                                  0x3400...0x4DBF, 0x4E00...0x9FFF, 0xF900...0xFAFF, 0x20000...0x2FFFF]
    static func isUnspaced(_ s: Unicode.Scalar) -> Bool { unspaced.contains { $0.contains(s.value) } }

    public static func searchText(_ text: String) -> String { tokens(text).joined(separator: " ") }

    /// Loose folding for city-name prefix search (no stemming).
    public static func foldForPrefix(_ text: String) -> String {
        var stripped = String.UnicodeScalarView()
        for s in text.trimmingCharacters(in: .whitespaces).decomposedStringWithCompatibilityMapping.unicodeScalars
        where s.properties.generalCategory != .nonspacingMark {
            stripped.append(s)
        }
        var mapped = ""
        for s in String(stripped).lowercased().unicodeScalars {
            if let m = charMap[s.value] { mapped += m } else { mapped.unicodeScalars.append(s) }
        }
        let separators = CharacterSet(charactersIn: "-'’.").union(.whitespaces)
        return mapped.components(separatedBy: separators).filter { !$0.isEmpty }.joined(separator: " ")
    }

    static func isArabic(_ s: Unicode.Scalar) -> Bool { (0x0600...0x06FF).contains(s.value) }
    public static func containsArabicLetter(_ s: String) -> Bool { s.unicodeScalars.contains(where: isArabic) }

    static func stem(_ tok: String) -> String {
        let n = tok.unicodeScalars.count
        if containsArabicLetter(tok) {
            for p in arabicPrefixes where tok.hasPrefix(p) && n - p.unicodeScalars.count >= 3 {
                return String(String.UnicodeScalarView(tok.unicodeScalars.dropFirst(p.unicodeScalars.count)))
            }
            return tok
        }
        if n > 3 && tok.hasSuffix("s") && !tok.hasSuffix("ss") && !tok.hasSuffix("us") && !tok.hasSuffix("is") {
            return String(String.UnicodeScalarView(tok.unicodeScalars.dropLast()))
        }
        return tok
    }
}
