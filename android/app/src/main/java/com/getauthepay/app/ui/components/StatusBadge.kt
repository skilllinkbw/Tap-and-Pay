package com.getauthepay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getauthepay.app.core.models.PaymentStatus as PS
import com.getauthepay.app.core.models.TransactionStatus
import com.getauthepay.app.ui.theme.DangerRed
import com.getauthepay.app.ui.theme.SuccessGreen
import com.getauthepay.app.ui.theme.WarningAmber

@Composable
fun StatusBadge(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        Tone.SUCCESS -> SuccessGreen
        Tone.WARNING -> WarningAmber
        Tone.DANGER -> DangerRed
        Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

enum class Tone { SUCCESS, WARNING, DANGER, NEUTRAL }

fun PaymentStatusTone(status: PS): Tone = when (status) {
    PS.APPROVED -> Tone.SUCCESS
    PS.PROCESSING, PS.AUTHORIZING, PS.READY_FOR_TAP,
    PS.CARD_DETECTED, PS.CREATED -> Tone.NEUTRAL
    PS.DECLINED -> Tone.DANGER
    PS.CANCELLED, PS.TIMEOUT, PS.FAILED -> Tone.WARNING
    PS.REVERSED -> Tone.DANGER
    PS.REFUNDED -> Tone.NEUTRAL
}

fun TransactionStatusTone(status: TransactionStatus): Tone = when (status) {
    TransactionStatus.APPROVED, TransactionStatus.REFUND_PENDING -> Tone.SUCCESS
    TransactionStatus.DECLINED, TransactionStatus.REVERSED -> Tone.DANGER
    TransactionStatus.PARTIALLY_REFUNDED -> Tone.WARNING
    TransactionStatus.NOT_COMPLETED -> Tone.NEUTRAL
    TransactionStatus.REFUNDED -> Tone.NEUTRAL
}