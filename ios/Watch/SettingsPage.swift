import NMCore
import SwiftUI

/// Where the times come from, the method (for the watch's own location), the watch's own reminders and the taps.
struct SettingsPage: View {
    @Environment(WatchModel.self) private var model
    @State private var busy = false
    @State private var note: String?

    var body: some View {
        @Bindable var model = model
        let l10n = model.l10n
        List {
            Section {
                Picker(l10n.t("location_section"), selection: Binding(get: { model.settings.source }, set: { v in
                    if v == .watch { locate() } else { model.settings.source = .phone }
                })) {
                    Text(l10n.t("watch_from_iphone")).tag(WatchSource.phone)
                    Text(l10n.t("watch_this_watch")).tag(WatchSource.watch)
                }
                if model.settings.source == .watch {
                    Button { locate() } label: {
                        if busy { ProgressView() } else { Label(l10n.t("watch_update_location"), systemImage: "location.fill") }
                    }
                    .disabled(busy)
                }
                if let s = model.state { Label(s.name, systemImage: "mappin.and.ellipse").font(.footnote) }
                if let note { Text(note).font(.footnote).foregroundStyle(.secondary) }
            } footer: {
                Text(l10n.t("watch_location_note"))
            }
            if model.settings.source == .watch {
                Section(l10n.t("prayer_section")) {
                    Picker(l10n.t("calculation_method"), selection: Binding(get: { model.settings.prayer.method }, set: { model.setMethod($0) })) {
                        ForEach(PrayerMethod.allCases, id: \.self) { m in Text(l10n.t(Format.methodKey(m))).tag(m) }
                    }
                    Picker(l10n.t("asr_method"), selection: $model.settings.prayer.madhab) {
                        Text(l10n.t("asr_standard")).tag(AsrMadhab.SHAFI)
                        Text(l10n.t("asr_hanafi")).tag(AsrMadhab.HANAFI)
                    }
                }
            }
            Section {
                Toggle(l10n.t("watch_reminders"), isOn: $model.settings.ownReminders)
                if model.remindersBlocked {
                    Text(l10n.t("watch_reminders_denied")).font(.footnote).foregroundStyle(.orange)
                }
                if model.settings.ownReminders {
                    ForEach(PrayerEvent.prayers, id: \.self) { e in
                        Toggle(l10n.t(Format.prayerKey(e)), isOn: Binding(get: { model.settings.reminders.contains(e.rawValue) }, set: { on in
                            var r = Set(model.settings.reminders)
                            if on { r.insert(e.rawValue) } else { r.remove(e.rawValue) }
                            model.settings.reminders = PrayerEvent.prayers.map(\.rawValue).filter(r.contains)
                        }))
                    }
                }
            } footer: {
                Text(l10n.t("watch_reminders_note") + " " + l10n.t("watch_reminders_sound"))
            }
            if WatchHealth.available {
                Section {
                    Toggle(l10n.t("health_toggle"), isOn: Binding(get: { model.settings.healthOn }, set: { on in
                        Task { await model.setHealth(on) }
                    }))
                    if model.settings.healthOn {
                        Stepper(value: $model.settings.healthMinutes, in: 5...30, step: 5) {
                            Text(l10n.t("health_minutes", model.settings.healthMinutes.formatted(.number.locale(l10n.locale))))
                        }
                    }
                } header: {
                    Text(l10n.t("spirit_title"))
                } footer: {
                    Text(l10n.t("health_note"))
                }
            }
            Section {
                Toggle(l10n.t("watch_qibla_haptic"), isOn: $model.settings.qiblaHaptic)
                Toggle(l10n.t("watch_tasbih_haptic"), isOn: $model.settings.tasbihHaptic)
                Toggle(l10n.t("online_search"), isOn: $model.settings.onlineSearch)
            }
            Section {
                Text(l10n.t("version_label", Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""))
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle(l10n.t("settings"))
    }

    private func locate() {
        busy = true
        Task {
            let r = await model.useWatchLocation()
            busy = false
            note = r == .ok ? nil : model.l10n.t(r == .denied ? "location_denied" : "location_fix_failed")
        }
    }
}
