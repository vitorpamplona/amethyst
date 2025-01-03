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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.NostrUserFollowSetFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ListsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val followSetsViewModel: NostrUserFollowSetFeedViewModel =
        viewModel(
            key = "NostrUserFollowSetFeedViewModel",
            factory = NostrUserFollowSetFeedViewModel.Factory(accountViewModel.account),
        )

    val followSetsFlow by followSetsViewModel.account.followSetNotesFlow().collectAsState()
    LaunchedEffect(followSetsFlow) {
        followSetsViewModel.invalidateData()
    }

    CustomListsScreen(
        followSetsViewModel,
        accountViewModel,
        nav,
    )
}

@Composable
fun CustomListsScreen(
    followSetsViewModel: NostrUserFollowSetFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val lifeCycleOwner = LocalLifecycleOwner.current
    val setsState by followSetsViewModel.feedContent.collectAsStateWithLifecycle()

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Custom Lists Start")
                    followSetsViewModel.invalidateData()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        accountViewModel = accountViewModel,
        topBar = {
            TopBarWithBackButton(stringRes(R.string.my_lists), nav::popBack)
        },
    ) {
        Column(Modifier.padding(it).fillMaxHeight()) {
            when (setsState) {
                FollowSetState.Empty ->
                    FeedEmpty {
                        followSetsViewModel.invalidateData()
                    }
                is FollowSetState.FeedError ->
                    FeedError(
                        (setsState as FollowSetState.FeedError).errorMessage,
                    ) {
                        followSetsViewModel.invalidateData()
                    }
                is FollowSetState.Loaded -> {
                    val followSetFeed by (setsState as FollowSetState.Loaded).feed
                    FollowListLoaded(
                        loadedFeedState = followSetFeed,
                    )
                }
                FollowSetState.Loading -> LoadingFeed()
            }
        }
    }
}

@Composable
fun FollowListLoaded(
    modifier: Modifier = Modifier,
    loadedFeedState: ImmutableList<FollowSet>,
) {
    val listState = rememberLazyListState()
    Log.d("FollowSetComposable", "FollowListLoaded: Follow Set size: ${loadedFeedState.size}")
    LazyColumn(
        state = listState,
        contentPadding = FeedPadding,
    ) {
        itemsIndexed(loadedFeedState, key = { _, item -> item.title }) { index, set ->
            CustomListItem(followSet = set)
        }
    }
}

@Composable
fun CustomListItem(
    modifier: Modifier = Modifier,
    followSet: FollowSet,
) {
    Row(
        modifier =
            modifier
                .border(
                    width = Dp.Hairline,
                    color = Color.Gray,
                    shape = RoundedCornerShape(percent = 20),
                ).padding(all = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(followSet.title, fontWeight = FontWeight.Bold)
            Spacer(modifier = StdVertSpacer)
            Text(
                followSet.description ?: "No description for this list.",
                overflow = TextOverflow.Ellipsis,
                maxLines = 3,
            )
        }

        followSet.isPrivate.let {
            val text by derivedStateOf { if (it) "Private" else "Public" }
            Column(
                // modifier = modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (followSet.isPrivate) R.drawable.incognito else R.drawable.ic_public,
                        ),
                    contentDescription = "Icon for $text List",
                )
                Text(text, color = Color.Gray)
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun ListItemPreview() {
    val sampleFollowSet =
        FollowSet(
            isPrivate = true,
            title = "Sample List Title",
            description = "Sample List Description",
            emptySet(),
        )
    ThemeComparisonColumn {
        CustomListItem(
            modifier = Modifier,
            sampleFollowSet,
        )
    }
}
