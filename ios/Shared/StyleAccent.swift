import SwiftUI

/// The accent colour of each style picked in Settings (WidgetLook raw values). One choice colours the app's
/// buttons and highlights, the widgets, the Lock Screen and the Dynamic Island.
enum StyleAccent {
    private static func c(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }

    /// On dark backgrounds: the app in dark mode, the Dynamic Island, dark widgets.
    static func dark(_ style: String) -> Color {
        if let t = SolidTheme.of(style) { return t.dark.accent }
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
        if let t = SolidTheme.of(style) { return t.light.accent }
        switch style {
        case "green": return c(0x1E6B45)
        case "night": return c(0x1F4F80)
        case "teal": return c(0x1F6A62)
        case "sky": return c(0x22618A)
        default: return c(0x6E5414)
        }
    }
}

/// The themes with their own solid colours instead of the sky (shared/design/themes.json, contrast checked
/// against WCAG AA): Night garden, Desert lantern (with its light pair) and Charcoal crimson. Raw values are
/// WidgetLook raw values.
struct SolidTheme {
    /// The colours of one appearance.
    struct Tokens {
        let background: Color
        let surface: Color
        let surfaceRaised: Color
        let border: Color
        let textPrimary: Color
        let textSecondary: Color
        /// Marks the current prayer, links and highlights.
        let accent: Color
        /// Only the countdown to the iqama (the other countdowns keep the text colour).
        let signal: Color
        /// Prominent buttons: a fill only, never text (crimson and burnt amber fail as text).
        let buttonFill: Color
        let onButton: Color
    }

    let dark: Tokens
    let light: Tokens
    /// Dark only (Night garden, Charcoal crimson): the app stays dark whatever the phone's appearance.
    let alwaysDark: Bool

    private static func c(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }

    static let nightGarden = Tokens(background: c(0x06110C), surface: c(0x0E1D16), surfaceRaised: c(0x14271E), border: c(0x1F3A2C),
                                    textPrimary: c(0xE8F3EC), textSecondary: c(0x8FA89B), accent: c(0x9FD8BE), signal: c(0xE5483A),
                                    buttonFill: c(0xA31C1B), onButton: .white)
    static let charcoalCrimson = Tokens(background: c(0x171717), surface: c(0x222222), surfaceRaised: c(0x06110C), border: c(0x333333),
                                        textPrimary: c(0xF2F2F2), textSecondary: c(0x8C8C8C), accent: c(0x9FD8BE), signal: c(0xE5483A),
                                        buttonFill: c(0xA31C1B), onButton: .white)
    static let desertLantern = Tokens(background: c(0x3A2D20), surface: c(0x4A3928), surfaceRaised: c(0x5B4632), border: c(0x6B5440),
                                      textPrimary: c(0xF7EBD3), textSecondary: c(0xC9B596), accent: c(0xF4D9A0), signal: c(0xF4D9A0),
                                      buttonFill: c(0xC8792C), onButton: c(0x1E140B))
    static let desertLanternDay = Tokens(background: c(0xFBF5E9), surface: c(0xF3E7CF), surfaceRaised: c(0xF4D9A0), border: c(0xE6D6B8),
                                         textPrimary: c(0x3A2D20), textSecondary: c(0x7A6650), accent: c(0x9A5A1C), signal: c(0x9A5A1C),
                                         buttonFill: c(0x5B4632), onButton: c(0xF7EBD3))

    /// The theme for a style, nil for the sky styles.
    static func of(_ style: String) -> SolidTheme? {
        switch style {
        case "nightGarden": return SolidTheme(dark: nightGarden, light: nightGarden, alwaysDark: true)
        case "charcoalCrimson": return SolidTheme(dark: charcoalCrimson, light: charcoalCrimson, alwaysDark: true)
        case "desertLantern": return SolidTheme(dark: desertLantern, light: desertLanternDay, alwaysDark: false)
        default: return nil
        }
    }

    func tokens(dark isDark: Bool) -> Tokens { isDark || alwaysDark ? dark : light }
}
