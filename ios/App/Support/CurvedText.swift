import CoreText
import SwiftUI
import UIKit

/// Text bent along a circle. The string is shaped once by Core Text as a normal line (so Arabic letters
/// keep their joined forms and mixed Arabic/Latin keeps its order), then each glyph of that line is placed
/// on the arc at its own position. `outside`: along the outside of the circle, reading clockwise (down the
/// right side, up the left), tops outward. Otherwise along the inside, reading left to right across the
/// bottom, tops towards the centre. `angle` (degrees clockwise from the top) is the middle of the text.
struct CurvedText: View {
    let text: String
    var style: UIFont.TextStyle = .caption1
    var weight: UIFont.Weight = .medium
    var color: Color
    let center: CGPoint
    /// Baseline radius.
    let radius: CGFloat
    let angle: Double
    var outside = true

    var body: some View {
        Canvas { ctx, _ in
            let line = Self.line(text, style: style, weight: weight, color: UIColor(color))
            let width = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
            let mid = angle * .pi / 180
            ctx.withCGContext { cg in
                for run in (CTLineGetGlyphRuns(line) as? [CTRun]) ?? [] {
                    let n = CTRunGetGlyphCount(run)
                    guard n > 0 else { continue }
                    var glyphs = [CGGlyph](repeating: 0, count: n)
                    var positions = [CGPoint](repeating: .zero, count: n)
                    var advances = [CGSize](repeating: .zero, count: n)
                    CTRunGetGlyphs(run, CFRange(location: 0, length: n), &glyphs)
                    CTRunGetPositions(run, CFRange(location: 0, length: n), &positions)
                    CTRunGetAdvances(run, CFRange(location: 0, length: n), &advances)
                    let attrs = CTRunGetAttributes(run) as NSDictionary
                    // swiftlint:disable:next force_cast
                    let font = attrs[kCTFontAttributeName as String] as! CTFont
                    cg.setFillColor(UIColor(color).cgColor)
                    for i in 0..<n {
                        // Distance along the line to the glyph's middle, from the line's middle.
                        let s = positions[i].x + advances[i].width / 2 - width / 2
                        let phi = outside ? mid + s / radius : mid - s / radius
                        let p = CGPoint(x: center.x + radius * sin(phi), y: center.y - radius * cos(phi))
                        cg.saveGState()
                        cg.translateBy(x: p.x, y: p.y)
                        cg.rotate(by: outside ? phi : phi - .pi)
                        cg.scaleBy(x: 1, y: -1)   // Core Text draws with y up
                        var g = glyphs[i]
                        var at = CGPoint(x: -advances[i].width / 2, y: 0)
                        CTFontDrawGlyphs(font, &g, &at, 1, cg)
                        cg.restoreGState()
                    }
                }
            }
        }
        .environment(\.layoutDirection, .leftToRight)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// Width of the shaped line, for sizing a tap target over the curve.
    static func width(_ text: String, style: UIFont.TextStyle = .caption1, weight: UIFont.Weight = .medium) -> CGFloat {
        CGFloat(CTLineGetTypographicBounds(line(text, style: style, weight: weight, color: .black), nil, nil, nil))
    }

    private static func line(_ text: String, style: UIFont.TextStyle, weight: UIFont.Weight, color: UIColor) -> CTLine {
        // The text-size setting applies: the size comes from the preferred font for the style.
        let size = UIFont.preferredFont(forTextStyle: style).pointSize
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        let s = NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: color])
        return CTLineCreateWithAttributedString(s)
    }
}

/// An invisible tap target laid over a curved label (straight, tangent to the ring at its middle).
struct CurvedTapTarget: View {
    let label: String
    let width: CGFloat
    let center: CGPoint
    let radius: CGFloat
    let angle: Double
    var action: () -> Void

    var body: some View {
        let a = angle * .pi / 180
        Button(action: action) { Color.clear.frame(width: width + 16, height: 34).contentShape(Rectangle()) }
            .buttonStyle(.plain)
            // Tangent to the ring, like the text under it (clockwise: down the right side, up the left).
            .rotationEffect(.degrees(angle))
            .position(x: center.x + radius * sin(a), y: center.y - radius * cos(a))
            .accessibilityLabel(label)
    }
}

/// A label curved along the outside of a ring with a small icon before it (at its visual start), and,
/// with `action`, a tap target over it. Used for the side labels of the Qibla ring and the header arc.
struct CurvedSideLabel: View {
    let text: String
    var icon: Image?
    var style: UIFont.TextStyle = .caption1
    var weight: UIFont.Weight = .medium
    var color: Color
    let center: CGPoint
    let radius: CGFloat
    let angle: Double
    var action: (() -> Void)?

    var body: some View {
        let w = CurvedText.width(text, style: style, weight: weight)
        // The icon sits just before the text's visual start, further back along the arc.
        let iconAngle = angle - Double((w / 2 + 11) / radius) * 180 / .pi
        let a = iconAngle * .pi / 180
        ZStack {
            CurvedText(text: text, style: style, weight: weight, color: color, center: center, radius: radius, angle: angle)
            if let icon {
                icon.renderingMode(.template).resizable().scaledToFit().frame(width: 14, height: 14)
                    .foregroundStyle(color)
                    .rotationEffect(.degrees(iconAngle))
                    .position(x: center.x + (radius + 5) * sin(a), y: center.y - (radius + 5) * cos(a))
                    .accessibilityHidden(true)
            }
            if let action {
                CurvedTapTarget(label: text, width: w + 22, center: center, radius: radius + 5, angle: angle - Double(11 / radius) * 90 / .pi, action: action)
            } else {
                Color.clear.accessibilityElement().accessibilityLabel(text)
                    .frame(width: 1, height: 1)
                    .position(x: center.x + radius * sin(angle * .pi / 180), y: center.y - radius * cos(angle * .pi / 180))
            }
        }
    }
}
