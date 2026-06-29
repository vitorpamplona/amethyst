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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.GitBrowseState
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.GitStatusIndex
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.code.GitCodeTab
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.code.GitReadmeSection
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.dal.RepositoryIssuesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.dal.RepositoryPatchesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.datasource.RepositoryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

// ---------------------------------------------------------------------------
// Project Home + drill-in screens.
//
// The repository is presented as a scrollable "project home" (facts + README +
// navigation cards) rather than a tab bar. Code, Issues and Pull Requests are
// dedicated screens, each owning its own disappearing top bar — so per-section
// headers (branch/search selectors, status filters) track the bar correctly and
// heavy content (the code browser, the feeds) only loads when navigated to.
// ---------------------------------------------------------------------------

@Composable
fun GitRepositoryScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address, accountViewModel) { note ->
        note?.let {
            GitRepositoryHome(
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun GitRepositoryCodeScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address, accountViewModel) { note ->
        note?.let { GitRepositoryCode(it, accountViewModel, nav) }
    }
}

@Composable
fun GitRepositoryIssuesScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address, accountViewModel) { note ->
        note?.let { GitRepositoryIssues(it, accountViewModel, nav) }
    }
}

@Composable
fun GitRepositoryPullsScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address, accountViewModel) { note ->
        note?.let { GitRepositoryPulls(it, accountViewModel, nav) }
    }
}

/**
 * Builds the [GitRepositoryBrowserViewModel]. The factory lives app-side because the KMP
 * lifecycle artifact used in commons doesn't expose the `create(Class<T>)` override.
 */
internal class GitRepositoryBrowserViewModelFactory(
    private val okHttpClient: (String) -> OkHttpClient,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GitRepositoryBrowserViewModel(okHttpClient) as T
}

@Composable
private fun rememberRepoBrowser(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
): GitRepositoryBrowserViewModel =
    viewModel(
        key = note.idHex + "GitRepoBrowser",
        factory = GitRepositoryBrowserViewModelFactory(accountViewModel.httpClientBuilder::okHttpClientForPreview),
    )

/**
 * Subscribes to the repository's issues/patches/status events while [event] is loaded.
 *
 * RepositoryContentSubAssembler.updateFilter reads note.event and bails out if it isn't a
 * GitRepositoryEvent yet. The compose subscription manager doesn't re-run updateFilter when
 * note.event later mutates, so subscribing before the event arrives (cold-start / deep-link)
 * leaves an empty filter forever. Gating on event presence makes the subscription composable
 * enter composition only once the repo event is loaded.
 */
@Composable
private fun RepoContentSubscription(
    note: AddressableNote,
    event: GitRepositoryEvent?,
    accountViewModel: AccountViewModel,
) {
    if (event != null) {
        RepositoryFilterAssemblerSubscription(note, accountViewModel.dataSources().gitRepository)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitRepositoryHome(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val browserViewModel = rememberRepoBrowser(note, accountViewModel)
    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)

    // Start the smart-HTTP browser as soon as the announcement arrives, so the README renders.
    LaunchedEffect(event) {
        event?.let { browserViewModel.loadOnce(it.clones()) }
    }
    val browserState by browserViewModel.state.collectAsStateWithLifecycle()

    RepoContentSubscription(note, event, accountViewModel)

    // Feed view models power the issue/PR counts on the nav cards and the recent-activity pulse.
    val openIssues: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssuesOpen",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account, showClosed = false),
        )
    val closedIssues: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssuesClosed",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account, showClosed = true),
        )
    val openPatches: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatchesOpen",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account, showClosed = false),
        )
    val closedPatches: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatchesClosed",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account, showClosed = true),
        )
    WatchLifecycleAndUpdateModel(openIssues)
    WatchLifecycleAndUpdateModel(closedIssues)
    WatchLifecycleAndUpdateModel(openPatches)
    WatchLifecycleAndUpdateModel(closedPatches)

    val openIssueItems = rememberGitFeedItems(openIssues)
    val closedIssueItems = rememberGitFeedItems(closedIssues)
    val openPatchItems = rememberGitFeedItems(openPatches)
    val closedPatchItems = rememberGitFeedItems(closedPatches)

    val snapshot = (browserState as? GitBrowseState.Loaded)?.snapshot
    val fileNames = remember(snapshot) { snapshot?.walkFileNames() ?: emptyList() }
    val languageSlices = remember(fileNames) { computeLanguageBreakdown(fileNames) }
    val activity =
        remember(openIssueItems, closedIssueItems, openPatchItems, closedPatchItems) {
            (openIssueItems + closedIssueItems + openPatchItems + closedPatchItems)
                .sortedByDescending { it.createdAt() ?: 0L }
                .take(6)
        }

    // Nav-card badges count only the OPEN issues/PRs. The open/closed split needs the status
    // index (kinds 1630-1633), which is started here so the home reflects it without visiting
    // the Issues screen first; the count is then derived directly from the live index.
    LaunchedEffect(Unit) { GitStatusIndex.startIfNeeded() }
    val statusMap by GitStatusIndex.latestByTarget.collectAsStateWithLifecycle()
    val openIssueCount =
        remember(openIssueItems, closedIssueItems, statusMap) {
            (openIssueItems + closedIssueItems).distinctBy { it.idHex }.count { !GitStatusIndex.isClosedOrResolved(it.idHex, statusMap) }
        }
    val openPullCount =
        remember(openPatchItems, closedPatchItems, statusMap) {
            (openPatchItems + closedPatchItems).distinctBy { it.idHex }.count { !GitStatusIndex.isClosedOrResolved(it.idHex, statusMap) }
        }

    var showSettings by rememberSaveable(note.idHex) { mutableStateOf(false) }
    val currentEventForSettings = event
    if (showSettings && currentEventForSettings != null) {
        GitRepoSettingsDialog(currentEventForSettings, accountViewModel) { showSettings = false }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            ShorterTopAppBar(
                title = { RepoTitleBar(event = event, fallback = note.dTag(), accountViewModel = accountViewModel, nav = nav) },
                navigationIcon = {
                    Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = nav::popBack) { ArrowBackIcon() }
                    }
                },
                actions = {
                    val bookmarkedSet by accountViewModel.account.gitRepositoryListState.publicRepositoryAddressSet
                        .collectAsStateWithLifecycle()
                    val isBookmarked = remember(bookmarkedSet, note) { bookmarkedSet.contains(note.address) }
                    IconButton(onClick = { accountViewModel.toggleRepositoryBookmark(note, isBookmarked) }) {
                        Icon(
                            symbol = if (isBookmarked) MaterialSymbols.Bookmark else MaterialSymbols.BookmarkBorder,
                            contentDescription = stringRes(R.string.git_repo_bookmark),
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (event != null && accountViewModel.isLoggedUser(event?.pubKey)) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(MaterialSymbols.Edit, contentDescription = stringRes(R.string.git_repo_settings_title))
                        }
                    }
                    Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        MoreOptionsButton(note, accountViewModel = accountViewModel, nav = nav)
                    }
                },
            )
        },
        accountViewModel = accountViewModel,
    ) {
        val scaffoldPadding = LocalDisappearingScaffoldPadding.current
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(scaffoldPadding)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val currentEvent = event
            if (currentEvent != null) {
                RepoHero(currentEvent)
                RepoMaintainersRow(currentEvent, accountViewModel, nav)
            }

            if (snapshot != null) {
                RepoStatTiles(
                    branches = snapshot.branches.size,
                    tags = snapshot.tags.size,
                    files = fileNames.size,
                    updatedEpochSec = snapshot.tipCommit?.authorTimeSec,
                )
                if (languageSlices.isNotEmpty()) {
                    RepoLanguageBar(languageSlices)
                }
                snapshot.tipCommit?.let { RepoLastCommit(it) }
            }

            RepoNavCards(note, openIssueCount, openPullCount, nav)

            RepoActivityPulse(activity, accountViewModel, nav)

            if (currentEvent != null) {
                RepoSocialRow(note, accountViewModel, nav)
                GitReadmeSection(browserState, browserViewModel, currentEvent, accountViewModel, nav)
            } else {
                EmptyMessage(stringRes(R.string.loading_feed))
            }
        }
    }
}

@Composable
private fun GitRepositoryCode(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val browserViewModel = rememberRepoBrowser(note, accountViewModel)
    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)
    LaunchedEffect(event) {
        event?.let { browserViewModel.loadOnce(it.clones()) }
    }
    val browserState by browserViewModel.state.collectAsStateWithLifecycle()
    RepoContentSubscription(note, event, accountViewModel)

    GitRepoSubScreenScaffold(event, note.dTag(), accountViewModel, nav) {
        GitCodeTab(browserState, browserViewModel, accountViewModel, nav)
    }
}

@Composable
private fun GitRepositoryIssues(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val openViewModel: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssuesOpen",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account, showClosed = false),
        )
    val closedViewModel: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssuesClosed",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account, showClosed = true),
        )
    WatchLifecycleAndUpdateModel(openViewModel)
    WatchLifecycleAndUpdateModel(closedViewModel)

    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)
    RepoContentSubscription(note, event, accountViewModel)

    GitRepoSubScreenScaffold(event, note.dTag(), accountViewModel, nav) {
        GitIssuesTab(
            note = note,
            event = event,
            openViewModel = openViewModel,
            closedViewModel = closedViewModel,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun GitRepositoryPulls(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val openViewModel: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatchesOpen",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account, showClosed = false),
        )
    val closedViewModel: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatchesClosed",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account, showClosed = true),
        )
    WatchLifecycleAndUpdateModel(openViewModel)
    WatchLifecycleAndUpdateModel(closedViewModel)

    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)
    RepoContentSubscription(note, event, accountViewModel)

    GitRepoSubScreenScaffold(event, note.dTag(), accountViewModel, nav) {
        StatusSplitFeed(
            persistKey = note.idHex + "GitRepoPatchesStatus",
            openViewModel = openViewModel,
            closedViewModel = closedViewModel,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

/** Shared scaffold for the Code / Issues / Pull Requests drill-in screens: a back arrow and the repo title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitRepoSubScreenScaffold(
    event: GitRepositoryEvent?,
    fallbackTitle: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    content: @Composable () -> Unit,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            ShorterTopAppBar(
                title = { TopBarTitle(event = event, fallback = fallbackTitle) },
                navigationIcon = {
                    Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = nav::popBack) { ArrowBackIcon() }
                    }
                },
            )
        },
        accountViewModel = accountViewModel,
    ) {
        content()
    }
}

/** The Code / Issues / Pull Requests entry points on the project home. */
@Composable
private fun RepoNavCards(
    note: AddressableNote,
    openIssues: Int,
    openPulls: Int,
    nav: INav,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RepoNavCard(MaterialSymbols.Code, stringRes(R.string.git_repo_tab_code), null) {
            nav.nav(Route.GitRepositoryCode(note.address))
        }
        RepoNavCard(MaterialSymbols.ErrorOutline, stringRes(R.string.git_repo_tab_issues), openIssues) {
            nav.nav(Route.GitRepositoryIssues(note.address))
        }
        RepoNavCard(MaterialSymbols.CallMerge, stringRes(R.string.git_repo_tab_patches), openPulls) {
            nav.nav(Route.GitRepositoryPulls(note.address))
        }
    }
}

@Composable
private fun RepoNavCard(
    symbol: MaterialSymbol,
    title: String,
    count: Int?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (count != null && count > 0) {
            Text(
                text =
                    if (count > 999) {
                        "999+"
                    } else {
                        count.toString()
                    },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
        )
    }
}

/**
 * The Issues tab: the open/closed status feed plus a "New issue" composer reachable
 * from the filter row once the repository announcement has loaded.
 */
@Composable
private fun GitIssuesTab(
    note: AddressableNote,
    event: GitRepositoryEvent?,
    openViewModel: RepositoryIssuesFeedViewModel,
    closedViewModel: RepositoryIssuesFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var showNewIssue by rememberSaveable(note.idHex) { mutableStateOf(false) }

    StatusSplitFeed(
        persistKey = note.idHex + "GitRepoIssuesStatus",
        openViewModel = openViewModel,
        closedViewModel = closedViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
        headerAction = if (event != null) ({ NewIssueButton { showNewIssue = true } }) else null,
    )

    if (showNewIssue && event != null) {
        GitNewIssueDialog(repoNote = note, accountViewModel = accountViewModel, onDismiss = { showNewIssue = false })
    }
}

@Composable
private fun NewIssueButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringRes(R.string.git_new_issue_button), modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * Wraps a feed in an Open / Closed &amp; Resolved status filter, swapping between two
 * status-scoped feed view models. Each view model already filters by NIP-34 status, so the
 * selector only chooses which one is rendered. The selection survives configuration changes
 * via [persistKey]. An optional [headerAction] (e.g. a "New issue" button) is shown at the
 * trailing edge of the filter row.
 */
@Composable
private fun StatusSplitFeed(
    persistKey: String,
    openViewModel: FeedViewModel,
    closedViewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    headerAction: (@Composable () -> Unit)? = null,
) {
    var showClosed by rememberSaveable(persistKey) { mutableStateOf(false) }
    var selectedLabel by rememberSaveable(persistKey) { mutableStateOf<String?>(null) }

    val openItems = rememberGitFeedItems(openViewModel)
    val closedItems = rememberGitFeedItems(closedViewModel)

    val activeItems = if (showClosed) closedItems else openItems
    val labels =
        remember(activeItems) {
            activeItems.flatMap { gitLabelsOf(it.event) }.distinct().sorted()
        }

    // A label selected under one status may not exist under the other; drop it when it's gone.
    LaunchedEffect(labels) {
        if (selectedLabel != null && selectedLabel !in labels) selectedLabel = null
    }

    // The filter header is drawn statically below the disappearing top bar, so it must
    // consume the scaffold's top inset itself. The inner feed then renders with the top
    // inset zeroed — otherwise its LazyColumn re-applies the full bar height as content
    // padding on top of the header, leaving the empty band reported above the items.
    val scaffoldPadding = LocalDisappearingScaffoldPadding.current
    val layoutDirection = LocalLayoutDirection.current
    val feedPadding =
        remember(scaffoldPadding, layoutDirection) {
            PaddingValues(
                start = scaffoldPadding.calculateStartPadding(layoutDirection),
                top = 0.dp,
                end = scaffoldPadding.calculateEndPadding(layoutDirection),
                bottom = scaffoldPadding.calculateBottomPadding(),
            )
        }

    Column(Modifier.fillMaxSize().padding(top = scaffoldPadding.calculateTopPadding())) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !showClosed,
                onClick = { showClosed = false },
                label = { Text(countedLabel(stringRes(R.string.git_repo_filter_open), openItems.size)) },
                leadingIcon =
                    if (!showClosed) {
                        { Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
            )
            FilterChip(
                selected = showClosed,
                onClick = { showClosed = true },
                label = { Text(countedLabel(stringRes(R.string.git_repo_filter_closed), closedItems.size)) },
                leadingIcon =
                    if (showClosed) {
                        { Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
            )
            if (headerAction != null) {
                Spacer(Modifier.weight(1f))
                headerAction()
            }
        }

        if (labels.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedLabel == null,
                    onClick = { selectedLabel = null },
                    label = { Text(stringRes(R.string.git_repo_label_all)) },
                )
                labels.forEach { label ->
                    FilterChip(
                        selected = selectedLabel == label,
                        onClick = { selectedLabel = if (selectedLabel == label) null else label },
                        label = { Text("#$label") },
                    )
                }
            }
        }

        CompositionLocalProvider(LocalDisappearingScaffoldPadding provides feedPadding) {
            RefresheableFeedView(
                viewModel = if (showClosed) closedViewModel else openViewModel,
                routeForLastRead = null,
                accountViewModel = accountViewModel,
                nav = nav,
                onLoaded = { loaded, listState ->
                    GitItemFeedLoaded(loaded, listState, accountViewModel, nav, labelFilter = selectedLabel)
                },
            )
        }
    }
}

/** Appends a count to a chip label, e.g. "Open · 3". Hidden while the feed is still empty/loading. */
private fun countedLabel(
    base: String,
    count: Int,
): String = if (count > 0) "$base · $count" else base

/**
 * Mirrors the active feed list out of a [FeedViewModel] so the status row can show item counts
 * and derive the available label set. Emits an empty list while the feed is loading or empty.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun rememberGitFeedItems(viewModel: FeedViewModel): List<Note> {
    val flow =
        remember(viewModel) {
            viewModel.feedState.feedContent.flatMapLatest { state ->
                if (state is FeedState.Loaded) state.feed.map { it.list } else flowOf(emptyList())
            }
        }
    val items by flow.collectAsStateWithLifecycle(emptyList())
    return items
}

@Composable
private fun TopBarTitle(
    event: GitRepositoryEvent?,
    fallback: String,
) {
    Text(
        text = event?.name() ?: fallback,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}
