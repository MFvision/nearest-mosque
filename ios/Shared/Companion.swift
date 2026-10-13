import Foundation
import NMCore
import Security
import SwiftUI

/// The companion (the lantern or the heart): how full it is follows what you mark, a prayer (in the app, on the
/// widget or on a notification) or a round of 33 on the tasbih widget. It never claims to know
/// whether you prayed; it only reacts to what you mark. The log is kept in the shared keychain group (the app and
/// the widgets read it) and never leaves the phone.
enum CompanionKind: String, CaseIterable, Codable {
    case lantern, heart, none
}

enum CompanionMood { case happy, quiet, sad }

enum CompanionLog {
    private static let account = "companion-log"
    private static var group: String? { Bundle.main.object(forInfoDictionaryKey: "SharedKeychainGroup") as? String }

    private static func query() -> [String: Any] {
        var q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "sa.zood.nearmosque.shared",
                                kSecAttrAccount as String: account]
        // Simulator builds in CI are unsigned, so they have no access group: the item stays the app's own there.
        #if !targetEnvironment(simulator)
        if let group, !group.hasPrefix("$(") { q[kSecAttrAccessGroup as String] = group }
        #endif
        return q
    }

    /// Check-ins as "2026-10-10:dhuhr" (a prayer marked) or "2026-10-10:dhikr" (a tasbih round), newest last.
    static var marks: [String] {
        #if targetEnvironment(simulator)
        // Unsigned simulator builds (CI) have no keychain.
        return UserDefaults.standard.stringArray(forKey: "sim." + account) ?? []
        #endif
        var q = query()
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data,
              let list = try? JSONDecoder().decode([String].self, from: data) else { return [] }
        return list
    }

    private static func save(_ list: [String]) {
        #if targetEnvironment(simulator)
        UserDefaults.standard.set(list, forKey: "sim." + account)
        return
        #endif
        guard let data = try? JSONEncoder().encode(list) else { return }
        let q = query()
        let attrs: [String: Any] = [kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        if SecItemUpdate(q as CFDictionary, attrs as CFDictionary) != errSecSuccess {
            SecItemAdd(q.merging(attrs) { $1 } as CFDictionary, nil)
        }
    }

    /// Notes a check-in for `event` (a PrayerEvent raw value, or "dhikr") on `date`; the same one twice counts once.
    static func checkIn(_ event: String, on date: CivilDate) {
        let key = "\(iso(date)):\(event)"
        var list = marks
        guard !list.contains(key) else { return }
        list.append(key)
        save(Array(list.suffix(60)))
    }

    static func checkedIn(_ event: String, on date: CivilDate) -> Bool { marks.contains("\(iso(date)):\(event)") }

    /// Adds check-ins made on the other device (the iPhone or the Apple Watch); returns the ones that were new here.
    @discardableResult
    static func merge(_ incoming: [String]) -> [String] {
        var list = marks
        let new = incoming.filter { !list.contains($0) }
        guard !new.isEmpty else { return [] }
        list += new
        // "yyyy-MM-dd:event" sorts by day; the newest stay.
        save(Array(list.sorted().suffix(60)))
        return new
    }

    /// How full: each prayer marked today adds to it (five fill it), a tasbih round adds a little; with nothing
    /// today it rests on the last day you checked in, lower each day away. A newcomer starts half full.
    static func level(now: Date = Date(), zone: TimeZone = .current) -> Double {
        #if DEBUG
        // CI screenshots: `-demoCompanionLevel 1` (full), `0.5`, `0.15`.
        if let demo = UserDefaults.standard.object(forKey: "demoCompanionLevel") as? String, let v = Double(demo) { return v }
        #endif
        let list = marks
        guard !list.isEmpty else { return 0.55 }
        let today = CivilDate.of(now, in: zone)
        let todays = list.filter { $0.hasPrefix("\(iso(today)):") }
        let prayers = todays.filter { !$0.hasSuffix(":dhikr") }.count
        let dhikr = todays.count > prayers
        if !todays.isEmpty { return min(1, 0.45 + 0.11 * Double(prayers) + (dhikr ? 0.1 : 0)) }
        for (away, rest) in [(1, 0.42), (2, 0.28)] where list.contains(where: { $0.hasPrefix("\(iso(today.adding(days: -away))):") }) {
            return rest
        }
        return 0.12
    }

    /// The prayer to mark at `now` (Fajr until Dhuhr) and the date it belongs to (Isha after midnight is the
    /// previous day's); nil before the day's first prayer is known.
    static func markable(today: DaySchedule?, current: PrayerEvent?, now: Date) -> (PrayerEvent, CivilDate)? {
        guard let today else { return nil }
        // After midnight, before Fajr: last night's Isha.
        if current == nil, let fajr = today[.fajr], now < fajr { return (.isha, today.date.adding(days: -1)) }
        guard var e = current else { return nil }
        if e == .sunrise { e = .fajr }
        let date = (today[e].map { $0 <= now } ?? false) ? today.date : today.date.adding(days: -1)
        return (e, date)
    }

    static func iso(_ d: CivilDate) -> String { String(format: "%04d-%02d-%02d", d.year, d.month, d.day) }

    static func mood(_ level: Double) -> CompanionMood { level >= 0.75 ? .happy : level >= 0.4 ? .quiet : .sad }
}

/// One frame of the companion. The app steps a `CompanionPhysics` every frame (motion, taps, particles); widgets
/// and Settings draw a still pose from the level alone.
struct CompanionPose {
    /// How full, 0...1 (eased in the app, so changes flow).
    var level: Double
    /// Seconds; drives the flame's flicker and the liquid's waves.
    var time: Double = 0
    /// Radians: the lantern's swing on its ring, or the tilt of the heart's liquid surface.
    var swing: Double = 0
    /// 0...1: how stirred it is (bigger waves, livelier flame).
    var energy: Double = 0
    /// 0...1 after a tap: the flame flares, the liquid splashes.
    var flare: Double = 0
    /// 0...1: the heart's beat (scale).
    var pulse: Double = 0
    /// -1...1: where the eyes look.
    var gaze: Double = 0
    var blink = false
    /// Embers (lantern) or bubbles (heart), in unit coordinates of the figure.
    var particles: [CompanionParticle] = []
}

struct CompanionParticle {
    var x: Double
    var y: Double
    var vx: Double
    var vy: Double
    var age: Double
    var life: Double
    var size: Double
}

/// The companion drawn at one moment. The simple form (`level`, `phase`, `tilt`, `blink`) is for still frames.
struct CompanionFigure: View {
    let kind: CompanionKind
    let pose: CompanionPose

    init(kind: CompanionKind, pose: CompanionPose) {
        self.kind = kind
        self.pose = pose
    }

    init(kind: CompanionKind, level: Double, phase: Double = 0, tilt: Double = 0, blink: Bool = false) {
        self.kind = kind
        self.pose = CompanionPose(level: level, time: phase, swing: tilt, blink: blink)
    }

    var body: some View {
        switch kind {
        case .lantern:
            Canvas { ctx, size in LanternPainter.draw(&ctx, size, pose) }
                .aspectRatio(0.8, contentMode: .fit)
                .accessibilityHidden(true)
        case .heart:
            Canvas { ctx, size in HeartPainter.draw(&ctx, size, pose) }
                .aspectRatio(1, contentMode: .fit)
                .accessibilityHidden(true)
        case .none:
            EmptyView()
        }
    }
}

/// The face shared by both: eyes that look with `gaze` and blink, and a mouth that bends smoothly with the level
/// (a smile when full, flat in between, a small frown when low; never more than that).
enum CompanionEyes {
    static func draw(_ ctx: inout GraphicsContext, center c: CGPoint, scale u: CGFloat, level: Double, gaze: Double, blink: Bool, ink: Color) {
        let r = u * 0.06
        let dx = u * 0.17
        let look = CGFloat(max(-1, min(1, gaze))) * r * 0.55
        let sleepy = level < 0.35
        for side in [-1.0, 1.0] {
            let x = c.x + CGFloat(side) * dx
            if blink || sleepy {
                var p = Path()
                p.move(to: CGPoint(x: x - r * 1.2 + look, y: c.y))
                p.addQuadCurve(to: CGPoint(x: x + r * 1.2 + look, y: c.y), control: CGPoint(x: x + look, y: c.y + r * (sleepy && !blink ? 1.3 : 0.5)))
                ctx.stroke(p, with: .color(ink), style: StrokeStyle(lineWidth: r * 0.75, lineCap: .round))
            } else {
                ctx.fill(Path(ellipseIn: CGRect(x: x - r + look, y: c.y - r * 1.15, width: 2 * r, height: 2.3 * r)), with: .color(ink))
                // A catch-light.
                ctx.fill(Path(ellipseIn: CGRect(x: x - r * 0.35 + look, y: c.y - r * 0.8, width: r * 0.7, height: r * 0.7)), with: .color(.white.opacity(0.85)))
            }
        }
        // Mouth: bend from -0.6 (low) to +1 (full), continuous.
        let bend = CGFloat(max(-0.6, min(1, (level - 0.45) * 2.2)))
        let my = c.y + u * 0.15, mw = u * 0.11
        var m = Path()
        m.move(to: CGPoint(x: c.x - mw, y: my))
        m.addQuadCurve(to: CGPoint(x: c.x + mw, y: my), control: CGPoint(x: c.x, y: my + bend * u * 0.1))
        ctx.stroke(m, with: .color(ink), style: StrokeStyle(lineWidth: r * 0.75, lineCap: .round))
        if level > 0.8 {
            // Rosy cheeks when full.
            for side in [-1.0, 1.0] {
                let x = c.x + CGFloat(side) * dx * 1.45
                ctx.fill(Path(ellipseIn: CGRect(x: x - r * 1.1, y: my - r * 1.4, width: r * 2.2, height: r * 1.3)), with: .color(Color(red: 1, green: 0.5, blue: 0.45).opacity(0.35)))
            }
        }
    }
}

/// Smooth pseudo-noise from layered sines (no randomness, so still frames are stable).
private func wobble(_ t: Double, _ seed: Double) -> Double {
    let a: Double = sin(t * 7.3 + seed) * 0.5
    let b: Double = sin(t * 13.1 + seed * 2.1) * 0.3
    let c: Double = sin(t * 23.7 + seed * 3.7) * 0.2
    return a + b + c
}

/// The lantern: brass ring, dome and frame, warm glass, and a living flame that is the character's face. It
/// hangs from its ring and swings with the phone; the flame stays upright and flickers.
enum LanternPainter {
    static func draw(_ ctx: inout GraphicsContext, _ size: CGSize, _ p: CompanionPose) {
        let w = size.width, h = size.height
        let level = max(0, min(1, p.level))
        let lit = level > 0.15
        let brass = lit ? Color(red: 0.76, green: 0.59, blue: 0.19) : Color(red: 0.5, green: 0.47, blue: 0.42)
        let brassDark = lit ? Color(red: 0.52, green: 0.38, blue: 0.09) : Color(red: 0.36, green: 0.34, blue: 0.3)
        let glow: Double = min(1, level * 0.9 + p.flare * 0.6)

        // Halo (not swung): warmer and wider as it fills.
        let haloR = w * (0.45 + 0.25 * CGFloat(glow))
        let haloCenter = CGPoint(x: w / 2, y: h * 0.58)
        ctx.fill(Path(ellipseIn: CGRect(x: haloCenter.x - haloR, y: haloCenter.y - haloR, width: haloR * 2, height: haloR * 2)),
                 with: .radialGradient(Gradient(colors: [Color(red: 1, green: 0.8, blue: 0.4).opacity(0.55 * glow), .clear]),
                                       center: haloCenter, startRadius: 0, endRadius: haloR))

        var body = ctx
        let pivot = CGPoint(x: w / 2, y: h * 0.05)
        body.translateBy(x: pivot.x, y: pivot.y)
        body.rotate(by: .radians(p.swing))
        body.translateBy(x: -pivot.x, y: -pivot.y)

        // Ring and dome.
        var ring = Path()
        ring.addEllipse(in: CGRect(x: w / 2 - w * 0.07, y: h * 0.01, width: w * 0.14, height: h * 0.09))
        body.stroke(ring, with: .color(brass), lineWidth: w * 0.03)
        var dome = Path()
        dome.move(to: CGPoint(x: w * 0.26, y: h * 0.24))
        dome.addQuadCurve(to: CGPoint(x: w * 0.74, y: h * 0.24), control: CGPoint(x: w / 2, y: h * 0.02))
        dome.closeSubpath()
        body.fill(dome, with: .linearGradient(Gradient(colors: [brass, brassDark]), startPoint: CGPoint(x: w * 0.3, y: h * 0.1), endPoint: CGPoint(x: w * 0.7, y: h * 0.24)))
        body.fill(Path(roundedRect: CGRect(x: w * 0.22, y: h * 0.23, width: w * 0.56, height: h * 0.045), cornerRadius: w * 0.02), with: .color(brassDark))

        // Glass, glowing with the flame.
        let glass = CGRect(x: w * 0.27, y: h * 0.275, width: w * 0.46, height: h * 0.53)
        body.fill(Path(roundedRect: glass, cornerRadius: w * 0.06),
                  with: .linearGradient(Gradient(colors: [Color(red: 1, green: 0.96, blue: 0.82).opacity(0.25 + 0.55 * glow),
                                                          Color(red: 1, green: 0.84, blue: 0.55).opacity(0.2 + 0.6 * glow)]),
                                        startPoint: CGPoint(x: glass.midX, y: glass.minY), endPoint: CGPoint(x: glass.midX, y: glass.maxY)))

        // The flame stays upright while the lantern swings (counter-rotated about its base).
        let base = CGPoint(x: w / 2, y: h * 0.72)
        var flameCtx = body
        flameCtx.translateBy(x: base.x, y: base.y)
        flameCtx.rotate(by: .radians(-p.swing * 0.85 + sin(p.time * 2.1) * 0.04 * (1 + p.energy)))
        flameCtx.translateBy(x: -base.x, y: -base.y)
        drawFlame(&flameCtx, base: base, w: w, h: h, p: p, level: level)

        body.stroke(Path(roundedRect: glass, cornerRadius: w * 0.06), with: .color(brass), lineWidth: w * 0.03)
        // Glass shine.
        body.fill(Path(roundedRect: CGRect(x: glass.minX + w * 0.05, y: glass.minY + h * 0.03, width: w * 0.04, height: glass.height * 0.55), cornerRadius: w * 0.02),
                  with: .color(.white.opacity(0.35)))
        // Base.
        body.fill(Path(roundedRect: CGRect(x: w * 0.22, y: h * 0.795, width: w * 0.56, height: h * 0.06), cornerRadius: w * 0.025), with: .color(brassDark))
        body.fill(Path(roundedRect: CGRect(x: w * 0.3, y: h * 0.85, width: w * 0.4, height: h * 0.05), cornerRadius: w * 0.02), with: .color(brass))

        // Embers rise from the flame.
        for e in p.particles {
            let a = max(0, 1 - e.age / e.life)
            let r = CGFloat(e.size) * w
            ctx.fill(Path(ellipseIn: CGRect(x: CGFloat(e.x) * w - r, y: CGFloat(e.y) * h - r, width: 2 * r, height: 2 * r)),
                     with: .color(Color(red: 1, green: 0.72 + 0.2 * a, blue: 0.3).opacity(a)))
        }
    }

    private static func drawFlame(_ ctx: inout GraphicsContext, base: CGPoint, w: CGFloat, h: CGFloat, p: CompanionPose, level: Double) {
        let t = p.time
        let flick: Double = 1 + (0.10 + 0.12 * p.energy) * wobble(t, 0.3)
        let height = h * CGFloat((0.07 + 0.30 * level) * (1 + 0.45 * p.flare) * flick)
        let width = max(w * 0.1, height * 0.62)
        let lean = CGFloat(wobble(t * 0.7, 1.9)) * width * 0.18 * CGFloat(1 + p.energy)

        func tear(_ scale: CGFloat, _ seed: Double) -> Path {
            let hh = height * scale, ww = width * scale
            let tip = CGPoint(x: base.x + lean * scale + CGFloat(wobble(t * 1.3, seed)) * ww * 0.08, y: base.y - hh)
            var path = Path()
            path.move(to: CGPoint(x: base.x, y: base.y + ww * 0.08))
            path.addCurve(to: tip, control1: CGPoint(x: base.x - ww * 0.62, y: base.y - hh * 0.05), control2: CGPoint(x: base.x - ww * 0.18, y: base.y - hh * 0.62))
            path.addCurve(to: CGPoint(x: base.x, y: base.y + ww * 0.08), control1: CGPoint(x: base.x + ww * 0.18, y: base.y - hh * 0.62), control2: CGPoint(x: base.x + ww * 0.62, y: base.y - hh * 0.05))
            return path
        }
        // Soft glow behind the flame (a gradient: cheap to draw every frame).
        let glowR = max(width, height) * 0.85
        let glowC = CGPoint(x: base.x + lean * 0.5, y: base.y - height * 0.45)
        ctx.fill(Path(ellipseIn: CGRect(x: glowC.x - glowR, y: glowC.y - glowR, width: glowR * 2, height: glowR * 2)),
                 with: .radialGradient(Gradient(colors: [Color(red: 1, green: 0.62, blue: 0.18).opacity(0.5), .clear]),
                                       center: glowC, startRadius: 0, endRadius: glowR))
        ctx.fill(tear(1, 0.5), with: .linearGradient(Gradient(colors: [Color(red: 1, green: 0.45, blue: 0.12), Color(red: 1, green: 0.66, blue: 0.2)]),
                                                     startPoint: CGPoint(x: base.x, y: base.y - height), endPoint: base))
        ctx.fill(tear(0.74, 1.7), with: .linearGradient(Gradient(colors: [Color(red: 1, green: 0.78, blue: 0.3), Color(red: 1, green: 0.9, blue: 0.5)]),
                                                        startPoint: CGPoint(x: base.x, y: base.y - height * 0.74), endPoint: base))
        ctx.fill(tear(0.42, 2.9), with: .color(Color(red: 1, green: 0.98, blue: 0.86)))
        // The face sits on the flame's body, so the flame is the character.
        let faceCenter = CGPoint(x: base.x + lean * 0.3, y: base.y - max(height * 0.32, h * 0.06))
        CompanionEyes.draw(&ctx, center: faceCenter, scale: w * 0.5, level: level, gaze: p.gaze, blink: p.blink,
                           ink: Color(red: 0.35, green: 0.16, blue: 0.04))
    }
}

/// The heart: a glass vessel of light, filled to the level. The liquid keeps level as the phone tilts, sloshes
/// when moved, and bubbles; a tap makes it beat.
enum HeartPainter {
    static func draw(_ ctx: inout GraphicsContext, _ size: CGSize, _ p: CompanionPose) {
        let s = min(size.width, size.height)
        let level = max(0, min(1, p.level))
        let warm = level > 0.15
        let beat = 1 + 0.09 * CGFloat(p.pulse)
        let rect = CGRect(x: (size.width - s * beat) / 2, y: (size.height - s * beat) / 2, width: s * beat, height: s * beat)
        let heart = HeartShape().path(in: rect)
        let red = warm ? Color(red: 0.88, green: 0.3, blue: 0.32) : Color(red: 0.72, green: 0.6, blue: 0.6)
        let deep = warm ? Color(red: 0.66, green: 0.14, blue: 0.2) : Color(red: 0.55, green: 0.45, blue: 0.45)

        // Halo.
        let haloR = s * (0.5 + 0.18 * CGFloat(level))
        let c = CGPoint(x: rect.midX, y: rect.midY)
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - haloR, y: c.y - haloR, width: 2 * haloR, height: 2 * haloR)),
                 with: .radialGradient(Gradient(colors: [Color(red: 1, green: 0.55, blue: 0.55).opacity(0.45 * level + 0.25 * p.flare), .clear]),
                                       center: c, startRadius: 0, endRadius: haloR))
        ctx.fill(heart, with: .color(Color(red: 0.99, green: 0.92, blue: 0.91)))

        var inside = ctx
        inside.clip(to: heart)
        // The heart's inside runs from about 0.12 to 0.9 of its height.
        let top = rect.minY + rect.height * CGFloat(0.9 - 0.78 * level)
        let slope = CGFloat(tan(max(-0.7, min(0.7, p.swing))))
        let amp = rect.height * CGFloat(0.012 + 0.045 * p.energy + 0.05 * p.flare)
        func surface(_ phase: Double, _ k: Double, _ a: CGFloat) -> Path {
            var path = Path()
            path.move(to: CGPoint(x: rect.minX - 4, y: rect.maxY + 4))
            for i in 0...32 {
                let fx = Double(i) / 32
                let x = rect.minX + rect.width * CGFloat(fx)
                let wave: Double = sin(phase + fx * .pi * k) + 0.5 * sin(phase * 1.7 - fx * .pi * k * 1.6)
                path.addLine(to: CGPoint(x: x, y: top + slope * (x - rect.midX) + a * CGFloat(wave)))
            }
            path.addLine(to: CGPoint(x: rect.maxX + 4, y: rect.maxY + 4))
            path.closeSubpath()
            return path
        }
        inside.fill(surface(p.time * 2.6 + 1.3, 2.2, amp * 1.2), with: .color(deep.opacity(0.55)))
        inside.fill(surface(p.time * 3.1, 1.8, amp),
                    with: .linearGradient(Gradient(colors: [red, deep]), startPoint: CGPoint(x: rect.midX, y: top), endPoint: CGPoint(x: rect.midX, y: rect.maxY)))
        // Bubbles rise inside the liquid.
        for b in p.particles {
            let y = rect.minY + CGFloat(b.y) * rect.height
            guard y > top + 2 else { continue }
            let a = max(0, 1 - b.age / b.life)
            let r = CGFloat(b.size) * s
            inside.stroke(Path(ellipseIn: CGRect(x: rect.minX + CGFloat(b.x) * rect.width - r, y: y - r, width: 2 * r, height: 2 * r)),
                          with: .color(.white.opacity(0.7 * a)), lineWidth: max(1, r * 0.35))
        }
        // Gloss and rim.
        var gloss = Path()
        gloss.addArc(center: CGPoint(x: rect.minX + rect.width * 0.3, y: rect.minY + rect.height * 0.32), radius: rect.width * 0.12,
                     startAngle: .degrees(190), endAngle: .degrees(260), clockwise: false)
        ctx.stroke(gloss, with: .color(.white.opacity(0.75)), style: StrokeStyle(lineWidth: s * 0.03, lineCap: .round))
        ctx.stroke(heart, with: .color(warm ? deep.opacity(0.55) : .gray.opacity(0.5)), lineWidth: s * 0.02)

        CompanionEyes.draw(&ctx, center: CGPoint(x: rect.midX, y: rect.minY + rect.height * 0.45), scale: rect.width * 0.62,
                           level: level, gaze: p.gaze, blink: p.blink, ink: Color(red: 0.32, green: 0.05, blue: 0.08))
    }
}

struct HeartShape: Shape {
    func path(in r: CGRect) -> Path {
        let w = r.width, h = r.height
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: r.minX + x * w, y: r.minY + y * h) }
        var path = Path()
        path.move(to: p(0.5, 0.9))
        path.addCurve(to: p(0.06, 0.34), control1: p(0.2, 0.7), control2: p(0.06, 0.52))
        path.addCurve(to: p(0.5, 0.22), control1: p(0.06, 0.08), control2: p(0.4, 0.06))
        path.addCurve(to: p(0.94, 0.34), control1: p(0.6, 0.06), control2: p(0.94, 0.08))
        path.addCurve(to: p(0.5, 0.9), control1: p(0.94, 0.52), control2: p(0.8, 0.7))
        path.closeSubpath()
        return path
    }
}

/// Moves the companion: a damped spring for the lantern's swing or the liquid's surface (driven by the phone's
/// roll and sideways jolts), a tap's flare and heartbeat, eased level changes, random blinks, and particles.
/// Stepped once per frame by the app; nothing is stored.
final class CompanionPhysics {
    private(set) var pose = CompanionPose(level: 0.55)
    private var last: Double?
    private var angularVelocity = 0.0
    private var beatAge = 9.0
    private var nextBlink = 2.0
    private var blinkUntil = 0.0
    private var spawn = 0.0
    private var seed: UInt64 = 0x9E37_79B9_7F4A_7C15

    private func random() -> Double {
        seed = seed &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
        return Double(seed >> 11) / Double(1 << 53)
    }

    /// One frame. `roll` is the phone's roll (radians, 0 upright), `jolt` its sideways acceleration (g).
    /// One frame. `roll` is the phone's roll (radians, 0 upright), `jolt` its sideways acceleration (g). `gentle`
    /// (Reduce Motion) keeps it following the phone, with smaller movement and a few less embers and bubbles.
    func step(time: Double, kind: CompanionKind, level: Double, roll: Double, jolt: Double, gentle: Bool) -> CompanionPose {
        // A dropped frame never slows the motion down: the spring is integrated in small steps over the real time.
        let dt = min(0.1, max(0, time - (last ?? time)))
        last = time
        var p = pose
        p.time = time
        // Level eases toward the target (about a second).
        p.level += (level - p.level) * (1 - exp(-dt * 2.5))
        // Spring toward hanging straight down (lantern) or level with the ground (liquid).
        // The lantern swings at most about 25 degrees (it stays in its card); the liquid follows the phone further.
        let scale = gentle ? 0.6 : 1
        let limit = (kind == .lantern ? 0.45 : 0.8) * scale
        let target = -max(-limit, min(limit, roll * scale))
        let (k, c, push): (Double, Double, Double) = kind == .lantern ? (16, 1.6, 9) : (28, 3.2, 14)
        let damping = gentle ? c * 2 : c
        var remaining = dt
        while remaining > 0 {
            let h = min(remaining, 1.0 / 240)
            remaining -= h
            let accel = -k * (p.swing - target) - damping * angularVelocity - jolt * push * scale
            angularVelocity += accel * h
            p.swing += angularVelocity * h
            if kind == .lantern, abs(p.swing) > 0.55 {
                p.swing = 0.55 * (p.swing < 0 ? -1 : 1)
                angularVelocity *= -0.3
            }
        }
        p.energy += (min(1, abs(angularVelocity) * 0.5 + abs(jolt) * 1.5) - p.energy) * (1 - exp(-dt * 4))
        p.flare *= exp(-dt * 1.4)
        // Heartbeat: lub-dub after a tap, a gentle resting beat when full.
        beatAge += dt
        let lub = exp(-pow((beatAge - 0.08) / 0.07, 2)), dub = 0.7 * exp(-pow((beatAge - 0.36) / 0.08, 2))
        let resting: Double = p.level > 0.75 ? 0.25 * exp(-pow(time.truncatingRemainder(dividingBy: 1.1) / 0.12, 2)) : 0
        p.pulse = max(lub + dub, resting)
        // Eyes follow the tilt, then drift back.
        p.gaze += ((-p.swing * 2.2) - p.gaze) * (1 - exp(-dt * 6))
        // Blinks at random, every 2.5 to 6 seconds.
        if time > nextBlink { blinkUntil = time + 0.14; nextBlink = time + 2.5 + random() * 3.5 }
        p.blink = time < blinkUntil
        // Particles.
        p.particles = p.particles.compactMap { (old: CompanionParticle) -> CompanionParticle? in
            var q = old
            q.age += dt
            q.x += q.vx * dt
            q.y += q.vy * dt
            return q.age < q.life ? q : nil
        }
        let rate = kind == .lantern ? (p.level > 0.2 ? 0.6 + 2.5 * p.level : 0.2) + 14 * p.flare : 0.8 + 6 * p.energy + 10 * p.flare
        spawn += rate * dt * (gentle ? 0.6 : 1)
        while spawn >= 1, p.particles.count < 40 {
            spawn -= 1
            if kind == .lantern {
                let flameTop = 0.72 - (0.07 + 0.3 * p.level) * 0.8
                p.particles.append(CompanionParticle(x: 0.5 + (random() - 0.5) * 0.08, y: flameTop, vx: (random() - 0.5) * 0.08 - p.swing * 0.1,
                                                     vy: -0.18 - random() * 0.2, age: 0, life: 0.8 + random() * 0.8, size: 0.008 + random() * 0.012))
            } else {
                p.particles.append(CompanionParticle(x: 0.3 + random() * 0.4, y: 0.86, vx: (random() - 0.5) * 0.03,
                                                     vy: -0.12 - random() * 0.15, age: 0, life: 1.6 + random() * 1.4, size: 0.01 + random() * 0.015))
            }
        }
        if spawn >= 1 { spawn = 0 }
        pose = p
        return p
    }

    /// A tap: the flame flares and throws embers, or the heart beats and splashes.
    func tap(kind: CompanionKind) {
        pose.flare = 1
        beatAge = 0
        angularVelocity += kind == .lantern ? 1.4 : 2.2
    }
}
