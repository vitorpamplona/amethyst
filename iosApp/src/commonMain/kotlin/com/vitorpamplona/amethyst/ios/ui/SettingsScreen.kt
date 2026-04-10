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
package com.vitorpamplona.amethyst.ios.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ios.network.DefaultRelays
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.network.RelayConnectionState
import com.vitorpamplona.amethyst.ios.network.RelayStatus
import com.vitorpamplona.amethyst.ios.ui.qr.QrCodeDisplay
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

private const val GITHUB_URL = "https://github.com/vitorpamplona/amethyst"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    npub: String,
    pubKeyHex: String,
    relayManager: IosRelayConnectionManager,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onAccountSwitcher: (() -> Unit)? = null,
    onEditProfile: (() -> Unit)? = null,
    onBookmarks: (() -> Unit)? = null,
    onMuteList: (() -> Unit)? = null,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            // ── Account Section ──
            SectionHeader(icon = Icons.Default.Person, title = "Account")

            // Bookmarks button
            if (onBookmarks != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp),
                            ).clickable(onClick = onBookmarks)
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Bookmarks",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "View your bookmarked notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Mute List button
            if (onMuteList != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp),
                            ).clickable(onClick = onMuteList)
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Muted Users",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Manage your mute list",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Edit Profile button
            if (onEditProfile != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp),
                            ).clickable(onClick = onEditProfile)
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Edit Profile",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Update your display name, bio, and avatar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Account Switcher button
            if (onAccountSwitcher != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp),
                            ).clickable(onClick = onAccountSwitcher)
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Switch Account",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Manage multiple accounts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            AccountKeyRow(label = "Public Key (npub)", value = npub)

            Spacer(Modifier.height(8.dp))

            AccountKeyRow(label = "Public Key (hex)", value = pubKeyHex)

            Spacer(Modifier.height(12.dp))

            // Share QR Code
            ShareQrSection(npub = npub)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // ── Relay Management ──
            RelaySection(relayStatuses = relayStatuses, relayManager = relayManager)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // ── Relay Discovery ──
            RelayDiscoverySection(relayStatuses = relayStatuses, relayManager = relayManager)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // ── About Section ──
            SectionHeader(icon = Icons.Default.Info, title = "About")

            AboutRow(label = "App", value = "Amethyst for iOS")
            AboutRow(label = "Platform", value = "Compose Multiplatform")

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = {
                    NSURL.URLWithString(GITHUB_URL)?.let { url ->
                        UIApplication.sharedApplication.openURL(url)
                    }
                },
            ) {
                Text("View on GitHub →", color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(24.dp))

            // ── Logout ──
            LogoutSection(onLogout = onLogout)

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Section Header ──

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

// ── Account Keys ──

@Composable
private fun AccountKeyRow(
    label: String,
    value: String,
) {
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                ).padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = {
                    UIPasteboard.generalPasteboard.string = value
                    copied = true
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint =
                        if (copied) {
                            Color(0xFF4CAF50)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            value.take(20) + "..." + value.takeLast(8),
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    if (copied) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }
}

// ── Relay Section (full-featured) ──

@OptIn(DelicateCoroutinesApi::class)
@Composable
private fun RelaySection(
    relayStatuses: Map<NormalizedRelayUrl, RelayStatus>,
    relayManager: IosRelayConnectionManager,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    SectionHeader(icon = Icons.Default.Wifi, title = "Relays")

    // Connection summary
    val connectedCount = relayStatuses.values.count { it.connectionState == RelayConnectionState.CONNECTED }
    val connectingCount = relayStatuses.values.count { it.connectionState == RelayConnectionState.CONNECTING }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "$connectedCount of ${relayStatuses.size} connected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (connectingCount > 0) {
                Text(
                    "$connectingCount connecting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFA726),
                )
            }
        }
        IconButton(onClick = { relayManager.reconnectAll() }) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Reconnect all",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    if (relayStatuses.isEmpty()) {
        Text(
            "No relays configured",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        relayStatuses.forEach { (url, status) ->
            RelayRow(
                url = url,
                status = status,
                onRemove = { relayManager.removeRelay(url) },
                onToggleRead = { relayManager.setRelayRead(url, it) },
                onToggleWrite = { relayManager.setRelayWrite(url, it) },
                onReconnect = { relayManager.reconnectAll() },
            )
            Spacer(Modifier.height(4.dp))
        }
    }

    Spacer(Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { showAddDialog = true }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add Relay")
        }

        TextButton(
            onClick = {
                GlobalScope.launch { relayManager.reconnectAll() }
            },
        ) {
            Text("Reconnect All")
        }

        TextButton(onClick = {
            relayManager.addDefaultRelays()
            relayManager.connect()
        }) {
            Text("Reset Defaults")
        }
    }

    if (showAddDialog) {
        AddRelayDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url ->
                relayManager.addRelay(url)
                relayManager.connect()
                showAddDialog = false
            },
        )
    }
}

// ── Individual Relay Row with permissions + NIP-11 ──

@Composable
private fun RelayRow(
    url: NormalizedRelayUrl,
    status: RelayStatus,
    onRemove: () -> Unit,
    onToggleRead: (Boolean) -> Unit,
    onToggleWrite: (Boolean) -> Unit,
    onReconnect: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                ).clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Main row: URL + status + delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    url.url,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Status indicator
                    val (statusColor, statusText) =
                        when (status.connectionState) {
                            RelayConnectionState.CONNECTED -> {
                                Color(0xFF4CAF50) to ("Connected" + (status.pingMs?.let { " (${it}ms)" } ?: ""))
                            }

                            RelayConnectionState.CONNECTING -> {
                                Color(0xFFFFA726) to "Connecting…"
                            }

                            RelayConnectionState.DISCONNECTED -> {
                                Color(0xFFFF5252) to (status.error ?: "Disconnected")
                            }
                        }
                    Text("●", color = statusColor, style = MaterialTheme.typography.labelSmall)
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Read/Write badges
                    if (status.read && status.write) {
                        PermissionBadge("R/W")
                    } else if (status.read) {
                        PermissionBadge("R")
                    } else if (status.write) {
                        PermissionBadge("W")
                    }
                }
            }

            Row {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove relay",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Expandable detail section
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                // Read/Write toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Read", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = status.read,
                        onCheckedChange = onToggleRead,
                        modifier = Modifier.height(24.dp),
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Write", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = status.write,
                        onCheckedChange = onToggleWrite,
                        modifier = Modifier.height(24.dp),
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                            ),
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Reconnect button
                TextButton(onClick = onReconnect) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reconnect", style = MaterialTheme.typography.labelSmall)
                }

                // NIP-11 info (if available)
                status.nip11?.let { info ->
                    Spacer(Modifier.height(4.dp))
                    Nip11InfoCard(info)
                }

                // Compressed indicator
                if (status.compressed) {
                    Text(
                        "Compression enabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionBadge(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                ).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

// ── NIP-11 Relay Information Card ──

@Composable
private fun Nip11InfoCard(info: Nip11RelayInformation) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                ).padding(8.dp),
    ) {
        Text(
            "Relay Info (NIP-11)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))

        info.name?.let {
            Nip11Row("Name", it)
        }
        info.description?.let {
            Nip11Row("Description", it)
        }
        info.software?.let { sw ->
            val version = info.version?.let { " v$it" } ?: ""
            Nip11Row("Software", "$sw$version")
        }
        info.supported_nips?.takeIf { it.isNotEmpty() }?.let {
            Nip11Row("NIPs", it.joinToString(", "))
        }
        info.contact?.let {
            Nip11Row("Contact", it)
        }
        info.limitation?.let { lim ->
            lim.payment_required?.let { if (it) Nip11Row("Payment", "Required") }
            lim.auth_required?.let { if (it) Nip11Row("Auth", "Required") }
        }
    }
}

@Composable
private fun Nip11Row(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Relay Discovery Section ──

@Composable
private fun RelayDiscoverySection(
    relayStatuses: Map<NormalizedRelayUrl, RelayStatus>,
    relayManager: IosRelayConnectionManager,
) {
    var showRecommended by remember { mutableStateOf(false) }
    val configuredUrls = relayStatuses.keys.map { it.url }.toSet()

    SectionHeader(icon = Icons.Default.Add, title = "Discover Relays")

    Text(
        "Add popular relays or import from your NIP-65 relay list.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))

    // Recommended relays toggle
    TextButton(onClick = { showRecommended = !showRecommended }) {
        Icon(
            if (showRecommended) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(if (showRecommended) "Hide Recommended" else "Show Recommended Relays")
    }

    AnimatedVisibility(
        visible = showRecommended,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Column {
            DefaultRelays.RECOMMENDED.forEach { relayUrl ->
                val normalized = RelayUrlNormalizer.normalizeOrNull(relayUrl)
                val alreadyAdded = normalized != null && configuredUrls.contains(normalized.url)

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                            ).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        relayUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (alreadyAdded) {
                        Text(
                            "Added",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                        )
                    } else {
                        TextButton(
                            onClick = {
                                relayManager.addRelay(relayUrl)
                                relayManager.connect()
                            },
                        ) {
                            Text("Add", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ── Add Relay Dialog ──

@Composable
private fun AddRelayDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var relayUrl by remember { mutableStateOf("wss://") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Relay") },
        text = {
            Column {
                OutlinedTextField(
                    value = relayUrl,
                    onValueChange = {
                        relayUrl = it
                        error = null
                    },
                    label = { Text("Relay URL") },
                    placeholder = { Text("wss://relay.example.com") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = relayUrl.trim()
                    when {
                        !trimmed.startsWith("wss://") && !trimmed.startsWith("ws://") -> {
                            error = "URL must start with wss:// or ws://"
                        }

                        trimmed.length <= 6 -> {
                            error = "URL is too short"
                        }

                        RelayUrlNormalizer.normalizeOrNull(trimmed) == null -> {
                            error = "Invalid relay URL"
                        }

                        else -> {
                            onAdd(trimmed)
                        }
                    }
                },
                enabled = relayUrl.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ── About Row ──

@Composable
private fun AboutRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Share QR Code Section ──

@Composable
private fun ShareQrSection(npub: String) {
    var showQr by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showQr = !showQr },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (showQr) "Hide QR Code" else "Share QR Code")
    }

    AnimatedVisibility(
        visible = showQr,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QrCodeDisplay(
                data = npub,
                size = 200.dp,
                label = npub.take(20) + "..." + npub.takeLast(8),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Others can scan this to find you on Nostr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Logout Section ──

@Composable
private fun LogoutSection(onLogout: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showConfirmDialog = true },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Log Out")
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Log Out?") },
            text = { Text("Your keys will be removed from this device. Make sure you have them backed up.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onLogout()
                    },
                ) {
                    Text("Log Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
