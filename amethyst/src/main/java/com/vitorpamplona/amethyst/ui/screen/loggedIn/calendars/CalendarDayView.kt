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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import java.util.Calendar

@Composable
fun CalendarDayView(
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
    var dayMs by rememberSaveable {
        mutableStateOf(startOfDayMs(today))
    }

    val byDay by remember(notes, dayMs) {
        derivedStateOf { groupByDayKey(notes) }
    }

    val dayKey = dayKeyForMs(dayMs)
    val dayEvents = byDay[dayKey].orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        DayHeader(
            dayMs = dayMs,
            onPrev = { dayMs -= MILLIS_IN_DAY },
            onNext = { dayMs += MILLIS_IN_DAY },
            onToday = { dayMs = startOfDayMs(Calendar.getInstance()) },
        )

        if (dayEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No events on this day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        DayTimeline(dayEvents, nav)
    }
}

@Composable
private fun DayHeader(
    dayMs: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = dayMs }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                contentDescription = "Previous day",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = formatLongDate(cal.timeInMillis / 1000),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).clickable(onClick = onToday),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNext) {
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = "Next day",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DayTimeline(
    dayEvents: List<Note>,
    nav: INav,
) {
    val sorted =
        remember(dayEvents) {
            dayEvents.sortedBy {
                when (val e = it.event) {
                    is CalendarTimeSlotEvent -> e.start() ?: Long.MAX_VALUE
                    is CalendarDateSlotEvent -> 0L
                    else -> Long.MAX_VALUE
                }
            }
        }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(sorted, key = { it.idHex }) { note ->
            DayRow(note = note, onClick = { nav.nav(Route.Note(note.idHex)) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun DayRow(
    note: Note,
    onClick: () -> Unit,
) {
    val timeLabel =
        when (val e = note.event) {
            is CalendarTimeSlotEvent -> e.start()?.let { formatTimeOfDay(it) } ?: "—"
            is CalendarDateSlotEvent -> "All day"
            else -> "—"
        }
    val title =
        when (val e = note.event) {
            is CalendarTimeSlotEvent -> e.title()
            is CalendarDateSlotEvent -> e.title()
            else -> null
        }
    val location =
        when (val e = note.event) {
            is CalendarTimeSlotEvent -> e.location()
            is CalendarDateSlotEvent -> e.location()
            else -> null
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(width = 72.dp, height = androidx.compose.ui.unit.Dp.Unspecified),
        )
        Box(
            modifier =
                Modifier
                    .size(width = 3.dp, height = 40.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            location?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val MILLIS_IN_DAY: Long = 24L * 60L * 60L * 1000L

private fun startOfDayMs(cal: Calendar): Long {
    val c = cal.clone() as Calendar
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun dayKeyForMs(ms: Long): Long {
    val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    val local = Calendar.getInstance().apply { timeInMillis = ms }
    cal.clear()
    cal.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    return cal.timeInMillis / 1000
}
