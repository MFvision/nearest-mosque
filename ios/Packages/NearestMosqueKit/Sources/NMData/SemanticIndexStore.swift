import Foundation
import GRDB
import NMCore

/// Semantic vectors of the installed library collections (see NMCore SemanticSearch). They are computed on
/// the phone from the installed records with the bundled model, once per pack install, and cached as files
/// in `dir`; nothing leaves the device. Until a collection's vectors exist, Ask uses word search only.
public final class SemanticIndexStore: @unchecked Sendable {
    let dir: URL
    let db: AppDatabase
    let modelURL: URL
    private let lock = NSLock()
    private let buildLock = NSLock()
    private var loaded: StaticEmbedder?
    private var indexes: [String: VectorIndex] = [:]
    static let magic: UInt32 = 0x4E4D_5631 // "NMV1"

    public init(dir: URL, db: AppDatabase, modelURL: URL) {
        self.dir = dir
        self.db = db
        self.modelURL = modelURL
    }

    private var embedder: StaticEmbedder? {
        lock.lock(); defer { lock.unlock() }
        if loaded == nil { loaded = try? StaticEmbedder(data: Data(contentsOf: modelURL, options: .mappedIfSafe)) }
        return loaded
    }

    /// Collections whose vectors are loaded.
    public var ready: Set<String> { lock.lock(); defer { lock.unlock() }; return Set(indexes.keys) }

    public func index(_ packId: String) -> VectorIndex? { lock.lock(); defer { lock.unlock() }; return indexes[packId] }

    public func embed(_ text: String) -> [Double]? { embedder?.embed(text) }

    /// Loads or builds the vectors of every installed library pack and drops those of removed packs.
    public func ensure() throws {
        buildLock.lock(); defer { buildLock.unlock() }
        guard let embedder else { return }
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let packs = try db.writer.read { db in
            try Row.fetchAll(db, sql: "SELECT id, version, installedAt FROM installed_pack WHERE kind = 'sources'")
                .map { (id: $0["id"] as String, name: "\($0["id"] as String)@\($0["version"] as Int)-\(Int64(($0["installedAt"] as Double) * 1000)).vec") }
                .filter { ChunkScope.isLibrary($0.id) }
        }
        let names = Set(packs.map(\.name))
        for f in (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? [] where !names.contains(f.lastPathComponent) {
            try? FileManager.default.removeItem(at: f)
        }
        lock.lock(); indexes = indexes.filter { k, _ in packs.contains { $0.id == k } }; lock.unlock()
        for p in packs {
            let file = dir.appendingPathComponent(p.name)
            if index(p.id) != nil, FileManager.default.fileExists(atPath: file.path) { continue }
            let built: VectorIndex
            if let cached = try? read(file) { built = cached } else {
                built = try build(p.id, embedder)
                try write(built, to: file)
            }
            lock.lock(); indexes[p.id] = built; lock.unlock()
        }
    }

    private func build(_ packId: String, _ embedder: StaticEmbedder) throws -> VectorIndex {
        var ids: [String] = [], vectors: [Int8] = []
        try db.writer.read { db in
            let rows = try Row.fetchCursor(db, sql: "SELECT id, json FROM source_chunk WHERE packId = ? ORDER BY seq", arguments: [packId])
            while let r = try rows.next() {
                guard let c = try? JSONDecoder().decode(SourceChunk.self, from: Data((r["json"] as String).utf8)) else { continue }
                ids.append(r["id"])
                vectors += StaticEmbedder.quantize(embedder.embed(SemanticText.of(c)))
            }
        }
        return VectorIndex(ids: ids, vectors: vectors, dim: embedder.dim)
    }

    private func write(_ index: VectorIndex, to file: URL) throws {
        var data = Data()
        func u32(_ v: UInt32) { withUnsafeBytes(of: v.littleEndian) { data.append(contentsOf: $0) } }
        u32(Self.magic); u32(UInt32(index.dim)); u32(UInt32(index.ids.count))
        for id in index.ids { data.append(contentsOf: id.utf8); data.append(0x0A) }
        data.append(contentsOf: index.vectorBytes.map { UInt8(bitPattern: $0) })
        try data.write(to: file, options: .atomic)
    }

    private func read(_ file: URL) throws -> VectorIndex {
        let b = [UInt8](try Data(contentsOf: file))
        func u32(_ p: Int) -> Int { Int(UInt32(b[p]) | UInt32(b[p + 1]) << 8 | UInt32(b[p + 2]) << 16 | UInt32(b[p + 3]) << 24) }
        guard b.count >= 12, u32(0) == Int(Self.magic) else { throw CocoaError(.fileReadCorruptFile) }
        let dim = u32(4), n = u32(8)
        var ids: [String] = []
        ids.reserveCapacity(n)
        var p = 12
        for _ in 0..<n {
            var e = p
            while b[e] != 0x0A { e += 1 }
            ids.append(String(decoding: b[p..<e], as: UTF8.self))
            p = e + 1
        }
        guard b.count == p + n * dim else { throw CocoaError(.fileReadCorruptFile) }
        return VectorIndex(ids: ids, vectors: b[p...].map { Int8(bitPattern: $0) }, dim: dim)
    }
}
