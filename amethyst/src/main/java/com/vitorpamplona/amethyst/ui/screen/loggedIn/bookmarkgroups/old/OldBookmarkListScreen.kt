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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.old

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.ui.components.DeletedItemsBanner
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.old.dal.OldBookmarkPrivateFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.old.dal.OldBookmarkPublicFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import kotlinx.coroutines.launch

@Composable
fun OldBookmarkListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val publicFeedViewModel: OldBookmarkPublicFeedViewModel =
        viewModel(
            key = "NostrOldBookmarkPublicFeedViewModel",
            factory = OldBookmarkPublicFeedViewModel.Factory(accountViewModel.account),
        )

    val privateFeedViewModel: OldBookmarkPrivateFeedViewModel =
        viewModel(
            key = "NostrOldBookmarkPrivateFeedViewModel",
            factory = OldBookmarkPrivateFeedViewModel.Factory(accountViewModel.account),
        )

    val bookmarkState by accountViewModel.account.oldBookmarkState.bookmarks
        .collectAsStateWithLifecycle(null)

    LaunchedEffect(bookmarkState) {
        publicFeedViewModel.invalidateData()
        privateFeedViewModel.invalidateData()
    }

    // Preload all bookmarked events so they don't load one-by-one when scrolling
    PreloadOldBookmarkEvents(bookmarkState, accountViewModel)

    RenderOldBookmarkScreen(publicFeedViewModel, privateFeedViewModel, bookmarkState, accountViewModel, nav)
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RenderOldBookmarkScreen(
    publicFeedViewModel: OldBookmarkPublicFeedViewModel,
    privateFeedViewModel: OldBookmarkPrivateFeedViewModel,
    bookmarkState: com.vitorpamplona.amethyst.commons.model.nip51Lists.OldBookmarkListState.BookmarkList?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val cache = accountViewModel.account.cache
    val deletedEventIds = remember(bookmarkState) { mutableSetOf<String>() }
    val deletedAddresses = remember(bookmarkState) { mutableSetOf<com.vitorpamplona.quartz.nip01Core.core.Address>() }
    val deletedCount =
        remember(bookmarkState) {
            deletedEventIds.clear()
            deletedAddresses.clear()
            val all = bookmarkState?.public.orEmpty() + bookmarkState?.private.orEmpty()
            all.forEach { note ->
                val event = note.event
                if (event != null && cache.hasBeenDeleted(event)) {
                    deletedEventIds.add(note.idHex)
                    if (note is AddressableNote) deletedAddresses.add(note.address)
                }
            }
            deletedEventIds.size + deletedAddresses.size
        }

    var bannerDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(deletedCount) {
        if (deletedCount == 0) bannerDismissed = false
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                TopBarWithBackButton(stringRes(id = R.string.old_bookmarks_title), nav::popBack)
                SecondaryTabRow(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    selectedTabIndex = pagerState.currentPage,
                    modifier = TabRowHeight,
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(text = stringRes(R.string.private_bookmarks)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(text = stringRes(R.string.public_bookmarks)) },
                    )
                }
            }
        },
        floatingButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringRes(R.string.migrate_bookmarks_button)) },
                icon = {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.DriveFileMove,
                        contentDescription = stringRes(R.string.migrate_bookmarks_button),
                    )
                },
                onClick = {
                    accountViewModel.launchSigner {
                        accountViewModel.account.migrateOldBookmarksToNew()
                        coroutineScope.launch {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.migrate_bookmarks_success),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
            )
        },
        accountViewModel = accountViewModel,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxHeight()) { page ->
                when (page) {
                    0 -> {
                        RefresheableFeedView(
                            privateFeedViewModel,
                            null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }

                    1 -> {
                        RefresheableFeedView(
                            publicFeedViewModel,
                            null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }

            if (!bannerDismissed && deletedCount > 0) {
                DeletedItemsBanner(
                    count = deletedCount,
                    onRemove = {
                        accountViewModel.removeDeletedOldBookmarks(
                            deletedEventIds.toSet(),
                            deletedAddresses.toSet(),
                        )
                        bannerDismissed = true
                    },
                    onDismiss = { bannerDismissed = true },
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = paddingValues.calculateTopPadding()),
                )
            }
        }
    }
}

@Composable
private fun PreloadOldBookmarkEvents(
    bookmarkState: com.vitorpamplona.amethyst.commons.model.nip51Lists.OldBookmarkListState.BookmarkList?,
    accountViewModel: AccountViewModel,
) {
    val eventFinder = accountViewModel.dataSources().eventFinder
    val account = accountViewModel.account

    val queries =
        remember(bookmarkState) {
            val allNotes = bookmarkState?.public.orEmpty() + bookmarkState?.private.orEmpty()
            allNotes
                .filter { it.event == null }
                .map { EventFinderQueryState(it, account) }
        }

    DisposableEffect(queries) {
        eventFinder.subscribe(queries)
        onDispose {
            eventFinder.unsubscribe(queries)
        }
    }
}
