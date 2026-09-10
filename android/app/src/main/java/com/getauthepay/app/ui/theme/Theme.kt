package com.getauthepay.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandNavy,
    onPrimaryContainer = Color.White,
    secondary = BrandBlueMid,
    onSecondary = Color.White,
    tertiary = AccentTeal,
    onTertiary = Color.White,
    background = Color(0xFF0F141B),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF161D26),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1E2733),
    onSurfaceVariant = Color(0xFFB7C0CB),
    outline = Color(0xFF3A4350),
    error = DangerRed,
    onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueSoft,
    onPrimaryContainer = BrandNavy,
    secondary = BrandBlueMid,
    onSecondary = Color.White,
    tertiary = AccentTeal,
    onTertiary = Color.White,
    background = BackgroundNeutral,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    outline = DividerSubtle,
    error = DangerRed,
    onError = Color.White,
)

@Composable
fun AuthePayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuthePayTypography,
        content = content,
    )
}