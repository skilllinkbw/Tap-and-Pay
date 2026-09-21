# Tap & Pay — Data Retention Policy

**Product:** Tap & Pay, a Braincade Holdings Pty Ltd product
**Status:** DRAFT. Retention periods that depend on banking, tax, AML/CFT or
contractual requirements are marked **[CONFIRM]** and must be fixed with the
applicable professional/regulatory authority before launch. No statutory
periods are asserted here.

## Principles

- Data minimisation first: the app stores **no cardholder data** (no full PAN,
  CVV, PIN, track data) at any time.
- On-device sensitive state (session token, identifiers) lives only in
  Android Keystore-backed encrypted storage and is wiped on logout.
- Backups are disabled (`allowBackup=false`); logs are redacted by design.

## Retention categories

| Category | Examples | Where held | Retention |
|---|---|---|---|
| Account data | Merchant profile, contact details, roles | Backend (pending provider) / encrypted on-device session | Life of the account + **[CONFIRM]** post-closure period |
| Transaction records | Amount, currency, status, `ATX-` reference, auth code, processor reference, masked PAN, brand label | Backend ledger; on-device in-memory ledger for the running session | Per banking/tax/AML requirement — **[CONFIRM]** (commonly multi-year; do not assert until confirmed) |
| Audit logs | Authentication events, payment lifecycle events, security events, correlation IDs | Backend; redacted on-device diagnostics | **[CONFIRM]** with provider/regulator |
| Security logs | Device trust signals, lockouts, integrity events | Backend / on-device | **[CONFIRM]**; on-device security state cleared on logout/wipe |
| Support records | Correspondence via in-app support channel | Support system (pending) | Duration of the support relationship + **[CONFIRM]** |
| Analytics | None — the app ships no analytics SDK | — | Not applicable |
| Backups | Platform backups | Disabled on-device; backend backup policy **[CONFIRM]** with provider | Per backend backup schedule **[CONFIRM]** |

## Deletion and correction

- Account deletion requests: via in-app support; completed except where
  retention is legally/contractually required (e.g. transaction records).
- On logout, the app wipes the session token and Keystore key material.
- Transaction records that must be retained are kept in masked/tokenised form
  only — never with cardholder data.

## Review

This policy is reviewed at least annually and whenever a Payment Provider
contract or regulatory requirement changes.
