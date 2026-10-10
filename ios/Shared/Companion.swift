import Foundation
import NMCore
import Security
import SwiftUI

/// The companion (the lantern or the heart): how full it is follows what you mark, a prayer (in the app, on the
/// widget or on a notification) or a round of 33 on the tasbih widget. It never claims to know
/// whether you prayed; it only reacts to what you mark. The log is kept in the shared keychain group (the app and
/// the widgets read it) and never leaves the phone.
enum CompanionKind: String, CaseIterable, Codable {
    case lantern, heart, none
}

enum CompanionMood { case happy, quiet, sad }

enum CompanionLog {
    private static let account = "companion-log"
    private static var group: String? { Bundle.main.object(forInfoDictionaryKey: "SharedKeychainGroup") as? String }

    private static func query() -> [String: Any] {
        var q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "sa.zood.nearmosque.shared",
                                kSecAttrAccount as String: account]
        if let group, !group.hasPrefix("$(") { q[kSecAttrAccessGroup as String] = group }
        return q
    }

    /// Check-ins as "2026-10-10:dhuhr" (a prayer marked) or "2026-10-10:dhikr" (a tasbih round), newest last.
    static var marks: [String] {
        var q = query()
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data,
              let list = try? JSONDecoder().decode([String].self, from: data) else { return [] }
        return list
    }

    private static func save(_ list: [String]) {
        guard let data = try? JSONEncoder().encode(list) else { return }
        let q = query()
        let attrs: [String: Any] = [kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock]
        if SecItemUpdate(q as CFDictionary, attrs as CFDictionary) != errSecSuccess {
            SecItemAdd(q.merging(attrs) { $1 } as CFDictionary, nil)
        }
    }

    /// Notes a check-in for `event` (a PrayerEvent raw value, or "dhikr") on `date`; the same one twice counts once.
    static func checkIn(_ event: String, on date: CivilDate) {
        let key = "\(iso(date)):\(event)"
        var list = marks
        guard !list.contains(key) else { return }
        list.append(key)
        save(Array(list.suffix(60)))
    }

    static func checkedIn(_ event: String, on date: CivilDate) -> Bool { marks.contains("\(iso(date)):\(event)") }

    /// How full: each prayer marked today adds to it (five fill it), a tasbih round adds a little; with nothing
    /// today it rests on the last day you checked in, lower each day away. A newcomer starts half full.
    static func level(now: Date = Date(), zone: TimeZone = .current) -> Double {
        #if DEBUG
        // CI screenshots: `-demoCompanionLevel 1` (full), `0.5`, `0.15`.
        if let demo = UserDefaults.standard.object(forKey: "demoCompanionLevel") as? String, let v = Double(demo) { return v }
        #endif
        let list = marks
        guard !list.isEmpty else { return 0.55 }
        let today = CivilDate.of(now, in: zone)
        let todays = list.filter { $0.hasPrefix("\(iso(today)):") }
        let prayers = todays.filter { !$0.hasSuffix(":dhikr") }.count
        let dhikr = todays.count > prayers
        if !todays.isEmpty { return min(1, 0.45 + 0.11 * Double(prayers) + (dhikr ? 0.1 : 0)) }
        for (away, rest) in [(1, 0.42), (2, 0.28)] where list.contains(where: { $0.hasPrefix("\(iso(today.adding(days: -away))):") }) {
            return rest
        }
        return 0.12
    }

    /// The prayer to mark at `now` (Fajr until Dhuhr) and the date it belongs to (Isha after midnight is the
    /// previous day's); nil before the day's first prayer is known.
    static func markable(today: DaySchedule?, current: PrayerEvent?, now: Date) -> (PrayerEvent, CivilDate)? {
        guard let today, var e = current else { return nil }
        if e == .sunrise { e = .fajr }
        let date = (today[e].map { $0 <= now } ?? false) ? today.date : today.date.adding(days: -1)
        return (e, date)
    }

    static func iso(_ d: CivilDate) -> String { String(format: "%04d-%02d-%02d", d.year, d.month, d.day) }

    static func mood(_ level: Double) -> CompanionMood { level >= 0.75 ? .happy : level >= 0.4 ? .quiet : .sad }
}

/// The companion drawn at one moment: `level` 0...1, `phase` for the flame flicker and the liquid's wave,
/// `tilt` (radians) for the heart's liquid surface, `blink` to close the eyes. The app animates these; widgets
/// draw a still frame (and animate between frames when the level changes).
struct CompanionFigure: View {
    let kind: CompanionKind
    let level: Double
    var phase: Double = 0
    var tilt: Double = 0
    var blink = false

    var body: some View {
        switch kind {
        case .lantern: LanternFigure(level: level, phase: phase, blink: blink)
        case .heart: HeartFigure(level: level, phase: phase, tilt: tilt, blink: blink)
        case .none: EmptyView()
        }
    }
}

private struct Face: View {
    let mood: CompanionMood
    let blink: Bool
    let ink: Color
    let size: CGFloat

    var body: some View {
        Canvas { ctx, s in
            let eyeY = s.height * 0.36, dx = s.width * 0.2, r = s.width * 0.055
            for x in [s.width / 2 - dx, s.width / 2 + dx] {
                if blink || mood == .sad {
                    var p = Path()
                    p.move(to: CGPoint(x: x - r * 1.3, y: eyeY))
                    p.addQuadCurve(to: CGPoint(x: x + r * 1.3, y: eyeY), control: CGPoint(x: x, y: eyeY + (mood == .sad ? r * 1.6 : r * 0.6)))
                    ctx.stroke(p, with: .color(ink), style: StrokeStyle(lineWidth: r * 0.8, lineCap: .round))
                } else {
                    ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: eyeY - r, width: 2 * r, height: 2 * r)), with: .color(ink))
                }
            }
            let my = s.height * 0.58, mw = s.width * 0.18
            var m = Path()
            m.move(to: CGPoint(x: s.width / 2 - mw, y: my))
            let bend: CGFloat = mood == .happy ? s.height * 0.12 : mood == .quiet ? 0 : -s.height * 0.09
            m.addQuadCurve(to: CGPoint(x: s.width / 2 + mw, y: my), control: CGPoint(x: s.width / 2, y: my + bend))
            ctx.stroke(m, with: .color(ink), style: StrokeStyle(lineWidth: r * 0.8, lineCap: .round))
        }
        .frame(width: size, height: size)
    }
}

/// The lantern: brass frame, warm glass, a flame that grows with the level and flickers with the phase.
private struct LanternFigure: View {
    let level: Double
    let phase: Double
    let blink: Bool

    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            let mood = CompanionLog.mood(level)
            let brass = level > 0.2 ? Color(red: 0.72, green: 0.57, blue: 0.18) : Color(red: 0.45, green: 0.43, blue: 0.39)
            let glass = Color(red: 1, green: 0.95, blue: 0.77).opacity(0.25 + 0.6 * level)
            let flicker: Double = 1 + 0.06 * sin(phase * 7) + 0.03 * sin(phase * 13)
            let flameH: CGFloat = h * CGFloat((0.08 + 0.26 * level) * flicker)
            let sway: CGFloat = w * 0.015 * CGFloat(sin(phase * 5))
            ZStack {
                // Halo: stronger as the lantern fills.
                Circle().fill(RadialGradient(colors: [Color(red: 1, green: 0.85, blue: 0.5).opacity(0.55 * level), .clear], center: .center,
                                             startRadius: 0, endRadius: w * 0.6))
                    .frame(width: w * 1.2, height: w * 1.2).position(x: w / 2, y: h * 0.55)
                // Handle and cap.
                Path { p in
                    p.addArc(center: CGPoint(x: w / 2, y: h * 0.12), radius: w * 0.12, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
                }
                .stroke(brass, lineWidth: w * 0.035)
                Path { p in
                    p.move(to: CGPoint(x: w * 0.3, y: h * 0.24)); p.addLine(to: CGPoint(x: w * 0.7, y: h * 0.24))
                    p.addLine(to: CGPoint(x: w * 0.6, y: h * 0.13)); p.addLine(to: CGPoint(x: w * 0.4, y: h * 0.13)); p.closeSubpath()
                }
                .fill(brass)
                // Body.
                RoundedRectangle(cornerRadius: w * 0.1, style: .continuous).fill(brass).frame(width: w * 0.5, height: h * 0.6).position(x: w / 2, y: h * 0.55)
                RoundedRectangle(cornerRadius: w * 0.07, style: .continuous).fill(glass).frame(width: w * 0.38, height: h * 0.5).position(x: w / 2, y: h * 0.55)
                // Flame.
                Ellipse()
                    .fill(LinearGradient(colors: [Color(red: 1, green: 0.93, blue: 0.6), Color(red: 0.96, green: 0.6, blue: 0.2)], startPoint: .top, endPoint: .bottom))
                    .frame(width: w * 0.12 + w * 0.06 * level, height: flameH)
                    .position(x: w / 2 + sway, y: h * 0.74 - flameH / 2)
                    .opacity(level > 0.05 ? 1 : 0)
                Face(mood: mood, blink: blink, ink: Color(red: 0.3, green: 0.22, blue: 0.08), size: w * 0.36)
                    .position(x: w / 2, y: h * 0.45)
                RoundedRectangle(cornerRadius: w * 0.03).fill(brass.opacity(0.85)).frame(width: w * 0.42, height: h * 0.06).position(x: w / 2, y: h * 0.88)
            }
        }
        .aspectRatio(0.8, contentMode: .fit)
        .accessibilityHidden(true)
    }
}

/// The heart: a vessel of light filled to the level, its surface waving with the phase and tilting with the phone.
private struct HeartFigure: View {
    let level: Double
    let phase: Double
    let tilt: Double
    let blink: Bool

    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            let mood = CompanionLog.mood(level)
            let red = level > 0.2 ? Color(red: 0.85, green: 0.33, blue: 0.31) : Color(red: 0.72, green: 0.62, blue: 0.6)
            ZStack {
                Circle().fill(RadialGradient(colors: [Color(red: 0.95, green: 0.6, blue: 0.56).opacity(0.5 * level), .clear], center: .center,
                                             startRadius: 0, endRadius: w * 0.6))
                    .frame(width: w * 1.2, height: w * 1.2).position(x: w / 2, y: h / 2)
                HeartShape().fill(Color(red: 0.97, green: 0.88, blue: 0.86))
                LiquidShape(level: level, phase: phase, tilt: tilt).fill(red).clipShape(HeartShape())
                HeartShape().stroke(Color.white.opacity(0.6), lineWidth: w * 0.015)
                Face(mood: mood, blink: blink, ink: Color(red: 0.35, green: 0.08, blue: 0.08), size: w * 0.46)
                    .position(x: w / 2, y: h * 0.42)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }
}

struct HeartShape: Shape {
    func path(in r: CGRect) -> Path {
        let w = r.width, h = r.height
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: r.minX + x * w, y: r.minY + y * h) }
        var path = Path()
        path.move(to: p(0.5, 0.9))
        path.addCurve(to: p(0.06, 0.34), control1: p(0.2, 0.7), control2: p(0.06, 0.52))
        path.addCurve(to: p(0.5, 0.22), control1: p(0.06, 0.08), control2: p(0.4, 0.06))
        path.addCurve(to: p(0.94, 0.34), control1: p(0.6, 0.06), control2: p(0.94, 0.08))
        path.addCurve(to: p(0.5, 0.9), control1: p(0.94, 0.52), control2: p(0.8, 0.7))
        path.closeSubpath()
        return path
    }
}

/// The liquid inside the heart: filled from the bottom to `level`, a gentle wave on top, tilted by `tilt`.
struct LiquidShape: Shape {
    var level: Double
    var phase: Double
    var tilt: Double

    var animatableData: AnimatablePair<Double, AnimatablePair<Double, Double>> {
        get { AnimatablePair(level, AnimatablePair(phase, tilt)) }
        set { level = newValue.first; phase = newValue.second.first; tilt = newValue.second.second }
    }

    func path(in r: CGRect) -> Path {
        // The heart's inside runs from about 0.12 to 0.9 of its height.
        let top: CGFloat = r.minY + r.height * CGFloat(0.9 - 0.78 * min(1, max(0, level)))
        let slope: Double = tan(max(-0.6, min(0.6, tilt)))
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.maxY))
        let steps = 24
        for i in 0...steps {
            let x = r.minX + r.width * CGFloat(i) / CGFloat(steps)
            let angle: Double = phase * 3 + Double(i) / Double(steps) * .pi * 2
            let wave: CGFloat = r.height * 0.025 * CGFloat(sin(angle))
            let y: CGFloat = top + CGFloat(slope) * (x - r.midX) + wave
            p.addLine(to: CGPoint(x: x, y: y))
        }
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
        p.closeSubpath()
        return p
    }
}
