import NMCore
import NMData
import SwiftUI

/// The library on its own: saved items, every installed collection to browse and search, downloaded books.
struct LibraryView: View {
    @Environment(AppModel.self) private var app
    @Environment(Localization.self) private var l10n
    @Environment(\.dismiss) private var dismiss
    @State private var tab = 0

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("", selection: $tab) {
                    Text(l10n.t("library_saved")).tag(0)
                    Text(l10n.t("library_browse")).tag(1)
                    Text(l10n.t("library_downloads")).tag(2)
                }
                .pickerStyle(.segmented)
                .padding(16)
                switch tab {
                case 0: SavedList()
                case 1: CollectionsList()
                default: DownloadsList()
                }
                Spacer(minLength: 0)
            }
            .foregroundStyle(Theme.ink)
            .background(Color(hex: 0x0B1220).ignoresSafeArea())
            .navigationTitle(l10n.t("library_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(l10n.t("close")) { dismiss() } } }
        }
    }
}

private struct EmptyNote: View {
    let text: String
    var body: some View { Text(text).font(.body).foregroundStyle(Theme.ink.opacity(0.8)).padding(32).frame(maxWidth: .infinity, alignment: .leading) }
}

private struct SavedList: View {
    @Environment(AppModel.self) private var app
    @Environment(Localization.self) private var l10n
    var body: some View {
        let items = (try? app.ask?.resolve(app.bookmarks.sorted())) ?? []
        if items.isEmpty { EmptyNote(text: l10n.t("library_saved_empty")) } else {
            ScrollView { LazyVStack(spacing: 10) { ForEach(items) { LibraryCard(item: $0, question: "") } }.padding(16) }
        }
    }
}

private struct CollectionsList: View {
    @Environment(AppModel.self) private var app
    @Environment(Localization.self) private var l10n
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

private struct CollectionView: View {
    @Environment(AppModel.self) private var app
    @Environment(Localization.self) private var l10n
    let pack: AskRepository.LibraryPack
    @State private var query = ""
    @State private var pages = 1
    @State private var items: [ResolvedCitation] = []
    static let page = 40

    var body: some View {
        let title = pack.title[l10n.language] ?? pack.title["en"] ?? pack.id
        ScrollView {
            LazyVStack(spacing: 10) {
                ForEach(items) { LibraryCard(item: $0, question: query) }
                if query.isEmpty, items.count == pages * Self.page, items.count < pack.count {
                    Button(l10n.t("library_load_more")) { pages += 1 }.foregroundStyle(Color(hex: 0x8CC0DE)).padding()
                }
            }
            .padding(16)
        }
        .foregroundStyle(Theme.ink)
        .background(Color(hex: 0x0B1220).ignoresSafeArea())
        .navigationTitle(title)
        .searchable(text: $query, prompt: l10n.t("library_search_in", title))
        .task(id: "\(query)|\(pages)") {
            if query.trimmingCharacters(in: .whitespaces).isEmpty {
                items = (try? app.ask?.browse(pack.id, offset: 0, limit: pages * Self.page)) ?? []
            } else {
                try? await Task.sleep(nanoseconds: 250_000_000)
                guard !Task.isCancelled else { return }
                items = (try? app.ask?.searchIn(pack.id, query)) ?? []
            }
        }
    }
}

private struct DownloadsList: View {
    @Environment(AppModel.self) private var app
    @Environment(Localization.self) private var l10n
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
