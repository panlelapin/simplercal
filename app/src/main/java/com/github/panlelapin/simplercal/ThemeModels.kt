package com.github.panlelapin.simplercal

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal enum class ThemeMode(
    val preferenceValue: String,
    val label: String,
) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
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
    GOOGLE_VIOLET("google_violet", "Google Violet", ARGB_GOOGLE_VIOLET),
    SAMSUNG_BLUE("samsung_blue", "Samsung Blue", ARGB_SAMSUNG_BLUE),
    TEAL("teal", "Teal", ARGB_TEAL),
    MATERIAL_VIOLET("material_violet", "Material violet", ARGB_MATERIAL_VIOLET),
    PLUM("plum", "Plum", ARGB_PLUM),
    RASPBERRY("raspberry", "Raspberry", ARGB_RASPBERRY),
    EMERALD_GREEN("emerald_green", "Emerald green", ARGB_EMERALD_GREEN),
    RED("red", "Red", ARGB_MATERIAL_RED),
    RED_A100("red_a100", "Red A100", ARGB_MATERIAL_RED_A100),
    RED_A200("red_a200", "Red A200", ARGB_MATERIAL_RED_A200),
    RED_A400("red_a400", "Red A400", ARGB_MATERIAL_RED_A400),
    RED_A700("red_a700", "Red A700", ARGB_MATERIAL_RED_A700),
    PINK("pink", "Pink", ARGB_PINK),
    PINK_A100("pink_a100", "Pink A100", ARGB_MATERIAL_PINK_A100),
    PINK_A200("pink_a200", "Pink A200", ARGB_MATERIAL_PINK_A200),
    PINK_A400("pink_a400", "Pink A400", ARGB_MATERIAL_PINK_A400),
    PINK_A700("pink_a700", "Pink A700", ARGB_MATERIAL_PINK_A700),
    PURPLE("purple", "Purple", ARGB_MATERIAL_PURPLE),
    PURPLE_A100("purple_a100", "Purple A100", ARGB_MATERIAL_PURPLE_A100),
    PURPLE_A200("purple_a200", "Purple A200", ARGB_MATERIAL_PURPLE_A200),
    PURPLE_A400("purple_a400", "Purple A400", ARGB_MATERIAL_PURPLE_A400),
    PURPLE_A700("purple_a700", "Purple A700", ARGB_MATERIAL_PURPLE_A700),
    DEEP_PURPLE("deep_purple", "Deep Purple", ARGB_MATERIAL_DEEP_PURPLE),
    DEEP_PURPLE_A100(
        "deep_purple_a100",
        "Deep Purple A100",
        ARGB_MATERIAL_DEEP_PURPLE_A100,
    ),
    DEEP_PURPLE_A200(
        "deep_purple_a200",
        "Deep Purple A200",
        ARGB_MATERIAL_DEEP_PURPLE_A200,
    ),
    DEEP_PURPLE_A400(
        "deep_purple_a400",
        "Deep Purple A400",
        ARGB_MATERIAL_DEEP_PURPLE_A400,
    ),
    DEEP_PURPLE_A700(
        "deep_purple_a700",
        "Deep Purple A700",
        ARGB_MATERIAL_DEEP_PURPLE_A700,
    ),
    INDIGO("indigo", "Indigo", ARGB_INDIGO),
    INDIGO_A100("indigo_a100", "Indigo A100", ARGB_MATERIAL_INDIGO_A100),
    INDIGO_A200("indigo_a200", "Indigo A200", ARGB_MATERIAL_INDIGO_A200),
    INDIGO_A400("indigo_a400", "Indigo A400", ARGB_MATERIAL_INDIGO_A400),
    INDIGO_A700("indigo_a700", "Indigo A700", ARGB_MATERIAL_INDIGO_A700),
    BLUE("blue", "Blue", ARGB_MATERIAL_BLUE),
    BLUE_A100("blue_a100", "Blue A100", ARGB_MATERIAL_BLUE_A100),
    BLUE_A200("blue_a200", "Blue A200", ARGB_MATERIAL_BLUE_A200),
    BLUE_A400("blue_a400", "Blue A400", ARGB_MATERIAL_BLUE_A400),
    BLUE_A700("blue_a700", "Blue A700", ARGB_MATERIAL_BLUE_A700),
    LIGHT_BLUE("light_blue", "Light Blue", ARGB_MATERIAL_LIGHT_BLUE),
    LIGHT_BLUE_A100(
        "light_blue_a100",
        "Light Blue A100",
        ARGB_MATERIAL_LIGHT_BLUE_A100,
    ),
    LIGHT_BLUE_A200(
        "light_blue_a200",
        "Light Blue A200",
        ARGB_MATERIAL_LIGHT_BLUE_A200,
    ),
    LIGHT_BLUE_A400(
        "light_blue_a400",
        "Light Blue A400",
        ARGB_MATERIAL_LIGHT_BLUE_A400,
    ),
    LIGHT_BLUE_A700(
        "light_blue_a700",
        "Light Blue A700",
        ARGB_MATERIAL_LIGHT_BLUE_A700,
    ),
    CYAN("cyan", "Cyan", ARGB_MATERIAL_CYAN),
    CYAN_A100("cyan_a100", "Cyan A100", ARGB_MATERIAL_CYAN_A100),
    CYAN_A200("cyan_a200", "Cyan A200", ARGB_MATERIAL_CYAN_A200),
    CYAN_A400("cyan_a400", "Cyan A400", ARGB_MATERIAL_CYAN_A400),
    CYAN_A700("cyan_a700", "Cyan A700", ARGB_MATERIAL_CYAN_A700),
    MATERIAL_TEAL("material_teal", "Material teal", ARGB_MATERIAL_TEAL),
    MATERIAL_TEAL_A100(
        "material_teal_a100",
        "Material teal A100",
        ARGB_MATERIAL_TEAL_A100,
    ),
    MATERIAL_TEAL_A200(
        "material_teal_a200",
        "Material teal A200",
        ARGB_MATERIAL_TEAL_A200,
    ),
    MATERIAL_TEAL_A400(
        "material_teal_a400",
        "Material teal A400",
        ARGB_MATERIAL_TEAL_A400,
    ),
    MATERIAL_TEAL_A700(
        "material_teal_a700",
        "Material teal A700",
        ARGB_MATERIAL_TEAL_A700,
    ),
    GREEN("green", "Green", ARGB_MATERIAL_GREEN),
    GREEN_A100("green_a100", "Green A100", ARGB_MATERIAL_GREEN_A100),
    GREEN_A200("green_a200", "Green A200", ARGB_MATERIAL_GREEN_A200),
    GREEN_A400("green_a400", "Green A400", ARGB_MATERIAL_GREEN_A400),
    GREEN_A700("green_a700", "Green A700", ARGB_MATERIAL_GREEN_A700),
    LIGHT_GREEN("light_green", "Light Green", ARGB_MATERIAL_LIGHT_GREEN),
    LIGHT_GREEN_A100(
        "light_green_a100",
        "Light Green A100",
        ARGB_MATERIAL_LIGHT_GREEN_A100,
    ),
    LIGHT_GREEN_A200(
        "light_green_a200",
        "Light Green A200",
        ARGB_MATERIAL_LIGHT_GREEN_A200,
    ),
    LIGHT_GREEN_A400(
        "light_green_a400",
        "Light Green A400",
        ARGB_MATERIAL_LIGHT_GREEN_A400,
    ),
    LIGHT_GREEN_A700(
        "light_green_a700",
        "Light Green A700",
        ARGB_MATERIAL_LIGHT_GREEN_A700,
    ),
    LIME("lime", "Lime", ARGB_MATERIAL_LIME),
    LIME_A100("lime_a100", "Lime A100", ARGB_MATERIAL_LIME_A100),
    LIME_A200("lime_a200", "Lime A200", ARGB_MATERIAL_LIME_A200),
    LIME_A400("lime_a400", "Lime A400", ARGB_MATERIAL_LIME_A400),
    LIME_A700("lime_a700", "Lime A700", ARGB_MATERIAL_LIME_A700),
    YELLOW("yellow", "Yellow", ARGB_MATERIAL_YELLOW),
    YELLOW_A100("yellow_a100", "Yellow A100", ARGB_MATERIAL_YELLOW_A100),
    YELLOW_A200("yellow_a200", "Yellow A200", ARGB_MATERIAL_YELLOW_A200),
    YELLOW_A400("yellow_a400", "Yellow A400", ARGB_MATERIAL_YELLOW_A400),
    YELLOW_A700("yellow_a700", "Yellow A700", ARGB_MATERIAL_YELLOW_A700),
    AMBER("amber", "Amber", ARGB_MATERIAL_AMBER),
    AMBER_A100("amber_a100", "Amber A100", ARGB_MATERIAL_AMBER_A100),
    AMBER_A200("amber_a200", "Amber A200", ARGB_MATERIAL_AMBER_A200),
    AMBER_A400("amber_a400", "Amber A400", ARGB_MATERIAL_AMBER_A400),
    AMBER_A700("amber_a700", "Amber A700", ARGB_MATERIAL_AMBER_A700),
    ORANGE("orange", "Orange", ARGB_MATERIAL_ORANGE),
    ORANGE_A100("orange_a100", "Orange A100", ARGB_MATERIAL_ORANGE_A100),
    ORANGE_A200("orange_a200", "Orange A200", ARGB_MATERIAL_ORANGE_A200),
    ORANGE_A400("orange_a400", "Orange A400", ARGB_MATERIAL_ORANGE_A400),
    ORANGE_A700("orange_a700", "Orange A700", ARGB_MATERIAL_ORANGE_A700),
    DEEP_ORANGE("deep_orange", "Deep Orange", ARGB_MATERIAL_DEEP_ORANGE),
    DEEP_ORANGE_A100(
        "deep_orange_a100",
        "Deep Orange A100",
        ARGB_MATERIAL_DEEP_ORANGE_A100,
    ),
    DEEP_ORANGE_A200(
        "deep_orange_a200",
        "Deep Orange A200",
        ARGB_MATERIAL_DEEP_ORANGE_A200,
    ),
    DEEP_ORANGE_A400(
        "deep_orange_a400",
        "Deep Orange A400",
        ARGB_MATERIAL_DEEP_ORANGE_A400,
    ),
    DEEP_ORANGE_A700(
        "deep_orange_a700",
        "Deep Orange A700",
        ARGB_MATERIAL_DEEP_ORANGE_A700,
    ),
    BROWN("brown", "Brown", ARGB_MATERIAL_BROWN),
    GREY("grey", "Grey", ARGB_MATERIAL_GREY),
    BLUE_GREY("blue_grey", "Blue Grey", ARGB_MATERIAL_BLUE_GREY),
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

internal enum class SimulationMode(
    val preferenceValue: String,
    val label: String,
) {
    OFF("off", "Off"),
    SIMULATION("simulation", "Simulation"),
    ;

    companion object {
        fun fromPreferenceValue(value: String): SimulationMode =
            entries.firstOrNull { it.preferenceValue == value } ?: OFF
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
