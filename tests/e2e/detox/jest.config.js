/** @type {import('@jest/types').Config.InitialOptions} */
module.exports = {
    rootDir:             '../../../',
    testMatch:           ['<rootDir>/tests/e2e/detox/specs/**/*.spec.{js,ts}'],
    testTimeout:         120_000,
    maxWorkers:          1,          // Simulator tests must run serially
    globalSetup:         'detox/runners/jest/globalSetup',
    globalTeardown:      'detox/runners/jest/globalTeardown',
    reporters:           ['detox/runners/jest/reporter'],
    testEnvironment:     'detox/runners/jest/testEnvironment',
    verbose:             true,
};
