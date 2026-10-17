import NMCore
import SwiftUI

// The promise and the questions: where, Asr (with the method), the Adhan, Friday, the weather sky and the look.
// Every answer is saved at once through the real setting; nothing waits for the end.

// MARK: - Promise

struct PromiseStep: View {
    let model: OnboardingModel
    @Environment(\.l10n) private var l10n
    @Environment(\.onb) private var onb

    var body: some View {
        OnbPage(model: model, bottom: true) {
            VStack(spacing: 0) {
                OnbEyebrow(text: l10n.t("app_name")).onbEnter(0)
                OnbTitle(text: l10n.t("onb2_promise_title"), large: true)
                    .padding(.top, 8)
                    .onbEnter(1)
                OnbBodyText(text: l10n.t(onb.device("onb2_promise_body")))
                    .padding(.top, 10)
                    .onbEnter(2)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
        } footer: {
            VStack(spacing: 14) {
                OnbPrimaryButton(title: l10n.t("onb2_begin")) { model.go(.place) }
                OnbFootnote(text: l10n.t("onb2_promise_foot"))
                    .multilineTextAlignment(.center)
                    .onbEnter(5)
            }
        }
    }
}

// MARK: - Where

/// The place: the device's location (iOS asks for permission at that moment) or the city picker. Once a place is set
/// its row is checked, the city and method chips fly in and the flow moves on.
struct PlaceStep: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.onb) private var onb
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let loc = app.settings.location
        OnbPage(model: model) {
            VStack(alignment: .leading, spacing: 0) {
                OnbTitle(text: l10n.t("onb2_where_title")).onbEnter(0)
                OnbBodyText(text: l10n.t(onb.device("onb2_where_body"))).padding(.top, 10).onbEnter(1)
                VStack(spacing: 12) {
                    OnbRow(icon: .symbol("location.fill"), title: l10n.t("use_my_location"),
                           subtitle: model.locating ? l10n.t("locating")
                               : loc?.source == .device ? l10n.t("onb_location_set", loc?.name ?? "") : l10n.t("onb2_where_location_sub"),
                           selected: loc?.source == .device, pulse: model.placeSource == .device ? model.placePulse : 0,
                           action: { Task { await model.useLocation() } }) {
                        end(.device, loc)
                    }
                    .disabled(model.locating)
                    OnbRow(icon: .symbol("magnifyingglass"), title: l10n.t("search_city"),
                           subtitle: loc?.source == .city ? l10n.t("onb_location_set", loc?.name ?? "") : l10n.t("onb2_where_search_sub"),
                           selected: loc?.source == .city, pulse: model.placeSource == .city ? model.placePulse : 0,
                           action: { model.sheet = .city }) {
                        end(.city, loc)
                    }
                }
                .padding(.top, 28)
                .onbEnter(2)
                if let error = model.locateError {
                    OnbFootnote(text: error, color: OnbColor.error)
                        .padding(.top, 10)
                        .transition(.opacity)
                }
            }
        } footer: {
            // Seen again (Settings → Show welcome tour) with a place already set: Continue keeps it. Never a Skip here.
            if loc != nil && model.placeSource == nil {
                OnbPrimaryButton(title: l10n.t("onb2_continue")) { model.next() }
            }
        }
    }

    @ViewBuilder private func end(_ source: PrayerLocation.Source, _ loc: PrayerLocation?) -> some View {
        if source == .device && model.locating {
            ProgressView()
        } else if loc?.source == source {
            OnbCheck().transition(.checkPop(reduce: reduceMotion))
        } else {
            Image(systemName: "chevron.forward")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(OnbColor.secondary)
                .accessibilityHidden(true)
        }
    }
}

// MARK: - Asr

/// When Asr begins: two cards with the shadow drawn to scale and the real Asr time of each school today; the method
/// line above them opens the full list.
struct AsrStep: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let s = app.settings
        let times: [AsrMadhab: Date] = s.location.map { loc in
            app.calculator.asrTimes(loc.location, date: CivilDate.of(model.now, in: loc.zone), zone: loc.zone, settings: s.prayer)
        } ?? [:]
        let cards = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(spacing: 12)) : AnyLayout(HStackLayout(alignment: .top, spacing: 12))
        OnbPage(model: model) {
            VStack(alignment: .leading, spacing: 0) {
                OnbTitle(text: l10n.t("onb2_asr_title")).onbEnter(0)
                OnbBodyText(text: l10n.t("onb2_asr_body")).padding(.top, 10).onbEnter(1)
                methodLine.padding(.top, 20).onbEnter(2)
                cards {
                    card(.SHAFI, title: l10n.t("onb2_asr_standard"), subtitle: l10n.t("onb2_asr_standard_sub"), at: times[.SHAFI])
                    card(.HANAFI, title: l10n.t("asr_hanafi"), subtitle: l10n.t("onb2_asr_hanafi_sub"), at: times[.HANAFI])
                }
                .padding(.top, 14)
                .onbEnter(3)
            }
        } footer: {
            EmptyView()
        }
    }

    /// "Calculation: Umm al-Qura, Makkah · suggested for Riyadh" (the city only while the method follows the place).
    private var methodLine: some View {
        let s = app.settings
        let method = l10n.t(Format.methodKey(s.prayer.method))
        let named = s.location.map { $0.source == .city || $0.cityLatitude != nil } ?? false
        let text = !s.methodChosenByUser && named
            ? l10n.t("onb2_method_line_city", method, s.location?.name ?? "") : l10n.t("onb2_method_line", method)
        return HStack(spacing: 8) {
            Text(text)
                .font(.footnote)
                .foregroundStyle(OnbColor.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            Button { model.sheet = .methods } label: {
                Text(l10n.t("change"))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.accent)
                    .padding(.horizontal, 10)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.onbPress)
            .hoverEffect(.highlight)
            .accessibilityLabel(l10n.t("change") + ", " + l10n.t("calculation"))
        }
        .padding(.leading, 14)
        .padding(.trailing, 4)
        .glass(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func card(_ m: AsrMadhab, title: String, subtitle: String, at: Date?) -> some View {
        let time = at.map { Format.time($0, zone: app.settings.location?.zone ?? .current, locale: l10n.locale) } ?? "—"
        return AsrCard(madhab: m, title: title, subtitle: subtitle, time: l10n.t("onb2_asr_at", time),
                       selected: app.settings.prayer.madhab == m, pulse: app.settings.prayer.madhab == m ? model.asrPulse : 0) {
            model.chooseMadhab(m)
        }
    }
}

/// One school: the stick and its shadow (1× or 2×) drawn to scale, the name, who follows it and today's Asr time.
struct AsrCard: View {
    let madhab: AsrMadhab
    let title: String
    let subtitle: String
    let time: String
    let selected: Bool
    let pulse: Int
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var direction

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                AsrShadowArt(hanafi: madhab == .HANAFI)
                    .frame(height: 96)
                    .padding(.top, 2)
                    .padding(.bottom, 8)
                Text(title).font(.title3.bold())
                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(OnbColor.secondary)
                    .padding(.top, 4)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 10)
                Text(time)
                    .font(.headline)
                    .monospacedDigit()
                    .foregroundStyle(Theme.accent)
            }
            .padding(.horizontal, 14)
            .padding(.top, 10)
            .padding(.bottom, 16)
            .frame(maxWidth: .infinity, minHeight: 248, alignment: .topLeading)
            .contentShape(shape)
        }
        .buttonStyle(.onbPress(shape))
        .glass(shape)
        .overlay { if selected { shape.strokeBorder(Theme.gold, lineWidth: 2).allowsHitTesting(false) } }
        // Top right in every language: the drawing is never mirrored, so its sun stays top left.
        .overlay(alignment: direction == .rightToLeft ? .topLeading : .topTrailing) {
            if selected { OnbCheck().padding(10).transition(.checkPop(reduce: reduceMotion)) }
        }
        .onbPulse(shape, trigger: pulse)
        .hoverEffect(.highlight)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// The ground, the stick, the sun and the stick's shadow: as long as the stick (standard) or twice it (Hanafi). The
/// shadow grows in with the soft spring after 260 ms (under Reduce Motion it fades in whole), the dashed ray fades in
/// at 700 ms. Never mirrored.
struct AsrShadowArt: View {
    let hanafi: Bool
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.onb) private var onb
    @State private var grown = false
    @State private var rayShown = false

    var body: some View {
        let sun = hanafi ? CGPoint(x: 14, y: 28) : CGPoint(x: 20, y: 16)
        let tip: CGFloat = hanafi ? 122 : 86
        let full = grown || onb.still
        GeometryReader { geo in
            let k = min(geo.size.width / 150, geo.size.height / 96)
            ZStack(alignment: .topLeading) {
                Path { p in p.move(to: CGPoint(x: 6, y: 82)); p.addLine(to: CGPoint(x: 144, y: 82)) }
                    .stroke(OnbColor.track, style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                Path { p in p.move(to: CGPoint(x: 50, y: 82)); p.addLine(to: CGPoint(x: tip, y: 82)) }
                    .trim(from: 0, to: full || reduceMotion ? 1 : 0)
                    .stroke(scheme == .dark ? Color.black.opacity(0.42) : Theme.textLight.opacity(0.3), style: StrokeStyle(lineWidth: 5, lineCap: .round))
                    .opacity(full || !reduceMotion ? 1 : 0)
                Path { p in p.move(to: sun); p.addLine(to: CGPoint(x: tip, y: 82)) }
                    .stroke(Theme.gold, style: StrokeStyle(lineWidth: 1.25, lineCap: .round, dash: [2, 4]))
                    .opacity(rayShown || onb.still ? 0.75 : 0)
                Path { p in p.move(to: CGPoint(x: 50, y: 82)); p.addLine(to: CGPoint(x: 50, y: 46)) }
                    .stroke(Theme.ink, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                Circle()
                    .fill(OnbColor.sunMid)
                    .frame(width: 14, height: 14)
                    .shadow(color: OnbColor.sunMid.opacity(0.8), radius: 6)
                    .position(sun)
            }
            .frame(width: 150, height: 96, alignment: .topLeading)
            .scaleEffect(k, anchor: .topLeading)
            .frame(width: 150 * k, height: 96 * k, alignment: .topLeading)
        }
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
        .onAppear {
            guard !full else { return }
            withAnimation((reduceMotion ? OnbMotion.fade : OnbMotion.soft).delay(0.26)) { grown = true }
            withAnimation(.easeIn(duration: 0.6).delay(0.7)) { rayShown = true }
        }
    }
}

/// The eleven methods with their full names; the current one checked. Choosing one closes the list (no auto-advance).
struct MethodListSheet: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n

    var body: some View {
        NavigationStack {
            List(PrayerMethod.allCases, id: \.self) { m in
                let on = app.settings.prayer.method == m
                Button { model.chooseMethod(m) } label: {
                    HStack {
                        Text(l10n.t(Format.methodKey(m))).foregroundStyle(.primary)
                        Spacer()
                        if on { Image(systemName: "checkmark").foregroundStyle(Theme.accent).accessibilityHidden(true) }
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .accessibilityAddTraits(on ? .isSelected : [])
            }
            .navigationTitle(l10n.t("calculation"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { model.sheet = nil } label: { Label(l10n.t("cancel"), systemImage: "xmark") }
                        .keyboardShortcut(.cancelAction)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Adhan

/// How to be called: Makkah, Madinah, the short Adhan (the first Allahu Akbar) or a banner without sound, each but the
/// banner with a preview of the real recording. Quiet during prayer on iPhone and iPad (it needs a Shortcut, so its
/// setup is one tap away).
struct AdhanStep: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n

    var body: some View {
        let shown = model.adhanShown
        OnbPage(model: model) {
            VStack(alignment: .leading, spacing: 0) {
                OnbTitle(text: l10n.t("onb2_adhan_title")).onbEnter(0)
                OnbBodyText(text: l10n.t("onb2_adhan_body")).padding(.top, 10).onbEnter(1)
                VStack(spacing: 8) {
                    ForEach(AdhanChoice.allCases, id: \.self) { c in
                        AdhanRow(title: l10n.t(Self.titleKey(c)), subtitle: c == .short ? l10n.t("onb2_adhan_short_sub") : nil,
                                 selected: shown == c, pulse: shown == c ? model.adhanPulse : 0, preview: recording(c)) {
                            model.chooseAdhan(c)
                        }
                    }
                }
                .padding(.top, 20)
                .onbEnter(2)
                // A notification plays only the first 30 seconds; the whole Adhan plays while the app is open.
                OnbFootnote(text: l10n.t("adhan_note")).padding(.top, 10).onbEnter(3)
                #if os(iOS) && !targetEnvironment(macCatalyst)
                quiet.padding(.top, 12).onbEnter(3)
                #endif
            }
        } footer: {
            OnbPrimaryButton(title: l10n.t("onb2_continue")) { model.adhanContinue() }
        }
    }

    static func titleKey(_ c: AdhanChoice) -> String {
        switch c {
        case .makkah: return "onb2_adhan_makkah"
        case .madinah: return "onb2_adhan_madinah"
        case .short: return "onb2_adhan_short"
        case .bannerOnly: return "onb2_adhan_banner"
        }
    }

    /// The recording each row previews (AudioCatalog's names); none for the banner.
    private func recording(_ c: AdhanChoice) -> String? {
        switch c {
        case .makkah: return AudioCatalog.adhan(source: .makkah, fajr: false)
        case .madinah: return AudioCatalog.adhan(source: .madinah, fajr: false)
        case .short: return "adhan_\(app.settings.alerts.adhanSource.rawValue)_short"
        case .bannerOnly: return nil
        }
    }

    #if os(iOS) && !targetEnvironment(macCatalyst)
    /// The existing silence during prayer: iPhone lets no app turn on Focus, so it runs a Shortcut the person makes
    /// once; the steps are in Prayer & Audio, opened from here.
    private var quiet: some View {
        VStack(alignment: .leading, spacing: 2) {
            OnbSwitchRow(icon: .symbol("moon.zzz"), title: l10n.t("silence_section"), subtitle: l10n.t("onb2_quiet_sub_ios"),
                         isOn: app.settings.silence.enabled) { model.setSilence($0) }
            if app.settings.silence.enabled {
                Button { model.sheet = .audio } label: {
                    Text(l10n.t("onb2_quiet_setup"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                        .padding(.horizontal, 16)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.onbPress)
                .hoverEffect(.highlight)
                .modifier(OnSky())
                .transition(.opacity)
            }
        }
    }
    #endif
}

/// A radio row with a five-bar waveform (moving while its preview plays) and the preview button at its end.
struct AdhanRow: View {
    let title: String
    var subtitle: String?
    let selected: Bool
    let pulse: Int
    var preview: String?
    let action: () -> Void
    @Environment(\.l10n) private var l10n

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        let playing = preview != nil && AdhanPlayer.shared.playing == preview
        HStack(spacing: 0) {
            Button(action: action) {
                HStack(spacing: 12) {
                    ZStack {
                        Circle().strokeBorder(selected ? Theme.gold : OnbColor.placeholder, lineWidth: 2)
                        if selected { Circle().fill(Theme.gold).frame(width: 12, height: 12).transition(.scale.combined(with: .opacity)) }
                    }
                    .frame(width: 22, height: 22)
                    .accessibilityHidden(true)
                    Waveform(playing: playing, flat: preview == nil)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(.headline)
                        if let subtitle {
                            Text(subtitle).font(.footnote).foregroundStyle(OnbColor.secondary)
                        }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 4)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, minHeight: 58, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.onbPress)
            .accessibilityAddTraits(selected ? .isSelected : [])
            if let preview {
                Button { AdhanPlayer.shared.toggle(preview) } label: {
                    Image(systemName: playing ? "stop.fill" : "play.fill")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(playing ? Theme.accent : Theme.ink)
                        .frame(width: 44, height: 44)
                        .background(playing ? Theme.gold.opacity(0.22) : OnbColor.chip, in: Circle())
                        .contentShape(Circle())
                }
                .buttonStyle(.onbPress)
                .hoverEffect(.highlight)
                .padding(.trailing, 8)
                .accessibilityLabel(playing ? l10n.t("alarm_stop") : l10n.t("preview_play", title))
            }
        }
        .glass(shape)
        .overlay { if selected { shape.strokeBorder(Theme.gold, lineWidth: 2).allowsHitTesting(false) } }
        .onbPulse(shape, trigger: pulse)
    }
}

/// Five bars; they move while the recording plays (still under Reduce Motion), and lie flat for the banner.
struct Waveform: View {
    let playing: Bool
    var flat = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private static let levels: [Double] = [0.35, 0.7, 1, 0.6, 0.4]

    var body: some View {
        Group {
            if playing && !reduceMotion {
                TimelineView(.animation) { ctx in bars(ctx.date.timeIntervalSinceReferenceDate) }
            } else {
                bars(nil)
            }
        }
        .frame(width: 26, height: 24)
        .foregroundStyle(Theme.accent)
        .opacity(flat ? 0.6 : 1)
        .accessibilityHidden(true)
    }

    private func bars(_ t: Double?) -> some View {
        HStack(spacing: 2.5) {
            ForEach(0..<5, id: \.self) { i in
                let level: Double = {
                    if flat { return 0.16 }
                    guard let t else { return Self.levels[i] }
                    return 0.25 + 0.75 * abs(sin((t / 0.72 + Double(i) * 0.17) * .pi))
                }()
                Capsule().frame(width: 3, height: 22).scaleEffect(x: 1, y: CGFloat(level))
            }
        }
    }
}

// MARK: - Friday

/// Jumu'ah: the Friday reminder (45 minutes before Dhuhr) and Surat Al-Kahf, the app's existing switches. With the
/// Friday reminder on, the coming Friday's real times for the place.
struct FridayStep: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.onb) private var onb

    var body: some View {
        let a = app.settings.alerts
        OnbPage(model: model) {
            VStack(alignment: .leading, spacing: 0) {
                // On big screens the leading pane shows the midday sky with the skyline instead.
                if !onb.wide { FridayArt().padding(.bottom, 18).onbEnter(0) }
                OnbTitle(text: l10n.t("onb2_friday_title")).onbEnter(0)
                OnbBodyText(text: l10n.t("onb2_friday_body")).padding(.top, 10).onbEnter(1)
                VStack(spacing: 12) {
                    OnbSwitchRow(icon: .asset("MosqueTab"), title: l10n.t("alerts_friday"), subtitle: l10n.t("alerts_friday_note"),
                                 isOn: a.friday) { model.setFriday($0) }
                    OnbSwitchRow(icon: .symbol("book.closed"), title: l10n.t("alerts_kahf"), subtitle: l10n.t("alerts_kahf_note"),
                                 isOn: a.kahf) { model.setFriday(kahf: $0) }
                }
                .padding(.top, 20)
                .onbEnter(2)
                if a.friday, let loc = app.settings.location,
                   let f = FridayPreview.next(location: loc.location, zone: loc.zone, now: model.now, settings: app.settings.prayer, calculator: app.calculator) {
                    OnbFootnote(text: l10n.t("onb2_friday_next", Format.time(f.reminder, zone: loc.zone, locale: l10n.locale),
                                             Format.time(f.dhuhr, zone: loc.zone, locale: l10n.locale)))
                        .monospacedDigit()
                        .padding(.top, 12)
                        .transition(.opacity)
                }
            }
        } footer: {
            OnbPrimaryButton(title: l10n.t("onb2_continue")) { model.next() }
        }
    }
}

// MARK: - Weather

/// Sky follows the weather, off until turned on: the whole sky behind is the live preview (sample values labelled
/// Preview, no network), cycling until a condition is tapped. Turn on and Not now are equal choices.
struct WeatherStep: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.sky) private var sky

    var body: some View {
        let c = model.previewCondition
        let night = sky.period.hasStars
        OnbPage(model: model) {
            VStack(alignment: .leading, spacing: 0) {
                Text(l10n.t("onb2_new"))
                    .font(.caption.weight(.bold))
                    .textCase(l10n.isRTL ? nil : .uppercase)
                    .tracking(l10n.isRTL ? 0 : 0.96)
                    .foregroundStyle(Theme.accent)
                    .padding(.horizontal, 10)
                    .frame(minHeight: 24)
                    .background(Theme.gold.opacity(0.22), in: Capsule())
                    .padding(.bottom, 10)
                    .onbEnter(0)
                OnbTitle(text: l10n.t("sky_weather")).onbEnter(0)
                OnbBodyText(text: l10n.t("onb_weather_body")).padding(.top, 10).onbEnter(1)
                HStack(spacing: 0) {
                    ForEach(WeatherPreview.order, id: \.self) { cond in
                        conditionButton(cond, on: cond == c, night: night).frame(maxWidth: .infinity)
                    }
                }
                .padding(.top, 18)
                .accessibilityElement(children: .contain)
                .accessibilityLabel(l10n.t("onb2_weather_conditions"))
                .onbEnter(2)
                preview(c, night: night).padding(.top, 12).onbEnter(2)
                Link(destination: URL(string: Weather.attributionURL)!) {
                    Text(l10n.t("sky_weather_attribution")).underline().foregroundStyle(OnbColor.secondary)
                }
                .font(.caption)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 44)
                .modifier(OnSky())
                .onbEnter(3)
            }
        } footer: {
            HStack(spacing: 12) {
                OnbPrimaryButton(title: l10n.t("weather_offer_turn_on"), shortcut: false) { model.answerWeather(true) }
                OnbSecondaryButton(title: l10n.t("weather_offer_not_now")) { model.answerWeather(false) }
                    .onbEnter(4)
            }
        }
    }

    private func conditionButton(_ c: WeatherCondition, on: Bool, night: Bool) -> some View {
        Button { model.pickCondition(c) } label: {
            Image(systemName: c.symbolName(night: night))
                .symbolRenderingMode(.hierarchical)
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.onbPress)
        .foregroundStyle(on ? Theme.accent : Theme.ink)
        .glass(Circle())
        .overlay { if on { Circle().strokeBorder(Theme.gold, lineWidth: 2).allowsHitTesting(false) } }
        .hoverEffect(.highlight)
        .accessibilityLabel(l10n.t(c.stringKey))
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    /// "Preview · Rain · 17°C · Wind 22 km/h" in the device's units and the app's digits.
    private func preview(_ c: WeatherCondition, night: Bool) -> some View {
        let w = WeatherPreview.now(c, at: model.now)
        let units = app.weather.units
        let temp = Weather.isolate(l10n.t(units.temperature.stringKey, number(units.temperature(w.temperatureC))))
        let wind = Weather.isolate(l10n.t(units.wind.stringKey, number(units.wind(w.windMs))))
        return OnbCard(padding: 14) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(l10n.t("onb2_preview"))
                        .font(.caption.weight(.semibold))
                        .textCase(l10n.isRTL ? nil : .uppercase)
                        .foregroundStyle(Theme.accent)
                    Text(l10n.t(c.stringKey)).font(.title3.weight(.semibold))
                    Text(l10n.t(Weather.lineKey, temp, wind))
                        .font(.subheadline)
                        .monospacedDigit()
                        .foregroundStyle(OnbColor.secondary)
                }
                Spacer(minLength: 8)
                Image(systemName: c.symbolName(night: night))
                    .symbolRenderingMode(.hierarchical)
                    .font(.system(size: 34))
                    .foregroundStyle(Theme.accent)
                    .accessibilityHidden(true)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func number(_ v: Int) -> String { Format.number(Double(v), digits: 0, locale: l10n.locale) }
}

// MARK: - Look

/// One style for the app's buttons and highlights and every widget (the styles Settings offers), and the appearance.
/// The choice shows at once: this step's accent, its buttons and the header's chips follow it.
struct LookStep: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage("appearance") private var appearance = "system"

    var body: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 12, alignment: .top), count: 3)
        OnbPage(model: model) {
            VStack(alignment: .leading, spacing: 0) {
                OnbTitle(text: l10n.t("onb2_look_title")).onbEnter(0)
                OnbBodyText(text: l10n.t("onb2_look_body")).padding(.top, 10).onbEnter(1)
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(WidgetLook.choices, id: \.rawValue) { look in styleCard(look) }
                }
                .padding(.top, 20)
                .accessibilityElement(children: .contain)
                .accessibilityLabel(l10n.t("app_style_title"))
                .onbEnter(2)
                Text(l10n.t("appearance"))
                    .font(.headline)
                    .padding(.top, 20)
                    .modifier(OnSky())
                    .onbEnter(3)
                Picker(l10n.t("appearance"), selection: $appearance) {
                    Text(l10n.t("appearance_system")).tag("system")
                    Text(l10n.t("appearance_light")).tag("light")
                    Text(l10n.t("appearance_dark")).tag("dark")
                }
                .pickerStyle(.segmented)
                .padding(.top, 8)
                .onbEnter(3)
            }
        } footer: {
            OnbPrimaryButton(title: l10n.t("onb2_continue")) { model.next() }
        }
    }

    private func styleCard(_ look: WidgetLook) -> some View {
        let raw = look.rawValue
        let on = app.settings.widgetLook == raw
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        let today = model.days.count == 3 ? model.days[1] : nil
        let current = PrayerEvent.allCases.last { (today?[$0] ?? .distantFuture) <= model.now }
        return Button { model.chooseLook(raw) } label: {
            VStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(WidgetPalette.of(look, period: current ?? .maghrib).background)
                    .overlay(Circle().fill(StyleAccent.dark(raw)).frame(width: 22, height: 22))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(OnbColor.line, lineWidth: 1))
                    .frame(width: 56, height: 56)
                Text(l10n.t("style_" + raw))
                    .font(.footnote.weight(.semibold))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 6)
            .frame(maxWidth: .infinity, minHeight: 112)
            .contentShape(shape)
        }
        .buttonStyle(.onbPress(shape))
        .glass(shape)
        .overlay { if on { shape.strokeBorder(StyleAccent.dark(raw), lineWidth: 2).allowsHitTesting(false) } }
        .overlay(alignment: .topTrailing) {
            if on { OnbCheck(size: 20).padding(6).transition(.checkPop(reduce: reduceMotion)) }
        }
        .hoverEffect(.highlight)
        .accessibilityLabel(l10n.t("style_" + raw))
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}
