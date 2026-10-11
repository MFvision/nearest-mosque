import NMCore
import SwiftUI

/// Prayer & Audio: every Adhan, reminder and recitation has its own switch, and none changes another. Grouped
/// as the Adhan, reminders before the prayer, each prayer (expandable), Qur'an and dhikr, and more reminders.
/// Changes are saved at once (with the rest of the settings) and the reminders are rebuilt.
struct PrayerAudioSettingsView: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @State private var expanded: Set<PrayerEvent> = []

    private var a: AlertSettings { app.settings.alerts }
    private var voice: VoiceLanguage { a.voice ?? VoiceLanguage.forInterface(l10n.language) }

    var body: some View {
        ScrollViewReader { proxy in
        Form {
            adhanSection
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
        .onDisappear { if !AdhanPlayer.shared.isAdhan { AdhanPlayer.shared.stop() } }
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
        } header: { Text(l10n.t("adhan_section")) } footer: { Text(l10n.t("adhan_note")) }
    }

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
        } header: { Text(l10n.t("per_prayer_section")) } footer: { Text(l10n.t("per_prayer_note")) }
    }

    @ViewBuilder private func prayerRows(_ e: PrayerEvent) -> some View {
        let s = a.sounds(e)
        let name = l10n.t(Format.prayerKey(e))
        Toggle(l10n.t("at_prayer_notify"), isOn: Binding(get: { app.settings.reminders.contains(e) },
                                                       set: { on in Task { await app.setReminder(e, on) } }))
        gated(l10n.t("play_adhan"), on: s.adhan, master: e == .fajr ? a.fajrAdhan : a.adhan) { v in update { $0.setSounds(e, with(s) { $0.adhan = v }) } }
        gated(l10n.t("notif_minutes_until", 10, name), on: s.before10, master: a.before10) { v in update { $0.setSounds(e, with(s) { $0.before10 = v }) } }
        gated(l10n.t("notif_minutes_until", 5, name), on: s.before5, master: a.before5) { v in update { $0.setSounds(e, with(s) { $0.before5 = v }) } }
        gated(l10n.t("near") + " · " + l10n.t("alerts_minutes", a.nearMinutes), on: s.near, master: a.near) { v in
            update { $0.setSounds(e, with(s) { $0.near = v }) }
        }
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

    /// A per-prayer switch that works only while the same reminder is on for all prayers above.
    private func gated(_ title: String, on: Bool, master: Bool, set: @escaping (Bool) -> Void) -> some View {
        Toggle(isOn: Binding(get: { on }, set: set)) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                if !master { Text(l10n.t("off_above")).font(.caption).foregroundStyle(.secondary) }
            }
        }
        .disabled(!master)
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

    private func with(_ s: PrayerSounds, _ change: (inout PrayerSounds) -> Void) -> PrayerSounds {
        var copy = s
        change(&copy)
        return copy
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
