package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AthleticDarkColorScheme = darkColorScheme(
    primary = RunnerOrange,
    onPrimary = ObsidianBg,
    primaryContainer = RunnerOrangeContainer,
    onPrimaryContainer = RunnerOrangeSoft,
    secondary = RunnerBlue,
    onSecondary = ObsidianBg,
    secondaryContainer = RunnerBlueContainer,
    onSecondaryContainer = RunnerBlueSoft,
    tertiary = LiveGreen,
    onTertiary = ObsidianBg,
    tertiaryContainer = LiveGreenContainer,
    onTertiaryContainer = LiveGreen,
    error = EndRed,
    onError = InkWhite,
    errorContainer = EndRedContainer,
    onErrorContainer = EndRed,
    background = ObsidianBg,
    onBackground = InkWhite,
    surface = CarbonSurface,
    onSurface = InkWhite,
    surfaceVariant = CarbonElevated,
    onSurfaceVariant = InkSecondary,
    outline = CarbonOutline,
    outlineVariant = CarbonElevated
)

@Composable
fun RunTogetherTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AthleticDarkColorScheme,
        typography = Typography,
        content = content
    )
}
