package com.ledgerengine.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = LedgerBlue40,
    secondary = LedgerGreen40,
    error = LedgerRed40,
    background = Neutral99,
    surface = Neutral99,
    onBackground = Neutral10,
    onSurface = Neutral10,
)

private val DarkColors = darkColorScheme(
    primary = LedgerBlue80,
    secondary = LedgerGreen80,
    error = LedgerRed80,
    background = Neutral10,
    surface = Neutral20,
    onBackground = Neutral90,
    onSurface = Neutral90,
)

@Composable
fun LedgerEngineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LedgerTypography,
        content = content,
    )
}
