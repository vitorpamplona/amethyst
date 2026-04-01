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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.vitorpamplona.amethyst.desktop.model.DesktopDmRelayState
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.DefaultRelays
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.highlights.DesktopHighlightStore
import com.vitorpamplona.amethyst.desktop.service.images.DesktopImageLoaderSetup
import com.vitorpamplona.amethyst.desktop.service.media.VlcjPlayerPool
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.ui.ComposeNoteDialog
import com.vitorpamplona.amethyst.desktop.ui.ConnectingRelaysScreen
import com.vitorpamplona.amethyst.desktop.ui.LoginScreen
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.amethyst.desktop.ui.auth.ForceLogoutDialog
import com.vitorpamplona.amethyst.desktop.ui.chats.DmSendTracker
import com.vitorpamplona.amethyst.desktop.ui.deck.AddColumnDialog
import com.vitorpamplona.amethyst.desktop.ui.deck.DeckColumnType
import com.vitorpamplona.amethyst.desktop.ui.deck.DeckLayout
import com.vitorpamplona.amethyst.desktop.ui.deck.DeckSidebar
import com.vitorpamplona.amethyst.desktop.ui.deck.DeckState
import com.vitorpamplona.amethyst.desktop.ui.deck.SinglePaneLayout
import com.vitorpamplona.amethyst.desktop.ui.media.LocalAwtWindow
import com.vitorpamplona.amethyst.desktop.ui.media.LocalIsImmersiveFullscreen
import com.vitorpamplona.amethyst.desktop.ui.media.LocalWindowState
import com.vitorpamplona.amethyst.desktop.ui.profile.ProfileInfoCard
import com.vitorpamplona.amethyst.desktop.ui.relay.RelayStatusCard
import com.vitorpamplona.amethyst.desktop.ui.settings.MediaServerSettings
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

enum class LayoutMode {
    SINGLE_PANE,
    DECK,
}

/**
 * Desktop navigation state — used for in-column navigation (drill-down).
 */
sealed class DesktopScreen {
    data object Feed : DesktopScreen()

    data object Reads : DesktopScreen()

    data object Search : DesktopScreen()

    data object Bookmarks : DesktopScreen()

    data object Messages : DesktopScreen()

    data object Notifications : DesktopScreen()

    data object Chess : DesktopScreen()

    data object MyProfile : DesktopScreen()

    data class UserProfile(
        val pubKeyHex: String,
    ) : DesktopScreen()

    data class Thread(
        val noteId: String,
    ) : DesktopScreen()

    data class Article(
        val addressTag: String,
    ) : DesktopScreen()

    data class Editor(
        val draftSlug: String? = null,
    ) : DesktopScreen()

    data object Drafts : DesktopScreen()

    data object Settings : DesktopScreen()
}

fun main() {
    Log.minLevel = LogLevel.DEBUG
    DesktopImageLoaderSetup.setup()
    Runtime.getRuntime().addShutdownHook(
        Thread {
            com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
                .shutdown()
            VlcjPlayerPool.shutdown()
        },
    )
    // Pre-init VLC on background thread so first play is fast
    Thread { VlcjPlayerPool.init() }.start()
    application {
        val windowState =
            rememberWindowState(
                width = 1200.dp,
                height = 800.dp,
                position = WindowPosition.Aligned(Alignment.Center),
            )
        var showComposeDialog by remember { mutableStateOf(false) }
        var replyToNote by remember { mutableStateOf<com.vitorpamplona.quartz.nip01Core.core.Event?>(null) }
        val deckScope = rememberCoroutineScope()
        val deckState = remember { DeckState(deckScope).also { it.load() } }
        val accountManager = remember { AccountManager.create() }
        val accountState by accountManager.accountState.collectAsState()
        var showAddColumnDialog by remember { mutableStateOf(false) }
        var layoutMode by remember {
            mutableStateOf(
                try {
                    LayoutMode.valueOf(DesktopPreferences.layoutMode)
                } catch (e: Exception) {
                    LayoutMode.SINGLE_PANE
                },
            )
        }

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
                        onClick = {
                            if (deckState.hasColumnOfType(DeckColumnType.Settings)) {
                                deckState.focusExistingColumn(DeckColumnType.Settings)
                            } else {
                                deckState.addColumn(DeckColumnType.Settings)
                            }
                        },
                    )
                    Separator()
                    Item(
                        "Logout",
                        onClick = {
                            deckScope.launch {
                                accountManager.logout(deleteKey = true)
                            }
                        },
                        enabled = accountState is AccountState.LoggedIn,
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
                    Item(
                        if (layoutMode == LayoutMode.DECK) "\u2713 Deck Layout" else "Deck Layout",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.D, meta = true, shift = true)
                            } else {
                                KeyShortcut(Key.D, ctrl = true, shift = true)
                            },
                        onClick = {
                            layoutMode =
                                if (layoutMode == LayoutMode.DECK) LayoutMode.SINGLE_PANE else LayoutMode.DECK
                            DesktopPreferences.layoutMode = layoutMode.name
                        },
                    )
                    if (layoutMode == LayoutMode.DECK) {
                        Separator()
                        Item(
                            "Add Column",
                            shortcut =
                                if (isMacOS) {
                                    KeyShortcut(Key.T, meta = true)
                                } else {
                                    KeyShortcut(Key.T, ctrl = true)
                                },
                            onClick = { showAddColumnDialog = true },
                        )
                        Item(
                            "Close Column",
                            shortcut =
                                if (isMacOS) {
                                    KeyShortcut(Key.W, meta = true)
                                } else {
                                    KeyShortcut(Key.W, ctrl = true)
                                },
                            onClick = {
                                val cols = deckState.columns.value
                                val idx = deckState.focusedColumnIndex.value
                                if (cols.size > 1 && idx in cols.indices) {
                                    deckState.removeColumn(cols[idx].id)
                                }
                            },
                        )
                        Item(
                            "Move Column Left",
                            shortcut =
                                if (isMacOS) {
                                    KeyShortcut(Key.DirectionLeft, meta = true, shift = true)
                                } else {
                                    KeyShortcut(Key.DirectionLeft, ctrl = true, shift = true)
                                },
                            onClick = {
                                val idx = deckState.focusedColumnIndex.value
                                if (idx > 0) {
                                    deckState.moveColumn(idx, idx - 1)
                                    deckState.focusColumn(idx - 1)
                                }
                            },
                        )
                        Item(
                            "Move Column Right",
                            shortcut =
                                if (isMacOS) {
                                    KeyShortcut(Key.DirectionRight, meta = true, shift = true)
                                } else {
                                    KeyShortcut(Key.DirectionRight, ctrl = true, shift = true)
                                },
                            onClick = {
                                val idx = deckState.focusedColumnIndex.value
                                val size = deckState.columns.value.size
                                if (idx < size - 1) {
                                    deckState.moveColumn(idx, idx + 1)
                                    deckState.focusColumn(idx + 1)
                                }
                            },
                        )
                        Separator()
                        // Focus column by index (Cmd/Ctrl+1..9)
                        val columnKeys =
                            listOf(
                                Key.One,
                                Key.Two,
                                Key.Three,
                                Key.Four,
                                Key.Five,
                                Key.Six,
                                Key.Seven,
                                Key.Eight,
                                Key.Nine,
                            )
                        val columnCount = deckState.columns.value.size
                        columnKeys.take(columnCount).forEachIndexed { i, key ->
                            Item(
                                "Column ${i + 1}",
                                shortcut =
                                    if (isMacOS) {
                                        KeyShortcut(key, meta = true)
                                    } else {
                                        KeyShortcut(key, ctrl = true)
                                    },
                                onClick = { deckState.focusColumn(i) },
                            )
                        }
                        Separator()
                        Menu("Add Column...") {
                            Item("Home Feed", onClick = { deckState.addColumn(DeckColumnType.HomeFeed) })
                            Item("Notifications", onClick = { deckState.addColumn(DeckColumnType.Notifications) })
                            Item("Messages", onClick = { deckState.addColumn(DeckColumnType.Messages) })
                            Item("Search", onClick = { deckState.addColumn(DeckColumnType.Search) })
                            Item("Reads", onClick = { deckState.addColumn(DeckColumnType.Reads) })
                            Item("Drafts", onClick = { deckState.addColumn(DeckColumnType.Drafts) })
                            Item("Highlights", onClick = { deckState.addColumn(DeckColumnType.MyHighlights) })
                            Item("Bookmarks", onClick = { deckState.addColumn(DeckColumnType.Bookmarks) })
                            Item("Global Feed", onClick = { deckState.addColumn(DeckColumnType.GlobalFeed) })
                            Item("Profile", onClick = { deckState.addColumn(DeckColumnType.MyProfile) })
                            Item("Chess", onClick = { deckState.addColumn(DeckColumnType.Chess) })
                        }
                    }
                }
                Menu("Help") {
                    Item("About Amethyst", onClick = { })
                    Item("Keyboard Shortcuts", onClick = { })
                }
            }

            val immersiveFullscreenState = remember { mutableStateOf(false) }
            CompositionLocalProvider(
                LocalWindowState provides windowState,
                LocalAwtWindow provides window,
                LocalIsImmersiveFullscreen provides immersiveFullscreenState,
            ) {
                App(
                    layoutMode = layoutMode,
                    deckState = deckState,
                    accountManager = accountManager,
                    showComposeDialog = showComposeDialog,
                    showAddColumnDialog = showAddColumnDialog,
                    onShowComposeDialog = { showComposeDialog = true },
                    onShowReplyDialog = { event ->
                        replyToNote = event
                        showComposeDialog = true
                    },
                    onDismissComposeDialog = {
                        showComposeDialog = false
                        replyToNote = null
                    },
                    onDismissAddColumnDialog = { showAddColumnDialog = false },
                    onShowAddColumnDialog = { showAddColumnDialog = true },
                    replyToNote = replyToNote,
                )
            }
        }
    }
}

@Composable
fun App(
    layoutMode: LayoutMode,
    deckState: DeckState,
    accountManager: AccountManager,
    showComposeDialog: Boolean,
    showAddColumnDialog: Boolean,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onDismissComposeDialog: () -> Unit,
    onDismissAddColumnDialog: () -> Unit,
    onShowAddColumnDialog: () -> Unit,
    replyToNote: com.vitorpamplona.quartz.nip01Core.core.Event?,
) {
    val localCache = remember { DesktopLocalCache() }
    val accountState by accountManager.accountState.collectAsState()
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // Tor support: load settings, create manager, create proxy-aware HTTP client
    val torSettings =
        remember {
            com.vitorpamplona.amethyst.desktop.tor.DesktopTorPreferences
                .load()
        }
    val torTypeFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(torSettings.torType) }
    val externalPortFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(torSettings.externalSocksPort) }
    val torManager =
        remember {
            com.vitorpamplona.amethyst.desktop.tor
                .DesktopTorManager(torTypeFlow, externalPortFlow, scope)
        }
    val httpClient =
        remember {
            com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient(
                torManager = torManager,
                shouldUseTorForRelay = { false }, // TODO: wire TorRelayEvaluation when settings UI is built
                scope = scope,
            )
        }
    val relayManager = remember { DesktopRelayConnectionManager(httpClient) }

    // Subscriptions coordinator — uses default relay URLs for metadata indexing.
    // Feed subscriptions (inside MainContent) drive actual relay pool connections.
    val subscriptionsCoordinator =
        remember(relayManager, localCache) {
            DesktopRelaySubscriptionsCoordinator(
                client = relayManager.client,
                scope = scope,
                indexRelays =
                    DefaultRelays.RELAYS
                        .mapNotNull {
                            RelayUrlNormalizer.normalizeOrNull(it)
                        }.toSet(),
                localCache = localCache,
            ).also { it.startCleanupLoop() }
        }

    // Clear cache and subscriptions on logout
    LaunchedEffect(accountState) {
        if (accountState is AccountState.LoggedOut) {
            subscriptionsCoordinator.clear()
            localCache.clear()
        }
    }

    // Try to load saved account on startup
    DisposableEffect(Unit) {
        relayManager.addDefaultRelays()
        relayManager.connect()
        subscriptionsCoordinator.start()

        scope.launch(Dispatchers.IO) {
            if (accountManager.hasBunkerAccount()) {
                // Show connecting UI while dedicated NIP-46 client connects
                accountManager.setConnectingRelays()
            }
            val result = accountManager.loadSavedAccount()
            if (result.isSuccess) {
                val current = accountManager.currentAccount()
                if (current?.signerType is com.vitorpamplona.amethyst.desktop.account.SignerType.Remote) {
                    accountManager.startHeartbeat(scope)
                }
            } else if (accountManager.hasBunkerAccount()) {
                // Corrupt bunker state — fall back to login screen
                accountManager.logout(deleteKey = true)
            }
        }

        onDispose {
            accountManager.stopHeartbeat()
            runBlocking { accountManager.disconnectNip46Client() }
            subscriptionsCoordinator.clear()
            relayManager.disconnect()
            scope.cancel()
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
                        onLoginSuccess = {
                            // Start heartbeat if bunker account
                            val current = accountManager.currentAccount()
                            if (current?.signerType is com.vitorpamplona.amethyst.desktop.account.SignerType.Remote) {
                                accountManager.startHeartbeat(scope)
                            }
                        },
                    )
                }

                is AccountState.ConnectingRelays -> {
                    val relays by relayManager.relayStatuses.collectAsState()
                    ConnectingRelaysScreen(
                        subtitle = "Restoring remote signer session",
                        relayStatuses = relays,
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
                        layoutMode = layoutMode,
                        deckState = deckState,
                        relayManager = relayManager,
                        localCache = localCache,
                        accountManager = accountManager,
                        account = account,
                        nwcConnection = nwcConnection,
                        subscriptionsCoordinator = subscriptionsCoordinator,
                        appScope = scope,
                        torStatus = torManager.status.collectAsState().value,
                        onShowComposeDialog = onShowComposeDialog,
                        onShowReplyDialog = onShowReplyDialog,
                        onShowAddColumnDialog = onShowAddColumnDialog,
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

                    // Add column dialog
                    if (showAddColumnDialog) {
                        AddColumnDialog(
                            onDismiss = onDismissAddColumnDialog,
                            onAdd = { type ->
                                deckState.addColumn(type)
                                onDismissAddColumnDialog()
                            },
                        )
                    }
                }
            }

            // Force logout dialog overlay
            val forceLogoutReason by accountManager.forceLogoutReason.collectAsState()
            forceLogoutReason?.let { reason ->
                ForceLogoutDialog(
                    reason = reason,
                    onDismiss = { accountManager.clearForceLogoutReason() },
                )
            }
        }
    }
}

@Composable
fun MainContent(
    layoutMode: LayoutMode,
    deckState: DeckState,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    accountManager: AccountManager,
    account: AccountState.LoggedIn,
    nwcConnection: Nip47WalletConnect.Nip47URINorm?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    appScope: CoroutineScope,
    torStatus: com.vitorpamplona.amethyst.commons.tor.TorServiceStatus,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onShowAddColumnDialog: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val signerConnectionState by accountManager.signerConnectionState.collectAsState()
    val lastPingTimeSec by accountManager.lastPingTimeSec.collectAsState()

    // DM infrastructure — hoisted here so it survives screen navigation
    val dmSendTracker =
        remember(relayManager) {
            DmSendTracker(relayManager.client)
        }
    val iAccount =
        remember(account, localCache, relayManager, dmSendTracker) {
            DesktopIAccount(account, localCache, relayManager, dmSendTracker, scope)
        }

    val highlightStore = remember { DesktopHighlightStore(appScope) }
    val draftStore =
        remember {
            com.vitorpamplona.amethyst.desktop.service.drafts
                .DesktopDraftStore(appScope)
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

                                is com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent -> {
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
                                    innerEvent.originalPost().forEach { targetId ->
                                        val targetNote = localCache.getNoteIfExists(targetId)
                                        targetNote?.addReaction(reactionNote)
                                    }
                                }

                                else -> {}
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

    val isImmersive by com.vitorpamplona.amethyst.desktop.ui.media.LocalIsImmersiveFullscreen.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().weight(1f)) {
                when (layoutMode) {
                    LayoutMode.SINGLE_PANE -> {
                        val lastRelayEvent by subscriptionsCoordinator.lastEventAt.collectAsState()
                        SinglePaneLayout(
                            relayManager = relayManager,
                            localCache = localCache,
                            accountManager = accountManager,
                            account = account,
                            iAccount = iAccount,
                            nwcConnection = nwcConnection,
                            subscriptionsCoordinator = subscriptionsCoordinator,
                            highlightStore = highlightStore,
                            draftStore = draftStore,
                            appScope = appScope,
                            onShowComposeDialog = onShowComposeDialog,
                            onShowReplyDialog = onShowReplyDialog,
                            onZapFeedback = onZapFeedback,
                            signerConnectionState = signerConnectionState,
                            lastPingTimeSec = lastPingTimeSec,
                            lastRelayEventAt = lastRelayEvent,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    LayoutMode.DECK -> {
                        if (!isImmersive) {
                            DeckSidebar(
                                onAddColumn = onShowAddColumnDialog,
                                onOpenSettings = {
                                    if (deckState.hasColumnOfType(DeckColumnType.Settings)) {
                                        deckState.focusExistingColumn(DeckColumnType.Settings)
                                    } else {
                                        deckState.addColumn(DeckColumnType.Settings)
                                    }
                                },
                                signerConnectionState = signerConnectionState,
                                lastPingTimeSec = lastPingTimeSec,
                                torStatus = torStatus,
                            )

                            VerticalDivider()
                        }

                        DeckLayout(
                            deckState = deckState,
                            relayManager = relayManager,
                            localCache = localCache,
                            accountManager = accountManager,
                            account = account,
                            iAccount = iAccount,
                            nwcConnection = nwcConnection,
                            subscriptionsCoordinator = subscriptionsCoordinator,
                            highlightStore = highlightStore,
                            draftStore = draftStore,
                            appScope = appScope,
                            onShowComposeDialog = onShowComposeDialog,
                            onShowReplyDialog = onShowReplyDialog,
                            onZapFeedback = onZapFeedback,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } // end Row

            // Persistent media control bar
            com.vitorpamplona.amethyst.desktop.ui.media
                .NowPlayingBar()
        } // end Column

        // Global fullscreen video overlay
        com.vitorpamplona.amethyst.desktop.ui.media
            .GlobalFullscreenOverlay()

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
            onClick = { scope.launch { accountManager.logout(deleteKey = true) } },
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
    torStatus: com.vitorpamplona.amethyst.commons.tor.TorServiceStatus = com.vitorpamplona.amethyst.commons.tor.TorServiceStatus.Off,
    torSettings: com.vitorpamplona.amethyst.commons.tor.TorSettings =
        com.vitorpamplona.amethyst.commons.tor
            .TorSettings(torType = com.vitorpamplona.amethyst.commons.tor.TorType.OFF),
    onTorSettingsChanged: (com.vitorpamplona.amethyst.commons.tor.TorSettings) -> Unit = {},
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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
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

        // Media Server Settings
        MediaServerSettings(
            initialServers = DesktopPreferences.blossomServers,
            onServersChanged = { DesktopPreferences.blossomServers = it },
        )
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // Tor Settings
        com.vitorpamplona.amethyst.desktop.ui.tor.TorSettingsSection(
            torStatus = torStatus,
            currentSettings = torSettings,
            onSettingsChanged = onTorSettingsChanged,
        )
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

        Spacer(Modifier.height(16.dp))

        val logoutScope = rememberCoroutineScope()
        OutlinedButton(
            onClick = { logoutScope.launch { accountManager.logout(deleteKey = true) } },
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
        ) {
            Text("Logout")
        }
    }
}
