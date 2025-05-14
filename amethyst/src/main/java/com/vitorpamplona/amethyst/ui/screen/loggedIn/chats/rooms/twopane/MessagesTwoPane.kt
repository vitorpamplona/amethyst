/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.twopane

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.accompanist.adaptive.FoldAwareConfiguration
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import com.vitorpamplona.amethyst.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.MainTopBar
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.Chatroom
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ChannelView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.ChannelFabColumn
import com.vitorpamplona.amethyst.ui.theme.Size20dp

@Composable
fun MessagesTwoPane(
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
            AppBottomBar(Route.Message, accountViewModel) { route ->
                if (route == Route.Message) {
                    knownFeedContentState.sendToTop()
                    newFeedContentState.sendToTop()
                } else {
                    nav.newStack(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) { padding ->
        TwoPane(
            first = {
                Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.BottomEnd) {
                    ChatroomList(
                        knownFeedContentState,
                        newFeedContentState,
                        accountViewModel,
                        twoPaneNav,
                    )

                    Box(Modifier.padding(Size20dp), contentAlignment = Alignment.Center) {
                        ChannelFabColumn(nav)
                    }
                }
            },
            second = {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    twoPaneNav.innerNav.value?.let {
                        if (it is Route.Room) {
                            Chatroom(
                                roomId = it.id.toString(),
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }

                        if (it is Route.Channel) {
                            ChannelView(
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
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(),
        )
    }
}
