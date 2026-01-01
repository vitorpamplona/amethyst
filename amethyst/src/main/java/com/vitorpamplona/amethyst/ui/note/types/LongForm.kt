/**
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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.authorNotePictureForImageHeader
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent

@Composable
fun RenderLongFormContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? LongTextNoteEvent ?: return

    LongFormHeader(noteEvent, note, accountViewModel)
}

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

    Column(MaterialTheme.colorScheme.replyModifier) {
        image?.let {
            Box {
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

                WatchAuthor(baseNote = note, accountViewModel) { user ->
                    Box(authorNotePictureForImageHeader.align(Alignment.BottomStart)) {
                        BaseUserPicture(user, Size55dp, accountViewModel, Modifier)
                    }
                }
            }
        } ?: run {
            DefaultImageHeader(note, accountViewModel, Modifier.fillMaxWidth())
        }
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp),
            )
        }

        summary?.let {
            Spacer(modifier = StdVertSpacer)
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
