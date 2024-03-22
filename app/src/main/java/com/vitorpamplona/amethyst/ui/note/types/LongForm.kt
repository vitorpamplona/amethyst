/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.events.LongTextNoteEvent

@Composable
fun RenderLongFormContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? LongTextNoteEvent ?: return

    LongFormHeader(noteEvent, note, accountViewModel)
}

@Composable
private fun LongFormHeader(
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

    Column(
        modifier =
            Modifier
                .padding(top = Size5dp)
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
    ) {
        val automaticallyShowUrlPreview =
            remember { accountViewModel.settings.showImages.value }

        if (automaticallyShowUrlPreview) {
            image?.let {
                AsyncImage(
                    model = it,
                    contentDescription =
                        stringResource(
                            R.string.preview_card_image_for,
                            it,
                        ),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: run {
                DefaultImageHeader(note, accountViewModel)
            }
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
