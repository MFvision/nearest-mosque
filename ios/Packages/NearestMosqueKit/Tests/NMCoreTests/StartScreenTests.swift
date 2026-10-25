import XCTest
@testable import NMCore

final class StartScreenTests: XCTestCase {
    // Where I left off: Nearest Mosque only when it was the last page; anything else is Prayer & Qibla.
    func testLastPlace() {
        XCTAssertEqual(StartScreen.lastPlace.place(last: "mosques"), .mosques)
        XCTAssertEqual(StartScreen.lastPlace.place(last: "prayer"), .prayer)
        XCTAssertEqual(StartScreen.lastPlace.place(last: nil), .prayer)
        // A Qibla or Ask left open never opens by itself.
        XCTAssertEqual(StartScreen.lastPlace.place(last: "ask"), .prayer)
        XCTAssertEqual(StartScreen.lastPlace.place(last: "qibla"), .prayer)
        XCTAssertEqual(StartScreen.lastPlace.place(last: "garbage"), .prayer)
    }

    // A fixed choice ignores the last page.
    func testFixedChoices() {
        for s in StartScreen.allCases where s != .lastPlace {
            XCTAssertEqual(s.place(last: "mosques").rawValue, s.rawValue)
        }
    }

    // Every icon but the app's own has its own icon set, each name once; an unknown name is the app's own icon.
    func testIconNames() {
        XCTAssertNil(AppIconChoice.gold.iconName)
        let names = AppIconChoice.allCases.compactMap(\.iconName)
        XCTAssertEqual(names.count, AppIconChoice.allCases.count - 1)
        XCTAssertEqual(Set(names).count, names.count)
        for c in AppIconChoice.allCases { XCTAssertEqual(AppIconChoice(iconName: c.iconName), c) }
        XCTAssertEqual(AppIconChoice(iconName: "AppIcon-Removed"), .gold)
    }

    // The icon sets the code names are the ones project.yml asks Xcode to build, and each has its picture.
    func testIconSetsExist() throws {
        let ios = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("../../../..").standardized
        let yml = try String(contentsOf: ios.appendingPathComponent("project.yml"), encoding: .utf8)
        let assets = ios.appendingPathComponent("App/Resources/Assets.xcassets")
        let fm = FileManager.default
        for c in AppIconChoice.allCases {
            if let n = c.iconName {
                XCTAssertTrue(yml.contains(n), "\(n) missing from ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES")
                XCTAssertTrue(fm.fileExists(atPath: assets.appendingPathComponent("\(n).appiconset/\(n)-1024.png").path), n)
            }
            XCTAssertTrue(fm.fileExists(atPath: assets.appendingPathComponent("\(c.preview).imageset/\(c.preview).png").path), c.preview)
        }
    }
}
