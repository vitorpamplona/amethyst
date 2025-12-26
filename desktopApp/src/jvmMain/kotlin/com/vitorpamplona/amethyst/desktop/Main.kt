/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.network.RelayStatus
import com.vitorpamplona.amethyst.desktop.ui.FeedScreen
import com.vitorpamplona.amethyst.desktop.ui.LoginScreen

enum class Screen {
    Feed,
    Search,
    Messages,
    Notifications,
    Profile,
    Settings
}

fun main() = application {
    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 800.dp,
        position = WindowPosition.Aligned(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Amethyst"
    ) {
        MenuBar {
            Menu("File") {
                Item(
                    "New Note",
                    shortcut = KeyShortcut(Key.N, ctrl = true),
                    onClick = { /* TODO: Open new note dialog */ }
                )
                Separator()
                Item(
                    "Settings",
                    shortcut = KeyShortcut(Key.Comma, ctrl = true),
                    onClick = { /* TODO: Open settings */ }
                )
                Separator()
                Item(
                    "Quit",
                    shortcut = KeyShortcut(Key.Q, ctrl = true),
                    onClick = ::exitApplication
                )
            }
            Menu("Edit") {
                Item("Copy", shortcut = KeyShortcut(Key.C, ctrl = true), onClick = { })
                Item("Paste", shortcut = KeyShortcut(Key.V, ctrl = true), onClick = { })
            }
            Menu("View") {
                Item("Feed", onClick = { })
                Item("Messages", onClick = { })
                Item("Notifications", onClick = { })
            }
            Menu("Help") {
                Item("About Amethyst", onClick = { })
                Item("Keyboard Shortcuts", onClick = { })
            }
        }

        App()
    }
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Feed) }
    val relayManager = remember { DesktopRelayConnectionManager() }
    val accountManager = remember { AccountManager() }
    val accountState by accountManager.accountState.collectAsState()

    DisposableEffect(Unit) {
        relayManager.addDefaultRelays()
        relayManager.connect()
        onDispose {
            relayManager.disconnect()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (accountState) {
                is AccountState.LoggedOut -> {
                    LoginScreen(
                        accountManager = accountManager,
                        onLoginSuccess = { currentScreen = Screen.Feed }
                    )
                }
                is AccountState.LoggedIn -> {
                    MainContent(
                        currentScreen = currentScreen,
                        onScreenChange = { currentScreen = it },
                        relayManager = relayManager,
                        accountManager = accountManager,
                        account = accountState as AccountState.LoggedIn,
                    )
                }
            }
        }
    }
}

@Composable
fun MainContent(
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit,
    relayManager: DesktopRelayConnectionManager,
    accountManager: AccountManager,
    account: AccountState.LoggedIn,
) {
    Row(Modifier.fillMaxSize()) {
        // Sidebar Navigation
        NavigationRail(
            modifier = Modifier.width(80.dp).fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Spacer(Modifier.height(16.dp))

            NavigationRailItem(
                icon = { Icon(Icons.Default.Home, contentDescription = "Feed") },
                label = { Text("Feed") },
                selected = currentScreen == Screen.Feed,
                onClick = { onScreenChange(Screen.Feed) }
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                label = { Text("Search") },
                selected = currentScreen == Screen.Search,
                onClick = { onScreenChange(Screen.Search) }
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Email, contentDescription = "Messages") },
                label = { Text("DMs") },
                selected = currentScreen == Screen.Messages,
                onClick = { onScreenChange(Screen.Messages) }
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Notifications, contentDescription = "Notifications") },
                label = { Text("Alerts") },
                selected = currentScreen == Screen.Notifications,
                onClick = { onScreenChange(Screen.Notifications) }
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                label = { Text("Profile") },
                selected = currentScreen == Screen.Profile,
                onClick = { onScreenChange(Screen.Profile) }
            )

            Spacer(Modifier.weight(1f))

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            NavigationRailItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings") },
                selected = currentScreen == Screen.Settings,
                onClick = { onScreenChange(Screen.Settings) }
            )

            Spacer(Modifier.height(16.dp))
        }

        VerticalDivider()

        // Main Content
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)
        ) {
            when (currentScreen) {
                Screen.Feed -> FeedScreen(relayManager)
                Screen.Search -> SearchPlaceholder()
                Screen.Messages -> MessagesPlaceholder()
                Screen.Notifications -> NotificationsPlaceholder()
                Screen.Profile -> ProfileScreen(account, accountManager)
                Screen.Settings -> RelaySettingsScreen(relayManager)
            }
        }
    }
}

@Composable
fun SearchPlaceholder() {
    Column {
        Text(
            "Search",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Search for users, notes, and hashtags.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MessagesPlaceholder() {
    Column {
        Text(
            "Messages",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your encrypted direct messages will appear here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun NotificationsPlaceholder() {
    Column {
        Text(
            "Notifications",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Mentions, replies, and reactions will appear here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ProfileScreen(account: AccountState.LoggedIn, accountManager: AccountManager) {
    Column {
        Text(
            "Profile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (account.isReadOnly) {
                    Text(
                        "Read-only mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Yellow
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    "Public Key",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    account.npub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Hex",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    account.pubKeyHex,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { accountManager.logout() },
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = Color.Red
            )
        ) {
            Text("Logout")
        }
    }
}

@Composable
fun RelaySettingsScreen(relayManager: DesktopRelayConnectionManager) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    var newRelayUrl by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Relay Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${connectedRelays.size} of ${relayStatuses.size} relays connected",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = { relayManager.connect() }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reconnect",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newRelayUrl,
                onValueChange = { newRelayUrl = it },
                label = { Text("Add relay") },
                placeholder = { Text("wss://relay.example.com") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    if (newRelayUrl.isNotBlank()) {
                        relayManager.addRelay(newRelayUrl)
                        newRelayUrl = ""
                    }
                },
                enabled = newRelayUrl.isNotBlank()
            ) {
                Text("Add")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(relayStatuses.values.toList()) { status ->
                RelayStatusCard(status) {
                    relayManager.removeRelay(status.url)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { relayManager.addDefaultRelays() }) {
                Text("Reset to Defaults")
            }
        }
    }
}

@Composable
fun RelayStatusCard(status: RelayStatus, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                val statusColor = when {
                    status.connected -> Color.Green
                    status.error != null -> Color.Red
                    else -> Color.Gray
                }

                if (status.connected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Connected",
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (status.error != null) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Error",
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }

                Column {
                    Text(
                        status.url.url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (status.connected && status.pingMs != null) {
                        Text(
                            "${status.pingMs}ms${if (status.compressed) " • compressed" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    status.error?.let { error ->
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove relay",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
