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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.adaptive.FoldAwareConfiguration
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.ui.buttons.ChannelFabColumn
import com.vitorpamplona.amethyst.ui.screen.ChatroomListFeedView
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListKnownFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListNewFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ChatroomListScreen(
    knownFeedViewModel: NostrChatroomListKnownFeedViewModel,
    newFeedViewModel: NostrChatroomListNewFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val windowSizeClass by accountViewModel.settings.windowSizeClass

    val twoPane by remember {
        derivedStateOf {
            when (windowSizeClass?.widthSizeClass) {
                WindowWidthSizeClass.Compact -> false
                WindowWidthSizeClass.Expanded,
                WindowWidthSizeClass.Medium,
                -> true
                else -> false
            }
        }
    }

    if (twoPane && windowSizeClass != null) {
        ChatroomListTwoPane(
            knownFeedViewModel = knownFeedViewModel,
            newFeedViewModel = newFeedViewModel,
            widthSizeClass = windowSizeClass!!.widthSizeClass,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else {
        ChatroomListScreenOnlyList(
            knownFeedViewModel = knownFeedViewModel,
            newFeedViewModel = newFeedViewModel,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

data class RouteId(
    val route: String,
    val id: String,
)

@Composable
fun ChatroomListTwoPane(
    knownFeedViewModel: NostrChatroomListKnownFeedViewModel,
    newFeedViewModel: NostrChatroomListNewFeedViewModel,
    widthSizeClass: WindowWidthSizeClass,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    /** The index of the currently selected word, or `null` if none is selected */
    var selectedRoute: RouteId? by remember { mutableStateOf(null) }

    val navInterceptor =
        remember {
            { fullRoute: String ->
                if (fullRoute.startsWith("Room/") || fullRoute.startsWith("Channel/")) {
                    val route = fullRoute.substringBefore("/")
                    val id = fullRoute.substringAfter("/")
                    selectedRoute = RouteId(route, id)
                } else {
                    nav(fullRoute)
                }
            }
        }

    val strategy =
        remember {
            if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                HorizontalTwoPaneStrategy(
                    splitFraction = 1f / 3f,
                )
            } else {
                HorizontalTwoPaneStrategy(
                    splitFraction = 1f / 2.5f,
                )
            }
        }

    TwoPane(
        first = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                ChatroomListScreenOnlyList(
                    knownFeedViewModel,
                    newFeedViewModel,
                    accountViewModel,
                    navInterceptor,
                )
                Box(Modifier.padding(Size20dp), contentAlignment = Alignment.Center) {
                    ChannelFabColumn(accountViewModel, nav)
                }
                HorizontalDivider(
                    modifier =
                        Modifier
                            .fillMaxHeight() // fill the max height
                            .width(DividerThickness),
                )
            }
        },
        second = {
            selectedRoute?.let {
                if (it.route == "Room") {
                    ChatroomScreen(
                        roomId = it.id,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                if (it.route == "Channel") {
                    ChannelScreen(
                        channelId = it.id,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        },
        strategy = strategy,
        displayFeatures = accountViewModel.settings.displayFeatures.value,
        foldAwareConfiguration = FoldAwareConfiguration.VerticalFoldsOnly,
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatroomListScreenOnlyList(
    knownFeedViewModel: NostrChatroomListKnownFeedViewModel,
    newFeedViewModel: NostrChatroomListNewFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()

    var moreActionsExpanded by remember { mutableStateOf(false) }
    val markKnownAsRead = remember { mutableStateOf(false) }
    val markNewAsRead = remember { mutableStateOf(false) }

    WatchAccountForListScreen(knownFeedViewModel, newFeedViewModel, accountViewModel)

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    NostrChatroomListDataSource.account = accountViewModel.account
                    NostrChatroomListDataSource.start()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    val tabs by
        remember(knownFeedViewModel, markKnownAsRead) {
            derivedStateOf {
                listOf(
                    ChatroomListTabItem(R.string.known, knownFeedViewModel, markKnownAsRead),
                    ChatroomListTabItem(R.string.new_requests, newFeedViewModel, markNewAsRead),
                )
            }
        }

    Column(
        modifier = Modifier.fillMaxHeight(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            TabRow(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                selectedTabIndex = pagerState.currentPage,
                modifier = TabRowHeight,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        text = { Text(text = stringRes(tab.resource)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    )
                }
            }

            IconButton(
                modifier = Modifier.size(40.dp).align(Alignment.CenterEnd),
                onClick = { moreActionsExpanded = true },
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringRes(id = R.string.more_options),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )

                ChatroomTabMenu(
                    moreActionsExpanded,
                    { moreActionsExpanded = false },
                    { markKnownAsRead.value = true },
                    { markNewAsRead.value = true },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ChatroomListFeedView(
                viewModel = tabs[page].viewModel,
                accountViewModel = accountViewModel,
                nav = nav,
                markAsRead = tabs[page].markAsRead,
            )
        }
    }
}

@Composable
fun WatchAccountForListScreen(
    knownFeedViewModel: NostrChatroomListKnownFeedViewModel,
    newFeedViewModel: NostrChatroomListNewFeedViewModel,
    accountViewModel: AccountViewModel,
) {
    LaunchedEffect(accountViewModel) {
        launch(Dispatchers.IO) {
            NostrChatroomListDataSource.account = accountViewModel.account
            NostrChatroomListDataSource.start()
            knownFeedViewModel.invalidateData(true)
            newFeedViewModel.invalidateData(true)
        }
    }
}

@Immutable
class ChatroomListTabItem(
    val resource: Int,
    val viewModel: FeedViewModel,
    val markAsRead: MutableState<Boolean>,
)

@Composable
fun ChatroomTabMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMarkKnownAsRead: () -> Unit,
    onMarkNewAsRead: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringRes(R.string.mark_all_known_as_read)) },
            onClick = {
                onMarkKnownAsRead()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringRes(R.string.mark_all_new_as_read)) },
            onClick = {
                onMarkNewAsRead()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringRes(R.string.mark_all_as_read)) },
            onClick = {
                onMarkKnownAsRead()
                onMarkNewAsRead()
                onDismiss()
            },
        )
    }
}
