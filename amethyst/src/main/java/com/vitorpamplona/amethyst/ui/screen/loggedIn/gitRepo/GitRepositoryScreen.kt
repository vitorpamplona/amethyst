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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.dal.RepositoryIssuesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.dal.RepositoryPatchesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.datasource.RepositoryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
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
    val issuesViewModel: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssues",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account),
        )

    val patchesViewModel: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatches",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account),
        )

    GitRepositoryScreen(
        note = note,
        issuesViewModel = issuesViewModel,
        patchesViewModel = patchesViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitRepositoryScreen(
    note: AddressableNote,
    issuesViewModel: RepositoryIssuesFeedViewModel,
    patchesViewModel: RepositoryPatchesFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(issuesViewModel)
    WatchLifecycleAndUpdateModel(patchesViewModel)
    RepositoryFilterAssemblerSubscription(note, accountViewModel.dataSources().gitRepository)

    val pagerState = rememberForeverPagerState(note.idHex + "GitRepoScreenPagerState") { 3 }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                ShorterTopAppBar(
                    title = {
                        TopBarTitle(note)
                    },
                    navigationIcon = {
                        Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = nav::popBack) { ArrowBackIcon() }
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
                        text = { Text(stringRes(R.string.git_repo_tab_overview)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        text = { Text(stringRes(R.string.git_repo_tab_issues)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        text = { Text(stringRes(R.string.git_repo_tab_patches)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
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
                    val event = note.event as? GitRepositoryEvent
                    if (event != null) {
                        GitRepositoryOverview(event, accountViewModel, nav)
                    } else {
                        EmptyMessage(stringRes(R.string.loading_feed))
                    }
                }

                1 -> {
                    RefresheableFeedView(
                        viewModel = issuesViewModel,
                        routeForLastRead = null,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                2 -> {
                    RefresheableFeedView(
                        viewModel = patchesViewModel,
                        routeForLastRead = null,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBarTitle(note: AddressableNote) {
    val event = note.event as? GitRepositoryEvent
    val title = event?.name() ?: note.dTag()
    Text(
        text = title,
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
