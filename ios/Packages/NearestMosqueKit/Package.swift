// swift-tools-version:6.0
import PackageDescription

// Domain (NMCore) and storage (NMData) for the iOS app. Both build and test on Linux and macOS,
// so the shared fixtures run in CI without Xcode; the SwiftUI apps live in ios/App and ios/TV (Apple TV).
let package = Package(
    name: "NearestMosqueKit",
    platforms: [.iOS(.v18), .macOS(.v14), .tvOS(.v18)],
    products: [
        .library(name: "NMCore", targets: ["NMCore"]),
        .library(name: "NMData", targets: ["NMData"]),
    ],
    dependencies: [
        .package(url: "https://github.com/batoulapps/adhan-swift", exact: "1.5.0"),
        .package(url: "https://github.com/groue/GRDB.swift", exact: "7.11.1"),
    ],
    targets: [
        .target(name: "NMCore", dependencies: [.product(name: "Adhan", package: "adhan-swift")]),
        .target(name: "NMData", dependencies: ["NMCore", .product(name: "GRDB", package: "GRDB.swift")]),
        .testTarget(name: "NMCoreTests", dependencies: ["NMCore"]),
        .testTarget(name: "NMDataTests", dependencies: ["NMData"]),
    ],
    swiftLanguageModes: [.v5]
)
