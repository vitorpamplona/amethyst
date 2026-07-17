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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.SettingsCategoryWithButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.RelayDragState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.draggableRelayItem
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relayDragHandle
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.rememberRelayDragState
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertPadding
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.ui.theme.SettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.warningColor

@Composable
fun AllMediaBody(
    blossomServersViewModel: BlossomServersViewModel,
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
            SettingsCategory(
                R.string.media_servers_blossom_section,
                R.string.media_servers_blossom_explainer,
                SettingsCategoryFirstModifier,
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
            item {
                Text(
                    text = stringRes(id = R.string.media_servers_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }

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
            Spacer(modifier = StdVertSpacer)
            MediaServerEditField(R.string.add_a_blossom_server) {
                blossomServersViewModel.addServer(it)
            }
        }

        item {
            SettingsCategoryWithButton(
                title = R.string.recommended_media_servers,
                description = R.string.built_in_servers_description,
                modifier = SettingsCategorySpacingModifier,
            ) {
                OutlinedButton(
                    onClick = {
                        blossomServersViewModel.addServerList(
                            DEFAULT_MEDIA_SERVERS.mapNotNull { s -> if (s.type == ServerType.Blossom) s.baseUrl else null },
                        )
                    },
                ) {
                    Text(text = stringRes(id = R.string.use_default_servers))
                }
            }
        }

        itemsIndexed(
            DEFAULT_MEDIA_SERVERS,
            key = { _, server -> "Proposed" + server.baseUrl },
        ) { _, server ->
            RecommendedServerRow(serverEntry = server) {
                if (server.type == ServerType.Blossom) {
                    blossomServersViewModel.addServer(server.baseUrl)
                }
            }
        }

        item {
            Spacer(DoubleHorzSpacer)
        }
    }
}

/**
 * A draggable, ranked server row. Position in the list is the upload/fallback
 * priority (row #1 is tried first), so each row carries a rank badge and a drag
 * handle wired into the shared [RelayDragState].
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
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.DragIndicator,
            contentDescription = stringRes(id = R.string.media_server_reorder),
            modifier = Modifier.size(24.dp).relayDragHandle(index, dragState),
            tint = MaterialTheme.colorScheme.grayText,
        )

        RankBadge(rank = index + 1)

        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        ) {
            Text(
                text = serverEntry.name.replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = StdVertSpacer)
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

/** A recommended default server, added on tap of the trailing "+". */
@Composable
fun RecommendedServerRow(
    serverEntry: ServerName,
    onAdd: (serverUrl: String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = serverEntry.name.replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = StdVertSpacer)
            Text(
                text = serverEntry.baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = { onAdd(serverEntry.baseUrl) }) {
            Icon(
                symbol = MaterialSymbols.Add,
                contentDescription = stringRes(id = R.string.add_media_server),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Rounded rank badge. The primary target (#1) is filled with the accent color. */
@Composable
private fun RankBadge(rank: Int) {
    val isPrimary = rank == 1
    Box(
        modifier =
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
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
