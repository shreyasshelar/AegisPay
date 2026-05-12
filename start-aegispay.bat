@echo off
setlocal EnableDelayedExpansion
title AegisPay Windows Bootstrap

REM =========================================================
REM COLORS
REM =========================================================

echo =========================================================
echo AegisPay Bootstrap
echo =========================================================

REM =========================================================
REM ENVIRONMENT VARIABLES
REM =========================================================

set NODE_OPTIONS=--dns-result-order=ipv4first

set DB_PORT=5433

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

REM =========================================================
REM JAVA CHECK
REM =========================================================

java -version >nul 2>&1

IF %ERRORLEVEL% NEQ 0 (
    echo Java not found
    pause
    exit /b
)

REM =========================================================
REM FREE PORTS
REM =========================================================

echo.
echo Freeing required ports...

for %%p in (
5433 6379 27017 9094 8180 8123 8088 8090
8080 8081 8082 8083 8084 8085 8086 8087 8089 8091 3000
) do (
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%%p') do (
        taskkill /PID %%a /F >nul 2>&1
    )
)

echo All ports free

REM =========================================================
REM BUILD SHARED LIBS
REM =========================================================

echo.
echo Building shared libraries...

call "C:\Users\kanaka\Documents\program_files\Maven\apache-maven-3.9.15\bin\mvn.cmd" --batch-mode --no-transfer-progress clean package ^
-pl libs/common-domain,libs/common-security,libs/common-kafka,libs/common-observability ^
--also-make ^
-DskipTests -q

IF %ERRORLEVEL% NEQ 0 (
    echo Shared library build failed
    pause
    exit /b
)

echo Shared libs built

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
    echo Building %%s...

    call "C:\Users\kanaka\Documents\program_files\Maven\apache-maven-3.9.15\bin\mvn.cmd" --batch-mode --no-transfer-progress package ^
    -pl services/%%s ^
    --also-make ^
    -DskipTests -q

    IF !ERRORLEVEL! NEQ 0 (
        echo Build failed for %%s
        pause
        exit /b
    )
)

echo All service JARs built

REM =========================================================
REM START DOCKER
REM =========================================================

echo.
echo Starting infrastructure...

docker info >nul 2>&1

IF %ERRORLEVEL% NEQ 0 (
    echo Docker Desktop not running
    start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"

    :wait_docker
    docker info >nul 2>&1

    IF !ERRORLEVEL! NEQ 0 (
        echo Waiting for Docker...
        timeout /t 3 >nul
        goto wait_docker
    )
)

echo Docker ready

REM =========================================================
REM CLEAN INFRA
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

:wait_keycloak

curl -sf http://localhost:8180/realms/aegispay >nul 2>&1

IF %ERRORLEVEL% NEQ 0 (
    echo Waiting for Keycloak...
    timeout /t 3 >nul
    goto wait_keycloak
)

echo Keycloak ready

REM =========================================================
REM CONFIGURE KEYCLOAK
REM =========================================================

echo.
echo Configuring Keycloak...

call infra\local\keycloak\configure-keycloak.bat

REM =========================================================
REM WAIT FOR KAFKA
REM =========================================================

echo.
echo Waiting for Kafka...

:wait_kafka

docker exec aegispay-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >nul 2>&1

IF %ERRORLEVEL% NEQ 0 (
    echo Waiting for Kafka broker...
    timeout /t 3 >nul
    goto wait_kafka
)

echo Kafka ready

REM =========================================================
REM CREATE LOG DIRECTORY
REM =========================================================

IF NOT EXIST logs (
    mkdir logs
)

REM =========================================================
REM START SERVICES
REM =========================================================

echo.
echo Starting backend services...

start "api-gateway" cmd /k ^
"java -jar services\api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar > logs\api-gateway.log 2>&1"

start "user-service" cmd /k ^
"java -jar services\user-service\target\user-service-1.0.0-SNAPSHOT.jar > logs\user-service.log 2>&1"

start "transaction-service" cmd /k ^
"java -jar services\transaction-service\target\transaction-service-1.0.0-SNAPSHOT.jar > logs\transaction-service.log 2>&1"

start "ledger-service" cmd /k ^
"java -jar services\ledger-service\target\ledger-service-1.0.0-SNAPSHOT.jar > logs\ledger-service.log 2>&1"

start "payment-orchestrator" cmd /k ^
"java -jar services\payment-orchestrator\target\payment-orchestrator-1.0.0-SNAPSHOT.jar > logs\payment-orchestrator.log 2>&1"

start "risk-engine" cmd /k ^
"java -jar services\risk-engine\target\risk-engine-1.0.0-SNAPSHOT.jar > logs\risk-engine.log 2>&1"

start "notification-service" cmd /k ^
"java -jar services\notification-service\target\notification-service-1.0.0-SNAPSHOT.jar > logs\notification-service.log 2>&1"

start "ai-platform" cmd /k ^
"java -jar services\ai-platform\target\ai-platform-1.0.0-SNAPSHOT.jar > logs\ai-platform.log 2>&1"

start "data-pipeline" cmd /k ^
"java -jar services\data-pipeline\target\data-pipeline-1.0.0-SNAPSHOT.jar > logs\data-pipeline.log 2>&1"

REM =========================================================
REM WAIT FOR LEDGER
REM =========================================================

echo.
echo Waiting for ledger-service...

:wait_ledger

curl -sf http://localhost:8083/actuator/health >nul 2>&1

IF %ERRORLEVEL% NEQ 0 (
    timeout /t 3 >nul
    goto wait_ledger
)

echo ledger-service healthy

start "reconciliation-service" cmd /k ^
"java -jar services\reconciliation-service\target\reconciliation-service-1.0.0-SNAPSHOT.jar > logs\reconciliation-service.log 2>&1"

REM =========================================================
REM HEALTH CHECKS
REM =========================================================

echo.
echo Waiting for all services...

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




echo.
echo Seeding test accounts...

set CUSTOMER_KC_UUID=59295e61-a284-40ed-8d3b-9e15bedeb040
set PAYEE_KC_UUID=3bf3e523-9de8-4254-9cc9-d5fa50ff8d4a

docker exec aegispay-postgres psql -U aegispay -d aegispay_ledger -c "INSERT INTO accounts (user_id, currency, available_balance, reserved_balance) VALUES ('%CUSTOMER_KC_UUID%', 'INR', 50000.00, 0.00), ('%PAYEE_KC_UUID%', 'INR', 25000.00, 0.00) ON CONFLICT (user_id, currency) DO NOTHING;" >nul 2>&1

IF %ERRORLEVEL% NEQ 0 (
    echo Ledger seed skipped
) ELSE (
    echo Ledger accounts seeded
)

docker exec aegispay-postgres psql -U aegispay -d aegispay_users -c "INSERT INTO users (external_id, email, first_name, last_name, phone, role, kyc_status, is_active) VALUES ('%CUSTOMER_KC_UUID%', 'customer@aegispay.local', 'Test', 'Customer', '+919000000001', 'CUSTOMER', 'APPROVED', true), ('%PAYEE_KC_UUID%', 'payee@aegispay.local', 'Test', 'Payee', '+919000000002', 'CUSTOMER', 'APPROVED', true) ON CONFLICT DO NOTHING;" >nul 2>&1

IF %ERRORLEVEL% NEQ 0 (
    echo User seed skipped
) ELSE (
    echo User data seeded
)

echo Test data seeded
echo Customer Balance: INR 50000
echo Payee Balance: INR 25000

REM =========================================================
REM FRONTEND
REM =========================================================

echo.
echo Starting frontend...

IF NOT EXIST apps\web\.env.local (
    copy apps\web\.env.local.example apps\web\.env.local
)

call npm install --silent --prefer-offline

start "web-app" cmd /k ^
"npm run dev --workspace=apps/web > logs\web.log 2>&1"

REM =========================================================
REM SUMMARY
REM =========================================================

echo.
echo =========================================================
echo All services healthy
echo =========================================================
echo.
echo Web App:
echo http://localhost:3000
echo.
echo API Gateway:
echo http://localhost:8080/actuator/health
echo.
echo Keycloak:
echo http://localhost:8180
echo admin / admin
echo.
echo Kafka UI:
echo http://localhost:8090
echo.
echo Superset:
echo http://localhost:8088
echo.
echo Logs:
echo logs\
echo.
echo Stop everything:
echo docker compose down
echo taskkill /F /IM java.exe
echo taskkill /F /IM node.exe
echo.
pause
exit /b

REM =========================================================
REM WAIT FUNCTION
REM =========================================================

:wait_service

set SERVICE_NAME=%1
set PORT=%2

echo Waiting for %SERVICE_NAME% on %PORT%...

:service_loop

curl -sf http://localhost:%PORT%/actuator/health >nul 2>&1

IF %ERRORLEVEL% NEQ 0 (
    timeout /t 3 >nul
    goto service_loop
)

echo %SERVICE_NAME% healthy

exit /b