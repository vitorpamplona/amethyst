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
package com.vitorpamplona.amethyst.ui.navigation

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.model.navigation.MediaFeedRoute
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.favoriteIds
import com.vitorpamplona.amethyst.commons.model.navigation.isSameRoute
import com.vitorpamplona.amethyst.commons.model.navigation.limitToRouteTextArg
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.relayClient.event.LocalEventFinder
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinder
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinderAccount
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.invalid_nip19_uri
import com.vitorpamplona.amethyst.commons.resources.invalid_nip19_uri_description
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.LocalTabReselectCoordinator
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.TabReselectCoordinator
import com.vitorpamplona.amethyst.commons.ui.navigation.findQueryParameterValue
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavTransitionTier
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavigationHost
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableFromBottomArgs
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableFromEnd
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableFromEndArgs
import com.vitorpamplona.amethyst.commons.ui.navigation.host.sharedDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.rememberNav
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.consumesSharesInPlace
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.getRouteWithArguments
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.isBaseRoute
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.NowPlayingSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.uriToRoute
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.crashreports.DisplayCrashMessages
import com.vitorpamplona.amethyst.service.nowPlaying.AndroidAppIcon
import com.vitorpamplona.amethyst.service.nowPlaying.AndroidNowPlayingAccess
import com.vitorpamplona.amethyst.service.relayClient.authCommand.compose.RelayAuthPromptHost
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.compose.DisplayNotifyMessages
import com.vitorpamplona.amethyst.service.resourceusage.DisplayResourceUsageAlert
import com.vitorpamplona.amethyst.service.resourceusage.ScreenTimeIntegrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DisplayBlossomSyncProgress
import com.vitorpamplona.amethyst.ui.broadcast.DisplayBroadcastProgress
import com.vitorpamplona.amethyst.ui.call.CallActivity
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.components.toasts.DisplayErrorMessages
import com.vitorpamplona.amethyst.ui.layouts.rememberScreenLayoutSpec
import com.vitorpamplona.amethyst.ui.note.UpdateReactionTypeScreen
import com.vitorpamplona.amethyst.ui.note.share.ShareNoteAsImageFileScreen
import com.vitorpamplona.amethyst.ui.note.share.ShareNoteAsImageScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountSwitcherAndLeftDrawerLayout
import com.vitorpamplona.amethyst.ui.screen.loggedIn.browser.WebAppScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.create.NewCalendarEventScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.cordnGroup.CordnGroupChatScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabAccountWatcher
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabLayer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabPreloader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabThemeWatcher
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.FavoriteAppManifestPreloader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.NostrAppScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitNewIssueScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitRepositoryCodeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitRepositoryIssuesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitRepositoryPullsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitRepositoryScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.keyBackup.AccountBackupScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.AddToMusicPlaylistSheet
import com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.ConnectedAppDetailScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.NestsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.ScanQrImageScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.ResourceUsageScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.cordn.CordnBackupScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls.NewHlsVideoScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.fitness.MyFitnessScreen
import com.vitorpamplona.amethyst.ui.screen.loggedOff.AddAccountDialog
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip84Highlights.parse.SharedHighlightParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI

@Composable
fun AppNavigation(
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
) {
    val nav = rememberNav()

    // Shows the "log in to this relay?" dialog when a NIP-42 challenge needs the user to decide.
    // Hosted here rather than in LoggedInPage so one dialog serves the whole shell: challenges
    // arrive off the shared relay socket, not from whatever screen happens to be on top.
    RelayAuthPromptHost(accountViewModel)

    // One layout decision per window size for the whole shell: bottom bar vs rail vs
    // permanent drawer, plus the docked notification panel. Every screen, bar and panel
    // below reads the same spec through LocalScreenLayout. The provider wraps this whole
    // function body so anything added to AppNavigation later is inside it by construction.
    val screenLayout = rememberScreenLayoutSpec()
    val tabReselectCoordinator = remember { TabReselectCoordinator() }

    // Mirror the tier for the nav-transition specs, which run outside composition and so
    // can't read LocalScreenLayout (see NavTransitionTier).
    SideEffect { NavTransitionTier.isLargeScreen = screenLayout.isLargeScreen }

    CompositionLocalProvider(
        LocalScreenLayout provides screenLayout,
        LocalTabReselectCoordinator provides tabReselectCoordinator,
        // Provide the shared finder CompositionLocals so any commons composable that
        // uses the no-arg observeUser*/EventFinderFilterAssemblerSubscription(note)
        // overloads works when rendered on Android (they error() if unprovided). Android's
        // own UI uses the AccountViewModel overloads and doesn't strictly need these, but
        // providing them removes the runtime trap for shared composables reaching the
        // logged-in tree. (The :napplet process never renders these composables.)
        LocalUserFinder provides accountViewModel.dataSources().userFinder,
        LocalUserFinderAccount provides accountViewModel.account,
        LocalEventFinder provides accountViewModel.dataSources().eventFinder,
    ) {
        AccountSwitcherAndLeftDrawerLayout(accountViewModel, accountSessionManager, nav) {
            Box(Modifier.fillMaxSize()) {
                BuildNavigation(accountViewModel, nav)
                // Pull each pinned nsite/napplet's manifest into LocalCache (and keep a device-local copy)
                // so its favorite resolves as reliably as a pinned web app's URL — the data the embedded
                // preloader below and the full-screen launcher both need. Not API-gated: every device's
                // launcher benefits, and it's the only preload step that runs below API 30.
                FavoriteAppManifestPreloader(accountViewModel)
                // Persistent layer that keeps pinned embedded tabs (browser / nsite / napplet) warm by
                // holding their surfaces attached. Below the drawer (drawn by the layout above) and below
                // dialogs (separate windows). API 30+ only, matching the embedded-surface feature.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bottomBarItems by accountViewModel.account.settings.syncedSettings.navigation.bottomBarItems
                        .collectAsStateWithLifecycle()
                    // Move every embedded app to the new account on a switch. Mounted before the layer and
                    // the preloader so the previous account's sessions are dropped ahead of the first sweep
                    // (an embed WebView's storage profile is fixed at construction, so it must be rebuilt).
                    EmbeddedTabAccountWatcher()
                    EmbeddedTabLayer(bottomBarItems.favoriteIds())
                    // Warm every pinned tab at startup so the first tap is instant (content already local).
                    EmbeddedTabPreloader(accountViewModel)
                    // Rebuild the warm surfaces in the new theme when the app's DARK/LIGHT preference flips
                    // (an embed WebView's theme is fixed at construction, so it can't follow a live switch).
                    EmbeddedTabThemeWatcher()
                }
            }
        }

        NavigateIfIntentRequested(nav, accountViewModel, accountSessionManager)

        DisplayErrorMessages(accountViewModel.toastManager, accountViewModel, nav)
        DisplayNotifyMessages(accountViewModel, nav)
        DisplayCrashMessages(accountViewModel, nav)
        DisplayResourceUsageAlert(accountViewModel, nav)
        DisplayBroadcastProgress(accountViewModel)
        DisplayBlossomSyncProgress()

        ObserveIncomingCalls(accountViewModel)
    }
}

@Composable
private fun ObserveIncomingCalls(accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val callState by accountViewModel.callManager.state.collectAsState()

    LaunchedEffect(callState) {
        val state = callState
        if (state is CallState.IncomingCall || state is CallState.Offering) {
            CallActivity.launch(context)
        }
    }
}

/**
 * Feeds the resource-usage ledger with time-per-screen. Only the route's
 * base name crosses this boundary — [ScreenTimeIntegrator.screenNameOf]
 * strips every navigation argument first, so the ledger can say "Profile"
 * but never which profile.
 */
@Composable
private fun TrackScreenTime(
    nav: Nav,
    destinations: NavDestinations,
) {
    LaunchedEffect(nav, destinations) {
        snapshotFlow { ScreenTimeIntegrator.screenNameOf(destinations.serialNameOf(nav.currentRoute)) }
            .collect { Amethyst.instance.screenTime.onScreen(it) }
    }
    DisposableEffect(nav) {
        onDispose { Amethyst.instance.screenTime.onScreen(null) }
    }
}

@Composable
fun BuildNavigation(
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val destinations = remember(accountViewModel, nav) { NavDestinations().apply { appDestinations(accountViewModel, nav) } }

    NavigationHost(nav, destinations)

    TrackScreenTime(nav, destinations)
}

private fun NavDestinations.appDestinations(
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    sharedDestinations(accountViewModel, nav)

    composableFromEnd<Route.MyFitness> { MyFitnessScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.WebApp>(capWidth = false) { WebAppScreen(it.url, accountViewModel, nav) }
    composableFromEndArgs<Route.NostrApp>(capWidth = false) { NostrAppScreen(it.coordinate, accountViewModel, nav) }
    composableFromEndArgs<Route.ConnectedAppDetail> { ConnectedAppDetailScreen(it.coordinate, accountViewModel, nav) }
    composableFromBottomArgs<Route.NewCalendarEvent> { NewCalendarEventScreen(nav, accountViewModel) }
    composableFromBottomArgs<Route.EditCalendarEvent> {
        NewCalendarEventScreen(nav, accountViewModel, editKind = it.kind, editPubKeyHex = it.pubKeyHex, editDTag = it.dTag)
    }
    composableFromEnd<Route.Nests> { NestsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.AddToMusicPlaylist> { AddToMusicPlaylistSheet(trackAddress = it.trackAddress, accountViewModel = accountViewModel, nav = nav) }
    composableFromEnd<Route.NewHlsVideo> { NewHlsVideoScreen(accountViewModel, nav) }
    composableFromBottomArgs<Route.ScanQrImage> { ScanQrImageScreen(it.uri, accountViewModel, nav) }
    composableFromEnd<Route.AccountBackup> { AccountBackupScreen(accountViewModel, nav) }
    composableFromEnd<Route.NowPlayingSettings> {
        val context = LocalContext.current
        val access = remember(context) { AndroidNowPlayingAccess(context.applicationContext) }
        NowPlayingSettingsScreen(accountViewModel, nav, access) { appId, label -> AndroidAppIcon(appId, label) }
    }
    composableFromEnd<Route.ResourceUsage> { ResourceUsageScreen(accountViewModel, nav) }
    composableFromEnd<Route.CordnBackup> { CordnBackupScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.UpdateReactionType> { UpdateReactionTypeScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.ShareNoteAsImage> { ShareNoteAsImageScreen(it.id, accountViewModel, nav) }
    composableFromEndArgs<Route.ShareNoteAsImageFile> { ShareNoteAsImageFileScreen(it.id, accountViewModel, nav) }
    composableFromEndArgs<Route.GitRepository> { GitRepositoryScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.GitRepositoryCode> { GitRepositoryCodeScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.GitRepositoryIssues> { GitRepositoryIssuesScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.GitRepositoryPulls> { GitRepositoryPullsScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.GitRepositoryNewIssue> { GitNewIssueScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.CordnGroupChat> {
        CordnGroupChatScreen(it.coordinatorPubKey, it.gid, accountViewModel, nav)
    }
}

/** True for both share flavors: a single file/text (SEND) and a multi-file selection (SEND_MULTIPLE). */
private fun Intent.isShareAction(): Boolean = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE

/**
 * The text Android sent with the share — a caption, a URL, or the whole payload of a text-only
 * share. Capped on the way in: a share carries whatever the other app put in it (Binder allows
 * hundreds of kilobytes), and every share target below hands this straight to a route argument,
 * where an oversized value makes the destination unmatchable. See [limitToRouteTextArg].
 */
private fun Intent.sharedText(): String? = getStringExtra(Intent.EXTRA_TEXT)?.ifBlank { null }?.limitToRouteTextArg()

/**
 * Every content URI the share carries, in the order the sending app listed them. SEND_MULTIPLE
 * puts them in a parcelable ArrayList; plain SEND has at most one. Only the media targets declare
 * SEND_MULTIPLE, so every other caller can just take the first.
 */
private fun Intent.sharedStreamUris(): List<String> =
    if (action == Intent.ACTION_SEND_MULTIPLE) {
        IntentCompat
            .getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
            ?.map { it.toString() }
            .orEmpty()
    } else {
        listOfNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.toString())
    }

/**
 * Opens a media feed on a share. Sharing again while an earlier share is still on screen replaces
 * it instead of stacking a second copy of the feed — but only when the entry on top is itself a
 * share (it carries attachments). A feed the user opened from the bottom bar carries none, so it
 * stays put underneath and keeps its tab-root marker.
 */
private inline fun <reified T> Nav.navToSharedFeed(route: T) where T : Route, T : MediaFeedRoute {
    val current = getRouteWithArguments(T::class, this)
    if (current is MediaFeedRoute && current.attachments.isNotEmpty()) {
        popUpTo(route, T::class)
    } else {
        newStack(route)
    }
}

@Composable
private fun NavigateIfIntentRequested(
    nav: Nav,
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
) {
    accountViewModel.firstRoute?.let { newRoute ->
        accountViewModel.firstRoute = null
        val currentRoute = Snapshot.withoutReadObservation { getRouteWithArguments(newRoute::class, nav) }
        if (!isSameRoute(currentRoute, newRoute)) {
            nav.newStack(newRoute)
        }
    }

    val activity = LocalContext.current.getActivity()

    if (activity.intent.isShareAction()) {
        val target = ShareIntentRouting.targetOf(activity.intent.component?.className)

        // avoids restarting the destination screen when the intent is for the screen.
        // Microsoft's swift key sends Gifs as new actions.
        // The media targets land on a feed the user may well be standing on already, so they can't
        // guard on the destination — they rely on the intent being consumed below instead.
        //
        // Read without observing: the current route is snapshot state, and subscribing to it here
        // would re-run this whole block on every navigation, which is exactly when the guard stops
        // guarding (the user has left the composer).
        val alreadyOnDestination =
            Snapshot.withoutReadObservation {
                when (target) {
                    ShareTarget.HIGHLIGHT -> isBaseRoute<Route.NewHighlight>(nav)
                    ShareTarget.DIRECT_MESSAGE -> isBaseRoute<Route.ShareToDM>(nav)
                    ShareTarget.NEW_POST -> isBaseRoute<Route.NewShortNote>(nav)
                    ShareTarget.PICTURE, ShareTarget.SHORT_VIDEO, ShareTarget.VIDEO -> false
                    // Always re-runs: the route carries the image, so a second share of a different
                    // picture must decode that one rather than sit on the previous result.
                    ShareTarget.SCAN_QR -> false
                }
            }
        if (alreadyOnDestination) {
            // The composer this share opened is already up (the activity was restored with the
            // share still in its intent). Consume the intent, so a later pass can't reopen the
            // composer once the user leaves it, and so the listener below takes over.
            activity.intent.action = null
            return
        }

        // saves the intent to avoid processing again
        val message = remember { activity.intent.sharedText() }
        val attachments = remember { activity.intent.sharedStreamUris() }

        when (target) {
            ShareTarget.HIGHLIGHT -> {
                val parsed = message?.let { SharedHighlightParser.parse(it) }
                nav.newStack(
                    Route.NewHighlight(
                        quote = parsed?.quote,
                        url = parsed?.url,
                        prefix = parsed?.prefix,
                        suffix = parsed?.suffix,
                    ),
                )
            }
            // The single-file composers take the first URI: only the media targets declare
            // SEND_MULTIPLE, so anything else can only be carrying one.
            ShareTarget.DIRECT_MESSAGE -> nav.newStack(Route.ShareToDM(message = message, attachment = attachments.firstOrNull()))
            ShareTarget.PICTURE -> nav.navToSharedFeed(Route.Pictures(attachments = attachments, message = message))
            ShareTarget.SHORT_VIDEO -> nav.navToSharedFeed(Route.Shorts(attachments = attachments, message = message))
            ShareTarget.VIDEO -> nav.navToSharedFeed(Route.Video(attachments = attachments, message = message))
            ShareTarget.NEW_POST -> nav.newStack(Route.NewShortNote(message = message, attachment = attachments.firstOrNull()))
            ShareTarget.SCAN_QR ->
                attachments.firstOrNull()?.let { nav.newStack(Route.ScanQrImage(it.toString())) }
        }

        // Consume the launch intent so a later recomposition can't re-fire
        // newStack for the same share (the isBaseRoute guard is a non-reactive
        // snapshot and stops guarding once we navigate past the destination,
        // e.g. into a chat via the one-shot picker). Clearing the action also
        // lets the else-branch register the onNewIntent listener for the rest
        // of this session.
        activity.intent.action = null
    } else {
        var newAccount by remember { mutableStateOf<String?>(null) }

        var currentIntentNextPage by remember {
            mutableStateOf(
                activity.intent
                    ?.data
                    ?.toString()
                    ?.ifBlank { null },
            )
        }

        currentIntentNextPage?.let { intentNextPage ->
            var actionableNextPage by remember {
                mutableStateOf(uriToRoute(intentNextPage, accountViewModel.account))
            }

            LaunchedEffect(intentNextPage) {
                if (actionableNextPage != null) {
                    actionableNextPage?.let { nextRoute ->
                        val npub = intentNextPage.findQueryParameterValue("account")
                        if (npub != null && accountSessionManager.currentAccountNPub() != npub) {
                            accountSessionManager.checkAndSwitchUserSync(npub) { account ->
                                uriToRoute(intentNextPage, account)
                            }
                        } else {
                            val currentRoute = getRouteWithArguments(nextRoute::class, nav)
                            if (!isSameRoute(currentRoute, nextRoute)) {
                                nav.newStack(nextRoute)
                            }
                            actionableNextPage = null
                        }
                    }
                } else if (intentNextPage.contains("ncryptsec1")) {
                    // login functions
                    Nip19Parser.tryParseAndClean(intentNextPage)?.let {
                        newAccount = it
                    }

                    actionableNextPage = null
                } else {
                    accountViewModel.toastManager.toast(
                        Res.string.invalid_nip19_uri,
                        Res.string.invalid_nip19_uri_description,
                        intentNextPage,
                    )
                }

                currentIntentNextPage = null
            }
        }

        val scope = rememberCoroutineScope()

        DisposableEffect(nav, activity) {
            val consumer =
                Consumer<Intent> { intent ->
                    if (intent.isShareAction()) {
                        val target = ShareIntentRouting.targetOf(intent.component?.className)
                        val message = intent.sharedText()
                        val attachments = intent.sharedStreamUris()
                        val attachment = attachments.firstOrNull()

                        // avoids restarting the destination screen when the intent is for the screen.
                        // Microsoft's swift key sends Gifs as new actions.
                        // The media targets land on a feed the user may well be standing on already, so
                        // they always navigate: the route carries the attachment, so the composer opens
                        // on the newly shared file even when the feed itself is already on screen.
                        when (target) {
                            ShareTarget.HIGHLIGHT ->
                                if (!isBaseRoute<Route.NewHighlight>(nav)) {
                                    val parsed = message?.let { SharedHighlightParser.parse(it) }
                                    nav.newStack(
                                        Route.NewHighlight(
                                            quote = parsed?.quote,
                                            url = parsed?.url,
                                            prefix = parsed?.prefix,
                                            suffix = parsed?.suffix,
                                        ),
                                    )
                                }

                            ShareTarget.DIRECT_MESSAGE ->
                                if (!isBaseRoute<Route.ShareToDM>(nav)) {
                                    nav.newStack(Route.ShareToDM(message = message, attachment = attachment))
                                }

                            ShareTarget.PICTURE -> nav.navToSharedFeed(Route.Pictures(attachments = attachments, message = message))
                            ShareTarget.SHORT_VIDEO -> nav.navToSharedFeed(Route.Shorts(attachments = attachments, message = message))
                            ShareTarget.VIDEO -> nav.navToSharedFeed(Route.Video(attachments = attachments, message = message))

                            ShareTarget.NEW_POST ->
                                if (!consumesSharesInPlace(nav) && (message != null || attachment != null)) {
                                    nav.newStack(Route.NewShortNote(message = message, attachment = attachment))
                                }

                            ShareTarget.SCAN_QR -> attachment?.let { nav.newStack(Route.ScanQrImage(it.toString())) }
                        }
                    } else {
                        val uri = intent.data?.toString()

                        if (!uri.isNullOrBlank()) {
                            // navigation functions
                            val newPage = uriToRoute(uri, accountViewModel.account)

                            if (newPage != null) {
                                scope.launch {
                                    val npub = uri.findQueryParameterValue("account")
                                    if (npub != null && accountSessionManager.currentAccountNPub() != npub) {
                                        accountSessionManager.checkAndSwitchUserSync(npub) { newAccount ->
                                            uriToRoute(uri, newAccount)
                                        }
                                    } else {
                                        val currentRoute = getRouteWithArguments(newPage::class, nav)
                                        if (!isSameRoute(currentRoute, newPage)) {
                                            nav.newStack(newPage)
                                        }
                                    }
                                }
                            } else if (uri.contains("ncryptsec")) {
                                // login functions
                                Nip19Parser.tryParseAndClean(uri)?.let {
                                    newAccount = it
                                }
                            } else {
                                scope.launch {
                                    delay(1000)
                                    accountViewModel.toastManager.toast(
                                        Res.string.invalid_nip19_uri,
                                        Res.string.invalid_nip19_uri_description,
                                        uri,
                                    )
                                }
                            }
                        }
                    }
                }
            activity.addOnNewIntentListener(consumer)
            onDispose { activity.removeOnNewIntentListener(consumer) }
        }

        if (newAccount != null) {
            AddAccountDialog(newAccount, accountSessionManager) { newAccount = null }
        }
    }
}

fun URI.findParameterValue(parameterName: String): String? =
    rawQuery
        ?.split('&')
        ?.map {
            val parts = it.split('=')
            val name = parts.firstOrNull() ?: ""
            val value = parts.drop(1).firstOrNull() ?: ""
            Pair(name, value)
        }?.firstOrNull { it.first == parameterName }
        ?.second
