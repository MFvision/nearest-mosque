import Foundation
@testable import NMCore
import XCTest

final class RemotePacksTests: XCTestCase {
    // Stored without compression here (the app inflates with the system decompressor); the checks are the same.
    private let chunks = Data(#"{"id":"he:sw:1","seq":1,"anchor":"x","section":{},"original":{"docId":"d","lang":"sw","text":"t"}}"#.utf8)
    private var manifest: Data {
        Data("""
        {"id":"sources.hadeethenc-sw","kind":"sources","schemaVersion":1,"version":1,"title":{"en":"t"},"languages":["sw"],"recordCount":1,
         "source":{"name":"s","url":"https://s"},"license":{"id":"l","name":"l","url":"https://l","attribution":"a"},
         "files":[{"path":"chunks.jsonl","bytes":\(chunks.count),"sha256":"\(Sha256.hex(chunks))"}]}
        """.utf8)
    }

    private func catalog(manifestSha: String? = nil) -> (RemoteCatalog, [String: Data]) {
        let m = manifest
        let pack = RemotePack(id: "sources.hadeethenc-sw", language: "sw", title: ["en": "t"], version: 1, recordCount: 1,
                              manifestSha256: manifestSha ?? Sha256.hex(m), bytes: m.count + chunks.count, installedBytes: m.count + chunks.count,
                              files: [RemoteFile(path: "manifest.json", asset: "p.manifest.json.z", bytes: m.count, sha256: Sha256.hex(m), size: m.count),
                                      RemoteFile(path: "chunks.jsonl", asset: "p.chunks.jsonl.z", bytes: chunks.count, sha256: Sha256.hex(chunks), size: chunks.count)])
        return (RemoteCatalog(schemaVersion: 1, baseUrl: "https://host/c1/", packs: [pack]),
                ["https://host/c1/p.manifest.json.z": m, "https://host/c1/p.chunks.jsonl.z": chunks])
    }

    func testFetchChecksAndReturnsFiles() async throws {
        let (c, assets) = catalog()
        var received = 0
        let p = try await RemotePacks.fetch(c, c.forLanguage("sw")[0], get: { assets[$0.absoluteString]! }, inflate: { d, _ in d }, progress: { received += $0 })
        XCTAssertEqual(p.manifest.id, "sources.hadeethenc-sw")
        XCTAssertEqual(p.files["chunks.jsonl"], chunks)
        XCTAssertEqual(received, c.forLanguage("sw")[0].bytes)
        XCTAssertNoThrow(try PackVerifier.verify(p.manifest) { p.files[$0] })
    }

    func testRejectsChangedFileOrForeignManifest() async {
        let (c, assets) = catalog()
        var bad = assets
        bad["https://host/c1/p.chunks.jsonl.z"] = Data("x".utf8) + chunks.dropFirst()
        do {
            _ = try await RemotePacks.fetch(c, c.packs![0], get: { bad[$0.absoluteString]! }, inflate: { d, _ in d })
            XCTFail("changed file accepted")
        } catch {}
        let (c2, assets2) = catalog(manifestSha: String(repeating: "0", count: 64))
        do {
            _ = try await RemotePacks.fetch(c2, c2.packs![0], get: { assets2[$0.absoluteString]! }, inflate: { d, _ in d })
            XCTFail("foreign manifest accepted")
        } catch {
            XCTAssertEqual(error as? PackError, .checksum("manifest.json"))
        }
    }

    func testCatalogRules() {
        XCTAssertThrowsError(try RemoteCatalog.parse(Data(#"{"schemaVersion":1,"baseUrl":"http://x/","packs":[]}"#.utf8)))
        XCTAssertThrowsError(try RemoteCatalog.parse(Data(#"""
        {"schemaVersion":1,"baseUrl":"https://x/","packs":[{"id":"a","language":"sw","title":{},"version":1,"manifestSha256":"0","bytes":1,
         "installedBytes":1,"files":[{"path":"m","asset":"../x","bytes":1,"sha256":"0","size":1}]}]}
        """#.utf8)))
        XCTAssertEqual(try RemoteCatalog.parse(Data(#"{"schemaVersion":1,"baseUrl":"https://x/"}"#.utf8)).forLanguage("sw").count, 0)
    }
}
