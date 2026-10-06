import Foundation
import XCTest
@testable import NMCore

/// Semantic search matches tools/semantic.py on shared/fixtures/semantic.json.
final class SemanticSearchTests: XCTestCase {
    let fixture = Fixtures.json("shared/fixtures/semantic.json")
    lazy var model = try! StaticEmbedder(data: Data(contentsOf: Fixtures.root.appendingPathComponent("shared/semantic/model.bin")))

    func testTokenizesLikeReference() {
        for c in fixture["tokens"] as! [[String: Any]] {
            let text = c["text"] as! String
            XCTAssertEqual(model.tokenize(text), c["ids"] as! [Int], text)
        }
    }

    func testEmbedsLikeReference() {
        for c in fixture["embeddings"] as! [[String: Any]] {
            let text = c["text"] as! String
            let v = model.embed(text)
            for (d, x) in (c["first"] as! [Double]).enumerated() { XCTAssertEqual(v[d], x, accuracy: 1e-4, "\(text) dim \(d)") }
            XCTAssertEqual(StaticEmbedder.quantize(v), (c["quantized"] as! [Int]).map { Int8($0) }, text)
        }
    }

    func testRanksCorpusLikeReference() throws {
        var ids: [String] = [], vectors: [Int8] = []
        for d in fixture["corpus"] as! [[String: Any]] {
            var json: [String: Any] = ["id": d["id"]!, "seq": 1, "anchor": "", "original": ["docId": "fixture", "lang": "ar", "text": d["text"]!]]
            if let parts = d["parts"] as? [String] { json["section"] = ["parts": parts.map { ["kind": $0] }] }
            let chunk = try JSONDecoder().decode(SourceChunk.self, from: JSONSerialization.data(withJSONObject: json))
            ids.append(chunk.id)
            vectors += StaticEmbedder.quantize(model.embed(SemanticText.of(chunk)))
        }
        let index = VectorIndex(ids: ids, vectors: vectors, dim: model.dim)
        for c in fixture["cases"] as! [[String: Any]] {
            let q = c["q"] as! String
            let got = index.search(model.embed(q), k: 3, floor: 0)
            let want = c["expected"] as! [[Any]]
            XCTAssertEqual(got.map { $0.id }, want.map { $0[1] as! String }, q)
            for (w, g) in zip(want, got) { XCTAssertEqual(g.score, w[0] as! Double, accuracy: 1e-4, q) }
        }
    }

    func testMergesLikeReference() {
        XCTAssertEqual(SemanticMerge.assist, fixture["assist"] as! Double)
        XCTAssertEqual(SemanticMerge.alone, fixture["alone"] as! Double)
        for c in fixture["merge"] as! [[String: Any]] {
            let sem = (c["semantic"] as! [[Any]]).map { (score: $0[0] as! Double, id: $0[1] as! String) }
            XCTAssertEqual(SemanticMerge.merge(c["words"] as! [String], sem), c["expected"] as! [String])
        }
    }
}
