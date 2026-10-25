import SwiftUI

/// The App Clip: the nearest mosques from where you stand, with nothing to install. It opens from Apple's App Clip
/// link (shared in WhatsApp or Messages), a QR code, an NFC tag or the Safari banner on the website.
@main
struct NearMosqueClipApp: App {
    @State private var l10n = Localization()

    var body: some Scene {
        WindowGroup {
            ClipView()
                .environment(l10n)
                .environment(\.layoutDirection, l10n.layoutDirection)
                .environment(\.locale, l10n.locale)
                .preferredColorScheme(.dark)
        }
    }
}
