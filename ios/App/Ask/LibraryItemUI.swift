import NMCore
import NMData
import SwiftUI

private func isRTL(_ lang: String) -> Bool { Languages.isRTL(lang) }

/// Title shown for a library record (a Qur'an reference for QuranEnc verses).
func libraryTitle(_ c: SourceChunk, _ l10n: Localization) -> String {
    if c.section?.publisher == "quranenc", let s = c.section?.surah, let a = c.section?.ayah { return l10n.t("reference_quran", s, a) }
    return c.anchor
}

func libraryTypeLabel(_ c: SourceChunk, _ l10n: Localization) -> String? {
    let key: String
    switch c.section?.type {
    case "books": key = "library_type_books"
    case "articles": key = "library_type_articles"
    case "fatwa": key = "library_type_fatwa"
    case "videos": key = "library_type_videos"
    case "audios": key = "library_type_audios"
    case "hadith": key = "library_type_hadith"
    case "quran": key = "library_type_quran"
    case "tafsir": key = "library_type_tafsir"
    default: return nil
    }
    return ([l10n.t(key)] + [c.section?.collection].compactMap { $0 }).joined(separator: " · ")
}

/// Attribution required by each source (binbaz: the source; HadeethEnc/QuranEnc: source and version).
func libraryPublisherLine(_ c: SourceChunk, _ l10n: Localization) -> String? {
    switch c.section?.publisher {
    case "binbaz": return l10n.t("publisher_binbaz")
    case "hadeethenc": return l10n.t("publisher_hadeethenc", c.section?.version ?? "")
    case "quranenc": return l10n.t("publisher_quranenc", c.section?.translationTitle ?? "", c.section?.version ?? "")
    default: return nil
    }
}

func libraryWebLabel(_ c: SourceChunk, _ l10n: Localization) -> String {
    guard c.section?.publisher != nil, let host = c.url.flatMap(URL.init(string:))?.host else { return l10n.t("library_web") }
    return l10n.t("library_web_site", host.hasPrefix("www.") ? String(host.dropFirst(4)) : host)
}

/// A stored record read natively: each part (question, answer, hadith, explanation, translation...) with its
/// label and in its own direction; the Arabic verse above a QuranEnc translation (from the Quran pack); grade
/// and source; the required attribution; and the page for the full text when shortened.
struct PartsReader: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    let chunk: SourceChunk
    @State private var full = false

    private func label(_ kind: String) -> String? {
        switch kind {
        case "question": return l10n.t("library_question")
        case "answer": return l10n.t("library_answer")
        case "hadith": return l10n.t("library_part_hadith")
        case "explanation": return l10n.t("library_part_explanation")
        case "benefits": return l10n.t("library_part_benefits")
        case "words": return l10n.t("library_part_words")
        case "verse": return l10n.t("library_part_verse")
        case "translation": return l10n.t("library_part_translation")
        case "tafsir": return l10n.t("library_part_tafsir")
        case "footnotes": return l10n.t("library_part_footnotes")
        default: return nil
        }
    }

    var body: some View {
        let s = chunk.section
        let url = chunk.url.flatMap(URL.init(string:)).flatMap { LibraryFiles.isAllowed($0) ? $0 : nil }
        let verse = s?.verse.flatMap { id in (try? app.ask?.resolve([id]))?.first }.map { LibraryParts.Part(kind: "verse", lang: "ar", text: $0.chunk.original.text) }
        let parts = [verse].compactMap { $0 } + LibraryParts.parts(chunk).filter { $0.kind != "title" }
        return Group {
            if full, let url {
                SafariView(url: url).ignoresSafeArea(edges: .bottom)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        if let t = libraryTypeLabel(chunk, l10n) { Text(t).font(.footnote.weight(.semibold)).foregroundStyle(Theme.accent) }
                        Text(libraryTitle(chunk, l10n)).font(.title2.bold())
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .environment(\.layoutDirection, isRTL(chunk.original.lang) ? .rightToLeft : .leftToRight)
                        ForEach(Array(parts.enumerated()), id: \.offset) { _, p in
                            if let l = label(p.kind) { Text(l).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.accent).padding(.top, 6) }
                            Text(p.text)
                                .font(p.kind == "verse" ? .title2 : (p.kind == "footnotes" ? .footnote : .title3))
                                .lineSpacing(p.kind == "verse" ? 10 : 7)
                                .foregroundStyle(Theme.ink.opacity(p.kind == "footnotes" ? 0.8 : 1))
                                .textSelection(.enabled)
                                .multilineTextAlignment(.leading)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .environment(\.layoutDirection, isRTL(p.lang) ? .rightToLeft : .leftToRight)
                        }
                        if s?.truncated == true, url != nil {
                            Button(l10n.t("library_full_text")) { full = true }
                                .font(.subheadline.weight(.semibold)).foregroundStyle(Color(hex: 0x8CC0DE)).padding(.top, 4)
                        }
                        Group {
                            if let g = s?.grade, !g.isEmpty { Text(l10n.t("library_grade", g)).padding(.top, 10) }
                            if let src = s?.source, !src.isEmpty { Text(l10n.t("library_source", src)) }
                            if let pub = libraryPublisherLine(chunk, l10n) { Text(pub) }
                        }
                        .font(.caption).foregroundStyle(Theme.ink.opacity(0.75))
                    }
                    .padding(20)
                }
                .foregroundStyle(Theme.ink)
                .background(Color(hex: 0x0B1220).ignoresSafeArea())
            }
        }
    }
}
