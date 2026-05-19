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
import androidx.compose.material3.IconButton
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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.calendarLocalDayKey
import java.time.LocalDate
import java.util.Calendar

@Composable
fun CalendarMonthView(
    feedState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val state by feedState.feedContent.collectAsStateWithLifecycle()
    val notes =
        when (val s = state) {
            is FeedState.Loaded ->
                s.feed
                    .collectAsStateWithLifecycle()
                    .value.list
            else -> emptyList()
        }

    val today = remember { Calendar.getInstance() }
    var year by rememberSaveable { mutableStateOf(today.get(Calendar.YEAR)) }
    var month by rememberSaveable { mutableStateOf(today.get(Calendar.MONTH)) }

    // groupByDayKey only depends on `notes`; keying on year/month would needlessly recreate
    // the derived state on every month navigation.
    val eventsByDay by remember(notes) {
        derivedStateOf { groupByDayKey(notes) }
    }

    var selectedDayKey by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthHeader(
            year = year,
            month = month,
            onPrev = {
                if (month == 0) {
                    month = 11
                    year -= 1
                } else {
                    month -= 1
                }
                selectedDayKey = null
            },
            onNext = {
                if (month == 11) {
                    month = 0
                    year += 1
                } else {
                    month += 1
                }
                selectedDayKey = null
            },
            onToday = {
                year = today.get(Calendar.YEAR)
                month = today.get(Calendar.MONTH)
                selectedDayKey = null
            },
        )

        WeekdayHeader()

        MonthGrid(
            year = year,
            month = month,
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
private fun MonthHeader(
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                contentDescription = "Previous month",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = formatMonthYear(year, month),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).clickable(onClick = onToday),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNext) {
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = "Next month",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
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
    year: Int,
    month: Int,
    eventsByDay: Map<Long, List<Note>>,
    selectedDayKey: Long?,
    onDayClick: (Long) -> Unit,
) {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(year, month, 1)
    val firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY // 0..6
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = ((firstWeekday + daysInMonth + 6) / 7) * 7
    val rows = totalCells / 7

    val todayCal = remember { Calendar.getInstance() }
    val isCurrentMonth = year == todayCal.get(Calendar.YEAR) && month == todayCal.get(Calendar.MONTH)
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    Column(modifier = Modifier.fillMaxWidth()) {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val cellIndex = r * 7 + c
                    val dayNumber = cellIndex - firstWeekday + 1
                    if (dayNumber in 1..daysInMonth) {
                        // Calendar.MONTH is 0-based; LocalDate.of's month is 1-based.
                        val dayKey = LocalDate.of(year, month + 1, dayNumber).toEpochDay()
                        val dayEvents = eventsByDay[dayKey].orEmpty()
                        DayCell(
                            modifier = Modifier.weight(1f),
                            dayNumber = dayNumber,
                            isToday = isCurrentMonth && dayNumber == todayDay,
                            isSelected = selectedDayKey == dayKey,
                            eventCount = dayEvents.size,
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
        when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
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
    val displayedDots = eventCount.coerceAtMost(3)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(bottom = 1.dp),
    ) {
        repeat(displayedDots) {
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

/**
 * Buckets events by local calendar day (returned as `LocalDate.toEpochDay`). Time-slot events
 * land on the viewer's local date; date-slot events use the ISO date verbatim so "Jan 15" stays
 * on Jan 15 in every zone.
 */
fun groupByDayKey(notes: List<Note>): Map<Long, List<Note>> {
    val map = mutableMapOf<Long, MutableList<Note>>()
    notes.forEach {
        val dayKey = it.calendarLocalDayKey() ?: return@forEach
        map.getOrPut(dayKey) { mutableListOf() }.add(it)
    }
    return map
}
