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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.ui.components.AnimatedBorderTextCornerRadius
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteReactions
import com.vitorpamplona.amethyst.ui.components.InLineIconRenderer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LikedIcon
import com.vitorpamplona.amethyst.ui.note.ObserveZapAmountText
import com.vitorpamplona.amethyst.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Size14Modifier
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
private data class ReactionChip(
    val type: String,
    val count: Int,
    val includesMe: Boolean,
)

/**
 * Messenger-style pills under a chat bubble summarizing who engaged with the message:
 * one chip per reaction emoji (with count, highlighted when the logged-in user is
 * among the reactors — tapping toggles the same reaction back) plus a sats chip when
 * the message was zapped. Renders nothing when the message has no engagement.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatReactionChips(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val reactionsState by observeNoteReactions(baseNote, accountViewModel)

    val chips by
        remember(reactionsState) {
            derivedStateOf {
                val note = reactionsState?.note ?: baseNote
                val me = accountViewModel.userProfile()
                note.reactions
                    .map { (type, reactors) ->
                        ReactionChip(type, reactors.size, reactors.any { it.author == me })
                    }.sortedByDescending { it.count }
                    .toImmutableList()
            }
        }

    var showDetails by remember { mutableStateOf(false) }

    if (showDetails) {
        ChatEngagementDetailSheet(
            baseNote = baseNote,
            onDismiss = { showDetails = false },
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    ObserveZapAmountText(baseNote, accountViewModel) { zapAmount ->
        RenderChatReactionChips(
            chips = chips,
            zapAmount = zapAmount,
            onToggleReaction = { accountViewModel.reactToOrDelete(baseNote, it) },
            onOpenDetails = { showDetails = true },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderChatReactionChips(
    chips: ImmutableList<ReactionChip>,
    zapAmount: String,
    onToggleReaction: (String) -> Unit,
    onOpenDetails: () -> Unit,
) {
    if (chips.isEmpty() && zapAmount.isBlank()) return

    FlowRow(
        // Inset from the bubble's edge so overlapping chips ride the border without
        // poking past the bubble's rounded corners.
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (zapAmount.isNotBlank()) {
            ZapChip(zapAmount, onClick = onOpenDetails)
        }

        chips.forEach { chip ->
            ReactionChipView(
                chip = chip,
                onClick = { onToggleReaction(chip.type) },
                onLongClick = onOpenDetails,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReactionChipView(
    chip: ReactionChip,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val background =
        if (chip.includesMe) {
            MaterialTheme.colorScheme.primary
                .copy(alpha = 0.20f)
                .compositeOver(MaterialTheme.colorScheme.background)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    val border =
        if (chip.includesMe) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.60f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.subtleBorder)
        }

    Surface(
        shape = ButtonBorder,
        color = background,
        border = border,
        modifier =
            Modifier
                .clip(ButtonBorder)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        ChipContentRow {
            ChipReactionGlyph(chip.type)
            if (chip.count > 1) {
                Text(
                    text = chip.count.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ZapChip(
    amount: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.subtleBorder),
    ) {
        ChipContentRow {
            ZappedIcon(Size14Modifier)
            Text(
                text = amount,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ChipContentRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
internal fun ChipReactionGlyph(reactionType: String) {
    if (reactionType.startsWith(":")) {
        val url = reactionType.removePrefix(":").substringAfter(":")
        InLineIconRenderer(
            persistentListOf(CustomEmoji.ImageUrlType(url)),
            style = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
            fontSize = 13.sp,
            maxLines = 1,
        )
    } else {
        when (reactionType) {
            "+" -> LikedIcon(Size14Modifier)
            "-" -> Text(text = "👎", fontSize = 13.sp, maxLines = 1)
            else -> {
                if (EmojiCoder.isCoded(reactionType)) {
                    AnimatedBorderTextCornerRadius(reactionType, fontSize = 13.sp)
                } else {
                    Text(text = reactionType, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}
