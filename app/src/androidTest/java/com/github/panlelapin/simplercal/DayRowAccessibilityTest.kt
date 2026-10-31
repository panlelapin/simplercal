package com.github.panlelapin.simplercal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
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
    fun outsideYearDayIsDisabledAndHasNoLongPressOrDetailsAction() {
        val date = LocalDate.of(2025, 12, 29)
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    DayRow(DayRowState(0, null, date, false,
                        WeekDay(date, "Mon", false, false, emptyList(), isEnabled = false),
                        false, false, false, 80.dp, Color.Gray),
                        onClick = { error("Disabled day was selected") },
                        onShowDetails = { error("Disabled details were opened") })
                }
            }
        }
        composeRule.onNodeWithContentDescription("Monday, December 29, 2025, 0 events")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
    }

    @Test
    fun overflowEventsKeepOneClickableNodeAndAnAccessibleDetailsAction() {
        var detailsOpened = false
        val date = LocalDate.of(2026, 8, 30)
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    DayRow(
                        DayRowState(
                            6, 6, date, false,
                            WeekDay(date, "Sun", true, false, List(10) {
                                CalendarEvent(it.toLong(), "$it:0", "Event $it", 0, 1, true)
                            }),
                            true, true, true, 80.dp, Color.Gray,
                        ),
                        onClick = {},
                        onShowDetails = { detailsOpened = true },
                    )
                }
            }
        }
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        val actions = composeRule.onNodeWithContentDescription("Sunday, August 30, 2026, 10 events")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        composeRule.runOnIdle { assertTrue(actions.single().action()) }
        composeRule.runOnIdle { assertTrue(detailsOpened) }
    }

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
                                dayLabelColumnWidth = 80.dp,
                                separatorColor = Color.Gray,
                            ),
                        onClick = { clicked = true },
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Sunday, August 30, 2026, 1 event")
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
