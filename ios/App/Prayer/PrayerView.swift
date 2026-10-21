import NMCore
import SwiftUI

/// Values for one instant, computed from cached schedules (countdown derives from instants).
struct PrayerSnapshot {
    let now: Date
    let location: PrayerLocation?
    let today: DaySchedule?
    /// What the header counts down to: the next Adhan, or after an Adhan its iqama (with that prayer's name and the
    /// iqama's time), and during the prayer that prayer at its iqama (see `stage`).
    let next: Upcoming?
    let stage: PrayerMoment.Stage
    let current: PrayerEvent?
    let fajrEndsAt: Date?
    let qiblaBearing: Double?
    let qiblaDistance: Double?
    let sky: SkyPeriod
    /// Sunrise-to-Maghrib (or night) path for the sky card; nil without a location or on polar days.
    let arc: DayArc?

    @MainActor
    init(model: AppModel, now: Date) {
        self.now = now
        location = model.settings.location
        let days = model.days(now: now)
        today = days.count == 3 ? days[1] : nil
        let moment = model.calculator.moment(days, now: now, iqama: model.settings.iqama)
        stage = moment?.stage ?? .upcoming
        if let m = moment, m.stage != .upcoming {
            next = Upcoming(event: m.event, at: m.iqama ?? m.until, periodStart: m.adhan, isTomorrow: false)
        } else {
            next = moment?.next
        }
        if let t = today {
            current = PrayerEvent.allCases.last { (t[$0] ?? .distantFuture) <= now } ?? (days[0][.isha] != nil ? .isha : nil)
            fajrEndsAt = model.calculator.fajrEndsAt(t, now: now)
        } else {
            current = nil; fajrEndsAt = nil
        }
        qiblaBearing = location.map { Qibla.bearing(from: $0.location) }
        qiblaDistance = location.map { Qibla.distanceMeters(from: $0.location) }
        sky = location == nil ? .night : SkyPeriod.at(now, today: today, current: current)
        arc = location == nil ? nil : DayArc.at(now, days: days)
    }

    /// "1 hr 5 min left", "19 min until the iqama", or "Dhuhr prayer has begun".
    @MainActor
    func remainingText(_ l10n: Localization) -> String {
        guard let next else { return "" }
        let left = Format.remainingLong(PrayerCalculator.remaining(now, next.at), locale: l10n.locale)
        switch stage {
        case .upcoming: return l10n.t("remaining_long", left)
        case .toIqama: return l10n.t("iqama_left", left)
        case .prayer: return l10n.t("prayer_started", l10n.t(Format.prayerKey(next.event)))
        }
    }

    /// The prayer's name, or "Time for Dhuhr" while it waits for its iqama.
    @MainActor
    func title(_ l10n: Localization) -> String {
        guard let next else { return "" }
        let name = l10n.t(Format.prayerKey(next.event))
        return stage == .toIqama ? l10n.t("reminder_title", name) : name
    }

    /// Elapsed fraction of the current prayer period (time progress, not sun position).
    var progress: Double {
        guard let n = next, let start = n.periodStart else { return 0 }
        let total = n.at.timeIntervalSince(start)
        return total > 0 ? min(1, max(0, now.timeIntervalSince(start) / total)) : 0
    }
}

/// Prayer & Qibla in three views, changed by scrolling and tapping alone: the big header at the top (the
/// Qibla arc lit by the time of day, the next prayer, the nearest mosque); scrolling down shrinks it into
/// a bar across the top that keeps the next prayer and the Qibla arrow; tapping the big header opens the
/// full Qibla view with its guiding light. Below: the sun's path and today's times on glass.
struct PrayerView: View {
    @Environment(\.appModel) private var model
    @Environment(\.l10n) private var l10n
    @Binding var showSettings: Bool
    var onMosques: () -> Void = {}
    @State private var scrollOffset: CGFloat = 0
    @State private var showCity = false
    @State private var showCalc = false
    @State private var showCompass = false
    @State private var detector = AlignmentDetector()
    @State private var aligned = false
    @State private var mosque: RankedMosque?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Regular width (iPad, Mac): the wide layout.
    @Environment(\.horizontalSizeClass) private var hSize

    /// The bar replaces the header once most of the header has scrolled away.
    private var showCompact: Bool { scrollOffset > 300 }
    /// The big header fades as it scrolls up under the bar.
    private var heroOpacity: Double { 1 - min(1, max(0, (scrollOffset - 80) / 240)) }
    private var motion: Animation { reduceMotion ? .easeInOut(duration: 0.2) : Theme.spring }

    var body: some View {
        Group {
            if hSize == .regular { wide } else { narrow }
        }
        .task(id: nearestKey) { model.refreshNearestMosque() }
        .skyBackground()
        .toolbar(.hidden, for: .navigationBar)
        .sensoryFeedback(.success, trigger: aligned) { _, new in new }
        .onAppear {
            model.location.beginHeading()
            #if DEBUG
            if UserDefaults.standard.bool(forKey: "demoCompass") { showCompass = true }
            #endif
        }
        .onDisappear { model.location.endHeading() }
        .onChange(of: model.requestQibla, initial: true) { _, asked in
            if asked { model.requestQibla = false; showCompass = true }
        }
        .sheet(isPresented: $showCity) { CityPickerView() }
        .sheet(isPresented: $showCalc) { CalculationView() }
        .fullScreenCover(isPresented: $showCompass) { QiblaCompassView() }
        .sheet(item: $mosque) { r in
            MosqueDetailView(ranked: r, favorite: ((try? model.mosques?.favorites()) ?? []).contains(r.id),
                             canFavorite: r.mosque.packId.hasPrefix("mosques.")) { on in
                try? model.mosques?.setFavorite(r.id, on)
            }
            .presentationDetents([.medium, .large])
        }
    }

    /// iPad and Mac: two columns, the header (Qibla arc, next prayer, nearest mosque) beside the sun's path,
    /// today's times and the dates; nothing shrinks into the bar.
    private var wide: some View {
        TimelineView(.periodic(from: .now, by: 1)) { ctx in
            let snap = PrayerSnapshot(model: model, now: ctx.date)
            GeometryReader { geo in
                HStack(alignment: .top, spacing: 24) {
                    // Each column is centred on the screen's height while it fits, and scrolls when it does not.
                    ScrollView {
                        PrayerHero(snap: snap, compass: model.location.compass, aligned: aligned,
                                   onLocation: { showCity = true }, onSettings: { showSettings = true },
                                   onQibla: { showCompass = true }, onNearest: { mosque = $0 })
                            .padding(.vertical, 32)
                            .frame(minHeight: geo.size.height)
                    }
                    .scrollIndicators(.hidden)
                    .frame(maxWidth: 520)
                    ScrollView {
                        VStack(spacing: 14) {
                            if snap.location == nil {
                                ChooseLocationCard(onPickCity: { showCity = true })
                            } else {
                                content(snap)
                            }
                        }
                        .padding(.top, 60)
                        .padding(.bottom, 80)
                        .frame(minHeight: geo.size.height)
                    }
                    .scrollIndicators(.hidden)
                    .frame(maxWidth: 640)
                }
                .padding(.horizontal, 28)
                .frame(maxWidth: .infinity)
            }
            .onChange(of: model.location.compass) { _, c in updateAlignment(c, snap.qiblaBearing) }
        }
    }

    /// iPhone: one column; scrolling shrinks the header into the bar.
    private var narrow: some View {
        ScrollViewReader { proxy in
            TimelineView(.periodic(from: .now, by: 1)) { ctx in
                let snap = PrayerSnapshot(model: model, now: ctx.date)
                ZStack(alignment: .top) {
                    ScrollView {
                        VStack(spacing: 14) {
                            PrayerHero(snap: snap, compass: model.location.compass, aligned: aligned,
                                       onLocation: { showCity = true }, onSettings: { showSettings = true },
                                       onQibla: { showCompass = true }, onNearest: { mosque = $0 })
                                .opacity(heroOpacity)
                                .scaleEffect(reduceMotion ? 1 : 0.9 + 0.1 * heroOpacity, anchor: .top)
                                .id("top")
                            if snap.location == nil {
                                ChooseLocationCard(onPickCity: { showCity = true })
                            } else {
                                content(snap)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 32)
                    }
                    .scrollIndicators(.hidden)
                    .onScrollGeometryChange(for: CGFloat.self) { $0.contentOffset.y + $0.contentInsets.top } action: { _, v in scrollOffset = v }
                    if showCompact, snap.next != nil {
                        CompactPrayerBar(snap: snap, compass: model.location.compass, aligned: aligned) {
                            withAnimation(motion) { proxy.scrollTo("top", anchor: .top) }
                        }
                        .transition(.move(edge: .top).combined(with: .opacity))
                    }
                }
                .animation(motion, value: showCompact)
                .onChange(of: model.location.compass) { _, c in updateAlignment(c, snap.qiblaBearing) }
                #if DEBUG
                .task {
                    if DemoPreview.kind == "header" { await headerTour(proxy, bearing: snap.qiblaBearing); return }
                    // CI screenshots: `-demoCompact YES` scrolls down so the header shrinks into the bar;
                    // `-demoCompanionCard YES` scrolls to the companion.
                    let card = UserDefaults.standard.bool(forKey: "demoCompanionCard")
                    guard card || UserDefaults.standard.bool(forKey: "demoCompact") else { return }
                    try? await Task.sleep(for: .seconds(2))
                    proxy.scrollTo(card ? "companion" : "schedule", anchor: card ? .center : .top)
                }
                #endif
            }
        }
    }

    #if DEBUG
    /// CI App Store preview (`-demoPreview header`): the big header, scrolled into the bar and back, then a tap into
    /// the Qibla view while the phone turns until it faces the Qibla.
    private func headerTour(_ proxy: ScrollViewProxy, bearing: Double?) async {
        await DemoPreview.waitForStart()
        withAnimation(.easeInOut(duration: 1.4)) { proxy.scrollTo("schedule", anchor: .top) }
        await DemoPreview.pause(4)
        withAnimation(.easeInOut(duration: 1.4)) { proxy.scrollTo("top", anchor: .top) }
        await DemoPreview.pause(3)
        guard let b = bearing ?? model.settings.location.map({ Qibla.bearing(from: $0.location) }) else { return }
        // From 75° off, easing in with a small overshoot, as a hand turns the phone.
        model.location.demoHeading(b + 75 + 360)
        showCompass = true
        await DemoPreview.pause(1.5)
        let steps = 150
        for i in 1...steps {
            let t = Double(i) / Double(steps)
            let ease = 1 - pow(1 - t, 3)
            let off = 75 * (1 - ease) - 9 * sin(.pi * min(1, t * 1.4)) * (1 - t)
            model.location.demoHeading(b + off + 360)
            try? await Task.sleep(for: .milliseconds(33))
        }
        model.location.demoHeading(b + 360)
    }
    #endif

    /// Changes when the phone or the prayer city moves, or the data becomes ready.
    private var nearestKey: String {
        let p = model.location.position?.location ?? model.settings.location?.location
        return "\(model.ready) \(p?.latitude ?? 0) \(p?.longitude ?? 0)"
    }

    private func updateAlignment(_ c: CompassState, _ bearing: Double?) {
        guard case let .live(heading, accuracy, _) = c, let b = bearing else {
            _ = detector.update(relativeDeg: 180, accuracyDeg: nil); aligned = false; return
        }
        _ = detector.update(relativeDeg: Angles.relativeToQibla(qiblaBearingTrue: b, headingTrue: heading), accuracyDeg: accuracy)
        aligned = detector.aligned
    }

    @ViewBuilder private func content(_ snap: PrayerSnapshot) -> some View {
        let loc = snap.location!
        // Alerts are on but cannot arrive (notifications, or alarms for the Adhan, not allowed).
        if let block = model.alertBlock {
            AlertAccessNotice(block: block).frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(Theme.ink).glassCard()
        }
        if snap.today?.status.isEstimated == true {
            Label(l10n.t("estimated_badge"), systemImage: "exclamationmark.circle")
                .font(.footnote.weight(.semibold)).foregroundStyle(Theme.accent)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .glass(Capsule())
        }
        if !loc.zoneConfirmed { ZoneConfirmCard(location: loc, onChange: { showCity = true }) }
        if snap.today?.status == .unavailable { PolarCard() }
        if let arc = snap.arc {
            DayArcView(arc: arc, period: snap.sky, zone: loc.zone).padding(6).glassCard(padding: 0)
        }
        if model.settings.companion != CompanionKind.none.rawValue, snap.today?.status != .unavailable { CompanionCard(snap: snap).id("companion") }
        if let today = snap.today, today.status != .unavailable { ScheduleCard(snap: snap, today: today).id("schedule") }
        DatesCard(snap: snap, onCalculation: { showCalc = true })
    }
}

private struct ChooseLocationCard: View {
    @Environment(\.appModel) private var model
    @Environment(\.l10n) private var l10n
    var onPickCity: () -> Void
    @State private var locating = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(l10n.t("choose_city_title")).font(.title2.weight(.semibold)).accessibilityAddTraits(.isHeader)
            Text(l10n.t("choose_city_body")).foregroundStyle(Theme.ink.opacity(0.85))
            GlassGroup {
                VStack(spacing: 10) {
                    Button {
                        Task {
                            locating = true
                            switch await model.useDeviceLocation() {
                            case .ok: error = nil
                            case .denied: error = l10n.t("location_denied")
                            case .noFix: error = l10n.t("location_fix_failed")
                            }
                            locating = false
                        }
                    } label: {
                        Label(l10n.t(locating ? "locating" : "use_my_location"), systemImage: "location.fill").frame(maxWidth: .infinity, minHeight: 32)
                    }
                    .prominentButton().disabled(locating)
                    Button(action: onPickCity) {
                        Label(l10n.t("search_city"), systemImage: "magnifyingglass").frame(maxWidth: .infinity, minHeight: 32)
                    }
                    .glassButton()
                }
            }
            if let error { Text(error).font(.footnote).foregroundStyle(Color(hex: 0xF2B8B5)) }
        }
        .foregroundStyle(Theme.ink)
        .glassCard(padding: 20)
    }
}

private struct ZoneConfirmCard: View {
    @Environment(\.appModel) private var model
    @Environment(\.l10n) private var l10n
    let location: PrayerLocation
    var onChange: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(l10n.t("confirm_time_zone_title"), systemImage: "clock.badge.questionmark").font(.headline)
            Text(l10n.t("confirm_time_zone_body", Format.zoneName(location.zone, locale: l10n.locale))).font(.subheadline)
            HStack {
                Button(l10n.t("use_this_time_zone")) { model.settings.location?.zoneConfirmed = true }.prominentButton()
                Button(l10n.t("change"), action: onChange).glassButton()
            }
        }
        .foregroundStyle(Theme.ink)
        .glassCard()
    }
}

private struct PolarCard: View {
    @Environment(\.appModel) private var model
    @Environment(\.l10n) private var l10n
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(l10n.t("polar_unavailable_title"), systemImage: "exclamationmark.triangle").font(.headline)
            Text(l10n.t("polar_unavailable_body")).font(.subheadline)
            Button(l10n.t("polar_use_nearest")) { model.setPolarRule(.NEAREST_LATITUDE) }.prominentButton()
        }
        .foregroundStyle(Theme.ink)
        .glassCard()
    }
}

/// Today's times on glass. The upcoming prayer sits in a gold glass pill with an "Upcoming" badge.
struct ScheduleCard: View {
    @Environment(\.appModel) private var model
    @Environment(\.l10n) private var l10n
    let snap: PrayerSnapshot
    let today: DaySchedule

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(l10n.t("todays_times")).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink.opacity(0.8))
                .accessibilityAddTraits(.isHeader).padding(.horizontal, 8).padding(.bottom, 6)
            GlassGroup(spacing: 4) {
                VStack(spacing: 2) {
                    ForEach(PrayerEvent.allCases, id: \.self) { e in
                        if let at = today[e] { row(e, at) }
                    }
                }
            }
            Text(l10n.t("sunrise_not_prayer_note")).font(.caption).foregroundStyle(Theme.ink.opacity(0.7)).padding(.horizontal, 8).padding(.top, 8)
        }
        .foregroundStyle(Theme.ink)
        .glassCard(padding: 10)
    }

    @ViewBuilder private func row(_ e: PrayerEvent, _ at: Date) -> some View {
        let isNext = snap.next?.event == e && snap.next?.isTomorrow == false
        let name = l10n.t(Format.prayerKey(e))
        let nextDay = CivilDate.of(at, in: today.zone) > today.date
        HStack(spacing: 12) {
            Image(systemName: Theme.icon(e)).font(.body).frame(minWidth: 26)
                .foregroundStyle(isNext ? Theme.accent : Theme.ink.opacity(e.isPrayer ? 0.9 : 0.6))
                .accessibilityHidden(true)
            Text(name).font(.body.weight(isNext ? .semibold : .regular))
                .foregroundStyle(isNext ? Theme.accent : Theme.ink.opacity(e.isPrayer ? 1 : 0.7))
            if isNext {
                Text(l10n.t("upcoming")).font(.caption2.weight(.semibold)).foregroundStyle(Theme.accent)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .overlay(Capsule().stroke(Theme.gold.opacity(0.7), lineWidth: 1))
            }
            Spacer(minLength: 4)
            Text(Format.time(at, zone: today.zone, locale: l10n.locale) + (nextDay ? " (\(l10n.t("next_day")))" : ""))
                .font(.title3.weight(isNext ? .semibold : .regular)).monospacedDigit()
                .foregroundStyle(isNext ? Theme.accent : Theme.ink)
            // Every row has a bell, Sunrise too (a notification that Fajr time has ended). It shows what comes at the
            // prayer's time, the Adhan too, and turns both on or off.
            let adhan = model.settings.alerts.adhanPlays(e)
            let on = adhan || model.settings.reminders.contains(e)
            Button { Task { await model.setPrayerCall(e, !on) } } label: {
                Image(systemName: adhan ? "speaker.wave.2.fill" : on ? "bell.fill" : "bell.slash").font(.subheadline)
                    .foregroundStyle(on ? Theme.accent : Theme.ink.opacity(0.55))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(l10n.t(adhan ? "adhan_on_a11y" : on ? "reminder_on_a11y" : "reminder_off_a11y", name))
        }
        .padding(.leading, 10)
        .frame(minHeight: 52)
        .background {
            if isNext {
                RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Theme.gold.opacity(0.14))
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Theme.gold.opacity(0.55), lineWidth: 1))
            }
        }
        .accessibilityElement(children: .combine)
    }
}

private struct DatesCard: View {
    @Environment(\.appModel) private var model
    @Environment(\.l10n) private var l10n
    let snap: PrayerSnapshot
    var onCalculation: () -> Void

    var body: some View {
        if let loc = snap.location {
            let d = CivilDate.of(snap.now, in: loc.zone)
            VStack(alignment: .leading, spacing: 6) {
                Text(Format.gregorian(d, locale: l10n.locale)).font(.headline)
                Text(Format.hijri(d, adjustment: model.settings.prayer.hijriAdjustmentDays, locale: l10n.locale)).foregroundStyle(Theme.accent)
                Text(l10n.t("hijri_calendar_note")).font(.caption).foregroundStyle(Theme.ink.opacity(0.7))
                Divider().overlay(Theme.ink.opacity(0.2)).padding(.vertical, 4)
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(l10n.t(Format.methodKey(model.settings.prayer.method))).font(.subheadline)
                        Text(l10n.t("time_zone_label", Format.zoneName(loc.zone, locale: l10n.locale))).font(.caption).foregroundStyle(Theme.ink.opacity(0.7))
                    }
                    Spacer()
                    Button(l10n.t("calculation"), action: onCalculation).glassButton()
                }
            }
            .foregroundStyle(Theme.ink)
            .glassCard()
        }
    }
}
