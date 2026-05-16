const { exec } = require('child_process');
exec('adb shell cmd connectivity airplane-mode disable', (err) => {
    if (err) console.error('airplane-off failed:', err.message);
});
