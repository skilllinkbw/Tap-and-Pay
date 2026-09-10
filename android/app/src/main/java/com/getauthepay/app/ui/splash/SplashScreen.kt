package com.getauthepay.app.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getauthepay.app.ui.components.BrandLogo
import com.getauthepay.app.ui.components.SandboxBanner
import com.getauthepay.app.ui.theme.BrandNavy
import com.getauthepay.app.ui.theme.TextOnBrand
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onContinue: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(900)
        onContinue()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandNavy),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLogo(showWordmark = true)
            Spacer(Modifier.height(16.dp))
            Text(
                "Secure payments. Built for merchants.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextOnBrand.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Braincade Holdings",
                style = MaterialTheme.typography.labelMedium,
                color = TextOnBrand.copy(alpha = 0.55f),
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            SandboxBanner(modifier = Modifier.padding(0.dp))
        }
    }
}