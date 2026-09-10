package com.getauthepay.app.core.models

/**
 * Deterministic outcome scenarios the sandbox payment provider can
 * produce. The field lives on [PaymentRequest.sandboxScenario] (nullable)
 * so production providers can ignore it without coupling to the
 * simulator. Real payment flows MUST NEVER honour this field.
 *
 *   TEST_APPROVED         — happy-path authorisation
 *   TEST_DECLINED         — issuer declines (DO_NOT_HONOR)
 *   TEST_TIMEOUT          — processor does not respond in time
 *   TEST_CANCELLED        — merchant cancelled
 *   TEST_DUPLICATE        — idempotency collision (DUPLICATE_TRANSACTION)
 *   TEST_NETWORK_FAILURE  — could not reach the processor
 *   TEST_PROCESSOR_ERROR  — processor returned 5xx
 *   TEST_RISK_DECLINE     — risk engine blocked
 */
enum class TestScenario {
    TEST_APPROVED,
    TEST_DECLINED,
    TEST_TIMEOUT,
    TEST_CANCELLED,
    TEST_DUPLICATE,
    TEST_NETWORK_FAILURE,
    TEST_PROCESSOR_ERROR,
    TEST_RISK_DECLINE,
}