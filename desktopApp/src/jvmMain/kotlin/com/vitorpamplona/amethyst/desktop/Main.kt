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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.vitorpamplona.amethyst.commons.defaults.DefaultDmIndexerRelays
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.icons.symbols.ProvideMaterialSymbols
import com.vitorpamplona.amethyst.commons.moderation.LocalHashtagSpamSettings
import com.vitorpamplona.amethyst.commons.moderation.LocalSpamExemptKeys
import com.vitorpamplona.amethyst.commons.moderation.PreferencesHashtagSpamSettings
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalBanner
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.DmInboxRelayResolver
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.unwrapAndUnsealOrNull
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.wot.LocalWoTReady
import com.vitorpamplona.amethyst.commons.wot.LocalWoTService
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.auth.DesktopAuthCoordinator
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.model.DEFAULT_BLOSSOM_SERVER
import com.vitorpamplona.amethyst.desktop.model.DesktopAccountRelays
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.model.DesktopRelayCategories
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.network.Nip11Fetcher
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.amethyst.desktop.platform.applyNativeWindowChrome
import com.vitorpamplona.amethyst.desktop.service.highlights.DesktopHighlightStore
import com.vitorpamplona.amethyst.desktop.service.images.DesktopImageLoaderSetup
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.namecoin.DesktopNamecoinNameService
import com.vitorpamplona.amethyst.desktop.service.namecoin.DesktopNamecoinPreferences
import com.vitorpamplona.amethyst.desktop.service.namecoin.LocalNamecoinPreferences
import com.vitorpamplona.amethyst.desktop.service.namecoin.LocalNamecoinService
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.DesktopScheduledPostScheduler
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.DesktopScheduledPostStore
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.LocalScheduledPostStore
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.OsScheduler
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.runHeadlessPublish
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.ui.ComposeNoteDialog
import com.vitorpamplona.amethyst.desktop.ui.ConnectingRelaysScreen
import com.vitorpamplona.amethyst.desktop.ui.ImportFollowListDialog
import com.vitorpamplona.amethyst.desktop.ui.LocalBlossomServers
import com.vitorpamplona.amethyst.desktop.ui.LoginScreen
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.amethyst.desktop.ui.auth.ForceLogoutDialog
import com.vitorpamplona.amethyst.desktop.ui.chats.DmSendTracker
import com.vitorpamplona.amethyst.desktop.ui.deck.AppDrawer
import com.vitorpamplona.amethyst.desktop.ui.deck.DeckColumnType
import com.vitorpamplona.amethyst.desktop.ui.deck.DeckLayout
import com.vitorpamplona.amethyst.desktop.ui.deck.DeckState
import com.vitorpamplona.amethyst.desktop.ui.deck.MainSidebar
import com.vitorpamplona.amethyst.desktop.ui.deck.PinnedNavBarState
import com.vitorpamplona.amethyst.desktop.ui.deck.SinglePaneLayout
import com.vitorpamplona.amethyst.desktop.ui.deck.SinglePaneState
import com.vitorpamplona.amethyst.desktop.ui.deck.Workspace
import com.vitorpamplona.amethyst.desktop.ui.deck.WorkspaceManager
import com.vitorpamplona.amethyst.desktop.ui.deck.param
import com.vitorpamplona.amethyst.desktop.ui.media.LocalAwtWindow
import com.vitorpamplona.amethyst.desktop.ui.media.LocalIsImmersiveFullscreen
import com.vitorpamplona.amethyst.desktop.ui.media.LocalWindowState
import com.vitorpamplona.amethyst.desktop.ui.profile.ProfileInfoCard
import com.vitorpamplona.amethyst.desktop.ui.relay.LocalRelayCategories
import com.vitorpamplona.amethyst.desktop.ui.relay.RelayStatusCard
import com.vitorpamplona.amethyst.desktop.ui.settings.ImageCompressionSettings
import com.vitorpamplona.amethyst.desktop.ui.settings.MediaServerSettings
import com.vitorpamplona.amethyst.desktop.ui.settings.NamecoinSettingsSection
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val isMacOS = com.vitorpamplona.amethyst.desktop.platform.PlatformInfo.isMacOS

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

    data object NotificationSettings : DesktopScreen()

    data object Settings : DesktopScreen()

    data object LocalRelaySettings : DesktopScreen()

    data class FollowPackDetail(
        val addressTag: String,
    ) : DesktopScreen()

    data object FollowPackBrowseAll : DesktopScreen()
}

/** Reference to active Tor manager for shutdown hook. Set by App composable. */
@Volatile
private var activeTorManager: com.vitorpamplona.amethyst.desktop.tor.DesktopTorManager? = null

fun main(args: Array<String>) {
    // Headless publish mode: launched by the OS scheduler (~every 5 min) while the
    // desktop app is closed. Drains due, pre-signed scheduled posts and exits
    // WITHOUT touching Compose/AWT, the signer, or the keychain. Must run before any
    // window/AWT init so it stays a lightweight, GUI-free process.
    if (args.contains("--publish-scheduled")) {
        val code = runHeadlessPublish()
        kotlin.system.exitProcess(code)
    }

    // macOS: route the app's MenuBar to the system menu bar at the top of the
    // screen and set the application name shown in the apple-menu. Both must be
    // set before AWT initializes (i.e. before any Swing/AWT class loads).
    if (com.vitorpamplona.amethyst.desktop.platform.PlatformInfo.isMacOS) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.name", "Amethyst")
        System.setProperty("apple.awt.application.appearance", "system")
    }

    // Set the dock / taskbar icon image before any window is shown.
    //
    // On macOS the Cmd+Tab app switcher and the dock use the Taskbar API's
    // iconImage, NOT the Window(icon=) composable parameter (which only sets
    // the in-title-bar proxy icon). Without this, a JVM launched via gradle
    // shows the generic Java coffee-cup square in Cmd+Tab. ImageIO preserves
    // the PNG alpha channel so the dock renders the logo with transparency;
    // on macOS the logo is then wrapped in a squircle so it matches
    // first-party dock icons.
    try {
        val adapted = com.vitorpamplona.amethyst.desktop.platform.IconResources.adaptedBufferedImage
        if (adapted != null && java.awt.Taskbar.isTaskbarSupported()) {
            val taskbar = java.awt.Taskbar.getTaskbar()
            if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                taskbar.iconImage = adapted
            }
        }
    } catch (e: Exception) {
        Log.w("Main") { "Failed to set dock icon: ${e.message}" }
    }

    Log.minLevel = LogLevel.DEBUG
    DesktopImageLoaderSetup.setup()
    Runtime.getRuntime().addShutdownHook(
        Thread {
            GlobalMediaPlayer.shutdown()
            // Stop Tor daemon if running — reference set by App composable
            activeTorManager?.stopSync()
        },
    )
    // kdroidFilter lazy-loads the native player on first playback — no pre-init needed.
    application {
        val windowState =
            rememberWindowState(
                width = 1200.dp,
                height = 800.dp,
                position = WindowPosition.Aligned(Alignment.Center),
            )
        var appRestartKey by remember { mutableStateOf(0) }
        var showComposeDialog by remember { mutableStateOf(false) }
        var replyToNote by remember { mutableStateOf<com.vitorpamplona.quartz.nip01Core.core.Event?>(null) }
        // Prefill state for opening the composer to edit an existing draft / scheduled post.
        // Cleared on dismiss so the plain "New Note" path never inherits a stale prefill.
        var composeEditContent by remember { mutableStateOf<String?>(null) }
        var composeEditDraftTag by remember { mutableStateOf<String?>(null) }
        var composeEditScheduledForSec by remember { mutableStateOf<Long?>(null) }
        val deckScope = rememberCoroutineScope()
        val deckState = remember { DeckState(deckScope).also { it.load() } }
        val workspaceManager = remember { WorkspaceManager(deckScope).also { it.load() } }
        val accountManager = remember { AccountManager.create() }
        val accountState by accountManager.accountState.collectAsState()
        var showAppDrawer by remember { mutableStateOf(false) }
        var showAddColumnDialog by remember { mutableStateOf(false) }
        var showImportFollowListDialog by remember { mutableStateOf(false) }
        val feedSearchActiveState = remember { mutableStateOf(false) }

        // Tor state at Window level — survives key() app rebuild
        var torSettings by remember {
            mutableStateOf(
                com.vitorpamplona.amethyst.desktop.tor.DesktopTorPreferences
                    .load(),
            )
        }
        val torTypeFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(torSettings.torType) }
        val externalPortFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(torSettings.externalSocksPort) }
        val windowScope = rememberCoroutineScope()
        val torManager =
            remember {
                com.vitorpamplona.amethyst.desktop.tor.DesktopTorManager(torTypeFlow, externalPortFlow, windowScope).also {
                    activeTorManager = it
                }
            }
        var layoutMode by remember {
            mutableStateOf(
                try {
                    LayoutMode.valueOf(DesktopPreferences.layoutMode)
                } catch (e: Exception) {
                    LayoutMode.SINGLE_PANE
                },
            )
        }
        // Callback set by App() for single pane navigation from MenuBar
        var navigateToScreen by remember { mutableStateOf<((DeckColumnType) -> Unit)?>(null) }

        // Messages privacy lock CompositionLocals are provided inside App()
        // itself (see App() around line ~700) so tests that call App()
        // directly — bypassing this Main.kt Window shell — still get them.

        // Window title-bar / taskbar thumbnail icon. On macOS the source logo
        // is wrapped in a squircle so it matches every other dock icon; on
        // other platforms the raw transparent logo is used as-is.
        val appIcon = com.vitorpamplona.amethyst.desktop.platform.IconResources.adaptedBitmapPainter

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Amethyst",
            icon = appIcon,
        ) {
            // macOS: transparent + full-window-content title bar so the deck/sidebar
            // shows through, with traffic lights still drawn on top. No-op elsewhere.
            applyNativeWindowChrome()

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
                    Item(
                        "Save as Workspace",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.S, meta = true, shift = true)
                            } else {
                                KeyShortcut(Key.S, ctrl = true, shift = true)
                            },
                        onClick = {
                            if (workspaceManager.workspaces.value.size < WorkspaceManager.MAX_WORKSPACES) {
                                val columns =
                                    deckState.columns.value.map { col ->
                                        Workspace.WorkspaceColumn(
                                            typeKey = col.type.typeKey(),
                                            param = col.type.param(),
                                            width = col.width,
                                        )
                                    }
                                val ws =
                                    Workspace(
                                        name = "Workspace ${workspaceManager.workspaces.value.size + 1}",
                                        iconName = "Star",
                                        layoutMode = layoutMode,
                                        columns = columns,
                                        singlePaneScreens =
                                            if (layoutMode == LayoutMode.SINGLE_PANE) {
                                                columns.map { it.typeKey }
                                            } else {
                                                emptyList()
                                            },
                                    )
                                workspaceManager.addWorkspace(ws)
                            }
                        },
                        enabled = workspaceManager.workspaces.value.size < WorkspaceManager.MAX_WORKSPACES,
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
                        "Import Follow List…",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.I, meta = true, shift = true)
                            } else {
                                KeyShortcut(Key.I, ctrl = true, shift = true)
                            },
                        onClick = { showImportFollowListDialog = true },
                        enabled = accountState is AccountState.LoggedIn,
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
                        "Search",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.F, meta = true)
                            } else {
                                KeyShortcut(Key.F, ctrl = true)
                            },
                        onClick = {
                            feedSearchActiveState.value = !feedSearchActiveState.value
                        },
                    )
                    Item(
                        "App Drawer",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.K, meta = true)
                            } else {
                                KeyShortcut(Key.K, ctrl = true)
                            },
                        onClick = { showAppDrawer = !showAppDrawer },
                    )
                    Item(
                        "Relay Dashboard",
                        shortcut =
                            if (isMacOS) {
                                KeyShortcut(Key.R, meta = true, shift = true)
                            } else {
                                KeyShortcut(Key.R, ctrl = true, shift = true)
                            },
                        onClick = {
                            if (layoutMode == LayoutMode.DECK) {
                                if (deckState.hasColumnOfType(DeckColumnType.Relays)) {
                                    deckState.focusExistingColumn(DeckColumnType.Relays)
                                } else {
                                    deckState.addColumn(DeckColumnType.Relays)
                                }
                            } else {
                                navigateToScreen?.invoke(DeckColumnType.Relays)
                            }
                        },
                    )
                    Separator()
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
                            onClick = { showAppDrawer = true },
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
                            Item("Scheduled & Drafts", onClick = { deckState.addColumn(DeckColumnType.Drafts) })
                            Item("Highlights", onClick = { deckState.addColumn(DeckColumnType.MyHighlights) })
                            Item("Bookmarks", onClick = { deckState.addColumn(DeckColumnType.Bookmarks) })
                            Item("Global Feed", onClick = { deckState.addColumn(DeckColumnType.GlobalFeed) })
                            Item("Profile", onClick = { deckState.addColumn(DeckColumnType.MyProfile) })
                            Item("Chess", onClick = { deckState.addColumn(DeckColumnType.Chess) })
                            Item("Relays", onClick = { deckState.addColumn(DeckColumnType.Relays) })
                            Item("Wallet", onClick = { deckState.addColumn(DeckColumnType.Wallet) })
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
                key(appRestartKey) {
                    CompositionLocalProvider(
                        com.vitorpamplona.amethyst.desktop.ui.theme.LocalFeedSearchActive provides feedSearchActiveState,
                        com.vitorpamplona.amethyst.desktop.ui.theme.LocalOpenFullSearch provides {
                            navigateToScreen?.invoke(DeckColumnType.Search)
                        },
                    ) {
                        App(
                            layoutMode = layoutMode,
                            onLayoutModeChange = { newMode ->
                                layoutMode = newMode
                                DesktopPreferences.layoutMode = newMode.name
                            },
                            deckState = deckState,
                            workspaceManager = workspaceManager,
                            accountManager = accountManager,
                            showComposeDialog = showComposeDialog,
                            showAppDrawer = showAppDrawer,
                            onShowComposeDialog = { showComposeDialog = true },
                            onShowReplyDialog = { event ->
                                replyToNote = event
                                showComposeDialog = true
                            },
                            onEditInComposer = { content, draftDTag, scheduledForSec ->
                                composeEditContent = content
                                composeEditDraftTag = draftDTag
                                composeEditScheduledForSec = scheduledForSec
                                showComposeDialog = true
                            },
                            onDismissComposeDialog = {
                                showComposeDialog = false
                                replyToNote = null
                                composeEditContent = null
                                composeEditDraftTag = null
                                composeEditScheduledForSec = null
                            },
                            composeEditContent = composeEditContent,
                            composeEditDraftTag = composeEditDraftTag,
                            composeEditScheduledForSec = composeEditScheduledForSec,
                            onDismissAppDrawer = { showAppDrawer = false },
                            onShowAppDrawer = { showAppDrawer = true },
                            replyToNote = replyToNote,
                            showImportFollowListDialog = showImportFollowListDialog,
                            onShowImportFollowListDialog = { showImportFollowListDialog = true },
                            onDismissImportFollowListDialog = { showImportFollowListDialog = false },
                            onRestartApp = { appRestartKey++ },
                            torManager = torManager,
                            torTypeFlow = torTypeFlow,
                            externalPortFlow = externalPortFlow,
                            initialTorSettings = torSettings,
                            onNavigateToScreen = { navigateToScreen = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun App(
    layoutMode: LayoutMode,
    onLayoutModeChange: (LayoutMode) -> Unit,
    deckState: DeckState,
    workspaceManager: WorkspaceManager,
    accountManager: AccountManager,
    showComposeDialog: Boolean,
    showAppDrawer: Boolean,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onEditInComposer: (content: String, draftDTag: String?, scheduledForSec: Long?) -> Unit = { _, _, _ -> },
    onDismissComposeDialog: () -> Unit,
    onDismissAppDrawer: () -> Unit,
    onShowAppDrawer: () -> Unit,
    replyToNote: com.vitorpamplona.quartz.nip01Core.core.Event?,
    composeEditContent: String? = null,
    composeEditDraftTag: String? = null,
    composeEditScheduledForSec: Long? = null,
    showImportFollowListDialog: Boolean = false,
    onShowImportFollowListDialog: () -> Unit = {},
    onDismissImportFollowListDialog: () -> Unit = {},
    onRestartApp: () -> Unit = {},
    torManager: com.vitorpamplona.amethyst.commons.tor.ITorManager,
    torTypeFlow: kotlinx.coroutines.flow.MutableStateFlow<com.vitorpamplona.amethyst.commons.tor.TorType>,
    externalPortFlow: kotlinx.coroutines.flow.MutableStateFlow<Int>,
    initialTorSettings: com.vitorpamplona.amethyst.commons.tor.TorSettings,
    onNavigateToScreen: ((DeckColumnType) -> Unit) -> Unit = {},
    testOverrides: LaunchTestOverrides? = null,
) {
    val singlePaneState = remember { SinglePaneState() }
    val pinnedNavBarState = remember { PinnedNavBarState(workspaceManager).also { it.loadFromWorkspace() } }

    // Messages privacy lock — app-global settings + state holder, scoped to
    // App() so they survive appRestartKey rebuilds but rebuild on genuine app
    // restart. Provided as CompositionLocals right here so both production
    // (called from application { Window { App() } }) and tests (which call
    // App() directly, bypassing outer providers) see them.
    val appScope = rememberCoroutineScope()
    val privacyLockSettings =
        remember {
            com.vitorpamplona.amethyst.commons.privacylock
                .PreferencesPrivacyLockSettings()
        }
    val privacyLockStates =
        remember(privacyLockSettings) {
            val scopes = com.vitorpamplona.amethyst.commons.privacylock.LockScope.entries
            scopes.associateWith { scope ->
                com.vitorpamplona.amethyst.commons.privacylock
                    .PrivacyLockState(scope, privacyLockSettings, appScope)
            }
        }

    CompositionLocalProvider(
        com.vitorpamplona.amethyst.commons.privacylock.LocalPrivacyLockState provides privacyLockStates,
        com.vitorpamplona.amethyst.desktop.security.LocalPrivacyLockSettings provides privacyLockSettings,
    ) {
        AppInner(
            layoutMode = layoutMode,
            onLayoutModeChange = onLayoutModeChange,
            deckState = deckState,
            workspaceManager = workspaceManager,
            accountManager = accountManager,
            showComposeDialog = showComposeDialog,
            showAppDrawer = showAppDrawer,
            onShowComposeDialog = onShowComposeDialog,
            onShowReplyDialog = onShowReplyDialog,
            onEditInComposer = onEditInComposer,
            onDismissComposeDialog = onDismissComposeDialog,
            onDismissAppDrawer = onDismissAppDrawer,
            onShowAppDrawer = onShowAppDrawer,
            replyToNote = replyToNote,
            composeEditContent = composeEditContent,
            composeEditDraftTag = composeEditDraftTag,
            composeEditScheduledForSec = composeEditScheduledForSec,
            showImportFollowListDialog = showImportFollowListDialog,
            onShowImportFollowListDialog = onShowImportFollowListDialog,
            onDismissImportFollowListDialog = onDismissImportFollowListDialog,
            onRestartApp = onRestartApp,
            torManager = torManager,
            torTypeFlow = torTypeFlow,
            externalPortFlow = externalPortFlow,
            initialTorSettings = initialTorSettings,
            onNavigateToScreen = onNavigateToScreen,
            testOverrides = testOverrides,
            singlePaneState = singlePaneState,
            pinnedNavBarState = pinnedNavBarState,
        )
    }
}

@Composable
private fun AppInner(
    layoutMode: LayoutMode,
    onLayoutModeChange: (LayoutMode) -> Unit,
    deckState: DeckState,
    workspaceManager: WorkspaceManager,
    accountManager: AccountManager,
    showComposeDialog: Boolean,
    showAppDrawer: Boolean,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onEditInComposer: (content: String, draftDTag: String?, scheduledForSec: Long?) -> Unit,
    onDismissComposeDialog: () -> Unit,
    onDismissAppDrawer: () -> Unit,
    onShowAppDrawer: () -> Unit,
    replyToNote: com.vitorpamplona.quartz.nip01Core.core.Event?,
    composeEditContent: String?,
    composeEditDraftTag: String?,
    composeEditScheduledForSec: Long?,
    showImportFollowListDialog: Boolean,
    onShowImportFollowListDialog: () -> Unit,
    onDismissImportFollowListDialog: () -> Unit,
    onRestartApp: () -> Unit,
    torManager: com.vitorpamplona.amethyst.commons.tor.ITorManager,
    torTypeFlow: kotlinx.coroutines.flow.MutableStateFlow<com.vitorpamplona.amethyst.commons.tor.TorType>,
    externalPortFlow: kotlinx.coroutines.flow.MutableStateFlow<Int>,
    initialTorSettings: com.vitorpamplona.amethyst.commons.tor.TorSettings,
    onNavigateToScreen: ((DeckColumnType) -> Unit) -> Unit,
    testOverrides: LaunchTestOverrides?,
    singlePaneState: SinglePaneState,
    pinnedNavBarState: PinnedNavBarState,
) {
    // Register single pane navigation callback for MenuBar shortcuts
    LaunchedEffect(singlePaneState) {
        onNavigateToScreen { screen -> singlePaneState.navigate(screen) }
    }

    // Always reload from prefs — after key() rebuild, prefs have the latest saved settings.
    // Tests can short-circuit the prefs read via `testOverrides.torSettingsOverride` so the
    // Tor splash gate (below) does not block them behind a real kmp-tor runtime.
    var torSettings by remember {
        mutableStateOf(
            testOverrides?.torSettingsOverride
                ?: com.vitorpamplona.amethyst.desktop.tor.DesktopTorPreferences
                    .load(),
        )
    }

    // Gate: block EVERYTHING until Tor proxy is ready (when Tor expected)
    // This must be before any OkHttpClient/Coil/relay creation
    val torStatus by torManager.status.collectAsState()
    val isTorExpected = torSettings.torType != com.vitorpamplona.amethyst.commons.tor.TorType.OFF
    if (isTorExpected && torStatus !is com.vitorpamplona.amethyst.commons.tor.TorServiceStatus.Active) {
        val splashIcon = com.vitorpamplona.amethyst.desktop.platform.IconResources.rawBitmapPainter
        androidx.compose.foundation.layout.Box(
            modifier =
                androidx.compose.ui.Modifier
                    .fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.CircularProgressIndicator()
                androidx.compose.foundation.layout.Spacer(
                    modifier =
                        androidx.compose.ui.Modifier
                            .height(16.dp),
                )
                if (torStatus is com.vitorpamplona.amethyst.commons.tor.TorServiceStatus.Error) {
                    androidx.compose.material3.Text(
                        "Tor error: ${(torStatus as com.vitorpamplona.amethyst.commons.tor.TorServiceStatus.Error).message}",
                    )
                } else {
                    androidx.compose.material3.Text("Connecting to Tor...")
                }
                androidx.compose.foundation.layout.Spacer(
                    modifier =
                        androidx.compose.ui.Modifier
                            .height(24.dp),
                )
                androidx.compose.material3.Icon(
                    painter = splashIcon,
                    contentDescription = "Amethyst",
                    modifier =
                        androidx.compose.ui.Modifier
                            .size(96.dp),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                )
            }
        }
        return // Nothing below runs until Tor is Active
    }

    var appDrawerInitialTab by remember {
        mutableStateOf<com.vitorpamplona.amethyst.desktop.ui.deck.AppDrawerTab?>(null)
    }

    val localCache = remember { testOverrides?.localCache ?: DesktopLocalCache() }
    val accountState by accountManager.accountState.collectAsState()
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // Shared scheduled-post store — single writer per app, reached by the composer
    // and the in-app drain timer.
    val scheduledPostStore =
        remember {
            DesktopScheduledPostStore.create()
        }

    // Shared short-note draft store — written by the composer, read by the Drafts tab.
    val noteDraftStore =
        remember {
            com.vitorpamplona.amethyst.desktop.service.drafts
                .DesktopNoteDraftStore(scope)
        }

    // Hashtag-spam filter settings, persisted in the shared java.util.prefs
    // node so the `amy` CLI binary observes the same toggle.
    val hashtagSpamSettings = remember { PreferencesHashtagSpamSettings() }

    // Index-relay preference — user-configurable set used to fetch profile
    // metadata (kind 0) and follow lists (kind 3). Persisted in a shared
    // java.util.prefs node so `amy wot sync` reads from the same source of
    // truth. Falls back to PreferencesIndexRelays.DEFAULT_INDEX_RELAYS when
    // the user hasn't configured anything.
    val indexRelaysStore =
        remember {
            com.vitorpamplona.amethyst.commons.relays.index
                .PreferencesIndexRelays()
        }

    // Local relay store — persists events to SQLite per account
    val localRelayStore =
        remember {
            testOverrides?.localRelayStore ?: com.vitorpamplona.amethyst.desktop.relay
                .LocalRelayStore(scope)
        }
    val localRelayMaintenance =
        remember {
            com.vitorpamplona.amethyst.desktop.relay
                .LocalRelayMaintenance(localRelayStore, scope)
        }

    // Wire local relay store to cache for write-through
    LaunchedEffect(localRelayStore) {
        localCache.localRelayStore = localRelayStore
    }

    // Build TorRelayEvaluation for per-relay routing
    val torRelayEvaluation =
        remember(torSettings) {
            com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation(
                torSettings =
                    com.vitorpamplona.amethyst.commons.tor.TorRelaySettings(
                        torType = torSettings.torType,
                        onionRelaysViaTor = torSettings.onionRelaysViaTor,
                        dmRelaysViaTor = torSettings.dmRelaysViaTor,
                        newRelaysViaTor = torSettings.newRelaysViaTor,
                        trustedRelaysViaTor = torSettings.trustedRelaysViaTor,
                    ),
                trustedRelayList = emptySet(), // TODO: populate from account relay lists
                dmRelayList = emptySet(), // TODO: populate from account relay lists
            )
        }

    val httpClient =
        remember(torRelayEvaluation) {
            com.vitorpamplona.amethyst.desktop.network
                .DesktopHttpClient(
                    torManager = torManager,
                    shouldUseTorForRelay = { url -> torRelayEvaluation.useTor(url) },
                    torTypeProvider = { torTypeFlow.value },
                    scope = scope,
                ).also {
                    com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient
                        .setInstance(it)
                }
        }
    // Clean up old httpClient's connection pool on rebuild
    DisposableEffect(httpClient) {
        onDispose {
            httpClient.sharedConnectionPool.evictAll()
        }
    }

    // Reinitialize Coil after setInstance so image loads use the Tor-aware client
    remember(httpClient) {
        httpClient.evictConnections()
        com.vitorpamplona.amethyst.desktop.service.images.DesktopImageLoaderSetup
            .setup()
    }

    val relayManager =
        remember(httpClient) {
            testOverrides?.relayManager ?: DesktopRelayConnectionManager(httpClient)
        }
    val nip11Fetcher = remember { Nip11Fetcher() }

    // Dedicated unauthenticated NostrClient for kind:10050 lookups against
    // curated indexer relays. MUST NOT have a RelayAuthenticator attached —
    // an authenticated indexer query would extract identity-key signatures
    // and turn "indexer learns who we want to DM" into "indexer learns user
    // U wants to DM pubkey X" (security review F-01).
    val indexerClient =
        remember(httpClient) {
            NostrClient(BasicOkHttpWebSocket.Builder(httpClient::getHttpClient)).also { it.connect() }
        }
    DisposableEffect(indexerClient) {
        onDispose { indexerClient.disconnect() }
    }

    // Resolver consults LocalCache first, then its own LRU, then the indexer
    // client. Strict kind:10050 only — no NIP-65 read-marker fallback.
    val dmInboxResolver =
        remember(indexerClient, localCache) {
            DmInboxRelayResolver(
                unauthenticatedClient = indexerClient,
                indexerRelays =
                    DefaultDmIndexerRelays.RELAYS
                        .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                        .toSet(),
                localLookup = { pubkey ->
                    // Strict kind:10050 only — the lenient dmInboxRelays() falls
                    // back to NIP-65 read relays, which this fast-path would
                    // return before the strict indexer fan-out ran, leaking DM
                    // metadata to relays the recipient never designated for DMs.
                    localCache.getUserIfExists(pubkey)?.dmInboxRelaysStrict()
                },
                outboxLookup = { pubkey ->
                    // The recipient's kind:10050 lives on their NIP-65 write
                    // relays. When we've already cached their kind:10002 (via the
                    // feed / outbox dispatcher), hand those write relays to the
                    // resolver so it reads the 10050 from the source instead of
                    // relying on the curated indexers to have mirrored it.
                    localCache.cachedAdvertisedRelayList(pubkey)?.writeRelaysNorm()
                },
            )
        }

    // Start 1Hz metrics snapshot for relay dashboard
    LaunchedEffect(relayManager) {
        relayManager.startMetricsSnapshot(this)
    }

    // Detect host-machine sleep/wake: after a long delay overshoot the OkHttp
    // sockets we held are dead even though needsToReconnect() still reads false,
    // so force a hard disconnect+connect. See SleepResumeMonitor.kt.
    LaunchedEffect(relayManager) {
        com.vitorpamplona.amethyst.desktop.network.runSleepResumeMonitor {
            relayManager.client.reconnect(
                onlyIfChanged = false,
                ignoreRetryDelays = true,
            )
        }
    }

    // Subscriptions coordinator — uses the user's configured index relays
    // (or PreferencesIndexRelays.DEFAULT_INDEX_RELAYS as fallback) for
    // metadata + follow-list REQs. Changes made via the settings UI take
    // effect on next relaunch — the coordinator snapshots the set here.
    val subscriptionsCoordinator =
        remember(relayManager, localCache, indexRelaysStore) {
            DesktopRelaySubscriptionsCoordinator(
                client = relayManager.client,
                scope = scope,
                indexRelays = indexRelaysStore.effective(),
                localCache = localCache,
            ).also { coordinator ->
                coordinator.startCleanupLoop()
                // Whenever we ingest a user's kind:0 profile, back-fill their
                // kind:10002 so their outbox (write) relays — where their
                // kind:10050 and other replaceables live — are known.
                localCache.onProfileMetadataConsumed = { pubkey ->
                    coordinator.ensureOutboxRelayList(pubkey)
                }
            }
        }

    // NIP-42 AUTH coordinator — wires relay-auth challenges through the
    // tier classifier so own DM-inbox relays auto-sign and unknown relays
    // surface a tier-2 banner approval via authCoordinator.pendingApprovals.
    val authCoordinator =
        remember(relayManager, localCache) {
            DesktopAuthCoordinator(relayManager, localCache, scope)
        }

    // Clear cache and subscriptions on logout or account switch
    var previousAccountPubKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(accountState) {
        when (val state = accountState) {
            is AccountState.LoggedOut -> {
                authCoordinator.onLogout()
                subscriptionsCoordinator.clear()
                localCache.accountPubkey = null
                localCache.clear()
                localRelayMaintenance.stop()
                localRelayStore.close()
                previousAccountPubKey = null
            }

            is AccountState.LoggedIn -> {
                val currentPubKey = state.pubKeyHex
                if (previousAccountPubKey != null && previousAccountPubKey != currentPubKey) {
                    // Account switched — clear old data so new feed loads fresh
                    authCoordinator.onLogout()
                    subscriptionsCoordinator.clear()
                    localCache.accountPubkey = null
                    localCache.clear()
                    localRelayMaintenance.stop()
                    localRelayStore.close()
                    subscriptionsCoordinator.start()
                }
                // Bind the active-user pubkey BEFORE hydration launches. The
                // hydration coroutine below reads the local relay store on
                // Dispatchers.IO and calls consumeContactList; without this
                // ordering, a cached self kind-3 would be stamped without
                // updating _followedUsers, and a later relay retry of the
                // same event would be rejected by the createdAt gate,
                // leaving the follow list empty and FollowAction.follow
                // publishing a fresh kind-3 that wipes the real one.
                // See commons/plans/2026-07-06-fix-wot-outbox-model-and-review-fixes-plan.md
                // (Fix 1).
                localCache.accountPubkey = currentPubKey
                // Open local relay store for the current account and hydrate cache
                localRelayStore.openForAccount(currentPubKey)
                localRelayMaintenance.start()
                scope.launch(Dispatchers.IO) {
                    localRelayStore.hydrate(localCache)
                }
                authCoordinator.onLogin(state)
                previousAccountPubKey = currentPubKey
            }

            is AccountState.ConnectingRelays -> {}
            is AccountState.Loading -> {}
        }
    }

    // Fetch metadata for all accounts in the switcher + persist display names
    val allAccountsForMetadata by accountManager.allAccounts.collectAsState()
    LaunchedEffect(allAccountsForMetadata, accountState) {
        if (accountState !is AccountState.LoggedIn) return@LaunchedEffect
        // Wait for relay connections before requesting metadata
        relayManager.connectedRelays.first { it.isNotEmpty() }
        // Request metadata for all account pubkeys
        val pubkeys =
            allAccountsForMetadata.mapNotNull { info ->
                com.vitorpamplona.quartz.nip19Bech32
                    .decodePublicKeyAsHexOrNull(info.npub)
            }
        if (pubkeys.isNotEmpty()) {
            subscriptionsCoordinator.loadMetadataForPubkeys(pubkeys)
        }
    }

    // Persist display names when metadata arrives (debounced)
    LaunchedEffect(Unit) {
        localCache.metadataVersion.collect {
            kotlinx.coroutines.delay(3000)
            val accounts = accountManager.allAccounts.value
            for (info in accounts) {
                val hex =
                    com.vitorpamplona.quartz.nip19Bech32
                        .decodePublicKeyAsHexOrNull(info.npub) ?: continue
                val user = localCache.getUserIfExists(hex) ?: continue
                val name = user.toBestDisplayName()
                if (name != user.pubkeyDisplayHex() && name != info.displayName) {
                    accountManager.updateDisplayName(info.npub, name)
                }
            }
        }
    }

    // Try to load saved account on startup
    DisposableEffect(Unit) {
        if (testOverrides?.skipStartupRelayBootstrap != true) {
            relayManager.addDefaultRelays()
            relayManager.connect()
            subscriptionsCoordinator.start()
        }

        scope.launch(Dispatchers.IO) {
            // Load account list from encrypted storage
            accountManager.refreshAccountListOnStartup()

            val result = accountManager.loadSavedAccount()
            if (result.isSuccess) {
                accountManager.refreshAccountList()

                val current = accountManager.currentAccount()
                if (current?.signerType is com.vitorpamplona.amethyst.commons.model.account.SignerType.Remote) {
                    accountManager.startHeartbeat(scope)
                }
                // Load per-account NWC
                if (current != null) {
                    accountManager.loadNwcConnection(current.npub)
                }
            } else {
                // No saved account found → show login screen
                accountManager.setLoggedOut()
            }
        }

        onDispose {
            accountManager.stopHeartbeat()
            runBlocking { accountManager.disconnectNip46Client() }
            subscriptionsCoordinator.clear()
            relayManager.disconnect()
            localRelayMaintenance.stop()
            localRelayStore.close()
            scope.cancel()
        }
    }

    val isDark by com.vitorpamplona.amethyst.desktop.platform
        .rememberSystemDark(LocalAwtWindow.current)

    com.vitorpamplona.amethyst.desktop.platform.PlatformMaterialTheme(isDark = isDark) {
        ProvideMaterialSymbols(
            weight = com.vitorpamplona.amethyst.desktop.platform.PlatformIconWeight.current,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                // OS notification dispatcher: Nucleus-backed with AWT fallback.
                // Constructed once for the app lifetime; native lib loads
                // lazily on first requestPermission / send.
                val notifDispatcherScope = rememberCoroutineScope()
                val notifDispatcher =
                    remember {
                        com.vitorpamplona.amethyst.commons.moderation.notifications
                            .NucleusNotificationDispatcher(
                                bundleId = "com.vitorpamplona.amethyst.desktop",
                                appLabel = "Amethyst",
                                fallback =
                                    com.vitorpamplona.amethyst.commons.moderation.notifications
                                        .AwtTrayNotifier(),
                                scope = notifDispatcherScope,
                            )
                    }
                DisposableEffect(notifDispatcher) {
                    onDispose { notifDispatcher.release() }
                }

                // Window-focus tracking for the auto-dispatcher's suppression rule.
                val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
                val isWindowFocusedFlow =
                    remember {
                        kotlinx.coroutines.flow.MutableStateFlow(windowInfo.isWindowFocused)
                    }
                LaunchedEffect(windowInfo) {
                    androidx.compose.runtime
                        .snapshotFlow { windowInfo.isWindowFocused }
                        .collect { isWindowFocusedFlow.value = it }
                }

                // Session start — used by the auto-dispatcher to reject cold-boot backfill.
                val notifSessionStartSec =
                    remember {
                        com.vitorpamplona.amethyst.commons.moderation.notifications
                            .nowEpochSeconds()
                    }

                // Auto-dispatcher: subscribes to newEventBundles and fires OS toasts.
                // Only starts once the user is logged in — pubKey and settings must exist.
                val loggedIn = accountState as? AccountState.LoggedIn
                val notifSettings =
                    remember {
                        com.vitorpamplona.amethyst.commons.moderation.notifications
                            .PreferencesNotificationSettings()
                    }
                DisposableEffect(loggedIn?.pubKeyHex, notifDispatcher, localCache) {
                    val myPk = loggedIn?.pubKeyHex
                    val autoDispatcherJob =
                        if (myPk != null) {
                            com.vitorpamplona.amethyst.desktop.ui.notifications
                                .DesktopNotificationAutoDispatcher(
                                    dispatcher = notifDispatcher,
                                    settings = notifSettings,
                                    myPubKeyHex = myPk,
                                    localCache = localCache,
                                    isWindowFocused = isWindowFocusedFlow,
                                    sessionStartSec = notifSessionStartSec,
                                    scope = notifDispatcherScope,
                                ).start()
                        } else {
                            null
                        }
                    onDispose { autoDispatcherJob?.cancel() }
                }
                CompositionLocalProvider(
                    com.vitorpamplona.amethyst.desktop.ui.deck.LocalDesktopCache provides localCache,
                    com.vitorpamplona.amethyst.desktop.ui.deck.LocalRelayManager provides relayManager,
                    com.vitorpamplona.amethyst.desktop.ui.deck.LocalLocalRelayStore provides localRelayStore,
                    LocalScheduledPostStore provides scheduledPostStore,
                    com.vitorpamplona.amethyst.desktop.service.drafts.LocalNoteDraftStore provides noteDraftStore,
                    LocalHashtagSpamSettings provides hashtagSpamSettings,
                    com.vitorpamplona.amethyst.desktop.ui.notifications.LocalNotificationDispatcher provides notifDispatcher,
                ) {
                    when (accountState) {
                        is AccountState.Loading -> {
                            // Branded loading screen while accounts load from storage
                            val loadingIcon = com.vitorpamplona.amethyst.desktop.platform.IconResources.rawBitmapPainter
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "Amethyst",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    androidx.compose.material3.Icon(
                                        painter = loadingIcon,
                                        contentDescription = "Amethyst",
                                        modifier = Modifier.size(96.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        is AccountState.LoggedOut -> {
                            LoginScreen(
                                accountManager = accountManager,
                                onLoginSuccess = {
                                    // Start heartbeat if bunker account
                                    val current = accountManager.currentAccount()
                                    if (current?.signerType is com.vitorpamplona.amethyst.commons.model.account.SignerType.Remote) {
                                        accountManager.startHeartbeat(scope)
                                    }
                                    // Save account (privkey to keychain + metadata to disk)
                                    // then ensure multi-account storage is up to date.
                                    // Uses App-level scope so it survives LoginScreen leaving composition.
                                    scope.launch(Dispatchers.IO) {
                                        accountManager.saveCurrentAccount()
                                        accountManager.ensureCurrentAccountInStorage()
                                        accountManager.refreshAccountList()
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

                            // Account state holders (relay lists, blossom servers, DMs, WoT).
                            // Hoisted above MainContent so the top-level compose dialog can also
                            // read the account's blossom server list from iAccount directly.
                            val dmSendTracker = remember(relayManager) { DmSendTracker(relayManager.client) }
                            // Created before iAccount so NIP-65 backup can be loaded.
                            val accountRelays =
                                remember(account, relayManager, scope) {
                                    DesktopAccountRelays(account.pubKeyHex, relayManager, scope)
                                }
                            // Cold-boot AUTH race fix: when the account's own kind:10050 DM-inbox
                            // set loads (often AFTER an inbox relay has already challenged for
                            // AUTH), retroactively auto-approve any pending tier-2 prompt for a
                            // relay that is actually tier-1, instead of leaving a spurious banner.
                            LaunchedEffect(authCoordinator, accountRelays) {
                                accountRelays.dmRelayList.collect { dmInbox ->
                                    authCoordinator.onSelfApprovedRelaysChanged(dmInbox)
                                }
                            }
                            val iAccount =
                                remember(account, localCache, relayManager, dmSendTracker, accountRelays, dmInboxResolver) {
                                    DesktopIAccount(account, localCache, relayManager, dmSendTracker, scope, accountRelays, dmInboxResolver)
                                }
                            // When iAccount is replaced (account switch), close the previous
                            // WoTService so its writer coroutine + ops Channel don't leak.
                            DisposableEffect(iAccount) {
                                onDispose { iAccount.wotService.close() }
                            }

                            // Lazy-load Namecoin services. The Core RPC HTTP
                            // client is sourced from the Tor-aware DesktopHttpClient
                            // singleton so .onion RPC URLs route through the
                            // user's Tor settings without extra plumbing.
                            val namecoinPreferences = remember { DesktopNamecoinPreferences() }
                            val namecoinService =
                                remember {
                                    DesktopNamecoinNameService(
                                        preferencesProvider = { namecoinPreferences.current },
                                        pinnedCertsProvider = { namecoinPreferences.loadPinnedCerts() },
                                        coreRpcHttpClientProvider = { _ ->
                                            com.vitorpamplona.amethyst.desktop.network
                                                .DesktopHttpClient
                                                .currentClient()
                                        },
                                    )
                                }

                            // NWC loaded during startup in loadSavedAccount flow

                            val currentTorStatus = torManager.status.collectAsState().value
                            val followedUsers by localCache.followedUsers.collectAsState()
                            val spamExemptKeys =
                                remember(followedUsers, account.pubKeyHex) {
                                    followedUsers + account.pubKeyHex
                                }
                            androidx.compose.runtime.CompositionLocalProvider(
                                com.vitorpamplona.amethyst.desktop.ui.tor.LocalTorState provides
                                    com.vitorpamplona.amethyst.desktop.ui.tor.TorState(
                                        status = currentTorStatus,
                                        settings = torSettings,
                                        onSettingsChanged = { newSettings ->
                                            torSettings = newSettings
                                            com.vitorpamplona.amethyst.desktop.tor.DesktopTorPreferences
                                                .save(newSettings)
                                            torTypeFlow.value = newSettings.torType
                                            externalPortFlow.value = newSettings.externalSocksPort
                                            // Rebuild app to apply Tor changes
                                            onRestartApp()
                                        },
                                    ),
                                LocalNamecoinPreferences provides namecoinPreferences,
                                LocalNamecoinService provides namecoinService,
                                LocalSpamExemptKeys provides spamExemptKeys,
                                com.vitorpamplona.amethyst.desktop.model.LocalDesktopIAccount provides iAccount,
                            ) {
                                val pendingAuthApprovals by authCoordinator.pendingApprovals.collectAsState()
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // On macOS the window uses `apple.awt.fullWindowContent`
                                    // (see [applyNativeWindowChrome]), so the traffic-light
                                    // buttons sit over the top-left corner of content. Clear
                                    // that zone so the banner text/icon aren't occluded.
                                    val bannerModifier =
                                        if (PlatformInfo.isMacOS) {
                                            Modifier.padding(start = 80.dp, top = 8.dp, end = 8.dp, bottom = 4.dp)
                                        } else {
                                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        }
                                    AuthApprovalBanner(
                                        pending = pendingAuthApprovals.values.toList(),
                                        onResolve = { url, scope -> authCoordinator.resolve(url, scope) },
                                        modifier = bannerModifier,
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        MainContent(
                                            layoutMode = layoutMode,
                                            deckState = deckState,
                                            workspaceManager = workspaceManager,
                                            singlePaneState = singlePaneState,
                                            pinnedNavBarState = pinnedNavBarState,
                                            relayManager = relayManager,
                                            localCache = localCache,
                                            accountManager = accountManager,
                                            account = account,
                                            iAccount = iAccount,
                                            accountRelays = accountRelays,
                                            dmSendTracker = dmSendTracker,
                                            nwcConnection = nwcConnection,
                                            subscriptionsCoordinator = subscriptionsCoordinator,
                                            indexRelaysStore = indexRelaysStore,
                                            nip11Fetcher = nip11Fetcher,
                                            dmInboxResolver = dmInboxResolver,
                                            appScope = scope,
                                            torStatus = currentTorStatus,
                                            onShowComposeDialog = onShowComposeDialog,
                                            onShowReplyDialog = onShowReplyDialog,
                                            onEditInComposer = onEditInComposer,
                                            onShowAppDrawer = onShowAppDrawer,
                                            onOpenFeedsDrawer = {
                                                appDrawerInitialTab =
                                                    com.vitorpamplona.amethyst.desktop.ui.deck.AppDrawerTab.FEEDS
                                                onShowAppDrawer()
                                            },
                                            onShowImportFollowListDialog = onShowImportFollowListDialog,
                                        )
                                    }
                                }

                                // Import Follow List dialog (triggered from File menu /
                                // Cmd+Shift+I). Rendered inside this CompositionLocalProvider
                                // so LocalNamecoinService is available for .bit / d/ / id/
                                // identifier resolution.
                                if (showImportFollowListDialog) {
                                    ImportFollowListDialog(
                                        onDismiss = onDismissImportFollowListDialog,
                                        relayManager = relayManager,
                                        account = account,
                                        localCache = localCache,
                                    )
                                }
                            }

                            // Compose dialog. Hosted outside MainContent's provider,
                            // so provide the account's blossom list here too.
                            if (showComposeDialog) {
                                CompositionLocalProvider(
                                    LocalBlossomServers provides iAccount.blossomServerList.flow,
                                ) {
                                    ComposeNoteDialog(
                                        onDismiss = onDismissComposeDialog,
                                        relayManager = relayManager,
                                        account = account,
                                        localCache = localCache,
                                        replyTo = replyToNote,
                                        draftDTag = composeEditDraftTag,
                                        draftInitialContent = composeEditContent,
                                        initialScheduledForSec = composeEditScheduledForSec,
                                    )
                                }
                            }

                            // App Drawer overlay
                            if (showAppDrawer) {
                                val openColumns by deckState.columns.collectAsState()
                                AppDrawer(
                                    initialTab = appDrawerInitialTab,
                                    openColumnTypes =
                                        if (layoutMode == LayoutMode.DECK) {
                                            openColumns.map { it.type.typeKey() }.toSet()
                                        } else {
                                            emptySet()
                                        },
                                    pinnedNavBarState = pinnedNavBarState,
                                    workspaceManager = workspaceManager,
                                    onSwitchWorkspace = { ws ->
                                        // Switch layout mode to match workspace
                                        onLayoutModeChange(ws.layoutMode)
                                        // Load columns or single pane screen
                                        when (ws.layoutMode) {
                                            LayoutMode.DECK -> {
                                                deckState.loadFromWorkspace(ws.columns)
                                            }

                                            LayoutMode.SINGLE_PANE -> {
                                                // Load nav bar from workspace + navigate to first screen
                                                pinnedNavBarState.loadFromWorkspace()
                                                val firstKey =
                                                    ws.singlePaneScreens.firstOrNull() ?: "home"
                                                val type = DeckState.parseColumnTypeFromKey(firstKey)
                                                if (type != null) singlePaneState.navigate(type)
                                            }
                                        }
                                    },
                                    onSelectScreen = { type ->
                                        when (layoutMode) {
                                            LayoutMode.DECK -> {
                                                if (deckState.hasColumnOfType(type)) {
                                                    deckState.focusExistingColumn(type)
                                                } else {
                                                    deckState.addColumn(type)
                                                }
                                            }

                                            LayoutMode.SINGLE_PANE -> {
                                                singlePaneState.navigate(type)
                                            }
                                        }
                                    },
                                    onDismiss = {
                                        appDrawerInitialTab = null
                                        onDismissAppDrawer()
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
    }
}

@Composable
fun MainContent(
    layoutMode: LayoutMode,
    deckState: DeckState,
    workspaceManager: WorkspaceManager,
    singlePaneState: SinglePaneState,
    pinnedNavBarState: PinnedNavBarState,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    accountManager: AccountManager,
    account: AccountState.LoggedIn,
    iAccount: DesktopIAccount,
    accountRelays: DesktopAccountRelays,
    dmSendTracker: DmSendTracker,
    nwcConnection: Nip47WalletConnect.Nip47URINorm?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    indexRelaysStore: com.vitorpamplona.amethyst.commons.relays.index.PreferencesIndexRelays,
    nip11Fetcher: Nip11Fetcher,
    dmInboxResolver: DmInboxRelayResolver,
    appScope: CoroutineScope,
    torStatus: com.vitorpamplona.amethyst.commons.tor.TorServiceStatus,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onEditInComposer: (content: String, draftDTag: String?, scheduledForSec: Long?) -> Unit,
    onShowAppDrawer: () -> Unit,
    onOpenFeedsDrawer: () -> Unit = onShowAppDrawer,
    onShowImportFollowListDialog: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val signerConnectionState by accountManager.signerConnectionState.collectAsState()
    val lastPingTimeSec by accountManager.lastPingTimeSec.collectAsState()

    // Follow Packs state — single per-account holder for Discover + sidebar + naddr cards
    val followPacksState =
        remember(iAccount, localCache, relayManager, scope) {
            com.vitorpamplona.amethyst.desktop.followpacks
                .FollowPacksState(
                    cache = localCache,
                    relayManager = relayManager,
                    kind3FollowList = iAccount.kind3FollowList,
                    scope = scope,
                )
        }

    // Aggregated relay categories (feed, notifications, search, DM, index)
    val relayCategories =
        remember(iAccount.nip65RelayList, accountRelays, relayManager, indexRelaysStore) {
            DesktopRelayCategories(
                nip65State = iAccount.nip65RelayList,
                accountRelays = accountRelays,
                connectedRelays = relayManager.connectedRelays,
                indexRelaysStore = indexRelaysStore,
                scope = scope,
            )
        }

    val highlightStore = remember { DesktopHighlightStore(appScope) }
    val draftStore =
        remember {
            com.vitorpamplona.amethyst.desktop.service.drafts
                .DesktopDraftStore(appScope)
        }

    // In-app scheduled-post drain: catch-up on start, then tick every ~45s while the
    // app runs. Restarts on account switch so each account only publishes its own rows,
    // resolving relays from its NIP-65 outbox (falling back to connected relays).
    val scheduledPostStore = LocalScheduledPostStore.current
    val scheduledPostScheduler =
        remember(scheduledPostStore, appScope) {
            DesktopScheduledPostScheduler(scheduledPostStore, appScope)
        }
    DisposableEffect(scheduledPostScheduler, iAccount, relayManager) {
        scheduledPostScheduler.start(
            client = relayManager.client,
            accountPubkey = account.pubKeyHex,
            resolveRelays = {
                iAccount.nip65RelayList.outboxFlow.value
                    .ifEmpty { relayManager.connectedRelays.value }
            },
        )
        onDispose { scheduledPostScheduler.stop() }
    }

    // OS-level scheduler: keep a native timer job registered only while pending posts
    // exist, so scheduled posts fire even when the app is fully closed. The job
    // relaunches the app binary in headless `--publish-scheduled` mode. In dev
    // (`./gradlew run`) resolveAppLaunchCommand() is null → the scheduler no-ops.
    val osScheduler =
        remember {
            OsScheduler(
                OsScheduler.resolveAppLaunchCommand() ?: emptyList(),
            )
        }
    LaunchedEffect(osScheduler, scheduledPostStore) {
        // Level-triggered reconcile once on startup, then edge-triggered on the
        // pending-count crossing 0. Idempotent register/unregister make this safe.
        var lastHadPending: Boolean? = null
        scheduledPostStore.flow.collect { posts ->
            val hasPending =
                posts.any {
                    it.status == ScheduledPostStatus.PENDING ||
                        it.status == ScheduledPostStatus.PUBLISHING
                }
            if (hasPending != lastHadPending) {
                lastHadPending = hasPending
                withContext(Dispatchers.IO) {
                    if (hasPending) osScheduler.ensureRegistered() else osScheduler.unregister()
                }
            }
        }
    }

    // Bootstrap subscription: fetch relay config events (kinds 10002, 10050, 10007, 10006).
    // Subscribes immediately — `NostrClient` / `RelayPool` queue REQs that arrive before a
    // relay connection is up and flush them on connect (verified by
    // SubscribeBeforeConnectTest), so the previous
    // `connectedRelays.first { isNotEmpty() }` + 30s timeout gate has been
    // removed. Per the Phase 5.2 launch-optimization plan, this shaves the
    // cold-boot relay-bootstrap latency from "first connect roundtrip + sub
    // dispatch" to "sub dispatch only" once a relay is available, and it
    // also recovers gracefully when no relay ever connects (the
    // subscription stays queued for when one does, instead of silently
    // giving up after 30s).
    DisposableEffect(accountRelays) {
        val bootstrapSubId = "bootstrap-relay-config"
        val filter =
            Filter(
                kinds =
                    listOf(
                        AdvertisedRelayListEvent.KIND,
                        ChatMessageRelayListEvent.KIND,
                        SearchRelayListEvent.KIND,
                        BlockedRelayListEvent.KIND,
                        BlossomServersEvent.KIND,
                        MuteListEvent.KIND,
                    ),
                authors = listOf(account.pubKeyHex),
                limit = 5,
            )
        relayManager.subscribe(
            subId = bootstrapSubId,
            filters = listOf(filter),
            listener =
                object : SubscriptionListener {
                    override fun onEvent(
                        event: com.vitorpamplona.quartz.nip01Core.core.Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        // NIP-65 (kind 10002), the NIP-17 DM relay list (kind
                        // 10050) and the Blossom server list (kind 10063) are
                        // addressable/replaceable events; route them through
                        // justConsumeMyOwnEvent so they land in the addressable-
                        // note cache the User model / state holders observe (e.g.
                        // dmInboxRelaysStrict, self-copy resolution) alongside
                        // accountRelays' persisted copy.
                        if (event is AdvertisedRelayListEvent ||
                            event is ChatMessageRelayListEvent ||
                            event is BlossomServersEvent ||
                            event is MuteListEvent
                        ) {
                            scope.launch(Dispatchers.IO) {
                                localCache.justConsumeMyOwnEvent(event)
                            }
                        }
                        // Route to accountRelays for persistence + state updates
                        accountRelays.consumeIfRelevant(event)
                    }
                },
        )
        onDispose { relayManager.unsubscribe(bootstrapSubId) }
    }

    // The bootstrap subscription above only reaches the default relays. A user's
    // own account data — Blossom server list (kind 10063), DM/search/blocked
    // relay lists — is published to their write relays, not the defaults, so it
    // won't be found there. Re-fetch those kinds from the NIP-65 outbox
    // (write + untagged relays) whenever it becomes known.
    LaunchedEffect(iAccount) {
        iAccount.nip65RelayList.outboxFlow.collect { outbox ->
            if (outbox.isEmpty()) return@collect
            relayManager.subscribe(
                subId = "account-config-outbox",
                filters =
                    listOf(
                        Filter(
                            kinds =
                                listOf(
                                    AdvertisedRelayListEvent.KIND,
                                    ChatMessageRelayListEvent.KIND,
                                    SearchRelayListEvent.KIND,
                                    BlockedRelayListEvent.KIND,
                                    BlossomServersEvent.KIND,
                                    MuteListEvent.KIND,
                                ),
                            authors = listOf(account.pubKeyHex),
                            limit = 10,
                        ),
                    ),
                relays = outbox,
                listener =
                    object : SubscriptionListener {
                        override fun onEvent(
                            event: com.vitorpamplona.quartz.nip01Core.core.Event,
                            isLive: Boolean,
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            if (event is AdvertisedRelayListEvent || event is BlossomServersEvent || event is MuteListEvent) {
                                scope.launch(Dispatchers.IO) {
                                    localCache.justConsumeMyOwnEvent(event)
                                }
                            }
                            accountRelays.consumeIfRelevant(event)
                        }
                    },
            )
        }
    }

    // Subscribe to incoming DMs and process into chatroomList
    LaunchedEffect(account) {
        relayManager.connectedRelays.first { it.isNotEmpty() }

        subscriptionsCoordinator.subscribeToDms(
            userPubKeyHex = account.pubKeyHex,
            dmRelayState = accountRelays.dmRelays,
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
                        // NIP-17: peel both the gift-wrap and sealed-rumor layers.
                        scope.launch {
                            val innerEvent = event.unwrapAndUnsealOrNull(iAccount.signer) ?: return@launch
                            when (innerEvent) {
                                // Any DM-group event (kind 14 text, kind 15 encrypted file, and any
                                // future NIP-17 variant) routes into the room by its participant set.
                                is ChatroomKeyable -> {
                                    if (innerEvent.isIncluded(iAccount.pubKey)) {
                                        val innerNote = localCache.getOrCreateNote(innerEvent.id)
                                        val innerAuthor = localCache.getOrCreateUser(innerEvent.pubKey)
                                        if (innerNote.event == null) {
                                            innerNote.loadEvent(innerEvent, innerAuthor, emptyList())
                                        }
                                        // Rumors are unsigned: citing or rebroadcasting them must
                                        // go through the wrap that delivered them.
                                        innerNote.recordRumorHost(event)
                                        iAccount.chatroomList.addMessage(
                                            innerEvent.chatroomKey(iAccount.pubKey),
                                            innerNote,
                                        )
                                    }
                                }

                                // Self-authored NIP-37 draft wrapped to self. Store it so it isn't
                                // dropped; the desktop chat UI doesn't render drafts in the room feed
                                // yet, so it is intentionally not added to a chatroom.
                                is DraftWrapEvent -> {
                                    val draftNote = localCache.getOrCreateNote(innerEvent.id)
                                    val draftAuthor = localCache.getOrCreateUser(innerEvent.pubKey)
                                    if (draftNote.event == null) {
                                        draftNote.loadEvent(innerEvent, draftAuthor, emptyList())
                                    }
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

    // Relay-health store: per-account, persists liveness + snooze; installs a
    // RelayConnectionListener so quartz lifecycle drives the timestamps. The latency tracker
    // shares the same lifecycle — it's swept + snapshotted on the existing 60 s reclassify tick.
    val torStateForHealth = com.vitorpamplona.amethyst.desktop.ui.tor.LocalTorState.current
    val relayLatencyTracker =
        remember(account.pubKeyHex) {
            com.vitorpamplona.amethyst.commons.relays.health
                .RelayLatencyTracker()
        }
    val relayHealthStore =
        remember(account.pubKeyHex) {
            com.vitorpamplona.amethyst.commons.relays.health.RelayHealthStore(
                persistence =
                    com.vitorpamplona.amethyst.desktop.model.PreferencesRelayHealthPersistence(
                        userPubKeyHex = account.pubKeyHex,
                    ),
                torEnabledProvider = {
                    torStateForHealth.settings.torType != com.vitorpamplona.amethyst.commons.tor.TorType.OFF
                },
                parentScope = scope,
                // `prefs.flush()` is blocking — keep it off the composition scope's Main dispatcher.
                ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
                latencyTracker = relayLatencyTracker,
                nip11Provider = {
                    nip11Fetcher
                        .allCached()
                        .mapValues { entry ->
                            entry.value as com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation?
                        }.toPersistentMap()
                },
                // Desktop doesn't handle NIP-42 AUTH yet — treat any auth-required relay as
                // "auth not complete", so it's excluded from the slow cohort instead of being
                // perpetually flagged for sending CLOSED to anonymous queries.
                authProvider = { false },
            )
        }
    DisposableEffect(relayHealthStore, relayManager, relayLatencyTracker) {
        val healthListener =
            com.vitorpamplona.amethyst.commons.relays.health
                .RelayHealthListener(relayHealthStore)
        val latencyListener =
            com.vitorpamplona.amethyst.commons.relays.health
                .RelayLatencyListener(relayLatencyTracker)
        healthListener.installInto(relayManager.client)
        latencyListener.installInto(relayManager.client)
        onDispose {
            healthListener.uninstallFrom(relayManager.client)
            latencyListener.uninstallFrom(relayManager.client)
            relayHealthStore.close()
        }
    }
    // Build the relay-list mutator once per account (uses signer + the per-list states).
    val relayListMutator =
        remember(iAccount, accountRelays, relayManager) {
            com.vitorpamplona.amethyst.desktop.model.DesktopRelayListMutator(
                signer = iAccount.signer,
                nip65State = iAccount.nip65RelayList,
                accountRelays = accountRelays,
                relayManager = relayManager,
            )
        }
    // Push list-membership changes into the store so the classifier knows which relays count.
    LaunchedEffect(relayHealthStore, iAccount.nip65RelayList, accountRelays) {
        kotlinx.coroutines.flow
            .combine(
                iAccount.nip65RelayList.allFlowNoDefaults,
                accountRelays.dmRelayList,
                accountRelays.searchRelayList,
                accountRelays.blockedRelayList,
            ) { nip65, dm, search, blocked ->
                com.vitorpamplona.amethyst.desktop.model
                    .computeListMembership(nip65, dm, search, blocked)
            }.collect { membership ->
                relayHealthStore.setListMembership(membership)
            }
    }
    LaunchedEffect(relayHealthStore) {
        relayHealthStore.scanNow()
    }

    // Web-of-Trust: pubkey is already bound by the outer LaunchedEffect
    // that also gates hydration ordering (see the LoggedIn branch above).
    // This effect re-asserts the binding to cover the (rare) case where
    // MainContent's `account` diverges from the outer accountState mid-
    // recomposition; it's idempotent when they already match.
    LaunchedEffect(localCache, account.pubKeyHex) {
        localCache.accountPubkey = account.pubKeyHex
    }
    val wotReady by iAccount.wotService.isReady.collectAsState()
    LaunchedEffect(
        iAccount.wotService,
        localCache,
        subscriptionsCoordinator,
        account.pubKeyHex,
    ) {
        // Fan-in of every accepted kind-3 event from the local cache.
        launch {
            localCache.contactListEvents.collect { evt ->
                iAccount.wotService.applyKind3(evt.pubKey, evt.verifiedFollowKeySet())
            }
        }
        // React to changes in the active user's follow set. Under the
        // outbox model (PR #3483 review directive from Vitor) kind-3
        // fetch goes to each author's declared write relays instead of a
        // static index-relay broadcast — the OutboxDispatcher does the
        // NIP-65 discovery, transposes with RelayListRecommendationProcessor
        // and issues per-outbox-relay REQs. Falls back to index relays
        // for authors that never returned a 10002.
        launch {
            localCache.followedUsers.collect { follows ->
                iAccount.wotService.onFollowSetChange(follows, account.pubKeyHex)
                when {
                    iAccount.wotService.isDisabled.value -> {
                        // Guardrail — mega-follow accounts skip WoT
                        // entirely so we don't dispatch a batch that
                        // would be discarded anyway.
                        iAccount.wotService.markReadyOnce()
                    }
                    follows.isEmpty() -> {
                        iAccount.wotService.markReadyOnce()
                    }
                    else -> {
                        launch {
                            val result = subscriptionsCoordinator.loadKind3ViaOutbox(follows)
                            Log.d("WotOutbox") {
                                "fetchKind3Only authors=${result.authorsRequested} " +
                                    "covered=${result.outboxCoveredAuthors} " +
                                    "fallback=${result.fallbackAuthors} " +
                                    "kind10002=${result.kind10002Received} " +
                                    "kind3=${result.kind3Received}"
                            }
                            iAccount.wotService.markReadyOnce()
                        }
                    }
                }
            }
        }
        // Safety net: mark ready after 2s regardless of REQ progress so
        // avatars stop suppressing badges even if index relays never EOSE.
        launch {
            kotlinx.coroutines.delay(2_000)
            iAccount.wotService.markReadyOnce()
        }
    }

    CompositionLocalProvider(
        LocalRelayCategories provides relayCategories,
        LocalBlossomServers provides iAccount.blossomServerList.flow,
        com.vitorpamplona.amethyst.desktop.model.LocalDesktopIAccount provides iAccount,
        com.vitorpamplona.amethyst.desktop.ui.LocalSnackbarHost provides snackbarHostState,
        com.vitorpamplona.amethyst.desktop.ui.relay.LocalAccountRelays provides accountRelays,
        com.vitorpamplona.amethyst.desktop.ui.deck.LocalDesktopCache provides localCache,
        com.vitorpamplona.amethyst.desktop.ui.deck.LocalRelayManager provides relayManager,
        com.vitorpamplona.amethyst.desktop.ui.deck.LocalRelayHealthStore provides relayHealthStore,
        com.vitorpamplona.amethyst.desktop.ui.deck.LocalRelayListMutator provides relayListMutator,
        com.vitorpamplona.amethyst.desktop.ui.deck.LocalFollowPacksState provides followPacksState,
        LocalWoTService provides iAccount.wotService,
        LocalWoTReady provides wotReady,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize().weight(1f)) {
                    // Shared sidebar for both layout modes
                    if (!isImmersive) {
                        val allAccountsState by accountManager.allAccounts.collectAsState()
                        val searchActiveState = com.vitorpamplona.amethyst.desktop.ui.theme.LocalFeedSearchActive.current
                        var showAddAccountDialog by remember { mutableStateOf(false) }

                        // Derive active column type based on layout mode
                        val activeColumnType =
                            when (layoutMode) {
                                LayoutMode.DECK -> {
                                    val deckColumns by deckState.columns.collectAsState()
                                    val focusedIdx by deckState.focusedColumnIndex.collectAsState()
                                    deckColumns.getOrNull(focusedIdx)?.type
                                }
                                LayoutMode.SINGLE_PANE -> {
                                    val currentScreen by singlePaneState.currentScreen.collectAsState()
                                    currentScreen
                                }
                            }

                        Box {
                            MainSidebar(
                                activeNpub = accountManager.currentAccount()?.npub,
                                allAccounts = allAccountsState,
                                localCache = localCache,
                                onSwitchAccount = { npub ->
                                    scope.launch(Dispatchers.IO) {
                                        accountManager.switchAccount(npub)
                                    }
                                },
                                onAddAccount = { showAddAccountDialog = true },
                                onRemoveAccount = { npub ->
                                    scope.launch(Dispatchers.IO) {
                                        accountManager.removeAccountFromStorage(npub)
                                    }
                                },
                                onAddColumn = onShowAppDrawer,
                                onOpenSettings = {
                                    when (layoutMode) {
                                        LayoutMode.DECK -> {
                                            if (deckState.hasColumnOfType(DeckColumnType.Settings)) {
                                                deckState.focusExistingColumn(DeckColumnType.Settings)
                                            } else {
                                                deckState.addColumn(DeckColumnType.Settings)
                                            }
                                        }
                                        LayoutMode.SINGLE_PANE -> {
                                            singlePaneState.navigate(DeckColumnType.Settings)
                                        }
                                    }
                                },
                                onNavigate = { type ->
                                    searchActiveState.value = false
                                    when (layoutMode) {
                                        LayoutMode.DECK -> {
                                            if (deckState.hasColumnOfType(type)) {
                                                deckState.focusExistingColumn(type)
                                            } else {
                                                deckState.addColumn(type)
                                            }
                                        }
                                        LayoutMode.SINGLE_PANE -> {
                                            singlePaneState.navigate(type)
                                        }
                                    }
                                },
                                activeColumnType = activeColumnType,
                                feedTabActive = activeColumnType is DeckColumnType.HomeFeed,
                                onShowImportFollowListDialog = onShowImportFollowListDialog,
                                signerConnectionState = signerConnectionState,
                                lastPingTimeSec = lastPingTimeSec,
                                torStatus = torStatus,
                            )

                            // Dim sidebar when feed search is active
                            if (com.vitorpamplona.amethyst.desktop.ui.theme.LocalFeedSearchActive.current.value) {
                                Box(
                                    modifier =
                                        Modifier
                                            .matchParentSize()
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                )
                            }
                        }

                        VerticalDivider()

                        if (showAddAccountDialog) {
                            com.vitorpamplona.amethyst.desktop.ui.account.AddAccountDialog(
                                accountManager = accountManager,
                                onDismiss = { showAddAccountDialog = false },
                                onAccountAdded = {
                                    showAddAccountDialog = false
                                    scope.launch(Dispatchers.IO) {
                                        accountManager.refreshAccountList()
                                    }
                                },
                            )
                        }
                    }

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
                                nip11Fetcher = nip11Fetcher,
                                appScope = appScope,
                                singlePaneState = singlePaneState,
                                pinnedNavBarState = pinnedNavBarState,
                                onOpenAppDrawer = onShowAppDrawer,
                                onOpenFeedsDrawer = onOpenFeedsDrawer,
                                onShowComposeDialog = onShowComposeDialog,
                                onShowReplyDialog = onShowReplyDialog,
                                onEditInComposer = onEditInComposer,
                                onShowImportFollowListDialog = onShowImportFollowListDialog,
                                onZapFeedback = onZapFeedback,
                                signerConnectionState = signerConnectionState,
                                lastPingTimeSec = lastPingTimeSec,
                                lastRelayEventAt = lastRelayEvent,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        LayoutMode.DECK -> {
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
                                nip11Fetcher = nip11Fetcher,
                                appScope = appScope,
                                onShowComposeDialog = onShowComposeDialog,
                                onShowReplyDialog = onShowReplyDialog,
                                onEditInComposer = onEditInComposer,
                                onZapFeedback = onZapFeedback,
                                onNavigateToRelays = {
                                    if (deckState.hasColumnOfType(DeckColumnType.Relays)) {
                                        deckState.focusExistingColumn(DeckColumnType.Relays)
                                    } else {
                                        deckState.addColumn(DeckColumnType.Relays)
                                    }
                                },
                                onOpenNotificationSettings = {
                                    if (deckState.hasColumnOfType(DeckColumnType.NotificationSettings)) {
                                        deckState.focusExistingColumn(DeckColumnType.NotificationSettings)
                                    } else {
                                        deckState.addColumn(DeckColumnType.NotificationSettings)
                                    }
                                },
                                onOpenMessages = {
                                    if (deckState.hasColumnOfType(DeckColumnType.Messages)) {
                                        deckState.focusExistingColumn(DeckColumnType.Messages)
                                    } else {
                                        deckState.addColumn(DeckColumnType.Messages)
                                    }
                                },
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
    } // end CompositionLocalProvider
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
    namecoinPreferences: DesktopNamecoinPreferences? = null,
    onBlossomServersChanged: (List<String>) -> Unit = {},
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val nwcConnection by accountManager.nwcConnection.collectAsState()
    var newRelayUrl by remember { mutableStateOf("") }
    var nwcInput by remember { mutableStateOf("") }
    var nwcError by remember { mutableStateOf<String?>(null) }

    val nwcScope = rememberCoroutineScope()

    com.vitorpamplona.amethyst.desktop.ui.ReadingColumn {
        val sidePadding =
            com.vitorpamplona.amethyst.desktop.ui
                .readingHorizontalPadding()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = sidePadding),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Wallet Connect Section
            Text(
                "Wallet Connect (NWC)",
                style = MaterialTheme.typography.titleSmall,
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
                        onClick = { nwcScope.launch { accountManager.clearNwcConnection(account.npub) } },
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
                            nwcScope.launch {
                                val result = accountManager.setNwcConnection(account.npub, nwcInput)
                                result.fold(
                                    onSuccess = { nwcInput = "" },
                                    onFailure = { nwcError = it.message ?: "Invalid connection string" },
                                )
                            }
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

            // Media Server Settings (Blossom, kind 10063 — synced with mobile)
            val networkBlossomServers by (LocalBlossomServers.current?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) })
            // The kind-10063 list is authoritative; before it loads (or when the
            // user has published none) show the default server.
            val effectiveBlossomServers = networkBlossomServers.ifEmpty { listOf(DEFAULT_BLOSSOM_SERVER) }
            key(effectiveBlossomServers) {
                MediaServerSettings(
                    initialServers = effectiveBlossomServers,
                    onServersChanged = onBlossomServersChanged,
                )
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Image Compression Settings
            ImageCompressionSettings()
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

            // Namecoin Settings (ElectrumX servers for .bit / d/ / id/ resolution)
            val namecoinPrefsHere = namecoinPreferences ?: LocalNamecoinPreferences.current
            val namecoinServiceHere = LocalNamecoinService.current
            if (namecoinPrefsHere != null) {
                val namecoinScope = rememberCoroutineScope()
                val namecoinSettings by namecoinPrefsHere.settings.collectAsState()
                NamecoinSettingsSection(
                    settings = namecoinSettings,
                    onToggleEnabled = { enabled ->
                        namecoinScope.launch { namecoinPrefsHere.setEnabled(enabled) }
                    },
                    onAddServer = { server ->
                        namecoinScope.launch { namecoinPrefsHere.addServer(server) }
                    },
                    onRemoveServer = { server ->
                        namecoinScope.launch { namecoinPrefsHere.removeServer(server) }
                    },
                    onReset = {
                        namecoinScope.launch { namecoinPrefsHere.reset() }
                    },
                    onTestServer =
                        namecoinServiceHere?.let { svc ->
                            { server -> svc.client.testServer(server) }
                        },
                    onPinCert =
                        namecoinServiceHere?.let { svc ->
                            { pem ->
                                namecoinPrefsHere.addPinnedCert(pem)
                                // Apply immediately so the next lookup uses the new pin.
                                // The same list is shared with the Namecoin Core RPC
                                // client when present, mirroring Android's behaviour
                                // where both backends consume one trust store.
                                namecoinScope.launch {
                                    try {
                                        val pins = namecoinPrefsHere.loadPinnedCerts()
                                        svc.client.setDynamicCerts(pins)
                                        svc.rpcClient?.setDynamicCerts(pins)
                                    } catch (_: Exception) {
                                        // Best-effort — persisted, will apply on next restart.
                                    }
                                }
                            }
                        },
                    onSetBackend = { backend ->
                        namecoinScope.launch { namecoinPrefsHere.setBackend(backend) }
                    },
                    onSetCoreRpcConfig = { cfg ->
                        namecoinScope.launch {
                            namecoinPrefsHere.setCoreRpcConfig(cfg)
                            // Push the new config into the live client so the
                            // next lookup uses it without restarting the app.
                            namecoinServiceHere?.rpcClient?.setConfig(cfg)
                        }
                    },
                    onSetFallbackToCustomElectrumx = { enabled ->
                        namecoinScope.launch {
                            namecoinPrefsHere.setFallbackToCustomElectrumx(enabled)
                        }
                    },
                    onSetFallbackToDefaultElectrumx = { enabled ->
                        namecoinScope.launch {
                            namecoinPrefsHere.setFallbackToDefaultElectrumx(enabled)
                        }
                    },
                    onTestCoreRpc =
                        namecoinServiceHere?.let { svc ->
                            { cfg ->
                                svc.probeCoreRpc(cfg)
                                    ?: com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin
                                        .RpcProbeResult(
                                            success = false,
                                            elapsedMs = 0,
                                            error = "Namecoin Core RPC client not available",
                                        )
                            }
                        },
                )
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(24.dp))
            }

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
                style = MaterialTheme.typography.titleSmall,
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
                        MaterialSymbols.Refresh,
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
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Local Relay section
            val localRelay = com.vitorpamplona.amethyst.desktop.ui.deck.LocalLocalRelayStore.current
            if (localRelay != null) {
                com.vitorpamplona.amethyst.desktop.ui.settings.LocalRelaySettingsScreen(
                    localRelayStore = localRelay,
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            // Privacy lock section
            com.vitorpamplona.amethyst.desktop.ui.settings
                .PrivacyLockSettingsScreen()
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Content Filters section — hashtag-spam filter and future
            // content-moderation toggles.
            Text(
                text = "Content Filters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            com.vitorpamplona.amethyst.desktop.ui.settings.HashtagSpamSettingsSection(
                settings = LocalHashtagSpamSettings.current,
            )
            Spacer(Modifier.height(16.dp))
            com.vitorpamplona.amethyst.desktop.ui.settings
                .ModerationSettingsSection()
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
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
}
