import NMCore
import SwiftUI

/// The sky follows the prayer period in progress. Colours mirror shared/design/tokens.json ("sky").
enum SkyPeriod: String, CaseIterable, Sendable {
    case night, fajr, sunrise, day, asr, maghrib, isha

    var hasStars: Bool { self == .night || self == .isha || self == .fajr }

    static func at(_ now: Date, today: DaySchedule?, current: PrayerEvent?) -> SkyPeriod {
        switch current {
        case .fajr: return .fajr
        case .sunrise:
            if let s = today?[.sunrise], now.timeIntervalSince(s) < 3600 { return .sunrise }
            return .day
        case .dhuhr: return .day
        case .asr: return .asr
        case .maghrib: return .maghrib
        case .isha: return .isha
        case nil: return .night
        }
    }
}

struct Sky: Equatable {
    let period: SkyPeriod
    let top: Color
    let mid: Color
    let glow: Color
    let low: Color
    var dark = true

    static func of(_ p: SkyPeriod, dark: Bool = true) -> Sky {
        if !dark {
            // Light appearance (tokens.json "skyLight"): daytime pastels of the same periods.
            switch p {
            case .night: return Sky(period: p, top: Color(hex: 0xDCE4F0), mid: Color(hex: 0xE8EDF5), glow: Color(hex: 0xC3D0E6), low: Color(hex: 0xF5F7FB), dark: false)
            case .fajr: return Sky(period: p, top: Color(hex: 0xE2DEEF), mid: Color(hex: 0xEEE5EE), glow: Color(hex: 0xF2C4B4), low: Color(hex: 0xFBF6F3), dark: false)
            case .sunrise: return Sky(period: p, top: Color(hex: 0xF7E3C4), mid: Color(hex: 0xFBEEDB), glow: Color(hex: 0xF5C47A), low: Color(hex: 0xFFF9F0), dark: false)
            case .day: return Sky(period: p, top: Color(hex: 0xD6E7F5), mid: Color(hex: 0xEAF3FA), glow: Color(hex: 0xFBEBC0), low: Color(hex: 0xFFFDF7), dark: false)
            case .asr: return Sky(period: p, top: Color(hex: 0xF1DDB3), mid: Color(hex: 0xF7EBD2), glow: Color(hex: 0xF0CB7E), low: Color(hex: 0xFFFBF2), dark: false)
            case .maghrib: return Sky(period: p, top: Color(hex: 0xF3D2C2), mid: Color(hex: 0xF9E3D5), glow: Color(hex: 0xF2A47D), low: Color(hex: 0xFFF6EF), dark: false)
            case .isha: return Sky(period: p, top: Color(hex: 0xDEE1EF), mid: Color(hex: 0xEBEDF6), glow: Color(hex: 0xC6CBE4), low: Color(hex: 0xF8F8FC), dark: false)
            }
        }
        switch p {
        case .night: return Sky(period: p, top: Color(hex: 0x081B29), mid: Color(hex: 0x12304A), glow: Color(hex: 0x2C4C7A), low: Color(hex: 0x0B1A2C))
        case .fajr: return Sky(period: p, top: Color(hex: 0x141E3C), mid: Color(hex: 0x3A3566), glow: Color(hex: 0xC98A8A), low: Color(hex: 0x1E2242))
        case .sunrise: return Sky(period: p, top: Color(hex: 0x1C3F7A), mid: Color(hex: 0x345A99), glow: Color(hex: 0xF3C98B), low: Color(hex: 0x26365E))
        case .day: return Sky(period: p, top: Color(hex: 0x1C3F7A), mid: Color(hex: 0x2F5590), glow: Color(hex: 0xF7E0A8), low: Color(hex: 0x22406E))
        case .asr: return Sky(period: p, top: Color(hex: 0x173F5A), mid: Color(hex: 0x23606F), glow: Color(hex: 0xF2D27A), low: Color(hex: 0x1D3E4E))
        case .maghrib: return Sky(period: p, top: Color(hex: 0x1B1F45), mid: Color(hex: 0x4A2F5E), glow: Color(hex: 0xF08A5D), low: Color(hex: 0x2A1E3E))
        case .isha: return Sky(period: p, top: Color(hex: 0x0A1430), mid: Color(hex: 0x16264D), glow: Color(hex: 0x3A4E86), low: Color(hex: 0x0B1530))
        }
    }

    /// Tint for glass over this sky, so text stays legible where the horizon is bright.
    var glassTint: Color { dark ? low.opacity(0.35) : Color.white.opacity(0.45) }
    /// How much the ridges and lake are darkened (much less on the light skies).
    var shade: Double { dark ? 1 : 0.35 }
    /// Stars only on the night skies.
    var showsStars: Bool { dark && period.hasStars }
}

/// Light on the glass from where the sun (or the moon) is: pink-gold at Fajr, gold by day, orange at
/// Maghrib, cool moonlight at night. `angle` is degrees clockwise from the top of the view (-90 = the
/// left horizon, 90 = the right), following the sun along the day in the reading direction.
struct SkyLight: Equatable {
    let color: Color
    let angle: Double
    let strength: Double

    static func of(_ arc: DayArc?, period: SkyPeriod, sky: Sky, rtl: Bool) -> SkyLight {
        let f = arc.map { rtl ? 1 - $0.fraction : $0.fraction } ?? 0.5
        let angle = -90 + 180 * f
        if let arc, !arc.isDay {
            return SkyLight(color: sky.dark ? Color(hex: 0xC9D8FF) : Theme.navy, angle: angle, strength: sky.dark ? 0.7 : 0.35)
        }
        // The pale daytime skies of light mode need a deeper colour to show on the glass.
        let c = sky.dark ? sky.glow : sky.glow.mix(with: Theme.goldDeep, by: 0.55)
        switch period {
        case .fajr, .sunrise, .maghrib: return SkyLight(color: c, angle: angle, strength: 1)
        case .asr: return SkyLight(color: c, angle: angle, strength: 0.9)
        default: return SkyLight(color: c, angle: angle, strength: 0.8)
        }
    }

    /// A bright stretch of rim on the side facing the light, fading round the shape.
    func rim<S: Shape>(_ shape: S, width: CGFloat) -> some View {
        // Gradient stops round the shape from 12 o'clock, brightest at the light's angle (wrapping past 12).
        let c = (angle.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) / 360
        let stops: [Gradient.Stop] = (0...48).map { i in
            let t = Double(i) / 48
            let d = min(abs(t - c), 1 - abs(t - c))
            return .init(color: color.opacity(strength * max(0, 1 - d / 0.2)), location: t)
        }
        return shape
            .stroke(AngularGradient(stops: stops, center: .center, startAngle: .degrees(-90), endAngle: .degrees(270)), lineWidth: width)
            .blur(radius: 0.6)
            .allowsHitTesting(false)
    }

    /// A soft glow just inside the shape on the side facing the light.
    func sheen(size: CGFloat) -> some View {
        let r = Double.pi * angle / 180
        return Circle()
            .fill(RadialGradient(colors: [color.opacity(0.6 * strength), color.opacity(0)], center: .center, startRadius: 0, endRadius: size * 0.42))
            .frame(width: size * 0.84, height: size * 0.84)
            .offset(x: size * 0.36 * sin(r), y: -size * 0.36 * cos(r))
            .allowsHitTesting(false)
    }
}

private struct SkyKey: EnvironmentKey { static let defaultValue = Sky.of(.night) }

extension EnvironmentValues {
    var sky: Sky {
        get { self[SkyKey.self] }
        set { self[SkyKey.self] = newValue }
    }
}

/// Full-screen landscape behind the glass: gradient sky, stars at night, drifting clouds, a horizon
/// glow behind two mountain ridges and a calm lake. Decorative only (hidden from VoiceOver); motion
/// stops under Reduce Motion. Text never sits over the brightest glow (see check_tokens.py).
struct SkyBackdrop: View {
    let sky: Sky
    /// Horizon height as a fraction of the screen.
    var horizon: CGFloat = 0.64
    /// Draws a mosque silhouette on the horizon (Ask tab).
    var skyline = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drift = false
    @State private var twinkle = false

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height, y0 = h * horizon
            ZStack(alignment: .topLeading) {
                LinearGradient(stops: [
                    .init(color: sky.top, location: 0),
                    .init(color: sky.mid, location: horizon * 0.72),
                    .init(color: sky.mid.mix(with: sky.glow, by: 0.4), location: horizon),
                    .init(color: sky.low, location: min(1, horizon + 0.14)),
                    .init(color: sky.low, location: 1),
                ], startPoint: .top, endPoint: .bottom)

                if sky.showsStars {
                    StarField(seed: 7).opacity(twinkle ? 0.95 : 0.55)
                        .frame(width: w, height: y0 * 0.85)
                    StarField(seed: 31).opacity(twinkle ? 0.45 : 0.9)
                        .frame(width: w, height: y0 * 0.85)
                }

                // Horizon glow: an ellipse, faded to nothing well below the text area.
                Circle()
                    .fill(RadialGradient(colors: [sky.glow.opacity(0.95), sky.glow.opacity(0.4), sky.glow.opacity(0)],
                                         center: .center, startRadius: 0, endRadius: w * 0.7))
                    .frame(width: w * 1.4, height: w * 1.4)
                    .scaleEffect(x: 1, y: 0.42)
                    .position(x: w / 2, y: y0)

                // The cloud band is wider than the screen: as an overlay it never changes the stack's size
                // (an oversized child shifted every layer sideways and cut the ridges and lake with a hard edge).
                Color.clear.frame(width: w, height: h).overlay(alignment: .topLeading) {
                    CloudBand(tint: Color.white.mix(with: sky.glow, by: sky.dark ? 0.35 : 0.15))
                        .frame(width: w * 1.7, height: y0 * 0.55)
                        .offset(x: drift ? -w * 0.55 : -w * 0.1, y: y0 * 0.38)
                        .opacity(sky.showsStars ? 0.35 : 0.8)
                }

                Ridge(points: Ridge.far)
                    .fill(LinearGradient(colors: [sky.mid.mix(with: sky.glow, by: 0.22).opacity(0.85), sky.low], startPoint: .top, endPoint: .bottom))
                    .frame(width: w, height: h * 0.16)
                    .offset(y: y0 - h * 0.075)
                if skyline {
                    MosqueSkyline()
                        .fill(sky.low.mix(with: .black, by: 0.25 * sky.shade))
                        .frame(width: w * 0.62, height: h * 0.15)
                        .offset(x: w * 0.19, y: y0 - h * 0.13)
                }
                Ridge(points: Ridge.near)
                    .fill(sky.low.mix(with: .black, by: 0.18 * sky.shade))
                    .frame(width: w, height: h * 0.12)
                    .offset(y: y0 - h * 0.035)

                // Lake with a soft reflection of the glow.
                LinearGradient(colors: [sky.low.mix(with: sky.glow, by: 0.12), sky.low.mix(with: .black, by: 0.2 * sky.shade)], startPoint: .top, endPoint: .bottom)
                    .frame(width: w, height: max(0, h - y0 - h * 0.06))
                    .offset(y: y0 + h * 0.06)
                Circle()
                    .fill(RadialGradient(colors: [sky.glow.opacity(0.32), sky.glow.opacity(0)], center: .center, startRadius: 0, endRadius: w * 0.3))
                    .frame(width: w * 0.6, height: w * 0.6)
                    .scaleEffect(x: 1, y: 1.3)
                    .position(x: w / 2, y: y0 + h * 0.16)
                    .opacity(twinkle ? 1 : 0.75)
            }
            .frame(width: w, height: h, alignment: .topLeading)
            .clipped()
        }
        // Landscape art is the same in every language; right-to-left layout must not mirror its offsets.
        .environment(\.layoutDirection, .leftToRight)
        .drawingGroup()
        .accessibilityHidden(true)
        .allowsHitTesting(false)
        .onAppear { animate(reduceMotion) }
        .onChange(of: reduceMotion) { _, r in animate(r) }
    }

    private func animate(_ reduced: Bool) {
        guard !reduced else { drift = false; twinkle = false; return }
        withAnimation(.linear(duration: 140).repeatForever(autoreverses: true)) { drift = true }
        withAnimation(.easeInOut(duration: 3.2).repeatForever(autoreverses: true)) { twinkle = true }
    }
}

/// Soft cloud puffs drawn as radial gradients (no blur pass, cheap to move).
struct CloudBand: View {
    let tint: Color
    private static let puffs: [(x: CGFloat, y: CGFloat, r: CGFloat, squash: CGFloat, a: Double)] = [
        (0.08, 0.55, 0.10, 0.32, 0.20), (0.18, 0.62, 0.14, 0.28, 0.16), (0.30, 0.40, 0.09, 0.35, 0.14),
        (0.42, 0.70, 0.16, 0.25, 0.18), (0.55, 0.50, 0.12, 0.30, 0.15), (0.66, 0.66, 0.15, 0.26, 0.20),
        (0.78, 0.45, 0.10, 0.34, 0.13), (0.88, 0.60, 0.14, 0.28, 0.17), (0.97, 0.52, 0.09, 0.32, 0.12),
    ]

    var body: some View {
        Canvas { ctx, size in
            for p in Self.puffs {
                var c = ctx
                c.translateBy(x: p.x * size.width, y: p.y * size.height)
                c.scaleBy(x: 1, y: p.squash)
                let r = p.r * size.width
                c.fill(Path(ellipseIn: CGRect(x: -r, y: -r, width: 2 * r, height: 2 * r)),
                       with: .radialGradient(Gradient(colors: [tint.opacity(p.a), tint.opacity(0)]), center: .zero, startRadius: 0, endRadius: r))
            }
        }
    }
}

/// Deterministic star field (same stars on every launch).
struct StarField: View {
    let seed: UInt64
    var body: some View {
        Canvas { ctx, size in
            var s = seed &* 6364136223846793005 &+ 1442695040888963407
            func next() -> CGFloat {
                s = s &* 6364136223846793005 &+ 1442695040888963407
                return CGFloat((s >> 33) % 10_000) / 10_000
            }
            for _ in 0..<45 {
                let x = next() * size.width, y = next() * size.height, r = 0.5 + next() * 1.1
                let fade = 1 - Double(y / size.height) * 0.7
                ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)), with: .color(.white.opacity(fade)))
            }
        }
    }
}

/// Smooth mountain ridge through normalized points (x 0…1, y 0…1 within the frame), filled to the bottom.
struct Ridge: Shape {
    let points: [CGPoint]
    static let far: [CGPoint] = [
        .init(x: 0, y: 0.55), .init(x: 0.08, y: 0.42), .init(x: 0.17, y: 0.2), .init(x: 0.26, y: 0.38), .init(x: 0.36, y: 0.3),
        .init(x: 0.47, y: 0.5), .init(x: 0.58, y: 0.34), .init(x: 0.7, y: 0.12), .init(x: 0.8, y: 0.36), .init(x: 0.9, y: 0.28), .init(x: 1, y: 0.45),
    ]
    static let near: [CGPoint] = [
        .init(x: 0, y: 0.35), .init(x: 0.12, y: 0.55), .init(x: 0.24, y: 0.42), .init(x: 0.38, y: 0.7), .init(x: 0.52, y: 0.6),
        .init(x: 0.66, y: 0.75), .init(x: 0.8, y: 0.5), .init(x: 0.92, y: 0.62), .init(x: 1, y: 0.4),
    ]

    func path(in r: CGRect) -> Path {
        let pts = points.map { CGPoint(x: r.minX + $0.x * r.width, y: r.minY + $0.y * r.height) }
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.maxY))
        p.addLine(to: pts[0])
        for i in 1..<pts.count {
            let a = pts[i - 1], b = pts[i]
            p.addQuadCurve(to: CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2), control: a)
        }
        p.addLine(to: pts[pts.count - 1])
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
        p.closeSubpath()
        return p
    }
}

/// Wraps a tab's content: the sky for the current prayer period behind it (re-evaluated each minute)
/// and the sky in the environment for glass tints.
struct SkyBackground: ViewModifier {
    @Environment(\.appModel) private var model
    @Environment(\.colorScheme) private var scheme
    var horizon: CGFloat = 0.64
    var skyline = false

    func body(content: Content) -> some View {
        TimelineView(.everyMinute) { ctx in
            let sky = Sky.of(model.skyPeriod(now: ctx.date), dark: scheme == .dark)
            content
                .environment(\.sky, sky)
                .background { SkyBackdrop(sky: sky, horizon: horizon, skyline: skyline).ignoresSafeArea() }
                .animation(.easeInOut(duration: 1.2), value: sky)
        }
    }
}

extension View {
    func skyBackground(horizon: CGFloat = 0.64, skyline: Bool = false) -> some View {
        modifier(SkyBackground(horizon: horizon, skyline: skyline))
    }
}
