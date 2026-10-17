import NMCore
import NMData
import SwiftUI
#if canImport(WatchConnectivity) && !targetEnvironment(macCatalyst) && !os(visionOS)
import WatchConnectivity
#endif
import UniformTypeIdentifiers

/// Settings, grouped like the phone's own: General (language, appearance), Prayer (location, calculation,
/// Prayer & Audio, Lock Screen countdown), Personalize (companion, widget style), Mosques, Downloads, and About.
/// Each row shows its current value; longer choices open their own page.
struct SettingsView: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    @State private var showCity = false
    @State private var showCalc = false
    @AppStorage("appearance") private var appearance = "system"
    #if DEBUG
    /// CI screenshots: `-demoAudioSettings YES` opens Prayer & Audio.
    @State private var demoAudio = UserDefaults.standard.bool(forKey: "demoAudioSettings")
    #endif

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    NavigationLink { LanguagePickerView() } label: {
                        SettingsRow(icon: "globe", tint: .blue, title: l10n.t("language"),
                                    value: l10n.override == nil ? l10n.t("use_device_language") : (Localization.nativeNames[l10n.language] ?? l10n.language))
                    }
                    Picker(selection: $appearance) {
                        Text(l10n.t("appearance_system")).tag("system")
                        Text(l10n.t("appearance_light")).tag("light")
                        Text(l10n.t("appearance_dark")).tag("dark")
                    } label: {
                        SettingsRow(icon: "circle.lefthalf.filled", tint: .indigo, title: l10n.t("appearance"))
                    }
                    .pickerStyle(.menu)
                } header: { Text(l10n.t("settings_general")) }

                if ContentDownloadCard.offered(l10n.language, app) {
                    Section { ContentDownloadCard(lang: l10n.language) }
                }

                Section {
                    Button { showCity = true } label: {
                        SettingsRow(icon: "location.fill", tint: .green, title: l10n.t("location_section"), value: app.settings.location?.name, chevron: true)
                    }
                    .buttonStyle(.plain)
                    Button { showCalc = true } label: {
                        SettingsRow(icon: "sun.horizon.fill", tint: .orange, title: l10n.t("calculation"),
                                    value: l10n.t(Format.methodKey(app.settings.prayer.method)), chevron: true)
                    }
                    .buttonStyle(.plain)
                    NavigationLink { PrayerAudioSettingsView() } label: {
                        SettingsRow(icon: "speaker.wave.2.fill", tint: .red, title: l10n.t("audio_title"), subtitle: l10n.t("audio_row_note"))
                    }
                    #if !targetEnvironment(macCatalyst) && !os(visionOS)
                    Toggle(isOn: Binding(get: { app.settings.liveCountdown }, set: { app.settings.liveCountdown = $0 })) {
                        SettingsRow(icon: "lock.iphone", tint: .purple, title: l10n.t("live_countdown"), subtitle: l10n.t("live_countdown_note"))
                    }
                    #endif
                } header: { Text(l10n.t("prayer_section")) } footer: { Text(l10n.t("alerts_bells_note")) }

                Section {
                    NavigationLink {
                        Form {
                            Section {
                                CompanionPicker(selection: Binding(get: { app.settings.companion }, set: { app.settings.companion = $0 }))
                            } footer: { Text(l10n.t("companion_note")) }
                        }
                        .navigationTitle(l10n.t("companion_title"))
                        .navigationBarTitleDisplayMode(.inline)
                    } label: {
                        SettingsRow(icon: "flame.fill", tint: .pink, title: l10n.t("companion_title"),
                                    value: l10n.t("companion_" + (CompanionKind(rawValue: app.settings.companion) ?? .lantern).rawValue))
                    }
                    NavigationLink {
                        Form {
                            Section {
                                WidgetStylePicker(selection: Binding(get: { app.settings.widgetLook }, set: { app.settings.widgetLook = $0 }))
                            } footer: { Text(l10n.t("app_style_note")) }
                        }
                        .navigationTitle(l10n.t("app_style_title"))
                        .navigationBarTitleDisplayMode(.inline)
                    } label: {
                        SettingsRow(icon: "paintpalette.fill", tint: .teal, title: l10n.t("app_style_title"), value: l10n.t("style_" + app.settings.widgetLook))
                    }
                    // Sky follows the weather (opt-in): what is sent and to whom is in the note, the credit MET's
                    // licence asks for is in the footer.
                    Toggle(isOn: Binding(get: { app.settings.skyWeather }, set: { app.settings.skyWeather = $0 })) {
                        SettingsRow(icon: "cloud.sun.fill", tint: .cyan, title: l10n.t("sky_weather"), subtitle: l10n.t("sky_weather_note"))
                    }
                    #if !targetEnvironment(macCatalyst) && !os(visionOS)
                    // Only where an Apple Watch can be paired (WatchConnectivity is supported on iPhone only).
                    if WCSession.isSupported() {
                        NavigationLink { WatchFacesView() } label: {
                            SettingsRow(icon: "applewatch", tint: .orange, title: l10n.t("watch_section"))
                        }
                    }
                    #endif
                } header: { Text(l10n.t("settings_personalize")) } footer: {
                    Link(l10n.t("sky_weather_attribution"), destination: URL(string: Weather.attributionURL)!)
                }

                Section {
                    Toggle(isOn: Binding(get: { app.settings.onlineSearch }, set: { app.settings.onlineSearch = $0 })) {
                        SettingsRow(icon: "magnifyingglass", tint: .cyan, title: l10n.t("online_search"))
                    }
                } header: { Text(l10n.t("tab_mosques")) } footer: { Text(l10n.t("online_search_body")) }

                Section {
                    NavigationLink { DownloadsSettingsView() } label: {
                        SettingsRow(icon: "arrow.down.circle.fill", tint: .blue, title: l10n.t("downloads"), subtitle: l10n.t("downloads_body"))
                    }
                }

                Section {
                    NavigationLink {
                        Form { Section { Text(l10n.t("privacy_body")) } }
                            .navigationTitle(l10n.t("privacy")).navigationBarTitleDisplayMode(.inline)
                    } label: {
                        SettingsRow(icon: "hand.raised.fill", tint: .gray, title: l10n.t("privacy"))
                    }
                    NavigationLink { SourcesSettingsView() } label: {
                        SettingsRow(icon: "doc.text.fill", tint: .brown, title: l10n.t("sources_licenses"))
                    }
                    SettingsRow(icon: "cpu", tint: .mint, title: l10n.t("on_device_ai"),
                                subtitle: (LocalAnswerer.availability(language: l10n.language) == .available ? l10n.t("ai_status_local") : l10n.t("ai_pack_not_installed"))
                                    + " " + l10n.t("cloud_ai_off"))
                    Button {
                        dismiss()
                        app.settings.onboarded = false
                    } label: {
                        SettingsRow(icon: "sparkles", tint: .yellow, title: l10n.t("onb_replay"), chevron: true)
                    }
                    .buttonStyle(.plain)
                    SettingsRow(icon: "info.circle.fill", tint: .secondary, title: l10n.t("about"),
                                value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String)
                } header: { Text(l10n.t("about")) }

                // Who makes the app and how to reach them, as on the App Store page and the website.
                Section {
                    linkRow(Publisher.companyURL, icon: "building.2.fill", tint: .indigo, title: l10n.t("about_publisher"), value: Publisher.name)
                    linkRow(Publisher.websiteURL(l10n.language), icon: "globe", tint: .teal, title: l10n.t("website"), value: Publisher.website)
                    linkRow(Publisher.emailURL, icon: "envelope.fill", tint: .blue, title: l10n.t("about_support"), value: Publisher.email)
                    linkRow(Publisher.phoneURL, icon: "phone.fill", tint: .green, title: l10n.t("about_phone"), value: Publisher.phoneDisplay)
                    linkRow(Publisher.termsURL(l10n.language), icon: "doc.plaintext.fill", tint: .gray, title: l10n.t("about_terms"), value: nil)
                } footer: {
                    Text("© \(Publisher.year) \(Publisher.name). " + l10n.t("about_rights"))
                }
            }
            .navigationTitle(l10n.t("settings"))
            #if DEBUG
            .navigationDestination(isPresented: $demoAudio) { PrayerAudioSettingsView() }
            #endif
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button { dismiss() } label: { Label(l10n.t("done"), systemImage: "checkmark") }.keyboardShortcut(.cancelAction) } }
            .sheet(isPresented: $showCity) { CityPickerView() }
            .sheet(isPresented: $showCalc) { CalculationView() }
        }
    }
}

extension SettingsView {
    /// A row that opens a web page, an email or a call.
    func linkRow(_ url: URL, icon: String, tint: Color, title: String, value: String?) -> some View {
        Link(destination: url) {
            SettingsRow(icon: icon, tint: tint, title: title, value: value, chevron: true)
        }
        .foregroundStyle(.primary)
    }
}

/// The publisher's details shown in Settings (the same as on the App Store page and nearmosque.net).
enum Publisher {
    static let name = "AVA ai"
    static let companyURL = URL(string: "https://avaai.sa")!
    static let website = "nearmosque.net"
    static let email = "app@vision.bz"
    static let phone = "+966579372403"
    /// Kept left to right inside Arabic text.
    static let phoneDisplay = "\u{2066}+966 57 937 2403\u{2069}"
    static let year = 2026
    static var emailURL: URL { URL(string: "mailto:\(email)")! }
    static var phoneURL: URL { URL(string: "tel:\(phone)")! }
    /// The site and its terms page, in Arabic for an Arabic interface.
    static func websiteURL(_ language: String) -> URL { URL(string: "https://nearmosque.net/" + (language == "ar" ? "#ar" : ""))! }
    static func termsURL(_ language: String) -> URL { URL(string: "https://nearmosque.net/terms.html" + (language == "ar" ? "#ar" : ""))! }
}

/// A settings row: a coloured icon tile, the title (and a note), and the current value.
struct SettingsRow: View {
    let icon: String
    let tint: Color
    let title: String
    var subtitle: String?
    var value: String?
    var chevron = false

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 30, height: 30)
                .background(tint.gradient, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).foregroundStyle(.primary)
                if let subtitle { Text(subtitle).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true) }
            }
            Spacer(minLength: 8)
            if let value { Text(value).foregroundStyle(.secondary).lineLimit(1).multilineTextAlignment(.trailing) }
            if chevron { Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary).accessibilityHidden(true) }
        }
        .frame(minHeight: 44)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

/// Downloaded content: what is on the phone, import a pack, restore the built-in packs.
struct DownloadsSettingsView: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @State private var installed: [InstalledPack] = []
    @State private var confirmRemove: InstalledPack?
    @State private var importing = false
    @State private var message: String?

    var body: some View {
        Form {
            Section {
                if let m = app.packs?.citiesManifest() {
                    packRow(l10n.t("pack_kind_cities"), m, records: m.recordCount ?? 0, bytes: m.files.reduce(0) { $0 + $1.bytes }, builtin: true)
                }
                ForEach(installed) { p in
                    let title = p.kind == "mosques" ? l10n.t("pack_kind_mosques", p.manifest.coverage?.name ?? p.manifest.title(l10n.language))
                        : ChunkScope.isLibrary(p.id) ? l10n.t("pack_kind_library", p.manifest.title(l10n.language))
                        : l10n.t("pack_kind_books", p.manifest.title(l10n.language))
                    packRow(title, p.manifest, records: p.recordCount, bytes: p.bytes, builtin: p.builtin)
                        .swipeActions { Button(l10n.t("remove"), role: .destructive) { confirmRemove = p } }
                        .contextMenu { Button(l10n.t("remove"), role: .destructive) { confirmRemove = p } }
                }
            } footer: { Text(l10n.t("downloads_body")) }
            Section {
                Button(l10n.t("import_pack")) { importing = true }
                Button(l10n.t("restore_builtin")) { app.packs?.restoreBuiltins(include: ChunkScope.builtins(for: l10n.language)); reload() }
                if let message { Text(message).font(.footnote) }
            }
        }
        .navigationTitle(l10n.t("downloads"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: reload)
        .alert(confirmRemove.map { l10n.t("remove_pack_confirm", $0.manifest.coverage?.name ?? $0.manifest.title(l10n.language)) } ?? "",
               isPresented: Binding(get: { confirmRemove != nil }, set: { if !$0 { confirmRemove = nil } })) {
            Button(l10n.t("remove"), role: .destructive) {
                if let p = confirmRemove { try? app.packs?.remove(p.id); reload() }
            }
            Button(l10n.t("cancel"), role: .cancel) {}
        }
        // A pack is a folder with manifest.json and its data files (hash-verified on import).
        .fileImporter(isPresented: $importing, allowedContentTypes: [.folder]) { result in
            guard case let .success(url) = result else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            do {
                let m = try app.packs?.importFolder(url, reserved: Set((app.downloads.catalog.packs ?? []).map(\.id)))
                message = m.map { l10n.t("import_done", $0.title(l10n.language)) }
            } catch PackError.reserved {
                message = l10n.t("import_failed", l10n.t("error_pack_reserved"))
            } catch PackError.checksum {
                message = l10n.t("import_failed", l10n.t("error_pack_checksum"))
            } catch PackError.newerSchema {
                message = l10n.t("import_failed", l10n.t("error_pack_newer"))
            } catch {
                message = l10n.t("import_failed", l10n.t("error_pack_format"))
            }
            reload()
        }
    }

    private func reload() { installed = (try? app.packs?.installed()) ?? [] }

    private func packRow(_ title: String, _ m: PackManifest, records: Int, bytes: Int, builtin: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            Text(l10n.t("pack_details", Format.number(Double(records), digits: 0, locale: l10n.locale),
                        ByteCountFormatter.string(fromByteCount: Int64(bytes), countStyle: .file), String(m.version))
                 + (builtin ? " · " + l10n.t("pack_builtin") : ""))
                .font(.caption).foregroundStyle(.secondary)
        }
    }
}

/// Where the data, code and recordings come from, and their licences.
struct SourcesSettingsView: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n

    var body: some View {
        let installed = (try? app.packs?.installed()) ?? []
        let manifests = [app.packs?.citiesManifest()].compactMap { $0 } + installed.map(\.manifest)
        Form {
            Section {
                ForEach(Array(Set(manifests.map(\.license.attribution))).sorted(), id: \.self) { a in
                    let m = manifests.first { $0.license.attribution == a }!
                    VStack(alignment: .leading) {
                        Text(a)
                        Text(m.license.name + " · " + m.license.url).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            Section {
                Text(l10n.t("audio_credits")).font(.callout)
                Text("Adhan (MIT) · Batoul Apps; GRDB (MIT) · Gwendal Roué").font(.caption)
                Text("Search model: static-similarity-mrl-multilingual-v1 · sentence-transformers (Apache-2.0), reduced to 256 dimensions").font(.caption)
                Text("Map and live mosque results: Apple Maps (online)").font(.caption)
                // Sky follows the weather (CC BY 4.0 and NLOD 2.0 ask for the credit and a link to the licence).
                Text(Weather.credit).font(.caption)
                Link(destination: URL(string: "https://api.met.no/doc/License")!) { Text(verbatim: "api.met.no/doc/License") }.font(.caption)
            }
        }
        .navigationTitle(l10n.t("sources_licenses"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// The widget styles as swatches; the chosen one is ringed in gold.
struct WidgetStylePicker: View {
    @Environment(\.l10n) private var l10n
    @Binding var selection: String

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 16) {
                ForEach(WidgetLook.choices, id: \.rawValue) { look in
                    let on = selection == look.rawValue
                    Button { selection = look.rawValue } label: {
                        VStack(spacing: 8) {
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(WidgetPalette.of(look, period: .maghrib).background)
                                .overlay(Image(systemName: "moon.stars").foregroundStyle(WidgetPalette.of(look, period: .maghrib).accent))
                                .frame(width: 56, height: 56)
                                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(on ? Theme.gold : Color.secondary.opacity(0.3), lineWidth: on ? 3 : 1))
                            Text(l10n.t("style_\(look.rawValue)")).font(.caption).lineLimit(2).multilineTextAlignment(.center).frame(width: 72)
                        }
                        .frame(minWidth: 44, minHeight: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(l10n.t("style_\(look.rawValue)"))
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            .padding(.vertical, 8)
        }
    }
}

/// Lantern, heart or none, each shown as it looks today.
private struct CompanionPicker: View {
    @Environment(\.l10n) private var l10n
    @Binding var selection: String

    var body: some View {
        let level = CompanionLog.level()
        HStack(spacing: 12) {
            ForEach(CompanionKind.allCases, id: \.self) { k in
                let on = selection == k.rawValue
                Button { selection = k.rawValue } label: {
                    VStack(spacing: 8) {
                        Group {
                            if k == .none {
                                Image(systemName: "circle.slash").font(.system(size: 30)).foregroundStyle(.secondary)
                            } else {
                                CompanionFigure(kind: k, level: level)
                            }
                        }
                        .frame(width: 56, height: 60)
                        Text(l10n.t("companion_" + k.rawValue)).font(.footnote.weight(on ? .semibold : .regular))
                    }
                    .frame(maxWidth: .infinity, minHeight: 104)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Theme.gold.opacity(on ? 0.16 : 0)))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(on ? Theme.gold : Color.secondary.opacity(0.3), lineWidth: on ? 2 : 1))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(.vertical, 4)
    }
}
