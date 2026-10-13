import NMCore
import SwiftUI

/// Ask by voice (or Scribble, or the keyboard): the watch answers from the common answers it carries, with their
/// Qur'an and hadith references and the review label. Everything else is for the iPhone, which searches the books.
struct AskPage: View {
    @Environment(WatchModel.self) private var model
    @State private var question = ""
    @State private var answer: CommonQuestion?
    @State private var asked: String?

    var body: some View {
        let l10n = model.l10n
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                TextField(l10n.t("watch_ask_prompt"), text: $question)
                    .onSubmit { ask(question) }
                if let asked {
                    if let a = answer {
                        AnswerView(q: a, lang: l10n.language, l10n: l10n, accent: model.accent).watchCard()
                    } else {
                        Text(asked).font(.headline)
                        Text(l10n.t("watch_ask_none")).font(.footnote).foregroundStyle(.secondary)
                    }
                    Divider()
                }
                if let s = model.state {
                    let suggested = s.todaysQuestions(Date())
                    if !suggested.isEmpty {
                        Text(l10n.t("widget_ask_suggested")).font(.caption2).foregroundStyle(.secondary)
                        ForEach(suggested, id: \.self) { q in
                            Button { ask(q) } label: {
                                Label(q, systemImage: "questionmark.bubble").font(.footnote).lineLimit(3)
                            }
                        }
                    }
                }
            }
        }
        .watchSky()
        .navigationTitle(l10n.t("tab_ask_short"))
        .onAppear {
            if let q = model.pendingAsk {
                model.pendingAsk = nil
                ask(q)
            }
        }
    }

    private func ask(_ q: String) {
        let text = q.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        asked = text
        let r = model.askEngine?.retrieve(text)
        answer = r?.kind == .common ? r?.commonQuestion : nil
        question = ""
    }
}

/// A common answer: the question, the summary, its references and the review label.
struct AnswerView: View {
    let q: CommonQuestion
    let lang: String
    let l10n: Localization
    let accent: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(q.question[lang] ?? q.question["en"] ?? "").font(.headline)
            Text(q.summary[lang] ?? q.summary["en"] ?? "").font(.footnote)
            Text(l10n.t("sources")).font(.caption2.weight(.semibold)).foregroundStyle(accent)
            ForEach(q.citations, id: \.self) { c in
                Text(reference(c)).font(.caption2).foregroundStyle(.secondary)
            }
            if let h = q.hadith, !h.isEmpty {
                Text(l10n.t("library_type_hadith") + ": HadeethEnc " + h.map { "#\($0)" }.joined(separator: ", ")).font(.caption2).foregroundStyle(.secondary)
            }
            Label(q.isReviewed ? l10n.t("answer_common_reviewed", q.reviewedBy ?? "") : l10n.t("answer_common_unreviewed"),
                  systemImage: q.isReviewed ? "checkmark.seal" : "hourglass")
                .font(.caption2).foregroundStyle(.secondary)
        }
    }

    /// "quran:5:6" → "Quran 5:6" in the reader's language.
    private func reference(_ id: String) -> String {
        let p = id.split(separator: ":")
        if p.count == 3, p[0] == "quran", let s = Int(p[1]), let a = Int(p[2]) { return l10n.t("reference_quran", s, a) }
        return id
    }
}
