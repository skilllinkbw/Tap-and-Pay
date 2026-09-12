package com.getauthepay.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getauthepay.app.R
import com.getauthepay.app.ui.theme.BrandBlue
import com.getauthepay.app.ui.theme.BrandNavy

/**
 * The official AuthePay mark. Reuses the launcher icon shipped with
 * the app so brand consistency is maintained across splash, login and
 * dashboard surfaces.
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    showWordmark: Boolean = true,
    sizeDp: Int = 64,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BrandNavy),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "AuthePay",
                modifier = Modifier.size((sizeDp * 0.7).dp),
            )
        }
        if (showWordmark) {
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "AuthePay",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = BrandNavy,
                )
                Text(
                    "Tap & Pay",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandBlue,
                )
            }
        }
    }
}