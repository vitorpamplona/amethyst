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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteReactions
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
private data class ReactionEntry(
    val user: User,
    val reactionType: String,
    // The kind-7 reaction note itself, so the row can open it in its own thread
    // view (replies and reactions TO the reaction).
    val reactionNote: Note,
)

/**
 * Who engaged with a chat message: every zapper with their amount and comment,
 * then every reactor with the emoji they sent. Opened from the bubble's
 * engagement chips (long-press a reaction chip, tap the sats chip). Rows navigate
 * to the user's profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatEngagementDetailSheet(
    baseNote: Note,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val reactionsState by observeNoteReactions(baseNote, accountViewModel)

    val reactionEntries =
        remember(reactionsState) {
            buildReactionEntries(reactionsState?.note ?: baseNote)
        }

    val zaps by
        produceState<ImmutableList<ZapAmountCommentNotification>>(initialValue = persistentListOf(), baseNote) {
            accountViewModel.decryptAmountMessageInGroup(baseNote) { value = it }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            zaps.forEach { zap ->
                ZapperRow(zap, accountViewModel, nav, onDismiss)
            }

            reactionEntries.forEach { entry ->
                ReactorRow(entry, accountViewModel, nav, onDismiss)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun buildReactionEntries(note: Note): ImmutableList<ReactionEntry> =
    note.reactions
        .toList()
        .sortedByDescending { it.second.size }
        .flatMap { (type, reactionNotes) ->
            reactionNotes.mapNotNull { reactionNote ->
                reactionNote.author?.let { ReactionEntry(it, type, reactionNote) }
            }
        }.toImmutableList()

@Composable
private fun ZapperRow(
    zap: ZapAmountCommentNotification,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    // Open the zap receipt in its own thread view (replies to the
                    // zap itself); profile stays one tap away on the avatar. Falls
                    // back to the zapper's profile while the receipt is unknown.
                    val destination =
                        zap.zapNote?.let { routeFor(it, accountViewModel.account) }
                            ?: zap.user?.let { routeFor(it) }

                    destination?.let {
                        nav.nav(it)
                        onDismiss()
                    }
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            zap.user?.let { user ->
                UserPicture(user, Size25dp, Modifier, accountViewModel, nav)
                Row(modifier = Modifier.weight(1f)) {
                    UsernameDisplay(baseUser = user, accountViewModel = accountViewModel)
                }
            } ?: Spacer(Modifier.weight(1f))

            zap.amount?.let { amount ->
                Text(
                    text = "⚡ $amount",
                    color = BitcoinOrange,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        zap.comment?.takeIf { it.isNotBlank() }?.let { comment ->
            Text(
                text = comment,
                fontSize = Font12SP,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.padding(start = 33.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun ReactorRow(
    entry: ReactionEntry,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    // Open the reaction event in its own thread view; the avatar
                    // still navigates to the profile.
                    val destination =
                        routeFor(entry.reactionNote, accountViewModel.account)
                            ?: routeFor(entry.user)

                    nav.nav(destination)
                    onDismiss()
                },
    ) {
        UserPicture(entry.user, Size25dp, Modifier, accountViewModel, nav)
        Row(modifier = Modifier.weight(1f)) {
            UsernameDisplay(baseUser = entry.user, accountViewModel = accountViewModel)
        }
        ChipReactionGlyph(entry.reactionType)
    }
}
