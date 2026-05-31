// Maestro inline script — enable airplane mode via adb
// Called from maestro/offline-reconnect.yaml
const { exec } = require('child_process');
exec('adb shell cmd connectivity airplane-mode enable', (err) => {
    if (err) console.error('airplane-on failed:', err.message);
});
