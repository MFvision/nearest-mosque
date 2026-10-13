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
                    if p == .background { model.publish() }
                }
        }
        .backgroundTask(.appRefresh("refresh")) {
            await WatchModel.shared.backgroundRefresh()
        }
    }
}

/// Pages, one above the other (turn the Digital Crown): prayer times, Qibla, tasbih, nearest mosques and Ask.
/// Settings sit behind the gear at the top.
struct WatchRoot: View {
    @Environment(WatchModel.self) private var model
    @State private var settings = false

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            TabView(selection: $model.screen) {
                PrayerPage().tag(WatchScreen.prayer)
                QiblaPage().tag(WatchScreen.qibla)
                TasbihPage().tag(WatchScreen.tasbih)
                MosquesPage().tag(WatchScreen.mosques)
                AskPage().tag(WatchScreen.ask)
            }
            .tabViewStyle(.verticalPage)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { settings = true } label: { Image(systemName: "gearshape") }
                        .accessibilityLabel(model.l10n.t("settings"))
                }
            }
            .sheet(isPresented: $settings) {
                NavigationStack { SettingsPage() }
            }
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
