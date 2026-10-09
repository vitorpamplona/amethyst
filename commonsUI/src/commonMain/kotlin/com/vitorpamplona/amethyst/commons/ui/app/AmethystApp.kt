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
package com.vitorpamplona.amethyst.commons.ui.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.account.ui.LoginOrSignupScreen
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.isSameRoute
import com.vitorpamplona.amethyst.commons.model.navigation.lockScope
import com.vitorpamplona.amethyst.commons.privacylock.LocalPrivacyLockState
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose.RelayAuthPromptHost
import com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose.RelayAuthSubscription
import com.vitorpamplona.amethyst.commons.relayClient.event.LocalEventFinder
import com.vitorpamplona.amethyst.commons.relayClient.notifyCommand.compose.DisplayNotifyMessages
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountForegroundFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinder
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinderAccount
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.loading_account
import com.vitorpamplona.amethyst.commons.service.crashreports.DisplayCrashMessages
import com.vitorpamplona.amethyst.commons.ui.actions.mediaServers.DisplayBlossomSyncProgress
import com.vitorpamplona.amethyst.commons.ui.broadcast.DisplayBroadcastProgress
import com.vitorpamplona.amethyst.commons.ui.components.rememberCoarseLocationPermission
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.toasts.DisplayErrorMessages
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.LocalTabReselectCoordinator
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.TabReselectCoordinator
import com.vitorpamplona.amethyst.commons.ui.navigation.deck.DeckArea
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.AccountSwitchBottomSheet
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerContent
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.PermanentDrawerContent
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavTransitionTier
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavigationHost
import com.vitorpamplona.amethyst.commons.ui.navigation.host.sharedDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.rememberNav
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.getRouteWithArguments
import com.vitorpamplona.amethyst.commons.ui.navigation.shell.AppShellLayout
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.privacylock.PrivacyLockHost
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.BottomBarFeedPreloaders
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzDmDiscoveryPreload
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelPreload
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.BuzzDmJoinedChatTailPreload
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupJoinedChatTailPreload
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupJoinedStatePreload
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The whole app below the platform's window: the login screens while logged off, and the logged-in
 * shell (drawer, bars, navigation host and every shared screen) for the current account. [root]
 * supplies what only the front end has; see [AppRoot].
 */
@Composable
fun AmethystApp(
    sessionManager: AccountSessionManager,
    root: AppRoot,
) {
    root.AppEffects()

    val accountState by sessionManager.accountContent.collectAsStateWithLifecycle()

    Log.d("ActivityLifecycle") { "AmethystApp $accountState $sessionManager" }

    PrivacyLockHost(root.privacyLockSettings, root.blurWalletWhenUnfocused) {
        Crossfade(
            targetState = accountState,
            animationSpec = tween(durationMillis = 100),
        ) { state ->
            when (state) {
                is AccountState.Loading -> LoadingSetup()
                is AccountState.LoggedOff -> LoggedOffSetup(sessionManager)
                is AccountState.LoggedIn -> LoggedInSetup(state, sessionManager, root)
            }
        }
    }
}

@Composable
fun LoadingSetup() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringRes(Res.string.loading_account))
        }
    }
}

@Composable
fun LoggedOffSetup(sessionManager: AccountSessionManager) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LoginOrSignupScreen(null, sessionManager, isFirstLogin = true)
    }
}

@Composable
private fun LoggedInSetup(
    state: AccountState.LoggedIn,
    sessionManager: AccountSessionManager,
    root: AppRoot,
) {
    // Consume the one-shot route hint set by sign-in/account-switch so it can't re-fire when the
    // page re-composes after the window is recreated (rotation, dark-mode toggle, background).
    val initialRoute = remember(state) { state.route.also { state.route = null } }
    AccountScopedViewModelStore(state.account.signer.pubKey) {
        LoggedInPage(state.account, initialRoute, sessionManager, root)
    }
}

@Composable
private fun LoggedInPage(
    account: Account,
    route: Route?,
    sessionManager: AccountSessionManager,
    root: AppRoot,
) {
    val accountViewModel: AccountViewModel = rememberViewModel(key = "AccountViewModel") { root.createAccountViewModel(account) }

    LaunchedEffect(Unit) {
        accountViewModel.firstRoute = route
    }

    // Adds this account to the authentication procedures for relays.
    RelayAuthSubscription(accountViewModel)

    // Loads account information + DMs and Notifications from Relays.
    AccountFilterAssemblerSubscription(accountViewModel)

    // Preloads every joined Concord community's Control planes app-wide (their wraps are addressed to
    // derived stream keys, so the always-on DM tail can't pick them up) — so communities fold, and
    // their channels/metadata/icon appear, without waiting for a Concord screen to be opened.
    ConcordChannelPreload(accountViewModel)

    // Keeps joined NIP-29 groups' relay-signed state (metadata/roster/roles/pins) always current, and
    // their recent chat live for Messages-list previews — app-wide, so opening a group lands on cached
    // content. See amethyst/plans/2026-07-18-nip29-group-chat-subscriptions.md.
    RelayGroupJoinedStatePreload(accountViewModel)
    RelayGroupJoinedChatTailPreload(accountViewModel)

    // Discover the viewer's Buzz DM channels (44100 #p=me) across joined workspaces and keep their
    // messages warm app-wide, so a Buzz DM shows on the Notifications tab / in push without opening it.
    BuzzDmDiscoveryPreload(accountViewModel)
    BuzzDmJoinedChatTailPreload(accountViewModel)

    // Foreground-only loaders: follows-outbox finder + random-relay notifications.
    // Pauses on ON_STOP, resumes on ON_START.
    AccountForegroundFilterAssemblerSubscription(accountViewModel)

    // Pre-loads the feed for every icon the user has pinned to the bottom bar.
    // Subscriptions follow the user's chosen list reactively, not the default 5.
    BottomBarFeedPreloaders(accountViewModel)

    // Updates local cache of the anti-spam filter choice of this user.
    ObserveAntiSpamFilterSettings(accountViewModel)

    WatchLocationPermissions()

    root.LoggedInEffects(accountViewModel)

    AppNavigation(accountViewModel, sessionManager, root)
}

@Composable
private fun WatchLocationPermissions() {
    val locationPermissionState = rememberCoarseLocationPermission()

    val locationSource = LocalAppServices.current.deviceLocation
    LaunchedEffect(locationPermissionState.isGranted) {
        locationSource.setLocationPermission(locationPermissionState.isGranted)
    }
}

@Composable
private fun ObserveAntiSpamFilterSettings(accountViewModel: AccountViewModel) {
    val isSpamActive by accountViewModel.account.settings.syncedSettings.security.filterSpamFromStrangers
        .collectAsStateWithLifecycle(true)

    LocalCache.antiSpam.active = isSpamActive
}

@Composable
private fun AppNavigation(
    accountViewModel: AccountViewModel,
    sessionManager: AccountSessionManager,
    root: AppRoot,
) {
    val nav = rememberNav()

    // Shows the "log in to this relay?" dialog when a NIP-42 challenge needs the user to decide.
    // Hosted here rather than per account so one dialog serves the whole shell: challenges
    // arrive off the shared relay socket, not from whatever screen happens to be on top.
    RelayAuthPromptHost(accountViewModel)

    // One layout decision per window size for the whole shell: bottom bar vs rail vs
    // permanent drawer, plus the docked notification panel. Every screen, bar and panel
    // below reads the same spec through LocalScreenLayout. The provider wraps this whole
    // function body so anything added here later is inside it by construction.
    val screenLayout = root.rememberScreenLayoutSpec()
    val tabReselectCoordinator = remember { TabReselectCoordinator() }

    // Mirror the tier for the nav-transition specs, which run outside composition and so
    // can't read LocalScreenLayout (see NavTransitionTier).
    SideEffect { NavTransitionTier.isLargeScreen = screenLayout.isLargeScreen }

    CompositionLocalProvider(
        LocalScreenLayout provides screenLayout,
        LocalTabReselectCoordinator provides tabReselectCoordinator,
        // The finders shared composables reach for through the no-arg observeUser* /
        // EventFinderFilterAssemblerSubscription(note) overloads; they error() when unprovided.
        LocalUserFinder provides accountViewModel.dataSources().userFinder,
        LocalUserFinderAccount provides accountViewModel.account,
        LocalEventFinder provides accountViewModel.dataSources().eventFinder,
    ) {
        AppShellLayout(
            accountViewModel = accountViewModel,
            nav = nav,
            drawerContent = { openAccountSwitcher -> DrawerContent(nav, openAccountSwitcher, accountViewModel) },
            permanentDrawerContent = { openAccountSwitcher -> PermanentDrawerContent(nav, openAccountSwitcher, accountViewModel) },
            accountSwitcherContent = { AccountSwitchBottomSheet(accountViewModel, sessionManager) },
            suspendEdgeSwipe = root::suspendEdgeSwipe,
            deck = rememberDeck(accountViewModel, nav, root),
        ) {
            Box(Modifier.fillMaxSize()) {
                BuildNavigation(accountViewModel, nav, root)
                root.ShellOverlay(accountViewModel)
            }
        }

        OpenFirstRoute(accountViewModel, nav)

        root.NavigationEffects(accountViewModel, nav, sessionManager)

        DisplayErrorMessages(accountViewModel.toastManager, accountViewModel, nav)
        DisplayNotifyMessages(accountViewModel, nav)
        DisplayCrashMessages(accountViewModel, nav)
        DisplayBroadcastProgress(accountViewModel)
        DisplayBlossomSyncProgress()
    }
}

/** Opens the screen a sign-in or account switch asked for (an import-follows step, a linked note), once. */
@Composable
private fun OpenFirstRoute(
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    accountViewModel.firstRoute?.let { newRoute ->
        accountViewModel.firstRoute = null
        val currentRoute = Snapshot.withoutReadObservation { getRouteWithArguments(newRoute::class, nav) }
        if (!isSameRoute(currentRoute, newRoute)) {
            nav.newStack(newRoute)
        }
    }
}

/**
 * The deck's columns when the user turned the deck on and the window is wide enough for the
 * notification panel (about 1,200 dp); null otherwise. Each column gets the same screens as the
 * main navigation, built against the column's own back stack.
 */
@Composable
private fun rememberDeck(
    accountViewModel: AccountViewModel,
    nav: Nav,
    root: AppRoot,
): (@Composable (Modifier) -> Unit)? {
    val deckMode by accountViewModel.settings.uiSettingsFlow.deckMode
        .collectAsState()
    if (!deckMode || !LocalScreenLayout.current.hasRoomForNotificationPanel) return null
    return remember(accountViewModel, nav, root) {
        { modifier ->
            DeckArea(
                accountViewModel = accountViewModel,
                mainNav = nav,
                destinationsFor = { columnNav, column ->
                    NavDestinations().apply {
                        sharedDestinations(accountViewModel, columnNav)
                        root.registerDestinations(this, accountViewModel, column)
                    }
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun BuildNavigation(
    accountViewModel: AccountViewModel,
    nav: Nav,
    root: AppRoot,
) {
    val destinations =
        remember(accountViewModel, nav) {
            NavDestinations().apply {
                sharedDestinations(accountViewModel, nav)
                root.registerDestinations(this, accountViewModel, nav)
            }
        }

    NavigationHost(nav, destinations)

    TrackScreen(nav, destinations, root)
    RelockOnLeave(nav)
}

/**
 * Closes the messages and wallet locks as soon as the screen on top leaves what they guard. Done
 * here, by the route on top, rather than per screen: moving between two guarded screens (the
 * conversation list into a chat) must not lock the user out.
 */
@Composable
private fun RelockOnLeave(nav: Nav) {
    val states = LocalPrivacyLockState.current
    if (states.isEmpty()) return
    LaunchedEffect(nav, states) {
        snapshotFlow { nav.currentRoute.lockScope() }
            .distinctUntilChanged()
            .collect { onTop ->
                states.forEach { (scope, state) ->
                    if (scope != LockScope.App && scope != onTop) state.onLeaveRoute()
                }
            }
    }
}

/** Tells [root] which screen is on top, by its route's name only: never which profile or note. */
@Composable
private fun TrackScreen(
    nav: Nav,
    destinations: NavDestinations,
    root: AppRoot,
) {
    LaunchedEffect(nav, destinations) {
        snapshotFlow { destinations.serialNameOf(nav.currentRoute) }
            .collect { root.onScreen(it) }
    }
    DisposableEffect(nav) {
        onDispose { root.onScreen(null) }
    }
}
