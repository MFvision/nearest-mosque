import XCTest
@testable import NMCore

final class BooksTests: XCTestCase {
    private let sha = String(repeating: "a", count: 64)
    private func catalog(base: String = "https://example.org/books/", file: String = "tawhid-v01.pdf", hash: String? = nil) -> Data {
        let h = hash ?? sha
        return Data("""
        {"schemaVersion":1,"baseUrl":"\(base)","source":"OpenITI","books":[
         {"id":"tawhid","group":"aqidah","title":"كتاب التوحيد","author":"x","edition":"y","volumes":[{"file":"\(file)","volume":1,"pages":10,"bytes":5,"sha256":"\(h)"}]},
         {"id":"bukhari","group":"hadith","title":"صحيح البخاري","author":"x","edition":"y","volumes":[{"file":"bukhari-v01.pdf","volume":1,"pages":10,"bytes":5,"sha256":"\(sha)"}]}]}
        """.utf8)
    }

    func testParsesAndGroupsInOrder() throws {
        let c = try BookCatalog.parse(catalog())
        XCTAssertEqual(c.byGroup.map(\.group), ["aqidah", "hadith"])
        XCTAssertEqual(c.url(c.books[0].volumes[0])?.absoluteString, "https://example.org/books/tawhid-v01.pdf")
    }

    func testRefusesUnsafeCatalogs() {
        XCTAssertThrowsError(try BookCatalog.parse(catalog(base: "http://example.org/")))
        XCTAssertThrowsError(try BookCatalog.parse(catalog(file: "../x.pdf")))
        XCTAssertThrowsError(try BookCatalog.parse(catalog(file: "x.exe")))
        XCTAssertThrowsError(try BookCatalog.parse(catalog(hash: "nothex")))
    }
}
