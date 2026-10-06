import Foundation
import NMCore
import NMData

/// Content for languages without bundled content (hadith, Quran translation, tafsir, library), downloaded
/// only when the reader taps Download. Only packs listed in the bundled catalog (remote-packs.json) are
/// fetched, and each is checked against it before installation (see RemoteCatalog).
@MainActor @Observable
final class ContentDownloads {
    enum State: Equatable {
        case running(done: Int, total: Int)
        case done
        case failed
    }

    let catalog: RemoteCatalog = Bundle.main.url(forResource: "remote-packs", withExtension: "json")
        .flatMap { try? Data(contentsOf: $0) }.flatMap { try? RemoteCatalog.parse($0) } ?? .empty
    /// Per language, while or after downloading in this session.
    private(set) var state: [String: State] = [:]
    private var tasks: [String: Task<Void, Never>] = [:]

    /// Packs for `lang` not installed at the catalog's version.
    func missing(_ lang: String, packs: PackManager?) -> [RemotePack] {
        let installed = Dictionary(((try? packs?.installed()) ?? []).map { ($0.id, $0.version) }, uniquingKeysWith: { a, _ in a })
        return catalog.forLanguage(lang).filter { installed[$0.id] != $0.version }
    }

    func download(_ lang: String, packs: PackManager?, semantic: SemanticIndexStore?) {
        guard let packs, tasks[lang] == nil || state[lang] == .failed || state[lang] == .done else { return }
        let todo = missing(lang, packs: packs)
        let total = todo.reduce(0) { $0 + $1.bytes }
        let catalog = catalog
        state[lang] = .running(done: 0, total: total)
        tasks[lang] = Task {
            var done = 0
            do {
                for p in todo {
                    let fetched = try await RemotePacks.fetch(catalog, p, get: Self.get, inflate: Self.inflate) { n in
                        done += n
                        let now = done
                        Task { @MainActor in self.state[lang] = .running(done: now, total: total) }
                    }
                    try await Task.detached(priority: .utility) {
                        try packs.install(fetched.manifest, builtin: false) { fetched.files[$0] }
                    }.value
                }
                await Task.detached(priority: .utility) { try? semantic?.ensure() }.value
                state[lang] = .done
            } catch {
                state[lang] = .failed
            }
        }
    }

    private nonisolated static func get(_ url: URL) async throws -> Data {
        var request = URLRequest(url: url, timeoutInterval: 60)
        request.setValue("NearMosque", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        return data
    }

    /// Raw DEFLATE (the system's zlib decompressor), refusing anything that inflates to another size.
    private nonisolated static func inflate(_ data: Data, _ size: Int) throws -> Data {
        let raw = try (data as NSData).decompressed(using: .zlib) as Data
        guard raw.count == size else { throw PackError.format("inflated size") }
        return raw
    }
}
