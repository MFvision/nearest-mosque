import NMCore
import SwiftUI
import UserNotifications

/// The flow's state: the step, what has been answered, the timers, and the few values the views share (the drag on
/// the arc, the header slot and the arc for the sun). Every answer is saved at once through AppModel, as Settings does.
@MainActor
@Observable
final class OnboardingModel {
    let app: AppModel
    let flow = OnboardingFlow(hasLook: true)
    let still: Bool
    private(set) var step: OnboardingStep
    /// The 200 ms while the old step fades out.
    private(set) var leaving = false
    /// Steps answered or passed (their chips show).
    private(set) var answered: Set<OnboardingStep> = []
    /// The Adhan step was shown, with its quiet switch (so "Also in Near Mosque" leaves quiet out).
    private(set) var adhanVisited = false
    /// The city and method chips (a place was set before the flow, or on its place step).
    private(set) var placeShown = false
    /// Counted up to pop a chip again when its answer changes.
    private(set) var bumps: [OnbChip: Int] = [:]
    /// Counted up each time a chip flies in (a selection tap on the phone).
    private(set) var flights = 0
    /// Counted up when a drag turns the arc over to the other half (a tap too).
    private(set) var flips = 0
    /// Counted up when the disc jumps along the arc under Reduce Motion (it fades out and in again there).
    private(set) var arcJumps = 0
    var reduceMotion = false
    var sheet: OnbSheet?
    private(set) var now = Date()

    // Place
    var locating = false
    var locateError: String?
    /// The row that set the place on this step (ringed and checked).
    private(set) var placeSource: PrayerLocation.Source?
    private(set) var placePulse = 0
    // Asr and Adhan
    private(set) var asrPulse = 0
    /// The choice tapped on the Adhan step (nil: nothing tapped yet, the current setting is shown).
    private(set) var adhanPick: AdhanChoice?
    private(set) var adhanPulse = 0
    // Weather
    private(set) var previewCondition: WeatherCondition = .clear
    private(set) var autoCycle = true
    // Building
    private(set) var buildingDone = 0
    // Day: the dragged fraction (nil: now), the half shown (nil: the one in progress)
    var drag: Double?
    var half: DayArc.Half?
    var dragging = false
    // Privacy and done
    var notify = true
    private(set) var allow: AllowPhase = .idle
    /// The system will really ask (notifications not decided yet), so the "will ask you to confirm" line shows.
    private(set) var promptPending = false
    /// Notifications are turned off for the app (before, or just now in the prompt): no check, the way to Settings.
    private(set) var notificationsDenied = false
    private(set) var notificationsAllowed = false

    // Geometry for the sun, in the root's coordinates
    var slotFrame: CGRect = .zero
    var arcBox: CGRect = .zero
    var arcChord: CGFloat = 0
    /// The step's content height (the glass panel hugs it on big screens).
    var pageHeight: CGFloat = 0

    @ObservationIgnored private var timers: [Task<Void, Never>] = []
    @ObservationIgnored private var clock: Task<Void, Never>?
    /// The fade-out, then enter(): not one of the timers, so cancelTimers() can never leave the step hidden.
    @ObservationIgnored private var entering: Task<Void, Never>?

    /// Old -demoOnboardingPage values (the tour's six pages) as the nearest new steps.
    private static let pages: [OnboardingStep] = [.promise, .day, .day, .more, .extras, .place]

    init(app: AppModel) {
        self.app = app
        var start = OnboardingStep.promise
        var still = false
        #if DEBUG
        // Screenshots: `-demoOnboardingStep asr` (or the old `-demoOnboardingPage 0…5`), `-demoOnboardingStill YES`
        // (no timers or motion), `-demoOnboardingWeather rain` (the weather step's preview, not cycling).
        let d = UserDefaults.standard
        if let key = d.string(forKey: "demoOnboardingStep"), let s = OnboardingStep(rawValue: key) {
            start = s
        } else if d.object(forKey: "demoOnboardingPage") != nil {
            start = Self.pages[min(Self.pages.count - 1, max(0, d.integer(forKey: "demoOnboardingPage")))]
        }
        still = d.bool(forKey: "demoOnboardingStill")
        #endif
        self.step = start
        self.still = still
        #if DEBUG
        if let w = UserDefaults.standard.string(forKey: "demoOnboardingWeather").flatMap(WeatherCondition.init(rawValue:)) {
            previewCondition = w
            autoCycle = false
        }
        #endif
        placeShown = app.settings.location != nil
        // Starting later (debug, tests) counts the earlier steps as answered.
        if let i = flow.index(start) { answered = Set(flow.steps[..<i]) }
        adhanVisited = (flow.index(start) ?? 0) >= (flow.index(.adhan) ?? 0)
    }

    // MARK: Lifetime

    func start() {
        guard clock == nil else { return }
        onEnter()
        guard !still else { return }
        clock = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                guard let self else { return }
                let t = Date()
                // The countdown ticks every second on the day; elsewhere the minute is enough (the sky's period).
                let cal = Calendar.current
                if self.step == .day || cal.component(.minute, from: t) != cal.component(.minute, from: self.now) { self.now = t }
            }
        }
    }

    func stop() {
        clock?.cancel()
        clock = nil
        entering?.cancel()
        entering = nil
        cancelTimers()
        if !AdhanPlayer.shared.isAdhan { AdhanPlayer.shared.stop() }
    }

    private func later(_ ms: Int, _ action: @escaping @MainActor () -> Void) {
        timers.append(Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(ms))
            guard !Task.isCancelled else { return }
            action()
        })
    }

    private func cancelTimers() {
        timers.forEach { $0.cancel() }
        timers = []
    }

    private var bouncy: Animation { OnbMotion.motion(OnbMotion.bouncy, reduce: reduceMotion) }

    // MARK: Moving

    /// To `to`, after the old step has faded out; going forward counts the steps passed as answered.
    func go(_ to: OnboardingStep) {
        guard !leaving, to != step else { return }
        if let a = flow.index(step), let b = flow.index(to), b > a { reveal(Array(flow.steps[a..<b])) }
        cancelTimers()
        if step == .adhan, !AdhanPlayer.shared.isAdhan { AdhanPlayer.shared.stop() }
        if still {
            enter(to)
            return
        }
        withAnimation(OnbMotion.motion(OnbMotion.leave, reduce: reduceMotion)) { leaving = true }
        entering = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(OnboardingTiming.leave))
            guard !Task.isCancelled else { return }
            self?.enter(to)
        }
    }

    func next() { if let n = flow.next(step) { go(n) } }
    func back() { if let b = flow.back(step) { go(b) } }

    /// Skip changes nothing on the steps it passes; their chips show the settings as they are. On the Adhan step it
    /// keeps the choice shown there, as Continue does (Makkah for someone who has set nothing yet).
    func skip() {
        guard let s = flow.skip(step) else { return }
        if step == .adhan { keepShownAdhan() }
        go(s)
    }

    private func enter(_ to: OnboardingStep) {
        step = to
        leaving = false
        drag = nil
        half = nil
        dragging = false
        allow = .idle
        // Back on the place with one set: its Continue shows again (picking the same city changes nothing to move on).
        if to == .place {
            placeSource = nil
            placePulse = 0
        }
        onEnter()
    }

    private func onEnter() {
        switch step {
        case .adhan:
            adhanVisited = true
        case .weather:
            cycleWeather()
        case .building:
            runBuilding()
        case .privacy:
            Task {
                let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
                promptPending = status == .notDetermined
                notificationsDenied = status == .denied
            }
        case .done:
            Task { notificationsAllowed = await Reminders.authorized() }
        default:
            break
        }
    }

    private func reveal(_ steps: [OnboardingStep]) {
        let new = steps.filter { !answered.contains($0) }
        guard !new.isEmpty else { return }
        withAnimation(bouncy) { answered.formUnion(new) }
    }

    /// A chip flew in: the phone taps and VoiceOver says it.
    private func flew(_ text: String) {
        flights += 1
        AccessibilityNotification.Announcement(text).post()
    }

    private func bump(_ chip: OnbChip) {
        bumps[chip, default: 0] += 1
    }

    // MARK: Place

    func useLocation() async {
        guard !locating else { return }
        locating = true
        locateError = nil
        let before = app.settings.location
        let outcome = await app.useDeviceLocation()
        locating = false
        switch outcome {
        // A new place reaches placeChanged through OnboardingView; the same place again moves on from here.
        case .ok: if app.settings.location == before { placeChanged(before) }
        case .denied: locateError = app.l10n.t("location_denied")
        case .noFix: locateError = app.l10n.t("location_fix_failed")
        }
    }

    /// A place was set (here, or in the city picker): the city and method chips fly in (or pop, when they were there),
    /// the row that set it is checked, and on the place step the flow moves on.
    func placeChanged(_ loc: PrayerLocation?) {
        guard let loc else { return }
        if placeShown {
            bump(.city)
            bump(.method)
        } else {
            withAnimation(bouncy) { placeShown = true }
            flew(loc.name)
        }
        // Not while the step fades out: the move already queued goes on.
        guard step == .place, !leaving else { return }
        withAnimation(bouncy) {
            placeSource = loc.source
            placePulse += 1
        }
        cancelTimers()
        later(OnboardingTiming.advance) { [weak self] in self?.go(.asr) }
    }

    // MARK: Asr

    func chooseMethod(_ m: PrayerMethod) {
        withAnimation(bouncy) { app.setMethod(m) }
        bump(.method)
        sheet = nil
    }

    func chooseMadhab(_ m: AsrMadhab) {
        withAnimation(bouncy) {
            app.setMadhab(m)
            asrPulse += 1
        }
        if answered.contains(.asr) { bump(.asr) } else {
            reveal([.asr])
            flew(app.l10n.t(m == .HANAFI ? "asr_hanafi" : "onb2_asr_standard"))
        }
        cancelTimers()
        later(OnboardingTiming.advance) { [weak self] in self?.go(.adhan) }
    }

    // MARK: Adhan

    /// Selected on the Adhan step: the tap, else the setting (Makkah for someone who has set nothing yet).
    var adhanShown: AdhanChoice { adhanPick ?? app.settings.alerts.onboardingChoice }

    func chooseAdhan(_ c: AdhanChoice) {
        withAnimation(bouncy) {
            adhanPick = c
            adhanPulse += 1
            // The notification ask comes once, on the privacy step.
            app.setAlerts(app.settings.alerts.with(c), askPermission: false)
        }
        if answered.contains(.adhan) { bump(.adhan) } else {
            reveal([.adhan])
            flew(app.l10n.t(Self.adhanChipKey(c)))
        }
    }

    func adhanContinue() {
        keepShownAdhan()
        if !answered.contains(.adhan) { flew(app.l10n.t(Self.adhanChipKey(adhanShown))) }
        go(.friday)
    }

    /// Continue and Skip save the choice shown when nothing was tapped, unless the settings already say it (so a
    /// separate Fajr recording chosen in Settings is kept).
    private func keepShownAdhan() {
        if adhanPick == nil, app.settings.alerts.adhanChoice != adhanShown {
            app.setAlerts(app.settings.alerts.with(adhanShown), askPermission: false)
        }
    }

    static func adhanChipKey(_ c: AdhanChoice) -> String {
        switch c {
        case .makkah: return "onb2_chip_makkah"
        case .madinah: return "onb2_chip_madinah"
        case .short: return "onb2_adhan_short"
        case .bannerOnly: return "onb2_chip_banner"
        }
    }

    func setSilence(_ on: Bool) {
        var s = app.settings.silence
        s.enabled = on
        withAnimation(OnbMotion.motion(OnbMotion.spring, reduce: reduceMotion)) { app.setSilence(s, askPermission: false) }
    }

    // MARK: Friday

    func setFriday(_ friday: Bool? = nil, kahf: Bool? = nil) {
        var a = app.settings.alerts
        let before = a.friday || a.kahf
        if let friday { a.friday = friday }
        if let kahf { a.kahf = kahf }
        let after = a.friday || a.kahf
        withAnimation(bouncy) { app.setAlerts(a, askPermission: false) }
        if !before && after {
            flew(app.l10n.t("onb2_chip_friday"))
        } else if before && after && (friday == true || kahf == true) {
            bump(.friday)
        }
    }

    // MARK: Weather

    /// The preview moves on to the next condition every 2.6 s (crossfading) until one is tapped. No network.
    private func cycleWeather() {
        guard autoCycle, !still else { return }
        later(OnboardingTiming.previewCycle) { [weak self] in
            guard let self, self.autoCycle, self.step == .weather else { return }
            let order = WeatherPreview.order
            let i = ((order.firstIndex(of: self.previewCondition) ?? 0) + 1) % order.count
            withAnimation(.easeInOut(duration: Double(OnboardingTiming.previewFade) / 1000)) { self.previewCondition = order[i] }
            self.cycleWeather()
        }
    }

    func pickCondition(_ c: WeatherCondition) {
        autoCycle = false
        withAnimation(.easeInOut(duration: Double(OnboardingTiming.previewFade) / 1000)) { previewCondition = c }
    }

    /// Turn on: the existing switch (the app then fetches, disclosed on this step). Not now changes nothing: off for
    /// someone new, and on for someone who turned it on before and is seeing the tour again.
    func answerWeather(_ on: Bool) {
        guard on else {
            next()
            return
        }
        app.settings.skyWeather = true
        reveal([.weather])
        flew(app.l10n.t("onb2_chip_weather"))
        cancelTimers()
        later(OnboardingTiming.advance) { [weak self] in self?.next() }
    }

    // MARK: Look

    func chooseLook(_ raw: String) {
        withAnimation(bouncy) { app.settings.widgetLook = raw }
        if answered.contains(.look) { bump(.look) } else {
            reveal([.look])
            flew(app.l10n.t("style_" + raw))
        }
    }

    // MARK: Building

    private func runBuilding() {
        if still {
            buildingDone = 3
            return
        }
        buildingDone = 0
        for i in 1...3 {
            later(OnboardingTiming.buildingLine * i) { [weak self] in
                guard let self else { return }
                withAnimation(self.bouncy) { self.buildingDone = i }
            }
        }
        later(OnboardingTiming.building) { [weak self] in self?.go(.day) }
    }

    // MARK: Privacy

    /// Notifications are needed: the switch is on, or an Adhan or reminder chosen already comes as one (the Adhan step
    /// turns the Adhan on), so Allow asks even with the switch off.
    var wantsNotifications: Bool { notify || app.wantsNotifications }

    /// Allow: the button turns into a spinner (at least 500 ms) while iOS asks (for alarms too when an Adhan is on, so
    /// it rings even on silent), the five prayer bells go on (only when none were chosen and the switch is on), a check
    /// pops and holds, then done. Declined: no check, the note says where to turn them on and the button reads Finish.
    /// With nothing that needs notifications, or notifications already off for the app: Finish.
    func allowTapped() {
        guard allow == .idle, !leaving else { return }
        guard wantsNotifications, !notificationsDenied else {
            // The bells are on for when notifications are allowed in Settings.
            if notify { Task { await app.enablePrayerBells() } }
            go(.done)
            return
        }
        withAnimation(OnbMotion.motion(OnbMotion.spring, reduce: reduceMotion)) { allow = .working }
        Task {
            let started = Date()
            let granted = await Reminders.requestAuthorization()
            if app.wantsAlarms { await PrayerAlarms.requestAuthorization() }
            if notify { await app.enablePrayerBells() } else { await app.rescheduleReminders() }
            let left = Double(OnboardingTiming.allowSpinner) / 1000 - Date().timeIntervalSince(started)
            if left > 0, !still { try? await Task.sleep(for: .seconds(left)) }
            guard step == .privacy else { return }
            guard granted else {
                withAnimation(OnbMotion.motion(OnbMotion.spring, reduce: reduceMotion)) {
                    notificationsDenied = true
                    promptPending = false
                    allow = .idle
                }
                return
            }
            notificationsAllowed = true
            withAnimation(bouncy) { allow = .done }
            if !still { try? await Task.sleep(for: .milliseconds(OnboardingTiming.allowHold)) }
            if step == .privacy { go(.done) }
        }
    }

    // MARK: Day

    func beginDrag(_ f: Double) {
        half = shownHalf
        dragging = true
        drag = f
    }

    /// Past an end: the other half (the sun sets and the moon rises, and back).
    func flipHalf() {
        half = shownHalf == .day ? .night : .day
        flips += 1
    }

    /// On release the disc springs back to now, in the half in progress.
    func endDrag() {
        moveOnArc {
            dragging = false
            drag = nil
            half = nil
        }
    }

    /// The disc moving along the arc without a finger: with the soft spring, or under Reduce Motion a fade at the new
    /// place (no sliding).
    private func moveOnArc(_ change: () -> Void) {
        if reduceMotion {
            change()
            arcJumps += 1
        } else {
            withAnimation(OnbMotion.soft, change)
        }
    }

    /// VoiceOver, Switch Control and the arrow keys: a quarter of an hour at a time (it stays where it is put).
    func stepArc(_ direction: Int) {
        guard let a = arc else { return }
        let span = a.end.timeIntervalSince(a.start)
        guard span > 0 else { return }
        var h = shownHalf
        var f = min(1, max(0, drag ?? a.fraction)) + Double(direction) * 15 * 60 / span
        if f > 1 {
            h = h == .day ? .night : .day
            f = 0
        } else if f < 0 {
            h = h == .day ? .night : .day
            f = 1
        }
        moveOnArc {
            half = h
            drag = f
        }
    }

    /// The Day | Night control: the half in progress shows now; the other one starts at its first prayer.
    func selectHalf(_ h: DayArc.Half) {
        moveOnArc {
            if h == realHalf {
                half = nil
                drag = nil
            } else {
                half = h
                drag = DayArc.at(now, days: days, half: h)?.marks.first?.fraction ?? 0.5
            }
        }
    }

    // MARK: What the views read

    /// The chips' order with their placeholders: the answers given so far, dashed outlines where one is still to come
    /// (none for Friday and the weather, which only show when on).
    func headerSlots() -> [OnbHeaderSlot] {
        let s = app.settings
        let l10n = app.l10n
        var out: [OnbHeaderSlot] = []
        func add(_ chip: OnbChip, _ shown: Bool, _ text: String, placeholder: CGFloat?) {
            if shown { out.append(OnbHeaderSlot(chip: chip, text: text)) } else if let placeholder { out.append(OnbHeaderSlot(chip: chip, placeholder: placeholder)) }
        }
        add(.city, placeShown && s.location != nil, s.location?.name ?? "", placeholder: 64)
        add(.method, placeShown && s.location != nil, l10n.t(Format.methodShortKey(s.prayer.method)), placeholder: 80)
        add(.asr, answered.contains(.asr), l10n.t(s.prayer.madhab == .HANAFI ? "asr_hanafi" : "onb2_asr_standard"), placeholder: 70)
        // Passed by Skip without being shown, with nothing set yet: still a placeholder, not "Banner only".
        let adhanKnown = answered.contains(.adhan) && (adhanVisited || s.alerts != AlertSettings())
        add(.adhan, adhanKnown, l10n.t(Self.adhanChipKey(s.alerts.adhanChoice)), placeholder: 76)
        add(.friday, (answered.contains(.friday) || step == .friday) && (s.alerts.friday || s.alerts.kahf), l10n.t("onb2_chip_friday"), placeholder: nil)
        add(.weather, answered.contains(.weather) && s.skyWeather, l10n.t("onb2_chip_weather"), placeholder: nil)
        if flow.hasLook { add(.look, answered.contains(.look), l10n.t("style_" + s.widgetLook), placeholder: 60) }
        return out
    }

    var days: [DaySchedule] { app.days(now: now) }

    /// The half in progress (nil on polar days).
    var realHalf: DayArc.Half? { DayArc.at(now, days: days).map { $0.isDay ? .day : .night } }

    var shownHalf: DayArc.Half { half ?? realHalf ?? .day }

    var arc: DayArc? { DayArc.at(now, days: days, half: shownHalf) }

    var hasArc: Bool { app.settings.location != nil && arc != nil }

    /// The fraction the disc shows: the drag, else now.
    var shownFraction: Double {
        if let drag { return drag }
        return arc?.fraction ?? 0.5
    }

    /// The moment under the dragged disc.
    var dragInstant: Date? {
        guard let drag, let a = arc else { return nil }
        let f = min(1, max(0, drag))
        return a.start.addingTimeInterval(a.end.timeIntervalSince(a.start) * f)
    }

    /// The sky's period: the dragged moment on the day; midday on a two-pane Friday (for the skyline); the place's
    /// period; before any place, the period at the largest city in the device's time zone.
    func skyPeriod(wide: Bool) -> SkyPeriod {
        if step == .day, let t = dragInstant { return Self.period(at: t, days: days) }
        if wide && step == .friday { return .day }
        if app.settings.location != nil { return app.skyPeriod(now: now) }
        return app.previewPeriod(now: now)
    }

    /// The weather step's preview (samples, no network), else only real data.
    func skyWeather() -> WeatherNow? {
        if step == .weather { return WeatherPreview.now(previewCondition, at: now) }
        return app.skyWeather(now: now)
    }

    /// The period at any moment of the three days (as the prayer screen works it out for now).
    static func period(at t: Date, days: [DaySchedule]) -> SkyPeriod {
        guard let zone = days.first?.zone else { return .night }
        let date = CivilDate.of(t, in: zone)
        guard let i = days.firstIndex(where: { $0.date == date }) else { return .night }
        let d = days[i]
        let current = PrayerEvent.allCases.last { (d[$0] ?? .distantFuture) <= t } ?? (i > 0 && days[i - 1][.isha] != nil ? .isha : nil)
        return SkyPeriod.at(t, today: d, current: current)
    }

    /// The disc sits in the sky behind the steps on the promise and done, in front of everything elsewhere.
    var heroLow: Bool { step == .promise || step == .done }

    func heroPlacement(_ frame: OnbFrame, period: SkyPeriod) -> HeroPlacement {
        let inArc = step == .day || (frame.wide && step == .privacy)
        let moon = (inArc || step == .done) ? shownHalf == .night : period.hasStars
        let none: CGFloat = 100_000
        switch step {
        case .promise:
            return HeroPlacement(center: CGPoint(x: frame.paneMidX, y: frame.y(frame.wide ? 0.42 : 0.34)), size: HeroDisc.base, moon: moon,
                                 big: true, cut: frame.y(frame.horizon(.promise) - 0.02), kind: 0)
        case .done:
            if frame.wide, let p = arcPoint(fraction: arc?.fraction ?? 0.5, frame: frame) {
                return HeroPlacement(center: p, size: HeroDisc.base, moon: moon, big: true, cut: arcBase(frame), kind: 3)
            }
            let f = arc?.fraction ?? 0.5
            let x = 40 + (frame.size.width - 80) * CGFloat(frame.rtl ? 1 - f : f)
            let y = frame.y(0.773) - 0.39 * frame.fullHeight * CGFloat(sin(Double.pi * f))
            return HeroPlacement(center: CGPoint(x: x, y: y), size: HeroDisc.base * 1.1, moon: moon, big: true,
                                 cut: frame.y(frame.horizon(.done) - 0.02), kind: 3)
        default:
            if inArc, let p = arcPoint(fraction: shownFraction, frame: frame) {
                var h = HeroPlacement(center: p, size: frame.wide ? 46 : 34, moon: moon, big: false, cut: arcBase(frame), kind: 2)
                if step == .day, let t = dragInstant {
                    h.bubble = dragLabel(t)
                    let lo = frame.wide ? frame.paneMinX : 0, hi = frame.wide ? frame.paneMinX + frame.paneWidth : frame.size.width
                    h.bubbleMin = lo + 90
                    h.bubbleMax = hi - 90
                }
                return h
            }
            let slot = slotFrame == .zero ? CGPoint(x: frame.rtl ? frame.size.width - 39 : 39, y: 25) : CGPoint(x: slotFrame.midX, y: slotFrame.midY)
            return HeroPlacement(center: slot, size: 22, moon: moon, big: false, cut: none, kind: 1)
        }
    }

    private func arcPoint(fraction: Double, frame: OnbFrame) -> CGPoint? {
        guard arcBox != .zero, arcChord > 0 else { return nil }
        let g = OnbArcGeometry(size: arcBox.size, chord: arcChord, rtl: frame.rtl)
        let p = g.point(fraction)
        return CGPoint(x: arcBox.minX + p.x, y: arcBox.minY + p.y)
    }

    private func arcBase(_ frame: OnbFrame) -> CGFloat {
        arcBox == .zero ? 100_000 : arcBox.minY + OnbArcGeometry(size: arcBox.size, chord: arcChord, rtl: frame.rtl).base
    }

    /// "Dhuhr · 12:04 PM" near a prayer, else the part of the day ("Maghrib" period names are the prayers').
    func dragLabel(_ t: Date) -> String {
        let l10n = app.l10n
        let zone = app.settings.location?.zone ?? .current
        let time = Format.time(t, zone: zone, locale: l10n.locale)
        let near = days.flatMap { d in PrayerEvent.allCases.compactMap { e in d[e].map { (e, $0) } } }
            .first { abs($0.1.timeIntervalSince(t)) <= 8 * 60 }
        let name = near.map { l10n.t(Format.prayerKey($0.0)) } ?? l10n.t(shownHalf == .day ? "onb2_day" : "onb2_night")
        return name + " · " + time
    }
}

/// A chip in the header, or the dashed outline of one still to come.
struct OnbHeaderSlot: Identifiable, Equatable {
    let chip: OnbChip
    var text: String = ""
    /// The outline's width; nil for an answer.
    var placeholder: CGFloat?

    init(chip: OnbChip, text: String) { self.chip = chip; self.text = text }
    init(chip: OnbChip, placeholder: CGFloat) { self.chip = chip; self.placeholder = placeholder }

    var id: String { chip.rawValue + (placeholder == nil ? "" : "-placeholder") }
}
