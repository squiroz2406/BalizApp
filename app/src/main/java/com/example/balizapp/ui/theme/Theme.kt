package com.example.balizapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Amber40,
    onPrimary = OnAmber40,
    primaryContainer = AmberContainer90,
    onPrimaryContainer = OnAmberContainer10,
    secondary = Navy40,
    secondaryContainer = NavyContainer90,
    onSecondaryContainer = OnNavyContainer10,
    tertiary = Teal40,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = Amber80,
    onPrimary = OnAmber20,
    primaryContainer = AmberContainer30,
    onPrimaryContainer = OnAmberContainer90,
    secondary = Navy80,
    secondaryContainer = NavyContainer30,
    onSecondaryContainer = OnNavyContainer90,
    tertiary = Teal80,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
)

@Composable
fun BalizAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Apagado por defecto para que la app mantenga su identidad visual en todos los teléfonos.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
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
        content = content,
    )
}
