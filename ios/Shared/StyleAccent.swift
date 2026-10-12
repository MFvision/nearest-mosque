import SwiftUI

/// The accent colour of each style picked in Settings (WidgetLook raw values). One choice colours the app's
/// buttons and highlights, the widgets, the Lock Screen and the Dynamic Island.
enum StyleAccent {
    private static func c(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }

    /// On dark backgrounds: the app in dark mode, the Dynamic Island, dark widgets.
    static func dark(_ style: String) -> Color {
        switch style {
        case "green": return c(0x5FCB93)
        case "night": return c(0x8FB8EA)
        case "teal": return c(0x8CD9CC)
        case "sky": return c(0x9CCDEB)
        default: return c(0xD4A843)  // Gold and cream (the brand gold)
        }
    }

    /// For text and icons on light backgrounds (at least 4.5:1 on the light skies).
    static func light(_ style: String) -> Color {
        switch style {
        case "green": return c(0x1E6B45)
        case "night": return c(0x1F4F80)
        case "teal": return c(0x1F6A62)
        case "sky": return c(0x22618A)
        default: return c(0x6E5414)
        }
    }
}
