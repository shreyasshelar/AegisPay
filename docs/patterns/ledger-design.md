# AegisPay — Immutable Ledger Design

The ledger is the financial source of truth. It is designed to be **append-only** and **mathematically correct** at all times.

---

## Double-Entry Bookkeeping

Every payment creates exactly two ledger entries:

```
Transaction: Payer sends ₹500 to Payee

DEBIT   payer_account  ₹500   (money leaves payer)
CREDIT  payee_account  ₹500   (money arrives at payee)

Net sum of all entries = ₹0  ← always true, always verified
```

This is the same principle used by every bank since 15th-century Florence. It makes fraud or data corruption mathematically detectable: `SUM(all_debits) ≠ SUM(all_credits)` means something went wrong.

---

## Balance Reservation (Two-Phase Update)

A payment does NOT immediately deduct from the sender. It follows a two-phase process:

```
Phase 1 — RESERVE (when Saga starts)
  available_balance  ₹50,000 → ₹49,500  (decremented)
  reserved_balance          ₹0 → ₹500   (incremented)
  ← funds are "on hold", not yet moved to payee

Phase 2a — COMMIT (when payment succeeds)
  reserved_balance   ₹500 → ₹0          (released)
  DEBIT entry written: payer -₹500
  CREDIT entry written: payee +₹500
  payee.available_balance += ₹500

Phase 2b — RELEASE (when payment fails)
  reserved_balance   ₹500 → ₹0          (released back to available)
  available_balance  ₹49,500 → ₹50,000  (restored)
  No ledger entries written (nothing moved)
```

**Why reserve?** If we deducted immediately and Stripe failed, we'd need to "un-deduct" — a complex reversal. Reservation avoids double-writing and makes the failure path clean.

---

## Schema

```sql
-- accounts: current balances (mutable)
CREATE TABLE accounts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL,
    currency           CHAR(3) NOT NULL,
    available_balance  DECIMAL(18,4) NOT NULL DEFAULT 0,
    reserved_balance   DECIMAL(18,4) NOT NULL DEFAULT 0,
    version            INTEGER NOT NULL DEFAULT 0,  -- optimistic locking
    CONSTRAINT uq_account_user_currency UNIQUE (user_id, currency),
    CONSTRAINT chk_balance_non_negative CHECK (available_balance >= 0),
    CONSTRAINT chk_reserved_non_negative CHECK (reserved_balance >= 0)
);

-- ledger_entries: immutable audit trail (append-only)
CREATE TABLE ledger_entries (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id     UUID NOT NULL REFERENCES accounts(id),
    transaction_id UUID NOT NULL,
    type           VARCHAR(6) NOT NULL CHECK (type IN ('DEBIT','CREDIT')),
    amount         DECIMAL(18,4) NOT NULL CHECK (amount > 0),
    currency       CHAR(3) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_ledger_tx_type UNIQUE (transaction_id, type)  -- idempotency
);
-- No UPDATE, no DELETE allowed on ledger_entries (enforced by application + RLS in prod)
```

---

## Optimistic Locking

The `version` column on `accounts` prevents concurrent balance updates from overwriting each other:

```sql
UPDATE accounts
SET available_balance = available_balance - 500,
    version = version + 1
WHERE user_id = ? AND version = ?  ← must match current version
```

If two concurrent requests both read `version=5`, the first UPDATE matches (`version=5 → 6`). The second UPDATE finds `version=6 ≠ 5` and updates 0 rows → `OptimisticLockingFailureException` → retry.

---

## Anti-Double-Spend Verification

Before processing any reservation:
```sql
SELECT available_balance FROM accounts WHERE user_id = ? FOR UPDATE;
-- FOR UPDATE: row-level lock prevents concurrent reservation reads
```

If `available_balance < amount` → reject with `INSUFFICIENT_FUNDS` before modifying anything.

---

## Balance Consistency Invariants

Monitored by PrometheusRule `BalanceNegative`:
```promql
min by (account_id) (ledger_account_available_balance) < 0
```

Fires immediately (no `for` clause) → critical alert to finance team. A negative balance means a code bug bypassed the reservation check — must be investigated immediately.
