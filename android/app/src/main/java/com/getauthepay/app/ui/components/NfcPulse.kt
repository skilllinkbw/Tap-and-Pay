package com.getauthepay.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getauthepay.app.ui.theme.BrandBlue

/**
 * A radial pulse used to signal the NFC reader is active. Three
 * concentric rings animate outward from the centre to convey readiness.
 */
@Composable
fun NfcPulse(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 200.dp,
    tint: Color = BrandBlue,
) {
    val transition = rememberInfiniteTransition(label = "nfc-pulse")
    val phases = (0 until 3).map { idx ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, delayMillis = idx * 350),
                repeatMode = RepeatMode.Restart,
            ),
            label = "phase-$idx",
        )
    }
    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f
            phases.forEach { anim ->
                val r = anim.value * maxRadius
                drawCircle(
                    color = tint.copy(alpha = (1f - anim.value).coerceAtLeast(0f) * 0.6f),
                    radius = r,
                    center = centre,
                    style = Stroke(width = 4f),
                )
            }
            // Static inner ring.
            drawCircle(
                color = tint,
                radius = maxRadius * 0.18f,
                center = centre,
                style = Stroke(width = 6f),
            )
        }
    }
}