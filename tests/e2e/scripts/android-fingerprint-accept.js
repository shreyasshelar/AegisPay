// Simulate an accepted fingerprint on Android Emulator (enrolled finger ID = 1)
const { exec } = require('child_process');
exec('adb -e emu finger touch 1', (err) => {
    if (err) console.error('fingerprint-accept failed:', err.message);
});
