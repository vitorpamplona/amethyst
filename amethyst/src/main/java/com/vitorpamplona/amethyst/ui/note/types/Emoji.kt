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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteAndMap
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.elements.AddButton
import com.vitorpamplona.amethyst.ui.note.elements.RemoveButton
import com.vitorpamplona.amethyst.ui.note.getGradient
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.quartz.nip01Core.tags.addressables.isTaggedAddressableNote
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.taggedEmojis

@Composable
public fun RenderEmojiPack(
    baseNote: Note,
    actionable: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    onClick: ((EmojiUrlTag) -> Unit)? = null,
) {
    val noteEvent by observeNoteEvent<EmojiPackEvent>(baseNote, accountViewModel)

    noteEvent?.let {
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
    onClick: ((EmojiUrlTag) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    val allEmojis = remember(noteEvent) { noteEvent.taggedEmojis() }

    val emojisToShow =
        if (expanded) {
            allEmojis
        } else {
            allEmojis.take(60)
        }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
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
        FlowRow(
            modifier = Modifier.padding(top = 5.dp),
            verticalArrangement = spacedBy(1.dp),
            horizontalArrangement = spacedBy(1.dp),
        ) {
            emojisToShow.forEach { emoji ->
                if (onClick != null) {
                    AsyncImage(
                        model = emoji.url,
                        contentDescription = emoji.code,
                        modifier = Size35Modifier.clickable { onClick(emoji) },
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Size35Modifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = emoji.url,
                            contentDescription = emoji.code,
                            modifier = Size35Modifier,
                            contentScale = ContentScale.Crop,
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
        accountViewModel.account.emoji.getEmojiPackSelectionAddress(),
        accountViewModel,
    ) {
        it?.let { usersEmojiList ->
            val hasAddedThis by observeNoteAndMap(usersEmojiList, accountViewModel) {
                usersEmojiList.event?.isTaggedAddressableNote(emojiPackNote.idHex)
            }

            CrossfadeIfEnabled(targetState = hasAddedThis, label = "EmojiListOptions", accountViewModel = accountViewModel) {
                if (it != true) {
                    AddButton(modifier = Modifier.padding(start = 3.dp)) { accountViewModel.addEmojiPack(usersEmojiList, emojiPackNote) }
                } else {
                    RemoveButton(modifier = Modifier.padding(start = 3.dp)) { accountViewModel.removeEmojiPack(usersEmojiList, emojiPackNote) }
                }
            }
        }
    }
}
