package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DayRowAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dayRowExposesOneCompleteClickableSemanticNode() {
        var clicked = false
        val date = LocalDate.of(2026, 8, 30)
        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    DayRow(
                        state =
                            DayRowState(
                                dayIndex = 6,
                                highlightedDayIndex = 6,
                                referenceDate = date,
                                isDarkTheme = false,
                                day =
                                    WeekDay(
                                        date = date,
                                        abbreviation = "Sun",
                                        isWEorBankH = true,
                                        isHolidays = false,
                                        events =
                                            listOf(
                                                CalendarEvent(
                                                    eventId = 1,
                                                    instanceId = "1:0",
                                                    title = "Event",
                                                    beginMillis = 0,
                                                    endMillis = 1,
                                                    allDay = true,
                                                ),
                                            ),
                                    ),
                                isSelected = true,
                                isExpanded = true,
                                isContentExpanded = true,
                                weight = 1f,
                                dayLabelColumnWidth = 80.dp,
                                separatorColor = Color.Gray,
                                appBarBackground = Color.LightGray,
                            ),
                        onClick = { clicked = true },
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Sunday, August 30, 1 event")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Expanded",
                ),
            )
            .assertIsSelected()
            .performClick()

        assertTrue(clicked)
    }
}
