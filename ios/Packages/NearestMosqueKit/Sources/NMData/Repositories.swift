import Foundation
import GRDB
import NMCore

/// Outcome of a nearest-mosque query; every empty case is distinct.
public enum MosqueResult: Sendable {
    case found([RankedMosque], coverage: [String])
    case noRecordsInCoverage(String, radiusMeters: Double)
    case areaNotDownloaded(installed: [String])
}

public final class MosqueRepository: @unchecked Sendable {
    let db: AppDatabase
    public init(db: AppDatabase) { self.db = db }

    public func nearest(_ center: LatLng, radiusMeters: Double, lang: String) throws -> MosqueResult {
        let packs = try PackManager(db: db, bundledRoot: nil).installed().filter { $0.kind == "mosques" }
        guard !packs.isEmpty else { return .areaNotDownloaded(installed: []) }
        let box = Geo.boundingBox(center, radiusM: radiusMeters)
        let rows: [Mosque] = try db.writer.read { db in
            try box.longitudeRanges.flatMap { r in
                try Row.fetchAll(db, sql: "SELECT * FROM mosque WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?",
                                 arguments: [box.minLat, box.maxLat, r.lowerBound, r.upperBound]).compactMap(Self.mosque)
            }
        }
        let ranked = MosqueRanking.rank(center: center, candidates: rows, radiusMeters: radiusMeters)
        let covering = packs.filter { p in
            guard let b = p.manifest.coverage?.bbox else { return false }
            return (b.minLat...b.maxLat).contains(center.latitude) && (b.minLng...b.maxLng).contains(center.longitude)
        }
        func name(_ p: InstalledPack) -> String { p.manifest.coverage?.name ?? p.manifest.title(lang) }
        if !ranked.isEmpty { return .found(ranked, coverage: covering.map(name)) }
        if let c = covering.first { return .noRecordsInCoverage(name(c), radiusMeters: radiusMeters) }
        return .areaNotDownloaded(installed: packs.map(name))
    }

    public func byIds(_ ids: [String]) throws -> [Mosque] {
        guard !ids.isEmpty else { return [] }
        return try db.writer.read { db in
            try Row.fetchAll(db, sql: "SELECT * FROM mosque WHERE sourceId IN (\(ids.map { _ in "?" }.joined(separator: ",")))",
                             arguments: StatementArguments(ids)).compactMap(Self.mosque)
        }
    }

    public func favorites() throws -> Set<String> {
        try db.writer.read { db in Set(try String.fetchAll(db, sql: "SELECT sourceId FROM favorite")) }
    }

    public func setFavorite(_ id: String, _ on: Bool) throws {
        try db.writer.write { db in
            if on { try db.execute(sql: "INSERT OR REPLACE INTO favorite VALUES (?, ?)", arguments: [id, Date().timeIntervalSince1970]) }
            else { try db.execute(sql: "DELETE FROM favorite WHERE sourceId = ?", arguments: [id]) }
        }
    }

    static func mosque(_ r: Row) -> Mosque? {
        guard let loc = LatLng(r["lat"], r["lng"]) else { return nil }
        let names = (try? JSONDecoder().decode([String: String].self, from: Data((r["namesJson"] as String).utf8))) ?? [:]
        return Mosque(sourceId: r["sourceId"], packId: r["packId"], category: MosqueCategory(rawValue: r["category"]) ?? .mosque,
                      names: names, location: loc, address: r["address"], phone: r["phone"], website: r["website"],
                      openingHoursRaw: r["openingHoursRaw"], sourceTimestamp: r["sourceTimestamp"])
    }
}

/// Which chunks a store covers: the books (Quran) collection, or one library pack. Library packs are
/// separate collections so their statistics never change the Quran results.
public enum ChunkScope: Sendable {
    case books
    case library(String)

    /// Library collections, searched separately from the Quran: IslamHouse per language, Ibn Baz fatwas.
    public static let libraryPrefix = "sources.islamhouse-"
    public static let binbazPack = "sources.binbaz-ar"
    public static let libraryPrefixes = [libraryPrefix, "sources.binbaz-", "sources.hadeethenc-", "sources.quranenc-"]
    public static func libraryPack(_ lang: String) -> String { libraryPrefix + lang }
    public static func isLibrary(_ packId: String) -> Bool { libraryPrefixes.contains { packId.hasPrefix($0) } }
    /// Language of a library pack: the suffix after the last "-" (sources.binbaz-ar → ar).
    public static func libraryLanguage(_ packId: String) -> String { packId.split(separator: "-").last.map(String.init) ?? "" }
    /// Library languages installed for an interface language: that language and Arabic (the Ibn Baz fatwas
    /// and the Arabic library), plus English as a fallback for the other languages.
    public static func libraryLanguages(_ ui: String) -> Set<String> { ui == "ar" ? ["ar"] : [ui, "ar", "en"] }
    /// Built-in packs for an interface language: Quran, mosques, cities, and that language's libraries.
    public static func builtins(for ui: String) -> (PackManifest) -> Bool {
        let wanted = libraryLanguages(ui)
        return { m in !isLibrary(m.id) || wanted.contains(libraryLanguage(m.id)) }
    }

    var sql: String {
        switch self {
        case .books: return ChunkScope.libraryPrefixes.map { "c.packId NOT LIKE '\($0)%'" }.joined(separator: " AND ")
        case .library: return "c.packId = ?"
        }
    }
    var args: [DatabaseValueConvertible?] {
        switch self {
        case .books: return []
        case .library(let p): return [p]
        }
    }
}

/// FTS5 candidates for the retriever.
public final class GRDBChunkStore: ChunkStore, @unchecked Sendable {
    let db: AppDatabase
    let scope: ChunkScope
    public let totalChunks: Int
    public let averageLength: Double

    public init(db: AppDatabase, scope: ChunkScope = .books) throws {
        self.db = db
        self.scope = scope
        let where_ = scope.sql, args = StatementArguments(scope.args)
        (totalChunks, averageLength) = try db.writer.read { db in
            (try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM source_chunk c WHERE \(where_)", arguments: args) ?? 0,
             try Double.fetchOne(db, sql: "SELECT COALESCE(AVG(c.tokenCount), 0) FROM source_chunk c WHERE \(where_)", arguments: args) ?? 0)
        }
    }

    static func quote(_ t: String) -> String { "\"" + t.replacingOccurrences(of: "\"", with: "") + "\"" }

    public func documentFrequency(_ term: String) -> Int {
        let args = StatementArguments([Self.quote(term) as DatabaseValueConvertible?] + scope.args)
        return (try? db.writer.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM source_chunk_fts f CROSS JOIN source_chunk c ON c.rowid = f.rowid WHERE source_chunk_fts MATCH ? AND \(scope.sql)", arguments: args)
        }) ?? 0
    }

    public func candidates(_ terms: [String]) -> [CandidateChunk] {
        guard !terms.isEmpty else { return [] }
        let match = terms.map(Self.quote).joined(separator: " OR ")
        let args = StatementArguments([match as DatabaseValueConvertible?] + scope.args)
        return (try? db.writer.read { db in
            try Row.fetchAll(db, sql: """
                SELECT c.id, c.seq, c.searchText FROM source_chunk_fts f
                CROSS JOIN source_chunk c ON c.rowid = f.rowid WHERE source_chunk_fts MATCH ? AND \(scope.sql)
                """, arguments: args).map { r in
                let text: String = r["searchText"]
                return CandidateChunk(id: r["id"], seq: r["seq"], tokens: text.isEmpty ? [] : text.split(separator: " ").map(String.init))
            }
        }) ?? []
    }
}

/// One library pack in SQLite; tokens were indexed with `LibraryText.indexTokens`.
public final class GRDBLibraryStore: LibraryStore, @unchecked Sendable {
    let db: AppDatabase
    let packId: String
    public let totalChunks: Int
    public let averageLength: Double

    public init(db: AppDatabase, packId: String) throws {
        self.db = db
        self.packId = packId
        (totalChunks, averageLength) = try db.writer.read { db in
            (try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM source_chunk WHERE packId = ?", arguments: [packId]) ?? 0,
             try Double.fetchOne(db, sql: "SELECT COALESCE(AVG(tokenCount), 0) FROM source_chunk WHERE packId = ?", arguments: [packId]) ?? 0)
        }
    }

    /// FTS5 match: prefix query (`"term"*`) for 3+ letters, exact otherwise.
    static func match(_ variants: [String]) -> String {
        variants.map { v in
            let q = "\"" + v.replacingOccurrences(of: "\"", with: "") + "\""
            return LibraryText.len(v) >= LibraryText.prefixMin ? q + "*" : q
        }.joined(separator: " OR ")
    }

    public func ids(_ variants: [String]) -> Set<String> {
        guard !variants.isEmpty else { return [] }
        return Set((try? db.writer.read { db in
            try String.fetchAll(db, sql: "SELECT c.id FROM source_chunk_fts f CROSS JOIN source_chunk c ON c.rowid = f.rowid WHERE source_chunk_fts MATCH ? AND c.packId = ?",
                                arguments: [Self.match(variants), packId])
        }) ?? [])
    }

    public func lengths(_ ids: [String]) -> [String: Int] {
        guard !ids.isEmpty else { return [:] }
        return (try? db.writer.read { db in
            var out: [String: Int] = [:]
            for start in stride(from: 0, to: ids.count, by: 500) {
                let part = Array(ids[start..<min(start + 500, ids.count)])
                for r in try Row.fetchAll(db, sql: "SELECT id, tokenCount FROM source_chunk WHERE id IN (\(part.map { _ in "?" }.joined(separator: ",")))",
                                          arguments: StatementArguments(part)) {
                    out[r["id"]] = r["tokenCount"]
                }
            }
            return out
        }) ?? [:]
    }

    public func rows(_ ids: [String]) -> [CandidateChunk] {
        guard !ids.isEmpty else { return [] }
        return (try? db.writer.read { db in
            try stride(from: 0, to: ids.count, by: 500).flatMap { start -> [CandidateChunk] in
                let part = Array(ids[start..<min(start + 500, ids.count)])
                return try Row.fetchAll(db, sql: "SELECT id, seq, searchText FROM source_chunk WHERE id IN (\(part.map { _ in "?" }.joined(separator: ",")))",
                                        arguments: StatementArguments(part)).map { r in
                    let text: String = r["searchText"]
                    return CandidateChunk(id: r["id"], seq: r["seq"], tokens: text.isEmpty ? [] : text.split(separator: " ").map(String.init))
                }
            }
        }) ?? []
    }
}

public struct ResolvedCitation: Identifiable, Sendable {
    public var id: String { chunk.id }
    public let chunk: SourceChunk
    public let packId: String
    public let documents: [String: SourceDocument]
}

public final class AskRepository: @unchecked Sendable {
    let db: AppDatabase
    let stopwords: [String: [String]]
    let libraryStopwords: Set<String>
    let lexicon: Lexicon
    /// Meaning-based search; nil (tests) or not yet built means word search only.
    let semantic: SemanticIndexStore?
    private var cache: (key: String, retriever: Retriever, questions: [CommonQuestion], libraries: [String: LibraryRetriever])?
    private let lock = NSLock()
    public static let libraryResults = 6
    public static let libraryEnough = 3

    public init(db: AppDatabase, stopwords: [String: [String]], lexicon: Lexicon = .empty, semantic: SemanticIndexStore? = nil) {
        self.db = db
        self.stopwords = stopwords
        self.libraryStopwords = LibraryText.stopwords(stopwords)
        self.lexicon = lexicon
        self.semantic = semantic
    }

    private func retriever() throws -> (Retriever, [CommonQuestion]) {
        let c = try cached()
        return (c.retriever, c.questions)
    }

    private func cached() throws -> (key: String, retriever: Retriever, questions: [CommonQuestion], libraries: [String: LibraryRetriever]) {
        lock.lock(); defer { lock.unlock() }
        let key = try db.writer.read { db in
            try String.fetchOne(db, sql: "SELECT group_concat(id || ':' || installedAt) FROM installed_pack WHERE kind = 'sources'") ?? ""
        }
        if let c = cache, c.key == key { return c }
        let qs = try db.writer.read { db in
            try String.fetchAll(db, sql: "SELECT json FROM common_question ORDER BY packId, position")
        }.compactMap { try? JSONDecoder().decode(CommonQuestion.self, from: Data($0.utf8)) }
        let r = Retriever(store: try GRDBChunkStore(db: db), commonQuestions: qs, stopwords: stopwords)
        let packs = try db.writer.read { db in
            try String.fetchAll(db, sql: "SELECT DISTINCT packId FROM source_chunk")
        }.filter(ChunkScope.isLibrary).sorted()
        var libraries: [String: LibraryRetriever] = [:]
        for p in packs {
            libraries[p] = LibraryRetriever(store: try GRDBLibraryStore(db: db, packId: p), stopwords: libraryStopwords, lexicon: lexicon)
        }
        let c = (key, r, qs, libraries)
        cache = c
        return c
    }

    /// Library items for the question, by language: the interface language first, then English, then
    /// Arabic. Results from several libraries in one language (IslamHouse and the Ibn Baz fatwas in Arabic)
    /// are interleaved, strongest library first. The next language only fills in while fewer than
    /// `libraryEnough` items were found, so the reader's own language dominates; the lexicon lets a
    /// question in one language find items in another.
    public func library(_ q: String, context: [String] = [], lang: String) throws -> [String] {
        let libs = try cached().libraries
        var seen = Set<String>(), out: [String] = []
        for l in [lang, "en", "ar"] where seen.insert(l).inserted {
            if out.count >= Self.libraryEnough { break }
            let hits = libs.filter { ChunkScope.libraryLanguage($0.key) == l }.sorted { $0.key < $1.key }
                .map { $0.value.retrieve(q, context: context) }
                .filter { !$0.passages.isEmpty }
            for id in Self.interleave(hits.map(\.passages)) where !out.contains(id) { out.append(id) }
        }
        let langs: Set<String> = [lang, "en", "ar"]
        return withSemantic(q, Array(out.prefix(Self.libraryResults)), packs: libs.keys.filter { langs.contains(ChunkScope.libraryLanguage($0)) }, limit: Self.libraryResults)
    }

    public static let semanticCandidates = 10

    /// Adds items found by meaning (see SemanticMerge): they must clear a higher bar when word search found
    /// nothing, and at most three join, after the first three word-search results.
    func withSemantic(_ question: String, _ words: [String], packs: [String], limit: Int) -> [String] {
        guard let semantic else { return words }
        let indexes = packs.compactMap { semantic.index($0) }
        guard !indexes.isEmpty, let q = semantic.embed(question) else { return words }
        let hits = indexes.flatMap { $0.search(q, k: Self.semanticCandidates, floor: SemanticMerge.assist) }
            .sorted { $0.score != $1.score ? $0.score > $1.score : $0.id < $1.id }
        return SemanticMerge.merge(words, hits, limit: limit)
    }

    /// Round-robin over ranked lists, starting with the list whose best passage scores highest.
    static func interleave(_ lists: [[ScoredPassage]]) -> [String] {
        let ordered = lists.sorted { ($0.first?.score ?? 0) > ($1.first?.score ?? 0) }
        var out: [String] = [], seen = Set<String>()
        for i in 0..<(ordered.map(\.count).max() ?? 0) {
            for l in ordered where i < l.count && seen.insert(l[i].chunkId).inserted { out.append(l[i].chunkId) }
        }
        return out
    }

    public func commonQuestions() throws -> [CommonQuestion] { try retriever().1 }

    public func retrieve(_ q: String, context: [String] = []) throws -> RetrievalResult { try retriever().0.retrieve(q, context: context) }

    /// Another phrasing of the question to search the library with (written on the device), in `lang`.
    public struct AlsoSearch: Sendable {
        public let text: String
        public let lang: String
        public init(text: String, lang: String) { self.text = text; self.lang = lang }
    }

    /// `also`: rephrasings (e.g. an Arabic query for an English question) whose library results are
    /// interleaved after the question's own; each passes the same search gates on its own.
    public func ask(_ q: String, context: [String] = [], lang: String = "en", also: [AlsoSearch] = []) throws -> Answer {
        var a = AnswerComposer.compose(q, try retrieve(q, context: context))
        var lib = try library(q, context: context, lang: lang)
        if !also.isEmpty {
            lib = Self.roundRobin([lib] + (try also.map { try library($0.text, lang: $0.lang) }), limit: Self.libraryResults)
        }
        a.library = try withHadith(a.commonQuestion, lib, lang: lang)
        return a
    }

    /// One id from each list in turn (first list first), without duplicates.
    static func roundRobin(_ lists: [[String]], limit: Int) -> [String] {
        var out: [String] = []
        for i in 0..<(lists.map(\.count).max() ?? 0) {
            for l in lists where i < l.count && !out.contains(l[i]) { out.append(l[i]) }
        }
        return Array(out.prefix(limit))
    }

    /// A tapped common question always shows that question's answer, plus extra passages found.
    public func answer(for q: CommonQuestion, displayed: String, lang: String = "en") throws -> Answer {
        var r = try retrieve(displayed)
        r.kind = .common
        r.commonQuestion = q
        var a = AnswerComposer.compose(displayed, r)
        a.library = try withHadith(q, try library(displayed, lang: lang), lang: lang)
        return a
    }

    /// A common question's own hadith (installed ones, in the reader's language when possible) lead the library list.
    func withHadith(_ q: CommonQuestion?, _ library: [String], lang: String) throws -> [String] {
        guard let q, let items = q.hadith, !items.isEmpty else { return library }
        let wanted = items.flatMap { CommonHadith.candidates($0, lang: lang) }
        let installed = try db.writer.read { db in
            Set(try String.fetchAll(db, sql: "SELECT id FROM source_chunk WHERE id IN (\(wanted.map { _ in "?" }.joined(separator: ",")))", arguments: StatementArguments(wanted)))
        }
        return CommonHadith.merge(CommonHadith.resolve(q, lang: lang, installed: installed), library, limit: Self.libraryResults)
    }

    public func resolve(_ ids: [String]) throws -> [ResolvedCitation] {
        guard !ids.isEmpty else { return [] }
        return try db.writer.read { db in
            let docs = Dictionary(uniqueKeysWithValues: try String.fetchAll(db, sql: "SELECT json FROM source_document")
                .compactMap { try? JSONDecoder().decode(SourceDocument.self, from: Data($0.utf8)) }.map { ($0.id, $0) })
            let rows = try Row.fetchAll(db, sql: "SELECT id, packId, json FROM source_chunk WHERE id IN (\(ids.map { _ in "?" }.joined(separator: ",")))", arguments: StatementArguments(ids))
            let byId = Dictionary(uniqueKeysWithValues: rows.map { ($0["id"] as String, $0) })
            return ids.compactMap { id in
                guard let r = byId[id], let c = try? JSONDecoder().decode(SourceChunk.self, from: Data((r["json"] as String).utf8)) else { return nil }
                return ResolvedCitation(chunk: c, packId: r["packId"], documents: docs)
            }
        }
    }

    /// An installed library collection, for browsing.
    public struct LibraryPack: Identifiable, Sendable {
        public let id: String
        public let title: [String: String]
        public let language: String
        public let count: Int
    }

    public func libraryPacks() throws -> [LibraryPack] {
        try PackManager(db: db, bundledRoot: nil).installed().filter { ChunkScope.isLibrary($0.id) }
            .map { LibraryPack(id: $0.id, title: $0.manifest.title, language: ChunkScope.libraryLanguage($0.id), count: $0.recordCount) }
            .sorted { ($0.language, $0.id) < ($1.language, $1.id) }
    }

    /// Items of one collection in its own order, a page at a time.
    public func browse(_ packId: String, offset: Int, limit: Int) throws -> [ResolvedCitation] {
        let ids = try db.writer.read { db in
            try String.fetchAll(db, sql: "SELECT id FROM source_chunk WHERE packId = ? ORDER BY seq LIMIT ? OFFSET ?", arguments: [packId, limit, offset])
        }
        return try resolve(ids)
    }

    public static let searchInResults = 30

    /// Search inside one collection with library search.
    public func searchIn(_ packId: String, _ query: String) throws -> [ResolvedCitation] {
        guard let r = try cached().libraries[packId] else { return [] }
        let words = r.retrieve(query, limit: Self.searchInResults).passages.map(\.chunkId)
        return try resolve(withSemantic(query, words, packs: [packId], limit: Self.searchInResults))
    }

    public func context(_ c: ResolvedCitation, around: Int = 3) throws -> [SourceChunk] {
        try db.writer.read { db in
            try String.fetchAll(db, sql: "SELECT json FROM source_chunk WHERE packId = ? AND seq BETWEEN ? AND ? ORDER BY seq",
                                arguments: [c.packId, c.chunk.seq - around, c.chunk.seq + around])
        }.compactMap { try? JSONDecoder().decode(SourceChunk.self, from: Data($0.utf8)) }
    }

    public static func parseStopwords(_ data: Data) -> [String: [String]] {
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [:] }
        // Language lists plus `_domain`; the Retriever itself ignores keys starting with "_".
        return obj.compactMapValues { $0 as? [String] }
    }
}
