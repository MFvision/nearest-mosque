import SwiftUI

/// The app's language: the device's language first, then every supported language by its own name, with the
/// name in the current language beneath and a search field. Used from Settings and the first-launch setup.
struct LanguagePickerView: View {
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private func localName(_ code: String) -> String {
        l10n.locale.localizedString(forIdentifier: code) ?? l10n.locale.localizedString(forLanguageCode: code) ?? ""
    }

    private var matches: [String] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return Localization.supported }
        return Localization.supported.filter { code in
            [Localization.nativeNames[code] ?? code, localName(code), Locale(identifier: "en").localizedString(forIdentifier: code) ?? "", code]
                .contains { $0.lowercased().contains(q) }
        }
    }

    var body: some View {
        @Bindable var l10n = l10n
        List {
            if query.isEmpty {
                Section {
                    row(title: l10n.t("use_device_language"), subtitle: Localization.nativeNames[Localization.deviceLanguage ?? ""] ?? "",
                        selected: l10n.override == nil) { l10n.override = nil }
                }
            }
            Section {
                ForEach(matches, id: \.self) { code in
                    let native = Localization.nativeNames[code] ?? code
                    let local = localName(code)
                    row(title: native, subtitle: local.caseInsensitiveCompare(native) == .orderedSame ? "" : local,
                        selected: l10n.override == code) { l10n.override = code }
                }
            } footer: {
                Text(l10n.t("draft_translations_note"))
            }
        }
        .searchable(text: $query, prompt: Text(l10n.t("language_search")))
        .navigationTitle(l10n.t("language"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func row(title: String, subtitle: String, selected: Bool, choose: @escaping () -> Void) -> some View {
        Button {
            choose()
            dismiss()
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.body).foregroundStyle(.primary)
                    if !subtitle.isEmpty { Text(subtitle).font(.footnote).foregroundStyle(.secondary) }
                }
                Spacer()
                if selected { Image(systemName: "checkmark").font(.body.weight(.semibold)).foregroundStyle(Theme.accent) }
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
