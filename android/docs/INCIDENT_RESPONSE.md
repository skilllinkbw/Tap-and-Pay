# AuthePay — Incident Response

Procedures for the merchant-app surface. Backend/processor incidents are owned by
the AuthePay operations team; this guide covers on-device response.

## 1. Triage Contacts

- Security lead (Braincade Holdings): security@authepay.co.bw (placeholder)
- On-call backend: via AuthePay ops rotation
- Acquirer/processor liaison: per partner agreement

## 2. Scenarios

### 2.1 Suspected compromised merchant device
1. `DeviceSecurityChecker` signals root/emulator/adb → risk engine raises score;
   trust < 20 ⇒ hard `BLOCK` (`DEVICE_UNTRUSTED`).
2. Operator revokes device via `Dashboard → Devices → Revoke` (`v1/devices/$id`).
3. `SecurityManager.wipe()` removes the Keystore key on logout/revoke.
4. Force session end (`SessionManager.end()`).

### 2.2 Suspicious login / OTP abuse
1. Alerts raised server-side (`SUSPICIOUS_LOGIN`, `FAILED_OTP`, `NEW_DEVICE`).
2. Acknowledge via `SecurityAlertsScreen` → `acknowledgeAlert` (server-side audit).
3. If confirmed compromise: revoke device + reset merchant credentials server-side.

### 2.3 Refund abuse
1. Refunds require `SUPERVISOR+` + biometric (`BiometricGate`).
2. `REFUND_ACTIVITY` alerts surface unusual patterns.
3. Server reconciles; client cannot fabricate refund status.

### 2.4 Suspected cleartext / MITM
- `HttpClient` rejects non-https base URL; release `ALLOW_CLEARTEXT=false`.
- If observed, treat as config/transport breach; verify `network_security_config.xml`
  and `buildConfigField ALLOW_CLEARTEXT`.

### 2.5 Double-charge report
- `IdempotencyStore` + server dedup prevent it; replay cached terminal result.
- Investigate via `correlationId` (`X-AuthePay-Correlation-Id`) across client+server.

## 3. Containment Checklist

- [ ] Identify affected merchant id + terminal id + device id
- [ ] Revoke device + end session
- [ ] Pull `Transaction` history (masked) + audit events for the window
- [ ] Confirm no CHD was involved (app stores none by design)
- [ ] Notify acquirer if any live transaction is in doubt
- [ ] Post-incident: update `RiskService` rules / attestation policy server-side

## 4. Post-Incident

- Record in central audit; client alerts are acked server-side so the trail is
  tamper-evident.
- Review `THREAT_MODEL.md` items (esp. T4 attestation, T11 kernel).

See `SECURITY_ARCHITECTURE.md`, `DATA_RETENTION.md`, `THREAT_MODEL.md`.
