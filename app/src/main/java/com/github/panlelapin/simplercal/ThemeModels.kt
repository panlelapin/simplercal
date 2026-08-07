package com.github.panlelapin.simplercal

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal enum class ThemeMode(
    val preferenceValue: String,
    val label: String,
) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "System"),
    ;

    companion object {
        fun fromPreferenceValue(value: String): ThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

internal enum class AccentTheme(
    val preferenceValue: String,
    val label: String,
    private val argb: Long?,
) {
    SYSTEM("system", "System", null),
    ROYAL_BLUE("royal_blue", "Royal blue", ARGB_ROYAL_BLUE),
    INDIGO("indigo", "Indigo", ARGB_INDIGO),
    TEAL("teal", "Teal", ARGB_TEAL),
    MATERIAL_VIOLET("material_violet", "Material violet", ARGB_MATERIAL_VIOLET),
    PLUM("plum", "Plum", ARGB_PLUM),
    RASPBERRY("raspberry", "Raspberry", ARGB_RASPBERRY),
    MANDARIN("mandarin", "Mandarin", ARGB_MANDARIN),
    EMERALD_GREEN("emerald_green", "Emerald green", ARGB_EMERALD_GREEN),
    TEAL_SECOND("teal_second", "Teal", ARGB_TEAL),
    ;

    fun applyTo(
        base: ColorScheme,
        isDark: Boolean,
    ): ColorScheme {
        val accent = argb?.let(::Color) ?: return base
        val surfaceFraction = if (isDark) 0.10f else 0.06f
        val secondaryFraction = if (isDark) 0.60f else 0.35f
        val primaryContainer = accent.blendedOver(base.surfaceContainer, 0.30f)
        val secondary = accent.blendedOver(base.surface, secondaryFraction)
        val secondaryContainer = accent.blendedOver(base.surfaceContainer, 0.22f)
        val tertiary = accent.blendedOver(base.surface, if (isDark) 0.72f else 0.62f)
        val surface = accent.blendedOver(base.surface, surfaceFraction)
        val surfaceContainer = accent.blendedOver(base.surfaceContainer, surfaceFraction)
        return base.copy(
            primary = accent,
            onPrimary = readableContentColor(accent, base),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableContentColor(primaryContainer, base),
            secondary = secondary,
            onSecondary = readableContentColor(secondary, base),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableContentColor(secondaryContainer, base),
            tertiary = tertiary,
            onTertiary = readableContentColor(tertiary, base),
            surface = surface,
            surfaceContainer = surfaceContainer,
            surfaceTint = accent,
            outline = accent.blendedOver(base.outline, OUTLINE_BLEND_FRACTION),
            inversePrimary = accent,
        )
    }

    companion object {
        fun fromPreferenceValue(value: String): AccentTheme =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

internal fun Color.blendedOver(
    background: Color,
    foregroundFraction: Float,
): Color {
    val fraction =
        foregroundFraction.coerceIn(
            minimumValue = MIN_FRACTION,
            maximumValue = MAX_FRACTION,
        )
    return Color(
        red = red * fraction + background.red * (1f - fraction),
        green = green * fraction + background.green * (1f - fraction),
        blue = blue * fraction + background.blue * (1f - fraction),
        alpha = COLOR_ALPHA,
    )
}

private fun readableContentColor(
    background: Color,
    colorScheme: ColorScheme,
): Color {
    val surfaceContrast = contrastRatio(first = background, second = colorScheme.surface)
    val onSurfaceContrast = contrastRatio(first = background, second = colorScheme.onSurface)
    return if (surfaceContrast > onSurfaceContrast) colorScheme.surface else colorScheme.onSurface
}

private fun contrastRatio(
    first: Color,
    second: Color,
): Float =
    (maxOf(a = first.luminance(), b = second.luminance()) + CONTRAST_OFFSET) /
        (minOf(a = first.luminance(), b = second.luminance()) + CONTRAST_OFFSET)

internal enum class WeekScrollMode(
    val preferenceValue: String,
    val label: String,
    val activationOffset: Float,
) {
    SMOOTH("mode_1", "Discrete", DISCRETE_ACTIVATION_OFFSET),
    PER_DAY("mode_2", "Linear", MIN_FRACTION),
    ;

    companion object {
        fun fromPreferenceValue(value: String): WeekScrollMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SMOOTH
    }
}

internal enum class Debug1OutlineColor(
    val preferenceValue: String,
    val label: String,
) {
    BLACK("black", "Black"),
    APP_BAR_BACKGROUND("app_bar_background", "App bar background"),
    ;

    fun resolve(
        colorScheme: ColorScheme,
        appBarBackground: Color,
    ): Color =
        when (this) {
            BLACK -> colorScheme.onSurface
            APP_BAR_BACKGROUND -> appBarBackground
        }

    companion object {
        fun fromPreferenceValue(value: String): Debug1OutlineColor =
            entries.firstOrNull { it.preferenceValue == value } ?: APP_BAR_BACKGROUND
    }
}

internal enum class Debug2RightBackground(
    val preferenceValue: String,
    val label: String,
) {
    SURFACE("surface", "Surface"),
    APP_BAR_BACKGROUND("app_bar_background", "App bar background"),
    ;

    fun resolve(
        colorScheme: ColorScheme,
        appBarBackground: Color,
    ): Color =
        when (this) {
            SURFACE -> colorScheme.surface
            APP_BAR_BACKGROUND -> appBarBackground
        }

    companion object {
        fun fromPreferenceValue(value: String): Debug2RightBackground =
            entries.firstOrNull { it.preferenceValue == value } ?: SURFACE
    }
}
