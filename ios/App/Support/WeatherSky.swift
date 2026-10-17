import NMCore
import SwiftUI

// "Sky follows the weather" (opt-in): the chosen city's weather drawn on the time-of-day sky. The sky is veiled
// toward the condition's grey, the glow, stars and cloud band are scaled (WeatherLook, the same numbers on every
// platform), and a few quiet layers are added: an overcast deck, rain streaks or snow, a low fog band and, during a
// thunderstorm, a rare soft glow. Reduce Motion and Low Power Mode give one still frame with no lightning, and Dim
// Flashing Lights turns the lightning off. Everything is decorative: it is drawn inside SkyBackdrop, which is
// hidden from VoiceOver, never mirrored and never takes touches. iPhone, iPad, Mac and Vision Pro only (TV and
// Watch build Sky.swift without this file).

/// Colours from shared/design/tokens.json "weather" (tools/check_tokens.py checks this file uses only token colours).
enum WeatherTokens {
    /// The grey each sky stop is mixed toward (nil for a clear sky).
    static func veil(_ c: WeatherCondition, dark: Bool) -> Color? {
        switch c {
        case .clear: return nil
        case .partlyCloudy, .cloudy: return dark ? Color(hex: 0x2B3440) : Color(hex: 0xD9DEE4)
        case .rain: return dark ? Color(hex: 0x25303B) : Color(hex: 0xCDD5DE)
        case .thunderstorm: return dark ? Color(hex: 0x1F2630) : Color(hex: 0xC4CCD6)
        case .fog: return dark ? Color(hex: 0x3A434D) : Color(hex: 0xE3E6EA)
        case .snow: return dark ? Color(hex: 0x3B4656) : Color(hex: 0xE6EBF1)
        }
    }

    static let streakDark = Color(hex: 0xC9D6E3)
    static let streakLight = Color(hex: 0x4A5A66)
    static let flake = Color(hex: 0xFFFFFF)
    static let fogDark = Color(hex: 0x3A434D)
    static let fogLight = Color(hex: 0xE3E6EA)

    static func streak(dark: Bool) -> Color { dark ? streakDark.opacity(0.16) : streakLight.opacity(0.14) }
    static func fog(dark: Bool) -> Color { dark ? fogDark : fogLight }
    /// The overcast deck: a grey a little lighter than the veiled night and day skies in dark mode (so white text
    /// keeps 4.5:1 over it), white on the light skies.
    static func deck(dark: Bool) -> Color { dark ? fogDark : flake }
}

extension WeatherCondition {
    /// The SF Symbol for the condition, with the night variant when the sky has stars (the weather line on the prayer
    /// screen, the onboarding's preview).
    func symbolName(night: Bool) -> String {
        switch self {
        case .clear: return night ? "moon.stars.fill" : "sun.max.fill"
        case .partlyCloudy: return night ? "cloud.moon.fill" : "cloud.sun.fill"
        case .cloudy: return "cloud.fill"
        case .rain: return "cloud.rain.fill"
        case .thunderstorm: return "cloud.bolt.rain.fill"
        case .fog: return "cloud.fog.fill"
        case .snow: return "cloud.snow.fill"
        }
    }
}

extension Sky {
    /// This sky under the weather: every stop mixed toward the condition's veil (nil: today's sky).
    func under(_ w: WeatherNow?) -> Sky {
        guard let w, let veil = WeatherTokens.veil(w.symbol.condition, dark: dark) else { return self }
        return veiled(veil, by: WeatherLook.of(w.symbol, night: period.hasStars).veilMix)
    }
}

/// The weather's layers between the cloud band and the ridges. `still`: Reduce Motion or Low Power Mode (one frame,
/// no lightning). `flashes`: lightning allowed (off with Dim Flashing Lights).
struct WeatherLayers: View {
    let w: WeatherNow
    let look: WeatherLook
    let dark: Bool
    /// Horizon height as a fraction of the screen (as in SkyBackdrop).
    let horizon: CGFloat
    var still = false
    var flashes = true

    private var lightning: Bool { look.lightning && flashes && !still }
    private var falling: Bool { look.rainStreaks > 0 || look.snowFlakes > 0 }

    var body: some View {
        GeometryReader { geo in
            if still {
                layers(size: geo.size, t: 0)
            } else {
                // 30 frames a second for rain, snow and the lightning; the deck alone only drifts.
                TimelineView(.animation(minimumInterval: falling || lightning ? 1.0 / 30 : 1.0 / 10)) { tl in
                    layers(size: geo.size, t: tl.date.timeIntervalSinceReferenceDate)
                }
            }
        }
        // A new condition fades in over the old one (SkyBackground animates the change).
        .id(w.symbol)
        .transition(.opacity)
        .allowsHitTesting(false)
    }

    @ViewBuilder private func layers(size: CGSize, t: Double) -> some View {
        let y0 = size.height * horizon
        ZStack(alignment: .topLeading) {
            if look.deckOpacity > 0 {
                OvercastDeck(color: WeatherTokens.deck(dark: dark), horizonY: y0, t: t)
                    .opacity(look.deckOpacity)
            }
            if look.rainStreaks > 0 {
                RainLayer(count: look.rainStreaks, color: WeatherTokens.streak(dark: dark), horizonY: y0, t: t)
                    .mask { PrecipitationMask(horizon: horizon) }
            }
            if look.snowFlakes > 0 {
                SnowLayer(count: look.snowFlakes, dark: dark, horizonY: y0, t: t)
                    .mask { SnowMask(horizon: horizon) }
            }
            if lightning {
                LightningGlow(peak: dark ? 0.10 : 0.06, t: t)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

/// Precipitation is faint under the header text: 35% at the very top, full from 0.6 of the way to the horizon.
private struct PrecipitationMask: View {
    let horizon: CGFloat
    var body: some View {
        LinearGradient(stops: [.init(color: .black.opacity(0.35), location: 0), .init(color: .black, location: max(0.05, 0.6 * horizon))],
                       startPoint: .top, endPoint: .bottom)
    }
}

/// Flakes fade harder under the header text, so none reads as a star or as a letter's dot beside it: 10% at the very
/// top, 30% at 0.6 of the way to the horizon, full only at the horizon (as Android's snowMask).
private struct SnowMask: View {
    let horizon: CGFloat
    var body: some View {
        LinearGradient(stops: [.init(color: .black.opacity(0.1), location: 0),
                               .init(color: .black.opacity(0.3), location: max(0.03, 0.6 * horizon)),
                               .init(color: .black, location: max(0.05, horizon))],
                       startPoint: .top, endPoint: .bottom)
    }
}

/// Deterministic pseudo-random numbers in 0..<1 (the same drops and puffs on every launch).
private struct Seeded {
    private var s: UInt64
    init(_ seed: UInt64) { s = seed &* 6364136223846793005 &+ 1442695040888963407 }
    mutating func next() -> Double {
        s = s &* 6364136223846793005 &+ 1442695040888963407
        return Double((s >> 33) % 10_000) / 10_000
    }
}

/// v wrapped into 0..<m.
private func wrap(_ v: Double, _ m: Double) -> Double {
    let r = v.truncatingRemainder(dividingBy: m)
    return r < 0 ? r + m : r
}

/// Seven large, faint puffs over the upper sky, drifting slowly sideways (240 s each way).
private struct OvercastDeck: View {
    let color: Color
    let horizonY: CGFloat
    let t: Double
    private static let puffs: [(x: CGFloat, y: CGFloat, r: CGFloat, squash: CGFloat, a: Double)] = [
        (0.06, 0.16, 0.30, 0.42, 0.22), (0.30, 0.08, 0.34, 0.38, 0.18), (0.52, 0.30, 0.28, 0.45, 0.26),
        (0.74, 0.12, 0.32, 0.40, 0.20), (0.96, 0.26, 0.30, 0.44, 0.24), (0.20, 0.42, 0.26, 0.46, 0.30),
        (0.84, 0.46, 0.27, 0.42, 0.28),
    ]

    var body: some View {
        Canvas { ctx, size in
            let w = size.width
            // 0 at rest, 1 at the far end of the drift: -0.1 w to -0.5 w, as the cloud band.
            let phase = 0.5 - 0.5 * cos(2 * Double.pi * t / 480)
            let dx = -w * (0.1 + 0.4 * CGFloat(phase))
            let span = w * 1.7
            for p in Self.puffs {
                var c = ctx
                c.translateBy(x: dx + p.x * span, y: p.y * horizonY)
                c.scaleBy(x: 1, y: p.squash)
                let r = p.r * w
                c.fill(Path(ellipseIn: CGRect(x: -r, y: -r, width: 2 * r, height: 2 * r)),
                       with: .radialGradient(Gradient(colors: [color.opacity(p.a), color.opacity(0)]), center: .zero, startRadius: 0, endRadius: r))
            }
        }
    }
}

/// Thin slanted streaks (12°) falling about a quarter of the screen's height a second, above the horizon only.
private struct RainLayer: View {
    let count: Int
    let color: Color
    let horizonY: CGFloat
    let t: Double

    var body: some View {
        Canvas { ctx, size in
            let w = Double(size.width), h = Double(size.height), top = Double(horizonY)
            guard top > 0, w > 0 else { return }
            let slant = tan(12 * Double.pi / 180)
            var rng = Seeded(11)
            var path = Path()
            for _ in 0..<count {
                let x0 = rng.next(), phase = rng.next()
                let len = 12 + 8 * rng.next()
                let speed = 0.25 * h * (0.85 + 0.3 * rng.next())
                // The head falls from just above the top to the horizon, then starts again; it drifts sideways
                // along the slant (wrapping round the screen's width).
                let y = wrap(phase * (top + len) + t * speed, top + len) - len
                let x = wrap(x0 * (w + 40) + y * slant, w + 40) - 20
                path.move(to: CGPoint(x: x, y: y))
                path.addLine(to: CGPoint(x: x - len * slant, y: y - len))
            }
            ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        }
    }
}

/// Small flakes falling slowly (about 4% of the screen's height a second) with a gentle sway.
private struct SnowLayer: View {
    let count: Int
    let dark: Bool
    let horizonY: CGFloat
    let t: Double

    var body: some View {
        Canvas { ctx, size in
            let w = Double(size.width), h = Double(size.height), top = Double(horizonY)
            guard top > 0, w > 0 else { return }
            var rng = Seeded(23)
            var flakes = Path()
            var rims = Path()
            for _ in 0..<count {
                let x0 = rng.next(), phase = rng.next()
                let r = 1 + 1.2 * rng.next()
                let speed = 0.04 * h * (0.8 + 0.4 * rng.next())
                let sway = 6 + 8 * rng.next(), period = 4 + 3 * rng.next(), swayPhase = rng.next()
                let y = wrap(phase * (top + 2 * r) + t * speed, top + 2 * r) - r
                let x = x0 * w + sway * sin(2 * Double.pi * (t / period + swayPhase))
                flakes.addEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
                rims.addEllipse(in: CGRect(x: x - r - 0.5, y: y - r - 0.5, width: 2 * r + 1, height: 2 * r + 1))
            }
            // On the pale light skies white flakes alone vanish: a faint grey rim and a little more white. On the dark
            // skies a pale blue-grey, not the white of the text beside them.
            if !dark { ctx.fill(rims, with: .color(WeatherTokens.streakLight.opacity(0.18))) }
            ctx.fill(flakes, with: .color(dark ? WeatherTokens.streakDark.opacity(0.55) : WeatherTokens.flake.opacity(0.85)))
        }
    }
}

/// Thunderstorm: now and then (every 9 to 16 s) a soft white glow from the top of the sky, 80 ms in and 700 ms out.
/// Never drawn with Reduce Motion, Low Power Mode or Dim Flashing Lights.
private struct LightningGlow: View {
    /// Peak opacity: 0.10 on the dark skies, 0.06 on the light ones.
    let peak: Double
    let t: Double
    static let cycle = 12.5

    /// 0...1 brightness at time t. Each 12.5 s cycle has one flash at a random offset of 0-3.5 s, so flashes are
    /// 9 to 16 s apart.
    static func level(at t: Double) -> Double {
        let n = (t / cycle).rounded(.down)
        var rng = Seeded(UInt64(bitPattern: Int64(n)) &+ 101)
        let start = n * cycle + 3.5 * rng.next()
        let dt = t - start
        if dt < 0 || dt > 0.78 { return 0 }
        if dt < 0.08 { return dt / 0.08 }
        let f = 1 - (dt - 0.08) / 0.7
        return f * f
    }

    var body: some View {
        let a = peak * Self.level(at: t)
        GeometryReader { geo in
            if a > 0.001 {
                RadialGradient(colors: [WeatherTokens.flake.opacity(a), WeatherTokens.flake.opacity(0)],
                               center: .top, startRadius: 0, endRadius: max(geo.size.width, geo.size.height) * 0.6)
            }
        }
    }
}

/// Low fog lying on the horizon (drawn over the ridges and the lake): 0.22 of the screen tall, at most 45% opaque.
struct FogBand: View {
    let dark: Bool
    /// WeatherLook.fogBand (0...1).
    let strength: Double
    let horizon: CGFloat

    var body: some View {
        GeometryReader { geo in
            let h = geo.size.height, band = h * 0.22
            let a = 0.45 * min(1, max(0, strength))
            let c = WeatherTokens.fog(dark: dark)
            LinearGradient(colors: [c.opacity(0), c.opacity(a), c.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(width: geo.size.width, height: band)
                .offset(y: h * horizon - band / 2)
        }
        .transition(.opacity)
        .allowsHitTesting(false)
    }
}
