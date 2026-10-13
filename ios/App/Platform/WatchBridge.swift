#if canImport(WatchConnectivity) && !targetEnvironment(macCatalyst) && !os(visionOS)
import Foundation
import WatchConnectivity

/// Gives the Apple Watch app what the widgets get (city, method, language, reminders, nearest mosques, today's
/// questions) and how full the companion is, through WatchConnectivity's application context: the latest value
/// waits on the watch until the watch app reads it. It goes only to the paired watch, never to a server.
@MainActor
final class WatchBridge: NSObject, WCSessionDelegate {
    static let shared = WatchBridge()
    private var pending: [String: Any]?
    private var lastSent: (state: Data?, level: Double)?

    func send(_ state: SharedState?, companionLevel: Double) {
        guard WCSession.isSupported() else { return }
        let data = state.flatMap { try? JSONEncoder().encode($0) }
        let level = (companionLevel * 20).rounded() / 20
        var ctx: [String: Any] = ["level": level]
        if let data { ctx["state"] = data }
        pending = ctx
        if lastSent.map({ $0.state == data && $0.level == level }) == true { return }
        let session = WCSession.default
        if session.delegate == nil {
            session.delegate = self
            session.activate()
            return
        }
        flush()
    }

    private func flush() {
        let session = WCSession.default
        guard session.activationState == .activated, session.isPaired, session.isWatchAppInstalled, let ctx = pending else { return }
        do {
            try session.updateApplicationContext(ctx)
            lastSent = (ctx["state"] as? Data, ctx["level"] as? Double ?? 0)
        } catch {
            // Tried again with the next change, or when the watch app is installed.
        }
    }

    nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        Task { @MainActor in self.flush() }
    }

    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}

    nonisolated func sessionDidDeactivate(_ session: WCSession) {
        // Switching to another watch: start again for the new one.
        session.activate()
    }

    /// The watch app was installed (or the watch changed): send the latest again.
    nonisolated func sessionWatchStateDidChange(_ session: WCSession) {
        Task { @MainActor in
            self.lastSent = nil
            self.flush()
        }
    }
}
#endif
