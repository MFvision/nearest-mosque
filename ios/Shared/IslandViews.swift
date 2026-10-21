#if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
import NMCore
import SwiftUI
import WidgetKit

// Pieces of the Dynamic Island and Lock Screen designs, shared by the widget extension and the app's debug
// gallery (CI screenshots of the designs).

/// The accent colour of the island, from the widget style chosen in the app (gold for Gold and cream, Green and
/// Night sky; a lighter gold for Teal and Time of day): bright enough on the island's black.
enum IslandTint {
    static func of(_ style: String) -> Color { StyleAccent.dark(style) }
}

/// The logo's arrow (white body, coloured facet) turned the way to go; straight up means facing the Qibla.
struct IslandArrow: View {
    let turn: Double?
    let facing: Bool
    let tint: Color
    let size: CGFloat

    var body: some View {
        ZStack {
            BrandArrow().fill(Color.white)
            BrandArrow(facet: true).fill(tint)
        }
        .frame(width: size * 0.79, height: size)
        .rotationEffect(.degrees(turn ?? 0))
        .animation(.easeOut(duration: 0.35), value: turn ?? 0)
        .shadow(color: facing ? tint.opacity(0.9) : .clear, radius: size * 0.3)
        .opacity(turn == nil ? 0.5 : 1)
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
    }
}

/// The app's Qibla ring as an arc for the island: the Kaaba at the apex, the gold dot where the Qibla is now
/// (held at the ends when it is behind you), the glowing stretch between them, and the arrow at the centre. The
/// dot, the stretch and the arrow are shapes and turns, so they glide from one update to the next instead of
/// jumping, as the compass does in the app.
struct QiblaArcDial: View {
    let turn: Double?
    let facing: Bool
    let tint: Color
    let onDark: Bool

    var body: some View {
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            let r = min(w / 2 - 16, h - 14)
            let c = CGPoint(x: w / 2, y: h - 2)
            let t = max(-90, min(90, turn ?? 0))
            let ink = onDark ? Color.white : Color(red: 0.07, green: 0.15, blue: 0.23)
            ZStack {
                Canvas { ctx, _ in
                    var arc = Path()
                    arc.addArc(center: c, radius: r, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
                    ctx.stroke(arc, with: .color(ink.opacity(0.22)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    for i in 0...12 {
                        let a = (Double(i) * 15 - 90) * .pi / 180
                        var tick = Path()
                        tick.move(to: CGPoint(x: c.x + (r - 5) * CGFloat(Foundation.sin(a)), y: c.y - (r - 5) * CGFloat(Foundation.cos(a))))
                        tick.addLine(to: CGPoint(x: c.x + (r + 5) * CGFloat(Foundation.sin(a)), y: c.y - (r + 5) * CGFloat(Foundation.cos(a))))
                        ctx.stroke(tick, with: .color(ink.opacity(i % 6 == 0 ? 0.6 : 0.25)), lineWidth: i % 6 == 0 ? 1.5 : 1)
                    }
                }
                if turn != nil {
                    TurnArc(center: c, radius: r, turn: t)
                        .stroke(tint.opacity(0.9), style: StrokeStyle(lineWidth: 6, lineCap: .round)).blur(radius: 3)
                    TurnArc(center: c, radius: r, turn: t)
                        .stroke(tint, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    // The dot rides the arc: a turn about the centre, not a slide across.
                    Circle().fill(tint).overlay(Circle().stroke(Color.white, lineWidth: 2))
                        .frame(width: 14, height: 14)
                        .position(x: c.x, y: c.y - r)
                        .rotationEffect(.degrees(t), anchor: UnitPoint(x: c.x / max(w, 1), y: c.y / max(h, 1)))
                }
                // The Kaaba fixed at the apex: you face the Qibla when the dot reaches it.
                ZStack {
                    Circle().fill(Color.white)
                    WidgetKaaba().padding(5)
                }
                .frame(width: 26, height: 26)
                .overlay(Circle().stroke(facing ? tint : Color.white.opacity(0.6), lineWidth: facing ? 2.5 : 1))
                .shadow(color: facing ? tint : .clear, radius: 10)
                .position(x: c.x, y: c.y - r)
                IslandArrow(turn: turn, facing: facing, tint: tint, size: min(46, r * 0.75))
                    .position(x: c.x, y: c.y - r * 0.42)
            }
            .animation(.easeOut(duration: 0.35), value: t)
            .animation(.easeInOut(duration: 0.3), value: facing)
        }
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
    }
}

/// The stretch of the arc from the apex to the Qibla, `turn` degrees (negative: to the left); animatable.
struct TurnArc: Shape {
    var center: CGPoint
    var radius: CGFloat
    var turn: Double

    var animatableData: Double {
        get { turn }
        set { turn = newValue }
    }

    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.addArc(center: center, radius: radius, startAngle: .degrees(-90), endAngle: .degrees(-90 + turn), clockwise: turn < 0)
        return p
    }
}

/// The Lock Screen card: the sky of the widget style behind the same arc, with the words and the bearing.
struct QiblaLockCard: View {
    let attributes: QiblaActivityAttributes
    let state: QiblaActivityAttributes.ContentState

    var body: some View {
        let look = WidgetLook(rawValue: attributes.style) ?? .night
        let p = WidgetPalette.of(look == .cream || look == .auto ? .night : look, period: .isha)
        let tint = IslandTint.of(attributes.style)
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Label(attributes.title + " · " + attributes.city, systemImage: "location.north.line.fill")
                    .font(.caption.weight(.semibold)).foregroundStyle(p.secondary).lineLimit(1)
                Text(state.hint).font(.title3.weight(.bold)).foregroundStyle(state.facing ? tint : p.ink).lineLimit(2).minimumScaleFactor(0.8)
                Text(attributes.fromNorth).font(.caption).foregroundStyle(p.secondary).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            QiblaArcDial(turn: state.turn, facing: state.facing, tint: tint, onDark: true)
                .frame(width: 150, height: 86)
        }
        .padding(16)
        .background(p.background)
        .environment(\.layoutDirection, attributes.rtl ? .rightToLeft : .leftToRight)
        .accessibilityElement(children: .combine)
    }
}

/// The Lock Screen card: the sky of the hour, the next prayer with a live countdown and progress bar, and
/// today's prayers along the bottom with the next one lit. Once the time counted down to has passed (stale), the
/// next Adhan, already counting down, lit in its own day's row.
struct PrayerLockScreenView: View {
    let attributes: PrayerActivityAttributes
    let state: PrayerActivityAttributes.ContentState
    let stale: Bool

    var body: some View {
        let v = state.shown(stale: stale)
        let p = WidgetPalette.of(.sky, period: PrayerEvent(rawValue: v.period))
        // The countdown to the Adhan, or after it to the iqama when there is one; once that has passed, to the next Adhan.
        let range = v.range
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label(state.city, systemImage: "mappin.and.ellipse").font(.caption.weight(.semibold)).foregroundStyle(p.secondary).lineLimit(1)
                Spacer()
                Text(v.time).font(.caption.weight(.semibold).monospacedDigit()).foregroundStyle(p.secondary).lineLimit(1)
            }
            HStack(alignment: .firstTextBaseline) {
                Label {
                    Text(v.title).font(.title2.weight(.bold)).lineLimit(1).minimumScaleFactor(0.7)
                } icon: {
                    Image(systemName: v.symbol).foregroundStyle(p.accent)
                }
                .foregroundStyle(p.ink)
                Spacer(minLength: 8)
                if let range {
                    VStack(alignment: .trailing, spacing: 0) {
                        if let label = v.label {
                            Text(label).font(.caption.weight(.semibold)).foregroundStyle(p.secondary).lineLimit(1)
                        }
                        Text(timerInterval: range, countsDown: true)
                            .font(.system(size: 30, weight: .semibold, design: .rounded).monospacedDigit())
                            .foregroundStyle(p.accent)
                            .multilineTextAlignment(.trailing)
                            .environment(\.layoutDirection, .leftToRight)
                    }
                    .frame(maxWidth: 160, alignment: .trailing)
                }
            }
            if let range {
                ProgressView(timerInterval: range.lowerBound...max(range.lowerBound.addingTimeInterval(1), range.upperBound), countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
                .tint(p.accent)
            }
            HStack(spacing: 4) {
                ForEach(Array(v.slots.enumerated()), id: \.offset) { i, slot in
                    let on = i == v.index
                    VStack(spacing: 2) {
                        Text(slot.name).font(.caption2.weight(on ? .bold : .regular)).lineLimit(1).minimumScaleFactor(0.7)
                        Text(slot.time).font(.caption2.monospacedDigit().weight(on ? .bold : .regular)).lineLimit(1).minimumScaleFactor(0.7)
                    }
                    .foregroundStyle(on ? p.onPill : p.ink.opacity(0.85))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
                    .background(on ? p.pill : Color.clear, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
            }
        }
        .padding(16)
        .background(p.background)
        .environment(\.layoutDirection, attributes.rtl ? .rightToLeft : .leftToRight)
    }
}
#endif
