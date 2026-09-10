package com.getauthepay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getauthepay.app.BuildConfig
import com.getauthepay.app.ui.theme.SandboxAmber
import com.getauthepay.app.ui.theme.SandboxBackground

/**
 * Always-visible reminder when the running build is a sandbox. The
 * release build has SANDBOX_PAYMENTS=false so the banner is a no-op.
 */
@Composable
fun SandboxBanner(modifier: Modifier = Modifier) {
    if (!BuildConfig.SANDBOX_PAYMENTS) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SandboxBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Science,
                contentDescription = null,
                tint = SandboxAmber,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "SANDBOX • " + BuildConfig.ENVIRONMENT_NAME +
                    " • No real money is moved",
                color = Color(0xFF7A4F0F),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}