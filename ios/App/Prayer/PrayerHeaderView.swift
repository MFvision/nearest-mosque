import NMCore
import SwiftUI
import UIKit

/// Top of Prayer & Qibla, the middle of the three views: glass location pill and gear, the Qibla arc with
/// the logo at the top and the next prayer inside, lit by the time of day (the glass reflects the sun or
/// the moon from where it is). The logo's arrow and the gold dot on the arc point to the Qibla relative
/// to the top of the phone; when the phone faces it the logo glows. Without a live heading both are
/// north-up. Tapping anywhere on the arc opens the full Qibla view. Below: the nearest mosque.
struct PrayerHero: View {
    @Environment(AppModel.self) private var model
    @Environment(Localization.self) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorScheme) private var scheme
    @Environment(\.layoutDirection) private var direction
    /// The big time grows with the text-size setting (and still fits the arc: minimumScaleFactor).
    @ScaledMetric(relativeTo: .largeTitle) private var timeSize: CGFloat = 60
    let snap: PrayerSnapshot
    let compass: CompassState
    let aligned: Bool
    var onLocation: () -> Void
    var onSettings: () -> Void
    var onQibla: () -> Void
    /// Opens the nearest mosque (details, directions in a maps app, call, website).
    var onNearest: (RankedMosque) -> Void

    var body: some View {
        VStack(spacing: 6) {
            HStack {
                Color.clear.frame(width: 44, height: 44)
                Spacer()
                LocationPill(name: snap.location?.name ?? l10n.t("choose_city_title"), action: onLocation)
                Spacer()
                GlassIconButton(systemImage: "gearshape", label: l10n.t("settings"), action: onSettings)
            }
            .padding(.top, 4)

            QiblaArc(angle: dotAngle, mode: arcMode, aligned: aligned, light: light,
                     kaabaDistance: snap.qiblaDistance.map { l10n.t("qibla_distance", Format.distance($0, l10n: l10n)) },
                     nearest: model.nearestMosque.map { l10n.t("nearest_mosque_chip", Format.distance($0.distanceMeters, l10n: l10n)) },
                     onNearest: { if let n = model.nearestMosque { onNearest(n) } }) {
                arcContent
            }
            .contentShape(Rectangle())
            .onTapGesture { if snap.location != nil { onQibla() } }
            .accessibilityElement(children: .contain)
            .accessibilityAction(named: l10n.t("qibla_open_compass")) { if snap.location != nil { onQibla() } }

        }
        .foregroundStyle(Theme.ink)
    }

    private var light: SkyLight {
        SkyLight.of(snap.arc, period: snap.sky, sky: Sky.of(snap.sky, dark: scheme == .dark), rtl: direction == .rightToLeft)
    }

    private var arcMode: ArcMode {
        guard snap.qiblaBearing != nil else { return .none }
        if case .live = compass { return .live }
        return .northUp
    }

    private var dotAngle: Double {
        guard let b = snap.qiblaBearing else { return 0 }
        if case let .live(h, _, _) = compass { return Angles.relativeToQibla(qiblaBearingTrue: b, headingTrue: h) }
        return b
    }

    @ViewBuilder private var arcContent: some View {
        let zone = snap.location?.zone ?? .current
        VStack(spacing: 4) {
            if let next = snap.next {
                let name = l10n.t(Format.prayerKey(next.event))
                let time = Format.time(next.at, zone: zone, locale: l10n.locale)
                let remaining = PrayerCalculator.remaining(snap.now, next.at)
                VStack(spacing: 2) {
                    Text(name).font(.title2.weight(.medium)).foregroundStyle(Theme.ink.opacity(0.92))
                    Text(time).font(.system(size: timeSize, weight: .light)).monospacedDigit().minimumScaleFactor(0.5).lineLimit(1)
                        .contentTransition(.numericText())
                    Text(l10n.t("remaining_long", Format.remainingLong(remaining, locale: l10n.locale)))
                        .font(.callout).monospacedDigit().foregroundStyle(Theme.ink.opacity(0.85))
                        .contentTransition(reduceMotion ? .identity : .numericText(countsDown: true))
                }
                .accessibilityElement(children: .ignore)
                .accessibilityAddTraits(.isHeader)
                .accessibilityLabel(l10n.t("countdown_a11y", name, time, Format.countdown(remaining, locale: l10n.locale)))
            } else if snap.location == nil {
                Text(l10n.t("app_name")).font(.largeTitle.weight(.semibold))
            }
            if let b = snap.qiblaBearing {
                // Which way to turn (the logo's arrow shows it too); the angle from north only without a compass.
                Text(qiblaGuidance(compass, bearing: b, l10n: l10n)).font(.headline).multilineTextAlignment(.center)
                    .foregroundStyle(aligned ? Theme.accent : Theme.ink)
                    .padding(.top, 12).padding(.horizontal, 12)
                    .accessibilityAddTraits(.updatesFrequently)
                Text(l10n.t("sky_card_hint")).font(.caption).foregroundStyle(Theme.ink.opacity(0.7))
                    .accessibilityHidden(true)
            }
        }
    }
}

/// Glass capsule with the prayer location; opens the city picker.
struct LocationPill: View {
    @Environment(Localization.self) private var l10n
    let name: String
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "mappin.and.ellipse").font(.subheadline).accessibilityHidden(true)
                Text(name).font(.subheadline.weight(.semibold)).lineLimit(1)
                Image(systemName: "chevron.down").font(.caption.weight(.bold)).accessibilityHidden(true)
            }
            .foregroundStyle(Theme.ink)
            .padding(.horizontal, 16)
            .frame(minHeight: 44)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .glass(Capsule())
        .accessibilityHint(l10n.t("change_city_hint"))
    }
}

/// Large circle whose top is the phone's forward direction (live) or north (north-up). The disc sits
/// at the top; the gold dot sits at `angle` degrees clockwise from the top, reached along the arc by
/// the shortest path. The lower half fades out so content can sit inside. Geometry never mirrors.
enum ArcMode: Equatable { case live, northUp, none }

struct QiblaArc<Content: View>: View {
    let angle: Double
    let mode: ArcMode
    let aligned: Bool
    /// False when the caller already animates `angle` frame by frame (onboarding demo).
    var springs = true
    /// The time-of-day light reflected on the arc and the logo.
    var light: SkyLight?
    /// Small labels along the sides of the ring: the distance to the Kaaba (left) and the nearest mosque (right, tappable).
    var kaabaDistance: String?
    var nearest: String?
    var onNearest: (() -> Void)?
    @ViewBuilder var content: Content
    @Environment(Localization.self) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown: Double = 0

    init(angle: Double, mode: ArcMode, aligned: Bool, springs: Bool = true, light: SkyLight? = nil,
         kaabaDistance: String? = nil, nearest: String? = nil, onNearest: (() -> Void)? = nil, @ViewBuilder content: () -> Content) {
        self.angle = angle; self.mode = mode; self.aligned = aligned; self.springs = springs; self.light = light
        self.kaabaDistance = kaabaDistance; self.nearest = nearest; self.onNearest = onNearest; self.content = content()
    }

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let r = w * 0.43
            let discY = Theme.discSize / 2 + 6
            let c = CGPoint(x: w / 2, y: discY + r)
            ZStack(alignment: .top) {
                if let light {
                    // The glass catching the sun (or the moon): a soft glow on that side and a bright stretch of rim.
                    light.sheen(size: 2 * r).position(c)
                    light.rim(Circle(), width: 6)
                        .frame(width: 2 * r, height: 2 * r).position(c)
                        .mask(LinearGradient(stops: [.init(color: .black, location: 0), .init(color: .black, location: 0.45), .init(color: .clear, location: 0.8)],
                                             startPoint: .top, endPoint: .bottom))
                }
                ArcLayer(angle: shown, center: c, radius: r, mode: mode, aligned: aligned)
                LogoDisc(glow: aligned, arrow: mode == .none ? nil : shown, light: light).position(x: c.x, y: discY)
                if mode == .northUp {
                    Text(l10n.t("compass_north")).font(.caption.weight(.bold)).foregroundStyle(Theme.ink.opacity(0.85))
                        .position(x: c.x, y: discY + Theme.discSize / 2 + 14)
                }
                if let kaabaDistance {
                    CurvedSideLabel(text: kaabaDistance, icon: Image("LogoBody"), style: .caption2, color: Theme.ink.opacity(0.8),
                                    center: c, radius: r + 10, angle: 250)
                }
                if let nearest, let onNearest {
                    CurvedSideLabel(text: nearest, icon: Image("MosqueTab"), weight: .semibold, color: Theme.accent,
                                    center: c, radius: r + 10, angle: 110, action: onNearest)
                }
                // Content starts below the disc (never overlapping it), centred inside the circle.
                content
                    .frame(width: r * 1.7)
                    .padding(.top, discY + Theme.discSize / 2 + 26)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
            .environment(\.layoutDirection, .leftToRight)
        }
        .aspectRatio(1 / 1.0, contentMode: .fit)
        .frame(maxWidth: 520)
        .onAppear { shown = angle }
        .onChange(of: angle) { _, a in
            let next = Angles.shortestTarget(current: shown, target: a)
            if reduceMotion || !springs { shown = next } else { withAnimation(.interpolatingSpring(stiffness: 140, damping: 22)) { shown = next } }
        }
    }
}

/// Animatable drawing of the arc, the trail from the disc to the dot, and the dot.
private struct ArcLayer: View, Animatable {
    var angle: Double
    let center: CGPoint
    let radius: CGFloat
    let mode: ArcMode
    let aligned: Bool
    var animatableData: Double { get { angle } set { angle = newValue } }

    var body: some View {
        Canvas { ctx, size in
            let c = center, r = radius
            var circle = Path()
            circle.addEllipse(in: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
            ctx.stroke(circle, with: .linearGradient(Gradient(stops: [
                .init(color: Theme.ink.opacity(0.55), location: 0),
                .init(color: Theme.ink.opacity(0.22), location: 0.45),
                .init(color: Theme.ink.opacity(0.0), location: 0.8),
            ]), startPoint: CGPoint(x: c.x, y: c.y - r), endPoint: CGPoint(x: c.x, y: c.y + r)), lineWidth: 1.5)

            if aligned {
                for side in [-1.0, 1.0] {
                    var glow = Path()
                    glow.addArc(center: c, radius: r, startAngle: .degrees(-90), endAngle: .degrees(-90 + side * 38), clockwise: side < 0)
                    ctx.stroke(glow, with: .color(Theme.gold.opacity(0.9)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                }
            }
            guard mode != .none else { return }
            let rel = Angles.normalize180(angle)
            var trail = Path()
            trail.addArc(center: c, radius: r, startAngle: .degrees(-90), endAngle: .degrees(-90 + rel), clockwise: rel < 0)
            ctx.stroke(trail, with: .color(Theme.ink.opacity(0.75)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            let rad = angle * .pi / 180
            let m = CGPoint(x: c.x + r * sin(rad), y: c.y - r * cos(rad))
            ctx.fill(Path(ellipseIn: CGRect(x: m.x - 22, y: m.y - 22, width: 44, height: 44)),
                     with: .radialGradient(Gradient(colors: [Theme.gold.opacity(0.6), .clear]), center: m, startRadius: 0, endRadius: 22))
            ctx.fill(Path(ellipseIn: CGRect(x: m.x - 10, y: m.y - 10, width: 20, height: 20)), with: .color(Theme.gold))
            ctx.stroke(Path(ellipseIn: CGRect(x: m.x - 10, y: m.y - 10, width: 20, height: 20)), with: .color(Theme.ink.opacity(0.85)), lineWidth: 2)
        }
        .accessibilityHidden(true)
    }
}

/// The first of the three views: when the big header scrolls away, this bar takes its place at the top
/// (full width, on the sky, so the page never shows through). The logo's arrow keeps pointing to the
/// Qibla. Tapping it scrolls back up to the big header.
struct CompactPrayerBar: View {
    @Environment(Localization.self) private var l10n
    @Environment(\.colorScheme) private var scheme
    let snap: PrayerSnapshot
    let compass: CompassState
    let aligned: Bool
    var onExpand: () -> Void

    var body: some View {
        if let next = snap.next, let loc = snap.location {
            let sky = Sky.of(snap.sky, dark: scheme == .dark)
            Button(action: onExpand) {
                HStack(spacing: 12) {
                    TurningLogo(target: arrowTarget, size: 46, glow: aligned)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(l10n.t(Format.prayerKey(next.event)) + "  " + Format.time(next.at, zone: loc.zone, locale: l10n.locale)).font(.headline)
                        Text(l10n.t("remaining_long", Format.remainingLong(PrayerCalculator.remaining(snap.now, next.at), locale: l10n.locale)))
                            .font(.subheadline).monospacedDigit().foregroundStyle(Theme.accent)
                    }
                    Spacer()
                    Image(systemName: "chevron.down").font(.headline).frame(width: 44, height: 44).accessibilityHidden(true)
                }
                .foregroundStyle(Theme.ink)
                .padding(.horizontal, 16).padding(.vertical, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(PressHighlight(shape: AnyShape(Rectangle())))
            .background {
                // The top of the same sky, opaque, reaching up under the status bar.
                LinearGradient(colors: [sky.top, sky.top.mix(with: sky.mid, by: 0.35)], startPoint: .top, endPoint: .bottom)
                    .overlay(alignment: .bottom) { Rectangle().fill(Theme.ink.opacity(0.12)).frame(height: 1) }
                    .shadow(color: .black.opacity(0.18), radius: 8, y: 3)
                    .ignoresSafeArea(edges: .top)
            }
            .accessibilityHint(l10n.t("expand"))
        }
    }

    private var arrowTarget: Double? {
        guard let b = snap.qiblaBearing else { return nil }
        if case let .live(h, _, _) = compass { return Angles.relativeToQibla(qiblaBearingTrue: b, headingTrue: h) }
        return b
    }
}

/// The logo disc whose arrow turns to `target` by the shortest way (springy unless Reduce Motion).
struct TurningLogo: View {
    let target: Double?
    var size: CGFloat = Theme.discSize
    var glow = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown: Double = 0

    var body: some View {
        LogoDisc(size: size, glow: glow, arrow: target == nil ? nil : shown)
            .onAppear { shown = target ?? 0 }
            .onChange(of: target) { _, t in
                guard let t else { return }
                let next = Angles.shortestTarget(current: shown, target: t)
                if reduceMotion { shown = next } else { withAnimation(.interpolatingSpring(stiffness: 140, damping: 22)) { shown = next } }
            }
    }
}

/// Live: arrow to the Qibla relative to the phone. No heading: north-up diagram (north tick) with the bearing.
struct MiniQiblaIndicator: View {
    @Environment(Localization.self) private var l10n
    let bearing: Double
    let compass: CompassState

    var body: some View {
        let angle: Double = {
            if case let .live(h, _, _) = compass { return Angles.relativeToQibla(qiblaBearingTrue: bearing, headingTrue: h) }
            return bearing
        }()
        let live: Bool = { if case .live = compass { return true } else { return false } }()
        ZStack {
            Circle().stroke(Theme.ink.opacity(0.6), lineWidth: 1.5)
            if !live { Circle().fill(Theme.ink).frame(width: 4, height: 4).offset(y: -16) }
            Image(systemName: "location.north.fill").font(.footnote).foregroundStyle(Theme.accent).rotationEffect(.degrees(angle))
        }
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityElement()
        .accessibilityLabel(l10n.t("qibla") + ", " + l10n.t("qibla_bearing", Format.degrees(bearing, locale: l10n.locale)))
    }
}
