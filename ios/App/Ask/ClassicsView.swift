import NMCore
import SwiftUI

/// The classical books (shared/content/books-catalog.json): the author's text of each book, one PDF per printed
/// volume, downloaded only when the person taps it and read in the app's own reader. Not used by Ask.
enum Classics {
    static let catalog: BookCatalog = Bundle.main.url(forResource: "books-catalog", withExtension: "json")
        .flatMap { try? Data(contentsOf: $0) }.flatMap { try? BookCatalog.parse($0) } ?? .empty

    static func key(_ v: BookVolume) -> String { "book_" + v.file.replacingOccurrences(of: ".pdf", with: "") }

    static func isDownloaded(_ v: BookVolume) -> Bool {
        guard let u = catalog.url(v) else { return false }
        return LibraryFiles.isDownloaded(key(v), u)
    }

    /// The book and volume a downloaded file belongs to (for the downloads list).
    static func volume(forFile name: String) -> (book: ClassicBook, volume: BookVolume)? {
        for b in catalog.books { if let v = b.volumes.first(where: { key($0) + ".pdf" == name }) { return (b, v) } }
        return nil
    }

    static func size(_ bytes: Int) -> String { ByteCountFormatter.string(fromByteCount: Int64(bytes), countStyle: .file) }
}

/// The card at the top of the library that opens the classical books.
struct ClassicsCard: View {
    @Environment(\.l10n) private var l10n
    var body: some View {
        if !Classics.catalog.books.isEmpty {
            NavigationLink { ClassicsView() } label: {
                HStack(spacing: 12) {
                    Image(systemName: "books.vertical.fill").font(.title2).foregroundStyle(Theme.gold).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(l10n.t("classics_title")).font(.headline)
                        Text(l10n.t("classics_count", Classics.catalog.books.count)).font(.caption).foregroundStyle(Theme.ink.opacity(0.7))
                    }
                    Spacer()
                    Image(systemName: "chevron.forward").foregroundStyle(Theme.ink.opacity(0.5)).accessibilityHidden(true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .glassCard(padding: 16, cornerRadius: 18, tint: Theme.gold.opacity(0.10))
            }
            .buttonStyle(.plain)
        }
    }
}

struct ClassicsView: View {
    @Environment(\.l10n) private var l10n
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                Text(l10n.t("classics_note")).font(.footnote).foregroundStyle(Theme.ink.opacity(0.75))
                ForEach(Classics.catalog.byGroup, id: \.group) { g in
                    Text(l10n.t("classics_group_" + g.group)).font(.title3.weight(.semibold)).foregroundStyle(Theme.accent).padding(.top, 10)
                    ForEach(g.books) { b in
                        NavigationLink { BookView(book: b) } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(b.title).font(.headline).multilineTextAlignment(.leading)
                                Text(b.author).font(.subheadline).foregroundStyle(Theme.ink.opacity(0.8))
                                Text((b.volumes.count > 1 ? l10n.t("classics_volumes", b.volumes.count) + " · " : "") + Classics.size(b.bytes))
                                    .font(.caption).foregroundStyle(Theme.ink.opacity(0.6))
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .glassCard(padding: 14, cornerRadius: 16, tint: Color.white.opacity(0.04))
                        }
                        .buttonStyle(.plain)
                    }
                }
                Text(Classics.catalog.source).font(.caption2).foregroundStyle(Theme.ink.opacity(0.5)).padding(.top, 12)
            }
            .padding(16)
            .environment(\.layoutDirection, .rightToLeft)
        }
        .foregroundStyle(Theme.ink)
        .background(Color(hex: 0x0B1220).ignoresSafeArea())
        .navigationTitle(l10n.t("classics_title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// One book: its volumes, each downloaded on first tap and then opened offline.
struct BookView: View {
    @Environment(\.l10n) private var l10n
    let book: ClassicBook
    @State private var reading: BookVolume?
    @State private var version = 0

    var body: some View {
        let _ = version
        List {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(book.title).font(.title3.weight(.semibold))
                    Text(book.author).font(.subheadline)
                    Text(l10n.t("classics_edition", book.edition)).font(.caption).foregroundStyle(Theme.ink.opacity(0.7))
                }
                .environment(\.layoutDirection, .rightToLeft)
                .listRowBackground(Color.white.opacity(0.04))
            }
            Section {
                ForEach(book.volumes, id: \.file) { v in
                    let saved = Classics.isDownloaded(v)
                    Button { reading = v } label: {
                        HStack {
                            Image(systemName: saved ? "book.fill" : "arrow.down.circle").foregroundStyle(saved ? Theme.gold : Color(hex: 0x8CC0DE))
                                .accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(book.volumes.count > 1 ? l10n.t("classics_volume", v.volume) : l10n.t("classics_read")).font(.body.weight(.medium))
                                Text(l10n.t("classics_pages", v.pages) + " · " + Classics.size(v.bytes)).font(.caption).foregroundStyle(Theme.ink.opacity(0.6))
                            }
                            Spacer()
                            Text(saved ? l10n.t("classics_open") : l10n.t("classics_download")).font(.caption.weight(.semibold))
                                .foregroundStyle(saved ? Theme.gold : Color(hex: 0x8CC0DE))
                        }
                        .frame(minHeight: 44)
                    }
                    .swipeActions {
                        if saved, let u = Classics.catalog.url(v), let f = try? LibraryFiles.local(Classics.key(v), u) {
                            Button(l10n.t("library_delete"), role: .destructive) { LibraryFiles.delete(f); version += 1 }
                        }
                    }
                    .listRowBackground(Color.white.opacity(0.04))
                }
            }
        }
        .scrollContentBackground(.hidden)
        .foregroundStyle(Theme.ink)
        .background(Color(hex: 0x0B1220).ignoresSafeArea())
        .navigationTitle(book.title)
        .navigationBarTitleDisplayMode(.inline)
        .fullScreenCover(item: $reading, onDismiss: { version += 1 }) { v in
            BookReaderView(book: book, volume: v)
        }
    }
}

extension BookVolume: @retroactive Identifiable { public var id: String { file } }

struct BookReaderView: View {
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    let book: ClassicBook
    let volume: BookVolume

    var body: some View {
        NavigationStack {
            Group {
                if let u = Classics.catalog.url(volume) {
                    PDFReader(url: u, itemKey: Classics.key(volume), question: "", sha256: volume.sha256)
                }
            }
            .navigationTitle(book.volumes.count > 1 ? book.title + " · " + l10n.t("classics_volume", volume.volume) : book.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { Label(l10n.t("close"), systemImage: "xmark") }.keyboardShortcut(.cancelAction) } }
        }
        .environment(\.colorScheme, .dark)
    }
}
