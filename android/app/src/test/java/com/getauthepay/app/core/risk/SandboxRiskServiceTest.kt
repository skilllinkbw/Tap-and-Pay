package com.getauthepay.app.core.risk

import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.RiskDecision
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Risk decision policy (directive section 26).
 *
 * The policy must fail toward caution without blindly declining legitimate
 * trade: low scores are allowed, mid scores are flagged, and only genuinely
 * high scores block. Every decision carries a score, reason codes and a
 * model version so it can be audited.
 */
class SandboxRiskServiceTest {

    private val service = SandboxRiskService()

    private fun context(
        lastHour: Int = 0,
        lastDay: Int = 0,
        declines: Int = 0,
        trust: Int = 100,
        newDevice: Boolean = false,
        overseas: Boolean = false,
        hour: Int = 12,
    ) = RiskContext(
        merchantId = "MID-1",
        terminalId = "TID-1",
        amount = Money.of(BigDecimal("100.00"), "BWP"),
        transactionCountLastHour = lastHour,
        transactionCountLastDay = lastDay,
        previousDeclinesLastHour = declines,
        deviceTrustScore = trust,
        newDevice = newDevice,
        isOverseas = overseas,
        timeOfDayLocalHour = hour,
    )

    @Test
    fun `ordinary low-risk payment is allowed`() = runTest {
        val d = service.evaluate(context())
        assertEquals(RiskDecision.Decision.ALLOW, d.decision)
        assertTrue(d.score < 30)
    }

    @Test
    fun `high velocity raises the score`() = runTest {
        val d = service.evaluate(context(lastHour = 25))
        assertTrue(d.reasonCodes.contains("VELOCITY_HIGH"))
    }

    @Test
    fun `daily velocity is detected`() = runTest {
        val d = service.evaluate(context(lastDay = 200))
        assertTrue(d.reasonCodes.contains("VELOCITY_DAILY"))
    }

    @Test
    fun `decline burst is detected`() = runTest {
        val d = service.evaluate(context(declines = 5))
        assertTrue(d.reasonCodes.contains("DECLINE_BURST"))
    }

    @Test
    fun `new device is flagged`() = runTest {
        val d = service.evaluate(context(newDevice = true))
        assertTrue(d.reasonCodes.contains("NEW_DEVICE"))
    }

    @Test
    fun `low device trust is flagged and can block`() = runTest {
        val d = service.evaluate(context(trust = 10, newDevice = true, declines = 4))
        assertTrue(d.reasonCodes.contains("DEVICE_LOW_TRUST"))
        assertEquals(RiskDecision.Decision.BLOCK, d.decision)
    }

    @Test
    fun `device trust exactly at the threshold is not auto-blocked`() = runTest {
        // trust == 20 is the boundary: the hard fail-closed block only triggers
        // below 20, so a borderline device with no other signals stays ALLOW.
        val d = service.evaluate(context(trust = 20))
        assertEquals(RiskDecision.Decision.ALLOW, d.decision)
        assertTrue(d.score < 30)
    }

    @Test
    fun `odd hours are flagged`() = runTest {
        val d = service.evaluate(context(hour = 2))
        assertTrue(d.reasonCodes.contains("ODD_HOURS"))
    }

    @Test
    fun `overseas context is flagged`() = runTest {
        val d = service.evaluate(context(overseas = true))
        assertTrue(d.reasonCodes.contains("OVERSEAS"))
    }

    @Test
    fun `accumulated signals produce a blocking decision`() = runTest {
        val d = service.evaluate(
            context(lastHour = 30, lastDay = 200, declines = 5, trust = 20, newDevice = true),
        )
        assertEquals(RiskDecision.Decision.BLOCK, d.decision)
        assertTrue(d.score > 70)
    }

    @Test
    fun `moderate signals produce a challenge rather than a block`() = runTest {
        val d = service.evaluate(context(newDevice = true, overseas = true))
        assertEquals(RiskDecision.Decision.CHALLENGE, d.decision)
        assertTrue(d.score in 30..70)
    }

    @Test
    fun `score is always within zero and one hundred`() = runTest {
        val d = service.evaluate(
            context(lastHour = 9999, lastDay = 9999, declines = 9999, trust = 0, newDevice = true),
        )
        assertTrue(d.score in 0..100)
    }

    @Test
    fun `every decision records the model version`() = runTest {
        val d = service.evaluate(context(), modelVersion = "rules.v2")
        assertEquals("rules.v2", d.modelVersion)
    }

    @Test
    fun `every decision is timestamped`() = runTest {
        val before = System.currentTimeMillis()
        val d = service.evaluate(context())
        assertTrue(d.timestampMs >= before)
    }

    // ---- context validation --------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `blank merchant id is rejected`() {
        context().copy(merchantId = " ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `device trust outside range is rejected`() {
        context().copy(deviceTrustScore = 101)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid hour is rejected`() {
        context().copy(timeOfDayLocalHour = 24)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative counters are rejected`() {
        context().copy(transactionCountLastHour = -1)
    }

    // ---- decision model ------------------------------------------------------

    @Test
    fun `decision factory helpers set the right labels`() {
        assertEquals(RiskDecision.Decision.ALLOW, RiskDecision.allow().decision)
        assertEquals(RiskDecision.Decision.CHALLENGE, RiskDecision.challenge("X", 50).decision)
        assertEquals(RiskDecision.Decision.BLOCK, RiskDecision.block("X", 90).decision)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `score outside range is rejected`() {
        RiskDecision(score = 101, decision = RiskDecision.Decision.ALLOW, modelVersion = "v1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank model version is rejected`() {
        RiskDecision(score = 1, decision = RiskDecision.Decision.ALLOW, modelVersion = " ")
    }
}
