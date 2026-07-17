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
package com.vitorpamplona.amethyst.ui.actions.mediaServers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.RelayDragState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.draggableRelayItem
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relayDragHandle
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.rememberRelayDragState
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertPadding
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.warningColor
import com.vitorpamplona.quartz.utils.Rfc3986

/** Vibrant palette for server monograms; picked deterministically from the host name. */
private val MonogramColors =
    listOf(
        Color(0xFF8B5CF6),
        Color(0xFF0EA5A0),
        Color(0xFFE07B00),
        Color(0xFF4169E1),
        Color(0xFFD16D8F),
        Color(0xFF4F9D4F),
        Color(0xFFB66605),
        Color(0xFF7C6FE0),
    )

@Composable
fun AllMediaBody(
    blossomServersViewModel: BlossomServersViewModel,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val blossomServersState by blossomServersViewModel.fileServers.collectAsStateWithLifecycle()
    val healthState by blossomServersViewModel.health.collectAsStateWithLifecycle()

    val dragState =
        rememberRelayDragState(
            onMove = { from, to -> blossomServersViewModel.moveServer(from, to) },
            itemCount = { blossomServersState.size },
        )

    LazyColumn(
        modifier = modifier,
        contentPadding = FeedPadding,
        userScrollEnabled = !dragState.isDragging,
    ) {
        item {
            Text(
                text = stringRes(id = R.string.set_preferred_media_servers),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }

        item {
            SectionLabel(
                title = stringRes(id = R.string.media_servers_priority_section),
                caption = stringRes(id = R.string.media_servers_reorder_hint),
            )
        }

        if (blossomServersState.isEmpty()) {
            item {
                Text(
                    text = stringRes(id = R.string.no_blossom_server_message),
                    modifier = DoubleVertPadding,
                )
            }
        } else {
            itemsIndexed(
                blossomServersState,
                key = { _, server -> "blossom" + server.baseUrl },
            ) { index, entry ->
                MediaServerRow(
                    index = index,
                    serverEntry = entry,
                    health = healthState[entry.baseUrl] ?: ServerHealth.Unknown,
                    dragState = dragState,
                    onDelete = { blossomServersViewModel.removeServer(serverUrl = it) },
                )
            }
        }

        item {
            AddServerSection(
                // Server entries carry their host in `name`; match recommended chips by host so
                // normalization differences in the URL don't hide the "added" state.
                addedHosts = blossomServersState.mapTo(HashSet()) { it.name },
                onAddServer = { blossomServersViewModel.addServer(it) },
                onAddAll = {
                    blossomServersViewModel.addServerList(
                        DEFAULT_MEDIA_SERVERS.mapNotNull { s -> if (s.type == ServerType.Blossom) s.baseUrl else null },
                    )
                },
            )
        }

        item {
            SectionLabel(title = stringRes(id = R.string.media_servers_cache_section))
            MediaCacheSection(accountViewModel)
        }

        item {
            Spacer(DoubleHorzSpacer)
        }
    }
}

/** Compact section header: an accent label with an optional gray caption below. */
@Composable
private fun SectionLabel(
    title: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

/**
 * A draggable, ranked server row. Position in the list is the upload/fallback priority
 * (row #1 is tried first), so each row carries a rank badge and a drag handle wired into
 * the shared [RelayDragState], plus a monogram and a live reachability dot.
 */
@Composable
fun MediaServerRow(
    index: Int,
    serverEntry: ServerName,
    health: ServerHealth,
    dragState: RelayDragState,
    onDelete: (serverUrl: String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .draggableRelayItem(index, dragState)
                .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.DragIndicator,
            contentDescription = stringRes(id = R.string.media_server_reorder),
            modifier = Modifier.size(22.dp).relayDragHandle(index, dragState),
            tint = MaterialTheme.colorScheme.grayText,
        )

        RankBadge(rank = index + 1)

        Spacer(Modifier.size(10.dp))

        ServerMonogram(name = serverEntry.name, size = 32.dp)

        Column(
            modifier = Modifier.weight(1f).padding(start = 11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = serverEntry.name.replaceFirstChar(Char::titlecase),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (index == 0) {
                    Spacer(Modifier.size(6.dp))
                    PrimaryBadge()
                }
            }
            Text(
                text = serverEntry.baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HealthIndicator(health)

        IconButton(onClick = { onDelete(serverEntry.baseUrl) }) {
            Icon(
                symbol = MaterialSymbols.Delete,
                contentDescription = stringRes(id = R.string.delete_media_server),
                tint = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

/**
 * Inline "add a server" area: a URL field followed by the recommended servers as a
 * horizontal strip of add-chips (already-added ones read as done).
 */
@Composable
private fun AddServerSection(
    addedHosts: Set<String>,
    onAddServer: (String) -> Unit,
    onAddAll: () -> Unit,
) {
    SectionLabel(title = stringRes(id = R.string.media_servers_add_section))

    MediaServerEditField(R.string.add_a_blossom_server) { onAddServer(it) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(id = R.string.media_servers_recommended_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.grayText,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAddAll) {
            Text(text = stringRes(id = R.string.use_default_servers))
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(
            DEFAULT_MEDIA_SERVERS,
            key = { it.baseUrl },
        ) { server ->
            val host = runCatching { Rfc3986.host(server.baseUrl) }.getOrNull()
            RecommendedChip(
                serverEntry = server,
                added = host != null && host in addedHosts,
                onAdd = { onAddServer(server.baseUrl) },
            )
        }
    }
}

/** A recommended server as a tappable pill. Once added it reads as done and stops responding. */
@Composable
private fun RecommendedChip(
    serverEntry: ServerName,
    added: Boolean,
    onAdd: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .then(
                    if (added) {
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    },
                ).clickable(enabled = !added, onClick = onAdd)
                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ServerMonogram(name = serverEntry.name, size = 24.dp)
        Text(
            text = serverEntry.name.replaceFirstChar(Char::titlecase),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Icon(
            symbol = if (added) MaterialSymbols.CheckCircle else MaterialSymbols.Add,
            contentDescription =
                if (added) {
                    stringRes(id = R.string.media_server_added)
                } else {
                    stringRes(id = R.string.add_media_server)
                },
            modifier = Modifier.size(18.dp),
            tint = if (added) MaterialTheme.colorScheme.grayText else MaterialTheme.colorScheme.primary,
        )
    }
}

/** A colored letter tile identifying a server, derived from its host name. */
@Composable
private fun ServerMonogram(
    name: String,
    size: Dp,
) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val color = MonogramColors[((name.hashCode() % MonogramColors.size) + MonogramColors.size) % MonogramColors.size]
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3))
                .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = if (size >= 30.dp) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** Rounded rank badge. The primary target (#1) is filled with the accent color. */
@Composable
private fun RankBadge(rank: Int) {
    val isPrimary = rank == 1
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (isPrimary) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color =
                if (isPrimary) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/** Small "Primary" pill shown on the #1 server. */
@Composable
private fun PrimaryBadge() {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = stringRes(id = R.string.media_server_primary_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Colored reachability dot + label, or a spinner while a probe is in flight. */
@Composable
private fun HealthIndicator(health: ServerHealth) {
    if (health == ServerHealth.Unknown) return

    if (health == ServerHealth.Checking) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.grayText,
        )
        return
    }

    val color: Color
    val label: Int
    when (health) {
        ServerHealth.Online -> {
            color = MaterialTheme.colorScheme.allGoodColor
            label = R.string.media_server_status_online
        }
        ServerHealth.Slow -> {
            color = MaterialTheme.colorScheme.warningColor
            label = R.string.media_server_status_slow
        }
        else -> {
            color = MaterialTheme.colorScheme.error
            label = R.string.media_server_status_offline
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Text(
            text = stringRes(id = label),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}
