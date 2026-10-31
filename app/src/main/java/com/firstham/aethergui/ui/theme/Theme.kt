package com.firstham.aethergui.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MintGreen,
    onPrimary = BgDark,
    primaryContainer = MintGreenSoft,
    onPrimaryContainer = MintGreen,
    secondary = ElectricBlue,
    onSecondary = BgDark,
    secondaryContainer = ElectricBlueSoft,
    onSecondaryContainer = ElectricBlue,
    tertiary = ProwlViolet,
    onTertiary = BgDark,
    tertiaryContainer = ProwlVioletSoft,
    onTertiaryContainer = ProwlViolet,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = ErrorRed,
    onError = BgDark
)

@Composable
fun FoxyVPNTheme(
    darkTheme: Boolean = true, // VPN apps look premier in dark console mode
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
