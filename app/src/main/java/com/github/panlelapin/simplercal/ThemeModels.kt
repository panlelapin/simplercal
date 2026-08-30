package com.github.panlelapin.simplercal

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot

internal enum class ThemeMode(
    val preferenceValue: String,
    val labelResource: Int,
) {
    SYSTEM("system", R.string.option_system),
    LIGHT("light", R.string.option_light),
    DARK("dark", R.string.option_dark),
    ;

    companion object {
        fun fromPreferenceValue(value: String): ThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

internal enum class AccentTheme(
    val preferenceValue: String,
    val labelResource: Int,
    private val seedArgb: Int?,
) {
    SYSTEM("system", R.string.option_system, null),
    ROYAL_BLUE("royal_blue", R.string.accent_royal_blue, 0xFF005AC1.toInt()),
    INDIGO("indigo", R.string.accent_indigo, 0xFF3F51B5.toInt()),
    TEAL("teal", R.string.accent_teal, 0xFF006B5F.toInt()),
    MATERIAL_VIOLET("material_violet", R.string.accent_material_violet, 0xFF6750A4.toInt()),
    PLUM("plum", R.string.accent_plum, 0xFF7D3C98.toInt()),
    RASPBERRY("raspberry", R.string.accent_raspberry, 0xFFA7355C.toInt()),
    MANDARIN("mandarin", R.string.accent_mandarin, 0xFFF57C00.toInt()),
    EMERALD_GREEN("emerald_green", R.string.accent_emerald_green, 0xFF2E7D32.toInt()),
    TEAL_ALTERNATE("teal_alternate", R.string.accent_teal_alternate, 0xFF006B5F.toInt()),
    ;

    fun applyTo(
        dynamicBase: ColorScheme,
        isDark: Boolean,
    ): ColorScheme {
        val seed = seedArgb ?: return dynamicBase
        val scheme = SchemeTonalSpot(Hct.fromInt(seed), isDark, DEFAULT_CONTRAST_LEVEL)
        return dynamicBase.copy(
            primary = scheme.primary.asComposeColor(),
            onPrimary = scheme.onPrimary.asComposeColor(),
            primaryContainer = scheme.primaryContainer.asComposeColor(),
            onPrimaryContainer = scheme.onPrimaryContainer.asComposeColor(),
            inversePrimary = scheme.inversePrimary.asComposeColor(),
            secondary = scheme.secondary.asComposeColor(),
            onSecondary = scheme.onSecondary.asComposeColor(),
            secondaryContainer = scheme.secondaryContainer.asComposeColor(),
            onSecondaryContainer = scheme.onSecondaryContainer.asComposeColor(),
            tertiary = scheme.tertiary.asComposeColor(),
            onTertiary = scheme.onTertiary.asComposeColor(),
            tertiaryContainer = scheme.tertiaryContainer.asComposeColor(),
            onTertiaryContainer = scheme.onTertiaryContainer.asComposeColor(),
            background = scheme.background.asComposeColor(),
            onBackground = scheme.onBackground.asComposeColor(),
            surface = scheme.surface.asComposeColor(),
            onSurface = scheme.onSurface.asComposeColor(),
            surfaceVariant = scheme.surfaceVariant.asComposeColor(),
            onSurfaceVariant = scheme.onSurfaceVariant.asComposeColor(),
            surfaceTint = scheme.surfaceTint.asComposeColor(),
            inverseSurface = scheme.inverseSurface.asComposeColor(),
            inverseOnSurface = scheme.inverseOnSurface.asComposeColor(),
            error = scheme.error.asComposeColor(),
            onError = scheme.onError.asComposeColor(),
            errorContainer = scheme.errorContainer.asComposeColor(),
            onErrorContainer = scheme.onErrorContainer.asComposeColor(),
            outline = scheme.outline.asComposeColor(),
            outlineVariant = scheme.outlineVariant.asComposeColor(),
            scrim = scheme.scrim.asComposeColor(),
            surfaceBright = scheme.surfaceBright.asComposeColor(),
            surfaceDim = scheme.surfaceDim.asComposeColor(),
            surfaceContainer = scheme.surfaceContainer.asComposeColor(),
            surfaceContainerHigh = scheme.surfaceContainerHigh.asComposeColor(),
            surfaceContainerHighest = scheme.surfaceContainerHighest.asComposeColor(),
            surfaceContainerLow = scheme.surfaceContainerLow.asComposeColor(),
            surfaceContainerLowest = scheme.surfaceContainerLowest.asComposeColor(),
            primaryFixed = scheme.primaryFixed.asComposeColor(),
            primaryFixedDim = scheme.primaryFixedDim.asComposeColor(),
            onPrimaryFixed = scheme.onPrimaryFixed.asComposeColor(),
            onPrimaryFixedVariant = scheme.onPrimaryFixedVariant.asComposeColor(),
            secondaryFixed = scheme.secondaryFixed.asComposeColor(),
            secondaryFixedDim = scheme.secondaryFixedDim.asComposeColor(),
            onSecondaryFixed = scheme.onSecondaryFixed.asComposeColor(),
            onSecondaryFixedVariant = scheme.onSecondaryFixedVariant.asComposeColor(),
            tertiaryFixed = scheme.tertiaryFixed.asComposeColor(),
            tertiaryFixedDim = scheme.tertiaryFixedDim.asComposeColor(),
            onTertiaryFixed = scheme.onTertiaryFixed.asComposeColor(),
            onTertiaryFixedVariant = scheme.onTertiaryFixedVariant.asComposeColor(),
        )
    }

    companion object {
        fun fromPreferenceValue(value: String): AccentTheme =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

internal enum class WeekScrollMode(
    val preferenceValue: String,
    val labelResource: Int,
) {
    DISCRETE("mode_1", R.string.scroll_discrete),
    LINEAR("mode_2", R.string.scroll_linear),
    ;

    companion object {
        fun fromPreferenceValue(value: String): WeekScrollMode =
            entries.firstOrNull { it.preferenceValue == value } ?: DISCRETE
    }
}

internal enum class SimulationMode(
    val preferenceValue: String,
    val labelResource: Int,
) {
    OFF("off", R.string.option_off),
    SIMULATION("simulation", R.string.option_simulation),
    ;

    companion object {
        fun fromPreferenceValue(value: String): SimulationMode =
            entries.firstOrNull { it.preferenceValue == value } ?: OFF
    }
}

internal enum class Debug1OutlineColor(
    val preferenceValue: String,
    val labelResource: Int,
) {
    BLACK("black", R.string.debug_black),
    APP_BAR_BACKGROUND("app_bar_background", R.string.debug_app_bar_background),
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

private fun Int.asComposeColor(): Color = Color(this)

private const val DEFAULT_CONTRAST_LEVEL = 0.0
