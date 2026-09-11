package com.getauthepay.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign

/**
 * Single 4-8 digit OTP field with a monospaced look and constrained
 * keyboard. The component is intentionally simple: the security model
 * relies on backend rate-limiting, attempt counters, and short TTLs,
 * not on UI gimmicks.
 */
@Composable
fun OtpInput(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    expectedLength: Int = 6,
) {
    var field by remember(value) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    OutlinedTextField(
        value = field,
        onValueChange = { tf ->
            val digits = tf.text.filter { it.isDigit() }.take(expectedLength)
            field = TextFieldValue(digits, TextRange(digits.length))
            onValueChange(digits)
        },
        label = { Text("One-time code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        ),
        supportingText = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    "${value.length} / $expectedLength digits",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
    )
}