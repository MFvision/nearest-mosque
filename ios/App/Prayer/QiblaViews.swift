import NMCore
import SwiftUI

func qiblaGuidance(_ compass: CompassState, bearing: Double, l10n: Localization) -> String {
    switch compass {
    case .bearingOnly:
        return l10n.t("qibla_no_sensor", Format.degrees(bearing, locale: l10n.locale))
    case let .live(heading, _, calibrate):
        let rel = Angles.relativeToQibla(qiblaBearingTrue: bearing, headingTrue: heading)
        if calibrate { return l10n.t("qibla_calibrate") }
        if abs(rel) <= 5 { return l10n.t("qibla_facing_short") }
        // Plain words, no angles: "a little" within 25°.
        if abs(rel) < 25 { return l10n.t(rel > 0 ? "qibla_go_right_little" : "qibla_go_left_little") }
        return l10n.t(rel > 0 ? "qibla_go_right" : "qibla_go_left")
    }
}

