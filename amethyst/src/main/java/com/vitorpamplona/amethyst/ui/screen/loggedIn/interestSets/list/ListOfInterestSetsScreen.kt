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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.interestSets.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun ListOfInterestSetsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val sets by accountViewModel.account.interestSets.listFeedFlow
        .collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopBarWithBackButton(caption = stringRes(R.string.interest_sets_title), nav::popBack)
        },
        floatingActionButton = {
            InterestSetFab(onAdd = { nav.nav(Route.InterestSetMetadataEdit()) })
        },
    ) { paddingValues ->
        Column(
            Modifier
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                ).fillMaxHeight(),
        ) {
            if (sets.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringRes(R.string.interest_sets_empty))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(sets, key = { it.identifier }) { set ->
                        InterestSetItem(
                            interestSet = set,
                            onClick = { nav.nav(Route.InterestSetView(set.identifier)) },
                            onRename = { nav.nav(Route.InterestSetMetadataEdit(set.identifier)) },
                            onClone = {
                                accountViewModel.launchSigner {
                                    accountViewModel.account.interestSets.cloneInterestSet(
                                        source = set,
                                        customName = null,
                                        account = accountViewModel.account,
                                    )
                                }
                            },
                            onDelete = {
                                accountViewModel.launchSigner {
                                    accountViewModel.account.interestSets.deleteInterestSet(
                                        identifier = set.identifier,
                                        account = accountViewModel.account,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InterestSetFab(onAdd: () -> Unit) {
    ExtendedFloatingActionButton(
        text = {
            Text(text = stringRes(R.string.interest_set_create_btn_label))
        },
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
            )
        },
        onClick = onAdd,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
    )
}
