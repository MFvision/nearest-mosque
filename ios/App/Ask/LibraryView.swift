import NMCore
import NMData
import SwiftUI

/// The library on its own: the books shelf, every installed collection to browse and search, saved items and
/// downloaded books.
struct LibraryView: View {
    enum Shelf: String { case books, browse, saved, downloads }
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    @State private var tab = Shelf.books
    @State private var path: [String] = []
    #if DEBUG
    @State private var demoBook: ResolvedCitation?
    #endif

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                Picker("", selection: $tab) {
                    Text(l10n.t("library_books")).tag(Shelf.books)
                    Text(l10n.t("library_browse")).tag(Shelf.browse)
                    Text(l10n.t("library_saved")).tag(Shelf.saved)
                    Text(l10n.t("library_downloads")).tag(Shelf.downloads)
                }
                .pickerStyle(.segmented)
                .padding(16)
                switch tab {
                case .books: BooksShelf()
                case .browse: CollectionsList()
                case .saved: SavedList()
                case .downloads: DownloadsList()
                }
                Spacer(minLength: 0)
            }
            .foregroundStyle(Theme.ink)
            .background(Color(hex: 0x0B1220).ignoresSafeArea())
            .navigationTitle(l10n.t("library_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(l10n.t("close")) { dismiss() }.keyboardShortcut(.cancelAction) } }
            .navigationDestination(for: String.self) { id in
                if let p = ((try? app.ask?.libraryPacks()) ?? []).first(where: { $0.id == id }) { CollectionView(pack: p) }
            }
        }
        #if DEBUG
        // CI screenshots: `-demoLibraryTab books|browse|saved|downloads`, `-demoLibraryPack <pack id>` opens a collection,
        // `-demoOpenBook <item id>` opens that item in the reader (downloading a book first).
        .task(id: app.ready) {
            guard app.ready else { return }
            let d = UserDefaults.standard
            if let t = d.string(forKey: "demoLibraryTab").flatMap(Shelf.init(rawValue:)) { tab = t }
            for _ in 0..<60 where ((try? app.ask?.libraryPacks()) ?? []).isEmpty { try? await Task.sleep(for: .seconds(2)) }
            if let pack = d.string(forKey: "demoLibraryPack") { tab = .browse; path = [pack] }
            if let id = d.string(forKey: "demoOpenBook") { demoBook = (try? app.ask?.resolve([id]))?.first }
        }
        .fullScreenCover(item: $demoBook) { b in
            if let mode = LibraryMode(b.chunk) { LibraryReaderView(chunk: b.chunk, mode: mode, question: "") }
        }
        #endif
    }
}

private struct EmptyNote: View {
    let text: String
    var body: some View { Text(text).font(.body).foregroundStyle(Theme.ink.opacity(0.8)).padding(32).frame(maxWidth: .infinity, alignment: .leading) }
}

private struct SavedList: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    var body: some View {
        let items = (try? app.ask?.resolve(app.bookmarks.sorted())) ?? []
        if items.isEmpty { EmptyNote(text: l10n.t("library_saved_empty")) } else {
            ScrollView { LazyVStack(spacing: 10) { ForEach(items) { LibraryCard(item: $0, question: "") } }.padding(16) }
        }
    }
}

private struct CollectionsList: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    var body: some View {
        let packs = (try? app.ask?.libraryPacks()) ?? []
        if packs.isEmpty { EmptyNote(text: l10n.t("library_installing")) } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(packs) { p in
                        NavigationLink { CollectionView(pack: p) } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(p.title[l10n.language] ?? p.title["en"] ?? p.id).font(.subheadline.weight(.semibold))
                                Text(l10n.t("library_items_count", p.count)).font(.caption).foregroundStyle(Theme.ink.opacity(0.7))
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .glassCard(padding: 14, cornerRadius: 18, tint: Color.white.opacity(0.04))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
        }
    }
}

/// The kinds of item a collection can hold, in the order the filters show them.
private let libraryKinds = ["books", "audios", "videos", "articles", "fatwa", "hadith", "tafsir", "quran"]

/// One collection: filters by kind (books first), search, and its items a page at a time.
private struct CollectionView: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    let pack: AskRepository.LibraryPack
    var fixedKind: String? = nil
    @State private var kind: String?
    @State private var counts: [String: Int] = [:]
    @State private var query = ""
    @State private var pages = 1
    @State private var items: [ResolvedCitation] = []
    static let page = 40

    var body: some View {
        let title = pack.title[l10n.language] ?? pack.title["en"] ?? pack.id
        let kinds = libraryKinds.filter { (counts[$0] ?? 0) > 0 }
        let total = kind.flatMap { counts[$0] } ?? pack.count
        ScrollView {
            LazyVStack(spacing: 10) {
                if fixedKind == nil, kinds.count > 1 {
                    KindFilter(kinds: kinds, counts: counts, total: pack.count, selection: $kind)
                }
                ForEach(items) { LibraryCard(item: $0, question: query) }
                if query.isEmpty, items.count == pages * Self.page, items.count < total {
                    Button(l10n.t("library_load_more")) { pages += 1 }.foregroundStyle(Color(hex: 0x8CC0DE)).frame(minHeight: 44).padding()
                }
            }
            .padding(16)
        }
        .foregroundStyle(Theme.ink)
        .background(Color(hex: 0x0B1220).ignoresSafeArea())
        // On the books shelf the screen keeps the library's own title.
        .navigationTitle(fixedKind == nil ? title : l10n.t("library_title"))
        .searchable(text: $query, prompt: l10n.t("library_search_in", title))
        .task(id: pack.id) {
            counts = (try? app.ask?.typeCounts(pack.id)) ?? [:]
            if kind == nil { kind = fixedKind ?? ((counts["books"] ?? 0) > 0 ? "books" : nil) }
        }
        .task(id: "\(query)|\(pages)|\(kind ?? "")") {
            if query.trimmingCharacters(in: .whitespaces).isEmpty {
                items = (try? app.ask?.browse(pack.id, type: kind, offset: 0, limit: pages * Self.page)) ?? []
            } else {
                try? await Task.sleep(nanoseconds: 250_000_000)
                guard !Task.isCancelled else { return }
                let found = (try? app.ask?.searchIn(pack.id, query)) ?? []
                items = kind.map { k in found.filter { $0.chunk.section?.type == k } } ?? found
            }
        }
    }
}

/// Chips for the kinds in a collection: All, Books, Audio, Video, Articles...
private struct KindFilter: View {
    @Environment(\.l10n) private var l10n
    let kinds: [String]
    let counts: [String: Int]
    let total: Int
    @Binding var selection: String?

    private func label(_ k: String) -> String {
        switch k {
        case "books": return l10n.t("library_books")
        case "audios": return l10n.t("library_type_audios")
        case "videos": return l10n.t("library_type_videos")
        case "articles": return l10n.t("library_type_articles")
        case "fatwa": return l10n.t("library_type_fatwa")
        case "hadith": return l10n.t("library_type_hadith")
        case "tafsir": return l10n.t("library_type_tafsir")
        default: return l10n.t("library_type_quran")
        }
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip(l10n.t("library_filter_all"), count: total, on: selection == nil) { selection = nil }
                ForEach(kinds, id: \.self) { k in chip(label(k), count: counts[k] ?? 0, on: selection == k) { selection = k } }
            }
        }
    }

    private func chip(_ title: String, count: Int, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(count.formatted(.number.locale(l10n.locale))).font(.caption).opacity(0.75)
            }
            .padding(.horizontal, 16).frame(minHeight: 40)
            .foregroundStyle(on ? Color(hex: 0x081B29) : Theme.ink)
            .background(on ? Theme.gold : Color.white.opacity(0.08), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// Every book in the libraries installed for the reader's languages (the reader's own language first), each
/// with Read and Download.
private struct BooksShelf: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @State private var packId: String?

    var body: some View {
        let packs = ((try? app.ask?.libraryPacks()) ?? []).filter { $0.id.hasPrefix(ChunkScope.libraryPrefix) }
            .sorted { ($0.language == l10n.language ? 0 : 1, $0.language) < ($1.language == l10n.language ? 0 : 1, $1.language) }
        if packs.isEmpty {
            EmptyNote(text: l10n.t("library_installing"))
        } else {
            let current = packs.first { $0.id == packId } ?? packs[0]
            VStack(spacing: 0) {
                if packs.count > 1 {
                    Picker(l10n.t("language"), selection: Binding(get: { current.id }, set: { packId = $0 })) {
                        ForEach(packs) { p in Text(Localization.nativeNames[p.language] ?? p.language).tag(p.id) }
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, 16)
                }
                CollectionView(pack: current, fixedKind: "books").id(current.id)
            }
        }
    }
}

private struct DownloadsList: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @State private var version = 0

    var body: some View {
        let files = LibraryFiles.downloaded()
        let _ = version
        let resolved = Dictionary(uniqueKeysWithValues: ((try? app.ask?.resolve(files.map(\.id))) ?? []).map { ($0.id, $0) })
        if files.isEmpty { EmptyNote(text: l10n.t("library_downloads_empty")) } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text(ByteCountFormatter.string(fromByteCount: Int64(files.reduce(0) { $0 + $1.bytes }), countStyle: .file)).foregroundStyle(Theme.ink.opacity(0.8))
                        Spacer()
                        Button(l10n.t("library_delete_all")) { files.forEach { LibraryFiles.delete($0.url) }; version += 1 }.foregroundStyle(Color(hex: 0xE8A0A0))
                    }
                    ForEach(files, id: \.url) { f in
                        if let r = resolved[f.id] { LibraryCard(item: r, question: "") } else { Text(f.url.lastPathComponent) }
                        HStack {
                            Text(ByteCountFormatter.string(fromByteCount: Int64(f.bytes), countStyle: .file)).font(.caption).foregroundStyle(Theme.ink.opacity(0.7))
                            Spacer()
                            Button(l10n.t("library_delete")) { LibraryFiles.delete(f.url); version += 1 }.font(.caption).foregroundStyle(Color(hex: 0xE8A0A0))
                        }
                    }
                }
                .padding(16)
            }
        }
    }
}
