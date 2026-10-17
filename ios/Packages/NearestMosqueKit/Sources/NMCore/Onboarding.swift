import Foundation

// The "First Light" onboarding: its steps, where Back and Skip go, its timings, the weather preview samples, the
// coming Friday's times and the "Also in Near Mosque" list. The same on every platform (mirrors core/Onboarding.kt,
// which Android and the desktop use), so the order and the rules are tested once.

public enum OnboardingStep: String, CaseIterable, Sendable {
    /// The raw value is the step's name in tests and debug options; "where" for `place`.
    case promise
    case place = "where"
    case asr, adhan, friday, weather, look, building, day, extras, more, privacy, done

    public var key: String { rawValue }
}

/// The steps for one platform: Windows and Linux have no app style or appearance setting, so no `look`.
public struct OnboardingFlow: Equatable, Sendable {
    public let hasLook: Bool
    public let steps: [OnboardingStep]

    public init(hasLook: Bool = true) {
        self.hasLook = hasLook
        steps = OnboardingStep.allCases.filter { hasLook || $0 != .look }
    }

    /// The position in `steps`, or nil for a step this flow does not have.
    public func index(_ step: OnboardingStep) -> Int? { steps.firstIndex(of: step) }

    public func next(_ step: OnboardingStep) -> OnboardingStep? {
        guard let i = index(step), i + 1 < steps.count else { return nil }
        return steps[i + 1]
    }

    /// The step Back returns to; none on the promise, while building and when done. Back from the day skips building.
    public func back(_ step: OnboardingStep) -> OnboardingStep? {
        guard step != .promise, step != .building, step != .done, let i = index(step), i > 0 else { return nil }
        return steps[..<i].last { $0 != .building }
    }

    /// Where Skip goes, changing nothing on the steps it passes: from the questions to building, from the showcase to
    /// privacy (so the notification ask still comes). Nil where there is no Skip.
    public func skip(_ step: OnboardingStep) -> OnboardingStep? {
        switch step {
        case .asr, .adhan, .friday, .weather: return .building
        case .look: return hasLook ? .building : nil
        case .extras, .more: return .privacy
        default: return nil
        }
    }

    /// The "YOUR DAY" header shows from the place through privacy.
    public static func showsHeader(_ step: OnboardingStep) -> Bool { between(step, .place, .privacy) }

    /// The questions that shape the day: the place through the look.
    public static func isQuestion(_ step: OnboardingStep) -> Bool { between(step, .place, .look) }

    private static func between(_ s: OnboardingStep, _ a: OnboardingStep, _ b: OnboardingStep) -> Bool {
        let all = OnboardingStep.allCases
        guard let i = all.firstIndex(of: s), let lo = all.firstIndex(of: a), let hi = all.firstIndex(of: b) else { return false }
        return i >= lo && i <= hi
    }
}

/// Timings in milliseconds (shared/design/tokens.json "motion" holds the springs).
public enum OnboardingTiming {
    /// From an answer to the next step.
    public static let advance = 650
    public static let building = 1700
    /// Between the building lines ticking to a check.
    public static let buildingLine = 450
    /// The Allow button's spinner shows at least this long, then the check holds.
    public static let allowSpinner = 500
    public static let allowHold = 450
    /// The weather preview changes condition this often, crossfading.
    public static let previewCycle = 2600
    public static let previewFade = 1100
    public static let leave = 200
    /// Between the children of a step as it enters.
    public static let stagger = 40
}

/// Sample weather for the onboarding preview, shown only on the weather step and labelled "Preview": never sent for,
/// never stored, never shown as real.
public enum WeatherPreview {
    public static let order: [WeatherCondition] = [.clear, .partlyCloudy, .cloudy, .rain, .thunderstorm, .fog, .snow]

    /// The MET symbol code drawn for each condition.
    public static func code(_ c: WeatherCondition) -> String {
        switch c {
        case .clear: return "clearsky"
        case .partlyCloudy: return "partlycloudy"
        case .cloudy: return "cloudy"
        case .rain: return "rain"
        case .thunderstorm: return "rainandthunder"
        case .fog: return "fog"
        case .snow: return "snow"
        }
    }

    /// The sample temperature (°C) and wind (km/h).
    public static func sample(_ c: WeatherCondition) -> (temperatureC: Double, windKmh: Double) {
        switch c {
        case .clear: return (31, 9)
        case .partlyCloudy: return (28, 12)
        case .cloudy: return (22, 15)
        case .rain: return (17, 22)
        case .thunderstorm: return (19, 34)
        case .fog: return (12, 4)
        case .snow: return (-2, 11)
        }
    }

    public static func now(_ c: WeatherCondition, at: Date) -> WeatherNow {
        let s = sample(c)
        return WeatherNow(symbol: Weather.symbol(code(c)) ?? WeatherSymbol(condition: c, intensity: 1),
                          temperatureC: s.temperatureC, windMs: s.windKmh / 3.6, at: at)
    }
}

/// The coming Friday's reminder (AlertSettings.fridayLeadMinutes before Dhuhr) and its Dhuhr.
public struct FridayTimes: Equatable, Sendable {
    public let reminder: Date
    public let dhuhr: Date

    public init(reminder: Date, dhuhr: Date) {
        self.reminder = reminder
        self.dhuhr = dhuhr
    }
}

public enum FridayPreview {
    /// Today when today is Friday and the reminder is still ahead, otherwise the next Friday (up to 8 days ahead). Nil
    /// when Dhuhr cannot be calculated that day.
    public static func next(location: LatLng, zone: TimeZone, now: Date, settings: PrayerSettings,
                            calculator: PrayerCalculator = PrayerCalculator()) -> FridayTimes? {
        let today = CivilDate.of(now, in: zone)
        for i in 0...8 {
            let date = today.adding(days: i)
            guard date.isFriday else { continue }
            guard let dhuhr = calculator.schedule(location, date: date, zone: zone, settings: settings)[.dhuhr] else { return nil }
            let reminder = dhuhr.addingTimeInterval(-Double(AlertSettings.fridayLeadMinutes) * 60)
            if reminder > now { return FridayTimes(reminder: reminder, dhuhr: dhuhr) }
        }
        return nil
    }
}

/// What "Also in Near Mosque" can list, in its order. Each platform says which it has.
public enum OnboardingFeature: String, CaseIterable, Sendable {
    case iqama, beforePrayer, fajrAlarm, quiet, qiblaCompass, qiblaNorth, mosques, library, classics, hijri, tasbih, startAtSignIn

    /// The rest: the `available` features in this order, without those an earlier step already showed, and without
    /// the north bearing beside a compass or the classical books beside the library that holds them.
    public static func more(available: Set<OnboardingFeature>, shownEarlier: Set<OnboardingFeature> = []) -> [OnboardingFeature] {
        allCases.filter { f in
            available.contains(f) && !shownEarlier.contains(f)
                && !(f == .qiblaNorth && available.contains(.qiblaCompass))
                && !(f == .classics && available.contains(.library))
        }
    }
}
