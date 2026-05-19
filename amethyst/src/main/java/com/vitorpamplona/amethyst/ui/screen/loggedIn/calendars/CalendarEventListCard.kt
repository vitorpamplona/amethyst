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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.appointmentView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.UserCardHeader
import com.vitorpamplona.quartz.utils.TimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Thread-safe and hoisted: previously each CalendarDateBadge recompose allocated a new
// SimpleDateFormat, which (a) is not thread-safe and (b) created 500 allocations while scrolling.
private val MonthShortFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

@Composable
fun CalendarEventListCard(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val view = note.appointmentView() ?: return
    val range = remember(note.idHex) { formatCalendarRange(note) }
    val context = LocalContext.current
    val relative = remember(note.idHex, view.startSeconds) { relativeTimeLabel(context, view, TimeUtils.now()) }
    val event = note.event ?: return
    val detailRoute =
        remember(event.id) {
            val addr =
                (event as? com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent)?.address()
            addr?.let { Route.CalendarEventDetail(it) } ?: Route.Note(note.idHex)
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable { nav.nav(detailRoute) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        // Author header matches the picture-feed / shorts card shape: avatar + display name +
        // time-ago at the top of every social card in the app. Without this, calendar cards
        // looked alien next to the rest of the feed.
        UserCardHeader(baseNote = note, accountViewModel = accountViewModel, nav = nav)

        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CalendarDateBadge(view.startSeconds)

            Spacer(modifier = Modifier.size(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                view.title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                range?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                relative?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                view.location?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            symbol = MaterialSymbols.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!view.image.isNullOrBlank()) {
                    Spacer(modifier = Modifier.size(4.dp))
                    MyAsyncImage(
                        imageUrl = view.image,
                        contentDescription = view.title,
                        contentScale = ContentScale.Crop,
                        mainImageModifier = Modifier.fillMaxWidth().height(120.dp),
                        loadedImageModifier = Modifier,
                        accountViewModel = accountViewModel,
                        onLoadingBackground = { Box(modifier = Modifier.fillMaxWidth().height(120.dp)) },
                        onError = { Box(modifier = Modifier.fillMaxWidth().height(120.dp)) },
                    )
                }
                if (!view.summary.isNullOrBlank() && view.image.isNullOrBlank()) {
                    Text(
                        text = view.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDateBadge(startSeconds: Long?) {
    if (startSeconds == null) {
        Box(
            modifier = Modifier.size(width = 52.dp, height = 60.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    val localDate =
        remember(startSeconds) {
            Instant.ofEpochSecond(startSeconds).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    val day = localDate.dayOfMonth.toString()
    val month = remember(localDate) { MonthShortFormatter.format(localDate).uppercase() }

    Column(
        modifier = Modifier.size(width = 52.dp, height = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = month,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = day,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}
