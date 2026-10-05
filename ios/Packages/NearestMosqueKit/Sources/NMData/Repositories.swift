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

    public static let libraryPrefix = "sources.islamhouse-"
    public static func libraryPack(_ lang: String) -> String { libraryPrefix + lang }

    var sql: String {
        switch self {
        case .books: return "c.packId NOT LIKE '\(ChunkScope.libraryPrefix)%'"
        case .library: return "c.packId = ?"
        }
    }
    var args: [DatabaseValueConvertible] {
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
        let args = StatementArguments([Self.quote(term)] + scope.args)
        return (try? db.writer.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM source_chunk c JOIN source_chunk_fts f ON f.rowid = c.rowid WHERE source_chunk_fts MATCH ? AND \(scope.sql)", arguments: args)
        }) ?? 0
    }

    public func candidates(_ terms: [String]) -> [CandidateChunk] {
        guard !terms.isEmpty else { return [] }
        let match = terms.map(Self.quote).joined(separator: " OR ")
        let args = StatementArguments([match] + scope.args)
        return (try? db.writer.read { db in
            try Row.fetchAll(db, sql: """
                SELECT c.id, c.seq, c.searchText FROM source_chunk c
                JOIN source_chunk_fts f ON f.rowid = c.rowid WHERE source_chunk_fts MATCH ? AND \(scope.sql)
                """, arguments: args).map { r in
                let text: String = r["searchText"]
                return CandidateChunk(id: r["id"], seq: r["seq"], tokens: text.isEmpty ? [] : text.split(separator: " ").map(String.init))
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
    let libraryStopwords: [String: [String]]
    private var cache: (key: String, retriever: Retriever, questions: [CommonQuestion], libraries: [String: Retriever])?
    private let lock = NSLock()
    public static let libraryResults = 5

    public init(db: AppDatabase, stopwords: [String: [String]]) {
        self.db = db
        self.stopwords = stopwords
        self.libraryStopwords = NMCore.libraryStopwords(stopwords)
    }

    private func retriever() throws -> (Retriever, [CommonQuestion]) {
        let c = try cached()
        return (c.retriever, c.questions)
    }

    private func cached() throws -> (key: String, retriever: Retriever, questions: [CommonQuestion], libraries: [String: Retriever]) {
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
            try String.fetchAll(db, sql: "SELECT DISTINCT packId FROM source_chunk WHERE packId LIKE ?", arguments: [ChunkScope.libraryPrefix + "%"])
        }
        var libraries: [String: Retriever] = [:]
        for p in packs {
            libraries[String(p.dropFirst(ChunkScope.libraryPrefix.count))] =
                Retriever(store: try GRDBChunkStore(db: db, scope: .library(p)), commonQuestions: [], stopwords: libraryStopwords, gates: .library)
        }
        let c = (key, r, qs, libraries)
        cache = c
        return c
    }

    /// Library items (IslamHouse) for the question: the interface language's library first, then English
    /// and Arabic; the first library with evidence wins.
    public func library(_ q: String, context: [String] = [], lang: String) throws -> [String] {
        let libs = try cached().libraries
        var seen = Set<String>()
        for l in [lang, "en", "ar"] where seen.insert(l).inserted {
            guard let r = libs[l]?.retrieve(q, context: context), r.kind != .insufficient else { continue }
            return Array(r.passages.map(\.chunkId).prefix(Self.libraryResults))
        }
        return []
    }

    public func commonQuestions() throws -> [CommonQuestion] { try retriever().1 }

    public func retrieve(_ q: String, context: [String] = []) throws -> RetrievalResult { try retriever().0.retrieve(q, context: context) }

    public func ask(_ q: String, context: [String] = [], lang: String = "en") throws -> Answer {
        var a = AnswerComposer.compose(q, try retrieve(q, context: context))
        a.library = try library(q, context: context, lang: lang)
        return a
    }

    /// A tapped common question always shows that question's answer, plus extra passages found.
    public func answer(for q: CommonQuestion, displayed: String, lang: String = "en") throws -> Answer {
        var r = try retrieve(displayed)
        r.kind = .common
        r.commonQuestion = q
        var a = AnswerComposer.compose(displayed, r)
        a.library = try library(displayed, lang: lang)
        return a
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
