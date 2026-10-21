import AVFoundation
import NMCore
import SwiftUI
import UserNotifications

/// Which recording goes with which alert (files in Resources/Sounds, made by tools/build_audio.py; sources in
/// shared/audio/CREDITS.md). Notification sounds are the .caf files (at most 29.5 s, iOS's limit); the whole
/// Adhan is the .m4a, played by the app.
enum AudioCatalog {
    /// The whole Adhan for the prayer, or with the short Adhan chosen its first takbir (the same words at Fajr).
    static func adhan(_ e: PrayerEvent, _ a: AlertSettings) -> String {
        let source = e == .fajr ? a.fajrAdhanSource : a.adhanSource
        if a.shortAdhan { return "adhan_\(source.rawValue)_short" }
        return e == .fajr ? "fajr_\(source.rawValue)" : "adhan_\(source.rawValue)"
    }

    static func adhan(source: AdhanSource, fajr: Bool) -> String { fajr ? "fajr_\(source.rawValue)" : "adhan_\(source.rawValue)" }

    static func voice(_ kind: AlertKind, _ e: PrayerEvent, _ language: VoiceLanguage) -> String? {
        let variant: String
        switch kind {
        case .before10: variant = "10"
        case .before5: variant = "5"
        case .near: variant = "near"
        default: return nil
        }
        return "voice_\(language == .arabic ? "ar" : "en")_\(e.rawValue)_\(variant)"
    }

    static func salawat(_ r: Reciter) -> String { r == .alafasy ? "salawat_alafasy" : "salawat_husary" }
    static func evening(_ r: Reciter) -> String { r == .alafasy ? "evening_alafasy" : "evening_abdulbasit" }

    static func url(_ name: String, _ ext: String) -> URL? { Bundle.main.url(forResource: name, withExtension: ext) }

    /// `name` when its notification sound (.caf) is in the app, else nil (the default sound plays instead).
    static func available(_ name: String?) -> String? {
        guard let name, url(name, "caf") != nil else { return nil }
        return name
    }

    /// The notification sound for `name`, or the default sound when the file is missing.
    static func sound(_ name: String?) -> UNNotificationSound {
        guard let name = available(name) else { return .default }
        return UNNotificationSound(named: UNNotificationSoundName(name + ".caf"))
    }
}

/// Plays the whole Adhan in the app (and previews in Settings). The bar at the top of the app shows what is
/// playing, with Stop. Audio plays only while the app is open; other audio is lowered meanwhile.
@MainActor
@Observable
final class AdhanPlayer: NSObject, AVAudioPlayerDelegate {
    static let shared = AdhanPlayer()

    private(set) var playing: String?
    private(set) var title: String?
    /// True for the Adhan itself (the bar shows), false for a preview in Settings.
    private(set) var isAdhan = false
    @ObservationIgnored private var player: AVAudioPlayer?

    /// Plays `name` (.m4a if there is one, else .caf) from `offset` seconds; nothing if `offset` is past the end.
    func play(_ name: String, title: String? = nil, adhan: Bool = false, offset: TimeInterval = 0) {
        stop()
        guard let url = AudioCatalog.url(name, "m4a") ?? AudioCatalog.url(name, "caf"),
              let p = try? AVAudioPlayer(contentsOf: url), offset < p.duration - 1 else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        p.delegate = self
        p.currentTime = max(0, offset)
        guard p.play() else { return }
        player = p
        playing = name
        self.title = title
        isAdhan = adhan
    }

    func toggle(_ name: String) {
        if playing == name { stop() } else { play(name) }
    }

    func stop() {
        player?.stop()
        player = nil
        playing = nil
        title = nil
        isAdhan = false
        try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in self.stop() }
    }
}

/// "Adhan · Masjid al-Haram, Makkah  [Stop]" across the top while the whole Adhan plays.
struct AdhanNowPlayingBar: View {
    @Environment(\.l10n) private var l10n
    private var player: AdhanPlayer { .shared }

    var body: some View {
        if player.isAdhan, let title = player.title {
            HStack(spacing: 12) {
                Image(systemName: "speaker.wave.2.fill").foregroundStyle(Theme.accent).symbolEffect(.variableColor.iterative)
                Text(title).font(.subheadline.weight(.semibold)).lineLimit(1)
                Spacer(minLength: 8)
                Button { player.stop() } label: {
                    Label(l10n.t("alarm_stop"), systemImage: "stop.fill").font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 14).frame(minHeight: 44)
                }
                .glassButton()
            }
            .foregroundStyle(Theme.ink)
            .padding(.leading, 16).padding(.trailing, 6).padding(.vertical, 4)
            .glass(Capsule())
            .padding(.horizontal, 16)
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}
