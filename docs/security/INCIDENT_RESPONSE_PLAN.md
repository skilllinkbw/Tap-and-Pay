# Tap & Pay — Incident Response Plan

**Owner:** Braincade Holdings Pty Ltd — Tap & Pay / AuthePay
**Scope:** merchant app, its backend integration points, and payment-provider
touchpoints. Companion runbook: [`android/docs/INCIDENT_RESPONSE.md`](../../android/docs/INCIDENT_RESPONSE.md).

## 1. Roles

| Role | Responsibility |
|---|---|
| Incident lead | Overall coordination, decisions, timeline |
| Security lead | Technical investigation, containment | 
| Engineering | Fixes, redeploys, evidence collection |
| Partner liaison | Bank / processor / mobile-money communication |
| Communications | Merchant and (if required) public communication |

Named individuals and on-call contacts are maintained internally —
**do not publish personal contacts in this repository.** The repository
placeholder `security@authepay.co.bw` is **pending confirmation**.

## 2. Detection

Sources: redacted audit/security logs (correlation IDs), provider alerts,
merchant reports, monitoring of error rates (`http.io_error`,
`engine.processor.exception`, `otp.verify.failed` bursts), device trust-score
anomalies.

## 3. Classification

| Severity | Examples | Response target |
|---|---|---|
| S1 Critical | Confirmed unauthorised money movement; backend credential leak; mass account takeover | Immediate, all-hands |
| S2 High | Double-charge reports; OTP bypass; security control bypass | Same business day |
| S3 Medium | Elevated fraud attempts; isolated device compromise | 2 business days |
| S4 Low | Suspicious but blocked activity | Backlog review |

## 4. Containment

- Revoke affected sessions/tokens server-side (backend) — client honors expiry.
- Suspend merchant accounts or devices implicated.
- If a signing key or secret is exposed: **rotate/revoke first**, then purge
  from history; the committed dev build-verification key must be replaced with
  the production key via CI secrets/local.properties, never committed.
- Disable the affected feature flag remotely where the backend supports it.

## 5. Investigation

- Preserve evidence: redacted log extracts, correlation IDs, transaction
  references (`ATX-…`), app version/build type, device trust signals.
- Reconstruct the timeline from audit events (`engine.payment.*`,
  `http.response`, `otp.*`, `security.*`).
- Determine blast radius: which merchants, transactions, and provider accounts.

## 6. Notification

- Notify the affected Payment Provider(s) per contract timelines.
- Personal-data breach: assess notification duty to the Information and Data
  Protection Commissioner (Botswana) and affected individuals under the Data
  Protection Act, 2018 — **legal counsel to confirm thresholds/timelines**.
- Merchants: clear, factual notice of what happened, whether money moved, and
  what to do.

## 7. Recovery

- Deploy fix via the release pipeline (unit tests + lint + release build must
  pass; see TEST_RESULTS).
- Verify containment (no further anomalous events) before restoring full service.
- Reconcile any affected transactions with the provider before closing.

## 8. Lessons learned

Within 10 business days of an S1/S2: post-incident review, root cause, control
gaps, and tracked remediation items feeding back into the
[Threat Model](THREAT_MODEL.md).

## 9. Double-charge report playbook (common case)

1. Pull both transaction references; compare idempotency keys + correlation IDs.
2. Client-side dedup (idempotency store/terminal mutex) prevents same-device
   duplicates; server dedup is authoritative — query the provider.
3. If the provider shows one authorisation: replay/explain. If two: initiate
   reversal per provider rules and escalate to S2.
