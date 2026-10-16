import Foundation
import NMCore
import UIKit

/// Silent during prayer on iPhone: no app may turn on Focus, so the user makes a Shortcut once ("End of prayer",
/// then "Set Focus: Do Not Disturb, On, Until" its result) and the app runs it by name, from the button on the
/// Adhan or iqama notification and from Settings. Nothing leaves the phone.
@MainActor
enum SilenceShortcutRunner {
    /// iPhone and iPad, where the Shortcuts app opens `shortcuts://` links. Not the Mac app or Apple Vision Pro.
    nonisolated static var supported: Bool {
        #if os(iOS) && !targetEnvironment(macCatalyst)
        return true
        #else
        return false
        #endif
    }

    #if os(iOS) && !targetEnvironment(macCatalyst)
    private static var observer: NSObjectProtocol?

    /// Runs the Shortcut named `name`. From a notification button the app may still be coming to the screen, and
    /// iOS opens another app's link only from an app on the screen: then it waits for that (a minute at most).
    static func run(_ name: String) {
        guard let url = SilenceSettings.runShortcutURL(name) else { return }
        if let o = observer {
            NotificationCenter.default.removeObserver(o)
            observer = nil
        }
        if UIApplication.shared.applicationState == .active {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
            return
        }
        let deadline = Date().addingTimeInterval(60)
        observer = NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated {
                if let o = SilenceShortcutRunner.observer {
                    NotificationCenter.default.removeObserver(o)
                    SilenceShortcutRunner.observer = nil
                }
                if Date() < deadline { UIApplication.shared.open(url, options: [:], completionHandler: nil) }
            }
        }
    }
    #endif

    /// The notification button "Silence until after the prayer": the Shortcut named in Settings.
    static func runFromNotification() {
        #if os(iOS) && !targetEnvironment(macCatalyst)
        run(AppModel.shared.silenceShortcutName)
        #endif
    }
}
