import AVKit
import NMCore
import NMData
import PDFKit
import SafariServices
import SwiftUI

/// How a library item opens inside the app.
enum LibraryMode: Equatable {
    case pdf(URL)
    case media(URL, video: Bool)
    case web(URL)

    init?(_ c: SourceChunk) {
        let file = c.section?.attachment.flatMap(URL.init(string:)).flatMap { LibraryFiles.isAllowed($0) ? $0 : nil }
        switch (c.section?.attachmentType?.uppercased(), file) {
        case ("PDF", let f?): self = .pdf(f)
        case ("MP4", let f?), ("M4V", let f?): self = .media(f, video: true)
        case ("MP3", let f?), ("M4A", let f?), ("WAV", let f?): self = .media(f, video: false)
        default:
            guard let u = c.url.flatMap(URL.init(string:)), LibraryFiles.isAllowed(u) else { return nil }
            self = .web(u)
        }
    }
}

/// Books opened from the library: downloaded once (only when the user taps "Read in the app") into
/// Application Support, excluded from backup, so they open again offline. Only HTTPS IslamHouse files.
enum LibraryFiles {
    static let hosts: Set<String> = ["islamhouse.com", "www.islamhouse.com", "d1.islamhouse.com", "d2.islamhouse.com"]

    static func isAllowed(_ u: URL) -> Bool {
        guard u.scheme == "https", let h = u.host?.lowercased() else { return false }
        return hosts.contains(h) || h.hasSuffix(".islamhouse.com")
    }

    static func fileName(_ itemKey: String, _ url: URL) -> String {
        let ext = url.pathExtension.lowercased().filter { $0.isLetter || $0.isNumber }.prefix(5)
        let safe = itemKey.map { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" ? $0 : "_" }
        return String(safe) + "." + (ext.isEmpty ? "bin" : String(ext))
    }

    static func local(_ itemKey: String, _ url: URL) throws -> URL {
        var dir = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("library", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? dir.setResourceValues(values)
        return dir.appendingPathComponent(fileName(itemKey, url))
    }

    /// Returns the local copy, downloading it first if needed. `progress` is called on the main actor.
    @MainActor
    static func fetch(_ url: URL, itemKey: String, progress: @escaping (Double) -> Void) async throws -> URL {
        guard isAllowed(url) else { throw URLError(.unsupportedURL) }
        let target = try local(itemKey, url)
        if let size = try? target.resourceValues(forKeys: [.fileSizeKey]).fileSize, size > 0 { return target }
        var observation: NSKeyValueObservation?
        defer { observation?.invalidate() }
        let tmp: URL = try await withCheckedThrowingContinuation { cont in
            let task = URLSession.shared.downloadTask(with: url) { file, response, error in
                if let error { return cont.resume(throwing: error) }
                guard let file, let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                      let final = http.url, isAllowed(final) else { return cont.resume(throwing: URLError(.badServerResponse)) }
                // The temporary file is deleted when this handler returns: move it first.
                let keep = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
                do { try FileManager.default.moveItem(at: file, to: keep); cont.resume(returning: keep) } catch { cont.resume(throwing: error) }
            }
            observation = task.progress.observe(\.fractionCompleted) { p, _ in
                let f = p.fractionCompleted
                Task { @MainActor in progress(f) }
            }
            task.resume()
        }
        try? FileManager.default.removeItem(at: target)
        try FileManager.default.moveItem(at: tmp, to: target)
        return target
    }
}

private func libraryTypeKey(_ type: String?) -> String? {
    switch type {
    case "books": return "library_type_books"
    case "articles": return "library_type_articles"
    case "fatwa": return "library_type_fatwa"
    case "videos": return "library_type_videos"
    case "audios": return "library_type_audios"
    default: return nil
    }
}

/// An IslamHouse library item: type, title, authors, excerpt, and the item itself opened inside the app.
struct LibraryCard: View {
    @Environment(Localization.self) private var l10n
    @Environment(\.openURL) private var openURL
    let item: ResolvedCitation
    let question: String
    @State private var reading = false

    var body: some View {
        let c = item.chunk
        let mode = LibraryMode(c)
        let rtl = c.original.lang == "ar" || c.original.lang == "ur"
        let excerpt = String(c.original.text.dropFirst(c.original.text.hasPrefix(c.anchor) ? c.anchor.count : 0).trimmingCharacters(in: .whitespacesAndNewlines).prefix(260))
        VStack(alignment: .leading, spacing: 6) {
            if let k = libraryTypeKey(c.section?.type) {
                Text(l10n.t(k)).font(.caption2.weight(.semibold)).foregroundStyle(Theme.gold)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(c.anchor).font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity, alignment: .leading)
                if let authors = c.section?.authors, !authors.isEmpty {
                    Text(l10n.t("library_by", authors.joined(separator: ", "))).font(.caption).foregroundStyle(.white.opacity(0.7))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if !excerpt.isEmpty {
                    Text(excerpt + (c.original.text.count > excerpt.count + c.anchor.count + 1 ? "…" : ""))
                        .font(.subheadline).foregroundStyle(.white.opacity(0.9)).lineLimit(5)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .multilineTextAlignment(.leading)
            .environment(\.layoutDirection, rtl ? .rightToLeft : .leftToRight)
            HStack(spacing: 16) {
                if let mode {
                    Button { reading = true } label: { Label(actionTitle(mode, c), systemImage: actionIcon(mode)) }
                        .foregroundStyle(Theme.gold)
                }
                if let u = c.url.flatMap(URL.init(string:)) {
                    Button { openURL(u) } label: { Label(l10n.t("library_web"), systemImage: "safari") }
                        .foregroundStyle(Color(hex: 0x8CC0DE))
                }
            }
            .font(.subheadline.weight(.medium))
            .padding(.top, 2)
        }
        .foregroundStyle(.white)
        .glassCard(padding: 14, cornerRadius: 20, tint: Color.white.opacity(0.04))
        .fullScreenCover(isPresented: $reading) {
            if let mode { LibraryReaderView(chunk: c, mode: mode, question: question) }
        }
    }

    private func actionTitle(_ mode: LibraryMode, _ c: SourceChunk) -> String {
        let label: String
        switch mode {
        case .media(_, let video): label = l10n.t(video ? "library_watch" : "library_listen")
        default: label = l10n.t("library_read")
        }
        if case .web = mode { return label }
        return c.section?.attachmentSize.map { l10n.t("library_file", label, $0) } ?? label
    }

    private func actionIcon(_ mode: LibraryMode) -> String {
        switch mode {
        case .pdf: return "book.pages"
        case .media(_, let video): return video ? "play.rectangle" : "headphones"
        case .web: return "doc.text"
        }
    }
}

/// Full-screen reader for an IslamHouse item: the book itself (PDF), the video or audio, or the article page.
struct LibraryReaderView: View {
    @Environment(Localization.self) private var l10n
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let chunk: SourceChunk
    let mode: LibraryMode
    let question: String

    var body: some View {
        NavigationStack {
            Group {
                switch mode {
                case .pdf(let url): PDFReader(url: url, itemKey: chunk.id, question: question)
                case .media(let url, _): MediaReader(url: url)
                case .web(let url): SafariView(url: url).ignoresSafeArea(edges: .bottom)
                }
            }
            .navigationTitle(chunk.anchor)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(l10n.t("close")) { dismiss() } }
                if let u = chunk.url.flatMap(URL.init(string:)) {
                    ToolbarItem(placement: .primaryAction) {
                        Button { openURL(u) } label: { Label(l10n.t("library_web"), systemImage: "safari") }
                    }
                }
            }
        }
    }
}

private struct PDFReader: View {
    @Environment(Localization.self) private var l10n
    let url: URL
    let itemKey: String
    let question: String
    @State private var file: URL?
    @State private var progress = 0.0
    @State private var failed = false
    @State private var attempt = 0
    @State private var page = (current: 1, total: 0)
    @State private var foundPage = false

    var body: some View {
        ZStack {
            Color(hex: 0x0B1220).ignoresSafeArea()
            if let file {
                PDFKitView(file: file, searchTerms: Self.searchTerms(question), page: $page, foundPage: $foundPage)
                    .ignoresSafeArea(edges: .bottom)
                VStack {
                    if foundPage {
                        Text(l10n.t("library_found_page")).font(.footnote.weight(.medium)).foregroundStyle(.white)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(.black.opacity(0.65), in: Capsule())
                            .padding(.top, 8)
                            .transition(.opacity)
                    }
                    Spacer()
                    if page.total > 0 {
                        Text(l10n.t("library_page", page.current, page.total)).font(.footnote.weight(.semibold)).foregroundStyle(.white)
                            .padding(.horizontal, 14).padding(.vertical, 6)
                            .background(.black.opacity(0.6), in: Capsule())
                            .padding(.bottom, 16)
                    }
                }
            } else if failed {
                VStack(spacing: 12) {
                    Text(l10n.t("library_download_failed")).multilineTextAlignment(.center).foregroundStyle(.white)
                    Button(l10n.t("library_retry")) { attempt += 1 }.buttonStyle(.borderedProminent).tint(Theme.gold)
                }
                .padding(24)
            } else {
                VStack(spacing: 12) {
                    Text(l10n.t("library_downloading")).foregroundStyle(.white)
                    if progress > 0 { ProgressView(value: progress).frame(width: 220).tint(Theme.gold) } else { ProgressView().tint(.white) }
                }
            }
        }
        .task(id: attempt) {
            failed = false
            do { file = try await LibraryFiles.fetch(url, itemKey: itemKey) { progress = $0 } } catch { failed = true }
        }
    }

    /// The question's longer words, longest first: the reader opens at the first page that contains one.
    static func searchTerms(_ q: String) -> [String] {
        let words = q.components(separatedBy: CharacterSet.letters.inverted).filter { $0.count >= 4 }
        var seen = Set<String>()
        return words.sorted { $0.count > $1.count }.filter { seen.insert($0.lowercased()).inserted }.prefix(4).map { $0 }
    }
}

private struct PDFKitView: UIViewRepresentable {
    let file: URL
    let searchTerms: [String]
    @Binding var page: (current: Int, total: Int)
    @Binding var foundPage: Bool

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> PDFView {
        let v = PDFView()
        v.autoScales = true
        v.displayMode = .singlePageContinuous
        v.displayDirection = .vertical
        v.backgroundColor = UIColor(red: 0.043, green: 0.071, blue: 0.125, alpha: 1)
        if let doc = PDFDocument(url: file) {
            v.document = doc
            context.coordinator.observe(v)
            context.coordinator.search(doc, in: v)
        }
        return v
    }

    func updateUIView(_ uiView: PDFView, context: Context) {}

    final class Coordinator: NSObject, PDFDocumentDelegate {
        let parent: PDFKitView
        private var token: NSObjectProtocol?
        private weak var view: PDFView?
        private var jumped = false
        init(_ parent: PDFKitView) { self.parent = parent }
        deinit { if let token { NotificationCenter.default.removeObserver(token) } }

        func observe(_ v: PDFView) {
            view = v
            update(v)
            token = NotificationCenter.default.addObserver(forName: .PDFViewPageChanged, object: v, queue: .main) { [weak self, weak v] _ in
                if let v { self?.update(v) }
            }
        }

        private func update(_ v: PDFView) {
            guard let doc = v.document else { return }
            let current = v.currentPage.map { doc.index(for: $0) + 1 } ?? 1
            parent.page = (current, doc.pageCount)
        }

        /// PDFKit's asynchronous search: jump to and highlight the first page that mentions the question.
        func search(_ doc: PDFDocument, in v: PDFView) {
            guard !parent.searchTerms.isEmpty else { return }
            doc.delegate = self
            doc.beginFindStrings(parent.searchTerms, withOptions: [.caseInsensitive, .diacriticInsensitive])
        }

        func didMatchString(_ instance: PDFSelection) {
            guard !jumped, let v = view else { return }
            jumped = true
            v.document?.cancelFindString()
            instance.color = UIColor.systemYellow.withAlphaComponent(0.45)
            v.setCurrentSelection(instance, animate: false)
            v.go(to: instance)
            withAnimation { parent.foundPage = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 4) { [weak self] in withAnimation { self?.parent.foundPage = false } }
        }
    }
}

private struct MediaReader: View {
    let url: URL
    @State private var player: AVPlayer?
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let player { VideoPlayer(player: player).ignoresSafeArea(edges: .bottom) }
        }
        .onAppear { if player == nil { player = AVPlayer(url: url); player?.play() } }
        .onDisappear { player?.pause() }
    }
}

private struct SafariView: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}
