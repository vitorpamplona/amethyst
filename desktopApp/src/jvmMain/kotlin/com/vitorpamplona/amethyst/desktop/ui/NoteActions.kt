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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.Reply
import com.vitorpamplona.amethyst.commons.icons.Repost
import com.vitorpamplona.amethyst.commons.model.nip18Reposts.RepostAction
import com.vitorpamplona.amethyst.commons.model.nip25Reactions.ReactionAction
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Action buttons row for a note (react, reply, repost).
 */
@Composable
fun NoteActionsRow(
    event: Event,
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn,
    onReplyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLiked by remember { mutableStateOf(false) }
    var isReposted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Reply button
        IconButton(
            onClick = onReplyClick,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Reply,
                contentDescription = "Reply",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        // Like button
        IconButton(
            onClick = {
                if (!isLiked) {
                    scope.launch {
                        reactToNote(
                            event = event,
                            reaction = "+",
                            account = account,
                            relayManager = relayManager,
                        )
                        isLiked = true
                    }
                }
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                tint =
                    if (isLiked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(18.dp),
            )
        }

        // Repost button
        IconButton(
            onClick = {
                if (!isReposted) {
                    scope.launch {
                        repostNote(
                            event = event,
                            account = account,
                            relayManager = relayManager,
                        )
                        isReposted = true
                    }
                }
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Repost,
                contentDescription = "Repost",
                tint =
                    if (isReposted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(18.dp),
            )
        }

        // Placeholder for action count
        Text(
            "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Creates a reaction event and broadcasts to relays.
 */
private suspend fun reactToNote(
    event: Event,
    reaction: String,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
) {
    withContext(Dispatchers.IO) {
        // Use shared ReactionAction from commons
        val signedEvent = ReactionAction.reactTo(event, reaction, account.signer)

        // Broadcast to all relays
        relayManager.broadcastToAll(signedEvent)
    }
}

/**
 * Creates a repost event and broadcasts to relays.
 */
private suspend fun repostNote(
    event: Event,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
) {
    withContext(Dispatchers.IO) {
        // Use shared RepostAction from commons
        val signedEvent = RepostAction.repost(event, account.signer)

        // Broadcast to all relays
        relayManager.broadcastToAll(signedEvent)
    }
}
