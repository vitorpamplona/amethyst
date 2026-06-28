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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.code.GitCodeTab
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.code.GitReadmeTab
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.dal.RepositoryIssuesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.dal.RepositoryPatchesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.datasource.RepositoryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun GitRepositoryScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address, accountViewModel) { note ->
        note?.let {
            PrepareGitRepositoryScreen(
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun PrepareGitRepositoryScreen(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val openIssuesViewModel: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssuesOpen",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account, showClosed = false),
        )

    val closedIssuesViewModel: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssuesClosed",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account, showClosed = true),
        )

    val openPatchesViewModel: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatchesOpen",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account, showClosed = false),
        )

    val closedPatchesViewModel: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatchesClosed",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account, showClosed = true),
        )

    val browserViewModel: GitRepositoryBrowserViewModel =
        viewModel(
            key = note.idHex + "GitRepoBrowser",
            factory = GitRepositoryBrowserViewModel.Factory(accountViewModel.httpClientBuilder::okHttpClientForPreview),
        )

    GitRepositoryScreen(
        note = note,
        openIssuesViewModel = openIssuesViewModel,
        closedIssuesViewModel = closedIssuesViewModel,
        openPatchesViewModel = openPatchesViewModel,
        closedPatchesViewModel = closedPatchesViewModel,
        browserViewModel = browserViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitRepositoryScreen(
    note: AddressableNote,
    openIssuesViewModel: RepositoryIssuesFeedViewModel,
    closedIssuesViewModel: RepositoryIssuesFeedViewModel,
    openPatchesViewModel: RepositoryPatchesFeedViewModel,
    closedPatchesViewModel: RepositoryPatchesFeedViewModel,
    browserViewModel: GitRepositoryBrowserViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(openIssuesViewModel)
    WatchLifecycleAndUpdateModel(closedIssuesViewModel)
    WatchLifecycleAndUpdateModel(openPatchesViewModel)
    WatchLifecycleAndUpdateModel(closedPatchesViewModel)

    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)

    // Start the smart-HTTP browser as soon as the repository announcement arrives,
    // so the README and Code tabs can fetch from its clone URL.
    LaunchedEffect(event) {
        event?.let { browserViewModel.loadOnce(it.clones()) }
    }
    val browserState by browserViewModel.state.collectAsStateWithLifecycle()

    // RepositoryContentSubAssembler.updateFilter reads note.event and bails out if it isn't
    // a GitRepositoryEvent yet. The compose subscription manager doesn't re-run updateFilter
    // when note.event later mutates, so subscribing before the event has arrived (cold-start
    // / deep-link case) leaves an empty filter forever. Gate the subscription composable on
    // event presence so it enters composition (and fires DisposableEffect → subscribe → fresh
    // updateFilter) only once the repo event is loaded.
    if (event != null) {
        RepositoryFilterAssemblerSubscription(note, accountViewModel.dataSources().gitRepository)
    }

    val pagerState = rememberForeverPagerState(note.idHex + "GitRepoScreenPagerState") { 5 }
    var showSettings by rememberSaveable(note.idHex) { mutableStateOf(false) }

    val currentEventForSettings = event
    if (showSettings && currentEventForSettings != null) {
        GitRepoSettingsDialog(currentEventForSettings, accountViewModel) { showSettings = false }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                ShorterTopAppBar(
                    title = {
                        TopBarTitle(event = event, fallback = note.dTag())
                    },
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
                    },
                )

                SecondaryTabRow(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = TabRowHeight,
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    Tab(
                        selected = pagerState.currentPage == 0,
                        text = { Text(stringRes(R.string.git_repo_tab_readme)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        text = { Text(stringRes(R.string.git_repo_tab_code)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        text = { Text(stringRes(R.string.git_repo_tab_overview)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    )
                    Tab(
                        selected = pagerState.currentPage == 3,
                        text = { Text(stringRes(R.string.git_repo_tab_issues)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                    )
                    Tab(
                        selected = pagerState.currentPage == 4,
                        text = { Text(stringRes(R.string.git_repo_tab_patches)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(4) } },
                    )
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> {
                    val currentEvent = event
                    if (currentEvent != null) {
                        GitReadmeTab(browserState, browserViewModel, currentEvent, accountViewModel, nav)
                    } else {
                        EmptyMessage(stringRes(R.string.loading_feed))
                    }
                }

                1 -> {
                    GitCodeTab(browserState, browserViewModel, accountViewModel, nav)
                }

                2 -> {
                    val currentEvent = event
                    if (currentEvent != null) {
                        GitRepositoryOverview(currentEvent, accountViewModel, nav)
                    } else {
                        EmptyMessage(stringRes(R.string.loading_feed))
                    }
                }

                3 -> {
                    GitIssuesTab(
                        note = note,
                        event = event,
                        openViewModel = openIssuesViewModel,
                        closedViewModel = closedIssuesViewModel,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                4 -> {
                    StatusSplitFeed(
                        persistKey = note.idHex + "GitRepoPatchesStatus",
                        openViewModel = openPatchesViewModel,
                        closedViewModel = closedPatchesViewModel,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
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
 * and tab swipes via [persistKey]. An optional [headerAction] (e.g. a "New issue" button)
 * is shown at the trailing edge of the filter row.
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

    Column(Modifier.fillMaxSize()) {
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
                .fillMaxSize()
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
