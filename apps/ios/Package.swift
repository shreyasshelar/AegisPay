// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AegisPay",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "AegisPay", targets: ["AegisPay"]),
    ],
    dependencies: [
        // OAuth2 / PKCE
        .package(
            url: "https://github.com/openid/AppAuth-iOS.git",
            from: "1.7.5"
        ),
        // Keychain wrapper
        .package(
            url: "https://github.com/kishikawakatsumi/KeychainAccess.git",
            from: "4.2.2"
        ),
    ],
    targets: [
        .target(
            name: "AegisPay",
            dependencies: [
                .product(name: "AppAuth", package: "AppAuth-iOS"),
                "KeychainAccess",
            ],
            path: "AegisPay",
            swiftSettings: [
                .enableExperimentalFeature("StrictConcurrency"),
            ]
        ),
        .testTarget(
            name: "AegisPayTests",
            dependencies: ["AegisPay"],
            path: "AegisPayTests"
        ),
    ]
)
