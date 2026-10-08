import NMCore
import SwiftUI

@main
struct NearMosqueTVApp: App {
    @State private var model = TVModel.shared
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            TVRoot()
                .environment(model)
                .environment(\.locale, model.l10n.locale)
                .environment(\.layoutDirection, model.l10n.layoutDirection)
                .preferredColorScheme(.dark)
                .task { await model.start() }
                .onChange(of: phase) { _, p in
                    if p == .active { model.scheduleAlert() }
                }
        }
    }
}

enum TVTab: Hashable { case board, qibla, mosques, settings }

struct TVRoot: View {
    @Environment(TVModel.self) private var model
    @State private var tab: TVTab = .board

    var body: some View {
        let l10n = model.l10n
        ZStack {
            if !model.ready {
                SkyBackdrop(sky: Sky.of(.night)).ignoresSafeArea()
            } else if model.settings.location == nil {
                TVSetupView()
            } else {
                TabView(selection: $tab) {
                    Tab(l10n.t("prayer_section"), systemImage: "clock", value: TVTab.board) { BoardView() }
                    Tab(l10n.t("qibla"), systemImage: "location.north.line", value: TVTab.qibla) { TVQiblaView() }
                    Tab(l10n.t("tab_mosques"), systemImage: "building.columns", value: TVTab.mosques) { TVMosquesView() }
                    Tab(l10n.t("settings"), systemImage: "gearshape", value: TVTab.settings) { TVSettingsView() }
                }
                .onAppear(perform: applyDemoTab)
                .disabled(model.alert != nil)
            }
            #if DEBUG
            // Screenshots in CI: the Top Shelf banner as the extension draws it.
            if UserDefaults.standard.bool(forKey: "demoTopShelf"), let state = model.sharedState {
                Color.black.ignoresSafeArea()
                TopShelfBanner(state: state, now: Date())
                    .environment(\.layoutDirection, state.isRTL ? .rightToLeft : .leftToRight)
                    .scaleEffect(0.9)
            }
            #endif
            if let alert = model.alert {
                PrayerAlertView(alert: alert) { model.alert = nil }
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .animation(.easeInOut(duration: 0.6), value: model.alert)
        // The screen saver waits while the board is open (Settings → keep the prayer board on screen).
        .onChange(of: tab, initial: true) { _, t in keepAwake(t) }
        .onChange(of: model.settings.keepAwake) { _, _ in keepAwake(tab) }
        .onChange(of: l10n.language) { _, _ in model.shareWithTopShelf() }
        .onOpenURL { url in
            switch url.host {
            case "qibla": tab = .qibla
            case "mosques": tab = .mosques
            default: tab = .board
            }
        }
    }

    private func keepAwake(_ t: TVTab) {
        UIApplication.shared.isIdleTimerDisabled = model.settings.keepAwake && t == .board
    }

    private func applyDemoTab() {
        #if DEBUG
        switch UserDefaults.standard.string(forKey: "demoTab") {
        case "qibla": tab = .qibla
        case "mosques": tab = .mosques
        case "settings": tab = .settings
        default: break
        }
        if UserDefaults.standard.bool(forKey: "demoAlert") { model.alert = PrayerAlert(event: .dhuhr, at: Date()) }
        #endif
    }
}
