import Foundation
import XCTest
@testable import NMCore

/// Library search matches tools/library_search.py on shared/fixtures/library-retrieval.json.
final class LibrarySearchTests: XCTestCase {
    let fixture = Fixtures.json("shared/fixtures/library-retrieval.json")

    func testStemsAndVariantsMatchReference() {
        for case let c as [Any] in fixture["stem"] as! [Any] {
            let tokens = TextNormalizer.tokens(c[0] as! String)
            XCTAssertEqual(tokens, [c[1] as! String])
            XCTAssertEqual(LibraryText.lightStem(tokens[0]), c[2] as! String, c[0] as! String)
            XCTAssertEqual(LibraryText.variants(tokens[0]), c[3] as! [String], c[0] as! String)
        }
    }

    func testRetrievalMatchesReference() throws {
        let stop = Fixtures.json("shared/content/stopwords.json").compactMapValues { $0 as? [String] }
        let lexicon = Lexicon(json: try Data(contentsOf: Fixtures.root.appendingPathComponent("shared/content/lexicon.json")))
        let docs = (fixture["corpus"] as! [[String: Any]]).map { (id: $0["id"] as! String, seq: $0["seq"] as! Int, text: $0["text"] as! String) }
        let r = LibraryRetriever(store: InMemoryLibraryStore(docs), stopwords: LibraryText.stopwords(stop), lexicon: lexicon)
        for c in fixture["cases"] as! [[String: Any]] {
            let q = c["q"] as! String
            let got = r.retrieve(q, context: c["context"] as? [String] ?? []).passages.map(\.chunkId)
            XCTAssertEqual(got, c["expected"] as! [String], q)
        }
    }
}

final class OtherSourcesTests: XCTestCase {
    func testLinksCarryTheQuestionAndLanguage() {
        let l = Dictionary(uniqueKeysWithValues: OtherSources.links("ما حكم تارك الصلاة؟", lang: "ar").map { ($0.id, $0.url.absoluteString) })
        XCTAssertEqual(l["islamqa"], "https://islamqa.info/ar/search?q=%D9%85%D8%A7%20%D8%AD%D9%83%D9%85%20%D8%AA%D8%A7%D8%B1%D9%83%20%D8%A7%D9%84%D8%B5%D9%84%D8%A7%D8%A9%D8%9F")
        XCTAssertTrue(l["binothaimeen"]?.hasPrefix("https://www.google.com/search?q=site%3Abinothaimeen.net%20") == true)
        XCTAssertEqual(OtherSources.links("prayer", lang: "xx").first?.url.absoluteString, "https://islamqa.info/en/search?q=prayer")
    }
}
