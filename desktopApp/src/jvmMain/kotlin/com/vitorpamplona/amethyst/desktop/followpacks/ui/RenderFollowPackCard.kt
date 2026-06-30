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
package com.vitorpamplona.amethyst.desktop.followpacks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.vitorpamplona.amethyst.desktop.followpacks.FollowPackEditor
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress as NAddressEntity

/**
 * Inline rich card for `naddr` references to kind 39089 (Follow Pack) events
 * embedded inside notes. Renders avatar stack + Follow-all CTA.
 *
 * If the addressable event isn't in the cache, subscribes for it and shows a
 * skeleton until it arrives.
 */
@Composable
fun RenderFollowPackCard(
    address: NAddressEntity,
    cache: DesktopLocalCache,
    iAccount: DesktopIAccount?,
    relayManager: RelayConnectionManager,
    onOpenPack: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE")
    val packVersion by cache.followPackVersion.collectAsState()

    val aTag = remember(address) { Address.assemble(address.kind, address.author, address.dTag) }
    val pack: FollowListEvent? =
        remember(packVersion, aTag) {
            cache.snapshotFollowPacks().firstOrNull { FollowPackEditor.aTag(it) == aTag }
        }

    // If not in cache, kick off a one-shot subscription
    if (pack == null) {
        DisposableEffect(aTag) {
            val subId = "naddr-fetch-${aTag.hashCode()}"
            val filter =
                Filter(
                    kinds = listOf(address.kind),
                    authors = listOf(address.author),
                    tags = mapOf("d" to listOf(address.dTag)),
                    limit = 1,
                )
            relayManager.subscribe(
                subId,
                listOf(filter),
                listener =
                    object : com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener {
                        override fun onEvent(
                            event: com.vitorpamplona.quartz.nip01Core.core.Event,
                            isLive: Boolean,
                            relay: com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            cache.consume(event, relay)
                        }
                    },
            )
            onDispose {
                relayManager.unsubscribe(subId)
            }
        }
        PackCardSkeleton(modifier = modifier)
        return
    }

    if (pack.follows().isEmpty()) {
        // Empty pack — render minimal state, no Follow-all CTA (gap C5)
        Card(
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = pack.title()?.ifBlank { null } ?: "Untitled pack",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Empty pack",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    var showFollowAll by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Color(
                                    ((pack.title() ?: pack.id).hashCode() and 0x80FFFFFF.toInt()) or 0xFF608080.toInt(),
                                ),
                            ),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pack.title()?.ifBlank { null } ?: "Follow pack",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${pack.follows().size} people",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            FollowPackAvatarStack(
                memberHexes = pack.followIds(),
                cache = cache,
                avatarSize = 28.dp,
                maxVisible = 6,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenPack(aTag) }) { Text("Open") }
                if (iAccount != null) {
                    TextButton(onClick = { showFollowAll = true }) {
                        Text("Follow all")
                    }
                }
            }
        }
    }

    if (showFollowAll && iAccount != null) {
        FollowAllConfirmDialog(
            pack = pack,
            iAccount = iAccount,
            cache = cache,
            relayManager = relayManager,
            onDismiss = { showFollowAll = false },
        )
    }
}

@Composable
private fun PackCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )
        }
    }
}
