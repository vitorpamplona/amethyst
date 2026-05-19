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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.IcsExport
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.UserCardHeader
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Composable
fun CalendarCollectionsView(
    feedState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RefresheableBox(feedState, true) {
        val state by feedState.feedContent.collectAsStateWithLifecycle()

        when (val s = state) {
            is FeedState.Loaded -> CollectionsBody(s, accountViewModel, nav)
            is FeedState.Empty -> EmptyCollections()
            is FeedState.Loading -> Box(modifier = Modifier.fillMaxSize())
            is FeedState.FeedError ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = s.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
        }
    }
}

@Composable
private fun CollectionsBody(
    loaded: FeedState.Loaded,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    LazyColumn(
        // Reserve top space for the surrounding [DisappearingScaffold]'s top bar — without this
        // the first card scrolls under it on the initial render.
        contentPadding = rememberFeedContentPadding(FeedPadding),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items.list, key = { it.idHex }) { note ->
            CalendarCollectionCard(note, accountViewModel, nav)
        }
    }
}

@Composable
private fun EmptyCollections() {
    CalendarEmptyState(
        title = stringRes(R.string.calendar_empty_collections_title),
        subtitle = stringRes(R.string.calendar_empty_collections_subtitle),
    )
}

@Composable
fun CalendarCollectionCard(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? CalendarEvent ?: return
    val title = remember(note.idHex) { event.title() }
    val description = remember(note.idHex) { event.content.take(180) }
    val count = remember(note.idHex) { event.calendarEventAddresses().size }
    val context = LocalContext.current

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable { nav.nav(Route.Note(note.idHex)) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        // Author header matches every other social card in the app.
        UserCardHeader(
            baseNote = note,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringRes(R.string.calendar_collection_count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = {
                val members = collectMembers(event)
                val ics = IcsExport.calendarToIcs(event, members, TimeUtils.now())
                val filename = IcsExport.calendarFilename(event)
                shareIcs(context, filename, ics)
            }) {
                Icon(
                    symbol = MaterialSymbols.Share,
                    contentDescription = stringRes(R.string.calendar_export_event),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Resolves a calendar's member addresses to their cached events, skipping members that haven't
 * arrived from relays yet or aren't appointments. Returns a list compatible with
 * [IcsExport.calendarToIcs].
 */
private fun collectMembers(calendar: CalendarEvent): List<Pair<Address, Any>> =
    calendar
        .calendarEventAddresses()
        .mapNotNull { addr ->
            val cachedEvent = LocalCache.addressables.get(addr)?.event
            if (cachedEvent is CalendarTimeSlotEvent || cachedEvent is CalendarDateSlotEvent) {
                addr to cachedEvent
            } else {
                null
            }
        }
