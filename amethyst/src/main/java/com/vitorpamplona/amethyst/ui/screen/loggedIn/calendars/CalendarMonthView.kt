/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.MONTH_GRID_MAX_LANES
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.MonthGridBarSegment
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.computeMonthGridBars
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.groupByDayKeyExpanded
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun CalendarMonthView(
    feedState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
    filterAddresses: Set<com.vitorpamplona.quartz.nip01Core.core.Address>? = null,
) {
    val state by feedState.feedContent.collectAsStateWithLifecycle()
    val notes =
        when (val s = state) {
            is FeedState.Loaded ->
                s.feed
                    .collectAsStateWithLifecycle()
                    .value.list
                    .applyCalendarFilter(filterAddresses)
            else -> emptyList()
        }

    val today = remember { LocalDate.now() }
    // YearMonth is not Parcelable/auto-saveable; persist the two ints and rebuild on each read.
    var visibleYear by rememberSaveable { mutableStateOf(today.year) }
    var visibleMonthValue by rememberSaveable { mutableStateOf(today.monthValue) }
    val visibleMonth = YearMonth.of(visibleYear, visibleMonthValue)

    fun setVisibleMonth(ym: YearMonth) {
        visibleYear = ym.year
        visibleMonthValue = ym.monthValue
    }

    val eventsByDay by remember(notes) { derivedStateOf { groupByDayKeyExpanded(notes) } }
    val barsByDay by remember(notes) { derivedStateOf { computeMonthGridBars(notes) } }

    var selectedDayKey by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .disappearingScaffoldPadding()
                .calendarSwipeNavigation(
                    key = visibleYear to visibleMonthValue,
                    onSwipeLeft = {
                        setVisibleMonth(visibleMonth.plusMonths(1))
                        selectedDayKey = null
                    },
                    onSwipeRight = {
                        setVisibleMonth(visibleMonth.minusMonths(1))
                        selectedDayKey = null
                    },
                ),
    ) {
        CalendarNavigationHeader(
            title = formatMonthYear(visibleMonth.year, visibleMonth.monthValue - 1),
            prevContentDescription = stringRes(R.string.calendar_nav_previous_month),
            nextContentDescription = stringRes(R.string.calendar_nav_next_month),
            onPrev = {
                setVisibleMonth(visibleMonth.minusMonths(1))
                selectedDayKey = null
            },
            onNext = {
                setVisibleMonth(visibleMonth.plusMonths(1))
                selectedDayKey = null
            },
            onToday = {
                setVisibleMonth(YearMonth.from(LocalDate.now()))
                selectedDayKey = null
            },
        )

        WeekdayHeader()

        MonthGrid(
            visibleMonth = visibleMonth,
            today = today,
            barsByDay = barsByDay,
            selectedDayKey = selectedDayKey,
            onDayClick = { dayKey ->
                selectedDayKey = if (selectedDayKey == dayKey) null else dayKey
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        val selectedEvents = selectedDayKey?.let { eventsByDay[it] }.orEmpty()
        if (selectedEvents.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(selectedEvents, key = { it.idHex }) { note ->
                    CalendarEventListCard(note, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        for (i in 0..6) {
            Text(
                text = formatShortWeekday(i),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    visibleMonth: YearMonth,
    today: LocalDate,
    barsByDay: Map<Long, List<MonthGridBarSegment>>,
    selectedDayKey: Long?,
    onDayClick: (Long) -> Unit,
) {
    val firstOfMonth = visibleMonth.atDay(1)
    // SUNDAY = 7 in DayOfWeek; we want Sunday = 0 to match `formatShortWeekday`.
    val firstWeekdayIndex = firstOfMonth.dayOfWeek.value % 7
    val daysInMonth = visibleMonth.lengthOfMonth()
    val rows = ((firstWeekdayIndex + daysInMonth + 6) / 7)
    val isCurrentMonth = visibleMonth == YearMonth.from(today)

    Column(modifier = Modifier.fillMaxWidth()) {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val cellIndex = r * 7 + c
                    val dayNumber = cellIndex - firstWeekdayIndex + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = visibleMonth.atDay(dayNumber)
                        val dayKey = date.toEpochDay()
                        val cellBars = barsByDay[dayKey].orEmpty()
                        val isToday = isCurrentMonth && date == today
                        val isSelected = selectedDayKey == dayKey
                        DayCell(
                            modifier = Modifier.weight(1f),
                            dayNumber = dayNumber,
                            isToday = isToday,
                            isSelected = isSelected,
                            bars = cellBars,
                            // Anything past the visible lane cap collapses into a "+N" tail —
                            // keeps each cell readable when a day has more than three events.
                            extraEventCount = cellBars.count { it.lane >= MONTH_GRID_MAX_LANES },
                            isWeekStart = c == 0,
                            isWeekEnd = c == 6,
                            // Full date label fed to the screen-reader content description so
                            // TalkBack reads "Wednesday January 15 2025, 2 events" instead of
                            // just "15".
                            dateLabel = formatLongDate(date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()),
                            totalEventCount = cellBars.size,
                            onClick = { onDayClick(dayKey) },
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).height(MONTH_CELL_HEIGHT))
                    }
                }
            }
        }
    }
}

private val MONTH_CELL_HEIGHT = 72.dp

@Composable
private fun DayCell(
    modifier: Modifier,
    dayNumber: Int,
    isToday: Boolean,
    isSelected: Boolean,
    bars: List<MonthGridBarSegment>,
    extraEventCount: Int,
    isWeekStart: Boolean,
    isWeekEnd: Boolean,
    dateLabel: String,
    totalEventCount: Int,
    onClick: () -> Unit,
) {
    val bg =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    val baseDescription =
        pluralStringResource(R.plurals.calendar_day_a11y_events, totalEventCount, dateLabel, totalEventCount)
    val todaySuffix = stringRes(R.string.calendar_day_a11y_today_suffix)
    val selectedSuffix = stringRes(R.string.calendar_day_a11y_selected_suffix)
    val a11y =
        buildString {
            append(baseDescription)
            if (isToday) append(", ").append(todaySuffix)
            if (isSelected) append(", ").append(selectedSuffix)
        }

    Box(
        modifier =
            modifier
                .height(MONTH_CELL_HEIGHT)
                // Vertical-only padding so adjacent cells in a row touch horizontally — a
                // multi-day bar that extends from the right edge of one cell to the left edge of
                // the next visually merges into a single uninterrupted line.
                .padding(vertical = 2.dp)
                .background(bg, RoundedCornerShape(8.dp))
                .border(
                    width = if (isToday) 1.5.dp else 0.5.dp,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                ).clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = a11y },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color =
                    if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            Spacer(modifier = Modifier.height(2.dp))
            EventBarLanes(
                bars = bars,
                extraEventCount = extraEventCount,
                isWeekStart = isWeekStart,
                isWeekEnd = isWeekEnd,
            )
        }
    }
}

/**
 * Renders up to [MONTH_GRID_MAX_LANES] horizontal bars stacked vertically. Each lane occupies a
 * fixed height across every cell so a multi-day event sits on the same y-row in every column it
 * covers — the visual continuity that makes "spans 3 days" readable at a glance.
 *
 * The bar is rounded only at the event's start (`isLeftEnd`) and end (`isRightEnd`). On week
 * boundaries we also round so each row of the grid looks self-contained instead of bleeding into
 * an unaligned next row.
 */
@Composable
private fun EventBarLanes(
    bars: List<MonthGridBarSegment>,
    extraEventCount: Int,
    isWeekStart: Boolean,
    isWeekEnd: Boolean,
) {
    val barColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        for (i in 0 until MONTH_GRID_MAX_LANES) {
            val seg = bars.firstOrNull { it.lane == i }
            if (seg != null) {
                val roundLeft = seg.isLeftEnd || isWeekStart
                val roundRight = seg.isRightEnd || isWeekEnd
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(
                                color = barColor,
                                shape =
                                    RoundedCornerShape(
                                        topStart = if (roundLeft) 2.dp else 0.dp,
                                        bottomStart = if (roundLeft) 2.dp else 0.dp,
                                        topEnd = if (roundRight) 2.dp else 0.dp,
                                        bottomEnd = if (roundRight) 2.dp else 0.dp,
                                    ),
                            ),
                )
            } else {
                Spacer(modifier = Modifier.fillMaxWidth().height(5.dp))
            }
        }
        if (extraEventCount > 0) {
            Text(
                text = "+$extraEventCount",
                style = MaterialTheme.typography.labelSmall,
                color = barColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
