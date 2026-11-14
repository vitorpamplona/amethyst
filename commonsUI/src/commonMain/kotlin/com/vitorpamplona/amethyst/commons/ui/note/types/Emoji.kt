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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.emoji_pack_emoji_count
import com.vitorpamplona.amethyst.commons.resources.kind_emoji_packs
import com.vitorpamplona.amethyst.commons.resources.nip51_list_has_private_members
import com.vitorpamplona.amethyst.commons.ui.components.AddButton
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.RemoveButton
import com.vitorpamplona.amethyst.commons.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.getGradient
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.hidesPrivateMembers
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.listTitle
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.loadPrivateItems
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.rememberAllMembers
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiListEvent
import kotlinx.collections.immutable.toImmutableMap

/** How many emojis a collapsed pack shows: about four rows on a phone, before "Show more". */
private const val COLLAPSED_EMOJIS = 32

private val CoverSize = 64.dp
private val CoverShape = RoundedCornerShape(12.dp)
private val TileSize = 48.dp
private val TileImageSize = 40.dp
private val TileShape = RoundedCornerShape(8.dp)

@Composable
fun RenderEmojiPack(
    baseNote: Note,
    actionable: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
) {
    val noteEvent by observeNoteEvent<EmojiPackEvent>(baseNote, accountViewModel)

    noteEvent?.let {
        RenderEmojiPack(
            noteEvent = it,
            baseNote = baseNote,
            actionable = actionable,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
        )
    }
}

/**
 * A kind 30030 emoji pack as a feed card: the pack's cover, title, size and description on top,
 * then the emojis themselves as a grid of even tiles. Hovering a tile on desktop names its
 * `:shortcode:`, which is what a reader types to use it.
 *
 * Private emojis are decrypted only for the pack's author ([loadPrivateItems]); everyone else sees
 * the public ones, and an all-private pack says so instead of rendering as an empty card.
 */
@Composable
fun RenderEmojiPack(
    noteEvent: EmojiPackEvent,
    baseNote: Note,
    actionable: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
) {
    val publicEmojis = remember(noteEvent) { noteEvent.publicEmojis() }
    val privateEmojis by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateEmojis(it) }
    val allEmojis = rememberAllMembers(publicEmojis, privateEmojis)

    val title = listTitle(noteEvent.titleOrName(), noteEvent.dTag(), Res.string.kind_emoji_packs)
    val cover = remember(noteEvent) { noteEvent.image()?.takeIf { it.isNotBlank() } }
    val description = remember(noteEvent) { noteEvent.description()?.takeIf { it.isNotBlank() } }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalArrangement = spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = spacedBy(12.dp),
        ) {
            EmojiPackCover(cover, title)

            Column(modifier = Modifier.weight(1f), verticalArrangement = spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringRes(Res.plurals.emoji_pack_emoji_count, allEmojis.size, allEmojis.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            if (actionable) {
                EmojiListOptions(accountViewModel, baseNote)
            }
        }

        description?.let {
            // A pack's description often shows off its own emojis, so resolve them from the pack.
            val emojiMap = remember(allEmojis) { allEmojis.associate { it.code to it.url }.toImmutableMap() }
            CreateTextWithEmoji(
                text = it,
                emojis = emojiMap,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (allEmojis.isEmpty()) {
            if (noteEvent.hidesPrivateMembers(privateEmojis)) {
                Text(
                    text = stringRes(Res.string.nip51_list_has_private_members),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            EmojiPackGrid(allEmojis, backgroundColor)
        }
    }
}

/** The pack's `image`, or an emoji glyph on a tinted tile when it has none, so every header lines up. */
@Composable
private fun EmojiPackCover(
    cover: String?,
    title: String,
) {
    val tint = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = remember(tint) { Modifier.size(CoverSize).clip(CoverShape).background(tint) },
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = title,
                modifier = Modifier.size(CoverSize),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                symbol = MaterialSymbols.EmojiEmotions,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmojiPackGrid(
    emojis: List<EmojiUrlTag>,
    backgroundColor: MutableState<Color>,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded || emojis.size <= COLLAPSED_EMOJIS) emojis else emojis.subList(0, COLLAPSED_EMOJIS)

    val tileColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val tileModifier = remember(tileColor) { Modifier.size(TileSize).clip(TileShape).background(tileColor) }

    Box(Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = spacedBy(6.dp),
            verticalArrangement = spacedBy(6.dp),
        ) {
            shown.forEach { emoji -> EmojiTile(emoji, tileModifier) }
        }

        if (!expanded && emojis.size > COLLAPSED_EMOJIS) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton { expanded = true }
            }
        }
    }
}

@Composable
private fun EmojiTile(
    emoji: EmojiUrlTag,
    tileModifier: Modifier,
) {
    val label = remember(emoji.code) { ":${emoji.code}:" }
    HoverTooltip(label) {
        Box(modifier = tileModifier, contentAlignment = Alignment.Center) {
            AsyncImage(
                model = emoji.url,
                contentDescription = label,
                modifier = Modifier.size(TileImageSize),
                // Fit, not Crop: a wide logo or a tall sticker keeps its whole shape.
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun EmojiListOptions(
    accountViewModel: AccountViewModel,
    emojiPackNote: Note,
) {
    LoadAddressableNote(
        accountViewModel.account.emoji.getEmojiListAddress(),
    ) {
        it?.let { usersEmojiList ->
            val hasAddedThis by observeNoteAndMap(usersEmojiList, accountViewModel) {
                (usersEmojiList.event as? EmojiListEvent)?.hasEmojiPack(emojiPackNote.idHex)
            }

            CrossfadeIfEnabled(targetState = hasAddedThis, label = "EmojiListOptions") {
                if (it != true) {
                    AddButton(modifier = Modifier.padding(start = 3.dp)) { accountViewModel.addEmojiPack(emojiPackNote) }
                } else {
                    RemoveButton(modifier = Modifier.padding(start = 3.dp)) { accountViewModel.removeEmojiPack(emojiPackNote) }
                }
            }
        }
    }
}
