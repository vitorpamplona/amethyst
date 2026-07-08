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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupRosterSubscription
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * The top-level "Relay Groups" destination — one tap from the nav drawer / bottom
 * bar, distinct from the DM-centric Messages tab. Lists the host relays of the
 * groups the user has joined (each drilling into that relay's channels) and offers
 * the discovery entry to find more. Keeps every joined group's roster live while
 * on top via [RelayGroupRosterSubscription].
 */
@Composable
fun RelayGroupsHomeScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayGroupRosterSubscription(accountViewModel.dataSources().relayGroupRoster, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            UserDrawerSearchTopBar(accountViewModel, nav) {
                Text(
                    text = stringRes(R.string.relay_groups_title),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        bottomBar = {
            AppBottomBar(Route.RelayGroups, nav, accountViewModel) { route ->
                nav.navBottomBar(route)
            }
        },
        accountViewModel = accountViewModel,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Joined relays (each drills into its channels), followed by the
            // always-present "Find groups" discovery row.
            RelayGroupServerList(accountViewModel, nav)
        }
    }
}
