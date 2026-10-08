import SwiftUI

// The app's model and language, read by every screen. They fall back to the app's single instances, so a
// view shown where the environment was not passed on (on the Mac, a presented window can be) still finds
// them instead of stopping the app ("No Observable object of type AppModel found").

private struct AppModelKey: EnvironmentKey {
    static var defaultValue: AppModel { MainActor.assumeIsolated { AppModel.shared } }
}

private struct LocalizationKey: EnvironmentKey {
    static var defaultValue: Localization { MainActor.assumeIsolated { AppModel.shared.l10n } }
}

extension EnvironmentValues {
    var appModel: AppModel {
        get { self[AppModelKey.self] }
        set { self[AppModelKey.self] = newValue }
    }

    var l10n: Localization {
        get { self[LocalizationKey.self] }
        set { self[LocalizationKey.self] = newValue }
    }
}
