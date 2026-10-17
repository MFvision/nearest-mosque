import NMCore
import SwiftUI

/// Wraps a tab's content: the sky for the current prayer period behind it (re-evaluated each minute)
/// and the sky in the environment for glass tints. With "Sky follows the weather" on, the sky also shows the
/// prayer city's weather (WeatherSky.swift); the veiled sky goes into the environment, so the glass follows it.
struct SkyBackground: ViewModifier {
    @Environment(\.appModel) private var model
    @Environment(\.colorScheme) private var scheme
    var horizon: CGFloat = 0.64
    var skyline = false

    func body(content: Content) -> some View {
        TimelineView(.everyMinute) { ctx in
            let period = model.skyPeriod(now: ctx.date)
            let w = model.skyWeather(now: ctx.date)
            let sky = SkyScene.sky(period, weather: w, dark: scheme == .dark)
            content
                .environment(\.sky, sky)
                .background {
                    SkyScene(period: period, weather: w, horizon: horizon, skyline: skyline)
                        .ignoresSafeArea()
                }
                .animation(.easeInOut(duration: 1.2), value: sky)
        }
    }
}

/// One period's sky with the weather on it (nil: today's sky), as SkyBackground draws it behind every tab; the
/// onboarding draws it too, with a chosen period and the weather step's preview.
struct SkyScene: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityDimFlashingLights) private var dimFlashingLights
    let period: SkyPeriod
    let weather: WeatherNow?
    var horizon: CGFloat = 0.64
    var skyline = false
    /// How long the weather takes to fade from one condition to the next.
    var weatherFade: Double = 2
    /// One still frame of the weather (screenshots); Reduce Motion and Low Power Mode give one anyway.
    var still = false

    /// The sky for the environment (glass tints), veiled by the weather.
    static func sky(_ period: SkyPeriod, weather: WeatherNow?, dark: Bool) -> Sky {
        Sky.of(period, dark: dark).under(weather)
    }

    var body: some View {
        let look = WeatherLook.of(weather?.symbol, night: period.hasStars)
        let sky = Self.sky(period, weather: weather, dark: scheme == .dark)
        // Reduce Motion or Low Power Mode: one still frame of the weather, never a flash.
        let frozen = still || reduceMotion || ProcessInfo.processInfo.isLowPowerModeEnabled
        let layers: AnyView? = weather.map { AnyView(WeatherLayers(w: $0, look: look, dark: sky.dark, horizon: horizon,
                                                                   still: frozen, flashes: !dimFlashingLights)) }
        let mist: AnyView? = look.fogBand > 0 ? AnyView(FogBand(dark: sky.dark, strength: look.fogBand, horizon: horizon)) : nil
        SkyBackdrop(sky: sky, horizon: horizon, skyline: skyline, glowStrength: look.glowStrength,
                    starOpacity: look.starOpacity, cloudOpacity: look.cloudOpacity, weather: layers, mist: mist)
            // The weather fades in and out, and from one condition to the next.
            .animation(.easeInOut(duration: weatherFade), value: weather?.symbol)
    }
}

extension View {
    func skyBackground(horizon: CGFloat = 0.64, skyline: Bool = false) -> some View {
        modifier(SkyBackground(horizon: horizon, skyline: skyline))
    }
}
