import NMCore
import SwiftUI
import UIKit
#if !os(visionOS)
import WidgetKit
#endif

// "Near Mosque, everywhere" (the widgets, Siri, the Apple Watch and Ask, each only where the device has it) and "Also
// in Near Mosque" (the rest, as tiles). Nothing here opens anything; the previews are the real views with this
// person's place and style.

// MARK: - Extras

struct ExtrasStep: View {
    let model: OnboardingModel
    @Environment(\.l10n) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        OnbPage(model: model) {
            VStack(alignment: .leading, spacing: 0) {
                OnbTitle(text: l10n.t("onb2_extras_title")).onbEnter(0)
                OnbBodyText(text: l10n.t("onb2_extras_body")).padding(.top, 10).onbEnter(1)
                VStack(spacing: 12) {
                    // Apple Vision Pro has no widgets of this app (no visionOS widget extension).
                    #if !os(visionOS)
                    WidgetsCard()
                    #endif
                    SiriCard()
                    #if canImport(WatchConnectivity) && !targetEnvironment(macCatalyst) && !os(visionOS)
                    WatchCard(model: model)
                    #endif
                    AskCard()
                }
                .padding(.top, 20)
                .onbEnter(2)
            }
        } footer: {
            OnbPrimaryButton(title: l10n.t("onb2_continue")) { model.next() }
        }
    }
}

/// A card's title (a heading for VoiceOver).
private struct CardTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.subheadline.weight(.semibold))
            .accessibilityAddTraits(.isHeader)
    }
}

// MARK: Widgets

#if !os(visionOS)
/// The real widget views (as in the widget gallery) with this person's place and the style chosen on the look step,
/// and on iPhone the Lock Screen countdown; how to add them below. Not tappable.
struct WidgetsCard: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n

    private struct Item: Identifiable {
        let kind: PrayerWidgetKind
        let family: WidgetFamily
        let size: CGSize
        let name: String
        var id: String { name }
    }

    private static let items: [Item] = [
        Item(kind: .next, family: .systemSmall, size: CGSize(width: 158, height: 158), name: "widget_kind_next"),
        Item(kind: .today, family: .systemMedium, size: CGSize(width: 338, height: 158), name: "widget_kind_today"),
        Item(kind: .ring, family: .systemSmall, size: CGSize(width: 158, height: 158), name: "widget_kind_ring"),
        Item(kind: .qibla, family: .systemSmall, size: CGSize(width: 158, height: 158), name: "widget_kind_qibla"),
    ]

    private var howTo: String {
        #if targetEnvironment(macCatalyst)
        return l10n.t("onb2_widgets_howto_mac")
        #else
        return l10n.t("onb2_widgets_howto_ios")
        #endif
    }

    var body: some View {
        OnbCard {
            VStack(alignment: .leading, spacing: 10) {
                CardTitle(text: l10n.t("onb2_widgets"))
                Text(l10n.t("onb2_widgets_style", l10n.t("style_" + app.settings.widgetLook)))
                    .font(.footnote)
                    .foregroundStyle(OnbColor.secondary)
                TimelineView(.everyMinute) { ctx in
                    // "Same as app": the style chosen on the look step, as a widget left on that.
                    let entry = PrayerEntry.make(ctx.date, look: .auto, state: app.widgetState() ?? SharedState.preview)
                    ScrollView(.horizontal) {
                        HStack(alignment: .top, spacing: 12) {
                            ForEach(Self.items) { item in widget(entry, item) }
                            lockScreen(ctx.date)
                        }
                        .padding(.horizontal, 16)
                    }
                    .scrollIndicators(.hidden)
                    .padding(.horizontal, -16)
                }
                Text(howTo)
                    .font(.footnote)
                    .foregroundStyle(OnbColor.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func widget(_ entry: PrayerEntry, _ item: Item) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            PrayerWidgetView(entry: entry, kind: item.kind, family: item.family)
                .padding(16)
                .frame(width: item.size.width, height: item.size.height)
                .background(entry.palette.background)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                // The widget views hold links and intent buttons; here they only show.
                .allowsHitTesting(false)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(l10n.t("onb2_preview_a11y", l10n.t(item.name)))
            Text(l10n.t(item.name))
                .font(.caption)
                .foregroundStyle(OnbColor.secondary)
                .accessibilityHidden(true)
        }
    }

    /// iPhone: the next-prayer Live Activity as the Lock Screen shows it.
    @ViewBuilder private func lockScreen(_ now: Date) -> some View {
        #if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
        if UIDevice.current.userInterfaceIdiom == .phone, let content = LiveCountdown.content(app, now: now) {
            VStack(alignment: .leading, spacing: 6) {
                PrayerLockScreenView(attributes: content.0, state: content.1, stale: content.1.at <= now)
                    .frame(width: 338)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .allowsHitTesting(false)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(l10n.t("onb2_preview_a11y", l10n.t("live_countdown")))
                Text(l10n.t("live_countdown"))
                    .font(.caption)
                    .foregroundStyle(OnbColor.secondary)
                    .accessibilityHidden(true)
            }
        }
        #endif
    }
}
#endif

// MARK: Siri

/// Three of NearMosqueShortcuts' phrases (ios/App/AppShortcuts.swift), read from the AppShortcuts string table in the
/// system language the app runs in (an in-app choice changes neither Siri nor the app's name on the Home Screen);
/// English and Arabic have them (English only on Apple Vision Pro).
enum SiriPhrases {
    static let nextPrayer = "When is the next prayer in ${applicationName}"
    static let qibla = "Where is the Qibla in ${applicationName}"
    static let ask = "Ask ${applicationName}"

    static var languages: [String] {
        #if os(visionOS)
        return ["en"]
        #else
        return ["en", "ar"]
        #endif
    }

    /// The system language the app runs in; nil when the app doesn't have it. Not Siri's own language setting:
    /// INPreferences.siriLanguageCode() ends the app unless it has the SiriKit entitlement, which App Intents don't use.
    static var language: String? {
        Bundle.main.preferredLocalizations.first.flatMap(Languages.code(identifier:))
    }

    /// The phrase as Siri hears it in `lang`, with the app's name as the system shows it in that language.
    static func phrase(_ key: String, lang: String) -> String {
        let bundle = Bundle.main.path(forResource: Languages.lproj(lang), ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
        let shown = bundle.localizedString(forKey: "CFBundleDisplayName", value: nil, table: "InfoPlist")
        let name = shown == "CFBundleDisplayName" ? Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "" : shown
        return bundle.localizedString(forKey: key, value: key, table: "AppShortcuts")
            .replacingOccurrences(of: "${applicationName}", with: name)
    }
}

/// What to say to Siri, or (in a language without phrases) the actions in the Shortcuts app and an honest note.
struct SiriCard: View {
    @Environment(\.l10n) private var l10n

    var body: some View {
        if let lang = SiriPhrases.language, SiriPhrases.languages.contains(lang) {
            OnbCard {
                VStack(alignment: .leading, spacing: 10) {
                    CardTitle(text: l10n.t("onb2_siri_title"))
                    phrase(SiriPhrases.nextPrayer, lang: lang, symbol: "clock")
                    phrase(SiriPhrases.qibla, lang: lang, symbol: "location.north.line.fill")
                    phrase(SiriPhrases.ask, lang: lang, symbol: "sparkles")
                }
            }
        } else {
            #if !os(visionOS)
            OnbCard {
                VStack(alignment: .leading, spacing: 10) {
                    CardTitle(text: l10n.t("onb2_siri_title"))
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 8, alignment: .leading), GridItem(.flexible(), spacing: 8, alignment: .leading)],
                              alignment: .leading, spacing: 8) {
                        action("widget_kind_next", symbol: "clock")
                        action("widget_kind_qibla", symbol: "location.north.line.fill")
                        action("widget_kind_mosque", symbol: "building.columns")
                        action("widget_kind_ask", symbol: "sparkles")
                    }
                    Text(l10n.t("onb2_siri_other"))
                        .font(.footnote)
                        .foregroundStyle(OnbColor.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            #endif
        }
    }

    private func phrase(_ key: String, lang: String, symbol: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Image(systemName: symbol)
                .foregroundStyle(Theme.accent)
                .frame(width: 22)
                .accessibilityHidden(true)
            // Isolated: an Arabic phrase reads right inside an English line, and the other way round.
            Text(l10n.t("onb2_say", Weather.isolate(SiriPhrases.phrase(key, lang: lang))))
                .font(.subheadline)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func action(_ key: String, symbol: String) -> some View {
        Label(l10n.t(key), systemImage: symbol)
            .font(.footnote.weight(.semibold))
            .lineLimit(2)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(OnbColor.chip, in: Capsule())
    }
}

// MARK: Apple Watch

#if canImport(WatchConnectivity) && !targetEnvironment(macCatalyst) && !os(visionOS)
/// iPhone with a paired Apple Watch: the app is on it (or how to install it), beside a drawing of its face. No card
/// until WatchConnectivity knows; when it answers while the step is open, the card slides in.
struct WatchCard: View {
    let model: OnboardingModel
    @Environment(\.l10n) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let state = WatchPairing.shared.state
        Group {
            if case .paired(let installed)? = state {
                OnbCard {
                    HStack(alignment: .top, spacing: 14) {
                        WatchFacePreview(model: model)
                        VStack(alignment: .leading, spacing: 6) {
                            CardTitle(text: l10n.t("watch_section"))
                            Text(l10n.t(installed ? "onb2_watch_on" : "onb2_watch_install"))
                                .font(.footnote)
                                .foregroundStyle(OnbColor.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .transition(reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .animation(OnbMotion.motion(OnbMotion.soft, reduce: reduceMotion), value: state)
    }
}

/// A small watch face: the time, the next prayer in the chosen style's accent and its countdown.
struct WatchFacePreview: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        let next = app.calculator.nextPrayer(model.days, now: model.now)
        VStack(spacing: 3) {
            Text(model.now, format: .dateTime.hour().minute())
                .font(.system(size: 24, weight: .semibold, design: .rounded))
                .minimumScaleFactor(0.6)
            if let next {
                HStack(spacing: 3) {
                    Image(systemName: next.event.symbol)
                    Text(l10n.t(Format.prayerKey(next.event)))
                }
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(StyleAccent.dark(app.settings.widgetLook))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                Text(timerInterval: model.now...max(model.now, next.at), countsDown: true)
                    .font(.system(size: 13).monospacedDigit())
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }
        }
        .foregroundStyle(.white)
        .padding(8)
        .frame(width: 84, height: 102)
        .background(Color.black, in: shape)
        .overlay(shape.strokeBorder(Color.white.opacity(0.25), lineWidth: 3))
        .environment(\.locale, l10n.locale)
        .dynamicTypeSize(.large)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(l10n.t("onb2_preview_a11y", l10n.t("watch_section")))
    }
}
#endif

// MARK: Ask

/// One of the common questions (about the Friday prayer) as Ask shows it: the review label, the start of the summary
/// and its first source. It opens nothing.
struct AskCard: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n

    var body: some View {
        let list = (try? app.ask?.commonQuestions()) ?? []
        if let q = list.first(where: { $0.id == "friday-prayer" }) ?? list.first {
            let lang = l10n.language
            OnbCard {
                VStack(alignment: .leading, spacing: 10) {
                    CardTitle(text: l10n.t("tab_ask"))
                    Text(l10n.t("onb2_ask_line"))
                        .font(.footnote)
                        .foregroundStyle(OnbColor.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack {
                        Spacer(minLength: 40)
                        Text(q.question[lang] ?? q.question["en"] ?? "")
                            .font(.subheadline)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(Theme.gold.opacity(0.18), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                    Text(q.isReviewed ? l10n.t("answer_common_reviewed", q.reviewedBy ?? "") : l10n.t("answer_common_unreviewed"))
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                    Text(q.summary[lang] ?? q.summary["en"] ?? "")
                        .font(.footnote)
                        .lineLimit(2)
                    if let source = reference(q) {
                        Label(source, systemImage: "book.closed")
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(OnbColor.chip, in: Capsule())
                    }
                }
            }
            .accessibilityElement(children: .combine)
            .transition(.opacity)
        }
    }

    /// The first citation as the source chips name it ("Qur'an 62:9 · Al-Jumu'ah"), or from its id alone.
    private func reference(_ q: CommonQuestion) -> String? {
        guard let id = q.citations.first else { return nil }
        if let chunk = (try? app.ask?.resolve([id]))?.first?.chunk { return referenceLabel(chunk, l10n: l10n) }
        let parts = id.split(separator: ":")
        if parts.count == 3, parts[0] == "quran", let s = Int(parts[1]), let a = Int(parts[2]) { return l10n.t("reference_quran", s, a) }
        return nil
    }
}

// MARK: - More

/// "Also in Near Mosque": what the earlier steps did not show, as tiles (two columns; one when narrow or with the
/// largest text). Quiet is left out when the Adhan step showed its switch; the Live Activity is on extras.
struct MoreStep: View {
    let model: OnboardingModel
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var width: CGFloat = 342

    var body: some View {
        let features = OnboardingFeature.more(available: available, shownEarlier: model.adhanVisited ? [.quiet] : [])
        let columns = width < 340 || typeSize.isAccessibilitySize ? 1 : 2
        let rows = stride(from: 0, to: features.count, by: columns).map { Array(features[$0..<min($0 + columns, features.count)]) }
        OnbPage(model: model) {
            VStack(alignment: .leading, spacing: 0) {
                OnbTitle(text: l10n.t("onb2_more_title")).onbEnter(0)
                Grid(horizontalSpacing: 12, verticalSpacing: 12) {
                    ForEach(rows.indices, id: \.self) { r in
                        GridRow {
                            ForEach(rows[r], id: \.self) { f in
                                tile(f).onbEnter(1 + r * columns + (rows[r].firstIndex(of: f) ?? 0))
                            }
                            if rows[r].count < columns { Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]) }
                        }
                    }
                }
                .padding(.top, 20)
                .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
                OnbFootnote(text: l10n.t("onb2_more_foot")).padding(.top, 14).onbEnter(2)
            }
        } footer: {
            OnbPrimaryButton(title: l10n.t("onb2_continue")) { model.next() }
        }
    }

    /// What this device has.
    private var available: Set<OnboardingFeature> {
        var s: Set<OnboardingFeature> = [.iqama, .beforePrayer, .mosques, .library, .hijri]
        s.insert(app.location.headingAvailable ? .qiblaCompass : .qiblaNorth)
        #if canImport(AlarmKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
        // The Fajr alarm rings through AlarmKit (iOS 26); before that the setting is a notification, not listed here.
        if #available(iOS 26.0, *) { s.insert(.fajrAlarm) }
        #endif
        #if os(iOS) && !targetEnvironment(macCatalyst)
        s.insert(.quiet)
        #endif
        #if !os(visionOS)
        s.insert(.tasbih)
        #endif
        return s
    }

    private func tile(_ f: OnboardingFeature) -> some View {
        let t = info(f)
        return VStack(alignment: .leading, spacing: 8) {
            OnbIconCircle(icon: t.icon, size: 36)
            Text(t.name)
                .font(.subheadline.weight(.semibold))
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
            Text(t.sub)
                .font(.footnote)
                .foregroundStyle(OnbColor.secondary)
                .lineLimit(3)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 96, maxHeight: .infinity, alignment: .topLeading)
        .glass(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private func info(_ f: OnboardingFeature) -> (icon: OnbIcon, name: String, sub: String) {
        switch f {
        case .iqama: return (.symbol("timer"), l10n.t("onb2_more_iqama"), l10n.t("onb2_more_iqama_sub"))
        case .beforePrayer: return (.symbol("bell.badge"), l10n.t("onb2_more_before"), l10n.t("onb2_more_before_sub"))
        case .fajrAlarm: return (.symbol("alarm"), l10n.t("alerts_fajr_alarm"), l10n.t("onb2_more_fajr_sub"))
        case .quiet: return (.symbol("moon.zzz"), l10n.t("silence_section"), l10n.t("onb2_more_quiet_sub_ios"))
        case .qiblaCompass: return (.symbol("location.north.circle"), l10n.t("onb2_more_compass"), l10n.t("onb2_more_compass_sub"))
        case .qiblaNorth: return (.symbol("location.north.line"), l10n.t("onb2_more_qibla_north"), l10n.t("onb2_more_qibla_north_sub"))
        case .mosques: return (.asset("MosqueTab"), l10n.t("onb2_more_mosques"), l10n.t("onb2_more_mosques_sub"))
        case .library: return (.symbol("books.vertical"), l10n.t("onb2_more_library"), l10n.t("onb2_more_library_sub"))
        case .classics: return (.symbol("book"), l10n.t("classics_title"), l10n.t("onb2_more_classics_sub"))
        case .hijri: return (.symbol("calendar"), l10n.t("onb2_more_hijri"), l10n.t("onb2_more_hijri_sub"))
        case .tasbih: return (.symbol("hand.tap"), l10n.t("widget_kind_tasbih"), l10n.t(watchInstalled ? "onb2_more_tasbih_watch" : "onb2_more_tasbih_widget"))
        case .startAtSignIn: return (.symbol("power"), l10n.t("start_at_login"), l10n.t("onb2_more_startup_sub"))
        }
    }

    /// The tasbih is on this iPhone's Apple Watch too.
    private var watchInstalled: Bool {
        #if canImport(WatchConnectivity) && !targetEnvironment(macCatalyst) && !os(visionOS)
        return WatchPairing.shared.state == .paired(appInstalled: true)
        #else
        return false
        #endif
    }
}
