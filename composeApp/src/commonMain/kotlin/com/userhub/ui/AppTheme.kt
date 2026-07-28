package com.userhub.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ElectricBlue = Color(0xFF2E55FF)
private val RoyalBlue = Color(0xFF1633B6)
private val CaribbeanCyan = Color(0xFF00D0FF)
private val DeepNavy = Color(0xFF001352)
private val PaleBlue = Color(0xFFDDE3FF)
private val IceBlue = Color(0xFFCBD5FF)
private val DeepTeal = Color(0xFF00697F)
private val PaleCyan = Color(0xFFB3F1FF)
private val AlmostWhite = Color(0xFFF7F7F7)
private val PanelGrey = Color(0xFFF1F2F6)
private val Grayscale10 = Color(0xFFE5E5E7)
private val Grayscale20 = Color(0xFFCFCFD0)
private val Grayscale40 = Color(0xFFA2A2A2)
private val Grayscale60 = Color(0xFF737375)
private val Grayscale100 = Color(0xFF121212)
private val Charcoal = Color(0xFF1C1C1E)
private val SoftLavender = Color(0xFFB7C4FF)

private val LightColors = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = PaleBlue,
    onPrimaryContainer = DeepNavy,
    secondary = RoyalBlue,
    onSecondary = Color.White,
    secondaryContainer = IceBlue,
    onSecondaryContainer = DeepNavy,
    tertiary = DeepTeal,
    onTertiary = Color.White,
    tertiaryContainer = PaleCyan,
    onTertiaryContainer = Color(0xFF002F3C),
    background = AlmostWhite,
    onBackground = Grayscale100,
    surface = AlmostWhite,
    onSurface = Grayscale100,
    surfaceVariant = Grayscale10,
    onSurfaceVariant = Grayscale60,
    surfaceContainerLow = PanelGrey,
    outline = Grayscale40,
    outlineVariant = Grayscale20
)

private val DarkColors = darkColorScheme(
    primary = SoftLavender,
    onPrimary = Color(0xFF00218A),
    primaryContainer = RoyalBlue,
    onPrimaryContainer = PaleBlue,
    secondary = SoftLavender,
    onSecondary = Color(0xFF00218A),
    secondaryContainer = Color(0xFF16215E),
    onSecondaryContainer = IceBlue,
    tertiary = CaribbeanCyan,
    onTertiary = Color(0xFF003543),
    tertiaryContainer = Color(0xFF004E60),
    onTertiaryContainer = PaleCyan,
    background = Grayscale100,
    onBackground = Grayscale10,
    surface = Grayscale100,
    onSurface = Grayscale10,
    surfaceVariant = Charcoal,
    onSurfaceVariant = Grayscale40,
    surfaceContainerLow = Charcoal,
    outline = Grayscale60,
    outlineVariant = Color(0xFF3A3A3C)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
