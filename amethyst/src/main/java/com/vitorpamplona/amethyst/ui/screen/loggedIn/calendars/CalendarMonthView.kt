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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.groupByDayKeyExpanded
import com.vitorpamplona.amethyst.ui.stringRes
import java.time.LocalDate
import java.time.YearMonth

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

    var selectedDayKey by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
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
            eventsByDay = eventsByDay,
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
    eventsByDay: Map<Long, List<Note>>,
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
                        DayCell(
                            modifier = Modifier.weight(1f),
                            dayNumber = dayNumber,
                            isToday = isCurrentMonth && date == today,
                            isSelected = selectedDayKey == dayKey,
                            eventCount = eventsByDay[dayKey]?.size ?: 0,
                            onClick = { onDayClick(dayKey) },
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).height(56.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier,
    dayNumber: Int,
    isToday: Boolean,
    isSelected: Boolean,
    eventCount: Int,
    onClick: () -> Unit,
) {
    val bg =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    Box(
        modifier =
            modifier
                .height(56.dp)
                .padding(2.dp)
                .background(bg, RoundedCornerShape(8.dp))
                .border(
                    width = if (isToday) 1.5.dp else 0.5.dp,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                ).clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
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
            EventDotRow(eventCount)
        }
    }
}

@Composable
private fun EventDotRow(eventCount: Int) {
    if (eventCount <= 0) {
        Spacer(modifier = Modifier.height(6.dp))
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(bottom = 1.dp),
    ) {
        repeat(eventCount.coerceAtMost(3)) {
            Box(
                modifier =
                    Modifier
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        if (eventCount > 3) {
            Text(
                text = "+",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { translationY = -3f },
            )
        }
    }
}
