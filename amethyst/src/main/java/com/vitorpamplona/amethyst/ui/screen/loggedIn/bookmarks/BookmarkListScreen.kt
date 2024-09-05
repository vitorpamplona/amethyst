/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.NostrBookmarkPrivateFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrBookmarkPublicFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import kotlinx.coroutines.launch

@Composable
fun BookmarkListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val publicFeedViewModel: NostrBookmarkPublicFeedViewModel =
        viewModel(
            key = "NostrBookmarkPublicFeedViewModel",
            factory = NostrBookmarkPublicFeedViewModel.Factory(accountViewModel.account),
        )

    val privateFeedViewModel: NostrBookmarkPrivateFeedViewModel =
        viewModel(
            key = "NostrBookmarkPrivateFeedViewModel",
            factory = NostrBookmarkPrivateFeedViewModel.Factory(accountViewModel.account),
        )

    val userState by accountViewModel.account.decryptBookmarks.observeAsState()

    LaunchedEffect(userState) {
        publicFeedViewModel.invalidateData()
        privateFeedViewModel.invalidateData()
    }

    RenderBookmarkScreen(privateFeedViewModel, accountViewModel, nav, publicFeedViewModel)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RenderBookmarkScreen(
    privateFeedViewModel: NostrBookmarkPrivateFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    publicFeedViewModel: NostrBookmarkPublicFeedViewModel,
) {
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                TopBarWithBackButton(stringRes(id = R.string.bookmarks), nav::popBack)
                TabRow(
                    containerColor = Color.Transparent,
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
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it).fillMaxHeight()) {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 ->
                        RefresheableFeedView(
                            privateFeedViewModel,
                            null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )

                    1 ->
                        RefresheableFeedView(
                            publicFeedViewModel,
                            null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                }
            }
        }
    }
}
