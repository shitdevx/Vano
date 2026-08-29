package com.vamora.vano.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = VanoGreen,
    onPrimary = VanoBackground,
    primaryContainer = VanoGreenContainer,
    onPrimaryContainer = VanoOnGreenContainer,
    secondary = VanoGreenLight,
    tertiary = VanoGreenDark,
    background = VanoBackground,
    surface = VanoSurface,
    surfaceVariant = VanoSurfaceVariant,
    onBackground = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.White,
    outline = VanoOutline
)

private val LightColorScheme = lightColorScheme(
    primary = VanoGreenDark,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = VanoOnGreenContainer,
    onPrimaryContainer = VanoGreenContainer,
    secondary = VanoGreen,
    tertiary = VanoGreenDark,
    background = androidx.compose.ui.graphics.Color(0xFFF6FFF8),
    surface = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color(0xFF0A0F0A),
    onSurface = androidx.compose.ui.graphics.Color(0xFF0A0F0A),
)

@Composable
fun VanoTheme(
    darkTheme: Boolean = true,
    // Disable dynamic color to keep green accent consistent
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}