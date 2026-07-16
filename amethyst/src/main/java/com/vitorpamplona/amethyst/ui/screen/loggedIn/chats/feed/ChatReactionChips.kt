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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.ui.components.AnimatedBorderTextCornerRadius
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteMinichatReplyCount
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteReactions
import com.vitorpamplona.amethyst.ui.components.InLineIconRenderer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.LikedIcon
import com.vitorpamplona.amethyst.ui.note.ObserveZapAmountText
import com.vitorpamplona.amethyst.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.Size14Modifier
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
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

private val ChipGlyphFontSize = 13.sp

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
    // A geohash chat provides these so reactions stay under its anonymous per-cell identity
    // (react + own-highlight); null (every other chat) keeps the standard account behavior.
    val onReact = LocalChatReactOverride.current
    val myIdentities = LocalChatActingIdentities.current
    // Deliberate cost: this observes reactions/zaps for EVERY visible message
    // (the ids fold into the batched EventFinder relay filters — one shared REQ,
    // not one per note), which is exactly what ReactionsRow already does for
    // every note in the main feeds. Gating on already-known engagement would be
    // cheaper but would never DISCOVER reactions for messages nobody expanded.
    val reactionsState by observeNoteReactions(baseNote, accountViewModel)

    val chips by
        remember(reactionsState, myIdentities) {
            derivedStateOf {
                val note = reactionsState?.note ?: baseNote
                val me = accountViewModel.userProfile()
                note.reactions
                    .map { (type, reactors) ->
                        val mine =
                            if (myIdentities != null) {
                                reactors.any { it.author?.pubkeyHex in myIdentities }
                            } else {
                                reactors.any { it.author == me }
                            }
                        ReactionChip(type, reactors.size, mine)
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

    // Kind-1111 thread ("minichat") replies. Only public chats wire them up (Concord,
    // NIP-28, NIP-29); NIP-17 DMs deliberately don't, so most clients wouldn't render
    // a thread there. An inline reply is an ordinary message and isn't counted.
    val supportsMinichat =
        remember(baseNote) {
            baseNote.inGatherers?.any { it is ConcordChannel || it is PublicChatChannel || it is RelayGroupChannel } == true
        }
    val minichatCount by if (supportsMinichat) observeNoteMinichatReplyCount(baseNote, accountViewModel) else remember { mutableStateOf(0) }

    // Optimistic feedback for a zap the user just fired from the drawer: the receipt (and
    // its amount) only reach relays a moment later, so show a pending zap chip until then.
    // The set changes only at zap start/end, so this rarely re-runs the row.
    val zapsInFlight by accountViewModel.zapsInFlightFlow.collectAsStateWithLifecycle()
    val isZapping = zapsInFlight.contains(baseNote.idHex)

    ObserveZapAmountText(baseNote, accountViewModel) { zapAmount ->
        RenderChatReactionChips(
            chips = chips,
            zapAmount = zapAmount,
            minichatCount = minichatCount,
            // Once the receipt's amount arrives the real sats chip takes over, so the
            // pending chip only shows while the amount is still blank.
            isZapping = isZapping && zapAmount.isBlank(),
            onToggleReaction = { onReact?.invoke(baseNote, it) ?: accountViewModel.reactToOrDelete(baseNote, it) },
            onOpenDetails = { showDetails = true },
            onOpenMinichat = { nav.nav(Route.ChatMinichat(baseNote.idHex)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderChatReactionChips(
    chips: ImmutableList<ReactionChip>,
    zapAmount: String,
    minichatCount: Int,
    isZapping: Boolean,
    onToggleReaction: (String) -> Unit,
    onOpenDetails: () -> Unit,
    onOpenMinichat: () -> Unit,
) {
    if (chips.isEmpty() && zapAmount.isBlank() && minichatCount <= 0 && !isZapping) return

    FlowRow(
        // Inset from the bubble's edge so overlapping chips ride the border without
        // poking past the bubble's rounded corners.
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (zapAmount.isNotBlank()) {
            ZapChip(zapAmount, onClick = onOpenDetails)
        } else if (isZapping) {
            PendingZapChip()
        }

        chips.forEach { chip ->
            ReactionChipView(
                chip = chip,
                onClick = { onToggleReaction(chip.type) },
                onLongClick = onOpenDetails,
            )
        }

        if (minichatCount > 0) {
            MinichatChip(minichatCount, onClick = onOpenMinichat)
        }
    }
}

/**
 * "N replies" thread entry, styled like the reaction/zap chips so the engagement row
 * reads as one strip under the bubble. Tapping opens the message's minichat thread.
 */
@Composable
private fun MinichatChip(
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.subtleBorder),
        // Plain clickable (not the Surface onClick overload) so the chip keeps its
        // content height instead of being padded to the 48dp minimum touch target,
        // which would drop it below the bubble border the reaction chips ride.
        modifier = Modifier.clip(ButtonBorder).clickable(onClick = onClick),
    ) {
        ChipContentRow {
            Icon(
                symbol = MaterialSymbols.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.grayText,
                modifier = Size14Modifier,
            )
            Text(
                text = pluralStringResource(R.plurals.chat_minichat_reply_count, count, count),
                fontSize = Font12SP,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
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
                    fontSize = Font12SP,
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
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.subtleBorder),
        // Plain clickable (not the Surface onClick overload) so the chip keeps its
        // content height instead of being padded to the 48dp minimum touch target,
        // which would drop it below the bubble border the reaction chips ride.
        modifier = Modifier.clip(ButtonBorder).clickable(onClick = onClick),
    ) {
        ChipContentRow {
            ZappedIcon(Size14Modifier)
            Text(
                text = amount,
                fontSize = Font12SP,
                fontWeight = FontWeight.Bold,
                // Match the lightning glyph's bitcoin orange (theme-aware for contrast).
                color = MaterialTheme.colorScheme.bitcoinColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * Optimistic placeholder shown while a zap the user just fired is still being paid:
 * the lightning glyph next to a small spinner. Replaced by the real sats [ZapChip]
 * as soon as the receipt's amount arrives.
 */
@Composable
private fun PendingZapChip() {
    Surface(
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.subtleBorder),
    ) {
        ChipContentRow {
            ZappedIcon(Size14Modifier)
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.grayText,
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
            fontSize = ChipGlyphFontSize,
            maxLines = 1,
        )
    } else {
        when (reactionType) {
            "+" -> LikedIcon(Size14Modifier)
            "-" -> Text(text = "👎", fontSize = ChipGlyphFontSize, maxLines = 1)
            else -> {
                if (EmojiCoder.isCoded(reactionType)) {
                    AnimatedBorderTextCornerRadius(reactionType, fontSize = ChipGlyphFontSize)
                } else {
                    Text(text = reactionType, fontSize = ChipGlyphFontSize, maxLines = 1)
                }
            }
        }
    }
}
