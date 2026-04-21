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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font10SP
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent

private const val WORDS_PER_MINUTE = 225
private val COVER_ASPECT_RATIO = 16f / 9f

@Composable
fun RenderLongFormContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? LongTextNoteEvent ?: return

    LongFormHeader(noteEvent, note, accountViewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LongFormHeader(
    noteEvent: LongTextNoteEvent,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val image = noteEvent.image()
    val title = noteEvent.title()
    val summary =
        remember(noteEvent) {
            noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null }
        }
    val topics = remember(noteEvent) { noteEvent.topics().distinct().take(3) }
    val readingMinutes = remember(noteEvent) { estimateReadingMinutes(noteEvent.content) }

    Column(MaterialTheme.colorScheme.replyModifier) {
        LongFormCoverImage(image, note, accountViewModel)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            summary?.let {
                Spacer(Modifier.padding(top = 6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.padding(top = 12.dp))
            AuthorMetaRow(note, readingMinutes, accountViewModel)

            if (topics.isNotEmpty()) {
                Spacer(Modifier.padding(top = 8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Size5dp),
                    verticalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    topics.forEach { TopicChip(it) }
                }
            }
        }
    }
}

@Composable
private fun LongFormCoverImage(
    image: String?,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val imageShape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
    val imageModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(imageShape)

    Box(imageModifier) {
        if (image != null) {
            MyAsyncImage(
                imageUrl = image,
                contentDescription = stringRes(R.string.preview_card_image_for, image),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxWidth(),
                loadedImageModifier = imageModifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel, imageModifier) },
                onError = { DefaultImageHeader(note, accountViewModel, imageModifier) },
            )
        } else {
            DefaultImageHeader(note, accountViewModel, imageModifier)
        }
    }
}

@Composable
private fun TopicChip(topic: String) {
    Text(
        text = "#$topic",
        style = MaterialTheme.typography.labelSmall,
        fontSize = Font10SP,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun AuthorMetaRow(
    note: Note,
    readingMinutes: Int,
    accountViewModel: AccountViewModel,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        WatchAuthor(baseNote = note, accountViewModel) { user ->
            BaseUserPicture(user, 25.dp, accountViewModel, Modifier)
        }

        Spacer(Modifier.padding(start = 8.dp))

        NoteUsernameDisplay(
            baseNote = note,
            weight = Modifier.weight(1f, fill = false),
            textColor = MaterialTheme.colorScheme.onSurface,
            accountViewModel = accountViewModel,
        )

        MetaSeparator()
        TimeAgo(note)

        MetaSeparator()
        ReadingTimeBadge(readingMinutes)
    }
}

@Composable
private fun MetaSeparator() {
    Text(
        text = " · ",
        color = MaterialTheme.colorScheme.grayText,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ReadingTimeBadge(minutes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.grayText,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.padding(start = 4.dp))
        Text(
            text = stringRes(R.string.long_form_reading_minutes, minutes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
        )
    }
}

private fun estimateReadingMinutes(content: String): Int {
    if (content.isBlank()) return 1
    val words = content.split(Regex("\\s+")).count { it.isNotBlank() }
    return maxOf(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE)
}
