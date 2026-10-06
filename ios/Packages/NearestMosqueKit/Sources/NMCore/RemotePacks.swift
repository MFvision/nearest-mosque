import Foundation

/// One file of a downloadable pack: compressed (`bytes`, `sha256`) and installed (`size`).
public struct RemoteFile: Codable, Sendable, Hashable {
    public let path: String
    public let asset: String
    public let bytes: Int
    public let sha256: String
    public let size: Int
}

public struct RemotePack: Codable, Sendable, Hashable, Identifiable {
    public let id: String
    public let language: String
    public let title: [String: String]
    public let version: Int
    public var recordCount: Int?
    public let manifestSha256: String
    /// Download size (compressed).
    public let bytes: Int
    /// Size once installed.
    public let installedBytes: Int
    public let files: [RemoteFile]

    public func title(_ lang: String) -> String { title[lang] ?? title["en"] ?? id }
}

/// The content packs the app may download (shared/content/remote-packs.json, bundled with the app). Only
/// what it lists is ever downloaded, from `baseUrl`, and a pack is installed only when its manifest matches
/// `manifestSha256`; the manifest then fixes the SHA-256 of every file. So the host (GitHub releases now,
/// another server later) can serve only exactly the content this app version was built with.
public struct RemoteCatalog: Codable, Sendable {
    public let schemaVersion: Int
    public let baseUrl: String
    public var packs: [RemotePack]?

    public static let empty = RemoteCatalog(schemaVersion: 1, baseUrl: "https://invalid/", packs: [])

    public func forLanguage(_ lang: String) -> [RemotePack] { (packs ?? []).filter { $0.language == lang } }

    public static func parse(_ data: Data) throws -> RemoteCatalog {
        let c: RemoteCatalog
        do { c = try JSONDecoder().decode(RemoteCatalog.self, from: data) } catch { throw PackError.format("catalog: \(error)") }
        guard c.schemaVersion <= 1 else { throw PackError.newerSchema(c.schemaVersion) }
        guard c.baseUrl.hasPrefix("https://"), c.baseUrl.hasSuffix("/") else { throw PackError.format("catalog: base URL") }
        let safe = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-")
        for p in c.packs ?? [] {
            for f in p.files where f.asset.isEmpty || f.asset.unicodeScalars.contains(where: { !safe.contains($0) })
                || f.path.contains("..") || f.path.hasPrefix("/") {
                throw PackError.format("catalog: unsafe name")
            }
        }
        return c
    }
}

/// A downloaded pack, checked and ready for `PackVerifier.verify` and installation.
public struct FetchedPack: Sendable {
    public let manifest: PackManifest
    public let files: [String: Data]
}

public enum RemotePacks {
    /// Downloads every file of `pack` with `get`, checks each compressed file's size and SHA-256, inflates it
    /// with `inflate` (raw DEFLATE; given by the app, which has the system decompressor), checks its installed
    /// size, and checks the manifest against the catalog. Throws before anything is installed if any check
    /// fails. `progress` gets the compressed bytes of each file as it arrives.
    public static func fetch(_ catalog: RemoteCatalog, _ pack: RemotePack,
                             get: (URL) async throws -> Data,
                             inflate: (Data, Int) throws -> Data,
                             progress: (Int) -> Void = { _ in }) async throws -> FetchedPack {
        var files: [String: Data] = [:]
        for f in pack.files {
            guard let url = URL(string: catalog.baseUrl + f.asset) else { throw PackError.format("asset URL") }
            let z = try await get(url)
            guard z.count == f.bytes, Sha256.hex(z) == f.sha256.lowercased() else { throw PackError.checksum(f.asset) }
            let raw = try inflate(z, f.size)
            guard raw.count == f.size else { throw PackError.format("inflated size") }
            files[f.path] = raw
            progress(z.count)
        }
        guard let manifestData = files["manifest.json"] else { throw PackError.format("no manifest") }
        guard Sha256.hex(manifestData) == pack.manifestSha256.lowercased() else { throw PackError.checksum("manifest.json") }
        let m = try PackVerifier.parseManifest(manifestData)
        guard m.id == pack.id, m.version == pack.version, m.kind == "sources" else { throw PackError.format("manifest does not match catalog") }
        return FetchedPack(manifest: m, files: files)
    }
}
