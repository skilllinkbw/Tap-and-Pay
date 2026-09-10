package com.getauthepay.app.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getauthepay.app.core.models.MerchantRole
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.SectionHeader
import com.getauthepay.app.ui.theme.BrandBlue
import com.getauthepay.app.ui.theme.SuccessGreen

/**
 * Team and role management.
 *
 * The member list is server-authoritative. Until the AuthePay backend
 * exposes a team endpoint this screen renders an honest empty state — it
 * does not invent sample colleagues.
 *
 * The permission matrix below is derived from [MerchantRole] itself, so it
 * can never drift from the rules the app enforces. Note the standing rule
 * from directive section 29: these client-side checks only hide UI the
 * server would reject anyway — authorisation is enforced server-side.
 */
@Composable
fun TeamScreen(onBack: () -> Unit) {
    var showMatrix by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Team",
        subtitle = "Roles and permissions",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SectionHeader("Team members")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "No team members to show",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Team management is handled by the AuthePay merchant service. " +
                            "Once members are added to your merchant account they will " +
                            "appear here with their role.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { showMatrix = !showMatrix }) {
                        Text(if (showMatrix) "Hide role permissions" else "View role permissions")
                    }
                }
            }

            if (showMatrix) {
                SectionHeader("Role permissions")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        PermissionMatrix()
                    }
                }
            }

            SectionHeader("Roles")
            MerchantRole.entries.forEach { role ->
                RoleCard(role = role)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Permissions shown here are for guidance. Every sensitive action " +
                        "is authorised again by the AuthePay server before it is " +
                        "applied, so a modified client cannot grant itself access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoleCard(role: MerchantRole) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = BrandBlue.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    role.name.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(role.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    roleSummary(role),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionMatrix() {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(
                "Permission",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.4f),
            )
            MerchantRole.entries.forEach { role ->
                Text(
                    role.name.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
        HorizontalDivider()
        PermissionRow("Accept payments") { it.canAcceptPayments() }
        PermissionRow("Issue refunds") { it.canIssueRefunds() }
        PermissionRow("Manage team") { it.canManageTeam() }
        PermissionRow("Manage settlement") { it.canManageSettlement() }
        PermissionRow("Revoke device") { it.canRevokeDevice() }
        PermissionRow("View audit") { it.canViewAudit() }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    check: (MerchantRole) -> Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.4f),
        )
        MerchantRole.entries.forEach { role ->
            val allowed = check(role)
            Icon(
                imageVector = if (allowed) Icons.Outlined.Check else Icons.Outlined.Close,
                contentDescription = if (allowed) "$label allowed" else "$label not allowed",
                tint = if (allowed) SuccessGreen
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .weight(0.6f)
                    .height(16.dp)
                    .width(16.dp),
            )
        }
    }
}

private fun roleSummary(role: MerchantRole): String = when (role) {
    MerchantRole.OWNER -> "Full access to the merchant account"
    MerchantRole.ADMIN -> "Merchant, team and device management"
    MerchantRole.SUPERVISOR -> "Payments and refunds"
    MerchantRole.CASHIER -> "Accept payments only"
    MerchantRole.AUDITOR -> "Read-only transaction and audit access"
}
