import NMCore
import SwiftUI

/// The logo's arrow as a shape (shared/brand/emblem/options.py ARROW, tip up, centred on its middle):
/// `body` is the whole arrow, `facet` its lit right half.
struct BrandArrow: Shape {
    var facet = false
    func path(in r: CGRect) -> Path {
        // Arrow space: x -44…44, y -60…52 (112 tall); centre the box on (0, -4).
        let k = min(r.width / 88, r.height / 112)
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: r.midX + x * k, y: r.midY + (y + 4) * k) }
        var path = Path()
        path.move(to: p(0, -60))
        path.addLine(to: p(44, 52))
        path.addLine(to: p(0, 30))
        if !facet { path.addLine(to: p(-44, 52)) }
        path.closeSubpath()
        return path
    }
}

/// The Qibla ring, after the reference: a thin glass ring with the Kaaba (the logo's cube) fixed at the
/// top and the logo's arrow in the middle pointing to the Qibla. A gold dot on the ring marks the Qibla
/// with a dotted line towards the middle, and a glowing stretch of ring runs from the dot to the Kaaba:
/// how far to turn. Plain words below the arrow; the distance to the Kaaba small along the left side,
/// the nearest mosque along the right (it opens the mosque). No north, no angles.
struct QiblaRing: View {
    @Environment(\.l10n) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorScheme) private var scheme
    /// Degrees clockwise from the top of the phone to the Qibla (north-up bearing without a compass).
    let angle: Double
    let aligned: Bool
    let guidance: String
    /// Short guidance (with a live compass) curves along the ring; the longer no-compass note stays straight.
    var curveGuidance = true
    var kaabaDistance: String?
    var nearest: String?
    var onNearest: (() -> Void)?
    @State private var shown: Double = 0

    var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width, geo.size.height)
            let r = s / 2 - 34
            let c = CGPoint(x: geo.size.width / 2, y: geo.size.height / 2)
            let rad = shown * .pi / 180
            let dot = CGPoint(x: c.x + r * sin(rad), y: c.y - r * cos(rad))
            ZStack {
                Circle().fill(Theme.ink.opacity(scheme == .dark ? 0.06 : 0.1))
                    .frame(width: 2 * r, height: 2 * r).position(c)
                Circle().stroke(Theme.ink.opacity(0.45), lineWidth: 1.5)
                    .frame(width: 2 * r, height: 2 * r).position(c)
                Canvas { ctx, _ in
                    // How far to turn: the ring from the Kaaba (top) to the dot, glowing.
                    let rel = Angles.normalize180(shown)
                    var trail = Path()
                    trail.addArc(center: c, radius: r, startAngle: .degrees(-90), endAngle: .degrees(-90 + rel), clockwise: rel < 0)
                    var glow = ctx
                    glow.addFilter(.blur(radius: 4))
                    glow.stroke(trail, with: .color(.white.opacity(0.9)), style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    ctx.stroke(trail, with: .color(.white), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    // Dotted line from the dot towards the middle.
                    var line = Path()
                    line.move(to: dot)
                    line.addLine(to: CGPoint(x: c.x + r * 0.55 * sin(rad), y: c.y - r * 0.55 * cos(rad)))
                    ctx.stroke(line, with: .color(Theme.ink.opacity(0.5)), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [3, 5]))
                    ctx.fill(Path(ellipseIn: CGRect(x: dot.x - 9, y: dot.y - 9, width: 18, height: 18)), with: .color(Theme.gold))
                    ctx.stroke(Path(ellipseIn: CGRect(x: dot.x - 9, y: dot.y - 9, width: 18, height: 18)), with: .color(.white), lineWidth: 2.5)
                }
                .allowsHitTesting(false)
                // The Kaaba, fixed at the top: the phone faces the Qibla when the dot reaches it.
                ZStack {
                    Circle().fill(.white)
                    Image("LogoBody").resizable().scaledToFit().padding(9)
                }
                .frame(width: 50, height: 50)
                .overlay(Circle().stroke(aligned ? Theme.gold : .white.opacity(0.8), lineWidth: aligned ? 3 : 1.5))
                .shadow(color: aligned ? Theme.gold.opacity(0.8) : .black.opacity(0.2), radius: aligned ? 16 : 6)
                .position(x: c.x, y: c.y - r)
                // The logo's arrow, pointing to the Qibla.
                ZStack {
                    BrandArrow().fill(scheme == .dark ? Color.white : Theme.navy)
                    BrandArrow(facet: true).fill(Theme.gold)
                }
                .frame(width: s * 0.2, height: s * 0.26)
                .rotationEffect(.degrees(shown))
                .shadow(color: aligned ? Theme.gold.opacity(0.9) : .black.opacity(0.25), radius: aligned ? 14 : 6)
                .position(c)
                if curveGuidance {
                    // Plain words along the inside of the ring, below the arrow.
                    CurvedText(text: guidance, style: .headline, weight: .semibold, color: Theme.accent,
                               center: c, radius: r * 0.74, angle: 180, outside: false)
                    Color.clear.frame(width: 1, height: 1).position(c)
                        .accessibilityElement().accessibilityLabel(guidance).accessibilityAddTraits(.updatesFrequently)
                } else {
                    Text(guidance).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.accent).multilineTextAlignment(.center)
                        .frame(width: r * 1.4)
                        .position(x: c.x, y: c.y + r * 0.62)
                        .accessibilityAddTraits(.updatesFrequently)
                }
                if let kaabaDistance {
                    CurvedSideLabel(text: kaabaDistance, icon: Image("LogoBody"), color: Theme.ink.opacity(0.85),
                                    center: c, radius: r + 12, angle: 250)
                }
                if let nearest, let onNearest {
                    CurvedSideLabel(text: nearest, icon: Image("MosqueTab"), weight: .semibold, color: Theme.accent,
                                    center: c, radius: r + 12, angle: 110, action: onNearest)
                }
            }
            .environment(\.layoutDirection, .leftToRight)
        }
        .aspectRatio(1, contentMode: .fit)
        .onAppear { shown = angle }
        .onChange(of: angle) { _, a in
            let next = Angles.shortestTarget(current: shown, target: a)
            if reduceMotion { shown = next } else { withAnimation(.interpolatingSpring(stiffness: 140, damping: 22)) { shown = next } }
        }
    }
}

/// Warm light falling across the screen from the Qibla's side, turning with the phone; brighter when
/// the phone faces the Qibla. Decorative; still under Reduce Motion.
struct QiblaLightBeam: View {
    let angle: Double
    let aligned: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown: Double = 0

    var body: some View {
        GeometryReader { g in
            let h = max(g.size.width, g.size.height) * 1.6
            LinearGradient(stops: [
                .init(color: Theme.gold.opacity(0), location: 0),
                .init(color: Theme.gold.opacity(aligned ? 0.5 : 0.3), location: 0.5),
                .init(color: Theme.gold.opacity(0), location: 1),
            ], startPoint: .leading, endPoint: .trailing)
            .frame(width: g.size.width * (aligned ? 0.75 : 0.5), height: h)
            .mask(LinearGradient(colors: [.black, .black.opacity(0.6), .clear], startPoint: .top, endPoint: .bottom))
            // The band starts at the middle of the screen and reaches out towards the Qibla.
            .offset(y: -h / 2)
            .rotationEffect(.degrees(shown), anchor: .center)
            .position(x: g.size.width / 2, y: g.size.height * 0.36)
            .blur(radius: 24)
        }
        .environment(\.layoutDirection, .leftToRight)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear { shown = angle }
        .onChange(of: angle) { _, a in
            let next = Angles.shortestTarget(current: shown, target: a)
            if reduceMotion { shown = next } else { withAnimation(.interpolatingSpring(stiffness: 120, damping: 24)) { shown = next } }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.4), value: aligned)
    }
}

/// The full Qibla view (opened by tapping the big header): city and calculation method, the Qibla
/// ring in warm light, then today's date and prayer times. Heading runs only while visible.
struct QiblaCompassView: View {
    @Environment(\.appModel) private var model
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    @State private var detector = AlignmentDetector()
    @State private var aligned = false
    @State private var mosque: RankedMosque?

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { ctx in
            let snap = PrayerSnapshot(model: model, now: ctx.date)
            let compass = model.location.compass
            ZStack(alignment: .top) {
                if let b = snap.qiblaBearing { QiblaLightBeam(angle: relative(b, compass), aligned: aligned).ignoresSafeArea() }
                ScrollView {
                    VStack(spacing: 14) {
                        header(snap)
                        if let bearing = snap.qiblaBearing {
                            QiblaRing(angle: relative(bearing, compass), aligned: aligned,
                                      guidance: qiblaGuidance(compass, bearing: bearing, l10n: l10n),
                                      curveGuidance: { if case .live = compass { return true } else { return false } }(),
                                      kaabaDistance: snap.qiblaDistance.map { l10n.t("qibla_distance", Format.distance($0, l10n: l10n)) },
                                      nearest: model.nearestMosque.map { l10n.t("nearest_mosque_chip", Format.distance($0.distanceMeters, l10n: l10n)) },
                                      onNearest: { mosque = model.nearestMosque })
                                .frame(maxWidth: 420)
                                .accessibilityElement(children: .contain)
                            // Only where there is a compass to hold (not on a Mac).
                            if model.location.headingAvailable {
                                Text(l10n.t("qibla_hold_flat") + " · " + l10n.t("qibla_approximate"))
                                    .font(.caption).foregroundStyle(Theme.ink.opacity(0.7)).multilineTextAlignment(.center)
                            }
                        } else {
                            Text(l10n.t("qibla_location_needed")).padding(.top, 60)
                        }
                        if let loc = snap.location {
                            let d = CivilDate.of(snap.now, in: loc.zone)
                            VStack(spacing: 2) {
                                Text(Format.gregorian(d, locale: l10n.locale)).font(.headline)
                                Text(Format.hijri(d, adjustment: model.settings.prayer.hijriAdjustmentDays, locale: l10n.locale)).font(.subheadline).foregroundStyle(Theme.accent)
                            }
                            .padding(.top, 8)
                        }
                        if let today = snap.today, today.status != .unavailable { ScheduleCard(snap: snap, today: today) }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 32)
                }
                .scrollIndicators(.hidden)
            }
            .foregroundStyle(Theme.ink)
            .onChange(of: compass) { _, c in
                guard case let .live(h, acc, _) = c, let b = snap.qiblaBearing else { aligned = false; return }
                _ = detector.update(relativeDeg: Angles.relativeToQibla(qiblaBearingTrue: b, headingTrue: h), accuracyDeg: acc)
                aligned = detector.aligned
            }
        }
        .skyBackground(horizon: 0.82)
        .sensoryFeedback(.success, trigger: aligned) { _, new in new }
        .onAppear { model.location.beginHeading() }
        .onDisappear { model.location.endHeading() }
        .sheet(item: $mosque) { r in
            MosqueDetailView(ranked: r, favorite: ((try? model.mosques?.favorites()) ?? []).contains(r.id),
                             canFavorite: r.mosque.packId.hasPrefix("mosques.")) { on in
                try? model.mosques?.setFavorite(r.id, on)
            }
            .presentationDetents([.medium, .large])
        }
    }

    private func header(_ snap: PrayerSnapshot) -> some View {
        ZStack {
            VStack(spacing: 2) {
                Text(snap.location?.name ?? l10n.t("qibla")).font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
                Text(l10n.t(Format.methodKey(model.settings.prayer.method))).font(.caption).foregroundStyle(Theme.ink.opacity(0.75))
            }
            .padding(.horizontal, 56)
            HStack {
                Spacer()
                GlassIconButton(systemImage: "xmark", label: l10n.t("close")) { dismiss() }
            }
        }
        .padding(.top, 8)
    }

    private func relative(_ bearing: Double, _ compass: CompassState) -> Double {
        if case let .live(h, _, _) = compass { return Angles.relativeToQibla(qiblaBearingTrue: bearing, headingTrue: h) }
        return bearing
    }
}
