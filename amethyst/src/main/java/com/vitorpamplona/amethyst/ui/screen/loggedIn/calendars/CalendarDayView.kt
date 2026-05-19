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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.appointmentView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.groupByDayKey
import com.vitorpamplona.amethyst.ui.stringRes
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CalendarDayView(
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
    // Persisting an epoch-day Long is auto-saveable; arithmetic in [LocalDate] is DST-safe
    // (millisecond stepping was off by an hour after spring/fall transitions).
    var visibleEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val visibleDate = LocalDate.ofEpochDay(visibleEpochDay)

    val byDay by remember(notes) { derivedStateOf { groupByDayKey(notes) } }
    val dayEvents = byDay[visibleDate.toEpochDay()].orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        CalendarNavigationHeader(
            title = formatLongDate(visibleDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()),
            prevContentDescription = stringRes(R.string.calendar_nav_previous_day),
            nextContentDescription = stringRes(R.string.calendar_nav_next_day),
            onPrev = { visibleEpochDay = visibleDate.minusDays(1).toEpochDay() },
            onNext = { visibleEpochDay = visibleDate.plusDays(1).toEpochDay() },
            onToday = { visibleEpochDay = LocalDate.now().toEpochDay() },
        )

        if (dayEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringRes(R.string.calendar_no_events_today),
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
private fun DayTimeline(
    dayEvents: List<Note>,
    nav: INav,
) {
    val sorted =
        remember(dayEvents) {
            // All-day events bubble to the top (Long.MIN_VALUE), then time-slot events in order.
            dayEvents.sortedBy { it.appointmentView()?.startSeconds ?: Long.MAX_VALUE }
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
    val view = note.appointmentView() ?: return

    val timeLabel =
        when {
            view.isAllDay -> stringRes(R.string.calendar_all_day)
            view.startSeconds != null -> formatTimeOfDay(view.startSeconds)
            else -> "—"
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
            modifier = Modifier.width(72.dp),
        )
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            view.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            view.location?.let {
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
