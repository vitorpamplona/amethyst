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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.createLiveActivitiesSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * "LIVE NOW" section for the Desktop Discover screen: subscribes to NIP-53 live streams (kind
 * 30311) while visible, ranks them via the shared [LiveActivityRanking] (live > planned > ended,
 * follow-participation, then viewers), and offers a client-side search over title / host / hashtag.
 * Clicking a card invokes [onOpenLive] with the stream's address (`kind:pubkey:d`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LivesSection(
    cache: DesktopLocalCache,
    relayManager: RelayConnectionManager,
    followSet: Set<String>,
    onOpenLive: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()

    // Subscribe to global live streams while this section is composed; torn down on dispose.
    rememberSubscription(connectedRelays, relayManager = relayManager) {
        if (connectedRelays.isEmpty()) {
            null
        } else {
            createLiveActivitiesSubscription(
                relays = connectedRelays,
                authors = null,
                onEvent = { event, _, relay, _ -> cache.consume(event, relay) },
            )
        }
    }

    val version by cache.liveActivityVersion.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    val ranked =
        remember(version, query, followSet) {
            LiveActivityRanking
                .rankForDiscover(cache.snapshotLiveActivities(), followSet)
                .filter { LiveActivityRanking.matchesQuery(it, query) }
        }

    // Hide the whole section until at least one stream is known (keeps Discover clean when empty).
    if (ranked.isEmpty() && query.isBlank()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LiveDot()
            Spacer(Modifier.width(6.dp))
            Text(
                text = "LIVE NOW",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search live streams…") },
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        if (ranked.isEmpty()) {
            Text(
                text = "No live streams match \"$query\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ranked.forEach { channel ->
                    LiveStreamCard(
                        channel = channel,
                        onClick = { onOpenLive(channel.address.toValue()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveStreamCard(
    channel: LiveActivitiesChannel,
    onClick: () -> Unit,
) {
    val info = channel.info
    Card(
        modifier =
            Modifier
                .width(240.dp)
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box {
            val image = channel.profilePicture()
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(MaterialTheme.colorScheme.surface),
                )
            }
            StatusBadge(
                channel = channel,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = channel.toBestDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val host = channel.creatorName()
            if (host != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = host,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val viewers = info?.currentParticipants()
            if (viewers != null && LiveActivityRanking.isLive(channel)) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$viewers watching",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    channel: LiveActivitiesChannel,
    modifier: Modifier = Modifier,
) {
    val status = channel.info?.status()
    when (status) {
        StatusTag.STATUS.LIVE ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xE6D32F2F))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }

        StatusTag.STATUS.PLANNED ->
            Text(
                text = startsInLabel(channel.info?.starts()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier =
                    modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )

        else -> Unit
    }
}

@Composable
private fun LiveDot() {
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFD32F2F)),
    )
}

private fun startsInLabel(startsAt: Long?): String {
    if (startsAt == null) return "SCHEDULED"
    val delta = startsAt - TimeUtils.now()
    if (delta <= 0) return "SCHEDULED"
    val hours = delta / 3600
    val minutes = (delta % 3600) / 60
    return when {
        hours >= 24 -> "in ${hours / 24}d"
        hours >= 1 -> "in ${hours}h"
        else -> "in ${minutes}m"
    }
}
