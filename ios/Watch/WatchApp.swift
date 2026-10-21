import NMCore
import SwiftUI

@main
struct NearMosqueWatchApp: App {
    @State private var model = WatchModel.shared
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            WatchRoot()
                .environment(model)
                .environment(\.locale, model.l10n.locale)
                .environment(\.layoutDirection, model.l10n.layoutDirection)
                .task { await model.start() }
                .onChange(of: phase) { _, p in
                    // Back on screen: notifications may have been allowed or turned off meanwhile.
                    if p == .active, model.settings.ownReminders { model.publish() }
                    if p == .background { model.publishLeaving() }
                }
        }
        .backgroundTask(.appRefresh("refresh")) {
            await WatchModel.shared.backgroundRefresh()
        }
    }
}

/// Pages, one above the other (turn the Digital Crown): prayer times, Qibla, spiritual health, tasbih, nearest
/// mosques and Ask.
/// Settings open from the end of the prayer page (nothing sits beside the clock).
struct WatchRoot: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            TabView(selection: $model.screen) {
                PrayerPage().tag(WatchScreen.prayer)
                QiblaPage().tag(WatchScreen.qibla)
                SpiritPage().tag(WatchScreen.spirit)
                TasbihPage().tag(WatchScreen.tasbih)
                MosquesPage().tag(WatchScreen.mosques)
                AskPage().tag(WatchScreen.ask)
            }
            .tabViewStyle(.verticalPage)
            .navigationDestination(isPresented: $model.showSettings) { SettingsPage() }
        }
        .tint(model.accent)
        .onOpenURL { url in
            if let s = WatchScreen(url: url) { model.screen = s }
        }
        .onReceive(NotificationCenter.default.publisher(for: .openWatchScreen)) { n in
            if let raw = n.object as? String, let s = WatchScreen(rawValue: raw) {
                model.screen = s
                UserDefaults.standard.removeObject(forKey: "openWatchScreen")
            }
        }
    }
}
