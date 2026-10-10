#if DEBUG && canImport(ActivityKit) && !targetEnvironment(macCatalyst)
import NMCore
import SwiftUI

/// Debug builds only: the Dynamic Island and Lock Screen designs drawn in the app for CI screenshots
/// (`-demoWidgets YES -demoWidgetKind island -demoWidgetLook <style>`). The island shapes are a close
/// approximation of the system's (black capsule, camera in the middle); on a phone the system lays them out.
struct IslandGalleryView: View {
    @Environment(\.appModel) private var model
    let style: String

    var body: some View {
        let l10n = model.l10n
        let tint = IslandTint.of(style)
        let city = model.settings.location?.name ?? "Riyadh"
        let qibla = QiblaActivityAttributes(city: city, title: l10n.t("qibla"), bearingText: "243°",
                                            fromNorth: l10n.t("qibla_bearing", "243"), rtl: l10n.isRTL, style: style)
        let turning = QiblaActivityAttributes.ContentState(turn: -38, facing: false, hint: l10n.t("qibla_go_left"))
        let facing = QiblaActivityAttributes.ContentState(turn: 1, facing: true, hint: l10n.t("qibla_facing_short"))
        ScrollView {
            VStack(spacing: 22) {
                // Compact: arrow left of the camera, degrees to turn on the right.
                compact {
                    IslandArrow(turn: turning.turn, facing: false, tint: tint, size: 22)
                } trailing: {
                    HStack(spacing: 2) {
                        Image(systemName: "arrow.turn.up.left").font(.system(size: 10, weight: .bold))
                        Text("38°").font(.system(.caption, design: .rounded).weight(.bold))
                    }
                    .foregroundStyle(tint)
                }
                compact {
                    IslandArrow(turn: facing.turn, facing: true, tint: tint, size: 22)
                } trailing: {
                    WidgetKaaba().frame(width: 20, height: 20).shadow(color: tint, radius: 6)
                }
                expanded(qibla, turning, tint)
                expanded(qibla, facing, tint)
                QiblaLockCard(attributes: qibla, state: turning)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .frame(width: 360)
                prayerLock(tint)
            }
            .padding(.vertical, 50)
            .frame(maxWidth: .infinity)
        }
        .background(Color(white: 0.55).ignoresSafeArea())
    }

    private func compact<L: View, T: View>(@ViewBuilder _ leading: () -> L, @ViewBuilder trailing: () -> T) -> some View {
        HStack(spacing: 0) {
            leading().frame(width: 44)
            Capsule().fill(Color(white: 0.08)).frame(width: 110, height: 30) // the camera area stays empty
            trailing().frame(width: 52)
        }
        .frame(height: 37)
        .padding(.horizontal, 6)
        .background(Color.black, in: Capsule())
    }

    private func expanded(_ a: QiblaActivityAttributes, _ s: QiblaActivityAttributes.ContentState, _ tint: Color) -> some View {
        VStack(spacing: 4) {
            HStack {
                HStack(spacing: 8) {
                    WidgetKaaba().frame(width: 26, height: 26).shadow(color: s.facing ? tint.opacity(0.9) : .clear, radius: 8)
                    Text(a.title).font(.headline).foregroundStyle(.white)
                }
                Spacer()
                Capsule().fill(Color(white: 0.08)).frame(width: 110, height: 30)
                Spacer()
                VStack(alignment: .trailing, spacing: 0) {
                    Text(a.bearingText).font(.system(.title3, design: .rounded).weight(.bold)).foregroundStyle(tint)
                    Text(a.city).font(.caption2).foregroundStyle(.white.opacity(0.6))
                }
            }
            QiblaArcDial(turn: s.turn, facing: s.facing, tint: tint, onDark: true).frame(height: 92)
            Text(s.hint).font(.subheadline.weight(.semibold)).foregroundStyle(s.facing ? tint : .white)
        }
        .padding(.horizontal, 20).padding(.vertical, 14)
        .frame(width: 370)
        .background(Color.black, in: RoundedRectangle(cornerRadius: 44, style: .continuous))
        .environment(\.layoutDirection, a.rtl ? .rightToLeft : .leftToRight)
    }

    private func prayerLock(_ tint: Color) -> some View {
        let l10n = model.l10n
        let names = PrayerEvent.prayers.map { l10n.t(Format.prayerKey($0)) }
        let times = ["4:31", "11:40", "3:01", "5:31", "7:01"]
        let state = PrayerActivityAttributes.ContentState(
            prayer: names[2], symbol: Theme.icon(.asr), at: Date().addingTimeInterval(42 * 60 + 10), start: Date().addingTimeInterval(-3 * 3600),
            timeText: "3:01", city: model.settings.location?.name ?? "Riyadh", nowTitle: l10n.t("reminder_title", names[2]),
            slots: zip(names, times).map { PrayerActivityAttributes.Slot(name: $0, time: $1) }, nextIndex: 2, period: "dhuhr")
        let attrs = PrayerActivityAttributes(rtl: l10n.isRTL, style: style, nextLabel: l10n.t("next_prayer"), qiblaLabel: l10n.t("qibla"),
                                             mosqueLabel: l10n.t("tab_mosques"))
        return PrayerLockScreenView(attributes: attrs, state: state, stale: false)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .frame(width: 360)
    }
}
#endif
