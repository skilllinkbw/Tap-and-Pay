# Tap & Pay — Terms and Conditions

**Product:** Tap & Pay, a Braincade Holdings Pty Ltd product
**Status:** DRAFT — pending final legal review before commercial launch.

---

## 1. Acceptance

By creating an account or using Tap & Pay ("the Service") you agree to these
Terms. If you use the Service on behalf of a business, you confirm you are
authorised to bind that business.

## 2. The Service

Tap & Pay is a merchant application for initiating and recording contactless
(NFC) and QR-based payment acceptance. Tap & Pay is **not** a bank and does not
itself hold customer funds. Actual movement of money is performed by licensed
banks, payment processors and/or mobile-money providers ("Payment Providers").

**Integration status:** live payment rails require executed agreements with
Payment Providers — *pending external partner approval*. Sandbox/demo builds
simulate provider responses and are clearly labelled; simulated transactions
move no money.

## 3. Eligibility and account responsibilities

- You must be a legitimate business authorised to accept payments.
- You must provide accurate onboarding information and keep it current.
- You are responsible for all activity under your account and devices.
- Keep your device secure; never share OTP codes. AuthePay staff will never
  ask for your OTP, PIN or biometric credentials.

## 4. Merchant responsibilities

- Enter the correct amount before presenting the device to a customer.
- Verify the on-screen result before treating a sale as complete. Only a status
  of **APPROVED** (confirmed by the provider in production) means money was
  authorised. PENDING/FAILED/CANCELLED/TIMEOUT outcomes are not sales.
- Honour valid refund requests and cooperate with dispute investigations.
- Comply with card-scheme, mobile-money and acquirer rules once live.

## 5. Payments, authorisation and integrity

- Transaction status reported by the Payment Provider is authoritative; the
  app's local history is a record of what the provider reported.
- The Service applies duplicate-submission protection (idempotency keys and
  single-flight processing) but you must still avoid presenting the device for
  two simultaneous sales.
- Failed, declined, cancelled or timed-out attempts move no money.

## 6. Reversals, refunds and disputes

- Approved transactions may be reversed or refunded per Payment Provider rules.
- Disputes and chargebacks (where applicable) are handled under the Payment
  Provider's and scheme's rules; you must respond with requested evidence
  (receipts, references, audit trail) promptly.

## 7. Prohibited use

You must not use the Service for anything listed in the
[Acceptable Use Policy](ACCEPTABLE_USE_POLICY.md), including fraud, money
laundering, unauthorised transactions, or circumventing security controls.

## 8. Fees

Any fees for the Service will be presented and agreed before they apply.
This document does not itself establish a fee schedule — **pricing is
published separately once commercial terms are finalised.**

## 9. Service availability

The Service is provided "as is" and "as available". Payment initiation requires
network connectivity to the Payment Provider; outages of the device, network or
provider may prevent acceptance. We do not guarantee uninterrupted service.

## 10. Third-party providers

Payment processing, SMS/OTP delivery and settlement are performed by third
parties under their own terms. We are not responsible for third-party failures,
but we will assist in investigation via transaction/correlation references.

## 11. Intellectual property

Tap & Pay, AuthePay, the "A" monogram and associated branding are the property
of Braincade Holdings Pty Ltd. You receive a limited, non-transferable licence
to use the app for its intended purpose.

## 12. Limitation of liability

To the maximum extent permitted by applicable law, Braincade Holdings Pty Ltd
is not liable for indirect or consequential losses, lost profits, or losses
arising from third-party payment rails. Nothing in these Terms excludes
liability that cannot be excluded by law.

## 13. Suspension and termination

We may suspend or terminate access for breach of these Terms, suspected fraud,
security risk, or as required by law or a Payment Provider. You may stop using
the Service at any time; records retention follows the
[Data Retention Policy](DATA_RETENTION_POLICY.md).

## 14. Changes

We may update these Terms; material changes will be communicated in-app before
taking effect.

## 15. Governing law

These Terms are governed by the laws of **Botswana**, unless a different
governing law is agreed in a Payment Provider or enterprise agreement.

## 16. Contact

Via the in-app *Settings → Help / About* channel. A dedicated legal contact
address is **pending confirmation before publication**.

---

*Draft prepared from the actual product behaviour. Requires review by a
qualified legal professional in Botswana before commercial launch.*
