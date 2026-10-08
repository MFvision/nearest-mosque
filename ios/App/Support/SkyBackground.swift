import SwiftUI

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
