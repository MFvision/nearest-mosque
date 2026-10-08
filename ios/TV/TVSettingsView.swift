import NMCore
import SwiftUI

/// First launch: choose the city (or use the TV's location) before the board appears.
struct TVSetupView: View {
    @Environment(TVModel.self) private var model

    var body: some View {
        let l10n = model.l10n
        NavigationStack {
            HStack(spacing: 80) {
                LogoDisc(size: 320, glow: true)
                VStack(alignment: .leading, spacing: 28) {
                    Text(l10n.t("onb_welcome_title")).font(.system(size: 72, weight: .bold))
                    Text(l10n.t("tv_choose_city_body")).font(.system(size: 34)).foregroundStyle(.white.opacity(0.85))
                    TVLocateButton()
                    NavigationLink {
                        TVCityPicker()
                    } label: {
                        Label(l10n.t("search_city"), systemImage: "magnifyingglass")
                    }
                    NavigationLink {
                        TVLanguagePicker()
                    } label: {
                        Label(l10n.t("language") + ": " + (Localization.nativeNames[l10n.language] ?? l10n.language), systemImage: "globe")
                    }
                }
                .frame(maxWidth: 900, alignment: .leading)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background { SkyBackdrop(sky: Sky.of(.fajr), horizon: 0.8, skyline: true).ignoresSafeArea() }
        }
    }
}

/// "Use my location": the TV's own location, with the outcome said in words.
struct TVLocateButton: View {
    @Environment(TVModel.self) private var model
    @State private var locating = false
    @State private var error: String?

    var body: some View {
        let l10n = model.l10n
        VStack(alignment: .leading, spacing: 10) {
            Button {
                Task {
                    locating = true
                    let r = await model.useTVLocation()
                    locating = false
                    switch r {
                    case .ok: error = nil
                    case .denied: error = l10n.t("location_denied")
                    case .noFix: error = l10n.t("location_fix_failed")
                    }
                }
            } label: {
                Label(l10n.t(locating ? "locating" : "use_my_location"), systemImage: "location.fill")
            }
            .disabled(locating)
            if let error { Text(error).font(.system(size: 24)).foregroundStyle(.orange) }
        }
    }
}

struct TVCityPicker: View {
    @Environment(TVModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    var body: some View {
        let l10n = model.l10n
        let results = model.cities?.search(query, limit: 40) ?? []
        List {
            if results.isEmpty && !query.isEmpty {
                Text(l10n.t("no_city_results")).foregroundStyle(.secondary)
            }
            ForEach(results) { city in
                Button {
                    model.choose(city: city)
                    dismiss()
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(city.displayName(l10n.language))
                        Text(Format.country(city.countryCode, locale: l10n.locale) + " · " + city.zoneId).font(.system(size: 22)).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .searchable(text: $query, prompt: l10n.t("search_city"))
        .navigationTitle(l10n.t("choose_city_title"))
    }
}

struct TVLanguagePicker: View {
    @Environment(TVModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let l10n = model.l10n
        List {
            Button {
                l10n.override = nil
                dismiss()
            } label: {
                row(l10n.t("use_device_language"), on: l10n.override == nil)
            }
            ForEach(Languages.all, id: \.code) { lang in
                Button {
                    l10n.override = lang.code
                    dismiss()
                } label: {
                    row(lang.name, on: l10n.override == lang.code)
                }
            }
        }
        .navigationTitle(l10n.t("language"))
    }

    private func row(_ title: String, on: Bool) -> some View {
        HStack {
            Text(title)
            Spacer()
            if on { Image(systemName: "checkmark") }
        }
    }
}

/// City, calculation, language and the TV's own options (keep the board on screen, the prayer alert,
/// online mosque search).
struct TVSettingsView: View {
    @Environment(TVModel.self) private var model

    var body: some View {
        @Bindable var model = model
        let l10n = model.l10n
        NavigationStack {
            Form {
                Section(l10n.t("location_section")) {
                    NavigationLink {
                        TVCityPicker()
                    } label: {
                        LabeledContent(l10n.t("choose_city_title"), value: model.settings.location?.name ?? "")
                    }
                    TVLocateButton()
                }
                Section(l10n.t("calculation")) {
                    Picker(l10n.t("calculation_method"), selection: Binding(get: { model.settings.prayer.method }, set: { model.setMethod($0) })) {
                        ForEach(PrayerMethod.allCases, id: \.self) { Text(l10n.t(Format.methodKey($0))).tag($0) }
                    }
                    Picker(l10n.t("asr_method"), selection: $model.settings.prayer.madhab) {
                        Text(l10n.t("asr_standard")).tag(AsrMadhab.SHAFI)
                        Text(l10n.t("asr_hanafi")).tag(AsrMadhab.HANAFI)
                    }
                    Picker(l10n.t("high_latitude_rule"), selection: $model.settings.prayer.highLatitudeRule) {
                        Text(l10n.t("hl_auto")).tag(HighLatRule.AUTO)
                        Text(l10n.t("hl_middle")).tag(HighLatRule.MIDDLE_OF_THE_NIGHT)
                        Text(l10n.t("hl_seventh")).tag(HighLatRule.SEVENTH_OF_THE_NIGHT)
                        Text(l10n.t("hl_twilight")).tag(HighLatRule.TWILIGHT_ANGLE)
                    }
                    Picker(l10n.t("polar_rule"), selection: $model.settings.prayer.polarRule) {
                        Text(l10n.t("polar_none")).tag(PolarRule.UNAVAILABLE)
                        Text(l10n.t("polar_nearest")).tag(PolarRule.NEAREST_LATITUDE)
                    }
                    if model.settings.prayer.method == .UMM_AL_QURA {
                        Toggle(l10n.t("ramadan_isha"), isOn: $model.settings.prayer.ramadanIshaExtension)
                    }
                    Picker(l10n.t("hijri_adjustment"), selection: $model.settings.prayer.hijriAdjustmentDays) {
                        ForEach(-2...2, id: \.self) { Text(signed($0)).tag($0) }
                    }
                    NavigationLink(l10n.t("minute_adjustments")) { TVOffsetsView() }
                }
                Section {
                    NavigationLink {
                        TVLanguagePicker()
                    } label: {
                        LabeledContent(l10n.t("language"), value: l10n.override.flatMap { Localization.nativeNames[$0] } ?? l10n.t("use_device_language"))
                    }
                }
                Section {
                    Toggle(l10n.t("tv_keep_awake"), isOn: $model.settings.keepAwake)
                    Toggle(l10n.t("tv_alert"), isOn: $model.settings.prayerAlert)
                } footer: {
                    Text(l10n.t("tv_keep_awake_note") + " " + l10n.t("tv_alert_note"))
                }
                Section {
                    Toggle(l10n.t("online_search"), isOn: $model.settings.onlineSearch)
                } footer: {
                    Text(l10n.t("online_search_body"))
                }
                Section(l10n.t("about")) {
                    LabeledContent(l10n.t("app_name"), value: l10n.t("version_label", Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""))
                    Text(l10n.t("data_attribution_osm")).font(.system(size: 22)).foregroundStyle(.secondary)
                    Text(l10n.t("draft_translations_note")).font(.system(size: 22)).foregroundStyle(.secondary)
                }
            }
            .navigationTitle(l10n.t("settings"))
        }
    }

    private func signed(_ v: Int) -> String {
        let n = Format.number(Double(abs(v)), digits: 0, locale: model.l10n.locale)
        return v > 0 ? "+" + n : v < 0 ? "−" + n : n
    }
}

/// Minutes added to or taken from each time, to match a local mosque's timetable.
struct TVOffsetsView: View {
    @Environment(TVModel.self) private var model

    var body: some View {
        @Bindable var model = model
        let l10n = model.l10n
        Form {
            ForEach(PrayerEvent.allCases, id: \.self) { e in
                Picker(l10n.t(Format.prayerKey(e)), selection: Binding(get: { model.settings.prayer.offsets[e] ?? 0 },
                                                                       set: { model.settings.prayer.offsets[e] = $0 == 0 ? nil : $0 })) {
                    ForEach(-30...30, id: \.self) { Text(l10n.t("minutes_value", $0)).tag($0) }
                }
            }
        }
        .navigationTitle(l10n.t("minute_adjustments"))
    }
}
