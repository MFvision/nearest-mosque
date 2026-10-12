import NMCore
import SwiftUI
import WidgetKit

/// The companion widget's face (shared with the debug gallery): the figure as full as your check-ins, a short
/// line, and on the medium size the next prayer and an "I prayed" button. `wrapMark` turns the button into the
/// widget's intent; elsewhere it stays a plain label.
struct CompanionFace: View {
    let entry: PrayerEntry
    let family: WidgetFamily
    var wrapMark: (_ event: String, _ date: String, _ label: AnyView) -> AnyView = { _, _, l in l }

    private var kind: CompanionKind {
        let k = entry.state?.companion.flatMap(CompanionKind.init(rawValue:)) ?? .lantern
        return k == .none ? .lantern : k
    }

    private var level: Double { CompanionLog.level(now: entry.date, zone: entry.state?.zone ?? .current) }

    private func t(_ key: String, _ fallback: String) -> String { entry.state?.t(key) ?? fallback }

    private var line: String {
        switch CompanionLog.mood(level) {
        case .happy: return t("companion_happy", "Bright and full today.")
        case .quiet: return t("companion_quiet", "Mark a prayer to fill it up.")
        case .sad: return t("companion_sad", "It missed you. One prayer lights it again.")
        }
    }

    var body: some View {
        let p = entry.palette
        switch family {
        #if !targetEnvironment(macCatalyst) && !os(visionOS)
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                CompanionFigure(kind: kind, level: level).padding(6)
            }
        #endif
        case .systemMedium:
            HStack(spacing: 14) {
                figure.frame(maxWidth: 120)
                VStack(alignment: .leading, spacing: 8) {
                    Text(line).font(.system(size: 16, weight: .semibold, design: .rounded)).foregroundStyle(p.ink)
                        .lineLimit(3).minimumScaleFactor(0.8)
                    if let n = entry.next, let s = entry.state {
                        Text(s.t("prayer_\(n.event.rawValue)") + " " + s.time(n.at)).font(.system(size: 14, weight: .medium))
                            .foregroundStyle(p.secondary).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    mark(p)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 4)
        default:
            VStack(spacing: 6) {
                figure
                Text(line).font(.system(size: 12, weight: .semibold, design: .rounded)).foregroundStyle(p.ink)
                    .multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.75)
            }
        }
    }

    private var figure: some View {
        CompanionFigure(kind: kind, level: level, phase: entry.date.timeIntervalSinceReferenceDate / 600)
            .id(Int(level * 20))
            .transition(.scale(scale: 0.8).combined(with: .opacity))
    }

    @ViewBuilder private func mark(_ p: WidgetPalette) -> some View {
        if let s = entry.state, let m = CompanionLog.markable(today: entry.today, current: entry.current, now: entry.date) {
            let name = s.t("prayer_\(m.0.rawValue)")
            let done = CompanionLog.checkedIn(m.0.rawValue, on: m.1)
            let label = Label(String(format: s.t(done ? "companion_marked" : "companion_mark"), locale: s.locale, name),
                              systemImage: done ? "checkmark.circle.fill" : "hands.and.sparkles")
                .font(.system(size: 14, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.75)
                .foregroundStyle(done ? p.secondary : p.onPill)
                .padding(.horizontal, 12).frame(minHeight: 36)
                .background(Capsule().fill(done ? p.card : p.pill))
            if done { label } else { wrapMark(m.0.rawValue, CompanionLog.iso(m.1), AnyView(label)) }
        }
    }
}
