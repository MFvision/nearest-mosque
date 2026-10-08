import NMCore
import SwiftUI

/// The Qibla from north for the TV's city: a north-up dial with the direction and distance to the
/// Kaaba. A television has no compass, so this is a diagram to set a prayer mat by, not a live pointer.
struct TVQiblaView: View {
    @Environment(TVModel.self) private var model

    var body: some View {
        let l10n = model.l10n
        TimelineView(.everyMinute) { ctx in
            let snap = TVSnapshot(model, now: ctx.date)
            let sky = Sky.of(snap.sky)
            HStack(spacing: 100) {
                if let loc = model.settings.location {
                    let bearing = Qibla.bearing(from: loc.location)
                    QiblaNorthDial(bearing: bearing, north: l10n.t("compass_north"))
                        .frame(width: 640, height: 640)
                        .accessibilityLabel(l10n.t("qibla_dial_a11y", l10n.t("qibla_bearing", Format.degrees(bearing, locale: l10n.locale))))
                    VStack(alignment: .leading, spacing: 24) {
                        Text(l10n.t("qibla")).font(.system(size: 76, weight: .bold))
                        Label(loc.name, systemImage: "mappin.and.ellipse").font(.system(size: 36)).foregroundStyle(.white.opacity(0.85))
                        Text(l10n.t("qibla_bearing", Format.degrees(bearing, locale: l10n.locale)))
                            .font(.system(size: 54, weight: .semibold))
                            .foregroundStyle(Theme.gold)
                        Text(l10n.t("qibla_distance", Format.distance(Qibla.distanceMeters(from: loc.location), l10n: l10n)))
                            .font(.system(size: 36))
                        Text(l10n.t("qibla_north_up"))
                            .font(.system(size: 28))
                            .foregroundStyle(.white.opacity(0.75))
                            .padding(.top, 20)
                    }
                    .frame(maxWidth: 820, alignment: .leading)
                }
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background { SkyBackdrop(sky: sky, horizon: 0.82).ignoresSafeArea() }
        }
        .focusable()
        .focusEffectDisabled()
    }
}

/// North at the top, the eight compass points, and a gold arrow from the centre to the Kaaba on the rim.
struct QiblaNorthDial: View {
    let bearing: Double
    let north: String

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let r = size / 2
            ZStack {
                Circle().fill(.ultraThinMaterial)
                Circle().stroke(.white.opacity(0.35), lineWidth: 2)
                Canvas { ctx, s in
                    let c = CGPoint(x: s.width / 2, y: s.height / 2)
                    for i in 0..<72 {
                        let major = i % 9 == 0
                        let a = Double(i) * 5 * .pi / 180
                        let outer = r - 10, inner = r - (major ? 44 : 24)
                        var p = Path()
                        p.move(to: point(c, inner, a))
                        p.addLine(to: point(c, outer, a))
                        ctx.stroke(p, with: .color(.white.opacity(major ? 0.85 : 0.35)), lineWidth: major ? 4 : 2)
                    }
                    // The arrow: a gold line from the centre towards the Qibla, with a head.
                    let a = bearing * .pi / 180
                    let tip = point(c, r - 110, a)
                    var shaft = Path()
                    shaft.move(to: c)
                    shaft.addLine(to: tip)
                    ctx.stroke(shaft, with: .color(Theme.gold), style: StrokeStyle(lineWidth: 12, lineCap: .round))
                    var head = Path()
                    head.move(to: point(c, r - 80, a))
                    head.addLine(to: point(tip, 34, a + .pi * 0.78))
                    head.addLine(to: point(tip, 34, a - .pi * 0.78))
                    head.closeSubpath()
                    ctx.fill(head, with: .color(Theme.gold))
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 18, y: c.y - 18, width: 36, height: 36)), with: .color(.white))
                }
                Text(north)
                    .font(.system(size: 44, weight: .bold))
                    .foregroundStyle(Theme.gold)
                    .position(x: geo.size.width / 2, y: geo.size.height / 2 - r + 78)
                KaabaIcon(size: 72)
                    .position(point(CGPoint(x: geo.size.width / 2, y: geo.size.height / 2), r - 50, bearing * .pi / 180))
            }
            .frame(width: size, height: size)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        // A compass is the same in every language: right-to-left layout must not mirror it.
        .environment(\.layoutDirection, .leftToRight)
    }

    /// The point at `radius` from `c`, `angle` radians clockwise from north (up).
    private func point(_ c: CGPoint, _ radius: CGFloat, _ angle: Double) -> CGPoint {
        let dx: Double = sin(angle), dy: Double = cos(angle)
        return CGPoint(x: c.x + radius * CGFloat(dx), y: c.y - radius * CGFloat(dy))
    }
}
