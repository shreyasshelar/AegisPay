// Simulate a rejected fingerprint on Android Emulator (unregistered ID = 99)
const { exec } = require('child_process');
exec('adb -e emu finger touch 99', (err) => {
    if (err) console.error('fingerprint-reject failed:', err.message);
});
