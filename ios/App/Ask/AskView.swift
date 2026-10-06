import NMCore
import NMData
import SwiftUI

struct Turn: Identifiable {
    let id = UUID()
    let question: String
    var answer: Answer?
    var citations: [ResolvedCitation] = []
    var related: [ResolvedCitation] = []
    var library: [ResolvedCitation] = []
    /// When an on-device answer cites library records, [n] for library item i is libraryCiteOffset + i + 1.
    var libraryCiteOffset: Int?
    var libraryCited = 0
}

/// Conversation is kept in memory only: not persisted and never sent anywhere.
@MainActor
@Observable
final class AskModel {
    var turns: [Turn] = []
    var busy = false
    private var task: Task<Void, Never>?

    func ask(_ app: AppModel, _ text: String) {
        let q = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty, !busy, let repo = app.ask else { return }
        let context = turns.suffix(2).map(\.question)
        let lang = app.l10n.language
        run(app, q, rephrase: true) { also in try repo.ask(q, context: context, lang: lang, also: also) }
    }

    func ask(_ app: AppModel, common q: CommonQuestion, displayed: String) {
        guard let repo = app.ask else { return }
        let lang = app.l10n.language
        run(app, displayed) { _ in try repo.answer(for: q, displayed: displayed, lang: lang) }
    }

    private func run(_ app: AppModel, _ question: String, rephrase: Bool = false, _ block: @escaping @Sendable ([AskRepository.AlsoSearch]) throws -> Answer) {
        guard let repo = app.ask else { return }
        let turn = Turn(question: question)
        turns.append(turn)
        busy = true
        let lang = app.l10n.language
        task = Task {
            defer { busy = false }
            // With Apple Intelligence: Arabic and English search phrasings of the question, written on the device.
            let also = rephrase ? await QueryRewriter.searchQueries(for: question, language: lang) : []
            guard var answer = try? await Task.detached(operation: { try block(also) }).value else { return }
            let cites = (try? repo.resolve(answer.citations)) ?? []
            let related = (try? repo.resolve(answer.related)) ?? []
            let library = (try? repo.resolve(answer.library)) ?? []
            // On-device answer from the verses found and from fatwas, hadiths and translations in the library.
            let quran = answer.kind == .passages ? cites.map(LocalAnswerer.evidence(quran:)) : []
            let fromLibrary = Array(library.compactMap { l in LocalAnswerer.evidence(library: l).map { (l, $0) } }.prefix(3))
            var offset: Int?
            if answer.kind != .common, !(quran.isEmpty && fromLibrary.isEmpty),
               let written = await LocalAnswerer.write(question: question, evidence: quran + fromLibrary.map(\.1), language: lang) {
                answer.generated = written
                offset = quran.count
            }
            // Cited library records first, so the numbers in the answer match the cards' order.
            let citedIds = Set(fromLibrary.map(\.0.id))
            let orderedLibrary = offset == nil ? library : fromLibrary.map(\.0) + library.filter { !citedIds.contains($0.id) }
            guard !Task.isCancelled, let i = turns.firstIndex(where: { $0.id == turn.id }) else { return }
            turns[i].answer = answer
            turns[i].citations = cites
            turns[i].related = related
            turns[i].library = orderedLibrary
            turns[i].libraryCiteOffset = offset
            turns[i].libraryCited = offset == nil ? 0 : fromLibrary.count
        }
    }

    func stop() {
        task?.cancel()
        turns.removeAll { $0.answer == nil }
        busy = false
    }

    func reset() { task?.cancel(); turns = []; busy = false }
}

struct AskView: View {
    @Environment(AppModel.self) private var app
    @Environment(Localization.self) private var l10n
    @Binding var showSettings: Bool
    @State private var vm = AskModel()
    @State private var input = ""
    @State private var reading: ResolvedCitation?
    @State private var showLibrary = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 0) {
        header.padding(.horizontal, 16)
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    if vm.turns.isEmpty {
                        // Leaves the mosque skyline on the horizon visible above the intro.
                        Color.clear.frame(height: 130)
                        intro
                    }
                    ForEach(vm.turns) { t in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Spacer(minLength: 40)
                                Text(t.question).padding(.horizontal, 16).padding(.vertical, 12)
                                    .glass(RoundedRectangle(cornerRadius: 20, style: .continuous), tint: Theme.navy.opacity(0.55))
                            }
                            if let a = t.answer {
                                AnswerCard(turn: t, answer: a, onRead: { reading = $0 })
                                    .transition(.move(edge: .bottom).combined(with: .opacity))
                            } else {
                                HStack(spacing: 10) {
                                    ProgressView().tint(Theme.ink)
                                    Text(l10n.t("answer_searching"))
                                    Spacer()
                                    Button(l10n.t("stop")) { vm.stop() }.glassButton()
                                }
                                .glassCard(padding: 12)
                            }
                        }
                        .id(t.id)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
                .animation(Theme.spring, value: vm.turns.map(\.answer?.question))
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: vm.turns.last?.answer?.question) { _, _ in
                if let id = vm.turns.last?.id { withAnimation { proxy.scrollTo(id, anchor: .top) } }
            }
        }
        }
        .foregroundStyle(Theme.ink)
        .safeAreaInset(edge: .bottom) { inputBar }
        .skyBackground(horizon: 0.3, skyline: true)
        .toolbar(.hidden, for: .navigationBar)
        .sheet(item: $reading) { ReaderView(citation: $0) }
        .fullScreenCover(isPresented: $showLibrary) { LibraryView() }
        #if DEBUG
        // The library packs follow the interface language (installed in the background when it changes).
        .task(id: l10n.language) { if app.ready { app.ensureLibraries(for: l10n.language) } }
        .task(id: app.ready) {
            // CI screenshots: `-demoAsk YES` asks the first common question.
            guard app.ready, vm.turns.isEmpty, UserDefaults.standard.bool(forKey: "demoAsk"),
                  let q = try? app.ask?.commonQuestions().first else { return }
            vm.ask(app, common: q, displayed: q.question[l10n.language] ?? q.question["en"] ?? q.id)
        }
        #endif
    }

    private var header: some View {
        HStack {
            if !vm.turns.isEmpty {
                GlassIconButton(systemImage: "square.and.pencil", label: l10n.t("new_conversation")) { withAnimation(Theme.spring) { vm.reset() } }
            } else {
                Color.clear.frame(width: 44, height: 44)
            }
            Spacer()
            Text(l10n.t("ask_title")).font(.headline).lineLimit(1).minimumScaleFactor(0.8)
            Spacer()
            GlassIconButton(systemImage: "books.vertical", label: l10n.t("library_title")) { showLibrary = true }
            SettingsButton(show: $showSettings)
        }
        .padding(.top, 4)
        .padding(.bottom, 8)
    }

    private var intro: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(l10n.t("ask_title")).font(.largeTitle.bold()).accessibilityAddTraits(.isHeader)
            Text(l10n.t("ask_subtitle")).foregroundStyle(Theme.ink.opacity(0.85))
            if LocalAnswerer.availability(language: l10n.language) != .available {
                Label(l10n.t("ai_pack_not_installed"), systemImage: "lock.shield").font(.footnote).foregroundStyle(Theme.ink.opacity(0.75))
            }
            Text(l10n.t("common_questions")).font(.headline).padding(.top, 8).accessibilityAddTraits(.isHeader)
            if !app.ready { ProgressView().tint(Theme.ink) }
            let qs = (try? app.ask?.commonQuestions()) ?? []
            GlassGroup(spacing: 8) {
                FlowLayout(spacing: 8) {
                    ForEach(qs) { q in
                        let text = q.question[l10n.language] ?? q.question["en"] ?? q.id
                        Button { vm.ask(app, common: q, displayed: text) } label: {
                            Text(text).font(.subheadline).multilineTextAlignment(.leading)
                                .padding(.horizontal, 14).padding(.vertical, 10)
                                .frame(minHeight: 44)
                                .contentShape(RoundedRectangle(cornerRadius: 18))
                        }
                        .buttonStyle(.plain)
                        .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                }
            }
        }
    }

    private var inputBar: some View {
        GlassGroup(spacing: 10) {
            HStack(spacing: 10) {
                TextField(l10n.t(vm.turns.isEmpty ? "ask_placeholder" : "ask_follow_up_placeholder"), text: $input, axis: .vertical)
                    .lineLimit(1...4)
                    .focused($focused)
                    .submitLabel(.send)
                    .onSubmit(send)
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .glass(RoundedRectangle(cornerRadius: 24, style: .continuous))
                Button(action: send) {
                    Image(systemName: "arrow.up").font(.headline.weight(.bold)).frame(width: 30, height: 30)
                }
                .prominentButton()
                .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty || vm.busy)
                .accessibilityLabel(l10n.t("ask_send"))
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
    }

    private func send() {
        vm.ask(app, input)
        input = ""
    }
}

struct AnswerCard: View {
    @Environment(Localization.self) private var l10n
    @Environment(\.colorScheme) private var scheme
    let turn: Turn
    let answer: Answer
    var onRead: (ResolvedCitation) -> Void
    @State private var more = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            switch answer.kind {
            case .common:
                let q = answer.commonQuestion!
                Text(q.isReviewed ? l10n.t("answer_common_reviewed", q.reviewedBy ?? "") : l10n.t("answer_common_unreviewed"))
                    .font(.footnote.weight(.semibold)).foregroundStyle(Theme.accent)
                Text(q.summary[l10n.language] ?? q.summary["en"] ?? "")
            case .passages:
                if let g = answer.generated {
                    Text(l10n.t("answer_generated_on_device")).font(.footnote.weight(.semibold)).foregroundStyle(Theme.accent)
                    Text(g)
                } else {
                    Text(l10n.t("answer_from_passages")).font(.headline)
                    Text(l10n.t("answer_from_passages_body")).font(.subheadline)
                }
            case .insufficient:
                if let g = answer.generated {
                    Text(l10n.t("answer_generated_on_device")).font(.footnote.weight(.semibold)).foregroundStyle(Theme.accent)
                    Text(g)
                } else if turn.library.isEmpty {
                    Text(l10n.t("answer_insufficient_title")).font(.headline)
                    Text(l10n.t("answer_insufficient_body")).font(.subheadline)
                } else {
                    Text(l10n.t("library_found_title")).font(.headline)
                    Text(l10n.t("library_found_body")).font(.subheadline)
                }
            }
            if !turn.citations.isEmpty {
                Text(l10n.t("sources")).font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                ForEach(Array(turn.citations.enumerated()), id: \.element.id) { i, c in SourceCard(index: i + 1, citation: c, onRead: onRead) }
            }
            if !turn.related.isEmpty {
                Button(l10n.t(more ? "show_less_detail" : "show_more_detail")) { withAnimation { more.toggle() } }
                if more {
                    Text(l10n.t("related_passages")).font(.subheadline.weight(.semibold))
                    ForEach(turn.related) { SourceCard(index: nil, citation: $0, onRead: onRead) }
                }
            }
            if !turn.library.isEmpty {
                Text(l10n.t("library_section")).font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                ForEach(Array(turn.library.enumerated()), id: \.element.id) { i, item in
                    let n = turn.libraryCiteOffset.flatMap { i < turn.libraryCited ? $0 + i + 1 : nil }
                    LibraryCard(item: item, question: turn.question, index: n)
                }
                Text(l10n.t("library_note") + " " + l10n.t("library_offline_note")).font(.caption).foregroundStyle(Theme.ink.opacity(0.7))
            }
            if !["ar", "en"].contains(l10n.language), answer.kind != .insufficient {
                Text(l10n.t("answer_language_note")).font(.caption).foregroundStyle(Theme.ink.opacity(0.7))
            }
            OtherSourcesRow(question: turn.question)
            Text(l10n.t("answer_not_fatwa")).font(.caption).foregroundStyle(Theme.ink.opacity(0.7))
        }
        .foregroundStyle(Theme.ink)
        .glassCard()
    }
}

/// Wraps chips onto multiple lines (mirrors automatically in RTL).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(ProposedViewSize(width: width, height: nil))
            if x + size.width > width, x > 0 { x = 0; y += rowH + spacing; rowH = 0 }
            x += size.width + spacing
            rowH = max(rowH, size.height)
        }
        return CGSize(width: width == .infinity ? x : width, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(ProposedViewSize(width: bounds.width, height: nil))
            if x + size.width > bounds.maxX, x > bounds.minX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowH = max(rowH, size.height)
        }
    }
}

/// Sites the app links to but does not copy: each opens its own search for the question when tapped.
struct OtherSourcesRow: View {
    @Environment(Localization.self) private var l10n
    @Environment(\.openURL) private var openURL
    let question: String
    private static let labels = ["islamqa": "site_islamqa", "dorar": "site_dorar", "binothaimeen": "site_binothaimeen", "alifta": "site_alifta"]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(l10n.t("answer_search_more")).font(.caption.weight(.semibold)).foregroundStyle(Theme.accent)
            FlowLayout(spacing: 8) {
                ForEach(OtherSources.links(question, lang: l10n.language), id: \.id) { link in
                    Button { openURL(link.url) } label: {
                        Text(l10n.t(Self.labels[link.id] ?? link.id)).font(.caption)
                            .padding(.horizontal, 12).frame(minHeight: 36)
                            .foregroundStyle(Color(hex: 0x8CC0DE))
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .glass(Capsule())
                }
            }
            Text(l10n.t("answer_search_more_note")).font(.caption2).foregroundStyle(Theme.ink.opacity(0.6))
        }
        .padding(.top, 4)
    }
}
