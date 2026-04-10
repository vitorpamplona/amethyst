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
package com.vitorpamplona.amethyst.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.ui.feed.FeedHeader
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.screens.MessagesPlaceholder
import com.vitorpamplona.amethyst.commons.ui.screens.NotificationsPlaceholder
import com.vitorpamplona.amethyst.ios.account.AccountManager
import com.vitorpamplona.amethyst.ios.account.AccountState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.feeds.IosFollowingFeedFilter
import com.vitorpamplona.amethyst.ios.feeds.IosGlobalFeedFilter
import com.vitorpamplona.amethyst.ios.feeds.IosProfileFeedFilter
import com.vitorpamplona.amethyst.ios.feeds.IosThreadFilter
import com.vitorpamplona.amethyst.ios.namecoin.IosNamecoinNameService
import com.vitorpamplona.amethyst.ios.namecoin.IosNamecoinPreferences
import com.vitorpamplona.amethyst.ios.namecoin.LocalNamecoinPreferences
import com.vitorpamplona.amethyst.ios.namecoin.LocalNamecoinService
import com.vitorpamplona.amethyst.ios.namecoin.NamecoinSearchBar
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.ios.subscriptions.IosSubscriptionsCoordinator
import com.vitorpamplona.amethyst.ios.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.ios.subscriptions.generateSubId
import com.vitorpamplona.amethyst.ios.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.ios.ui.LoginScreen
import com.vitorpamplona.amethyst.ios.ui.note.NoteCard
import com.vitorpamplona.amethyst.ios.ui.toNoteDisplayData
import com.vitorpamplona.amethyst.ios.viewmodels.IosFeedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

enum class Tab(
    val icon: ImageVector,
    val label: String,
) {
    FEED(Icons.Default.Home, "Feed"),
    SEARCH(Icons.Default.Search, "Search"),
    NOTIFICATIONS(Icons.Default.Notifications, "Notifications"),
    MESSAGES(Icons.Default.MailOutline, "Messages"),
    PROFILE(Icons.Default.Person, "Profile"),
}

sealed class Screen {
    data object Feed : Screen()

    data object Search : Screen()

    data object Notifications : Screen()

    data object Messages : Screen()

    data object MyProfile : Screen()

    data class Profile(
        val pubKeyHex: String,
    ) : Screen()

    data class Thread(
        val noteId: String,
    ) : Screen()
}

enum class FeedMode { GLOBAL, FOLLOWING }

@Composable
fun App() {
    val accountManager = remember { AccountManager() }
    val accountState by accountManager.accountState.collectAsState()

    LaunchedEffect(Unit) {
        accountManager.tryRestoreSession()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val state = accountState) {
                is AccountState.LoggedOut -> {
                    LoginScreen(
                        onLogin = { key -> accountManager.login(key) },
                        onCreateAccount = { accountManager.createAccount() },
                    )
                }

                is AccountState.LoggedIn -> {
                    MainScreen(
                        account = state,
                        onLogout = { accountManager.logout() },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    account: AccountState.LoggedIn,
    onLogout: () -> Unit,
) {
    val relayManager = remember { IosRelayConnectionManager() }
    val localCache = remember { IosLocalCache() }
    val coordinator = remember { IosSubscriptionsCoordinator(CoroutineScope(Dispatchers.Default), localCache) }
    val namecoinPreferences = remember { IosNamecoinPreferences() }
    val namecoinService = remember { IosNamecoinNameService(namecoinPreferences) }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Feed) }
    var selectedTab by remember { mutableStateOf(Tab.FEED) }
    val navStack = remember { mutableListOf<Screen>() }

    fun navigateTo(screen: Screen) {
        navStack.add(currentScreen)
        currentScreen = screen
    }

    fun goBack() {
        if (navStack.isNotEmpty()) {
            currentScreen = navStack.removeAt(navStack.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        relayManager.addDefaultRelays()
        relayManager.connect()
    }

    val showBottomBar =
        currentScreen is Screen.Feed || currentScreen is Screen.Search ||
            currentScreen is Screen.Notifications || currentScreen is Screen.Messages ||
            currentScreen is Screen.MyProfile

    CompositionLocalProvider(
        LocalNamecoinService provides namecoinService,
        LocalNamecoinPreferences provides namecoinPreferences,
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(24.dp)) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    navStack.clear()
                                    currentScreen =
                                        when (tab) {
                                            Tab.FEED -> Screen.Feed
                                            Tab.SEARCH -> Screen.Search
                                            Tab.NOTIFICATIONS -> Screen.Notifications
                                            Tab.MESSAGES -> Screen.Messages
                                            Tab.PROFILE -> Screen.MyProfile
                                        }
                                },
                            )
                        }
                    }
                }
            },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when (val screen = currentScreen) {
                    is Screen.Feed -> {
                        IosFeedContent(
                            relayManager = relayManager,
                            localCache = localCache,
                            coordinator = coordinator,
                            pubKeyHex = account.pubKeyHex,
                            onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                            onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                        )
                    }

                    is Screen.Search -> {
                        NamecoinSearchBar(
                            namecoinService = namecoinService,
                            onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                        )
                    }

                    is Screen.Notifications -> {
                        // TODO: Wire commons notification feed when full notification UI is available
                        NotificationsPlaceholder(modifier = Modifier.padding(16.dp))
                    }

                    is Screen.Messages -> {
                        // TODO: Wire commons chat components when full DM UI is available
                        MessagesPlaceholder(modifier = Modifier.padding(16.dp))
                    }

                    is Screen.MyProfile -> {
                        IosProfileContent(
                            pubKeyHex = account.pubKeyHex,
                            relayManager = relayManager,
                            localCache = localCache,
                            coordinator = coordinator,
                            onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                            onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                        )
                    }

                    is Screen.Profile -> {
                        IosProfileContent(
                            pubKeyHex = screen.pubKeyHex,
                            relayManager = relayManager,
                            localCache = localCache,
                            coordinator = coordinator,
                            onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                            onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                        )
                    }

                    is Screen.Thread -> {
                        IosThreadContent(
                            noteId = screen.noteId,
                            relayManager = relayManager,
                            localCache = localCache,
                            coordinator = coordinator,
                            onNavigateToProfile = { navigateTo(Screen.Profile(it)) },
                            onNavigateToThread = { navigateTo(Screen.Thread(it)) },
                        )
                    }
                }
            }
        }
    } // CompositionLocalProvider
}

// ─── Feed content using commons FeedContentState + FeedViewModel ───

@Composable
private fun IosFeedContent(
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    pubKeyHex: String?,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val followedUsers by localCache.followedUsers.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }

    var feedMode by remember { mutableStateOf(FeedMode.GLOBAL) }

    // Subscribe to contact list
    rememberSubscription(allRelayUrls, pubKeyHex, relayManager = relayManager) {
        if (allRelayUrls.isNotEmpty() && pubKeyHex != null) {
            SubscriptionConfig(
                subId = generateSubId("contacts"),
                filters = listOf(FilterBuilders.contactList(pubKeyHex)),
                relays = allRelayUrls,
                onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
            )
        } else {
            null
        }
    }

    // Subscribe to feed events
    rememberSubscription(allRelayUrls, feedMode, followedUsers, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        when (feedMode) {
            FeedMode.GLOBAL -> {
                SubscriptionConfig(
                    subId = generateSubId("global-feed"),
                    filters = listOf(FilterBuilders.textNotesGlobal(limit = 200)),
                    relays = allRelayUrls,
                    onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
                )
            }

            FeedMode.FOLLOWING -> {
                val follows = followedUsers.toList()
                if (follows.isNotEmpty()) {
                    SubscriptionConfig(
                        subId = generateSubId("following-feed"),
                        filters = listOf(FilterBuilders.textNotesFromAuthors(follows, limit = 200)),
                        relays = allRelayUrls,
                        onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
                    )
                } else {
                    null
                }
            }
        }
    }

    // Commons FeedViewModel keyed on feedMode
    val viewModel =
        remember(feedMode) {
            val filter =
                when (feedMode) {
                    FeedMode.GLOBAL -> IosGlobalFeedFilter(localCache)
                    FeedMode.FOLLOWING -> IosFollowingFeedFilter(localCache) { localCache.followedUsers.value }
                }
            IosFeedViewModel(filter, localCache)
        }

    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }

    val feedState by viewModel.feedState.feedContent.collectAsState()

    // Fetch metadata for visible note authors
    val missingPubkeys =
        remember(feedState) {
            if (feedState !is FeedState.Loaded) return@remember emptyList<String>()
            viewModel.feedState
                .visibleNotes()
                .mapNotNull { it.author }
                .filter { it.profilePicture() == null }
                .map { it.pubkeyHex }
                .distinct()
                .take(50)
        }

    rememberSubscription(allRelayUrls, missingPubkeys, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || missingPubkeys.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("fetch-metadata"),
            filters = listOf(FilterBuilders.userMetadataMultiple(missingPubkeys)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FeedHeader(
            title = if (feedMode == FeedMode.GLOBAL) "Global Feed" else "Following",
            connectedRelayCount = connectedRelays.size,
            onRefresh = { relayManager.connect() },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when (val state = feedState) {
            is FeedState.Loading -> {
                if (connectedRelays.isEmpty()) {
                    LoadingState("Connecting to relays...")
                } else {
                    LoadingState("Loading notes...")
                }
            }

            is FeedState.Empty -> {
                EmptyState(
                    title = if (feedMode == FeedMode.FOLLOWING) "No notes from followed users" else "No notes found",
                    onRefresh = { relayManager.connect() },
                )
            }

            is FeedState.FeedError -> {
                EmptyState(
                    title = "Error loading feed",
                    description = state.errorMessage,
                    onRefresh = { relayManager.connect() },
                )
            }

            is FeedState.Loaded -> {
                val loadedState by state.feed.collectAsState()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    items(loadedState.list, key = { it.idHex }) { note ->
                        val event = note.event ?: return@items
                        NoteCard(
                            note = event.toNoteDisplayData(localCache),
                            onClick = { onNavigateToThread(event.id) },
                            onAuthorClick = onNavigateToProfile,
                        )
                    }
                }
            }
        }
    }
}

// ─── Profile content using commons feed filter ───

@Composable
private fun IosProfileContent(
    pubKeyHex: String,
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }
    val connectedRelays by relayManager.connectedRelays.collectAsState()

    // Subscribe to profile metadata + notes
    rememberSubscription(allRelayUrls, pubKeyHex, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("profile-meta"),
            filters = listOf(FilterBuilders.userMetadata(pubKeyHex)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    rememberSubscription(allRelayUrls, pubKeyHex, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("profile-notes"),
            filters = listOf(FilterBuilders.textNotesFromAuthors(listOf(pubKeyHex), limit = 100)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    val viewModel =
        remember(pubKeyHex) {
            IosFeedViewModel(IosProfileFeedFilter(pubKeyHex, localCache), localCache)
        }

    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }

    val feedState by viewModel.feedState.feedContent.collectAsState()
    val user = localCache.getUserIfExists(pubKeyHex)

    Column(modifier = Modifier.fillMaxSize()) {
        // Profile header using commons UserAvatar
        Column(modifier = Modifier.padding(16.dp)) {
            UserAvatar(
                userHex = pubKeyHex,
                pictureUrl = user?.profilePicture(),
                size = 64.dp,
                contentDescription = "Profile",
            )
            val displayName = user?.toBestDisplayName() ?: pubKeyHex.take(16) + "..."
            Text(
                displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Notes feed
        when (val state = feedState) {
            is FeedState.Loading -> {
                LoadingState("Loading profile...")
            }

            is FeedState.Empty -> {
                EmptyState(title = "No notes yet")
            }

            is FeedState.FeedError -> {
                EmptyState(title = "Error", description = state.errorMessage)
            }

            is FeedState.Loaded -> {
                val loadedState by state.feed.collectAsState()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    items(loadedState.list, key = { it.idHex }) { note ->
                        val event = note.event ?: return@items
                        NoteCard(
                            note = event.toNoteDisplayData(localCache),
                            onClick = { onNavigateToThread(event.id) },
                            onAuthorClick = onNavigateToProfile,
                        )
                    }
                }
            }
        }
    }
}

// ─── Thread content using commons feed filter ───

@Composable
private fun IosThreadContent(
    noteId: String,
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }

    // Fetch the thread note + replies
    rememberSubscription(allRelayUrls, noteId, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("thread"),
            filters = listOf(FilterBuilders.byIds(listOf(noteId))),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    val viewModel =
        remember(noteId) {
            IosFeedViewModel(IosThreadFilter(noteId, localCache), localCache)
        }

    DisposableEffect(viewModel) {
        onDispose { viewModel.destroy() }
    }

    val feedState by viewModel.feedState.feedContent.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        FeedHeader(
            title = "Thread",
            connectedRelayCount = 0,
            onRefresh = { },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when (val state = feedState) {
            is FeedState.Loading -> {
                LoadingState("Loading thread...")
            }

            is FeedState.Empty -> {
                EmptyState(title = "Note not found")
            }

            is FeedState.FeedError -> {
                EmptyState(title = "Error", description = state.errorMessage)
            }

            is FeedState.Loaded -> {
                val loadedState by state.feed.collectAsState()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                ) {
                    items(loadedState.list, key = { it.idHex }) { note ->
                        val event = note.event ?: return@items
                        NoteCard(
                            note = event.toNoteDisplayData(localCache),
                            onClick = { onNavigateToThread(event.id) },
                            onAuthorClick = onNavigateToProfile,
                        )
                    }
                }
            }
        }
    }
}
