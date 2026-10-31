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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BgDark.toArgb()
            window.navigationBarColor = BgDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
