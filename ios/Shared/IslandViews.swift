#if canImport(ActivityKit) && !targetEnvironment(macCatalyst)
import NMCore
import SwiftUI
import WidgetKit

// Pieces of the Dynamic Island and Lock Screen designs, shared by the widget extension and the app's debug
// gallery (CI screenshots of the designs).

/// The accent colour of the island, from the widget style chosen in the app (gold for Gold and cream, Green and
/// Night sky; a lighter gold for Teal and Time of day): bright enough on the island's black.
enum IslandTint {
    static func of(_ style: String) -> Color {
        switch style {
        case "green": return Color(red: 0.91, green: 0.71, blue: 0.35)
        case "night": return Color(red: 0.83, green: 0.66, blue: 0.26)
        case "teal": return Color(red: 0.55, green: 0.85, blue: 0.80)
        case "sky": return Color(red: 0.95, green: 0.82, blue: 0.48)
        default: return Color(red: 0.89, green: 0.75, blue: 0.39)
        }
    }
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
        .shadow(color: facing ? tint.opacity(0.9) : .clear, radius: size * 0.3)
        .opacity(turn == nil ? 0.5 : 1)
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
    }
}

/// The app's Qibla ring as an arc for the island: the Kaaba at the apex, the gold dot where the Qibla is now
/// (held at the ends when it is behind you), the glowing stretch between them, and the arrow at the centre.
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
            let rad = t * .pi / 180
            let dot = CGPoint(x: c.x + r * CGFloat(Foundation.sin(rad)), y: c.y - r * CGFloat(Foundation.cos(rad)))
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
                    if turn != nil {
                        var trail = Path()
                        trail.addArc(center: c, radius: r, startAngle: .degrees(-90), endAngle: .degrees(-90 + t), clockwise: t < 0)
                        var glow = ctx
                        glow.addFilter(.blur(radius: 3))
                        glow.stroke(trail, with: .color(tint.opacity(0.9)), style: StrokeStyle(lineWidth: 6, lineCap: .round))
                        ctx.stroke(trail, with: .color(tint), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                        ctx.fill(Path(ellipseIn: CGRect(x: dot.x - 7, y: dot.y - 7, width: 14, height: 14)), with: .color(tint))
                        ctx.stroke(Path(ellipseIn: CGRect(x: dot.x - 7, y: dot.y - 7, width: 14, height: 14)), with: .color(.white), lineWidth: 2)
                    }
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
        }
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
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
/// today's prayers along the bottom with the next one lit.
struct PrayerLockScreenView: View {
    let attributes: PrayerActivityAttributes
    let state: PrayerActivityAttributes.ContentState
    let stale: Bool

    var body: some View {
        let p = WidgetPalette.of(.sky, period: PrayerEvent(rawValue: state.period))
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label(state.city, systemImage: "mappin.and.ellipse").font(.caption.weight(.semibold)).foregroundStyle(p.secondary).lineLimit(1)
                Spacer()
                Text(state.timeText).font(.caption.weight(.semibold).monospacedDigit()).foregroundStyle(p.secondary)
            }
            HStack(alignment: .firstTextBaseline) {
                Label {
                    Text(stale ? state.nowTitle : state.prayer).font(.title2.weight(.bold)).lineLimit(1).minimumScaleFactor(0.7)
                } icon: {
                    Image(systemName: state.symbol).foregroundStyle(p.accent)
                }
                .foregroundStyle(p.ink)
                Spacer(minLength: 8)
                if !stale {
                    Text(timerInterval: state.start...max(state.start, state.at), countsDown: true)
                        .font(.system(size: 30, weight: .semibold, design: .rounded).monospacedDigit())
                        .foregroundStyle(p.accent)
                        .multilineTextAlignment(.trailing)
                        .environment(\.layoutDirection, .leftToRight)
                        .frame(maxWidth: 140, alignment: .trailing)
                }
            }
            if !stale {
                ProgressView(timerInterval: state.start...max(state.start.addingTimeInterval(1), state.at), countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
                .tint(p.accent)
            }
            HStack(spacing: 4) {
                ForEach(Array(state.slots.enumerated()), id: \.offset) { i, slot in
                    let on = i == state.nextIndex
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
