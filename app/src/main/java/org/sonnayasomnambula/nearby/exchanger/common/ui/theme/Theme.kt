package org.sonnayasomnambula.nearby.exchanger.common.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkActiveButton,
    surface = DarkCard,
    error = DarkRedButton,
    background = Color.Black,
    onSecondary = DarkHighlightedText
)

private val LightColorScheme = lightColorScheme(
    primary = LightActiveButton,
    surface = LightCard,
    surfaceTint = Color.Red,
    background = Color.White,
    onSecondary = LightHighlightedText
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}