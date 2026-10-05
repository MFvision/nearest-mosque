import Foundation
import XCTest
import NMCore
@testable import NMData

final class DataLayerTests: XCTestCase {
    static let root: URL = {
        var u = URL(fileURLWithPath: #filePath)
        while u.path != "/" {
            u.deleteLastPathComponent()
            if FileManager.default.fileExists(atPath: u.appendingPathComponent("shared/fixtures").path) { return u }
        }
        fatalError("root not found")
    }()

    var db: AppDatabase!
    var packs: PackManager!
    var defaults: UserDefaults!

    override func setUpWithError() throws {
        db = try AppDatabase.inMemory()
        defaults = UserDefaults(suiteName: "nm-test-\(UUID().uuidString)")!
        packs = PackManager(db: db, bundledRoot: Self.root.appendingPathComponent("packs"), defaults: defaults)
        // Quran, mosques and cities only: the library packs are installed once, in testLibraryCollections.
        XCTAssertEqual(packs.ensureBuiltins(include: Self.core), [])
    }

    static let core: (PackManifest) -> Bool = { !ChunkScope.isLibrary($0.id) }

    func testBuiltinsInstallWithManifestCounts() throws {
        let installed = try packs.installed()
        XCTAssertEqual(Set(installed.map(\.id)), ["mosques.za-cape-town", "mosques.eg-cairo", "mosques.gb-london", "sources.quran-tanzil-pickthall"])
        for p in installed { XCTAssertEqual(p.recordCount, p.manifest.recordCount, p.id) }
    }

    func testRetrievalThroughFts5MatchesReference() throws {
        let stop = AskRepository.parseStopwords(try Data(contentsOf: Self.root.appendingPathComponent("shared/content/stopwords.json")))
        let ask = AskRepository(db: db, stopwords: stop)
        let fixture = try JSONSerialization.jsonObject(with: Data(contentsOf: Self.root.appendingPathComponent("shared/fixtures/retrieval.json"))) as! [String: Any]
        var failures: [String] = []
        for c in fixture["cases"] as! [[String: Any]] {
            let r = try ask.retrieve(c["q"] as! String, context: c["context"] as? [String] ?? [])
            let ref = c["referenceResult"] as! [String: Any]
            if r.kind.rawValue != ref["kind"] as! String || r.commonQuestion?.id != ref["commonQuestionId"] as? String || r.passages.map(\.chunkId) != ref["passages"] as! [String] {
                failures.append("\(c["id"]!): \(r.kind) \(String(describing: r.commonQuestion?.id)) \(r.passages.map(\.chunkId))")
            }
        }
        XCTAssert(failures.isEmpty, failures.joined(separator: "\n"))
        let wudu = try ask.ask("How do I perform wudu?")
        XCTAssertEqual(wudu.citations.first, "quran:5:6")
        let resolved = try ask.resolve(wudu.citations)
        XCTAssertNotNil(resolved.first?.documents["quran-en-pickthall"])
        XCTAssertEqual(try ask.context(resolved[0]).map(\.id), ["quran:5:3", "quran:5:4", "quran:5:5", "quran:5:6", "quran:5:7", "quran:5:8", "quran:5:9"])
    }

    /// The library collections, installed once (English, Arabic, Urdu): IslamHouse, the Ibn Baz fatwas,
    /// HadeethEnc hadiths and QuranEnc translations, searched in the reader's language first.
    func testLibraryCollections() throws {
        let wanted: Set<String> = ["en", "ar", "ur"]
        XCTAssertEqual(packs.ensureBuiltins { ChunkScope.isLibrary($0.id) && wanted.contains(ChunkScope.libraryLanguage($0.id)) }, [])
        for p in try packs.installed() where ChunkScope.isLibrary(p.id) { XCTAssertEqual(p.recordCount, p.manifest.recordCount, p.id) }
        let stop = AskRepository.parseStopwords(try Data(contentsOf: Self.root.appendingPathComponent("shared/content/stopwords.json")))
        let lexicon = Lexicon(json: try Data(contentsOf: Self.root.appendingPathComponent("shared/content/lexicon.json")))
        let ask = AskRepository(db: db, stopwords: stop, lexicon: lexicon)

        let en = try ask.ask("What is Islam?", lang: "en")
        XCTAssertFalse(en.library.isEmpty)
        XCTAssertFalse(en.library.contains { $0.hasPrefix("bb:") })
        let items = try ask.resolve(en.library.filter { $0.hasPrefix("ih:en:") })
        XCTAssertTrue(items.contains { $0.chunk.anchor.localizedCaseInsensitiveContains("Islam") })
        XCTAssertTrue(try ask.ask("What is the capital of France?", lang: "en").library.isEmpty)

        let fatwas = try ask.resolve(try ask.ask("ما حكم تارك الصلاة؟", lang: "ar").library.filter { $0.hasPrefix("bb:") })
        XCTAssertFalse(fatwas.isEmpty)
        XCTAssertEqual(fatwas.first?.chunk.section?.publisher, "binbaz")
        XCTAssertEqual(fatwas.first.map { LibraryParts.parts($0.chunk).map(\.kind) }, ["title", "question", "answer"])
        XCTAssertFalse(try ask.ask("صلاته", lang: "ar").library.isEmpty)

        let hadiths = try ask.resolve(try ask.ask("Islam is built on five", lang: "en").library.filter { $0.hasPrefix("he:en:") })
        XCTAssertFalse(hadiths.isEmpty)
        XCTAssertEqual(hadiths.first?.chunk.section?.publisher, "hadeethenc")
        XCTAssertFalse(hadiths.first?.chunk.section?.version?.isEmpty ?? true)
        let ur = try ask.ask("نماز کی پابندی", lang: "ur")
        XCTAssertFalse(ur.library.isEmpty)
        XCTAssertTrue(ur.library.allSatisfy { $0.hasPrefix("ih:ur:") || $0.hasPrefix("he:ur:") || $0.hasPrefix("qe:ur:") })
        let verse = try ask.resolve(["qe:ur:2:255"]).first?.chunk
        XCTAssertEqual(verse?.section?.verse, "quran:2:255")
        XCTAssertEqual(verse.map { LibraryParts.parts($0).first?.kind }, "translation")
        XCTAssertEqual(try ask.context(try ask.resolve(["quran:2:255"])[0]).count, 7) // Quran collection unchanged
    }

    func testRemoveAndRestoreBookPack() throws {
        let stop = AskRepository.parseStopwords(try Data(contentsOf: Self.root.appendingPathComponent("shared/content/stopwords.json")))
        let ask = AskRepository(db: db, stopwords: stop)
        try packs.remove("sources.quran-tanzil-pickthall")
        XCTAssertEqual(try ask.ask("neither slumber nor sleep").kind, .insufficient)
        packs.ensureBuiltins(include: Self.core)
        XCTAssertFalse(try packs.installed().contains { $0.id == "sources.quran-tanzil-pickthall" })
        packs.restoreBuiltins(include: Self.core)
        XCTAssertEqual(try ask.ask("neither slumber nor sleep").citations.first, "quran:2:255")
    }

    func testCorruptUpdateKeepsInstalledVersion() throws {
        let dir = Self.root.appendingPathComponent("packs/mosques/za-cape-town")
        var m = try PackVerifier.parseManifest(Data(contentsOf: dir.appendingPathComponent("manifest.json")))
        let json = String(decoding: try JSONEncoder().encode(m), as: UTF8.self).replacingOccurrences(of: "\"version\":1", with: "\"version\":2")
        m = try JSONDecoder().decode(PackManifest.self, from: Data(json.utf8))
        XCTAssertThrowsError(try packs.install(m, builtin: true) { _ in Data("{}".utf8) })
        XCTAssertEqual(try packs.installed().first { $0.id == m.id }?.version, 1)
    }

    func testMosqueResultsAndFavorites() throws {
        let repo = MosqueRepository(db: db)
        guard case .found(let items, let coverage) = try repo.nearest(LatLng(-33.9258, 18.4232)!, radiusMeters: 25_000, lang: "en") else { return XCTFail() }
        XCTAssertEqual(items.map(\.distanceMeters), items.map(\.distanceMeters).sorted())
        XCTAssertTrue(coverage.contains("Cape Town"))
        guard case .areaNotDownloaded = try repo.nearest(LatLng(0, 0)!, radiusMeters: 25_000, lang: "en") else { return XCTFail() }
        guard case .noRecordsInCoverage = try repo.nearest(LatLng(51.62, -0.47)!, radiusMeters: 30, lang: "en") else { return XCTFail() }
        let id = items[0].mosque.sourceId
        try repo.setFavorite(id, true)
        try packs.remove("mosques.za-cape-town")
        packs.restoreBuiltins(include: Self.core)
        XCTAssertEqual(try repo.favorites(), [id])
        XCTAssertEqual(try repo.byIds([id]).map(\.sourceId), [id])
    }
}
