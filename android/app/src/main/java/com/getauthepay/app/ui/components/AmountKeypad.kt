package com.getauthepay.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A 4x3 numeric keypad with a clear key. Emits updates to a host
 * String state.
 */
@Composable
fun AmountKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    fun append(token: String) {
        if (!enabled) return
        when (token) {
            "C" -> onValueChange("")
            "." -> if (!value.contains(".")) {
                onValueChange(if (value.isEmpty()) "0." else "$value.")
            }
            else -> onValueChange((value + token).take(12))
        }
    }
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "C"),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (key in row) {
                    Button(
                        onClick = { append(key) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.6f),
                        enabled = enabled,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}