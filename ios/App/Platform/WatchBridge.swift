#if canImport(WatchConnectivity) && !targetEnvironment(macCatalyst) && !os(visionOS)
import Foundation
import Observation
import WatchConnectivity
import WidgetKit

/// Whether an Apple Watch is paired with this iPhone and has the app: the onboarding's watch card shows only then. Nil
/// until WatchConnectivity has activated (the card simply does not appear); never logged, never sent anywhere.
@MainActor
@Observable
final class WatchPairing {
    static let shared = WatchPairing()
    enum State: Equatable { case unpaired, paired(appInstalled: Bool) }
    var state: State?
}

/// Gives the Apple Watch app what the widgets get (city, method, language, reminders, nearest mosques, today's
/// questions) and how full the companion is, through WatchConnectivity's application context: the latest value
/// waits on the watch until the watch app reads it. It goes only to the paired watch, never to a server.
@MainActor
final class WatchBridge: NSObject, WCSessionDelegate {
    static let shared = WatchBridge()
    private var pending: [String: Any]?
    private var lastSent: (state: Data?, level: Double, marks: [String])?

    func send(_ state: SharedState?, companionLevel: Double, marks: [String]) {
        guard WCSession.isSupported() else { return }
        let data = state.flatMap { try? JSONEncoder().encode($0) }
        let level = (companionLevel * 20).rounded() / 20
        var ctx: [String: Any] = ["level": level, "marks": marks]
        if let data { ctx["state"] = data }
        pending = ctx
        if lastSent.map({ $0.state == data && $0.level == level && $0.marks == marks }) == true { return }
        if WCSession.default.delegate == nil {
            activate()
            return
        }
        flush()
    }

    /// Starts WatchConnectivity (the first send does too; the onboarding starts it early so its watch card can know
    /// whether a watch is paired). The pending context goes out once activation completes.
    func activate() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        if session.delegate == nil {
            session.delegate = self
            session.activate()
        }
    }

    /// The paired watch and its app, for WatchPairing (read on the delegate's queue, applied here).
    nonisolated private static func pairing(_ session: WCSession) -> WatchPairing.State? {
        guard session.activationState == .activated else { return nil }
        return session.isPaired ? .paired(appInstalled: session.isWatchAppInstalled) : .unpaired
    }

    private func setPairing(_ state: WatchPairing.State?) {
        #if DEBUG
        // CI screenshots set the watch with -demoOnboardingWatch (AppModel); a real session does not replace it.
        if UserDefaults.standard.string(forKey: "demoOnboardingWatch") != nil { return }
        #endif
        if let state { WatchPairing.shared.state = state }
    }

    private func flush() {
        let session = WCSession.default
        guard session.activationState == .activated, session.isPaired, session.isWatchAppInstalled, let ctx = pending else { return }
        do {
            try session.updateApplicationContext(ctx)
            lastSent = (ctx["state"] as? Data, ctx["level"] as? Double ?? 0, ctx["marks"] as? [String] ?? [])
        } catch {
            // Tried again with the next change, or when the watch app is installed.
        }
    }

    nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        let pairing = Self.pairing(session)
        Task { @MainActor in
            self.setPairing(pairing)
            self.flush()
        }
    }

    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}

    nonisolated func sessionDidDeactivate(_ session: WCSession) {
        // Switching to another watch: start again for the new one.
        session.activate()
    }

    /// Prayers marked on the watch (and its tasbih rounds): into the companion's log here.
    nonisolated func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any]) {
        guard let marks = userInfo["marks"] as? [String] else { return }
        Task { @MainActor in
            if !CompanionLog.merge(marks).isEmpty {
                WidgetCenter.shared.reloadTimelines(ofKind: "Companion")
                AppModel.shared.shareWithWidgets()
            }
        }
    }

    /// The watch app was installed (or the watch changed): send the latest again.
    nonisolated func sessionWatchStateDidChange(_ session: WCSession) {
        let pairing = Self.pairing(session)
        Task { @MainActor in
            self.setPairing(pairing)
            self.lastSent = nil
            self.flush()
        }
    }
}
#endif
