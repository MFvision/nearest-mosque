import Foundation
import HealthKit
import NMCore

/// Optional (Settings → Apple Health, off by default): each prayer you mark on the watch adds mindful minutes to Apple Health,
/// as many as you choose. Only mindful sessions are written; nothing is read. A sync identifier per prayer
/// ("2026-10-13:dhuhr") keeps one entry per prayer however many devices write it.
enum WatchHealth {
    private static let store = HKHealthStore()
    private static let type = HKCategoryType(.mindfulSession)

    static var available: Bool { HKHealthStore.isHealthDataAvailable() }

    /// Asks for permission to write mindful minutes; false when refused or unavailable.
    static func authorize() async -> Bool {
        guard available else { return false }
        do {
            try await store.requestAuthorization(toShare: [type], read: [])
        } catch {
            return false
        }
        return store.authorizationStatus(for: type) == .sharingAuthorized
    }

    /// One mindful session for a prayer marked on the watch, from `start`, `minutes` long.
    static func record(_ mark: String, start: Date, minutes: Int) async {
        guard available, store.authorizationStatus(for: type) == .sharingAuthorized else { return }
        let sample = HKCategorySample(type: type, value: HKCategoryValue.notApplicable.rawValue, start: start,
                                      end: start.addingTimeInterval(Double(max(1, minutes)) * 60),
                                      metadata: [HKMetadataKeySyncIdentifier: "nearmosque.prayer.\(mark)", HKMetadataKeySyncVersion: 1])
        try? await store.save(sample)
    }
}
