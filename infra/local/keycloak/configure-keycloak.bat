@echo off
setlocal EnableDelayedExpansion

set KC_URL=http://localhost:8180
set REALM=aegispay
set ADMIN_USER=admin
set ADMIN_PASS=admin
set CLIENT_ID_NAME=aegispay-web

echo =========================================================
echo Configuring Keycloak
echo =========================================================

echo Waiting for Keycloak admin token...

:wait_token

curl -s -X POST "%KC_URL%/realms/master/protocol/openid-connect/token" -H "Content-Type: application/x-www-form-urlencoded" -d "client_id=admin-cli" -d "username=%ADMIN_USER%" -d "password=%ADMIN_PASS%" -d "grant_type=password" -o token.json

findstr "access_token" token.json >nul

IF %ERRORLEVEL% NEQ 0 (
    echo Waiting for admin token...
    timeout /t 3 >nul
    goto wait_token
)

echo Admin token obtained

REM =========================================================
REM EXTRACT TOKEN
REM =========================================================

for /f "tokens=2 delims=:," %%a in ('findstr "access_token" token.json') do (
    set TOKEN=%%a
)

set TOKEN=%TOKEN:"=%
set TOKEN=%TOKEN: =%

echo Token extracted

REM =========================================================
REM GET CLIENT UUID
REM =========================================================

echo Resolving client UUID...

curl -s "%KC_URL%/admin/realms/%REALM%/clients?clientId=%CLIENT_ID_NAME%" -H "Authorization: Bearer %TOKEN%" -o client.json

type client.json

echo.
echo =========================================================
echo Keycloak configuration completed
echo =========================================================

pause