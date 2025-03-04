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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chatlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.vitorpamplona.amethyst.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.navigation.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.MainTopBar
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatlist.private.Chatroom
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatlist.public.Channel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ChatroomListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
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
            knownFeedContentState = accountViewModel.feedStates.dmKnown,
            newFeedContentState = accountViewModel.feedStates.dmNew,
            widthSizeClass = windowSizeClass!!.widthSizeClass,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else {
        ScaffoldChatroomListScreenOnlyList(
            knownFeedContentState = accountViewModel.feedStates.dmKnown,
            newFeedContentState = accountViewModel.feedStates.dmNew,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

class TwoPaneNav(
    val nav: INav,
    val scope: CoroutineScope,
) : INav {
    override val drawerState: DrawerState = nav.drawerState

    val innerNav = mutableStateOf<RouteId?>(null)

    override fun nav(route: String) {
        if (route.startsWith("Room/") || route.startsWith("Channel/")) {
            innerNav.value = RouteId(route.substringBefore("/"), route.substringAfter("/"))
        } else {
            nav.nav(route)
        }
    }

    override fun nav(routeMaker: suspend () -> String) {
        scope.launch(Dispatchers.Default) {
            val route = routeMaker()
            if (route.startsWith("Room/") || route.startsWith("Channel/")) {
                innerNav.value = RouteId(route.substringBefore("/"), route.substringAfter("/"))
            } else {
                nav.nav(route)
            }
        }
    }

    override fun newStack(route: String) {
        nav.newStack(route)
    }

    override fun popBack() {
        nav.popBack()
    }

    override fun popUpTo(
        route: String,
        upTo: String,
    ) {
        nav.popUpTo(route, upTo)
    }

    override fun closeDrawer() {
        nav.closeDrawer()
    }

    override fun openDrawer() {
        nav.openDrawer()
    }

    data class RouteId(
        val route: String,
        val id: String,
    )
}

@Composable
fun ChatroomListTwoPane(
    knownFeedContentState: FeedContentState,
    newFeedContentState: FeedContentState,
    widthSizeClass: WindowWidthSizeClass,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    /** The index of the currently selected word, or `null` if none is selected */
    val scope = rememberCoroutineScope()
    val twoPaneNav = remember { TwoPaneNav(nav, scope) }

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

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                MainTopBar(accountViewModel, nav)
            }
        },
        bottomBar = {
            AppBottomBar(Route.Message, accountViewModel) { route, _ ->
                nav.newStack(route.base)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        TwoPane(
            first = {
                Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.BottomEnd) {
                    ChatroomListScreenOnlyList(
                        knownFeedContentState,
                        newFeedContentState,
                        accountViewModel,
                        twoPaneNav,
                    )

                    Box(Modifier.padding(Size20dp), contentAlignment = Alignment.Center) {
                        ChannelFabColumn(accountViewModel, nav)
                    }
                }
            },
            second = {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    twoPaneNav.innerNav.value?.let {
                        if (it.route == "Room") {
                            Chatroom(
                                roomId = it.id,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }

                        if (it.route == "Channel") {
                            Channel(
                                channelId = it.id,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }
                    }
                }
            },
            strategy = strategy,
            displayFeatures = accountViewModel.settings.displayFeatures.value,
            foldAwareConfiguration = FoldAwareConfiguration.VerticalFoldsOnly,
            modifier = Modifier.padding(it).consumeWindowInsets(it).fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatroomListScreenOnlyList(
    knownFeedContentState: FeedContentState,
    newFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberPagerState { 2 }

    val markKnownAsRead = remember { mutableStateOf(false) }
    val markNewAsRead = remember { mutableStateOf(false) }

    WatchAccountForListScreen(knownFeedContentState, newFeedContentState, accountViewModel)
    WatchLifecycleAndRefreshDataSource(accountViewModel)

    val tabs by
        remember(knownFeedContentState, markKnownAsRead) {
            derivedStateOf {
                listOf(
                    ChatroomListTabItem(R.string.known, knownFeedContentState, markKnownAsRead),
                    ChatroomListTabItem(R.string.new_requests, newFeedContentState, markNewAsRead),
                )
            }
        }

    Column {
        ChatroomListOnlyTabs(
            pagerState,
            tabs,
            { markKnownAsRead.value = true },
            { markNewAsRead.value = true },
        )

        ChatroomListTabs(
            pagerState,
            tabs,
            PaddingValues(0.dp),
            accountViewModel,
            nav,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScaffoldChatroomListScreenOnlyList(
    knownFeedContentState: FeedContentState,
    newFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberPagerState { 2 }

    val markKnownAsRead = remember { mutableStateOf(false) }
    val markNewAsRead = remember { mutableStateOf(false) }

    WatchAccountForListScreen(knownFeedContentState, newFeedContentState, accountViewModel)
    WatchLifecycleAndRefreshDataSource(accountViewModel)

    val tabs by
        remember(knownFeedContentState, markKnownAsRead) {
            derivedStateOf {
                listOf(
                    ChatroomListTabItem(R.string.known, knownFeedContentState, markKnownAsRead),
                    ChatroomListTabItem(R.string.new_requests, newFeedContentState, markNewAsRead),
                )
            }
        }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                MainTopBar(accountViewModel, nav)
                ChatroomListOnlyTabs(
                    pagerState,
                    tabs,
                    { markKnownAsRead.value = true },
                    { markNewAsRead.value = true },
                )
            }
        },
        bottomBar = {
            AppBottomBar(Route.Message, accountViewModel) { route, _ ->
                if (route == Route.Message) {
                    tabs[pagerState.currentPage].feedContentState.sendToTop()
                } else {
                    nav.newStack(route.base)
                }
            }
        },
        floatingButton = {
            ChannelFabColumn(accountViewModel, nav)
        },
        accountViewModel = accountViewModel,
    ) {
        ChatroomListTabs(
            pagerState,
            tabs,
            it,
            accountViewModel,
            nav,
        )
    }
}

@Composable
private fun WatchLifecycleAndRefreshDataSource(accountViewModel: AccountViewModel) {
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
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatroomListTabs(
    pagerState: PagerState,
    tabs: List<ChatroomListTabItem>,
    paddingValues: PaddingValues,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    HorizontalPager(
        contentPadding = paddingValues,
        state = pagerState,
        userScrollEnabled = false,
    ) { page ->
        ChatroomListFeedView(
            feedContentState = tabs[page].feedContentState,
            accountViewModel = accountViewModel,
            nav = nav,
            markAsRead = tabs[page].markAsRead,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatroomListOnlyTabs(
    pagerState: PagerState,
    tabs: List<ChatroomListTabItem>,
    onMarkKnownAsRead: () -> Unit,
    onMarkNewAsRead: () -> Unit,
) {
    var moreActionsExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
            modifier =
                Modifier
                    .size(40.dp)
                    .align(Alignment.CenterEnd),
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
                onMarkKnownAsRead,
                onMarkNewAsRead,
            )
        }
    }
}

@Composable
fun WatchAccountForListScreen(
    knownFeedContentState: FeedContentState,
    newFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    LaunchedEffect(accountViewModel) {
        launch(Dispatchers.IO) {
            NostrChatroomListDataSource.account = accountViewModel.account
            NostrChatroomListDataSource.start()
            knownFeedContentState.invalidateData(true)
            newFeedContentState.invalidateData(true)
        }
    }
}

@Immutable
class ChatroomListTabItem(
    val resource: Int,
    val feedContentState: FeedContentState,
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
