import SwiftUI

@main
struct NearMosqueApp: App {
    @State private var model = AppModel()
    /// "system", "light" or "dark" (Settings → Appearance).
    @AppStorage("appearance") private var appearance = "system"

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .environment(model.l10n)
                .environment(\.locale, model.l10n.locale)
                .environment(\.layoutDirection, model.l10n.layoutDirection)
                // Follows the phone unless Settings → Appearance picks light or dark.
                .preferredColorScheme(appearance == "light" ? .light : appearance == "dark" ? .dark : nil)
                .task { await model.start() }
        }
    }
}

enum AppTab: Hashable { case prayer, mosques, ask }

/// Two tabs on a Liquid Glass tab bar (Prayer & Qibla, Nearest Mosque) and Ask AI as a floating pill
/// above it that opens the chat full screen. Settings, downloads and sources sit behind the gear on
/// each tab. First launch shows the animated tour.
struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(Localization.self) private var l10n
    @State private var tab: AppTab = Self.initialTab == .mosques ? .mosques : .prayer
    @State private var showSettings = false
    @State private var showAsk = Self.initialTab == .ask
    /// Kept here so closing Ask and opening it again keeps the conversation.
    @State private var ask = AskModel()

    private static var initialTab: AppTab {
        #if DEBUG
        switch UserDefaults.standard.string(forKey: "demoTab") {
        case "mosques": return .mosques
        case "ask": return .ask
        default: return .prayer
        }
        #else
        return .prayer
        #endif
    }

    var body: some View {
        TabView(selection: $tab) {
            Tab(l10n.t("tab_prayer"), systemImage: "location.north.circle", value: AppTab.prayer) {
                NavigationStack { PrayerView(showSettings: $showSettings) }
            }
            Tab(l10n.t("tab_mosques"), image: "MosqueTab", value: AppTab.mosques) {
                NavigationStack { MosquesView(showSettings: $showSettings) }
            }
        }
        .tint(Theme.gold)
        .modifier(TabBarMinimize())
        .modifier(AskAccessory { showAsk = true })
        .fullScreenCover(isPresented: $showAsk) {
            NavigationStack { AskView(vm: ask) { showAsk = false } }
                .environment(\.locale, l10n.locale)
                .environment(\.layoutDirection, l10n.layoutDirection)
        }
        .sheet(isPresented: $showSettings) {
            SettingsView()
                .environment(\.locale, l10n.locale)
                .environment(\.layoutDirection, l10n.layoutDirection)
        }
        .fullScreenCover(isPresented: Binding(get: { model.ready && !model.settings.onboarded },
                                              set: { if !$0 { model.settings.onboarded = true } })) {
            OnboardingView()
                .environment(\.locale, l10n.locale)
                .environment(\.layoutDirection, l10n.layoutDirection)
        }
    }
}

/// iOS 26: the Liquid Glass tab bar minimizes while scrolling down.
private struct TabBarMinimize: ViewModifier {
    func body(content: Content) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) { content.tabBarMinimizeBehavior(.onScrollDown) } else { content }
        #else
        content
        #endif
    }
}

/// The floating "Ask AI" pill. iOS 26: the tab bar's bottom accessory (it folds in beside the bar when
/// the bar minimizes). Earlier: a glass capsule just above the tab bar.
private struct AskAccessory: ViewModifier {
    var open: () -> Void
    func body(content: Content) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            content.tabViewBottomAccessory { AskPill(open: open) }
        } else {
            fallback(content)
        }
        #else
        fallback(content)
        #endif
    }

    private func fallback(_ content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            AskPill(open: open)
                .glass(Capsule(), tint: Theme.gold.opacity(0.25), interactive: true)
                .padding(.horizontal, 40)
                .padding(.bottom, 58)
        }
    }
}

private struct AskPill: View {
    @Environment(Localization.self) private var l10n
    var open: () -> Void
    var body: some View {
        Button(action: open) {
            HStack(spacing: 10) {
                Image(systemName: "sparkles").font(.headline).foregroundStyle(Theme.accent)
                Text(l10n.t("tab_ask")).font(.headline)
                Text(l10n.t("ask_title")).font(.subheadline).foregroundStyle(Theme.ink.opacity(0.75)).lineLimit(1)
                Spacer(minLength: 0)
                Image(systemName: "chevron.up").font(.footnote.weight(.bold)).foregroundStyle(Theme.ink.opacity(0.6))
            }
            .foregroundStyle(Theme.ink)
            .padding(.horizontal, 16)
            .frame(minHeight: 48)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(l10n.t("tab_ask"))
        .accessibilityHint(l10n.t("ask_title"))
        .accessibilityAddTraits(.isButton)
    }
}

/// Gear button shown at the top trailing edge of each tab.
struct SettingsButton: View {
    @Binding var show: Bool
    @Environment(Localization.self) private var l10n
    var body: some View {
        GlassIconButton(systemImage: "gearshape", label: l10n.t("settings")) { show = true }
    }
}
