/**
 * Detox configuration — AegisPay iOS E2E tests
 *
 * Run:
 *   # Build once
 *   detox build --configuration ios.sim.debug
 *
 *   # Run all specs
 *   detox test --configuration ios.sim.debug
 *
 *   # Run a single spec
 *   detox test --configuration ios.sim.debug tests/e2e/detox/specs/happy-path.spec.js
 *
 * CI:
 *   detox test --configuration ios.ci --cleanup --headless --record-logs all
 */

/** @type {Detox.DetoxConfig} */
module.exports = {
    testRunner: {
        args: {
            '$0':    'jest',
            config:  'tests/e2e/detox/jest.config.js',
            _:       ['tests/e2e/detox/specs'],
        },
        jest: { setupTimeout: 120_000 },
    },

    apps: {
        'ios.debug': {
            type:       'ios.app',
            binaryPath: 'apps/ios/DerivedData/AegisPay/Build/Products/Debug-iphonesimulator/AegisPay.app',
            build:      'xcodebuild -project apps/ios/AegisPay.xcodeproj -scheme AegisPay -configuration Debug -sdk iphonesimulator -derivedDataPath apps/ios/DerivedData CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO',
        },
        'ios.release': {
            type:       'ios.app',
            binaryPath: 'apps/ios/DerivedData/AegisPay/Build/Products/Release-iphonesimulator/AegisPay.app',
            build:      'xcodebuild -project apps/ios/AegisPay.xcodeproj -scheme AegisPay -configuration Release -sdk iphonesimulator -derivedDataPath apps/ios/DerivedData CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO',
        },
    },

    devices: {
        'simulator': {
            type:   'ios.simulator',
            device: { type: 'iPhone 15 Pro' },
        },
        'simulator.ci': {
            type:   'ios.simulator',
            device: { type: 'iPhone 15', os: 'iOS 17.5' },
        },
    },

    configurations: {
        'ios.sim.debug': {
            device: 'simulator',
            app:    'ios.debug',
        },
        'ios.sim.release': {
            device: 'simulator',
            app:    'ios.release',
        },
        'ios.ci': {
            device: 'simulator.ci',
            app:    'ios.release',
        },
    },
};
