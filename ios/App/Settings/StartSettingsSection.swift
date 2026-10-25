import NMCore
import SwiftUI
import UIKit

/// Where the menu, start and icon choices are kept (UserDefaults; nothing leaves the phone).
enum StartKey {
    static let menuStyle = "menuStyle"
    static let startScreen = "startScreen"
    /// The last page shown (StartPlace raw value), for Open with › Where I left off.
    static let lastPlace = "lastPlace"
}

/// Settings › Menu and start: the menu style (Tabs or Classic), the screen the app opens with, and the app icon.
struct StartSettingsSection: View {
    @Environment(\.l10n) private var l10n
    @AppStorage(StartKey.menuStyle) private var menuStyle = MenuStyle.tabs.rawValue
    @AppStorage(StartKey.startScreen) private var startScreen = StartScreen.lastPlace.rawValue
    @State private var icon = AppIconChoice(iconName: Self.currentIconName)
    @State private var iconError = false

    var body: some View {
        Section {
            Picker(l10n.t("menu_style"), selection: $menuStyle) {
                ForEach(MenuStyle.allCases, id: \.self) { Text(l10n.t($0.titleKey)).tag($0.rawValue) }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("settings.menuStyle")
            Picker(selection: $startScreen) {
                ForEach(StartScreen.allCases, id: \.self) { s in
                    Label(l10n.t(s.titleKey), systemImage: s.symbol).tag(s.rawValue)
                }
            } label: {
                SettingsRow(icon: "arrow.up.forward.app.fill", tint: .orange, title: l10n.t("open_with"))
            }
            .pickerStyle(.menu)
            .accessibilityIdentifier("settings.startScreen")
            if Self.canChangeIcon {
                VStack(alignment: .leading, spacing: 10) {
                    Text(l10n.t("app_icon"))
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(AppIconChoice.allCases, id: \.self) { iconButton($0) }
                        }
                        .padding(.vertical, 6)
                        .padding(.horizontal, 4)
                    }
                }
                .accessibilityIdentifier("settings.appIcon")
            }
        } header: { Text(l10n.t("menu_start")) } footer: { Text(l10n.t("menu_start_note")) }
        .sensoryFeedback(.selection, trigger: menuStyle)
        .sensoryFeedback(.selection, trigger: icon)
        .alert(l10n.t("icon_error"), isPresented: $iconError) {
            Button(l10n.t("done"), role: .cancel) {}
        }
    }

    private func iconButton(_ choice: AppIconChoice) -> some View {
        let on = icon == choice
        return Button { choose(choice) } label: {
            VStack(spacing: 6) {
                Image(choice.preview)
                    .resizable()
                    .frame(width: 60, height: 60)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 17, style: .continuous)
                            .strokeBorder(on ? Theme.gold : .clear, lineWidth: 2.5)
                            .padding(-4)
                    }
                Text(l10n.t(choice.titleKey))
                    .font(.caption.weight(on ? .semibold : .regular))
                    .foregroundStyle(on ? .primary : .secondary)
            }
            .padding(4)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(l10n.t(choice.titleKey))
        .accessibilityAddTraits(on ? .isSelected : [])
        .accessibilityIdentifier("settings.appIcon.\(choice.rawValue)")
    }

    private func choose(_ choice: AppIconChoice) {
        guard choice != icon else { return }
        let previous = icon
        icon = choice
        #if !targetEnvironment(macCatalyst) && !os(visionOS)
        Task { @MainActor in
            do {
                try await UIApplication.shared.setAlternateIconName(choice.iconName)
            } catch {
                icon = previous
                iconError = true
            }
        }
        #endif
    }

    /// iPhone and iPad only: the Mac and Vision Pro apps keep their own icon.
    @MainActor private static var canChangeIcon: Bool {
        #if targetEnvironment(macCatalyst) || os(visionOS)
        false
        #else
        UIApplication.shared.supportsAlternateIcons
        #endif
    }

    @MainActor private static var currentIconName: String? {
        #if targetEnvironment(macCatalyst) || os(visionOS)
        nil
        #else
        UIApplication.shared.alternateIconName
        #endif
    }
}
