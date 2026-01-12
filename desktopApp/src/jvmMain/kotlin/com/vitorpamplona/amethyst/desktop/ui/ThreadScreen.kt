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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.state.EventCollectionState
import com.vitorpamplona.amethyst.commons.subscriptions.createNoteSubscription
import com.vitorpamplona.amethyst.commons.subscriptions.createThreadRepliesSubscription
import com.vitorpamplona.amethyst.commons.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.note.NoteCard
import com.vitorpamplona.amethyst.commons.ui.thread.drawReplyLevel
import com.vitorpamplona.amethyst.commons.util.toNoteDisplayData
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Desktop Thread Screen - displays a note and all its replies in a thread view.
 *
 * Uses the shared drawReplyLevel modifier from commons to display reply nesting.
 */
@Composable
fun ThreadScreen(
    noteId: String,
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn?,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val scope = rememberCoroutineScope()

    // State for the root note
    var rootNote by remember { mutableStateOf<Event?>(null) }

    // State for reply events
    val replyEventState =
        remember(noteId) {
            EventCollectionState<Event>(
                getId = { it.id },
                sortComparator = compareBy { it.createdAt },
                maxSize = 500,
                scope = scope,
            )
        }
    val replyEvents by replyEventState.items.collectAsState()

    // Cache for calculating reply levels
    val levelCache = remember(noteId) { mutableMapOf<String, Int>() }

    // Subscribe to the root note
    rememberSubscription(relayStatuses, noteId, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            createNoteSubscription(
                relays = configuredRelays,
                noteId = noteId,
                onEvent = { event, _, _, _ ->
                    if (event.id == noteId) {
                        rootNote = event
                        levelCache[event.id] = 0
                    }
                },
            )
        } else {
            null
        }
    }

    // Subscribe to replies
    rememberSubscription(relayStatuses, noteId, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            createThreadRepliesSubscription(
                relays = configuredRelays,
                noteId = noteId,
                onEvent = { event, _, _, _ ->
                    replyEventState.addItem(event)
                },
            )
        } else {
            null
        }
    }

    // Calculate reply level for an event based on e-tags
    fun calculateLevel(event: Event): Int {
        levelCache[event.id]?.let { return it }

        // Find the event this is replying to (last e-tag or marked reply/root)
        val replyToId = findReplyToId(event)
        val level =
            if (replyToId == null || replyToId == noteId) {
                1 // Direct reply to root
            } else {
                (levelCache[replyToId] ?: 0) + 1
            }
        levelCache[event.id] = level
        return level
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Thread",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (connectedRelays.isEmpty()) {
            LoadingState("Connecting to relays...")
        } else if (rootNote == null) {
            LoadingState("Loading thread...")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // Root note (no reply level indicator)
                item(key = noteId) {
                    Column(
                        modifier =
                            Modifier.clickable {
                                // Already viewing this thread, no-op
                            },
                    ) {
                        NoteCard(
                            note = rootNote!!.toNoteDisplayData(),
                            onAuthorClick = onNavigateToProfile,
                        )
                        if (account != null) {
                            NoteActionsRow(
                                event = rootNote!!,
                                relayManager = relayManager,
                                account = account,
                                onReplyClick = { /* TODO: Open reply dialog */ },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp)
                }

                // Reply notes with level indicators
                items(replyEvents, key = { it.id }) { event ->
                    val level = calculateLevel(event)

                    Column(
                        modifier =
                            Modifier
                                .drawReplyLevel(
                                    level = level,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    selected =
                                        if (event.id == noteId) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                ).clickable {
                                    onNavigateToThread(event.id)
                                },
                    ) {
                        NoteCard(
                            note = event.toNoteDisplayData(),
                            onAuthorClick = onNavigateToProfile,
                        )
                        if (account != null) {
                            NoteActionsRow(
                                event = event,
                                relayManager = relayManager,
                                account = account,
                                onReplyClick = { /* TODO: Open reply dialog */ },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp)
                }

                // Empty state for no replies
                if (replyEvents.isEmpty()) {
                    item {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "No replies yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Finds the event ID this event is replying to.
 * Uses NIP-10 markers (reply/root) or falls back to last e-tag.
 */
private fun findReplyToId(event: Event): String? {
    val eTags = event.tags.filter { it.size >= 2 && it[0] == "e" }
    if (eTags.isEmpty()) return null

    // Check for NIP-10 marked tags first
    val replyTag = eTags.find { it.size >= 4 && it[3] == "reply" }
    if (replyTag != null) return replyTag[1]

    val rootTag = eTags.find { it.size >= 4 && it[3] == "root" }
    if (rootTag != null && eTags.size == 1) return rootTag[1]

    // Fall back to positional (last e-tag is the reply-to)
    return eTags.lastOrNull()?.get(1)
}
