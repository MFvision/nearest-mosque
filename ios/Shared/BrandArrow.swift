import SwiftUI

/// The logo's arrow as a shape (shared/brand/emblem/options.py ARROW, tip up, centred on its middle):
/// `body` is the whole arrow, `facet` its lit right half.
struct BrandArrow: Shape {
    var facet = false
    func path(in r: CGRect) -> Path {
        // Arrow space: x -44…44, y -60…52 (112 tall); centre the box on (0, -4).
        let k = min(r.width / 88, r.height / 112)
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: r.midX + x * k, y: r.midY + (y + 4) * k) }
        var path = Path()
        path.move(to: p(0, -60))
        path.addLine(to: p(44, 52))
        path.addLine(to: p(0, 30))
        if !facet { path.addLine(to: p(-44, 52)) }
        path.closeSubpath()
        return path
    }
}
