package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BumpPartyLightColorScheme = lightColorScheme(
    primary = ElectricPink,
    onPrimary = PureWhite,
    primaryContainer = ElectricPinkSoft,
    onPrimaryContainer = DeepInk,
    secondary = CobaltBlue,
    onSecondary = PureWhite,
    secondaryContainer = CobaltBlueSoft,
    onSecondaryContainer = DeepInk,
    tertiary = MintGreen,
    onTertiary = DeepInk,
    tertiaryContainer = MintGreenSoft,
    onTertiaryContainer = DeepInk,
    background = WarmCream,
    onBackground = DeepInk,
    surface = PureWhite,
    onSurface = DeepInk,
    surfaceVariant = WarmCreamAlt,
    onSurfaceVariant = MutedInk,
    outline = DeepInk,
    outlineVariant = SoftBorder,
    error = FlameOrange,
    onError = PureWhite,
    errorContainer = FlameOrangeSoft,
    onErrorContainer = DeepInk
)

@Composable
fun BumpPartyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BumpPartyLightColorScheme,
        typography = BumpPartyTypography,
        content = content
    )
}
