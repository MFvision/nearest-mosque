import NMCore
import SwiftUI

/// "Time for Dhuhr", full screen, while the app is open. Silent (no adhan audio in this version); closes
/// with the button or the remote's Back button, and by itself after five minutes.
struct PrayerAlertView: View {
    @Environment(TVModel.self) private var model
    let alert: PrayerAlert
    var close: () -> Void
    @FocusState private var focused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var glow = false

    var body: some View {
        let l10n = model.l10n
        let name = l10n.t(Format.prayerKey(alert.event))
        let sky = Sky.of(SkyPeriod.at(alert.at, today: model.days(now: alert.at).dropFirst().first, current: alert.event))
        ZStack {
            SkyBackdrop(sky: sky, horizon: 0.78, skyline: true).ignoresSafeArea()
            Color.black.opacity(0.25).ignoresSafeArea()
            VStack(spacing: 36) {
                LogoDisc(size: 200, glow: glow)
                Text(l10n.t("reminder_title", name))
                    .font(.system(size: 96, weight: .bold))
                    .multilineTextAlignment(.center)
                Text(Format.time(alert.at, zone: model.settings.location?.zone ?? .current, locale: l10n.locale))
                    .font(.system(size: 56, weight: .medium).monospacedDigit())
                    .foregroundStyle(Theme.gold)
                if let loc = model.settings.location {
                    Text(loc.name).font(.system(size: 36)).foregroundStyle(.white.opacity(0.85))
                }
                Button(l10n.t("close"), action: close)
                    .focused($focused)
                    .padding(.top, 30)
            }
            .foregroundStyle(.white)
            .padding(80)
        }
        .onAppear {
            focused = true
            glow = !reduceMotion
        }
        .onExitCommand(perform: close)
        .accessibilityAddTraits(.isModal)
    }
}
