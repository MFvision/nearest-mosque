import NMCore
import SwiftUI
import UIKit

// Building, the day built from the answers (the arc with the sun or the moon to drag through it, the countdown, the
// times and three cards), exactly what leaves the device with the notification ask, and done.

// MARK: - Building

/// "Building your prayer day…": three lines tick to a check 450 ms apart over a pulsing skeleton of the day, then the
/// day. No Back or Skip.
struct BuildingStep: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.onb) private var onb
    @State private var breathe = false

    var body: some View {
        let s = app.settings
        let lines = [
            l10n.t("onb2_building_method", l10n.t(Format.methodShortKey(s.prayer.method))),
            l10n.t("onb2_building_qibla", s.location?.name ?? ""),
            l10n.t("onb2_building_sun"),
        ]
        VStack(spacing: 0) {
            Text(l10n.t("onb2_building"))
                .font(.title2.weight(.semibold))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .modifier(OnSky())
                .onbEnter(0)
            skeleton
                .padding(.top, 18)
                .opacity(breathe ? 0.45 : 1)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 12) {
                ForEach(lines.indices, id: \.self) { i in
                    HStack(spacing: 12) {
                        Group {
                            if model.buildingDone > i {
                                OnbCheck().transition(.checkPop(reduce: reduceMotion))
                            } else if model.buildingDone == i {
                                ProgressView().controlSize(.small)
                            } else {
                                Circle().strokeBorder(OnbColor.placeholder, lineWidth: 1.5).frame(width: 20, height: 20)
                            }
                        }
                        .frame(width: 24, height: 24)
                        Text(lines[i])
                            .font(.subheadline)
                            .foregroundStyle(model.buildingDone > i ? Theme.ink : OnbColor.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .modifier(OnSky())
                }
            }
            .padding(.top, 22)
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.updatesFrequently)
            .onbEnter(1)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: onb.wide ? 420 : .infinity, alignment: .top)
        .onAppear {
            // A 1.5 s pulse, still under Reduce Motion and in screenshots.
            guard !reduceMotion, !onb.still else { return }
            withAnimation(.easeInOut(duration: 0.75).repeatForever(autoreverses: true)) { breathe = true }
        }
    }

    /// The arc and three cards to come, as outlines.
    private var skeleton: some View {
        VStack(spacing: 14) {
            GeometryReader { geo in
                let g = OnbArcGeometry(size: geo.size, chord: geo.size.width * 0.7, rtl: false)
                Path { p in
                    for i in 0...48 {
                        let pt = g.point(Double(i) / 48)
                        if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
                    }
                }
                .stroke(OnbColor.track, style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }
            .frame(height: 130)
            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 20, style: .continuous).fill(OnbColor.chip).frame(height: 80)
                }
            }
        }
    }
}

// MARK: - Day

/// The day built from the answers: the arc (on the leading pane on big screens), the next prayer counting down, the
/// six times, and the Qibla, the Hijri date and the weather (real data only) or the length of the day.
struct DayStep: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.onb) private var onb

    var body: some View {
        let loc = app.settings.location
        OnbPage(model: model) {
            VStack(alignment: .leading, spacing: 0) {
                OnbEyebrow(text: l10n.t("onb2_day_eyebrow")).onbEnter(0)
                Text(l10n.t("onb2_day_title", loc?.name ?? ""))
                    .font(.title2.bold())
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .padding(.top, 4)
                    .accessibilityAddTraits(.isHeader)
                    .modifier(OnSky())
                    .onbEnter(0)
                if !onb.wide, model.hasArc {
                    OnbDayArc(model: model)
                        .padding(.top, 8)
                        .onbEnter(1)
                }
                countdown.padding(.top, onb.wide ? 16 : 4).onbEnter(2)
                if !onb.wide, model.hasArc {
                    OnbHalfControls(model: model).padding(.top, 10).onbEnter(2)
                }
                times.padding(.top, 14).onbEnter(3)
                OnbDayCards(model: model).padding(.top, 12).onbEnter(3)
            }
        } footer: {
            OnbPrimaryButton(title: l10n.t("onb2_continue")) { model.next() }
        }
    }

    private var next: Upcoming? { app.calculator.nextPrayer(model.days, now: model.now) }

    @ViewBuilder private var countdown: some View {
        if let next, let zone = app.settings.location?.zone {
            let name = l10n.t(Format.prayerKey(next.event))
            let time = Format.time(next.at, zone: zone, locale: l10n.locale)
            let left = Format.countdown(PrayerCalculator.remaining(model.now, next.at), locale: l10n.locale)
            VStack(spacing: 2) {
                Text(l10n.t("onb2_countdown", name, Weather.isolate(left)))
                    .font(.title3.weight(.semibold))
                    .monospacedDigit()
                Text(l10n.t("onb2_at_time", time))
                    .font(.footnote)
                    .monospacedDigit()
                    .foregroundStyle(OnbColor.secondary)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .modifier(OnSky())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(l10n.t("countdown_a11y", name, time, left))
            .accessibilityAddTraits(.updatesFrequently)
        }
    }

    /// Six columns, the next prayer in the accent (after Isha, tomorrow's times with tomorrow's Fajr next).
    @ViewBuilder private var times: some View {
        let days = model.days
        if days.count == 3, let zone = app.settings.location?.zone {
            let next = self.next
            let row = next?.isTomorrow == true ? days[2] : days[1]
            HStack(spacing: 0) {
                ForEach(PrayerEvent.allCases, id: \.self) { e in
                    let isNext = next?.event == e
                    VStack(spacing: 2) {
                        Text(l10n.t(Format.prayerKey(e)))
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(isNext ? Theme.accent : OnbColor.secondary)
                        Text(row[e].map { Format.time($0, zone: zone, locale: l10n.locale) } ?? "—")
                            .font(.caption.weight(.semibold))
                            .monospacedDigit()
                            .foregroundStyle(isNext ? Theme.accent : Theme.ink)
                    }
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(isNext ? Theme.gold.opacity(0.2) : .clear, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .accessibilityElement(children: .combine)
                }
            }
            .padding(4)
            .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
    }
}

/// The arc of the half shown, with the disc's track, the prayers on it and the ends below. The disc itself is the
/// flow's sun (OnboardingView draws it from this frame). Drag it to walk through the day: the sky follows, a bubble
/// says the moment, past an end the half turns over, and on release it springs back to now. VoiceOver, Switch Control
/// and the arrow keys move it a quarter of an hour at a time.
struct OnbDayArc: View {
    let model: OnboardingModel
    /// The chord as a share of the width (the tokens' arcDiameterFraction on one column).
    var chordShare: CGFloat = 0.86
    var interactive = true
    @Environment(\.l10n) private var l10n
    @Environment(\.appModel) private var app
    @State private var width: CGFloat = 342
    @State private var anchor: (start: Double, finger: Double)?

    var body: some View {
        let chord = width * chordShare
        GeometryReader { geo in
            let g = OnbArcGeometry(size: geo.size, chord: geo.size.width * chordShare, rtl: l10n.isRTL)
            ZStack(alignment: .topLeading) {
                if let arc = model.arc { drawing(arc, g) }
            }
            .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 2).onChanged { changed($0, g) }.onEnded { _ in ended() }, including: interactive ? .all : .none)
        }
        .frame(height: OnbArcGeometry.height(chord: chord))
        .environment(\.layoutDirection, .leftToRight)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(OnbFrame.space)) } action: { r in
            model.arcBox = r
            model.arcChord = r.width * chordShare
        }
        .accessibilityElement()
        .accessibilityLabel(l10n.t("onb2_time_of_day"))
        .accessibilityValue(valueText)
        .accessibilityAdjustableAction { direction in
            model.stepArc(direction == .increment ? 1 : -1)
        }
        .accessibilityHidden(!interactive)
        .focusable(interactive)
        .onKeyPress(.leftArrow) { model.stepArc(l10n.isRTL ? 1 : -1); return .handled }
        .onKeyPress(.rightArrow) { model.stepArc(l10n.isRTL ? -1 : 1); return .handled }
        .onKeyPress(.escape) {
            guard model.drag != nil || model.half != nil else { return .ignored }
            model.endDrag()
            return .handled
        }
    }

    private var valueText: String {
        let t = model.dragInstant ?? model.now
        return model.dragLabel(t)
    }

    @ViewBuilder private func drawing(_ arc: DayArc, _ g: OnbArcGeometry) -> some View {
        let shown = min(1, max(0, model.shownFraction))
        let zone = app.settings.location?.zone ?? .current
        path(g, to: 1)
            .stroke(OnbColor.track, style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [1, 7]))
        path(g, to: shown)
            .stroke(Theme.gold, style: StrokeStyle(lineWidth: 3, lineCap: .round))
        ForEach(arc.marks, id: \.at) { m in
            Circle()
                .fill(m.fraction <= shown ? Theme.gold : Theme.ink)
                .frame(width: 9, height: 9)
                .shadow(color: .black.opacity(0.16), radius: 1.5)
                .position(g.point(m.fraction))
            Text(l10n.t(Format.prayerKey(m.event)))
                .font(.caption2.weight(.semibold))
                .foregroundStyle(OnbColor.secondary)
                .fixedSize()
                .position(g.point(m.fraction, radius: g.radius + 17))
        }
        end(arc.startEvent, arc.start, zone).position(x: g.point(0).x, y: g.base + 22)
        end(arc.endEvent, arc.end, zone).position(x: g.point(1).x, y: g.base + 22)
    }

    private func end(_ e: PrayerEvent, _ at: Date, _ zone: TimeZone) -> some View {
        VStack(spacing: 0) {
            Text(l10n.t(Format.prayerKey(e))).font(.caption2).foregroundStyle(OnbColor.secondary)
            Text(Format.time(at, zone: zone, locale: l10n.locale)).font(.caption.weight(.semibold)).monospacedDigit()
        }
        .lineLimit(1)
        .fixedSize()
        .modifier(OnSky())
    }

    private func path(_ g: OnbArcGeometry, to f: Double) -> Path {
        Path { p in
            let n = max(2, Int(60 * f))
            for i in 0...n {
                let pt = g.point(f * Double(i) / Double(n))
                if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
            }
        }
    }

    /// The disc follows the finger from where it touched; going 0.1 past an end turns to the other half.
    private func changed(_ v: DragGesture.Value, _ g: OnbArcGeometry) {
        let finger = g.fraction(atX: v.location.x)
        if anchor == nil {
            let start = min(1.12, max(-0.12, g.fraction(atX: v.startLocation.x)))
            anchor = (start, g.fraction(atX: v.startLocation.x))
            model.beginDrag(start)
        }
        guard let a = anchor else { return }
        var f = a.start + (finger - a.finger)
        if f > 1.1 {
            model.flipHalf()
            anchor = (-0.1, finger)
            f = -0.1
        } else if f < -0.1 {
            model.flipHalf()
            anchor = (1.1, finger)
            f = 1.1
        }
        model.drag = min(1.12, max(-0.12, f))
    }

    private func ended() {
        anchor = nil
        model.endDrag()
    }
}

/// "Drag the sun to walk through your day" and the Day | Night control (the same for keyboard and switch users).
struct OnbHalfControls: View {
    let model: OnboardingModel
    @Environment(\.l10n) private var l10n

    var body: some View {
        VStack(spacing: 8) {
            Text(l10n.t(model.shownHalf == .night ? "onb2_hint_moon" : "onb2_hint_sun"))
                .font(.footnote)
                .foregroundStyle(OnbColor.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .modifier(OnSky())
            Picker(l10n.t("onb2_time_of_day"), selection: Binding(get: { model.shownHalf }, set: { model.selectHalf($0) })) {
                Text(l10n.t("onb2_day")).tag(DayArc.Half.day)
                Text(l10n.t("onb2_night")).tag(DayArc.Half.night)
            }
            .pickerStyle(.segmented)
            .frame(maxWidth: 240)
        }
        .frame(maxWidth: .infinity)
    }
}

/// On big screens the leading pane holds the large arc on the day, privacy and done (draggable on the day only), its
/// ends at 56% of the height, with the hint and the Day | Night control on the day.
struct OnbArcPane: View {
    let model: OnboardingModel
    let frame: OnbFrame

    var body: some View {
        let chord = 0.86 * frame.paneWidth
        let box = CGSize(width: chord + 64, height: OnbArcGeometry.height(chord: chord))
        let top = max(84, frame.size.height * 0.56 - (box.height - 34))
        VStack(spacing: 12) {
            OnbDayArc(model: model, chordShare: chord / box.width, interactive: model.step == .day)
                .frame(width: box.width)
            if model.step == .day {
                OnbHalfControls(model: model)
                    .padding(.horizontal, 24)
                    .transition(.opacity)
            }
        }
        .padding(.top, top)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}

/// The Qibla from the place, today's Hijri date, and the weather (when the weather sky has real data) or the length
/// of the day. One row of three; one column at the largest text sizes.
struct OnbDayCards: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.sky) private var sky

    var body: some View {
        let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(spacing: 8)) : AnyLayout(HStackLayout(alignment: .top, spacing: 8))
        if let loc = app.settings.location {
            layout {
                qibla(loc)
                today(loc)
                third(loc)
            }
        }
    }

    private func card<Visual: View>(_ label: String, value: String, sub: String? = nil, @ViewBuilder visual: () -> Visual) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label)
                .font(.caption2.weight(.bold))
                .textCase(l10n.isRTL ? nil : .uppercase)
                .foregroundStyle(Theme.accent)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            visual()
                .frame(width: 34, height: 34)
                .padding(.top, 8)
                .accessibilityHidden(true)
            Spacer(minLength: 8)
            Text(value)
                .font(.footnote.weight(.semibold))
                .monospacedDigit()
                .fixedSize(horizontal: false, vertical: true)
            if let sub {
                Text(sub).font(.caption2).foregroundStyle(OnbColor.secondary).lineLimit(2)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity, minHeight: 116, alignment: .topLeading)
        .glass(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private func qibla(_ loc: PrayerLocation) -> some View {
        let bearing = Qibla.bearing(from: loc.location)
        let atKaaba = Qibla.distanceMeters(from: loc.location) < 1000
        return card(l10n.t("qibla"), value: atKaaba ? l10n.t("onb2_at_kaaba") : l10n.t("qibla_bearing", Format.degrees(bearing, locale: l10n.locale))) {
            ZStack {
                Circle().strokeBorder(OnbColor.track, lineWidth: 1.5)
                Image(systemName: "location.north.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.accent)
                    .rotationEffect(.degrees(atKaaba ? 0 : bearing))
                    .opacity(atKaaba ? 0.25 : 1)
            }
            .environment(\.layoutDirection, .leftToRight)
        }
    }

    private func today(_ loc: PrayerLocation) -> some View {
        let date = CivilDate.of(model.now, in: loc.zone)
        return card(l10n.t("onb2_today"), value: Format.hijri(date, adjustment: app.settings.prayer.hijriAdjustmentDays, locale: l10n.locale)) {
            Image(systemName: "calendar").font(.system(size: 22)).foregroundStyle(Theme.accent)
        }
    }

    /// Real weather only (the weather step's samples never show here); otherwise sunrise to Maghrib.
    @ViewBuilder private func third(_ loc: PrayerLocation) -> some View {
        if let w = app.skyWeather(now: model.now) {
            let units = app.weather.units
            let temp = Weather.isolate(l10n.t(units.temperature.stringKey, number(units.temperature(w.temperatureC))))
            card(l10n.t("onb2_weather"), value: l10n.t(w.symbol.condition.stringKey), sub: temp) {
                Image(systemName: w.symbol.condition.symbolName(night: sky.period.hasStars))
                    .symbolRenderingMode(.hierarchical)
                    .font(.system(size: 24))
                    .foregroundStyle(Theme.accent)
            }
        } else {
            let days = model.days
            let length: TimeInterval? = days.count == 3 ? days[1][.sunrise].flatMap { s in days[1][.maghrib].map { $0.timeIntervalSince(s) } } : nil
            let minutes = Int(((length ?? 0) / 60).rounded())
            card(l10n.t("onb2_daylight"),
                 value: length == nil ? "—" : l10n.t("onb2_duration", number(minutes / 60), number(minutes % 60))) {
                Image(systemName: "sun.max").font(.system(size: 22)).foregroundStyle(Theme.accent)
            }
        }
    }

    private func number(_ v: Int) -> String { Format.number(Double(v), digits: 0, locale: l10n.locale) }
}

// MARK: - Privacy

/// Exactly what leaves the device, as a timeline, then the notification ask: Allow (the system asks only then) or,
/// with nothing that needs notifications, Finish.
struct PrivacyStep: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.onb) private var onb
    @Environment(\.openURL) private var openURL

    var body: some View {
        OnbPage(model: model) {
            VStack(alignment: .leading, spacing: 0) {
                OnbTitle(text: l10n.t(onb.device("onb2_privacy_title"))).onbEnter(0)
                VStack(alignment: .leading, spacing: 0) {
                    node(icon: "lock.fill", first: true, title: l10n.t(onb.device("onb2_privacy_n1_title")), body: l10n.t("onb2_privacy_n1_body"))
                    node(icon: "bell.fill", title: l10n.t("onb2_privacy_n2_title"), body: l10n.t(onb.device("onb2_privacy_n2_body")))
                    node(icon: "location.fill", last: true, title: l10n.t("onb2_privacy_n3_title"), body: l10n.t("onb2_privacy_n3_body")) {
                        pill
                    }
                }
                .padding(.top, 20)
                .onbEnter(1)
                OnbSwitchRow(icon: .symbol("bell.badge"), title: l10n.t("onb2_notify"), isOn: model.notify) { on in
                    withAnimation(OnbMotion.spring) { model.notify = on }
                }
                .padding(.top, 4)
                .onbEnter(2)
            }
        } footer: {
            VStack(spacing: 8) {
                OnbMorphButton(title: l10n.t(model.wantsNotifications && !model.notificationsDenied ? "onb2_allow" : "onb2_finish"), phase: model.allow) {
                    model.allowTapped()
                }
                .onbEnter(4)
                VStack(spacing: 2) {
                    if model.wantsNotifications && model.notificationsDenied {
                        // Off for the app (no prompt will come): where to turn them on.
                        OnbFootnote(text: l10n.t("notifications_denied"))
                        Button {
                            if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                        } label: {
                            Text(l10n.t("open_settings"))
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(Theme.accent)
                                .padding(.horizontal, 12)
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.onbPress)
                        .hoverEffect(.highlight)
                        .modifier(OnSky())
                    } else if model.wantsNotifications && model.promptPending {
                        OnbFootnote(text: l10n.t(onb.device("onb2_ask_confirm")))
                    }
                    OnbFootnote(text: l10n.t("onb2_change_later"))
                }
                .multilineTextAlignment(.center)
                .onbEnter(5)
            }
        }
    }

    private func node(icon: String, first: Bool = false, last: Bool = false, title: String, body: String) -> some View {
        node(icon: icon, first: first, last: last, title: title, body: body) { EmptyView() }
    }

    private func node<Extra: View>(icon: String, first: Bool = false, last: Bool = false, title: String, body: String,
                                   @ViewBuilder extra: () -> Extra) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(first ? Theme.navyNight : Theme.accent)
                .frame(width: 32, height: 32)
                .background(first ? Theme.gold : Color.clear, in: Circle())
                .glass(Circle())
                .overlay(Circle().strokeBorder(Theme.gold, lineWidth: 2))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(body)
                    .font(.subheadline)
                    .foregroundStyle(OnbColor.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                extra()
            }
            .padding(.top, 4)
            .modifier(OnSky())
        }
        .padding(.bottom, last ? 8 : 16)
        .background(alignment: .topLeading) {
            if !last {
                LinearGradient(colors: [Theme.gold, Theme.gold.opacity(0.25)], startPoint: .top, endPoint: .bottom)
                    .frame(width: 2)
                    .padding(.top, 36)
                    .padding(.bottom, 2)
                    .padding(.leading, 15)
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// Whether the weather sky is on (off until turned on).
    private var pill: some View {
        let on = app.settings.skyWeather
        return HStack(spacing: 7) {
            Circle()
                .fill(on ? Color(hex: 0x5ED38A) : OnbColor.secondary)
                .frame(width: 7, height: 7)
                .shadow(color: on ? Color(hex: 0x5ED38A).opacity(0.8) : .clear, radius: 3)
            Text(l10n.t(on ? "onb2_privacy_weather_on" : "onb2_privacy_weather_off"))
                .font(.caption.weight(.semibold))
        }
        .padding(.horizontal, 11)
        .frame(minHeight: 26)
        .background(OnbColor.chip, in: Capsule())
        .padding(.top, 8)
    }
}

// MARK: - Done

/// The header dissolves, the sky turns to the place's real period, the sun lands where it really is and the skyline
/// rises: "You're all set." with the next prayer, and the content offer for languages without built-in content.
struct DoneStep: View {
    let model: OnboardingModel
    let onOpen: () -> Void
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n

    var body: some View {
        OnbPage(model: model, bottom: true) {
            VStack(spacing: 0) {
                OnbTitle(text: l10n.t("onb2_done_title"), large: true).onbEnter(0)
                if let sub {
                    OnbBodyText(text: sub).padding(.top, 10).onbEnter(1)
                }
                if ContentDownloadCard.offered(l10n.language, app) {
                    ContentDownloadCard(lang: l10n.language, onSky: true)
                        .multilineTextAlignment(.leading)
                        .padding(.top, 18)
                        .onbEnter(2)
                }
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
        } footer: {
            OnbPrimaryButton(title: l10n.t("onb2_open_app"), action: onOpen)
        }
    }

    /// "Asr at 3:21 PM in Riyadh." and "We'll call you." when the device will (notifications allowed, and a bell or
    /// an Adhan on).
    private var sub: String? {
        guard let loc = app.settings.location, let next = app.calculator.nextPrayer(model.days, now: model.now) else { return nil }
        let line = l10n.t("onb2_done_sub", l10n.t(Format.prayerKey(next.event)), Format.time(next.at, zone: loc.zone, locale: l10n.locale), loc.name)
        let a = app.settings.alerts
        let calls = model.notificationsAllowed && (!app.settings.reminders.isEmpty || a.adhan || a.fajrAdhan)
        return calls ? line + " " + l10n.t("onb2_done_notify") : line
    }
}
