@echo off
setlocal EnableDelayedExpansion
title AegisPay Windows Bootstrap

REM =========================================================
REM AegisPay — Windows Bootstrap Script
REM Starts Docker infra, builds all JARs, launches all services,
REM the Next.js frontend, and optionally the Android emulator.
REM
REM Usage:
REM   start-aegispay.bat                   — backend + web only
REM   start-aegispay.bat android           — + Android emulator (10.0.2.2)
REM   start-aegispay.bat android <host-ip> — + Android on physical device
REM   start-aegispay.bat ios               — prints iOS/macOS instructions
REM
REM Prerequisites (all targets):
REM   - Docker Desktop (WSL 2 backend enabled)
REM   - Java 21 (JAVA_HOME set, or java.exe on PATH)
REM   - Maven 3.9+ (mvn.cmd on PATH)
REM   - Node 20 + pnpm 9 (node.exe on PATH)
REM
REM Prerequisites (android target):
REM   - Android Studio with SDK installed
REM   - ANDROID_HOME set to SDK root (e.g. %LOCALAPPDATA%\Android\Sdk)
REM   - At least one AVD created in Android Studio > Device Manager
REM   - adb.exe on PATH or reachable via %ANDROID_HOME%\platform-tools
REM =========================================================

echo =========================================================
echo   AegisPay Bootstrap  (Windows)
echo =========================================================

REM =========================================================
REM PARSE ARGUMENTS
REM   %1 = "android" | "ios" | (empty)
REM   %2 = optional host IP for physical device testing
REM =========================================================

set LAUNCH_ANDROID=0
set LAUNCH_IOS_NOTE=0
set PHYSICAL_DEVICE=0
set DEVICE_IP=10.0.2.2

IF /I "%1"=="android" (
    set LAUNCH_ANDROID=1
    IF NOT "%2"=="" (
        set PHYSICAL_DEVICE=1
        set DEVICE_IP=%2
    )
)
IF /I "%1"=="ios" set LAUNCH_IOS_NOTE=1

REM Derive Android URLs from DEVICE_IP (set once, used throughout)
set API_BASE_URL_ANDROID=http://!DEVICE_IP!:8080
set KEYCLOAK_URL_ANDROID=http://!DEVICE_IP!:8180/realms/aegispay
set WS_BASE_URL_ANDROID=ws://!DEVICE_IP!:8090

REM =========================================================
REM iOS note (Windows can't run the iOS Simulator)
REM =========================================================

IF NOT "!LAUNCH_IOS_NOTE!"=="1" goto :after_ios_note
echo.
echo =========================================================
echo   iOS Simulator - macOS only
echo =========================================================
echo.
echo   iOS development requires a Mac with Xcode installed.
echo   On your Mac run:
echo.
echo     ./start-local.sh --ios
echo.
echo   The script will:
echo     1. Write apps/ios/LocalDev.xcconfig with all API URLs
echo     2. Boot the default iPhone simulator
echo     3. Open Xcode - press Run Cmd+R to build and install
echo.
echo   Networking on the iOS Simulator:
echo     localhost inside the simulator == your Mac's localhost
echo     So the default API_BASE_URL=http://localhost:8080 works as-is.
echo.
echo   On a physical iPhone (same LAN):
echo     ./start-local.sh --ios --device-ip ^<your-mac-lan-ip^>
echo =========================================================
echo.
:after_ios_note

REM =========================================================
REM AUTO-DETECT MAVEN
REM =========================================================

set MVN_CMD=mvn
where mvn >nul 2>&1
if %ERRORLEVEL% EQU 0 goto :mvn_found
set MVN_CMD=
for %%d in (
    "C:\Program Files\Apache\maven\bin\mvn.cmd"
    "C:\tools\maven\bin\mvn.cmd"
    "%USERPROFILE%\scoop\apps\maven\current\bin\mvn.cmd"
    "%USERPROFILE%\AppData\Local\Programs\apache-maven\bin\mvn.cmd"
) do (
    if exist %%d if "!MVN_CMD!"=="" set MVN_CMD=%%d
)
if "!MVN_CMD!"=="" (
    echo ERROR: mvn not found on PATH and no common install dir matched.
    echo        Install Maven 3.9+ and ensure mvn.cmd is on PATH.
    echo        https://maven.apache.org/download.cgi
    pause
    exit /b 1
)
:mvn_found
echo Maven: !MVN_CMD!

REM =========================================================
REM JAVA CHECK (requires 21)
REM =========================================================

java -version >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo ERROR: java not found on PATH.
    echo        Install Temurin 21: https://adoptium.net
    pause
    exit /b 1
)
REM Verify Java 21+
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VER=%%v
)
set JAVA_VER=!JAVA_VER:"=!
for /f "delims=." %%m in ("!JAVA_VER!") do set JAVA_MAJOR=%%m
IF !JAVA_MAJOR! LSS 21 (
    echo ERROR: Java 21 required but found version !JAVA_VER!
    echo        Install Temurin 21: https://adoptium.net
    pause
    exit /b 1
)

REM =========================================================
REM ENVIRONMENT VARIABLES
REM =========================================================

set NODE_OPTIONS=--dns-result-order=ipv4first

REM ── PostgreSQL (host port 5433 maps to container port 5432) ──────────────
set DB_HOST=localhost
set DB_PORT=5433
set DB_USERNAME=aegispay
set DB_PASSWORD=aegispay_dev
REM Spring-native overrides — these are honoured unconditionally by Spring Boot's
REM property binder even when child processes are launched via "start cmd /c".
REM NOTE: SPRING_DATASOURCE_URL is intentionally NOT set here so each service
REM       resolves its own per-service database from application.yml (e.g.
REM       aegispay_users, aegispay_ledger, etc.). Only credentials are overridden.
set SPRING_DATASOURCE_USERNAME=aegispay
set SPRING_DATASOURCE_PASSWORD=aegispay_dev

REM ── Redis ─────────────────────────────────────────────────────────────────
set REDIS_HOST=localhost
set REDIS_PORT=6379
set REDIS_PASSWORD=aegispay_dev

REM ── MongoDB ───────────────────────────────────────────────────────────────
set MONGODB_HOST=localhost
set MONGODB_PORT=27017
set MONGODB_URI=mongodb://localhost:27017/aegispay

REM ── ClickHouse ────────────────────────────────────────────────────────────
set CLICKHOUSE_URL=jdbc:clickhouse://localhost:8123/aegispay_analytics
set CLICKHOUSE_USER=default
set CLICKHOUSE_PASSWORD=

set OAUTH2_ISSUER_URI=http://localhost:8180/realms/aegispay
set OAUTH2_PRIMARY_ISSUER_URI=http://localhost:8180/realms/aegispay
set OAUTH2_ISSUER_KEYCLOAK=http://localhost:8180/realms/aegispay

set USER_SERVICE_URI=http://localhost:8081
set TRANSACTION_SERVICE_URI=http://localhost:8082
set LEDGER_SERVICE_URI=http://localhost:8083
set ORCHESTRATOR_SERVICE_URI=http://localhost:8084
set RISK_ENGINE_URI=http://localhost:8085
set NOTIFICATION_SERVICE_URI=http://localhost:8086
set AI_PLATFORM_URI=http://localhost:8091

REM Optional — override if you have real keys
IF "%STRIPE_SECRET_KEY%"=="" set STRIPE_SECRET_KEY=sk_test_51TTkk2CyjRW67i1DP4dcrEgzhOm9dUe61k9U5kPNoDST6Deuy9rAvgJY0ZL93kKbDmdP7SEAUXUM6M4TMMtxkWNb00eKgRIql5
IF "%STRIPE_PUBLISHABLE_KEY%"=="" set STRIPE_PUBLISHABLE_KEY=pk_test_placeholder_local_dev
IF "%SMTP_PASSWORD%"=="" set SMTP_PASSWORD=mcinrqrbfqayklee
IF "%SLACK_WEBHOOK_URL%"=="" set SLACK_WEBHOOK_URL=https://hooks.slack.com/services/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
IF "%FAST2SMS_API_KEY%"=="" set FAST2SMS_API_KEY=ZNd8Xx4lqrERbj67Unwi1LHvB0smOFDayTCkgczIYKM9oPAfV2jDTskbhao0QZ3luvA7VfiLdWM2KNOe
IF "%STRIPE_WEBHOOK_SECRET%"=="" set STRIPE_WEBHOOK_SECRET=whsec_a832f229a3955efe1399cef8e1e858e598b2d5e8c01a4b0417a0684419e7b176

REM =========================================================
REM FREE REQUIRED PORTS
REM =========================================================

echo.
echo Freeing required ports...

for %%p in (
5433 6379 27017 9094 8180 8123 3100 8090
8080 8081 8082 8083 8084 8085 8086 8087 8089 8091 3000
) do (
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr /r ":%%p[ \t]" 2^>nul') do (
        if NOT "%%a"=="" taskkill /PID %%a /F >nul 2>&1
    )
)

echo All ports free

REM =========================================================
REM BUILD SHARED LIBS
REM =========================================================

echo.
echo Installing root POM...

call !MVN_CMD! --batch-mode --no-transfer-progress install -N -DskipTests -q

IF %ERRORLEVEL% NEQ 0 (
    echo ERROR: Root POM install failed
    pause
    exit /b 1
)

echo Root POM installed

echo.
echo Installing libs parent POM ^(aegispay-libs^)...

call !MVN_CMD! --batch-mode --no-transfer-progress install -N -f libs\pom.xml -DskipTests -q

IF %ERRORLEVEL% NEQ 0 (
    echo ERROR: libs parent POM install failed
    pause
    exit /b 1
)

echo Libs parent POM installed

echo.
echo Building and installing shared libraries...

call !MVN_CMD! --batch-mode --no-transfer-progress clean install ^
-pl libs/common-domain,libs/common-security,libs/common-kafka,libs/common-observability ^
--also-make ^
-DskipTests -q

IF %ERRORLEVEL% NEQ 0 (
    echo ERROR: Shared library build failed
    pause
    exit /b 1
)

echo Shared libs built and installed

REM =========================================================
REM BUILD SERVICES
REM =========================================================

echo.
echo Building services...

for %%s in (
api-gateway
user-service
transaction-service
ledger-service
payment-orchestrator
risk-engine
notification-service
ai-platform
data-pipeline
reconciliation-service
) do (
    echo   Building %%s...

    call !MVN_CMD! --batch-mode --no-transfer-progress package ^
    -pl services/%%s ^
    -DskipTests -q

    IF !ERRORLEVEL! NEQ 0 (
        echo ERROR: Build failed for %%s
        pause
        exit /b 1
    )
)

echo All service JARs built

REM =========================================================
REM CLEAN INFRA (fresh volumes every start)
REM =========================================================

echo.
echo Cleaning old infrastructure...

docker compose down -v --remove-orphans

echo Previous infra removed

docker compose up -d

REM =========================================================
REM WAIT FOR KEYCLOAK
REM =========================================================

echo.
echo Waiting for Keycloak realm...

set _KC_TRIES=0
:wait_keycloak
curl -sf http://localhost:8180/realms/aegispay >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    set /a _KC_TRIES+=1
    IF !_KC_TRIES! GEQ 60 (
        echo ERROR: Keycloak did not start after 3 minutes. Check Docker logs.
        pause
        exit /b 1
    )
    echo   Keycloak not ready yet ^(!_KC_TRIES!/60^)...
    timeout /t 3 >nul
    goto wait_keycloak
)
echo Keycloak ready

REM =========================================================
REM CONFIGURE KEYCLOAK
REM =========================================================

echo.
echo Configuring Keycloak client scopes...

IF EXIST infra\local\keycloak\configure-keycloak.bat (
    call infra\local\keycloak\configure-keycloak.bat
    echo Keycloak configured
) ELSE (
    echo WARNING: infra\local\keycloak\configure-keycloak.bat not found, skipping
)

REM =========================================================
REM WAIT FOR CLICKHOUSE
REM =========================================================

echo.
echo Waiting for ClickHouse...

set _CH_TRIES=0
:wait_clickhouse
curl -sf http://localhost:8123/ping 2>nul | findstr "Ok" >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    set /a _CH_TRIES+=1
    IF !_CH_TRIES! GEQ 36 (
        echo ERROR: ClickHouse did not start after 3 minutes. Check Docker logs.
        pause
        exit /b 1
    )
    echo   ClickHouse not ready yet ^(!_CH_TRIES!/36^)...
    timeout /t 5 >nul
    goto wait_clickhouse
)
echo ClickHouse ready

REM =========================================================
REM WAIT FOR GRAFANA
REM =========================================================

echo.
echo Waiting for Grafana...

set _GF_TRIES=0
:wait_grafana
curl -sf http://localhost:3100/api/health 2>nul | findstr "ok" >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    set /a _GF_TRIES+=1
    IF !_GF_TRIES! GEQ 36 (
        echo WARNING: Grafana did not respond after 3 minutes, continuing anyway...
        goto :grafana_done
    )
    echo   Grafana not ready yet ^(!_GF_TRIES!/36^)...
    timeout /t 5 >nul
    goto wait_grafana
)
echo Grafana ready
:grafana_done

REM =========================================================
REM WAIT FOR KAFKA
REM =========================================================

echo.
echo Waiting for Kafka broker...

set _KF_TRIES=0
:wait_kafka
docker exec aegispay-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    set /a _KF_TRIES+=1
    IF !_KF_TRIES! GEQ 60 (
        echo ERROR: Kafka did not start after 3 minutes. Check Docker logs.
        pause
        exit /b 1
    )
    echo   Kafka not ready yet ^(!_KF_TRIES!/60^)...
    timeout /t 3 >nul
    goto wait_kafka
)
echo Kafka ready

REM =========================================================
REM WAIT FOR DB-INIT (ensures per-service databases exist)
REM =========================================================

echo.
echo Waiting for db-init to finish creating databases...

set _DBI_TRIES=0
:wait_db_init
docker inspect --format "{{.State.Status}}" aegispay-db-init >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    REM container not yet visible — still starting
    set /a _DBI_TRIES+=1
    IF !_DBI_TRIES! GEQ 60 (
        echo WARNING: db-init status unknown after 60 tries, continuing...
        goto :db_init_done
    )
    timeout /t 2 >nul
    goto wait_db_init
)
for /f "tokens=*" %%s in ('docker inspect --format "{{.State.Status}}" aegispay-db-init 2^>nul') do set DBI_STATUS=%%s
IF "!DBI_STATUS!"=="exited" goto :db_init_done
set /a _DBI_TRIES+=1
IF !_DBI_TRIES! GEQ 60 (
    echo WARNING: db-init still running after 2 minutes, continuing...
    goto :db_init_done
)
echo   db-init still running ^(!DBI_STATUS!^)...
timeout /t 2 >nul
goto wait_db_init
:db_init_done

REM Verify aegispay user can connect — catch wrong-password early
echo Verifying PostgreSQL credentials...
docker exec -e PGPASSWORD=aegispay_dev aegispay-postgres psql -U aegispay -d aegispay -c "SELECT 1" >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo ERROR: Cannot connect to PostgreSQL as user 'aegispay'.
    echo        Check DB_PASSWORD matches POSTGRES_PASSWORD in docker-compose.yml.
    echo        Both must be 'aegispay_dev' for local dev.
    pause
    exit /b 1
)
echo PostgreSQL credentials verified

REM =========================================================
REM CREATE LOG DIRECTORY
REM =========================================================

IF NOT EXIST logs mkdir logs

REM =========================================================
REM START SERVICES (Wave 1 — all except reconciliation)
REM =========================================================

echo.
echo Starting backend services (Wave 1)...

start "api-gateway" /MIN cmd /c ^
"java -jar services\api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar > logs\api-gateway.log 2>&1"

start "user-service" /MIN cmd /c ^
"java -jar services\user-service\target\user-service-1.0.0-SNAPSHOT.jar > logs\user-service.log 2>&1"

start "transaction-service" /MIN cmd /c ^
"java -jar services\transaction-service\target\transaction-service-1.0.0-SNAPSHOT.jar > logs\transaction-service.log 2>&1"

start "ledger-service" /MIN cmd /c ^
"java -jar services\ledger-service\target\ledger-service-1.0.0-SNAPSHOT.jar > logs\ledger-service.log 2>&1"

start "payment-orchestrator" /MIN cmd /c ^
"java -jar services\payment-orchestrator\target\payment-orchestrator-1.0.0-SNAPSHOT.jar > logs\payment-orchestrator.log 2>&1"

start "risk-engine" /MIN cmd /c ^
"java -jar services\risk-engine\target\risk-engine-1.0.0-SNAPSHOT.jar > logs\risk-engine.log 2>&1"

start "notification-service" /MIN cmd /c ^
"java -jar services\notification-service\target\notification-service-1.0.0-SNAPSHOT.jar > logs\notification-service.log 2>&1"

start "ai-platform" /MIN cmd /c ^
"java -jar services\ai-platform\target\ai-platform-1.0.0-SNAPSHOT.jar > logs\ai-platform.log 2>&1"

start "data-pipeline" /MIN cmd /c ^
"java -jar services\data-pipeline\target\data-pipeline-1.0.0-SNAPSHOT.jar > logs\data-pipeline.log 2>&1"

REM =========================================================
REM WAIT FOR LEDGER (reconciliation depends on it)
REM =========================================================

echo.
echo Waiting for ledger-service before starting reconciliation...

:wait_ledger
curl -sf http://localhost:8083/actuator/health >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    timeout /t 3 >nul
    goto wait_ledger
)
echo ledger-service healthy

REM =========================================================
REM START SERVICES (Wave 2 — reconciliation)
REM =========================================================

start "reconciliation-service" /MIN cmd /c ^
"java -jar services\reconciliation-service\target\reconciliation-service-1.0.0-SNAPSHOT.jar > logs\reconciliation-service.log 2>&1"

REM =========================================================
REM HEALTH CHECKS
REM =========================================================

echo.
echo Waiting for all services to be healthy...

call :wait_service api-gateway 8080
call :wait_service user-service 8081
call :wait_service transaction-service 8082
call :wait_service ledger-service 8083
call :wait_service payment-orchestrator 8084
call :wait_service risk-engine 8085
call :wait_service notification-service 8086
call :wait_service reconciliation-service 8087
call :wait_service data-pipeline 8089
call :wait_service ai-platform 8091

REM =========================================================
REM SEED TEST ACCOUNTS
REM =========================================================

echo.
echo Seeding test accounts...

set CUSTOMER_KC_UUID=59295e61-a284-40ed-8d3b-9e15bedeb040
set PAYEE_KC_UUID=3bf3e523-9de8-4254-9cc9-d5fa50ff8d4a

docker exec -e PGPASSWORD=aegispay_dev aegispay-postgres psql -U aegispay -d aegispay_ledger -c "INSERT INTO accounts (user_id, currency, available_balance, reserved_balance) VALUES ('%CUSTOMER_KC_UUID%', 'INR', 50000.00, 0.00), ('%PAYEE_KC_UUID%', 'INR', 25000.00, 0.00) ON CONFLICT (user_id, currency) DO NOTHING;" >nul 2>&1
IF %ERRORLEVEL% EQU 0 ( echo Ledger accounts seeded ) ELSE ( echo Ledger seed skipped ^(may already exist^) )

docker exec -e PGPASSWORD=aegispay_dev aegispay-postgres psql -U aegispay -d aegispay_users -c "INSERT INTO users (external_id, email, first_name, last_name, phone, role, kyc_status, is_active) VALUES ('%CUSTOMER_KC_UUID%', 'customer@aegispay.local', 'Test', 'Customer', '+919000000001', 'CUSTOMER', 'APPROVED', true), ('%PAYEE_KC_UUID%', 'payee@aegispay.local', 'Test', 'Payee', '+919000000002', 'CUSTOMER', 'APPROVED', true) ON CONFLICT DO NOTHING;" >nul 2>&1
IF %ERRORLEVEL% EQU 0 ( echo User data seeded ) ELSE ( echo User seed skipped ^(may already exist^) )

echo Test accounts ready — Customer INR 50000  ^|  Payee INR 25000

REM =========================================================
REM FRONTEND
REM =========================================================

echo.
echo Starting frontend...

IF NOT EXIST apps\web\.env.local (
    copy apps\web\.env.local.example apps\web\.env.local >nul
    echo Created apps\web\.env.local from example
)

where pnpm >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo ERROR: pnpm not found on PATH.
    echo        Install: npm install -g pnpm
    pause
    exit /b 1
)
call pnpm install --silent
start "web-app" /MIN cmd /c "pnpm --filter @aegispay/web dev > logs\web.log 2>&1"

REM =========================================================
REM ANDROID EMULATOR  (only when "android" argument given)
REM =========================================================

IF "!LAUNCH_ANDROID!"=="0" goto :android_done

echo.
echo =========================================================
echo   Android Setup
echo =========================================================

REM ── Verify ANDROID_HOME ───────────────────────────────────
IF "!ANDROID_HOME!"=="" (
    echo.
    echo ERROR: ANDROID_HOME is not set.
    echo        Install Android Studio then add to your system env:
    echo          ANDROID_HOME = %LOCALAPPDATA%\Android\Sdk
    echo        Also add to PATH:
    echo          %LOCALAPPDATA%\Android\Sdk\platform-tools
    echo          %LOCALAPPDATA%\Android\Sdk\emulator
    echo.
    goto :android_done
)

REM ── Verify adb ────────────────────────────────────────────
set ADB_EXE="%ANDROID_HOME%\platform-tools\adb.exe"
IF NOT EXIST %ADB_EXE% set ADB_EXE=adb

REM ── Inject STRIPE_PUBLISHABLE_KEY into gradle.properties ──
set GRADLE_PROPS=apps\android\gradle.properties
IF EXIST "%GRADLE_PROPS%" (
    findstr /C:"STRIPE_PUBLISHABLE_KEY" "%GRADLE_PROPS%" >nul 2>&1
    IF !ERRORLEVEL! NEQ 0 (
        echo STRIPE_PUBLISHABLE_KEY=!STRIPE_PUBLISHABLE_KEY!>> "%GRADLE_PROPS%"
        echo   Injected STRIPE_PUBLISHABLE_KEY into gradle.properties
    ) ELSE (
        powershell -NoProfile -Command ^
          "(Get-Content '%GRADLE_PROPS%') -replace 'STRIPE_PUBLISHABLE_KEY=.*', 'STRIPE_PUBLISHABLE_KEY=!STRIPE_PUBLISHABLE_KEY!' | Set-Content '%GRADLE_PROPS%'"
        echo   Updated STRIPE_PUBLISHABLE_KEY in gradle.properties
    )
) ELSE (
    echo STRIPE_PUBLISHABLE_KEY=!STRIPE_PUBLISHABLE_KEY!> "%GRADLE_PROPS%"
    echo   Created gradle.properties with STRIPE_PUBLISHABLE_KEY
)

REM ── Physical device vs emulator ───────────────────────────
IF "!PHYSICAL_DEVICE!"=="1" (
    echo.
    echo Physical device mode - API URLs will use !DEVICE_IP!
    echo Make sure your Android device is connected via USB
    echo   ^(or paired for wireless debugging on the same LAN^)
    echo.
    set ADB_TARGET=-d
    goto :android_build
)

REM ── Find AVD ──────────────────────────────────────────────
set AVD_NAME=
IF NOT "!ANDROID_AVD_NAME!"=="" (
    set AVD_NAME=!ANDROID_AVD_NAME!
    echo   Using AVD from env: !AVD_NAME!
) ELSE (
    REM List AVDs and pick the first one
    for /f "usebackq tokens=*" %%a in (`"%ANDROID_HOME%\emulator\emulator.exe" -list-avds 2^>nul`) do (
        IF "!AVD_NAME!"=="" set AVD_NAME=%%a
    )
    IF "!AVD_NAME!"=="" (
        echo.
        echo ERROR: No Android Virtual Device ^(AVD^) found.
        echo        Create one in Android Studio: Tools ^> Device Manager ^> + ^> Virtual
        echo        Then re-run this script.
        echo        Or set ANDROID_AVD_NAME=^<your-avd-name^> before running.
        echo.
        goto :android_done
    )
    echo   Found AVD: !AVD_NAME!
)

REM ── Start emulator ────────────────────────────────────────
echo   Starting emulator '!AVD_NAME!'...
start "android-emulator" /MIN ^
    "%ANDROID_HOME%\emulator\emulator.exe" -avd "!AVD_NAME!" -no-snapshot-save -gpu auto

REM Wait for adb to see the emulator
echo   Waiting for emulator to come online (this may take ~60 s)...
%ADB_EXE% -e wait-for-device >nul 2>&1

REM Poll sys.boot_completed
set ADB_TARGET=-e
:wait_android_boot
set BOOT_VAL=0
for /f "usebackq tokens=*" %%b in (`%ADB_EXE% !ADB_TARGET! shell getprop sys.boot_completed 2^>nul`) do set BOOT_VAL=%%b
IF "!BOOT_VAL!"=="1" goto :android_booted
timeout /t 4 >nul
goto :wait_android_boot
:android_booted
echo   Emulator fully booted

:android_build
REM ── Build and install APK ─────────────────────────────────
echo.
echo   Building and installing Android APK...
echo     API URL  : !API_BASE_URL_ANDROID!
echo     Keycloak : !KEYCLOAK_URL_ANDROID!
echo     WS       : !WS_BASE_URL_ANDROID!
echo.

pushd apps\android
call gradlew.bat installDebug ^
  -PAPI_BASE_URL=!API_BASE_URL_ANDROID! ^
  -PKEYCLOAK_ISSUER=!KEYCLOAK_URL_ANDROID! ^
  -PWS_BASE_URL=!WS_BASE_URL_ANDROID! ^
  -PSTRIPE_PUBLISHABLE_KEY=!STRIPE_PUBLISHABLE_KEY! ^
  --no-daemon
set GRADLE_EXIT=!ERRORLEVEL!
popd

IF !GRADLE_EXIT! NEQ 0 (
    echo.
    echo ERROR: Android build/install failed ^(exit code !GRADLE_EXIT!^)
    echo        Check logs above for details.
    echo.
    goto :android_done
)

echo   APK installed on device

REM ── Launch app ────────────────────────────────────────────
%ADB_EXE% !ADB_TARGET! shell am start -n com.aegispay.android/.MainActivity >nul 2>&1
echo   AegisPay launched on Android ^(!ADB_TARGET! target^)

:android_done

REM =========================================================
REM SUMMARY
REM =========================================================

echo.
echo =========================================================
echo   AegisPay is running!
echo =========================================================
echo.
echo   Web App        -^>  http://localhost:3000
echo   API Gateway    -^>  http://localhost:8080/actuator/health
echo   Keycloak       -^>  http://localhost:8180   (admin / admin)
echo   Kafka UI       -^>  http://localhost:8090
echo   Grafana        -^>  http://localhost:3100   (admin / admin)
echo   ClickHouse     -^>  http://localhost:8123
echo   PostgreSQL     -^>  localhost:5433          (aegispay / aegispay_dev)
echo.
echo   Test accounts:
echo     Sender : customer@aegispay.local / Test@1234  (INR 50000)
echo     Payee  : payee@aegispay.local    / Test@1234  (INR 25000)
echo     Payee UUID: %PAYEE_KC_UUID%
echo.

IF "!LAUNCH_ANDROID!"=="1" (
    echo   Android
    echo   -------
    IF "!PHYSICAL_DEVICE!"=="1" (
        echo     Mode           : Physical device  ^(!DEVICE_IP!^)
        echo     API URL        : !API_BASE_URL_ANDROID!
        echo     Note           : Device must share the same LAN as this machine.
    ) ELSE (
        echo     Mode           : Emulator  ^(AVD: !AVD_NAME!^)
        echo     API URL        : !API_BASE_URL_ANDROID!
        echo     10.0.2.2 maps to this machine's localhost inside the emulator.
    )
    echo     Stripe key     : !STRIPE_PUBLISHABLE_KEY!
    echo     To rebuild only: cd apps\android ^&^& gradlew.bat installDebug
    echo     App logs       : %ADB_EXE% !ADB_TARGET! logcat -s AegisPay
    echo.
)

echo   iOS (macOS only)
echo   ----------------
echo     Run on a Mac: ./start-local.sh --ios
echo     The iOS Simulator uses "localhost" which maps to the Mac's localhost.
echo     For a physical iPhone add: --device-ip ^<mac-lan-ip^>
echo.
echo   Logs (backend+web): logs\
echo.
echo   Stop everything:
echo     docker compose down
echo     taskkill /F /IM java.exe
echo     taskkill /F /IM node.exe
IF "!LAUNCH_ANDROID!"=="1" (
    echo     taskkill /F /IM "qemu-system-x86_64.exe"   ^(emulator process^)
)
echo.
echo =========================================================
pause
exit /b

REM =========================================================
REM WAIT FUNCTION
REM =========================================================

:wait_service
set _SVC=%1
set _PORT=%2
echo   Waiting for %_SVC% on port %_PORT%...
:_svc_loop
curl -sf http://localhost:%_PORT%/actuator/health >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    timeout /t 3 >nul
    goto _svc_loop
)
echo   %_SVC% healthy
exit /b
