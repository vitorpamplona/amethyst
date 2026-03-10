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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RenderCalendarTimeSlotEvent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? CalendarTimeSlotEvent ?: return

    val title = noteEvent.title()
    val image = noteEvent.image()
    val summary = remember(noteEvent) {
        noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null }
    }
    val location = noteEvent.location()
    val dateRange = remember(noteEvent) {
        val start = noteEvent.start()
        val end = noteEvent.end()
        formatTimestampRange(start, end)
    }

    CalendarHeader(
        title = title,
        image = image,
        summary = summary,
        location = location,
        dateRange = dateRange,
        note = note,
        accountViewModel = accountViewModel,
    )
}

@Composable
fun RenderCalendarDateSlotEvent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? CalendarDateSlotEvent ?: return

    val title = noteEvent.title()
    val image = noteEvent.image()
    val summary = remember(noteEvent) {
        noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null }
    }
    val location = noteEvent.location()
    val dateRange = remember(noteEvent) {
        val start = noteEvent.start()
        val end = noteEvent.end()
        formatDateRange(start, end)
    }

    CalendarHeader(
        title = title,
        image = image,
        summary = summary,
        location = location,
        dateRange = dateRange,
        note = note,
        accountViewModel = accountViewModel,
    )
}

@Composable
private fun CalendarHeader(
    title: String?,
    image: String?,
    summary: String?,
    location: String?,
    dateRange: String?,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    Column(MaterialTheme.colorScheme.replyModifier) {
        image?.let {
            MyAsyncImage(
                imageUrl = it,
                contentDescription = stringRes(R.string.preview_card_image_for, it),
                contentScale = ContentScale.FillWidth,
                mainImageModifier = Modifier.fillMaxWidth(),
                loadedImageModifier = Modifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel) },
                onError = { DefaultImageHeader(note, accountViewModel) },
            )
        } ?: run {
            DefaultImageHeader(note, accountViewModel, Modifier.fillMaxWidth())
        }

        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        dateRange?.let {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        location?.let {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        summary?.let {
            Spacer(modifier = StdVertSpacer)
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (summary == null) {
            Spacer(modifier = StdVertSpacer)
        }
    }
}

private fun formatTimestampRange(
    start: Long?,
    end: Long?,
): String? {
    if (start == null) return null
    val formatter = SimpleDateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val startStr = formatter.format(Date(start * 1000))
    return if (end != null && end != start) {
        val endFormatter = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)
        "$startStr – ${endFormatter.format(Date(end * 1000))}"
    } else {
        startStr
    }
}

private fun formatDateRange(
    start: String?,
    end: String?,
): String? {
    if (start == null) return null
    return if (end != null && end != start) {
        "$start – $end"
    } else {
        start
    }
}
