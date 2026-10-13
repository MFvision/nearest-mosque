import NMCore
import SwiftUI

// The iPhone's landscape (App/Support/Sky.swift) on the wrist: the sky of the prayer period, stars at night,
// drifting clouds, the horizon glow, ridges, a lake and a mosque on the horizon, with the sun or the moon
// where it is in the sky now. Plain black when the screen dims (always on), so it never burns battery there.

/// What App/Support/Sky.swift and Art.swift need from the iPhone's theme.
enum Theme {
    static let gold = Color(hex: 0xD4A843)
    static let goldDeep = Color(hex: 0xB8922F)
    static let navy = Color(hex: 0x1A4D6E)
}

extension Color {
    init(hex: UInt32) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: 1)
    }
}

/// The page background: re-evaluated each minute, so the sky turns with the day.
struct WatchSkyBackground: View {
    @Environment(WatchModel.self) private var model
    @Environment(\.isLuminanceReduced) private var dimmed

    var body: some View {
        if dimmed {
            Color.black
        } else {
            TimelineView(.everyMinute) { tl in
                let now = tl.date
                let days = model.days(now: now)
                let today = days.count == 3 ? days[1] : nil
                let current = PrayerEvent.allCases.last { (today?[$0] ?? .distantFuture) <= now } ?? (days.first?[.isha] != nil ? .isha : nil)
                var period = model.state == nil ? SkyPeriod.night : SkyPeriod.at(now, today: today, current: current)
                let arc = model.state == nil ? nil : DayArc.at(now, days: days)
                var fraction = arc?.fraction ?? 0.5
                var isDay = arc?.isDay ?? false
                #if DEBUG
                // Screenshots: `-demoSky isha` (any SkyPeriod) draws that sky with its sun or moon.
                if let demo = UserDefaults.standard.string(forKey: "demoSky").flatMap(SkyPeriod.init(rawValue:)) {
                    period = demo
                    isDay = [.sunrise, .day, .asr].contains(demo)
                    fraction = isDay ? 0.62 : 0.3
                }
                #endif
                let sky = Sky.of(period)
                SkyBackdrop(sky: sky, horizon: 0.8, skyline: true, orb: AnyView(SkyOrb(fraction: fraction, isDay: isDay, sky: sky)))
                    .overlay {
                        // Keeps text legible over the brightest part of the sky.
                        LinearGradient(colors: [.black.opacity(0.35), .clear, .black.opacity(0.25)], startPoint: .top, endPoint: .bottom)
                    }
                    .animation(.easeInOut(duration: 1.2), value: sky)
            }
        }
    }
}

/// The sun by day, the moon by night, on an arc from the horizon: where it is now between sunrise and Maghrib
/// (or Maghrib and sunrise). The moon breathes a soft light.
struct SkyOrb: View {
    let fraction: Double
    let isDay: Bool
    let sky: Sky
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shine = false

    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            let f = fraction
            let horizonY = h * 0.8
            let x = w * (0.12 + 0.76 * f)
            let y = horizonY - CGFloat(sin(Double.pi * f)) * h * 0.52 - h * 0.04
            let day = isDay
            let d = w * (day ? 0.15 : 0.13)
            ZStack {
                // Halo.
                Circle()
                    .fill(RadialGradient(colors: [(day ? sky.glow : Color(hex: 0xDCE6FF)).opacity(day ? 0.55 : 0.4), .clear],
                                         center: .center, startRadius: 0, endRadius: d * 2.4))
                    .frame(width: d * 4.8, height: d * 4.8)
                    .scaleEffect(shine ? 1.12 : 0.94)
                if day {
                    Circle()
                        .fill(RadialGradient(colors: [.white, Color(hex: 0xFFE7A8), sky.glow], center: .center, startRadius: 0, endRadius: d * 0.6))
                        .frame(width: d, height: d)
                } else {
                    // A crescent: the moon with the night sky over part of it.
                    Circle()
                        .fill(RadialGradient(colors: [Color(hex: 0xFFFBEA), Color(hex: 0xE9E2C6)], center: .center, startRadius: 0, endRadius: d * 0.55))
                        .frame(width: d, height: d)
                        .mask {
                            Circle().frame(width: d, height: d)
                                .overlay(Circle().frame(width: d * 0.86, height: d * 0.86).offset(x: d * 0.3, y: -d * 0.12).blendMode(.destinationOut))
                                .compositingGroup()
                        }
                        .shadow(color: Color(hex: 0xFFF6D5).opacity(shine ? 0.9 : 0.5), radius: shine ? 8 : 4)
                }
            }
            .position(x: x, y: y)
        }
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 3.6).repeatForever(autoreverses: true)) { shine = true }
        }
    }
}

/// A translucent card over the sky.
struct WatchCard: ViewModifier {
    var radius: CGFloat = 16
    func body(content: Content) -> some View {
        content
            .padding(10)
            .background(.ultraThinMaterial.opacity(0.9), in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(.white.opacity(0.12), lineWidth: 0.5))
    }
}

extension View {
    func watchCard(radius: CGFloat = 16) -> some View { modifier(WatchCard(radius: radius)) }

    /// The living sky behind a page.
    func watchSky() -> some View {
        containerBackground(for: .tabView) { WatchSkyBackground() }
    }
}
