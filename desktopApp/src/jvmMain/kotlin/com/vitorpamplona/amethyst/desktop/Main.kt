/**
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vitorpamplona.amethyst.commons.ui.screens.MessagesPlaceholder
import com.vitorpamplona.amethyst.commons.ui.screens.SearchPlaceholder
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.ui.ComposeNoteDialog
import com.vitorpamplona.amethyst.desktop.ui.FeedScreen
import com.vitorpamplona.amethyst.desktop.ui.LoginScreen
import com.vitorpamplona.amethyst.desktop.ui.NotificationsScreen
import com.vitorpamplona.amethyst.desktop.ui.ThreadScreen
import com.vitorpamplona.amethyst.desktop.ui.UserProfileScreen
import com.vitorpamplona.amethyst.desktop.ui.profile.ProfileInfoCard
import com.vitorpamplona.amethyst.desktop.ui.relay.RelayStatusCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

/**
 * Desktop navigation state - extends AppScreen with dynamic destinations.
 */
sealed class DesktopScreen {
    object Feed : DesktopScreen()

    object Search : DesktopScreen()

    object Messages : DesktopScreen()

    object Notifications : DesktopScreen()

    object MyProfile : DesktopScreen()

    data class UserProfile(
        val pubKeyHex: String,
    ) : DesktopScreen()

    data class Thread(
        val noteId: String,
    ) : DesktopScreen()

    object Settings : DesktopScreen()
}

fun main() =
    application {
        val windowState =
            rememberWindowState(
                width = 1200.dp,
                height = 800.dp,
                position = WindowPosition.Aligned(Alignment.Center),
            )
        var showComposeDialog by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Amethyst",
        ) {
            MenuBar {
                Menu("File") {
                    Item(
                        "New Note",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.N, meta = true)
                            } else {
                                KeyShortcut(Key.N, ctrl = true)
                            },
                        onClick = { showComposeDialog = true },
                    )
                    Separator()
                    Item(
                        "Settings",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.Comma, meta = true)
                            } else {
                                KeyShortcut(Key.Comma, ctrl = true)
                            },
                        onClick = { /* TODO: Open settings */ },
                    )
                    Separator()
                    Item(
                        "Quit",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.Q, meta = true)
                            } else {
                                KeyShortcut(Key.Q, ctrl = true)
                            },
                        onClick = ::exitApplication,
                    )
                }
                Menu("Edit") {
                    Item(
                        "Copy",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.C, meta = true)
                            } else {
                                KeyShortcut(Key.C, ctrl = true)
                            },
                        onClick = { },
                    )
                    Item(
                        "Paste",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.V, meta = true)
                            } else {
                                KeyShortcut(Key.V, ctrl = true)
                            },
                        onClick = { },
                    )
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

            App(
                showComposeDialog = showComposeDialog,
                onShowComposeDialog = { showComposeDialog = true },
                onDismissComposeDialog = { showComposeDialog = false },
            )
        }
    }

@Composable
fun App(
    showComposeDialog: Boolean,
    onShowComposeDialog: () -> Unit,
    onDismissComposeDialog: () -> Unit,
) {
    var currentScreen by remember { mutableStateOf<DesktopScreen>(DesktopScreen.Feed) }
    val relayManager = remember { DesktopRelayConnectionManager() }
    val accountManager = remember { AccountManager.create() }
    val accountState by accountManager.accountState.collectAsState()
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // Try to load saved account on startup
    DisposableEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            // Load account on IO dispatcher to avoid blocking UI with password prompt (readLine)
            accountManager.loadSavedAccount()
        }

        relayManager.addDefaultRelays()
        relayManager.connect()
        onDispose {
            relayManager.disconnect()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (accountState) {
                is AccountState.LoggedOut -> {
                    LoginScreen(
                        accountManager = accountManager,
                        onLoginSuccess = { currentScreen = DesktopScreen.Feed },
                    )
                }
                is AccountState.LoggedIn -> {
                    val account = accountState as AccountState.LoggedIn

                    MainContent(
                        currentScreen = currentScreen,
                        onScreenChange = { currentScreen = it },
                        relayManager = relayManager,
                        accountManager = accountManager,
                        account = account,
                        onShowComposeDialog = onShowComposeDialog,
                    )

                    // Compose dialog
                    if (showComposeDialog) {
                        ComposeNoteDialog(
                            onDismiss = onDismissComposeDialog,
                            relayManager = relayManager,
                            account = account,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainContent(
    currentScreen: DesktopScreen,
    onScreenChange: (DesktopScreen) -> Unit,
    relayManager: DesktopRelayConnectionManager,
    accountManager: AccountManager,
    account: AccountState.LoggedIn,
    onShowComposeDialog: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        // Sidebar Navigation
        NavigationRail(
            modifier = Modifier.width(80.dp).fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Spacer(Modifier.height(16.dp))

            NavigationRailItem(
                icon = { Icon(Icons.Default.Home, contentDescription = "Feed") },
                label = { Text("Feed") },
                selected = currentScreen == DesktopScreen.Feed,
                onClick = { onScreenChange(DesktopScreen.Feed) },
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                label = { Text("Search") },
                selected = currentScreen == DesktopScreen.Search,
                onClick = { onScreenChange(DesktopScreen.Search) },
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Email, contentDescription = "Messages") },
                label = { Text("DMs") },
                selected = currentScreen == DesktopScreen.Messages,
                onClick = { onScreenChange(DesktopScreen.Messages) },
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Notifications, contentDescription = "Notifications") },
                label = { Text("Alerts") },
                selected = currentScreen == DesktopScreen.Notifications,
                onClick = { onScreenChange(DesktopScreen.Notifications) },
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                label = { Text("Profile") },
                selected = currentScreen == DesktopScreen.MyProfile || currentScreen is DesktopScreen.UserProfile,
                onClick = { onScreenChange(DesktopScreen.MyProfile) },
            )

            Spacer(Modifier.weight(1f))

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            NavigationRailItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings") },
                selected = currentScreen == DesktopScreen.Settings,
                onClick = { onScreenChange(DesktopScreen.Settings) },
            )

            Spacer(Modifier.height(16.dp))
        }

        VerticalDivider()

        // Main Content
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
        ) {
            when (currentScreen) {
                DesktopScreen.Feed ->
                    FeedScreen(
                        relayManager = relayManager,
                        account = account,
                        onCompose = onShowComposeDialog,
                        onNavigateToProfile = { pubKeyHex ->
                            onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                        },
                        onNavigateToThread = { noteId ->
                            onScreenChange(DesktopScreen.Thread(noteId))
                        },
                    )
                DesktopScreen.Search -> SearchPlaceholder()
                DesktopScreen.Messages -> MessagesPlaceholder()
                DesktopScreen.Notifications -> NotificationsScreen(relayManager, account)
                DesktopScreen.MyProfile ->
                    UserProfileScreen(
                        pubKeyHex = account.pubKeyHex,
                        relayManager = relayManager,
                        account = account,
                        onBack = { onScreenChange(DesktopScreen.Feed) },
                        onCompose = onShowComposeDialog,
                        onNavigateToProfile = { pubKeyHex ->
                            onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                        },
                    )
                is DesktopScreen.UserProfile ->
                    UserProfileScreen(
                        pubKeyHex = currentScreen.pubKeyHex,
                        relayManager = relayManager,
                        account = account,
                        onBack = { onScreenChange(DesktopScreen.Feed) },
                        onCompose = onShowComposeDialog,
                        onNavigateToProfile = { pubKeyHex ->
                            onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                        },
                    )
                is DesktopScreen.Thread ->
                    ThreadScreen(
                        noteId = currentScreen.noteId,
                        relayManager = relayManager,
                        account = account,
                        onBack = { onScreenChange(DesktopScreen.Feed) },
                        onNavigateToProfile = { pubKeyHex ->
                            onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                        },
                        onNavigateToThread = { noteId ->
                            onScreenChange(DesktopScreen.Thread(noteId))
                        },
                    )
                DesktopScreen.Settings -> RelaySettingsScreen(relayManager, account)
            }
        }
    }
}

@Composable
fun ProfileScreen(
    account: AccountState.LoggedIn,
    accountManager: AccountManager,
) {
    val scope = rememberCoroutineScope()

    Column {
        Text(
            "Profile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        ProfileInfoCard(
            npub = account.npub,
            pubKeyHex = account.pubKeyHex,
            isReadOnly = account.isReadOnly,
        )

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { scope.launch { accountManager.logout() } },
            colors =
                androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Red,
                ),
        ) {
            Text("Logout")
        }
    }
}

@Composable
fun RelaySettingsScreen(
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    var newRelayUrl by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(24.dp))

        // Developer Settings Section (only in debug mode)
        if (DebugConfig.isDebugMode) {
            com.vitorpamplona.amethyst.desktop.ui
                .DevSettingsSection(account = account)
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))
        }

        Text(
            "Relay Settings",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${connectedRelays.size} of ${relayStatuses.size} relays connected",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = { relayManager.connect() }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reconnect",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newRelayUrl,
                onValueChange = { newRelayUrl = it },
                label = { Text("Add relay") },
                placeholder = { Text("wss://relay.example.com") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(
                onClick = {
                    if (newRelayUrl.isNotBlank()) {
                        relayManager.addRelay(newRelayUrl)
                        newRelayUrl = ""
                    }
                },
                enabled = newRelayUrl.isNotBlank(),
            ) {
                Text("Add")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(relayStatuses.values.toList(), key = { it.url.url }) { status ->
                RelayStatusCard(
                    status = status,
                    onRemove = { relayManager.removeRelay(status.url) },
                )
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
