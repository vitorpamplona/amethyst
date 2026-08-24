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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.createLiveActivitiesSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription

/**
 * Compact pinned "live now" bar for the top of a feed column. Shows the single most-watched live
 * host in the column's audience ([scopeAuthors] = the column's follows, or null for the global
 * feed) plus a "+N live ›" affordance that expands the rest. Hidden entirely when nobody in scope
 * is live. Clicking a host opens the watch screen via [onOpenLive].
 */
@Composable
fun LiveNowBar(
    cache: DesktopLocalCache,
    relayManager: RelayConnectionManager,
    scopeAuthors: List<String>?,
    onOpenLive: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()

    rememberSubscription(connectedRelays, scopeAuthors, relayManager = relayManager) {
        if (connectedRelays.isEmpty()) {
            null
        } else {
            createLiveActivitiesSubscription(
                relays = connectedRelays,
                authors = scopeAuthors,
                onEvent = { event, _, relay, _ -> cache.consume(event, relay) },
            )
        }
    }

    val version by cache.liveActivityVersion.collectAsState()
    val liveNow =
        remember(version, scopeAuthors) {
            LiveActivityRanking.liveNowForBar(cache.snapshotLiveActivities(), scopeAuthors?.toSet())
        }

    if (liveNow.isEmpty()) return

    val top = liveNow.first()
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onOpenLive(top.address.toValue()) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        LiveDotSmall()
        Spacer(Modifier.width(8.dp))
        Text(
            text = topLabel(top.toBestDisplayName()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (liveNow.size > 1) {
            Spacer(Modifier.width(8.dp))
            Box {
                Text(
                    text = "+${liveNow.size - 1} live ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { expanded = true },
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    liveNow.forEach { channel ->
                        DropdownMenuItem(
                            text = { Text(channel.toBestDisplayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                expanded = false
                                onOpenLive(channel.address.toValue())
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun topLabel(name: String): String = "$name is live"

@Composable
private fun LiveDotSmall() {
    Box(
        modifier =
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFFD32F2F)),
    )
}
