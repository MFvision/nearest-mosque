import Foundation

/// One printed volume of a classical book, as a PDF (`file`) of `bytes` with this SHA-256.
public struct BookVolume: Codable, Sendable, Hashable {
    public let file: String
    public let volume: Int
    public let pages: Int
    public let bytes: Int
    public let sha256: String
}

/// A classical book of the library (shared/books/catalog.json): the author's text from OpenITI, one PDF per volume.
public struct ClassicBook: Codable, Sendable, Hashable, Identifiable {
    public let id: String
    public let group: String
    public let title: String
    public let author: String
    public let edition: String
    public let volumes: [BookVolume]

    public var bytes: Int { volumes.reduce(0) { $0 + $1.bytes } }
}

/// The classical books the app offers (bundled with the app). Only the files it lists are downloaded, from `baseUrl`,
/// and a file is kept only when its SHA-256 matches, so the host can serve nothing but these books.
public struct BookCatalog: Codable, Sendable {
    public let schemaVersion: Int
    public let baseUrl: String
    public let source: String
    public let books: [ClassicBook]

    public static let empty = BookCatalog(schemaVersion: 1, baseUrl: "https://invalid/", source: "", books: [])
    /// The order the library shows the groups in.
    public static let groups = ["aqidah", "tafsir", "hadith", "fiqh", "sira", "tarikh", "raqaiq"]

    public func url(_ v: BookVolume) -> URL? { URL(string: baseUrl + v.file) }

    /// The books by group, in `groups` order (groups the catalog does not use are left out).
    public var byGroup: [(group: String, books: [ClassicBook])] {
        Self.groups.compactMap { g in
            let list = books.filter { $0.group == g }
            return list.isEmpty ? nil : (g, list)
        }
    }

    public static func parse(_ data: Data) throws -> BookCatalog {
        let c: BookCatalog
        do { c = try JSONDecoder().decode(BookCatalog.self, from: data) } catch { throw PackError.format("books: \(error)") }
        guard c.schemaVersion <= 1 else { throw PackError.newerSchema(c.schemaVersion) }
        guard c.baseUrl.hasPrefix("https://"), c.baseUrl.hasSuffix("/") else { throw PackError.format("books: base URL") }
        let safe = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-")
        let hex = CharacterSet(charactersIn: "0123456789abcdef")
        for b in c.books {
            guard !b.id.isEmpty, b.id.unicodeScalars.allSatisfy(safe.contains), !b.volumes.isEmpty else { throw PackError.format("books: \(b.id)") }
            for v in b.volumes {
                guard v.file.hasSuffix(".pdf"), !v.file.hasPrefix("."), v.file.unicodeScalars.allSatisfy(safe.contains),
                      v.sha256.count == 64, v.sha256.unicodeScalars.allSatisfy(hex.contains), v.bytes > 0 else {
                    throw PackError.format("books: unsafe file \(v.file)")
                }
            }
        }
        return c
    }
}
