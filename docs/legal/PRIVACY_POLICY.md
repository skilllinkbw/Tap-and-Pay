# Tap & Pay — Privacy Policy

**Product:** Tap & Pay, a Braincade Holdings Pty Ltd product ("Tap & Pay", "we", "us")
**Applies to:** the Tap & Pay Android merchant application and supporting services
**Status:** DRAFT — pending final legal review before commercial launch.
**Framework considered:** Data Protection Act, 2018 (Botswana) and other laws
applicable to the final deployment. This document is a product-accurate draft,
not a legal certification of compliance.

---

## 1. Who we are

Tap & Pay is a contactless payment acceptance application operated by
**Braincade Holdings Pty Ltd** (Botswana). For privacy inquiries, contact us
via the in-app *Settings → Help / About* channel. A dedicated privacy contact
address is **pending confirmation before publication** — do not rely on any
address not shown in the published in-app policy.

## 2. Information we collect

### 2.1 Account information
- Merchant identifier used to sign in (phone number or email address)
- Merchant business profile supplied during onboarding (business name, type,
  country, contact details)
- Role of the signed-in user (owner / manager / cashier)

### 2.2 Transaction information
- Amount, currency, merchant reference, description
- Transaction status and timestamps
- Transaction reference (`ATX-…`), authorisation code and processor reference
  where supplied by the payment provider
- **Coarse card metadata only:** card brand label and masked PAN
  (e.g. `412345******1234`). The app **never** sees, stores or transmits the
  full card number, CVV, PIN or track data.

### 2.3 Device and technical information
- Device trust signals (e.g. root/emulator indicators) used by the risk engine
- Terminal identifier derived from the merchant account
- App version, build environment (SANDBOX / STAGING / PRODUCTION)

### 2.4 NFC and QR information
- **NFC:** used only to initiate a payment read at the merchant's request.
  No cardholder data is persisted from NFC interactions.
- **QR/camera:** used only to scan payment, reference, merchant, receipt and
  onboarding QR codes. Images are processed on-device; they are not uploaded.

### 2.5 Logs and security data
- Structured audit events (authentication, payment lifecycle, security events)
  correlated by transaction/correlation IDs. Logs are redacted by design:
  no PAN, CVV, PIN, OTP, tokens or private keys are written
  (enforced by automated tests).

### 2.6 Support information
- Information you choose to send when contacting support.

## 3. Why we collect it (purposes)

- Authenticating merchants and operating their accounts
- Initiating, processing, recording and reconciling payments
- Fraud prevention and risk scoring
- Security monitoring and incident investigation
- Product support and service improvement
- Meeting legal, regulatory, tax and contractual obligations

Where the Data Protection Act, 2018 (Botswana) applies, processing is based on
one or more of: performance of a contract, legal obligation, legitimate
interests (security and fraud prevention), and consent where required. The
exact lawful-basis mapping is **pending final legal review**.

## 4. Data minimisation

We collect only what is needed to accept and reconcile a payment. Notably:
- No cardholder data is ever constructed, stored or transmitted by the app.
- Session tokens and identifiers are kept in Android Keystore-backed encrypted
  storage; nothing sensitive is written to plaintext preferences, caches or logs.
- Screen capture is blocked on sensitive screens; backups are disabled.

## 5. Retention and deletion

Retention categories are defined in
[DATA_RETENTION_POLICY.md](DATA_RETENTION_POLICY.md). Where a retention period
depends on banking, tax, AML/CFT or contractual requirements, the exact period
is **to be confirmed with the applicable professional/regulatory authority**
before launch; this policy does not invent statutory periods.

You may request access, correction or deletion of your personal information via
the in-app support channel. Some records (e.g. completed transaction records)
may need to be retained where required by law or our payment partners.

## 6. Security

Controls are summarised in
[../security/SECURITY_ARCHITECTURE.md](../security/SECURITY_ARCHITECTURE.md):
HTTPS-only transport, Android Keystore-backed encryption, biometric options,
device integrity checks, redacted logging, least-privilege permissions.

## 7. Third-party providers

Payments are processed by banks, payment processors and/or mobile-money
providers engaged by Braincade Holdings Pty Ltd. **Specific provider
integrations are pending partner approval**; this policy will name them once
contracted. No payment credentials are shared with analytics or advertising
parties — the app ships **no third-party analytics or advertising SDKs**.

## 8. International transfers

If data is processed or stored outside Botswana (e.g. by a payment provider),
transfers will be assessed for adequacy/safeguards as required by applicable
law. **Details pending provider contracts — to be completed before launch.**

## 9. Children's data

Tap & Pay is a merchant business tool. It is not directed at children and we do
not knowingly collect children's personal information.

## 10. Your rights

Subject to applicable law (including the Data Protection Act, 2018), you may
have rights to access, correct, delete, or object to processing of your
personal information, and to lodge a complaint with the Information and Data
Protection Commissioner (Botswana). Exercise rights via the in-app support
channel.

## 11. Changes to this policy

Material changes will be communicated in-app and the "last updated" date
revised. **Last updated: 2026-09-21 (draft).**

---

*This document was prepared from the actual product implementation. It requires
review by a qualified legal professional in Botswana before publication.*
