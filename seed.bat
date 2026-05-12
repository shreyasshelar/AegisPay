
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