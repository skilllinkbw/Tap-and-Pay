package com.getauthepay.app.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Merchant role permissions (directive section 29).
 *
 * These tests document the intended permission ladder. The client only hides
 * affordances; the authoritative check is performed server-side on every
 * sensitive action.
 */
class MerchantRoleTest {

    @Test
    fun `all five roles exist`() {
        assertEquals(
            setOf("OWNER", "ADMIN", "SUPERVISOR", "CASHIER", "AUDITOR"),
            MerchantRole.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `cashier can accept payments but nothing else`() {
        assertTrue(MerchantRole.CASHIER.canAcceptPayments())
        assertFalse(MerchantRole.CASHIER.canIssueRefunds())
        assertFalse(MerchantRole.CASHIER.canManageTeam())
        assertFalse(MerchantRole.CASHIER.canManageSettlement())
        assertFalse(MerchantRole.CASHIER.canRevokeDevice())
    }

    @Test
    fun `supervisor can pay and refund`() {
        assertTrue(MerchantRole.SUPERVISOR.canAcceptPayments())
        assertTrue(MerchantRole.SUPERVISOR.canIssueRefunds())
        assertFalse(MerchantRole.SUPERVISOR.canManageTeam())
    }

    @Test
    fun `admin manages merchant team and settlement`() {
        assertTrue(MerchantRole.ADMIN.canManageTeam())
        assertTrue(MerchantRole.ADMIN.canManageSettlement())
        assertTrue(MerchantRole.ADMIN.canRevokeDevice())
    }

    @Test
    fun `owner has full access`() {
        MerchantRole.OWNER.let {
            assertTrue(it.canAcceptPayments())
            assertTrue(it.canIssueRefunds())
            assertTrue(it.canManageTeam())
            assertTrue(it.canManageSettlement())
            assertTrue(it.canRevokeDevice())
            assertTrue(it.canViewAudit())
        }
    }

    @Test
    fun `auditor is read only and cannot move money`() {
        assertTrue(MerchantRole.AUDITOR.canViewAudit())
        assertFalse(MerchantRole.AUDITOR.canAcceptPayments())
        assertFalse(MerchantRole.AUDITOR.canIssueRefunds())
        assertFalse(MerchantRole.AUDITOR.canManageTeam())
    }

    @Test
    fun `permission ladder is monotonic`() {
        val order = listOf(
            MerchantRole.AUDITOR,
            MerchantRole.CASHIER,
            MerchantRole.SUPERVISOR,
            MerchantRole.ADMIN,
            MerchantRole.OWNER,
        )
        for (i in 0 until order.size - 1) {
            assertTrue(
                "${order[i]} must not outrank ${order[i + 1]}",
                order[i].level < order[i + 1].level,
            )
        }
    }

    @Test
    fun `fromWire parses server role names`() {
        assertEquals(MerchantRole.OWNER, MerchantRole.fromWire("OWNER"))
        assertEquals(MerchantRole.ADMIN, MerchantRole.fromWire("admin"))
        assertEquals(MerchantRole.CASHIER, MerchantRole.fromWire("CASHIER"))
        assertEquals(MerchantRole.AUDITOR, MerchantRole.fromWire("AUDITOR"))
    }

    @Test
    fun `fromWire defaults to the least privileged role`() {
        assertEquals(MerchantRole.CASHIER, MerchantRole.fromWire(null))
        assertEquals(MerchantRole.CASHIER, MerchantRole.fromWire("NONSENSE"))
    }

    // ---- merchant status ---------------------------------------------------

    @Test
    fun `only active merchants may accept payments`() {
        assertTrue(MerchantStatus.ACTIVE.canAcceptPayments())
        listOf(
            MerchantStatus.PENDING, MerchantStatus.UNDER_REVIEW,
            MerchantStatus.VERIFIED, MerchantStatus.SUSPENDED, MerchantStatus.REJECTED,
        ).forEach {
            assertFalse("$it must not accept payments", it.canAcceptPayments())
        }
    }

    @Test
    fun `rejected merchants cannot reach the dashboard`() {
        assertFalse(MerchantStatus.REJECTED.canAccessDashboard())
        assertTrue(MerchantStatus.PENDING.canAccessDashboard())
    }
}
