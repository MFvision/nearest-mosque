import Foundation

/// How the iPhone moves between the app's places (Settings › Menu and start): the tab bar along the bottom
/// (Prayer & Qibla, Nearest Mosque), or the classic single page, Prayer & Qibla, with the other places one tap
/// away in a row of buttons at the bottom. The owner asked for both, as in his other app.
public enum MenuStyle: String, CaseIterable, Sendable {
    case tabs
    case classic

    /// The strings key of its name.
    public var titleKey: String { "menu_" + rawValue }
}

/// One of the app's places, where the app can start.
public enum StartPlace: String, CaseIterable, Sendable {
    case prayer
    case qibla
    case mosques
    case ask
}

/// Where the app opens (Settings › Open with). Read once, when the app starts.
public enum StartScreen: String, CaseIterable, Sendable {
    /// The page the app was on last time (Prayer & Qibla or Nearest Mosque).
    case lastPlace
    case prayer
    case qibla
    case mosques
    case ask

    public var titleKey: String {
        switch self {
        case .lastPlace: "start_last_place"
        case .prayer: "tab_prayer"
        case .qibla: "widget_kind_qibla"
        case .mosques: "tab_mosques"
        case .ask: "tab_ask"
        }
    }

    public var symbol: String {
        switch self {
        case .lastPlace: "clock.arrow.circlepath"
        case .prayer: "location.north.circle"
        case .qibla: "safari"
        case .mosques: "building.columns"
        case .ask: "sparkles"
        }
    }

    /// Where the app starts. `last` is the saved raw value of the last page (anything unknown is Prayer & Qibla);
    /// only the two pages count as a last place, so a closed Qibla or Ask never opens again by itself.
    public func place(last: String?) -> StartPlace {
        switch self {
        case .lastPlace: last == StartPlace.mosques.rawValue ? .mosques : .prayer
        case .prayer: .prayer
        case .qibla: .qibla
        case .mosques: .mosques
        case .ask: .ask
        }
    }
}

/// The app icons Settings offers: the app's mark in the colours of the app styles (shared/brand/icons/make_icons.py
/// draws every one). The names are the asset catalog's alternate icon sets (ios/project.yml lists them).
public enum AppIconChoice: String, CaseIterable, Sendable {
    case gold
    case night
    case green
    case teal
    case sky
    case black

    /// The alternate icon's name; nil is the app's own icon.
    public var iconName: String? {
        switch self {
        case .gold: nil
        case .night: "AppIcon-Night"
        case .green: "AppIcon-Green"
        case .teal: "AppIcon-Teal"
        case .sky: "AppIcon-Sky"
        case .black: "AppIcon-Black"
        }
    }

    /// The small picture of it in the picker (an image set in the asset catalog).
    public var preview: String { "AppIconPreview-" + rawValue }

    /// The strings key of its name.
    public var titleKey: String { "icon_" + rawValue }

    /// The choice for the icon iOS reports (`alternateIconName`); an unknown name is the app's own icon.
    public init(iconName: String?) {
        self = Self.allCases.first { $0.iconName == iconName } ?? .gold
    }
}
