# Tap & Pay — Partner Brief

**Braincade Holdings Pty Ltd** · Tap & Pay / AuthePay · Botswana

Tap & Pay is a merchant contactless-acceptance application (NFC card tap + customer QR) built for
Botswana and the wider African market. This brief is the entry point for banks, mobile-money
operators, payment processors and technology partners evaluating the platform.

## What Tap & Pay does

Merchants sign in on an Android device, enter an amount, and take a payment by tapping the
customer's contactless card on the device (NFC reader mode) or by scanning the customer's
payment QR code. Every payment runs through a 12-state server-authoritative state machine
(`READY_FOR_TAP → CARD_DETECTED → … → AUTHORIZING → APPROVED / DECLINED`), produces a receipt
with a masked PAN, and lands in the merchant's transaction history and settlement view.

## Who it is for

Micro- and small merchants, pop-up traders, and branch/agent networks that need card and QR
acceptance without dedicated terminal hardware. Multi-user roles (Admin / Supervisor / Cashier /
Auditor) and device enrolment/revocation are built in.

## How the system works (integration model)

```
Android merchant app  →  Secure API (TLS 1.2+, bearer auth)  →  Payment orchestration
      →  Payment provider adapters (AuthePay / bank / mobile-money / sandbox)
      →  Ledger & transaction records  →  Receipts, notifications, audit log
```

- The **`PaymentProvider` interface** is the single integration seam. New rails are added as
  adapters — no app rewrite.
- The app never decides the final transaction state; the provider/backend response is
  authoritative.
- Idempotency keys and duplicate-tap protection are enforced at the engine level.

## Security approach (summary)

- Android Keystore-backed session and PIN storage; no plaintext credentials.
- `FLAG_SECURE` on sensitive screens; screenshot/recording protection.
- Tokenization boundary: no PAN/CVV/PIN/OTP storage in the app; receipts show masked PAN only.
- Device integrity checks (root/debug detection) gate payment initiation.
- Structured, secret-free audit events for every financial action.
- Full detail: `SECURITY_ARCHITECTURE.md`, `THREAT_MODEL.md`, `PAYMENT_DATA_FLOW.md`.

## Honest status (read before a demo)

| Capability | Status |
|---|---|
| Merchant app: onboarding, login/OTP, accept payment, refunds, history, settlements, devices, team, alerts | **Working** (sandbox rail) |
| Payment state machine, idempotency, receipts, audit trail | **Working** |
| Sandbox payment rail | **Working** — clearly labelled "SANDBOX — no real money is moved" |
| AuthePay production API | **Reserved host** — awaiting provider contract + credentials |
| Bank / mobile-money rails | **Adapter boundaries only** — requires partner API credentials |
| Certified SoftPOS / contactless *acquiring* | **Not implemented** — requires certified partner SDK and scheme certification |

Tap & Pay does **not** claim PCI certification, regulatory approval, bank partnership or live
money movement. Sandbox transactions are always labelled. The NFC reader implements payment
*initiation* (card reading for authorization via the provider), not card-emulation SoftPOS.

## What a partner engagement unlocks

1. **Bank / processor:** supply API credentials + contract → `BankProvider`/`AuthePayProvider`
   adapter goes live against the real authorization endpoint.
2. **Mobile-money operator:** supply API credentials → `MobileMoneyProvider` adapter (plus
   tokenization service) goes live.
3. **Scheme/SoftPOS vendor:** certified SDK plugs into the isolated acceptance layer.

## Contact

Braincade Holdings Pty Ltd — Tap & Pay / AuthePay team.
See `BANK_DEMO_CHECKLIST.md` for the recommended live-demo script and
`BANK_SUBMISSION_READINESS.md` for the submission evidence pack.
