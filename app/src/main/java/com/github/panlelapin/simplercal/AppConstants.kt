package com.github.panlelapin.simplercal

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal const val PREFERENCES_NAME = "simplercal"
internal const val SELECTED_CALENDAR_KEY = "selected_calendar_id"
internal const val DAY_ACCENT_COLOR_KEY = "day_accent_color"
internal const val THEME_MODE_KEY = "theme_mode"
internal const val SCROLL_MODE_KEY = "scroll_mode"
internal const val DEBUG1_OUTLINE_COLOR_KEY = "debug1_outline_color"
internal const val SIMULATION_MODE_KEY = "simulation_mode"
internal const val GITHUB_URL = "https://github.com/panlelapin/simplercal"
internal const val WEEK_DAY_COUNT = 7
internal const val WEEKEND_START_INDEX = 5
internal const val COMPACT_DAY_WEIGHT = 6.5f
internal const val TOTAL_DAY_WEIGHT = 100f
internal const val DAY_STATE_ANIMATION_DURATION_MILLIS = 500
internal const val NANOS_PER_MILLISECOND = 1_000_000L
internal const val DAY_CONTENT_TEXT = "dolor sit amet bla bla truc bigoudi plan plan proutcul"
internal const val EXPANDED_CONTENT_LINE_COUNT = 9
internal const val COMPACT_CONTENT_LINE_COUNT = 1
internal val DAY_LABEL_HORIZONTAL_PADDING = 8.dp
internal val DAY_SEPARATOR_THICKNESS = 1.5.dp
internal val DAY_SUBCONTAINER_CORNER_RADIUS = 10.dp
internal val DAY_ACCENT_STRIPE_WIDTH = 3.dp
internal val MINIMUM_RIGHT_GESTURE_GUTTER = 24.dp
internal const val RIGHT_GESTURE_GUTTER_FRACTION = 0.225f
internal const val BOTTOM_GESTURE_GUTTER_FRACTION = 0.72f
internal const val MIN_FRACTION = 0f
internal const val MAX_FRACTION = 1f
internal const val CONTRAST_OFFSET = 0.05f
internal const val COLOR_ALPHA = 1f
internal const val OUTLINE_BLEND_FRACTION = 0.35f
internal const val DISCRETE_ACTIVATION_OFFSET = 0.5f
internal const val ARGB_ROYAL_BLUE = 0xFF005AC1L
internal const val ARGB_INDIGO = 0xFF3F51B5L
internal const val ARGB_TEAL = 0xFF006B5FL
internal const val ARGB_MATERIAL_VIOLET = 0xFF6750A4L
internal const val ARGB_PLUM = 0xFF7D3C98L
internal const val ARGB_RASPBERRY = 0xFFA7355CL
internal const val ARGB_MANDARIN = 0xFFF57C00L
internal const val ARGB_EMERALD_GREEN = 0xFF2E7D32L
internal val DAY_SUBCONTAINER_SHAPE = RoundedCornerShape(DAY_SUBCONTAINER_CORNER_RADIUS)
internal val DAY_ABBREVIATIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
