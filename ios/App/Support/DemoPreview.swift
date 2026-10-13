#if DEBUG
import Foundation

/// CI App Store previews (videos): `-demoPreview header` or `-demoPreview ask` plays a short scripted tour that
/// tools/store/record.py records from the simulator. Debug builds only.
enum DemoPreview {
    /// Set when the app starts (NearMosqueApp.init).
    static let launched = Date()
    static var kind: String? { UserDefaults.standard.string(forKey: "demoPreview") }
    /// The mosques tour runs once per launch.
    @MainActor static var mosquesTourStarted = false

    /// The tour starts at least this many seconds after launch, so the screen has settled.
    static func waitForStart(after seconds: Double = 9) async {
        let left = launched.addingTimeInterval(seconds).timeIntervalSinceNow
        if left > 0 { try? await Task.sleep(for: .seconds(left)) }
        markStart()
    }

    /// Tells record.py when the tour began (it trims the video from there): the time, in Caches.
    private static func markStart() {
        guard let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return }
        try? String(Date().timeIntervalSince1970).write(to: dir.appendingPathComponent("demoPreviewStart.txt"), atomically: true, encoding: .utf8)
    }

    static func pause(_ seconds: Double) async {
        try? await Task.sleep(for: .seconds(seconds))
    }
}
#endif
