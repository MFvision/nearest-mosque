import NMCore
import SwiftUI

/// Offers the content packs of `lang` (hadith, Quran translation, tafsir, library) when the app has none
/// bundled for it. Nothing is downloaded until the reader taps the button; nothing shows when there is
/// nothing to download. `onSky` = the onboarding style (glass on the sky) instead of a Settings row.
struct ContentDownloadCard: View {
    @Environment(AppModel.self) private var app
    @Environment(Localization.self) private var l10n
    let lang: String
    var onSky = false

    /// True when the catalog lists content for `lang` (Settings shows the section only then).
    static func offered(_ lang: String, _ app: AppModel) -> Bool { !app.downloads.catalog.forLanguage(lang).isEmpty }

    /// The kinds of content in `packs`, in a fixed order, as the string keys used in the library.
    static func kinds(_ packs: [RemotePack]) -> [String] {
        let ids = packs.map { $0.id.replacingOccurrences(of: "sources.", with: "") }
        return [
            ids.contains { $0.hasPrefix("hadeethenc-") } ? "library_type_hadith" : nil,
            ids.contains { $0.hasPrefix("quranenc-") && !$0.hasPrefix("quranenc-tafsir-") } ? "library_type_quran" : nil,
            ids.contains { $0.hasPrefix("quranenc-tafsir-") } ? "library_type_tafsir" : nil,
            ids.contains { $0.hasPrefix("islamhouse-") } ? "library_title" : nil,
        ].compactMap { $0 }
    }

    private func size(_ bytes: Int) -> String {
        ByteCountFormatter.string(fromByteCount: Int64(bytes), countStyle: .file)
    }

    var body: some View {
        let d = app.downloads
        let state = d.state[lang]
        let todo = d.missing(lang, packs: app.packs)
        if !todo.isEmpty || state == .done {
            let card = VStack(alignment: .leading, spacing: 8) {
                Text(l10n.t("content_title", Languages.name(lang))).font(.headline).accessibilityAddTraits(.isHeader)
                switch state {
                case .running(let done, let total)?:
                    Text(l10n.t("content_downloading", size(done), size(total)))
                        .font(.subheadline).foregroundStyle(Theme.ink.opacity(0.8))
                    ProgressView(value: Double(done), total: Double(max(total, 1))).tint(Theme.accent)
                case .done?:
                    Text(l10n.t("content_done")).font(.subheadline).foregroundStyle(Theme.accent)
                default:
                    // What this language actually has (some sources lack a translation or tafsir in it).
                    Text(Self.kinds(d.catalog.forLanguage(lang)).map { l10n.t($0) }.joined(separator: " · "))
                        .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.accent)
                    Text(l10n.t("content_note")).font(.subheadline).foregroundStyle(Theme.ink.opacity(0.8))
                    if state == .failed {
                        Text(l10n.t("content_failed")).font(.subheadline).foregroundStyle(.red)
                    }
                    Button {
                        d.download(lang, packs: app.packs, semantic: app.semantic)
                    } label: {
                        Label(l10n.t("content_download", size(todo.reduce(0) { $0 + $1.bytes })), systemImage: "arrow.down.circle")
                            .font(.body.weight(.semibold)).frame(maxWidth: onSky ? .infinity : nil, minHeight: 44)
                    }
                    .buttonStyle(.bordered).tint(Theme.accent)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if onSky { card.glassCard(padding: 16, cornerRadius: 20) } else { card }
        }
    }
}
