package dev.spinet.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.spinet.model.ThemeSpec

fun String.toColorOrNull(): Color? {
    val hex = trim().removePrefix("#")
    if (hex.length != 6) return null
    val value = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}

private fun Color.contrastOn(): Color {
    val l = luminance()
    val againstWhite = 1.05f / (l + 0.05f)
    val againstBlack = (l + 0.05f) / 0.05f
    return if (againstBlack >= againstWhite) Color.Black else Color.White
}

private fun Color.mix(other: Color, t: Float): Color = Color(
    red = red + (other.red - red) * t,
    green = green + (other.green - green) * t,
    blue = blue + (other.blue - blue) * t,
    alpha = 1f
)

fun ThemeSpec.toColorScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val bg = background.toColorOrNull() ?: base.background
    val primary = primary.toColorOrNull() ?: base.primary
    val secondary = secondary.toColorOrNull() ?: base.secondary
    val tertiary = tertiary.toColorOrNull() ?: base.tertiary
    val onBg = bg.contrastOn()

    val surfaceLift = bg.mix(onBg, 0.10f)
    val variant = bg.mix(onBg, 0.16f)
    val outline = bg.mix(onBg, 0.45f)

    val primaryContainer = primary.mix(bg, 0.25f)
    val secondaryContainer = secondary.mix(bg, 0.40f)
    val tertiaryContainer = tertiary.mix(bg, 0.40f)

    return base.copy(
        primary = primary,
        onPrimary = primary.contrastOn(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = primaryContainer.contrastOn(),
        secondary = secondary,
        onSecondary = secondary.contrastOn(),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = secondaryContainer.contrastOn(),
        tertiary = tertiary,
        onTertiary = tertiary.contrastOn(),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = tertiaryContainer.contrastOn(),
        background = bg,
        onBackground = onBg,
        surface = bg,
        onSurface = onBg,
        surfaceVariant = variant,

        onSurfaceVariant = onBg,
        surfaceContainer = surfaceLift,
        surfaceContainerLow = surfaceLift,
        surfaceContainerLowest = bg,
        surfaceContainerHigh = variant,
        surfaceContainerHighest = variant,
        outline = outline,
        outlineVariant = bg.mix(onBg, 0.30f),

        surfaceTint = onBg,
        surfaceBright = bg.mix(onBg, 0.24f),
        surfaceDim = bg,
        inverseSurface = onBg,
        inverseOnSurface = bg,

        inversePrimary = primary.mix(bg, 0.35f),
        scrim = Color.Black
    )
}
