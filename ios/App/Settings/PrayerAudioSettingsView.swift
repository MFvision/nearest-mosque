import NMCore
import SwiftUI

/// Prayer & Audio: every Adhan, reminder and recitation has its own switch, and none changes another. Grouped
/// as the Adhan, reminders before the prayer, each prayer (expandable), Qur'an and dhikr, and more reminders.
/// Changes are saved at once (with the rest of the settings) and the reminders are rebuilt.
struct PrayerAudioSettingsView: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.openURL) private var openURL
    @State private var expanded: Set<PrayerEvent> = []
    /// The Shortcut's name while it is typed (saved on Return, on leaving the field and on Try), read from the
    /// settings when the screen appears so Try never saves a field that was not filled in yet.
    @State private var shortcutDraft = ""

    private var a: AlertSettings { app.settings.alerts }
    private var voice: VoiceLanguage { a.voice ?? VoiceLanguage.forInterface(l10n.language) }

    var body: some View {
        ScrollViewReader { proxy in
        Form {
            if let block = app.alertBlock {
                Section { AlertAccessNotice(block: block) }
            }
            Section {
                NavigationLink { AlertCheckView() } label: {
                    SettingsRow(icon: "checklist", tint: .green, title: l10n.t("check_alerts"), subtitle: l10n.t("check_alerts_note"))
                }
            }
            adhanSection
            iqamaSection
            #if os(iOS) && !targetEnvironment(macCatalyst)
            silenceSections
            #endif
            beforeSection
            perPrayerSection
            dhikrSection
            moreSection
            Section {
                Label(l10n.t("audio_permission_note"), systemImage: "bell.badge").font(.footnote).foregroundStyle(.secondary)
                Text(l10n.t("audio_credits")).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle(l10n.t("audio_title"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { shortcutDraft = app.settings.silenceShortcut ?? "" }
        .onDisappear { if !AdhanPlayer.shared.isAdhan { AdhanPlayer.shared.stop() } }
        .task { await app.refreshAlertAccess() }
        #if DEBUG
        .task {
            // CI screenshots: `-demoAudioExpand maghrib` opens that prayer's settings and scrolls to it.
            guard let e = UserDefaults.standard.string(forKey: "demoAudioExpand").flatMap(PrayerEvent.init(rawValue:)) else { return }
            expanded = [e]
            try? await Task.sleep(for: .seconds(1))
            proxy.scrollTo(e, anchor: .top)
        }
        #endif
        }
    }

    // MARK: Sections

    private var adhanSection: some View {
        Section {
            Toggle(l10n.t("adhan_full"), isOn: bind(\.adhan))
            sourceRow(.makkah, fajr: false)
            sourceRow(.madinah, fajr: false)
            Toggle(l10n.t("adhan_fajr"), isOn: bind(\.fajrAdhan))
            sourceRow(.makkah, fajr: true)
            sourceRow(.madinah, fajr: true)
            Picker(l10n.t("adhan_length"), selection: bind(\.shortAdhan)) {
                Text(l10n.t("adhan_length_full")).tag(false)
                Text(l10n.t("adhan_short")).tag(true)
            }
            if a.shortAdhan {
                HStack {
                    Text(l10n.t("adhan_short"))
                    Spacer()
                    PreviewButton(name: "adhan_\(a.adhanSource.rawValue)_short", label: l10n.t("adhan_short"))
                }
            }
        } header: { Text(l10n.t("adhan_section")) } footer: {
            // With alarms allowed (iPhone and iPad, iOS 26) the Adhan is an alarm, not a 30-second notification sound.
            Text(l10n.t(PrayerAlarms.permission == .authorized ? "adhan_alarm_note" : "adhan_note"))
        }
    }

    private var q: IqamaSettings { app.settings.iqama }

    private func iqama<T>(_ key: WritableKeyPath<IqamaSettings, T>) -> Binding<T> {
        Binding(get: { app.settings.iqama[keyPath: key] }, set: { v in var s = app.settings.iqama; s[keyPath: key] = v; app.setIqama(s) })
    }

    /// The wait for the iqama after each Adhan, then the prayer, before the countdown to the next prayer.
    private var iqamaSection: some View {
        Section {
            Toggle(l10n.t("iqama_show"), isOn: iqama(\.enabled))
            if q.enabled {
                ForEach(PrayerEvent.prayers, id: \.self) { e in
                    Picker(selection: Binding(get: { q.wait(e) }, set: { m in var s = q; s.setWait(e, m); app.setIqama(s) })) {
                        ForEach(IqamaSettings.choices, id: \.self) { m in Text(l10n.t("minutes_value", m)).tag(m) }
                    } label: {
                        Label(l10n.t(Format.prayerKey(e)), systemImage: Theme.icon(e))
                    }
                }
                Picker(l10n.t("iqama_prayer_minutes"), selection: iqama(\.prayerMinutes)) {
                    ForEach(IqamaSettings.prayerChoices, id: \.self) { m in Text(l10n.t("minutes_value", m)).tag(m) }
                }
                Toggle(l10n.t("iqama_alert"), isOn: iqama(\.alert))
            }
        } header: { Text(l10n.t("iqama_section")) } footer: { Text(l10n.t("iqama_note")) }
    }

    #if os(iOS) && !targetEnvironment(macCatalyst)
    private var silence: SilenceSettings { app.settings.silence }

    private func silenceBinding<T>(_ key: WritableKeyPath<SilenceSettings, T>) -> Binding<T> {
        Binding(get: { app.settings.silence[keyPath: key] }, set: { v in var s = app.settings.silence; s[keyPath: key] = v; app.setSilence(s) })
    }

    private var defaultShortcutName: String { l10n.t("silence_shortcut_name") }
    /// The name Try would run: the one typed, else the one saved, else the default.
    private var shortcutName: String {
        SilenceSettings.shortcutName(SilenceSettings.storedShortcutName(shortcutDraft, current: app.settings.silenceShortcut), default: defaultShortcutName)
    }

    /// Saves the typed name (an empty field keeps the saved one) and shows what is saved.
    private func saveShortcutName() {
        app.setSilenceShortcut(shortcutDraft)
        shortcutDraft = app.settings.silenceShortcut ?? ""
    }

    /// Silent during prayer: iPhone lets no app turn on Focus, so a Shortcut the user makes once does it, run from
    /// the Adhan or iqama notification (and from Siri or the Action button).
    @ViewBuilder private var silenceSections: some View {
        Section {
            Toggle(l10n.t("silence_enable"), isOn: silenceBinding(\.enabled))
            if silence.enabled {
                Picker(selection: silenceBinding(\.start)) {
                    Text(l10n.t("silence_from_adhan")).tag(SilenceSettings.Start.adhan)
                    Text(l10n.t("silence_from_iqama")).tag(SilenceSettings.Start.iqama)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(l10n.t("silence_start"))
                        Text(l10n.t("silence_until", q.prayerMinutes)).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        } header: { Text(l10n.t("silence_section")) } footer: { Text(l10n.t("silence_note_ios")) }
        if silence.enabled {
            Section {
                ForEach(PrayerEvent.prayers, id: \.self) { e in
                    Toggle(isOn: Binding(get: { silence.includes(e) }, set: { on in var s = silence; s.set(e, on); app.setSilence(s) })) {
                        Label(l10n.t(Format.prayerKey(e)), systemImage: Theme.icon(e))
                    }
                }
                if silence.includes(.dhuhr) {
                    Picker(selection: silenceBinding(\.fridayMinutes)) {
                        ForEach(SilenceSettings.fridayChoices, id: \.self) { m in Text(l10n.t("silence_friday_minutes", m)).tag(m) }
                    } label: {
                        Label(l10n.t("silence_friday"), systemImage: "calendar")
                    }
                }
            } header: { Text(l10n.t("silence_prayers")) }
            Section {
                TextField(defaultShortcutName, text: $shortcutDraft)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
                    .onSubmit { saveShortcutName() }
                    .onDisappear { saveShortcutName() }
                Text(l10n.t("silence_ios_steps", shortcutName))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                Button { openURL(SilenceSettings.shortcutsAppURL) } label: {
                    Label(l10n.t("silence_open_shortcuts"), systemImage: "square.2.layers.3d")
                }
                Button {
                    app.setSilenceShortcut(shortcutDraft)
                    // The name run is kept as it is (the default too, so a change of language does not rename it).
                    SilenceShortcutRunner.run(app.keepSilenceShortcutName())
                    shortcutDraft = app.settings.silenceShortcut ?? ""
                } label: {
                    Label(l10n.t("silence_test"), systemImage: "moon.fill")
                }
            }
        }
    }
    #endif

    private var beforeSection: some View {
        Section {
            Toggle(l10n.t("before10"), isOn: bind(\.before10))
            Toggle(l10n.t("before5"), isOn: bind(\.before5))
            Toggle(isOn: bind(\.near)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(l10n.t("near"))
                    Text(l10n.t("near_note")).font(.caption).foregroundStyle(.secondary)
                }
            }
            if a.near {
                Picker(l10n.t("near_when"), selection: bind(\.nearMinutes)) {
                    ForEach(AlertSettings.nearChoices, id: \.self) { m in Text(l10n.t("alerts_minutes", m)).tag(m) }
                }
            }
            HStack {
                Picker(l10n.t("voice_language"), selection: Binding(get: { voice }, set: { v in update { $0.voice = v } })) {
                    Text(l10n.t("voice_arabic")).tag(VoiceLanguage.arabic)
                    Text(l10n.t("voice_english")).tag(VoiceLanguage.english)
                }
                PreviewButton(name: AudioCatalog.voice(.before10, .maghrib, voice) ?? "", label: l10n.t("voice_language"))
            }
        } header: { Text(l10n.t("before_section")) } footer: { Text(l10n.t("before_note")) }
    }

    private var perPrayerSection: some View {
        Section {
            ForEach(PrayerEvent.prayers, id: \.self) { e in
                DisclosureGroup(isExpanded: Binding(get: { expanded.contains(e) },
                                                    set: { open in if open { expanded.insert(e) } else { expanded.remove(e) } })) {
                    prayerRows(e)
                } label: {
                    HStack {
                        Label(l10n.t(Format.prayerKey(e)), systemImage: Theme.icon(e))
                        Spacer()
                        summary(e)
                    }
                }
                .id(e)
            }
            Toggle(isOn: Binding(get: { app.settings.reminders.contains(.sunrise) }, set: { on in Task { await app.setReminder(.sunrise, on) } })) {
                Label(l10n.t("sunrise_notify"), systemImage: Theme.icon(.sunrise))
            }
        } header: { Text(l10n.t("per_prayer_section")) } footer: { Text(l10n.t("per_prayer_note")) }
    }

    @ViewBuilder private func prayerRows(_ e: PrayerEvent) -> some View {
        let name = l10n.t(Format.prayerKey(e))
        Toggle(l10n.t("at_prayer_notify"), isOn: Binding(get: { app.settings.reminders.contains(e) },
                                                       set: { on in Task { await app.setReminder(e, on) } }))
        prayerToggle(l10n.t("play_adhan"), e, .adhan)
        prayerToggle(l10n.t("notif_minutes_until", 10, name), e, .before10)
        prayerToggle(l10n.t("notif_minutes_until", 5, name), e, .before5)
        prayerToggle(l10n.t("near") + " · " + l10n.t("alerts_minutes", a.nearMinutes), e, .near)
    }

    private var dhikrSection: some View {
        Section {
            timedToggle(l10n.t("salawat"), note: l10n.t("salawat_note"), on: bind(\.salawat), minutes: bind(\.salawatMinutes),
                        preview: AudioCatalog.salawat(a.reciter))
            timedToggle(l10n.t("quran_reminder"), note: l10n.t("quran_reminder_note"), on: bind(\.quran), minutes: bind(\.quranMinutes),
                        preview: AudioCatalog.evening(a.reciter))
            timedToggle(l10n.t("alerts_sleep"), note: l10n.t("alerts_sleep_note"),
                        on: Binding(get: { a.bedtimeMinutes != nil },
                                    set: { on in update { $0.bedtimeMinutes = on ? ($0.bedtimeMinutes ?? AlertSettings.defaultBedtimeMinutes) : nil } }),
                        minutes: Binding(get: { a.bedtimeMinutes ?? AlertSettings.defaultBedtimeMinutes }, set: { m in update { $0.bedtimeMinutes = m } }),
                        preview: AudioCatalog.evening(a.reciter))
            Toggle(isOn: bind(\.kahf)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(l10n.t("alerts_kahf"))
                    Text(l10n.t("alerts_kahf_note")).font(.caption).foregroundStyle(.secondary)
                }
            }
            Picker(l10n.t("reciter"), selection: bind(\.reciter)) {
                Text(l10n.t("reciter_alafasy")).tag(Reciter.alafasy)
                Text(l10n.t("reciter_classic")).tag(Reciter.classic)
            }
        } header: { Text(l10n.t("dhikr_section")) }
    }

    private var moreSection: some View {
        Section {
            Toggle(isOn: bind(\.friday)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(l10n.t("alerts_friday"))
                    Text(l10n.t("alerts_friday_note")).font(.caption).foregroundStyle(.secondary)
                }
            }
            Toggle(isOn: bind(\.ramadan)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(l10n.t("alerts_ramadan"))
                    Text(l10n.t("alerts_ramadan_note")).font(.caption).foregroundStyle(.secondary)
                }
            }
            Picker(selection: bind(\.fajrAlarmMinutesBefore)) {
                Text(l10n.t("alerts_off")).tag(Int?.none)
                ForEach(AlertSettings.fajrAlarmChoices, id: \.self) { m in
                    Text(m == 0 ? l10n.t("alerts_at_fajr") : l10n.t("alerts_minutes", m)).tag(Int?.some(m))
                }
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(l10n.t("alerts_fajr_alarm"))
                    Text(l10n.t("alerts_fajr_alarm_note")).font(.caption).foregroundStyle(.secondary)
                }
            }
        } header: { Text(l10n.t("more_reminders_section")) }
    }

    // MARK: Rows

    /// One recording to choose (checkmark) and hear (play); Madinah has no Fajr recording yet.
    @ViewBuilder private func sourceRow(_ source: AdhanSource, fajr: Bool) -> some View {
        let available = !fajr || source.hasFajr
        let chosen = (fajr ? a.fajrAdhanSource : a.adhanSource) == source
        let title = l10n.t(source == .makkah ? "source_makkah" : "source_madinah")
        HStack(spacing: 12) {
            Button {
                update { if fajr { $0.fajrAdhanSource = source } else { $0.adhanSource = source } }
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: chosen && available ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(chosen && available ? Theme.accent : .secondary)
                    Text(available ? title : l10n.t("source_unavailable", title))
                        .foregroundStyle(available ? .primary : .secondary)
                    Spacer(minLength: 0)
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!available)
            .accessibilityAddTraits(chosen && available ? .isSelected : [])
            if available { PreviewButton(name: AudioCatalog.adhan(source: source, fajr: fajr), label: title) }
        }
        .padding(.leading, 8)
    }

    /// A per-prayer switch, always usable: on here while the reminder is off above turns it on for this prayer only.
    private func prayerToggle(_ title: String, _ e: PrayerEvent, _ sound: AlertSettings.PrayerSound) -> some View {
        Toggle(title, isOn: Binding(get: { a.isOn(e, sound) }, set: { v in update { $0.setPrayer(e, sound, v) } }))
    }

    @ViewBuilder private func timedToggle(_ title: String, note: String, on: Binding<Bool>, minutes: Binding<Int>, preview: String) -> some View {
        HStack {
            Toggle(isOn: on) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                    Text(note).font(.caption).foregroundStyle(.secondary)
                }
            }
            PreviewButton(name: preview, label: title)
        }
        if on.wrappedValue {
            DatePicker(l10n.t("reminder_time"), selection: Binding(
                get: { Calendar.current.date(bySettingHour: minutes.wrappedValue / 60, minute: minutes.wrappedValue % 60, second: 0, of: Date()) ?? Date() },
                set: { d in
                    let c = Calendar.current.dateComponents([.hour, .minute], from: d)
                    minutes.wrappedValue = (c.hour ?? 0) * 60 + (c.minute ?? 0)
                }), displayedComponents: .hourAndMinute)
        }
    }

    /// Small icons for what is on for this prayer.
    private func summary(_ e: PrayerEvent) -> some View {
        let s = a.sounds(e)
        let icons: [String] = [
            app.settings.reminders.contains(e) ? "bell.fill" : nil,
            a.adhanPlays(e) ? "speaker.wave.2.fill" : nil,
            (a.before10 && s.before10) || (a.before5 && s.before5) || (a.near && s.near) ? "clock.fill" : nil,
        ].compactMap { $0 }
        return HStack(spacing: 6) {
            ForEach(icons, id: \.self) { Image(systemName: $0) }
        }
        .font(.caption)
        .foregroundStyle(Theme.accent)
        .accessibilityHidden(true)
    }

    // MARK: Saving

    private func update(_ change: (inout AlertSettings) -> Void) {
        var copy = app.settings.alerts
        change(&copy)
        app.setAlerts(copy)
    }

    private func bind<T>(_ path: WritableKeyPath<AlertSettings, T>) -> Binding<T> {
        Binding(get: { app.settings.alerts[keyPath: path] }, set: { v in update { $0[keyPath: path] = v } })
    }
}

/// Play / stop a recording in Settings.
struct PreviewButton: View {
    @Environment(\.l10n) private var l10n
    let name: String
    let label: String

    var body: some View {
        let on = AdhanPlayer.shared.playing == name
        Button { AdhanPlayer.shared.toggle(name) } label: {
            Image(systemName: on ? "stop.circle.fill" : "play.circle.fill")
                .font(.title2)
                .foregroundStyle(Theme.accent)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(on ? l10n.t("alarm_stop") : l10n.t("preview_play", label))
    }
}
