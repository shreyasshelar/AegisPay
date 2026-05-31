@echo off
setlocal EnableDelayedExpansion
title AegisPay — Android Build ^& Run (Windows)

REM =========================================================
REM AegisPay — Android Build & Run (Windows)
REM
REM Builds the Android debug APK and installs it on an emulator
REM or physical device. Assumes the backend is already running
REM (via start-aegispay.bat or Docker + individual services).
REM
REM Usage:
REM   start-android.bat                   — emulator  (10.0.2.2 → localhost)
REM   start-android.bat <device-ip>       — physical device on your LAN
REM
REM Prerequisites:
REM   - Android Studio with SDK installed
REM   - ANDROID_HOME set  (e.g. %LOCALAPPDATA%\Android\Sdk)
REM   - At least one AVD in Android Studio > Device Manager
REM   - adb.exe on PATH or reachable via %ANDROID_HOME%\platform-tools
REM   - JDK 17 or 21 on PATH  (Gradle uses it — not the Java 21 Maven check)
REM =========================================================


REM Load local secrets (gitignored) — copy .secrets.bat.template to .secrets.bat and fill in your values
IF EXIST "%~dp0.secrets.bat" call "%~dp0.secrets.bat"

REM Defaults to empty — set via .secrets.bat or Windows system environment variables
IF NOT DEFINED GOOGLE_CLIENT_ID     set GOOGLE_CLIENT_ID=
IF NOT DEFINED GOOGLE_CLIENT_SECRET set GOOGLE_CLIENT_SECRET=
IF NOT DEFINED MICROSOFT_CLIENT_ID  set MICROSOFT_CLIENT_ID=
IF NOT DEFINED MICROSOFT_CLIENT_SECRET set MICROSOFT_CLIENT_SECRET=

echo.
echo =========================================================
echo   AegisPay — Android Build ^& Run  (Windows)
echo =========================================================

REM =========================================================
REM ARGUMENTS
REM   %1 = optional LAN IP of this machine (physical device)
REM =========================================================

REM DEVICE_IP is the address the Android device uses to reach this machine.
REM   Emulator : 10.0.2.2  (fixed alias for host loopback)
REM   Physical : host machine's LAN IP — use DEV_HOST from .secrets.bat or pass as %1
set PHYSICAL_DEVICE=0
set DEVICE_IP=10.0.2.2
IF NOT "%1"=="" (
    REM Explicit IP passed on command line takes highest priority
    set PHYSICAL_DEVICE=1
    set DEVICE_IP=%1
) ELSE IF NOT "!DEV_HOST!"=="" IF NOT "!DEV_HOST!"=="localhost" (
    REM DEV_HOST set to a LAN IP or domain in .secrets.bat → physical device mode
    set PHYSICAL_DEVICE=1
    set DEVICE_IP=!DEV_HOST!
)

set API_BASE_URL=http://!DEVICE_IP!:8080
set KEYCLOAK_URL=http://!DEVICE_IP!:8180/realms/aegispay
set WS_BASE_URL=ws://!DEVICE_IP!:8080

REM =========================================================
REM CREDENTIALS & CONFIG
REM =========================================================

REM ── Stripe (test mode) ────────────────────────────────────
IF "%STRIPE_PUBLISHABLE_KEY%"=="" set STRIPE_PUBLISHABLE_KEY=pk_test_51TTkk2CyjRW67i1Dr44Sfw2W1FzJ2taFP757phrJYxYlwFTThQEEdL2eUnugV6w50ySvXjHUUXO35yHd6y3HAgiP007Aa0VZdL

REM ── Google OAuth (Keycloak social login) ──────────────────
REM Set GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET in your environment.
REM   https://console.cloud.google.com/apis/credentials
IF "%GOOGLE_CLIENT_ID%"==""     echo WARNING: GOOGLE_CLIENT_ID not set — Google login disabled
IF "%GOOGLE_CLIENT_SECRET%"==""  echo WARNING: GOOGLE_CLIENT_SECRET not set — Google login disabled

REM ── Microsoft OAuth (Keycloak social login) ───────────────
REM Set MICROSOFT_CLIENT_ID / MICROSOFT_CLIENT_SECRET in your environment.
REM   https://portal.azure.com/ -> App Registrations -> Certificates and Secrets
IF "%MICROSOFT_CLIENT_ID%"==""    echo WARNING: MICROSOFT_CLIENT_ID not set — Microsoft login disabled
IF "%MICROSOFT_CLIENT_SECRET%"=="" echo WARNING: MICROSOFT_CLIENT_SECRET not set — Microsoft login disabled
IF "%MICROSOFT_TENANT_ID%"==""    set MICROSOFT_TENANT_ID=common

REM =========================================================
REM VERIFY ANDROID_HOME
REM =========================================================

IF "!ANDROID_HOME!"=="" (
    echo.
    echo ERROR: ANDROID_HOME is not set.
    echo        Install Android Studio, then add to system environment variables:
    echo          ANDROID_HOME = %LOCALAPPDATA%\Android\Sdk
    echo        Also add to PATH:
    echo          %LOCALAPPDATA%\Android\Sdk\platform-tools
    echo          %LOCALAPPDATA%\Android\Sdk\emulator
    echo.
    pause
    exit /b 1
)
echo Android SDK: !ANDROID_HOME!

REM ── Locate adb ────────────────────────────────────────────
set ADB_EXE="%ANDROID_HOME%\platform-tools\adb.exe"
IF NOT EXIST %ADB_EXE% (
    where adb >nul 2>&1
    IF %ERRORLEVEL% EQU 0 (
        set ADB_EXE=adb
    ) ELSE (
        echo ERROR: adb.exe not found. Add %ANDROID_HOME%\platform-tools to PATH.
        pause
        exit /b 1
    )
)

REM =========================================================
REM INJECT GRADLE PROPERTIES
REM =========================================================

set GRADLE_PROPS=apps\android\gradle.properties

REM Ensure file exists
IF NOT EXIST "%GRADLE_PROPS%" (
    echo # AegisPay Android local properties > "%GRADLE_PROPS%"
)

REM Update or append each key using PowerShell (handles both cases cleanly)
for %%K in (
    "STRIPE_PUBLISHABLE_KEY=!STRIPE_PUBLISHABLE_KEY!"
    "GOOGLE_CLIENT_ID=!GOOGLE_CLIENT_ID!"
    "MICROSOFT_CLIENT_ID=!MICROSOFT_CLIENT_ID!"
) do (
    for /f "tokens=1,* delims==" %%A in (%%K) do (
        powershell -NoProfile -Command ^
          "$f='%GRADLE_PROPS%'; $k='%%A'; $v='%%B'; $lines = if (Test-Path $f) { Get-Content $f } else { @() }; if ($lines -match \"^$k=\") { $lines = $lines -replace \"^$k=.*\", \"$k=$v\" } else { $lines += \"$k=$v\" }; $lines | Set-Content $f" >nul 2>&1
        echo   Set %%A in gradle.properties
    )
)

REM =========================================================
REM EMULATOR OR PHYSICAL DEVICE
REM =========================================================

IF "!PHYSICAL_DEVICE!"=="1" (
    echo.
    echo Physical device mode — API URLs pointing to !DEVICE_IP!
    echo Ensure your device is connected via USB or wireless ADB.
    echo.
    set ADB_TARGET=-d
    goto :build
)

REM ── Find AVD ──────────────────────────────────────────────
set AVD_NAME=!ANDROID_AVD_NAME!
IF "!AVD_NAME!"=="" (
    for /f "usebackq tokens=*" %%a in (`"%ANDROID_HOME%\emulator\emulator.exe" -list-avds 2^>nul`) do (
        IF "!AVD_NAME!"=="" set AVD_NAME=%%a
    )
)

IF "!AVD_NAME!"=="" (
    echo.
    echo ERROR: No Android Virtual Device ^(AVD^) found.
    echo        Create one in Android Studio: Tools ^> Device Manager ^> + ^> Virtual
    echo        Recommended: Pixel 9 Pro, API 35, x86_64
    echo        Or set: set ANDROID_AVD_NAME=^<name^>
    echo.
    pause
    exit /b 1
)
echo AVD: !AVD_NAME!

REM ── Check if emulator already running ─────────────────────
set EMULATOR_ALREADY_RUNNING=0
%ADB_EXE% devices 2>nul | findstr /i "emulator" >nul 2>&1
IF %ERRORLEVEL% EQU 0 set EMULATOR_ALREADY_RUNNING=1

IF "!EMULATOR_ALREADY_RUNNING!"=="0" (
    echo.
    echo Starting emulator '!AVD_NAME!'...
    start "android-emulator" /MIN ^
        "%ANDROID_HOME%\emulator\emulator.exe" -avd "!AVD_NAME!" ^
        -no-snapshot-save -no-boot-anim -gpu auto

    echo Waiting for emulator to come online ^(~60 s^)...
    %ADB_EXE% -e wait-for-device >nul 2>&1

    :wait_boot
    set BOOT_VAL=0
    for /f "usebackq tokens=*" %%b in (`%ADB_EXE% -e shell getprop sys.boot_completed 2^>nul`) do set BOOT_VAL=%%b
    IF "!BOOT_VAL!"=="1" goto :emulator_ready
    timeout /t 4 >nul
    goto :wait_boot
    :emulator_ready
    echo Emulator fully booted
) ELSE (
    echo Emulator already running
)

set ADB_TARGET=-e

:build
REM =========================================================
REM BUILD DEBUG APK AND INSTALL
REM =========================================================

echo.
echo Building Android debug APK...
echo   API URL  : !API_BASE_URL!
echo   Keycloak : !KEYCLOAK_URL!
echo   WS       : !WS_BASE_URL!
echo   Google   : !GOOGLE_CLIENT_ID!
echo   Microsoft: !MICROSOFT_CLIENT_ID!
echo.

pushd apps\android
call gradlew.bat installDebug ^
  -PAPI_BASE_URL=!API_BASE_URL! ^
  -PKEYCLOAK_ISSUER=!KEYCLOAK_URL! ^
  -PWS_BASE_URL=!WS_BASE_URL! ^
  -PSTRIPE_PUBLISHABLE_KEY=!STRIPE_PUBLISHABLE_KEY! ^
  -PGOOGLE_CLIENT_ID=!GOOGLE_CLIENT_ID! ^
  -PMICROSOFT_CLIENT_ID=!MICROSOFT_CLIENT_ID! ^
  --no-daemon
set GRADLE_EXIT=!ERRORLEVEL!
popd

IF !GRADLE_EXIT! NEQ 0 (
    echo.
    echo ERROR: Android build failed ^(exit code !GRADLE_EXIT!^)
    echo        Check output above for compilation errors.
    echo.
    pause
    exit /b !GRADLE_EXIT!
)

echo APK installed

REM =========================================================
REM LAUNCH APP
REM =========================================================

%ADB_EXE% !ADB_TARGET! shell am start ^
    -n com.aegispay.android.debug/com.aegispay.android.ui.MainActivity ^
    >nul 2>&1
echo AegisPay Android launched

REM =========================================================
REM SUMMARY
REM =========================================================

echo.
echo =========================================================
echo   Android running!
echo =========================================================
echo.
IF "!PHYSICAL_DEVICE!"=="1" (
    echo   Mode     : Physical device  ^(!DEVICE_IP!^)
) ELSE (
    echo   Mode     : Emulator  ^(AVD: !AVD_NAME!^)
    echo   10.0.2.2 maps to this machine's localhost inside the emulator.
)
echo   API URL  : !API_BASE_URL!
echo   Keycloak : !KEYCLOAK_URL!
echo.
echo   Social login ^(via Keycloak^):
echo     Google    — uses !GOOGLE_CLIENT_ID!
echo     Microsoft — uses !MICROSOFT_CLIENT_ID!
echo.
echo   Useful commands:
echo     View logs  : %ADB_EXE% !ADB_TARGET! logcat -s AegisPay
echo     Rebuild    : cd apps\android ^&^& gradlew.bat installDebug
echo     Uninstall  : %ADB_EXE% !ADB_TARGET! uninstall com.aegispay.android.debug
echo     Kill emu   : %ADB_EXE% -e emu kill
echo.
pause
exit /b 0
