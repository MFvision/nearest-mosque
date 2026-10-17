import NMCore
import SwiftUI

/// Wraps a tab's content: the sky for the current prayer period behind it (re-evaluated each minute)
/// and the sky in the environment for glass tints. With "Sky follows the weather" on, the sky also shows the
/// prayer city's weather (WeatherSky.swift); the veiled sky goes into the environment, so the glass follows it.
struct SkyBackground: ViewModifier {
    @Environment(\.appModel) private var model
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityDimFlashingLights) private var dimFlashingLights
    var horizon: CGFloat = 0.64
    var skyline = false

    func body(content: Content) -> some View {
        TimelineView(.everyMinute) { ctx in
            let period = model.skyPeriod(now: ctx.date)
            let w = model.skyWeather(now: ctx.date)
            let look = WeatherLook.of(w?.symbol, night: period.hasStars)
            let sky = Sky.of(period, dark: scheme == .dark).under(w)
            // Reduce Motion or Low Power Mode: one still frame of the weather, never a flash.
            let still = reduceMotion || ProcessInfo.processInfo.isLowPowerModeEnabled
            let layers: AnyView? = w.map { AnyView(WeatherLayers(w: $0, look: look, dark: sky.dark, horizon: horizon,
                                                                 still: still, flashes: !dimFlashingLights)) }
            let mist: AnyView? = look.fogBand > 0 ? AnyView(FogBand(dark: sky.dark, strength: look.fogBand, horizon: horizon)) : nil
            content
                .environment(\.sky, sky)
                .background {
                    SkyBackdrop(sky: sky, horizon: horizon, skyline: skyline, glowStrength: look.glowStrength,
                                starOpacity: look.starOpacity, cloudOpacity: look.cloudOpacity, weather: layers, mist: mist)
                        // The weather fades in and out, and from one condition to the next, over two seconds.
                        .animation(.easeInOut(duration: 2), value: w?.symbol)
                        .ignoresSafeArea()
                }
                .animation(.easeInOut(duration: 1.2), value: sky)
        }
    }
}

extension View {
    func skyBackground(horizon: CGFloat = 0.64, skyline: Bool = false) -> some View {
        modifier(SkyBackground(horizon: horizon, skyline: skyline))
    }
}
