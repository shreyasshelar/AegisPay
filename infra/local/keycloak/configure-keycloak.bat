@echo off
setlocal EnableDelayedExpansion

set KC_URL=http://localhost:8180
set REALM=aegispay
set ADMIN_USER=admin
set ADMIN_PASS=admin

REM LAN_IP is inherited from start-aegispay.bat (set to machine's LAN IP for remote browser access)
REM Falls back to localhost if run standalone
IF "!LAN_IP!"=="" set LAN_IP=localhost

REM UUIDs must match the values seeded into aegispay_users / aegispay_ledger by start-aegispay.bat
set CUSTOMER_KC_UUID=59295e61-a284-40ed-8d3b-9e15bedeb040
set PAYEE_KC_UUID=3bf3e523-9de8-4254-9cc9-d5fa50ff8d4a

echo =========================================================
echo   Keycloak Post-Import Configuration
echo =========================================================

REM =========================================================
REM WAIT FOR ADMIN TOKEN
REM =========================================================

echo Waiting for Keycloak admin token...

:wait_token
curl -s -X POST "%KC_URL%/realms/master/protocol/openid-connect/token" ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "client_id=admin-cli" ^
  -d "username=%ADMIN_USER%" ^
  -d "password=%ADMIN_PASS%" ^
  -d "grant_type=password" ^
  -o token.json

findstr "access_token" token.json >nul
IF %ERRORLEVEL% NEQ 0 (
    echo   Keycloak not ready yet, retrying...
    timeout /t 3 >nul
    goto wait_token
)

REM Extract token (strip quotes and spaces)
for /f "tokens=2 delims=:," %%a in ('findstr "access_token" token.json') do set TOKEN=%%a
set TOKEN=!TOKEN:"=!
set TOKEN=!TOKEN: =!
echo Admin token obtained

REM =========================================================
REM 1. DECLARE CUSTOM USER PROFILE ATTRIBUTES
REM    Keycloak 24 Declarative User Profile blocks undeclared
REM    attributes.  Register aegispay_* attrs so they persist.
REM =========================================================

echo.
echo [1/4] Configuring User Profile...

curl -s -X PUT "%KC_URL%/admin/realms/%REALM%/users/profile" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"unmanagedAttributePolicy\":\"ADMIN_EDIT\",\"attributes\":[{\"name\":\"username\"},{\"name\":\"email\"},{\"name\":\"firstName\"},{\"name\":\"lastName\"},{\"name\":\"aegispay_user_id\",\"displayName\":\"AegisPay User ID\",\"permissions\":{\"view\":[\"admin\",\"user\"],\"edit\":[\"admin\"]}},{\"name\":\"aegispay_role\",\"displayName\":\"AegisPay Role\",\"permissions\":{\"view\":[\"admin\",\"user\"],\"edit\":[\"admin\"]}},{\"name\":\"aegispay_tenant_id\",\"displayName\":\"AegisPay Tenant ID\",\"permissions\":{\"view\":[\"admin\",\"user\"],\"edit\":[\"admin\"]}}]}" ^
  >nul 2>&1

echo   User Profile configured

REM =========================================================
REM 2. CREATE / UPDATE TEST USERS
REM    Uses PUT with the fixed UUID so re-runs are idempotent.
REM    Attributes must be set AFTER the profile declares them.
REM =========================================================

echo.
echo [2/4] Creating test users...

REM ── customer ──────────────────────────────────────────────
curl -s -X PUT "%KC_URL%/admin/realms/%REALM%/users/%CUSTOMER_KC_UUID%" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"id\":\"%CUSTOMER_KC_UUID%\",\"username\":\"customer\",\"email\":\"customer@aegispay.local\",\"firstName\":\"Test\",\"lastName\":\"Customer\",\"enabled\":true,\"emailVerified\":true,\"attributes\":{\"aegispay_user_id\":[\"%CUSTOMER_KC_UUID%\"],\"aegispay_role\":[\"CUSTOMER\"],\"aegispay_tenant_id\":[\"aegispay\"]},\"credentials\":[{\"type\":\"password\",\"value\":\"Test@1234\",\"temporary\":false}]}" ^
  -o nul -w "%%{http_code}" >nul 2>&1

REM If user doesn't exist (404 on PUT) create via POST instead
curl -s -X POST "%KC_URL%/admin/realms/%REALM%/users" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"id\":\"%CUSTOMER_KC_UUID%\",\"username\":\"customer\",\"email\":\"customer@aegispay.local\",\"firstName\":\"Test\",\"lastName\":\"Customer\",\"enabled\":true,\"emailVerified\":true,\"attributes\":{\"aegispay_user_id\":[\"%CUSTOMER_KC_UUID%\"],\"aegispay_role\":[\"CUSTOMER\"],\"aegispay_tenant_id\":[\"aegispay\"]},\"credentials\":[{\"type\":\"password\",\"value\":\"Test@1234\",\"temporary\":false}]}" ^
  >nul 2>&1

echo   customer@aegispay.local created/updated

REM ── payee ─────────────────────────────────────────────────
curl -s -X PUT "%KC_URL%/admin/realms/%REALM%/users/%PAYEE_KC_UUID%" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"id\":\"%PAYEE_KC_UUID%\",\"username\":\"payee\",\"email\":\"payee@aegispay.local\",\"firstName\":\"Test\",\"lastName\":\"Payee\",\"enabled\":true,\"emailVerified\":true,\"attributes\":{\"aegispay_user_id\":[\"%PAYEE_KC_UUID%\"],\"aegispay_role\":[\"CUSTOMER\"],\"aegispay_tenant_id\":[\"aegispay\"]},\"credentials\":[{\"type\":\"password\",\"value\":\"Test@1234\",\"temporary\":false}]}" ^
  >nul 2>&1

curl -s -X POST "%KC_URL%/admin/realms/%REALM%/users" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"id\":\"%PAYEE_KC_UUID%\",\"username\":\"payee\",\"email\":\"payee@aegispay.local\",\"firstName\":\"Test\",\"lastName\":\"Payee\",\"enabled\":true,\"emailVerified\":true,\"attributes\":{\"aegispay_user_id\":[\"%PAYEE_KC_UUID%\"],\"aegispay_role\":[\"CUSTOMER\"],\"aegispay_tenant_id\":[\"aegispay\"]},\"credentials\":[{\"type\":\"password\",\"value\":\"Test@1234\",\"temporary\":false}]}" ^
  >nul 2>&1

echo   payee@aegispay.local created/updated

REM =========================================================
REM 3. ASSIGN CUSTOMER REALM ROLE TO TEST USERS
REM    Fetch the role object first (need its id + name).
REM =========================================================

echo.
echo [3/4] Assigning CUSTOMER role...

curl -s "%KC_URL%/admin/realms/%REALM%/roles/CUSTOMER" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -o customer_role.json

REM Resolve actual Keycloak user IDs (may differ from the requested UUID)
curl -s "%KC_URL%/admin/realms/%REALM%/users?search=customer@aegispay.local&max=1" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -o cust_user.json

curl -s "%KC_URL%/admin/realms/%REALM%/users?search=payee@aegispay.local&max=1" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -o payee_user.json

REM Extract Keycloak-assigned user IDs
for /f "tokens=2 delims=:," %%a in ('findstr "\"id\"" cust_user.json') do (
    set CUST_ID=%%a
    set CUST_ID=!CUST_ID:"=!
    set CUST_ID=!CUST_ID: =!
    goto :got_cust_id
)
:got_cust_id

for /f "tokens=2 delims=:," %%a in ('findstr "\"id\"" payee_user.json') do (
    set PAYEE_ID=%%a
    set PAYEE_ID=!PAYEE_ID:"=!
    set PAYEE_ID=!PAYEE_ID: =!
    goto :got_payee_id
)
:got_payee_id

REM Wrap role in array and POST to role-mappings
for /f "tokens=*" %%r in ('type customer_role.json') do set ROLE_BODY=[%%r]

curl -s -X POST "%KC_URL%/admin/realms/%REALM%/users/!CUST_ID!/role-mappings/realm" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "!ROLE_BODY!" >nul 2>&1

curl -s -X POST "%KC_URL%/admin/realms/%REALM%/users/!PAYEE_ID!/role-mappings/realm" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "!ROLE_BODY!" >nul 2>&1

echo   CUSTOMER role assigned

REM =========================================================
REM 4. SET PASSWORDS (idempotent — reset even if already set)
REM =========================================================

echo.
echo [4/4] Setting passwords...

curl -s -X PUT "%KC_URL%/admin/realms/%REALM%/users/!CUST_ID!/reset-password" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"password\",\"value\":\"Test@1234\",\"temporary\":false}" >nul 2>&1

curl -s -X PUT "%KC_URL%/admin/realms/%REALM%/users/!PAYEE_ID!/reset-password" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"password\",\"value\":\"Test@1234\",\"temporary\":false}" >nul 2>&1

echo   Passwords set

REM =========================================================
REM 5. ADD LAN-IP REDIRECT URIs TO aegispay-web CLIENT
REM    Allows login from browsers on other devices on the LAN.
REM    Reads current redirectUris and appends the LAN IP entries.
REM =========================================================

echo.
echo [5/5] Registering LAN redirect URIs for http://!LAN_IP!:3000 ...

curl -s "%KC_URL%/admin/realms/%REALM%/clients?clientId=aegispay-web" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -o web_client.json

REM Extract the client's internal UUID
for /f "tokens=2 delims=:," %%a in ('findstr "\"id\"" web_client.json') do (
    set WEB_CLIENT_ID=%%a
    set WEB_CLIENT_ID=!WEB_CLIENT_ID:"=!
    set WEB_CLIENT_ID=!WEB_CLIENT_ID: =!
    goto :got_web_client
)
:got_web_client

REM Patch redirectUris and webOrigins to include the LAN IP
curl -s -X PUT "%KC_URL%/admin/realms/%REALM%/clients/!WEB_CLIENT_ID!" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"redirectUris\":[\"http://localhost:3000/*\",\"http://!LAN_IP!:3000/*\"],\"webOrigins\":[\"http://localhost:3000\",\"http://!LAN_IP!:3000\"]}" ^
  >nul 2>&1

echo   LAN redirect URIs registered (web)

REM ── Android: add LAN IP emulator redirect (10.0.2.2 = Android emulator localhost) ─
curl -s "%KC_URL%/admin/realms/%REALM%/clients?clientId=aegispay-android" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -o android_client.json
for /f "tokens=2 delims=:," %%a in ('findstr "\"id\"" android_client.json') do (
    set ANDROID_CLIENT_ID=%%a
    set ANDROID_CLIENT_ID=!ANDROID_CLIENT_ID:"=!
    set ANDROID_CLIENT_ID=!ANDROID_CLIENT_ID: =!
    goto :got_android_client
)
:got_android_client
curl -s -X PUT "%KC_URL%/admin/realms/%REALM%/clients/!ANDROID_CLIENT_ID!" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"redirectUris\":[\"com.aegispay.android://oauth/callback\",\"http://10.0.2.2:8080\",\"http://!LAN_IP!:8080\"],\"webOrigins\":[\"*\"]}" ^
  >nul 2>&1
echo   LAN redirect URIs registered (android)

REM ── iOS: add LAN IP redirect ─────────────────────────────────────────────────────
curl -s "%KC_URL%/admin/realms/%REALM%/clients?clientId=aegispay-ios" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -o ios_client.json
for /f "tokens=2 delims=:," %%a in ('findstr "\"id\"" ios_client.json') do (
    set IOS_CLIENT_ID=%%a
    set IOS_CLIENT_ID=!IOS_CLIENT_ID:"=!
    set IOS_CLIENT_ID=!IOS_CLIENT_ID: =!
    goto :got_ios_client
)
:got_ios_client
curl -s -X PUT "%KC_URL%/admin/realms/%REALM%/clients/!IOS_CLIENT_ID!" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"redirectUris\":[\"aegispay://oauth/callback\",\"https://aegispay.app/auth/callback\"],\"webOrigins\":[\"*\"]}" ^
  >nul 2>&1
echo   LAN redirect URIs registered (ios)

REM =========================================================
REM CLEANUP
REM =========================================================

del /q token.json client.json customer_role.json cust_user.json payee_user.json android_client.json ios_client.json web_client.json >nul 2>&1

echo.
echo =========================================================
echo   Keycloak configuration completed
echo   Test accounts:
echo     customer@aegispay.local / Test@1234
echo     payee@aegispay.local    / Test@1234
echo =========================================================
