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
package com.vitorpamplona.amethyst.desktop.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.service.media.ServerHealthCheck
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun MediaServerSettings(
    initialServers: List<String> = emptyList(),
    onServersChanged: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val servers = remember { mutableStateListOf<String>().apply { addAll(initialServers) } }
    val serverStatuses = remember { mutableStateMapOf<String, ServerHealthCheck.ServerStatus>() }
    var newServerUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }

    // Check health on first load (parallel)
    LaunchedEffect(servers.toList()) {
        coroutineScope {
            for (server in servers) {
                if (server !in serverStatuses) {
                    launch {
                        val status = ServerHealthCheck.check(server)
                        serverStatuses[server] = status
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Media Servers (Blossom)",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Configure Blossom servers for media uploads. First server is the default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // Server list
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (server in servers.toList()) {
                ServerRow(
                    server = server,
                    status = serverStatuses[server] ?: ServerHealthCheck.ServerStatus.UNKNOWN,
                    isDefault = servers.indexOf(server) == 0,
                    onSetDefault = {
                        servers.remove(server)
                        servers.add(0, server)
                        onServersChanged(servers.toList())
                    },
                    onRemove = {
                        servers.remove(server)
                        serverStatuses.remove(server)
                        onServersChanged(servers.toList())
                    },
                    onRefresh = {
                        scope.launch {
                            serverStatuses[server] = ServerHealthCheck.ServerStatus.UNKNOWN
                            val status = ServerHealthCheck.check(server)
                            serverStatuses[server] = status
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Add server
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newServerUrl,
                onValueChange = { newServerUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://blossom.example.com") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    val url = newServerUrl.trim().removeSuffix("/")
                    if (url.isNotBlank() && url !in servers && isValidServerUrl(url)) {
                        servers.add(url)
                        newServerUrl = ""
                        onServersChanged(servers.toList())
                        scope.launch {
                            val status = ServerHealthCheck.check(url)
                            serverStatuses[url] = status
                        }
                    }
                },
                enabled = newServerUrl.isNotBlank() && isValidServerUrl(newServerUrl.trim()),
            ) {
                Icon(MaterialSymbols.Add, contentDescription = "Add")
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Refresh all
        Button(
            onClick = {
                scope.launch {
                    isChecking = true
                    coroutineScope {
                        for (server in servers) {
                            launch {
                                serverStatuses[server] = ServerHealthCheck.ServerStatus.UNKNOWN
                                val status = ServerHealthCheck.check(server)
                                serverStatuses[server] = status
                            }
                        }
                    }
                    isChecking = false
                }
            },
            enabled = !isChecking,
        ) {
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Icon(MaterialSymbols.Refresh, contentDescription = "Refresh")
            }
            Spacer(Modifier.width(4.dp))
            Text("Check All")
        }
    }
}

private fun isValidServerUrl(url: String): Boolean {
    val trimmed = url.trim().removeSuffix("/")
    return try {
        val uri = java.net.URI(trimmed)
        uri.scheme in listOf("https", "http") && uri.host != null && uri.host.contains(".")
    } catch (_: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerRow(
    server: String,
    status: ServerHealthCheck.ServerStatus,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status indicator with tooltip
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above, 4.dp),
                tooltip = {
                    PlainTooltip {
                        Text(
                            when (status) {
                                ServerHealthCheck.ServerStatus.ONLINE -> "Online"
                                ServerHealthCheck.ServerStatus.OFFLINE -> "Offline — server unreachable"
                                ServerHealthCheck.ServerStatus.UNKNOWN -> "Checking..."
                            },
                        )
                    }
                },
                state = rememberTooltipState(),
            ) {
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = CircleShape,
                    color =
                        when (status) {
                            ServerHealthCheck.ServerStatus.ONLINE -> Color(0xFF4CAF50)
                            ServerHealthCheck.ServerStatus.OFFLINE -> Color(0xFFF44336)
                            ServerHealthCheck.ServerStatus.UNKNOWN -> Color(0xFF9E9E9E)
                        },
                ) {}
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    server,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isDefault) {
                    Text(
                        "Default server",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        "Set as default",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onSetDefault() },
                    )
                }
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    MaterialSymbols.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(18.dp),
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    MaterialSymbols.Delete,
                    contentDescription = "Remove",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
