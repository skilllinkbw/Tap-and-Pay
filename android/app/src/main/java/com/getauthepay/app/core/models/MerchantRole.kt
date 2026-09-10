package com.getauthepay.app.core.models

/**
 * Merchant-facing permissions. Server-side enforcement is authoritative;
 * the client uses these to hide UI affordances it cannot honour.
 */
enum class MerchantRole(val displayName: String, val level: Int) {
    OWNER("Owner", 100),
    ADMIN("Administrator", 80),
    SUPERVISOR("Supervisor", 60),
    CASHIER("Cashier", 40),
    AUDITOR("Auditor", 20);

    fun canAcceptPayments(): Boolean = level >= CASHIER.level
    fun canIssueRefunds(): Boolean = level >= SUPERVISOR.level
    fun canManageTeam(): Boolean = level >= ADMIN.level
    fun canManageSettlement(): Boolean = level >= ADMIN.level
    fun canRevokeDevice(): Boolean = level >= ADMIN.level
    fun canViewAudit(): Boolean = level >= AUDITOR.level

    companion object {
        fun fromWire(raw: String?): MerchantRole = when (raw?.uppercase()) {
            "OWNER" -> OWNER
            "ADMIN" -> ADMIN
            "SUPERVISOR" -> SUPERVISOR
            "CASHIER" -> CASHIER
            "AUDITOR" -> AUDITOR
            else -> CASHIER
        }
    }
}