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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.elements.AddButton
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.RemoveButton
import com.vitorpamplona.amethyst.ui.note.getGradient
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.events.EmojiPackEvent
import com.vitorpamplona.quartz.events.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.events.EmojiUrl
import com.vitorpamplona.quartz.events.WikiNoteEvent

@Composable
fun RenderWikiContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? WikiNoteEvent ?: return

    WikiNoteHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
private fun WikiNoteHeader(
    noteEvent: WikiNoteEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val title = remember(noteEvent) { noteEvent.title() }
    val summary =
        remember(noteEvent) {
            noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null }
        }
    val image = remember(noteEvent) { noteEvent.image() }

    Row(
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
        Column {
            val automaticallyShowUrlPreview =
                remember { accountViewModel.settings.showUrlPreview.value }

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
}

@Composable
public fun RenderEmojiPack(
    baseNote: Note,
    actionable: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    onClick: ((EmojiUrl) -> Unit)? = null,
) {
    val noteEvent by
        baseNote
            .live()
            .metadata
            .map { it.note.event }
            .distinctUntilChanged()
            .observeAsState(baseNote.event)

    if (noteEvent == null || noteEvent !is EmojiPackEvent) return

    (noteEvent as? EmojiPackEvent)?.let {
        RenderEmojiPack(
            noteEvent = it,
            baseNote = baseNote,
            actionable = actionable,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun RenderEmojiPack(
    noteEvent: EmojiPackEvent,
    baseNote: Note,
    actionable: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    onClick: ((EmojiUrl) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    val allEmojis = remember(noteEvent) { noteEvent.taggedEmojis() }

    val emojisToShow =
        if (expanded) {
            allEmojis
        } else {
            allEmojis.take(60)
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = remember(noteEvent) { "#${noteEvent.dTag()}" },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1F)
                    .padding(5.dp),
            textAlign = TextAlign.Center,
        )

        if (actionable) {
            EmojiListOptions(accountViewModel, baseNote)
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        FlowRow(modifier = Modifier.padding(top = 5.dp)) {
            emojisToShow.forEach { emoji ->
                if (onClick != null) {
                    IconButton(onClick = { onClick(emoji) }, modifier = Size35Modifier) {
                        AsyncImage(
                            model = emoji.url,
                            contentDescription = null,
                            modifier = Size35Modifier,
                        )
                    }
                } else {
                    Box(
                        modifier = Size35Modifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = emoji.url,
                            contentDescription = null,
                            modifier = Size35Modifier,
                        )
                    }
                }
            }
        }

        if (allEmojis.size > 60 && !expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton { expanded = !expanded }
            }
        }
    }
}

@Composable
private fun EmojiListOptions(
    accountViewModel: AccountViewModel,
    emojiPackNote: Note,
) {
    LoadAddressableNote(
        aTag =
            ATag(
                EmojiPackSelectionEvent.KIND,
                accountViewModel.userProfile().pubkeyHex,
                "",
                null,
            ),
        accountViewModel,
    ) {
        it?.let { usersEmojiList ->
            val hasAddedThis by
                remember {
                    usersEmojiList
                        .live()
                        .metadata
                        .map { usersEmojiList.event?.isTaggedAddressableNote(emojiPackNote.idHex) }
                        .distinctUntilChanged()
                }
                    .observeAsState()

            Crossfade(targetState = hasAddedThis, label = "EmojiListOptions") {
                if (it != true) {
                    AddButton { accountViewModel.addEmojiPack(usersEmojiList, emojiPackNote) }
                } else {
                    RemoveButton { accountViewModel.removeEmojiPack(usersEmojiList, emojiPackNote) }
                }
            }
        }
    }
}
