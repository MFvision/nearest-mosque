import NMCore
import SwiftUI

/// Middle level of the prayer header, opened from the compact bar: the next prayer and its countdown,
/// a small sky for the time of day with the sun (or the moon at night) on its path, and the Qibla
/// (which opens the full compass, the third level). Swipe up or tap the chevron to close.
struct SkyCard: View {
    @Environment(Localization.self) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorScheme) private var scheme
    let snap: PrayerSnapshot
    let compass: CompassState
    let aligned: Bool
    var onQibla: () -> Void
    var onCollapse: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            if let next = snap.next, let loc = snap.location {
                let name = l10n.t(Format.prayerKey(next.event))
                let time = Format.time(next.at, zone: loc.zone, locale: l10n.locale)
                let remaining = PrayerCalculator.remaining(snap.now, next.at)
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(name).font(.title3.weight(.semibold))
                        Text(time).font(.largeTitle.weight(.light)).monospacedDigit()
                        Text(l10n.t("remaining_long", Format.remainingLong(remaining, locale: l10n.locale)))
                            .font(.subheadline).monospacedDigit().foregroundStyle(Theme.accent)
                            .contentTransition(reduceMotion ? .identity : .numericText(countsDown: true))
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityLabel(l10n.t("countdown_a11y", name, time, Format.countdown(remaining, locale: l10n.locale)))
                    Spacer()
                    Button(action: onCollapse) {
                        Image(systemName: "chevron.up").font(.headline).frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(l10n.t("collapse"))
                }
            }
            if let arc = snap.arc, let loc = snap.location {
                DayArcView(arc: arc, period: snap.sky, zone: loc.zone)
            }
            if let b = snap.qiblaBearing {
                Button(action: onQibla) {
                    HStack(spacing: 12) {
                        MiniQiblaIndicator(bearing: b, compass: compass).frame(width: 40, height: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(l10n.t("qibla")).font(.headline)
                            Text(aligned ? l10n.t("qibla_you_are_facing") : l10n.t("qibla_bearing", Format.degrees(b, locale: l10n.locale)))
                                .font(.subheadline).foregroundStyle(aligned ? Theme.accent : Theme.ink.opacity(0.8))
                        }
                        Spacer()
                        Text(l10n.t("qibla_open_compass")).font(.footnote.weight(.semibold)).foregroundStyle(Theme.accent)
                        Image(systemName: "chevron.forward").font(.footnote.weight(.bold)).foregroundStyle(Theme.accent)
                    }
                    .padding(.horizontal, 12).frame(minHeight: 56)
                    .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(PressHighlight())
                .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
        }
        .foregroundStyle(Theme.ink)
        .padding(14)
        // Nearly opaque: the card floats over the scrolled list, which must not show through.
        .background(Sky.of(snap.sky, dark: scheme == .dark).low.opacity(0.9), in: RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
        .glass(RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
        .gesture(DragGesture(minimumDistance: 20).onEnded { v in if v.translation.height < -40 { onCollapse() } })
        .accessibilityAction(named: l10n.t("collapse"), onCollapse)
    }
}

/// A small sky for the time of day: daylight runs from sunrise (start) to Maghrib (end) with the sun on
/// its path; at night the moon moves from Maghrib to the next sunrise. Dhuhr and Asr (or Isha and Fajr)
/// are marked where they fall. Time runs in the reading direction (right to left in Arabic and Urdu).
struct DayArcView: View {
    @Environment(Localization.self) private var l10n
    @Environment(\.colorScheme) private var scheme
    @Environment(\.layoutDirection) private var direction
    let arc: DayArc
    let period: SkyPeriod
    let zone: TimeZone
    var height: CGFloat = 156

    var body: some View {
        let sky = Sky.of(period, dark: scheme == .dark)
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            let horizon = h * 0.74
            let a = w / 2 - 34, b = horizon - 40
            let point: (Double) -> CGPoint = { t in
                let f = direction == .rightToLeft ? 1 - t : t
                let theta = Double.pi * (1 - f)
                return CGPoint(x: w / 2 + a * cos(theta), y: horizon - b * sin(theta))
            }
            let here = point(arc.fraction)
            ZStack(alignment: .topLeading) {
                LinearGradient(colors: [sky.top, sky.mid, sky.mid.mix(with: sky.glow, by: 0.45)], startPoint: .top, endPoint: .init(x: 0.5, y: 0.74))
                // The glow follows the sun (or the moon, faintly).
                Circle()
                    .fill(RadialGradient(colors: [sky.glow.opacity(arc.isDay ? 0.85 : 0.35), sky.glow.opacity(0)], center: .center, startRadius: 0, endRadius: 90))
                    .frame(width: 180, height: 180)
                    .position(here)
                if !arc.isDay && sky.showsStars { StarField(seed: 11).frame(width: w, height: horizon).opacity(0.7) }
                Canvas { ctx, _ in
                    var path = Path()
                    for i in 0...60 { let p = point(Double(i) / 60); if i == 0 { path.move(to: p) } else { path.addLine(to: p) } }
                    ctx.stroke(path, with: .color(Theme.ink.opacity(0.45)), style: StrokeStyle(lineWidth: 1.5, dash: [3, 5]))
                    // The part of the half already passed, solid.
                    var done = Path()
                    let n = max(1, Int(arc.fraction * 60))
                    for i in 0...n { let p = point(arc.fraction * Double(i) / Double(n)); if i == 0 { done.move(to: p) } else { done.addLine(to: p) } }
                    ctx.stroke(done, with: .color(Theme.accent.opacity(0.9)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                    var ground = Path()
                    ground.move(to: CGPoint(x: 0, y: horizon)); ground.addLine(to: CGPoint(x: w, y: horizon))
                    ctx.stroke(ground, with: .color(Theme.ink.opacity(0.35)), lineWidth: 1)
                    for m in arc.marks {
                        let p = point(m.fraction)
                        ctx.fill(Path(ellipseIn: CGRect(x: p.x - 4, y: p.y - 4, width: 8, height: 8)), with: .color(m.fraction <= arc.fraction ? Theme.accent : Theme.ink.opacity(0.8)))
                    }
                }
                Rectangle().fill(sky.low.opacity(0.55)).frame(width: w, height: h - horizon).offset(y: horizon)
                // Labels sit inside the arc; outside it near the ends (clear of the horizon labels) or
                // where the sun or moon is on that mark.
                ForEach(arc.marks, id: \.at) { m in
                    let outside = abs(m.fraction - arc.fraction) < 0.1 || m.fraction < 0.2 || m.fraction > 0.8
                    label(m.event, m.at).position(x: point(m.fraction).x, y: point(m.fraction).y + (outside ? -22 : 22))
                }
                label(arc.startEvent, arc.start).position(x: point(0).x, y: horizon + 20)
                label(arc.endEvent, arc.end).position(x: point(1).x, y: horizon + 20)
                celestial.position(here)
            }
            .frame(width: w, height: h)
        }
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.ink.opacity(0.12), lineWidth: 1))
        .accessibilityElement()
        .accessibilityLabel(a11y)
    }

    @ViewBuilder private var celestial: some View {
        if arc.isDay {
            Circle().fill(RadialGradient(colors: [Color(hex: 0xFFF4D6), Theme.gold], center: .center, startRadius: 1, endRadius: 13))
                .frame(width: 26, height: 26)
                .shadow(color: Theme.gold.opacity(0.8), radius: 10)
        } else {
            Image(systemName: "moon.fill").font(.title2).foregroundStyle(Color(hex: 0xF4EEDF))
                .shadow(color: .white.opacity(0.6), radius: 8)
        }
    }

    private func label(_ e: PrayerEvent, _ at: Date) -> some View {
        VStack(spacing: 0) {
            Text(l10n.t(Format.prayerKey(e))).font(.caption2.weight(.semibold))
            Text(Format.time(at, zone: zone, locale: l10n.locale)).font(.caption2).monospacedDigit().foregroundStyle(Theme.ink.opacity(0.8))
        }
        .lineLimit(1).fixedSize()
        .foregroundStyle(Theme.ink)
    }

    private var a11y: String {
        let s = Format.time(arc.start, zone: zone, locale: l10n.locale)
        let e = Format.time(arc.end, zone: zone, locale: l10n.locale)
        let pct = arc.fraction.formatted(.percent.precision(.fractionLength(0)).locale(l10n.locale))
        return arc.isDay ? l10n.t("sky_day_a11y", s, e, pct) : l10n.t("sky_night_a11y", s, e, pct)
    }
}

/// Buttons that brighten and shrink a little while pressed, so a tap is visibly received.
struct PressHighlight: ButtonStyle {
    var shape = AnyShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(Theme.ink.opacity(configuration.isPressed ? 0.12 : 0), in: shape)
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}
