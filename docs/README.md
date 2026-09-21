# Tap & Pay — Documentation

**Product:** Tap & Pay (AuthePay merchant acceptance app)
**Company:** Braincade Holdings Pty Ltd
**Platform:** Android (`com.getauthepay.app`), Kotlin + Jetpack Compose

This directory is the commercial, legal and partner-facing documentation set.
Deep technical documentation lives in [`android/docs/`](../android/docs/).

## Legal

| Document | Purpose |
|---|---|
| [legal/PRIVACY_POLICY.md](legal/PRIVACY_POLICY.md) | What data Tap & Pay collects and why (draft — pending legal review) |
| [legal/TERMS_AND_CONDITIONS.md](legal/TERMS_AND_CONDITIONS.md) | Merchant/customer terms of service (draft — pending legal review) |
| [legal/ACCEPTABLE_USE_POLICY.md](legal/ACCEPTABLE_USE_POLICY.md) | Prohibited activity on the platform |
| [legal/DATA_RETENTION_POLICY.md](legal/DATA_RETENTION_POLICY.md) | Retention categories and disposal (periods pending regulatory confirmation) |

## Security

| Document | Purpose |
|---|---|
| [security/SECURITY_ARCHITECTURE.md](security/SECURITY_ARCHITECTURE.md) | Security controls, layered architecture |
| [security/THREAT_MODEL.md](security/THREAT_MODEL.md) | Threats, controls, residual risk |
| [security/INCIDENT_RESPONSE_PLAN.md](security/INCIDENT_RESPONSE_PLAN.md) | Detection → containment → recovery |

## Bank / partner

| Document | Purpose |
|---|---|
| [bank/BANK_PARTNERSHIP_BRIEF.md](bank/BANK_PARTNERSHIP_BRIEF.md) | Executive brief for banks and payment partners |
| [bank/BANK_DUE_DILIGENCE_CHECKLIST.md](bank/BANK_DUE_DILIGENCE_CHECKLIST.md) | Due-diligence evidence checklist |

## Product

| Document | Purpose |
|---|---|
| [PRODUCT_READINESS.md](PRODUCT_READINESS.md) | WORKING vs SANDBOX vs PENDING PARTNER vs PENDING CERTIFICATION vs FUTURE |

## Technical (in `android/docs/`)

Architecture, API contract, payment data flow, NFC architecture, threat model
detail, test plan/results, deployment guide, certification readiness, bank demo
script — see [`android/docs/`](../android/docs/).

---

**Honesty contract:** nothing in this documentation set claims bank approval,
regulatory certification, PCI DSS compliance, or live payment processing.
Where external approval is required it is marked
**"Pending external certification/partner approval."**
