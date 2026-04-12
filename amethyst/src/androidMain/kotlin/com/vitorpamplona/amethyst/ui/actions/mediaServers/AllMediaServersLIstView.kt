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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.SettingsCategoryWithButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertPadding
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.ui.theme.SettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.grayText

@Composable
fun AllMediaBody(blossomServersViewModel: BlossomServersViewModel) {
    val blossomServersState by blossomServersViewModel.fileServers.collectAsStateWithLifecycle()

    LazyColumn(
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = FeedPadding,
    ) {
        item {
            SettingsCategory(
                R.string.media_servers_blossom_section,
                R.string.media_servers_blossom_explainer,
                SettingsCategoryFirstModifier,
            )
        }

        renderMediaServerList(
            mediaServersState = blossomServersState,
            keyType = "blossom",
            editLabel = R.string.add_a_blossom_server,
            emptyLabel = R.string.no_blossom_server_message,
            onAddServer = { server ->
                blossomServersViewModel.addServer(server)
            },
            onDeleteServer = {
                blossomServersViewModel.removeServer(serverUrl = it)
            },
        )

        DEFAULT_MEDIA_SERVERS.let {
            item {
                SettingsCategoryWithButton(
                    title = R.string.recommended_media_servers,
                    description = R.string.built_in_servers_description,
                    modifier = SettingsCategorySpacingModifier,
                ) {
                    OutlinedButton(
                        onClick = {
                            blossomServersViewModel.addServerList(
                                it.mapNotNull { s -> if (s.type == ServerType.Blossom) s.baseUrl else null },
                            )
                        },
                    ) {
                        Text(text = stringRes(id = R.string.use_default_servers))
                    }
                }
            }
            itemsIndexed(
                it,
                key = { index: Int, server: ServerName ->
                    "Proposed" + server.baseUrl
                },
            ) { index, server ->
                MediaServerEntry(
                    serverEntry = server,
                    isAmethystDefault = true,
                    onAddOrDelete = { serverUrl ->
                        if (server.type == ServerType.Blossom) {
                            blossomServersViewModel.addServer(serverUrl)
                        }
                    },
                )
            }
        }

        item {
            Spacer(DoubleHorzSpacer)
        }
    }
}

fun LazyListScope.renderMediaServerList(
    mediaServersState: List<ServerName>,
    keyType: String,
    editLabel: Int,
    emptyLabel: Int,
    onAddServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
) {
    if (mediaServersState.isEmpty()) {
        item {
            Text(
                text = stringRes(id = emptyLabel),
                modifier = DoubleVertPadding,
            )
        }
    } else {
        itemsIndexed(
            mediaServersState,
            key = { index: Int, server: ServerName ->
                keyType + server.baseUrl
            },
        ) { index, entry ->
            MediaServerEntry(
                serverEntry = entry,
                onAddOrDelete = {
                    onDeleteServer(it)
                },
            )
        }
    }

    item {
        Spacer(modifier = StdVertSpacer)
        MediaServerEditField(editLabel) {
            onAddServer(it)
        }
    }
}

@Composable
fun MediaServerEntry(
    modifier: Modifier = Modifier,
    serverEntry: ServerName,
    isAmethystDefault: Boolean = false,
    onAddOrDelete: (serverUrl: String) -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f),
        ) {
            serverEntry.let {
                Text(
                    text = it.name.replaceFirstChar(Char::titlecase),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = StdVertSpacer)
                Text(
                    text = it.baseUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(
                onClick = {
                    onAddOrDelete(serverEntry.baseUrl)
                },
            ) {
                Icon(
                    imageVector = if (isAmethystDefault) Icons.Rounded.Add else Icons.Rounded.Delete,
                    contentDescription =
                        if (isAmethystDefault) {
                            stringRes(id = R.string.add_media_server)
                        } else {
                            stringRes(id = R.string.delete_media_server)
                        },
                )
            }
        }
    }
}
