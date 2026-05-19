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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.calendarStartSeconds
import java.util.Calendar

@Composable
fun CalendarWeekView(
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
    var weekStartMs by rememberSaveable {
        mutableStateOf(startOfWeekMs(today))
    }

    val eventsByDay by remember(notes, weekStartMs) {
        derivedStateOf { groupByDayKey(notes) }
    }

    var selectedDayIndex by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekHeader(
            weekStartMs = weekStartMs,
            onPrev = {
                weekStartMs -= MILLIS_PER_WEEK
                selectedDayIndex = 0
            },
            onNext = {
                weekStartMs += MILLIS_PER_WEEK
                selectedDayIndex = 0
            },
            onToday = {
                weekStartMs = startOfWeekMs(Calendar.getInstance())
                selectedDayIndex = 0
            },
        )

        WeekStrip(
            weekStartMs = weekStartMs,
            selectedIndex = selectedDayIndex,
            eventsByDay = eventsByDay,
            onSelect = { selectedDayIndex = it },
        )

        Spacer(modifier = Modifier.height(8.dp))

        val selectedDayKey = dayKeyForOffset(weekStartMs, selectedDayIndex)
        val dayNotes = eventsByDay[selectedDayKey].orEmpty()

        DaySummaryHeader(selectedDayKey)

        if (dayNotes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No events",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun WeekHeader(
    weekStartMs: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = weekStartMs }
    val title = formatMonthYear(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                contentDescription = "Previous week",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).clickable(onClick = onToday),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNext) {
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = "Next week",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun WeekStrip(
    weekStartMs: Long,
    selectedIndex: Int,
    eventsByDay: Map<Long, List<Note>>,
    onSelect: (Int) -> Unit,
) {
    val cal = Calendar.getInstance()
    val todayCal = remember { Calendar.getInstance() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        for (i in 0..6) {
            cal.timeInMillis = weekStartMs
            cal.add(Calendar.DAY_OF_YEAR, i)
            val dayKey = dayKeyForOffset(weekStartMs, i)
            val count = eventsByDay[dayKey]?.size ?: 0
            val isToday =
                cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
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
                        ).clickable { onSelect(i) }
                        .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatShortWeekday(i),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = cal.get(Calendar.DAY_OF_MONTH).toString(),
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
private fun DaySummaryHeader(dayKeyUtcSeconds: Long) {
    Text(
        text = formatLongDate(dayKeyUtcSeconds),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
private const val MILLIS_PER_WEEK: Long = 7L * MILLIS_PER_DAY

private fun startOfWeekMs(cal: Calendar): Long {
    val c = cal.clone() as Calendar
    c.firstDayOfWeek = Calendar.SUNDAY
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    val dow = c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    c.add(Calendar.DAY_OF_YEAR, -dow)
    return c.timeInMillis
}

private fun dayKeyForOffset(
    weekStartMs: Long,
    offset: Int,
): Long {
    val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    val local = Calendar.getInstance()
    local.timeInMillis = weekStartMs + offset * MILLIS_PER_DAY
    cal.clear()
    cal.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    return cal.timeInMillis / 1000
}

@Suppress("unused")
fun startOfWeekMsForNote(note: Note): Long? {
    val s = note.calendarStartSeconds() ?: return null
    val cal = Calendar.getInstance().apply { timeInMillis = s * 1000 }
    return startOfWeekMs(cal)
}
