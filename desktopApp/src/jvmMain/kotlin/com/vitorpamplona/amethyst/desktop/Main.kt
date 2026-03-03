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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.chess.ChessScreen
import com.vitorpamplona.amethyst.desktop.model.DesktopDmRelayState
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.ui.BookmarksScreen
import com.vitorpamplona.amethyst.desktop.ui.ComposeNoteDialog
import com.vitorpamplona.amethyst.desktop.ui.FeedScreen
import com.vitorpamplona.amethyst.desktop.ui.LoginScreen
import com.vitorpamplona.amethyst.desktop.ui.NotificationsScreen
import com.vitorpamplona.amethyst.desktop.ui.ReadsScreen
import com.vitorpamplona.amethyst.desktop.ui.SearchScreen
import com.vitorpamplona.amethyst.desktop.ui.ThreadScreen
import com.vitorpamplona.amethyst.desktop.ui.UserProfileScreen
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.amethyst.desktop.ui.chats.DesktopMessagesScreen
import com.vitorpamplona.amethyst.desktop.ui.chats.DmSendTracker
import com.vitorpamplona.amethyst.desktop.ui.profile.ProfileInfoCard
import com.vitorpamplona.amethyst.desktop.ui.relay.RelayStatusCard
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

/**
 * Desktop navigation state - extends AppScreen with dynamic destinations.
 */
sealed class DesktopScreen {
    object Feed : DesktopScreen()

    object Reads : DesktopScreen()

    object Search : DesktopScreen()

    object Bookmarks : DesktopScreen()

    object Messages : DesktopScreen()

    object Notifications : DesktopScreen()

    object Chess : DesktopScreen()

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
        var replyToNote by remember { mutableStateOf<com.vitorpamplona.quartz.nip01Core.core.Event?>(null) }
        var currentScreen by remember { mutableStateOf<DesktopScreen>(DesktopScreen.Feed) }

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
                        onClick = { currentScreen = DesktopScreen.Settings },
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
                currentScreen = currentScreen,
                onScreenChange = { currentScreen = it },
                showComposeDialog = showComposeDialog,
                onShowComposeDialog = { showComposeDialog = true },
                onShowReplyDialog = { event ->
                    replyToNote = event
                    showComposeDialog = true
                },
                onDismissComposeDialog = {
                    showComposeDialog = false
                    replyToNote = null
                },
                replyToNote = replyToNote,
            )
        }
    }

@Composable
fun App(
    currentScreen: DesktopScreen,
    onScreenChange: (DesktopScreen) -> Unit,
    showComposeDialog: Boolean,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onDismissComposeDialog: () -> Unit,
    replyToNote: com.vitorpamplona.quartz.nip01Core.core.Event?,
) {
    val relayManager = remember { DesktopRelayConnectionManager() }
    val localCache = remember { DesktopLocalCache() }
    val accountManager = remember { AccountManager.create() }
    val accountState by accountManager.accountState.collectAsState()
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // Subscriptions coordinator for metadata/reactions loading
    val subscriptionsCoordinator =
        remember(relayManager, localCache) {
            DesktopRelaySubscriptionsCoordinator(
                client = relayManager.client,
                scope = scope,
                indexRelays = relayManager.availableRelays.value,
                localCache = localCache,
            )
        }

    // Try to load saved account on startup
    DisposableEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            // Load account on IO dispatcher to avoid blocking UI with password prompt (readLine)
            accountManager.loadSavedAccount()
        }

        relayManager.addDefaultRelays()
        relayManager.connect()

        // Start subscriptions coordinator
        subscriptionsCoordinator.start()

        onDispose {
            subscriptionsCoordinator.clear()
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
                        onLoginSuccess = { onScreenChange(DesktopScreen.Feed) },
                    )
                }

                is AccountState.LoggedIn -> {
                    val account = accountState as AccountState.LoggedIn
                    val nwcConnection by accountManager.nwcConnection.collectAsState()

                    // Load NWC connection on first composition
                    LaunchedEffect(Unit) {
                        accountManager.loadNwcConnection()
                    }

                    MainContent(
                        currentScreen = currentScreen,
                        onScreenChange = onScreenChange,
                        relayManager = relayManager,
                        localCache = localCache,
                        accountManager = accountManager,
                        account = account,
                        nwcConnection = nwcConnection,
                        subscriptionsCoordinator = subscriptionsCoordinator,
                        onShowComposeDialog = onShowComposeDialog,
                        onShowReplyDialog = onShowReplyDialog,
                    )

                    // Compose dialog
                    if (showComposeDialog) {
                        ComposeNoteDialog(
                            onDismiss = onDismissComposeDialog,
                            relayManager = relayManager,
                            account = account,
                            replyTo = replyToNote,
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
    localCache: DesktopLocalCache,
    accountManager: AccountManager,
    account: AccountState.LoggedIn,
    nwcConnection: Nip47WalletConnect.Nip47URINorm?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // DM infrastructure — hoisted here so it survives screen navigation
    val dmSendTracker =
        remember(relayManager) {
            DmSendTracker(relayManager.client)
        }
    val iAccount =
        remember(account, localCache, relayManager, dmSendTracker) {
            DesktopIAccount(account, localCache, relayManager, dmSendTracker, scope)
        }

    // Subscribe to incoming DMs and process into chatroomList
    LaunchedEffect(account) {
        relayManager.connectedRelays.first { it.isNotEmpty() }

        val dmRelayState =
            DesktopDmRelayState(
                dmRelayList = kotlinx.coroutines.flow.MutableStateFlow(emptySet()),
                connectedRelays = relayManager.connectedRelays,
                scope = scope,
            )
        subscriptionsCoordinator.subscribeToDms(
            userPubKeyHex = account.pubKeyHex,
            dmRelayState = dmRelayState,
            onDmEvent = { event, relay ->
                // Store raw event in cache
                val note = localCache.getOrCreateNote(event.id)
                val author = localCache.getOrCreateUser(event.pubKey)
                if (note.event == null) {
                    note.loadEvent(event, author, emptyList())
                    note.addRelay(relay)
                }

                // Process into chatroomList based on event type
                when (event) {
                    is com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent -> {
                        iAccount.chatroomList.addMessage(
                            event.chatroomKey(iAccount.pubKey),
                            note,
                        )
                    }

                    is com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent -> {
                        // NIP-17: unwrap gift wrap → seal → inner event
                        scope.launch {
                            val seal =
                                event.unwrapOrNull(iAccount.signer)
                                    as? com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
                                    ?: return@launch
                            val innerEvent = seal.unsealOrNull(iAccount.signer) ?: return@launch
                            when (innerEvent) {
                                is com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent -> {
                                    val innerNote = localCache.getOrCreateNote(innerEvent.id)
                                    val innerAuthor = localCache.getOrCreateUser(innerEvent.pubKey)
                                    if (innerNote.event == null) {
                                        innerNote.loadEvent(innerEvent, innerAuthor, emptyList())
                                    }
                                    iAccount.chatroomList.addMessage(
                                        innerEvent.chatroomKey(iAccount.pubKey),
                                        innerNote,
                                    )
                                }

                                is com.vitorpamplona.quartz.nip25Reactions.ReactionEvent -> {
                                    val reactionNote = localCache.getOrCreateNote(innerEvent.id)
                                    val reactionAuthor = localCache.getOrCreateUser(innerEvent.pubKey)
                                    if (reactionNote.event == null) {
                                        reactionNote.loadEvent(innerEvent, reactionAuthor, emptyList())
                                    }
                                    // Attach reaction to the target message note
                                    innerEvent.originalPost().forEach { targetId ->
                                        val targetNote = localCache.getNoteIfExists(targetId)
                                        targetNote?.addReaction(reactionNote)
                                    }
                                }

                                else -> {
                                    println("Unhandled NIP-17 inner event: ${innerEvent.kind}")
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    // Clean up DM subscriptions when this composable leaves (logout)
    DisposableEffect(Unit) {
        onDispose { subscriptionsCoordinator.unsubscribeFromDms() }
    }

    val onZapFeedback: (ZapFeedback) -> Unit = { feedback ->
        scope.launch {
            val message =
                when (feedback) {
                    is ZapFeedback.Success -> "Zapped ${feedback.amountSats} sats"
                    is ZapFeedback.ExternalWallet -> "Invoice sent to wallet (${feedback.amountSats} sats)"
                    is ZapFeedback.Error -> "Zap failed: ${feedback.message}"
                    is ZapFeedback.Timeout -> "Zap timed out"
                    is ZapFeedback.NoLightningAddress -> "User has no lightning address"
                }
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                    icon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Reads") },
                    label = { Text("Reads") },
                    selected = currentScreen == DesktopScreen.Reads,
                    onClick = { onScreenChange(DesktopScreen.Reads) },
                )

                NavigationRailItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    label = { Text("Search") },
                    selected = currentScreen == DesktopScreen.Search,
                    onClick = { onScreenChange(DesktopScreen.Search) },
                )

                NavigationRailItem(
                    icon = { Icon(com.vitorpamplona.amethyst.commons.icons.Bookmark, contentDescription = "Bookmarks") },
                    label = { Text("Bookmarks") },
                    selected = currentScreen == DesktopScreen.Bookmarks,
                    onClick = { onScreenChange(DesktopScreen.Bookmarks) },
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
                    icon = { Icon(Icons.Default.Extension, contentDescription = "Chess") },
                    label = { Text("Chess") },
                    selected = currentScreen == DesktopScreen.Chess,
                    onClick = { onScreenChange(DesktopScreen.Chess) },
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
                    DesktopScreen.Feed -> {
                        FeedScreen(
                            relayManager = relayManager,
                            localCache = localCache,
                            account = account,
                            nwcConnection = nwcConnection,
                            subscriptionsCoordinator = subscriptionsCoordinator,
                            onCompose = onShowComposeDialog,
                            onNavigateToProfile = { pubKeyHex ->
                                onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                            },
                            onNavigateToThread = { noteId ->
                                onScreenChange(DesktopScreen.Thread(noteId))
                            },
                            onZapFeedback = onZapFeedback,
                        )
                    }

                    DesktopScreen.Reads -> {
                        ReadsScreen(
                            relayManager = relayManager,
                            localCache = localCache,
                            account = account,
                            onNavigateToProfile = { pubKeyHex ->
                                onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                            },
                            onNavigateToArticle = { noteId ->
                                onScreenChange(DesktopScreen.Thread(noteId))
                            },
                        )
                    }

                    DesktopScreen.Search -> {
                        SearchScreen(
                            localCache = localCache,
                            relayManager = relayManager,
                            subscriptionsCoordinator = subscriptionsCoordinator,
                            onNavigateToProfile = { pubKeyHex ->
                                onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                            },
                            onNavigateToThread = { noteId ->
                                onScreenChange(DesktopScreen.Thread(noteId))
                            },
                        )
                    }

                    DesktopScreen.Bookmarks -> {
                        BookmarksScreen(
                            relayManager = relayManager,
                            localCache = localCache,
                            account = account,
                            nwcConnection = nwcConnection,
                            subscriptionsCoordinator = subscriptionsCoordinator,
                            onNavigateToProfile = { pubKeyHex ->
                                onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                            },
                            onNavigateToThread = { noteId ->
                                onScreenChange(DesktopScreen.Thread(noteId))
                            },
                            onZapFeedback = onZapFeedback,
                        )
                    }

                    DesktopScreen.Messages -> {
                        DesktopMessagesScreen(
                            account = iAccount,
                            cacheProvider = localCache,
                            relayManager = relayManager,
                            localCache = localCache,
                            onNavigateToProfile = { pubKeyHex ->
                                onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                            },
                        )
                    }

                    DesktopScreen.Notifications -> {
                        NotificationsScreen(relayManager, account, subscriptionsCoordinator)
                    }

                    DesktopScreen.Chess -> {
                        ChessScreen(
                            relayManager = relayManager,
                            account = account,
                            onBack = { onScreenChange(DesktopScreen.Feed) },
                        )
                    }

                    DesktopScreen.MyProfile -> {
                        UserProfileScreen(
                            pubKeyHex = account.pubKeyHex,
                            relayManager = relayManager,
                            localCache = localCache,
                            account = account,
                            nwcConnection = nwcConnection,
                            subscriptionsCoordinator = subscriptionsCoordinator,
                            onBack = { onScreenChange(DesktopScreen.Feed) },
                            onCompose = onShowComposeDialog,
                            onNavigateToProfile = { pubKeyHex ->
                                onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                            },
                            onZapFeedback = onZapFeedback,
                        )
                    }

                    is DesktopScreen.UserProfile -> {
                        UserProfileScreen(
                            pubKeyHex = currentScreen.pubKeyHex,
                            relayManager = relayManager,
                            localCache = localCache,
                            account = account,
                            nwcConnection = nwcConnection,
                            subscriptionsCoordinator = subscriptionsCoordinator,
                            onBack = { onScreenChange(DesktopScreen.Feed) },
                            onCompose = onShowComposeDialog,
                            onNavigateToProfile = { pubKeyHex ->
                                onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                            },
                            onZapFeedback = onZapFeedback,
                        )
                    }

                    is DesktopScreen.Thread -> {
                        ThreadScreen(
                            noteId = currentScreen.noteId,
                            relayManager = relayManager,
                            localCache = localCache,
                            account = account,
                            nwcConnection = nwcConnection,
                            subscriptionsCoordinator = subscriptionsCoordinator,
                            onBack = { onScreenChange(DesktopScreen.Feed) },
                            onNavigateToProfile = { pubKeyHex ->
                                onScreenChange(DesktopScreen.UserProfile(pubKeyHex))
                            },
                            onNavigateToThread = { noteId ->
                                onScreenChange(DesktopScreen.Thread(noteId))
                            },
                            onZapFeedback = onZapFeedback,
                            onReply = onShowReplyDialog,
                        )
                    }

                    DesktopScreen.Settings -> {
                        RelaySettingsScreen(relayManager, account, accountManager)
                    }
                }
            }
        }

        // Snackbar for zap feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
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
    accountManager: AccountManager,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val nwcConnection by accountManager.nwcConnection.collectAsState()
    var newRelayUrl by remember { mutableStateOf("") }
    var nwcInput by remember { mutableStateOf("") }
    var nwcError by remember { mutableStateOf<String?>(null) }

    // Load NWC on first composition
    LaunchedEffect(Unit) {
        accountManager.loadNwcConnection()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(24.dp))

        // Wallet Connect Section
        Text(
            "Wallet Connect (NWC)",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))

        Text(
            "Connect a Lightning wallet to enable zaps. Get a connection string from Alby, Mutiny, or other NWC-compatible wallets.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        if (nwcConnection != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Wallet Connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Relay: ${nwcConnection!!.relayUri.url}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { accountManager.clearNwcConnection() },
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text("Disconnect")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = nwcInput,
                    onValueChange = {
                        nwcInput = it
                        nwcError = null
                    },
                    label = { Text("NWC Connection String") },
                    placeholder = { Text("nostr+walletconnect://...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = nwcError != null,
                    supportingText = nwcError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )
                Button(
                    onClick = {
                        val result = accountManager.setNwcConnection(nwcInput)
                        result.fold(
                            onSuccess = { nwcInput = "" },
                            onFailure = { nwcError = it.message ?: "Invalid connection string" },
                        )
                    },
                    enabled = nwcInput.isNotBlank(),
                ) {
                    Text("Connect")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
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
