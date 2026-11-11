package com.example.ui.theme

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

private val CinemaColorScheme = darkColorScheme(
    primary = CinemaAmber,
    onPrimary = CinemaBlack,
    primaryContainer = CinemaElevatedSurface,
    onPrimaryContainer = CinemaAmberLight,
    secondary = CinemaCyan,
    onSecondary = CinemaBlack,
    secondaryContainer = CinemaCardSurface,
    onSecondaryContainer = CinemaCyan,
    tertiary = CinemaRed,
    onTertiary = Color.White,
    background = CinemaBlack,
    onBackground = CinemaTextPrimary,
    surface = CinemaDarkSurface,
    onSurface = CinemaTextPrimary,
    surfaceVariant = CinemaElevatedSurface,
    onSurfaceVariant = CinemaTextSecondary,
    outline = CinemaBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CinemaColorScheme,
        typography = Typography,
        content = content
    )
}
