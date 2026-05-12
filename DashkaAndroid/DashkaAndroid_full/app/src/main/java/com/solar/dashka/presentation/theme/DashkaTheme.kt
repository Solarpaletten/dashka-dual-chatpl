package com.solar.dashka.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DashkaLightColors = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = OrangeOnPrimary,
    primaryContainer = OrangePrimaryContainer,
    onPrimaryContainer = OrangeOnPrimaryContainer,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnSurfaceLight,
    error = ErrorLight,
)

private val DashkaDarkColors = darkColorScheme(
    primary = OrangePrimary,
    onPrimary = OrangeOnPrimary,
    primaryContainer = OrangePrimaryContainer,
    onPrimaryContainer = OrangeOnPrimaryContainer,
    surface = NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    error = ErrorDark,
)

@Composable
fun DashkaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DashkaDarkColors else DashkaLightColors
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
