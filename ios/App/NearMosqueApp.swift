import SwiftUI
import UIKit
import UserNotifications

@main
struct NearMosqueApp: App {
    @State private var model = AppModel.shared
    /// "system", "light" or "dark" (Settings → Appearance).
    @AppStorage("appearance") private var appearance = "system"
    @Environment(\.scenePhase) private var phase

    init() {
        // Before launch finishes, so a tap that opened the app is handled too.
        UNUserNotificationCenter.current().delegate = NotificationHandler.shared
        #if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
        // The Qibla widget's button starts the compass in the Dynamic Island through the app (in the background).
        QiblaIslandRunner.start = { await QiblaIsland.shared.start() }
        #endif
        // Reminders reach only days ahead (iOS keeps 64); this tops them up while the app is closed.
        ReminderRefresh.register()
        #if DEBUG
        _ = DemoPreview.launched
        #endif
    }

    var body: some Scene {
        WindowGroup {
            rootView
                // The whole Adhan, while it plays (with Stop).
                .overlay(alignment: .top) {
                    AdhanNowPlayingBar().animation(Theme.spring, value: AdhanPlayer.shared.isAdhan)
                }
                .environment(model)
                .environment(model.l10n)
                .environment(\.appModel, model)
                .environment(\.l10n, model.l10n)
                .environment(\.locale, model.l10n.locale)
                .environment(\.layoutDirection, model.l10n.layoutDirection)
                // Follows the phone unless Settings → Appearance picks light or dark.
                .preferredColorScheme(appearance == "light" ? .light : appearance == "dark" ? .dark : nil)
                .task { await model.start() }
                // Each time the app comes to the screen: the Lock Screen countdown moves on, the reminders are topped
                // up and, with "Use my location", the place is checked again (AppModel.foregroundRefresh).
                .onChange(of: phase, initial: true) { _, p in
                    if p == .active { Task { await model.foregroundRefresh() } }
                    updateWeather(p)
                }
                // Midnight, or a change of clock or time zone: the reminders follow, also on a Mac left open for days.
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
                    Task { await model.timeChanged() }
                }
                .onReceive(NotificationCenter.default.publisher(for: .NSSystemTimeZoneDidChange)) { _ in
                    Task { await model.timeChanged() }
                }
                // "Sky follows the weather": turning it on or choosing another city asks at once (at most once a
                // minute); turning it off stops and deletes the saved weather.
                .onChange(of: weatherKey) { _, _ in updateWeather(phase) }
        }
        #if os(visionOS)
        // A comfortable reading window in the room; people can resize it.
        .defaultSize(width: 980, height: 720)
        #endif
        .commands {
            // Mac menu bar and iPad keyboard shortcuts (⌘1, ⌘2, ⌘K, ⌘,).
            CommandGroup(replacing: .appSettings) {
                Button(model.l10n.t("settings")) { open("nearmosque://settings") }.keyboardShortcut(",", modifiers: .command)
            }
            CommandGroup(replacing: .textFormatting) {}
            CommandGroup(replacing: .newItem) {}
            CommandMenu(model.l10n.t("app_name")) {
                Button(model.l10n.t("tab_prayer")) { open("nearmosque://prayer") }.keyboardShortcut("1", modifiers: .command)
                Button(model.l10n.t("tab_mosques")) { open("nearmosque://mosques") }.keyboardShortcut("2", modifiers: .command)
                Button(model.l10n.t("widget_kind_qibla")) { open("nearmosque://qibla") }.keyboardShortcut("3", modifiers: .command)
                Divider()
                Button(model.l10n.t("widget_kind_ask")) { open("nearmosque://ask") }.keyboardShortcut("k", modifiers: .command)
            }
        }
    }

    @MainActor private func open(_ s: String) {
        if let url = URL(string: s) { PendingRoute.open(url) }
    }

    /// Changes when the weather switch or the prayer city changes (with "Use my location", the city near the device).
    private var weatherKey: String {
        let p = model.settings.location?.weatherLocation
        return "\(model.settings.skyWeather) " + (p.map { "\($0.latitude) \($0.longitude)" } ?? "none")
    }

    /// The weather is fetched only while the app is active (MET's terms: no new data while the app is not in use);
    /// never in the background. Turning the switch off deletes the saved weather at once, active or not. Only a city's
    /// coordinates are sent: with "Use my location", the city near the device, and nothing without one.
    @MainActor private func updateWeather(_ p: ScenePhase) {
        if p == .active || !model.settings.skyWeather {
            model.weather.resume(location: model.settings.location?.weatherLocation, enabled: model.settings.skyWeather)
        }
        if p != .active { model.weather.pause() }
    }
}

extension NearMosqueApp {
    @ViewBuilder private var rootView: some View {
        #if DEBUG
        Group {
            if UserDefaults.standard.bool(forKey: "demoWidgets") { WidgetGalleryView() } else { RootView() }
        }
        .task {
            // CI screenshots on iPad: `-demoLandscape YES` turns the window to landscape.
            guard UserDefaults.standard.bool(forKey: "demoLandscape"),
                  let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
            #if !targetEnvironment(macCatalyst) && !os(visionOS)
            scene.requestGeometryUpdate(.iOS(interfaceOrientations: .landscapeRight)) { _ in }
            #endif
        }
        #else
        RootView()
        #endif
    }
}

enum AppTab: Hashable { case prayer, mosques, ask }

/// Two tabs on a Liquid Glass tab bar (Prayer & Qibla, Nearest Mosque) and Ask AI as a floating pill
/// above it that opens the chat full screen. Settings, downloads and sources sit behind the gear on
/// each tab. First launch shows the animated tour.
struct RootView: View {
    @Environment(\.appModel) private var model
    @Environment(\.l10n) private var l10n
    @State private var tab: AppTab = Self.initialTab == .mosques ? .mosques : .prayer
    #if DEBUG
    /// CI screenshots: `-demoAudioSettings YES` opens Settings (and then Prayer & Audio).
    @State private var showSettings = UserDefaults.standard.bool(forKey: "demoAudioSettings")
    #else
    @State private var showSettings = false
    #endif
    @State private var showAsk = Self.initialTab == .ask
    /// Kept here so closing Ask and opening it again keeps the conversation.
    @State private var ask = AskModel()
    @State private var pendingQuestion: String?

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
                NavigationStack {
                    PrayerView(showSettings: $showSettings, onMosques: { tab = .mosques })
                        .modifier(PageSwipe(edgeOnly: false, forward: { tab = .mosques }))
                }
            }
            Tab(l10n.t("tab_mosques"), image: "MosqueTab", value: AppTab.mosques) {
                NavigationStack {
                    // From the screen's edge only, so dragging the map never changes the page.
                    MosquesView(showSettings: $showSettings)
                        .modifier(PageSwipe(edgeOnly: true, back: { tab = .prayer }))
                }
            }
        }
        .tint(Theme.gold)
        .modifier(TabBarMinimize())
        // Widgets: nearmosque://prayer, nearmosque://qibla, nearmosque://mosques, nearmosque://mosque?id=…,
        // nearmosque://ask (optionally ?q=… to ask a question). Shortcuts: PendingRoute; controls: openQibla.
        .onOpenURL { open($0, trusted: false) }
        .onReceive(NotificationCenter.default.publisher(for: .openRoute)) { _ in PendingRoute.take().map { open($0, trusted: true) } }
        .onChange(of: model.ready) { _, ready in
            // A question from Siri or a widget that arrived before the library was open.
            if ready, let q = pendingQuestion { pendingQuestion = nil; ask.reset(); ask.ask(model, q) }
        }
        .onReceive(NotificationCenter.default.publisher(for: .openQibla)) { _ in openQiblaIfAsked() }
        .onAppear { openQiblaIfAsked(); PendingRoute.take().map { open($0, trusted: true) }; model.shareWithWidgets() }
        .onChange(of: l10n.language) { _, _ in model.shareWithWidgets() }
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

extension RootView {
    /// A Siri request or control run before the app was open leaves this flag.
    /// `trusted`: from the app itself (Siri, shortcuts, controls). A question arriving by link runs at once only when it
    /// carries this phone's widget token; from any other app or web page it is only put in the box (at most 300
    /// characters), for the person to send or not.
    fileprivate func open(_ url: URL, trusted: Bool) {
        let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        // A sheet on top would keep the requested screen from showing.
        if url.host != "settings" { showSettings = false }
        switch url.host {
        case "qibla": tab = .prayer; showAsk = false; model.requestQibla = true
        case "mosques": tab = .mosques; showAsk = false
        case "settings": showAsk = false; showSettings = true
        case "mosque":
            tab = .mosques; showAsk = false
            model.requestMosqueId = query.first { $0.name == "id" }?.value
        case "ask":
            if let raw = query.first(where: { $0.name == "q" })?.value, !raw.isEmpty {
                let q = String(raw.prefix(300))
                if trusted || query.first(where: { $0.name == "t" })?.value == AppModel.linkToken {
                    // Its own conversation: earlier turns would be mixed into a short question.
                    if model.ready { ask.reset(); ask.ask(model, q) } else { pendingQuestion = q }
                } else {
                    ask.draft = q
                }
            }
            showAsk = true
        default: tab = .prayer; showAsk = false
        }
    }

    fileprivate func openQiblaIfAsked() {
        guard UserDefaults.standard.bool(forKey: "openQiblaOnLaunch") else { return }
        UserDefaults.standard.set(false, forKey: "openQiblaOnLaunch")
        tab = .prayer; showAsk = false; model.requestQibla = true
    }
}

/// iOS 26: the Liquid Glass tab bar minimizes while scrolling down.
private struct TabBarMinimize: ViewModifier {
    func body(content: Content) -> some View {
        #if compiler(>=6.2) && !targetEnvironment(macCatalyst) && !os(visionOS)
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
        // The Mac keeps the floating pill (the tab bar accessory is a phone and tablet feature).
        #if compiler(>=6.2) && !targetEnvironment(macCatalyst) && !os(visionOS)
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
    @Environment(\.l10n) private var l10n
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
    @Environment(\.l10n) private var l10n
    var body: some View {
        GlassIconButton(systemImage: "gearshape", label: l10n.t("settings")) { show = true }
    }
}

/// Swiping across a main page moves to the other one: towards the next page in the reading direction (from
/// right to left in English, from left to right in Arabic) and back. A clearly sideways swipe only, so scrolling
/// is never taken for one; with `edgeOnly` it has to start at the screen's edge (for pages with a map).
struct PageSwipe: ViewModifier {
    @Environment(\.layoutDirection) private var direction
    var edgeOnly: Bool
    var forward: (() -> Void)? = nil
    var back: (() -> Void)? = nil
    @State private var width: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
            .simultaneousGesture(
                DragGesture(minimumDistance: 30, coordinateSpace: .local).onEnded { v in
                    let dx = v.translation.width, dy = v.translation.height
                    guard abs(dx) > 90, abs(dx) > abs(dy) * 2 else { return }
                    if edgeOnly {
                        let start = v.startLocation.x
                        guard dx > 0 ? start < 44 : start > width - 44 else { return }
                    }
                    let towardsNext = direction == .rightToLeft ? dx > 0 : dx < 0
                    withAnimation(.snappy) { (towardsNext ? forward : back)?() }
                }
            )
    }
}
