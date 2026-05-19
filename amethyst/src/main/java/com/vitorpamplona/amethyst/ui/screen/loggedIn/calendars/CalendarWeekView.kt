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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.groupByDayKeyExpanded
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CalendarWeekView(
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
    // Persist the week-start as an epoch-day Long (auto-saveable), reconstruct LocalDate on use.
    var weekStartEpochDay by rememberSaveable {
        mutableStateOf(startOfWeek(today).toEpochDay())
    }
    val weekStart = LocalDate.ofEpochDay(weekStartEpochDay)

    var selectedDayIndex by rememberSaveable { mutableStateOf(0) }

    val eventsByDay by remember(notes) { derivedStateOf { groupByDayKeyExpanded(notes) } }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .calendarSwipeNavigation(
                    key = weekStartEpochDay,
                    onSwipeLeft = {
                        weekStartEpochDay = weekStart.plusWeeks(1).toEpochDay()
                        selectedDayIndex = 0
                    },
                    onSwipeRight = {
                        weekStartEpochDay = weekStart.minusWeeks(1).toEpochDay()
                        selectedDayIndex = 0
                    },
                ),
    ) {
        CalendarNavigationHeader(
            title = formatMonthYear(weekStart.year, weekStart.monthValue - 1),
            prevContentDescription = stringRes(R.string.calendar_nav_previous_week),
            nextContentDescription = stringRes(R.string.calendar_nav_next_week),
            onPrev = {
                weekStartEpochDay = weekStart.minusWeeks(1).toEpochDay()
                selectedDayIndex = 0
            },
            onNext = {
                weekStartEpochDay = weekStart.plusWeeks(1).toEpochDay()
                selectedDayIndex = 0
            },
            onToday = {
                weekStartEpochDay = startOfWeek(LocalDate.now()).toEpochDay()
                selectedDayIndex = 0
            },
        )

        WeekStrip(
            weekStart = weekStart,
            today = today,
            selectedIndex = selectedDayIndex,
            eventsByDay = eventsByDay,
            onSelect = { selectedDayIndex = it },
        )

        Spacer(modifier = Modifier.height(8.dp))

        val selectedDate = weekStart.plusDays(selectedDayIndex.toLong())
        val dayNotes = eventsByDay[selectedDate.toEpochDay()].orEmpty()

        DaySummaryHeader(selectedDate)

        if (dayNotes.isEmpty()) {
            CalendarEmptyState(
                title = stringRes(R.string.calendar_empty_week_title),
                subtitle = stringRes(R.string.calendar_empty_week_subtitle),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(dayNotes, key = { it.idHex }) { note ->
                    CalendarEventListCard(note, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    weekStart: LocalDate,
    today: LocalDate,
    selectedIndex: Int,
    eventsByDay: Map<Long, List<Note>>,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val count = eventsByDay[date.toEpochDay()]?.size ?: 0
            val isToday = date == today
            val isSelected = i == selectedIndex

            val bg =
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            val fg =
                when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }

            val dateLabel = formatLongDate(date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond())
            val baseA11y = pluralStringResource(R.plurals.calendar_day_a11y_events, count, dateLabel, count)
            val todaySuffix = stringRes(R.string.calendar_day_a11y_today_suffix)
            val selectedSuffix = stringRes(R.string.calendar_day_a11y_selected_suffix)
            val a11y =
                buildString {
                    append(baseA11y)
                    if (isToday) append(", ").append(todaySuffix)
                    if (isSelected) append(", ").append(selectedSuffix)
                }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(3.dp)
                        .background(bg, RoundedCornerShape(10.dp))
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp),
                        ).clickable(role = Role.Tab) { onSelect(i) }
                        .padding(vertical = 6.dp)
                        .semantics(mergeDescendants = true) { contentDescription = a11y },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatShortWeekday(i),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                )
                if (count > 0) {
                    Text(
                        text = if (count > 9) "9+" else count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun DaySummaryHeader(date: LocalDate) {
    Text(
        text = formatLongDate(date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/**
 * Returns the Sunday on or before [date]. DST-safe because [LocalDate] arithmetic ignores zones.
 * `DayOfWeek.SUNDAY.value` is 7 in java.time, so `% 7` collapses Sunday → 0 with the rest of the
 * week following in order.
 */
private fun startOfWeek(date: LocalDate): LocalDate {
    val daysFromSunday = date.dayOfWeek.value % 7
    return date.minusDays(daysFromSunday.toLong())
}
