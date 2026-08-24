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
package com.vitorpamplona.amethyst.desktop.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.createLiveChatSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.media.DesktopVideoPlayer
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * Full-window watch screen for a NIP-53 live stream: HLS player on the left, live chat (kind 1311)
 * on the right, host/metadata + status below the player. Rendered as an app-root overlay driven by
 * [LiveWatchController]; [onClose] tears it down and returns to the deck / single-pane view.
 *
 * v1 scope: watch + read + post chat. Zap-the-stream and the live-vs-VOD player refinements
 * (seek-bar suppression, stall watchdog) are follow-ups.
 */
@Composable
fun LiveWatchScreen(
    address: String,
    cache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn?,
    onNavigateToProfile: (String) -> Unit,
    onClose: () -> Unit,
) {
    val channel =
        remember(address) {
            Address.parse(address)?.let { cache.getOrCreateLiveActivityChannel(it) }
        }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (channel == null) {
            WatchError("This stream could not be opened.", onClose)
            return@Surface
        }

        val connectedRelays by relayManager.connectedRelays.collectAsState()

        // Live chat subscription for this stream (kind 1311 by the root `a` tag), torn down on close.
        rememberSubscription(connectedRelays, address, relayManager = relayManager) {
            if (connectedRelays.isEmpty()) {
                null
            } else {
                createLiveChatSubscription(
                    relays = connectedRelays,
                    streamAddress = address,
                    onEvent = { event, _, relay, _ -> cache.consume(event, relay) },
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
            ) {
                IconButton(onClick = onClose) {
                    SymbolIcon(symbol = MaterialSymbols.Close, contentDescription = "Close")
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = channel.toBestDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider()

            Row(modifier = Modifier.fillMaxSize()) {
                // Left: player + metadata
                Column(modifier = Modifier.weight(1f).fillMaxSize().padding(16.dp)) {
                    val streamUrl = channel.info?.streaming()
                    if (streamUrl != null) {
                        DesktopVideoPlayer(
                            url = streamUrl,
                            autoPlay = true,
                            isLive = channel.info?.status() == StatusTag.STATUS.LIVE,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Stream is offline", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    WatchHeader(channel = channel, onNavigateToProfile = onNavigateToProfile)
                }

                VerticalDivider()

                // Right: live chat
                Column(modifier = Modifier.width(380.dp).fillMaxSize()) {
                    Text(
                        text = "LIVE CHAT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                    HorizontalDivider()
                    ChatColumn(
                        channel = channel,
                        account = account,
                        relayManager = relayManager,
                        onNavigateToProfile = onNavigateToProfile,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchHeader(
    channel: com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel,
    onNavigateToProfile: (String) -> Unit,
) {
    val info = channel.info
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (info?.status() == StatusTag.STATUS.LIVE) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD32F2F)))
            Spacer(Modifier.width(6.dp))
            Text("LIVE", style = MaterialTheme.typography.labelMedium, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
        }
        val host = channel.creatorName()
        val hostHex = channel.creator?.pubkeyHex
        if (host != null) {
            Text(
                text = host,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    if (hostHex != null) {
                        Modifier.clickable { onNavigateToProfile(hostHex) }
                    } else {
                        Modifier
                    },
            )
        }
        Spacer(Modifier.weight(1f))
        val viewers = info?.currentParticipants()
        if (viewers != null) {
            Text("$viewers watching", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val summary = channel.summary()
    if (!summary.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChatColumn(
    channel: com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel,
    account: AccountState.LoggedIn?,
    relayManager: DesktopRelayConnectionManager,
    onNavigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notesState by channel
        .flow()
        .notes.stateFlow
        .collectAsState()
    // Newest first so reverseLayout puts the latest message at the visual bottom.
    val messages =
        remember(notesState) {
            channel.notes
                .values()
                .filter { it.event is LiveActivitiesChatMessageEvent }
                .sortedByDescending { it.createdAt() ?: 0L }
        }

    val listState = rememberLazyListState()
    // Auto-stick to the newest message while the user is at the bottom (index 0 in reverseLayout).
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(messages, key = { it.idHex }) { note ->
                ChatMessageRow(
                    author = note.author?.toBestDisplayName() ?: "anon",
                    authorHex = note.author?.pubkeyHex,
                    content = note.event?.content.orEmpty(),
                    onNavigateToProfile = onNavigateToProfile,
                )
            }
        }
        HorizontalDivider()
        ChatComposer(channel = channel, account = account, relayManager = relayManager)
    }
}

@Composable
private fun ChatMessageRow(
    author: String,
    authorHex: String?,
    content: String,
    onNavigateToProfile: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = author,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                if (authorHex != null) {
                    Modifier.clickable { onNavigateToProfile(authorHex) }
                } else {
                    Modifier
                },
        )
        Text(text = content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChatComposer(
    channel: com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel,
    account: AccountState.LoggedIn?,
    relayManager: DesktopRelayConnectionManager,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val canPost = account != null && !account.isReadOnly

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            enabled = canPost && !sending,
            singleLine = true,
            placeholder = { Text(if (canPost) "Say something…" else "Log in to chat") },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            enabled = canPost && !sending && text.isNotBlank(),
            onClick = {
                val acct = account ?: return@TextButton
                val body = text.trim()
                if (body.isBlank()) return@TextButton
                sending = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val template = LiveActivitiesChatMessageEvent.message(body, channel.toATag())
                            val signed = acct.signer.sign(template)
                            relayManager.publish(signed, relayManager.connectedRelays.value)
                        }
                        text = ""
                    } finally {
                        sending = false
                    }
                }
            },
        ) {
            Text("Send")
        }
    }
}

@Composable
private fun WatchError(
    message: String,
    onClose: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onClose) { Text("Close") }
    }
}
