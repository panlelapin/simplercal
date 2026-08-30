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
internal const val SCHEDULE_TIME_KEY_PREFIX = "schedule_time_"
internal const val SCHEDULE_SLOT_COUNT = 5
internal const val UNSET_SCHEDULE_TIME = -1
internal const val NO_CALENDAR_ID = -1L
internal const val GITHUB_URL = "https://github.com/panlelapin/simplercal"
internal const val WEEK_DAY_COUNT = 7
internal const val WEEKEND_START_INDEX = 5
internal const val COMPACT_DAY_WEIGHT = 6.5f
internal const val TOTAL_DAY_WEIGHT = 100f
internal const val DAY_STATE_ANIMATION_DURATION_MILLIS = 500
internal const val NANOS_PER_MILLISECOND = 1_000_000L
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
internal val DAY_SUBCONTAINER_SHAPE = RoundedCornerShape(DAY_SUBCONTAINER_CORNER_RADIUS)
