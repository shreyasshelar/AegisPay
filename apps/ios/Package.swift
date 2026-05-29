// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AegisPay",
    platforms: [.iOS(.v17)],
    products: [],
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
        // Stripe iOS SDK — PaymentSheet for wallet top-up
        .package(
            url: "https://github.com/stripe/stripe-ios-spm.git",
            from: "23.31.0"
        ),
        // Firebase — Phone Auth OTP verification only (Keycloak handles primary auth)
        // IMPORTANT: add GoogleService-Info.plist from Firebase Console before building.
        .package(
            url: "https://github.com/firebase/firebase-ios-sdk.git",
            from: "10.0.0"
        ),
    ],
    targets: [
        .target(
            name: "AegisPay",
            dependencies: [
                .product(name: "AppAuth", package: "AppAuth-iOS"),
                "KeychainAccess",
                .product(name: "StripePaymentSheet", package: "stripe-ios-spm"),
                .product(name: "FirebaseAuth", package: "firebase-ios-sdk"),
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
