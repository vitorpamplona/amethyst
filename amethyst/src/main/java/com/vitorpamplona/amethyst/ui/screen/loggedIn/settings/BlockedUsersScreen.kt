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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.ShowUserButton
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.UserFeedState
import com.vitorpamplona.amethyst.ui.screen.UserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.dal.HiddenAccountsFeedViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size15dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp

@Composable
fun BlockedUsersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: HiddenAccountsFeedViewModel =
        viewModel(factory = HiddenAccountsFeedViewModel.Factory(accountViewModel.account))

    InvalidateOnBlockListChange(accountViewModel) { viewModel.invalidateData() }

    var selected by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            BlockListTopBar(
                title = R.string.blocked_users,
                selected = selected,
                onCancel = { selected = emptySet() },
                onUnblock = {
                    accountViewModel.showUsers(selected.toList())
                    selected = emptySet()
                },
                nav = nav,
            )
        },
    ) { padding ->
        SelectableUserList(
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
            emptyMessage = R.string.security_blocked_users_empty,
            selected = selected,
            onToggle = { selected = if (it in selected) selected - it else selected + it },
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SelectableUserList(
    modifier: Modifier = Modifier,
    viewModel: UserFeedViewModel,
    emptyMessage: Int,
    selected: Set<String>,
    onToggle: ((String) -> Unit)? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()
    val selectionMode = selected.isNotEmpty()

    when (val state = feedState) {
        is UserFeedState.Loaded -> {
            val items by state.feed.collectAsStateWithLifecycle()
            val listState = rememberLazyListState()

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = rememberFeedContentPadding(FeedPadding),
                state = listState,
            ) {
                itemsIndexed(items, key = { _, item -> item.pubkeyHex }) { _, user ->
                    val isSelected = user.pubkeyHex in selected
                    val rowModifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode && onToggle != null) {
                                        onToggle(user.pubkeyHex)
                                    } else {
                                        nav.nav(routeFor(user))
                                    }
                                },
                                onLongClick = { onToggle?.invoke(user.pubkeyHex) },
                            ).let {
                                if (isSelected) {
                                    it.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                } else {
                                    it
                                }
                            }

                    Row(
                        modifier = rowModifier.padding(horizontal = Size15dp, vertical = Size10dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserPicture(user, Size55dp, accountViewModel = accountViewModel, nav = nav)
                        Column(
                            modifier =
                                Modifier
                                    .padding(start = 10.dp)
                                    .weight(1f),
                        ) {
                            UsernameDisplay(user, accountViewModel = accountViewModel)
                        }
                        if (selectionMode) {
                            Checkbox(checked = isSelected, onCheckedChange = { onToggle?.invoke(user.pubkeyHex) })
                        } else {
                            ShowUserButton { accountViewModel.show(user) }
                        }
                    }
                    HorizontalDivider(thickness = DividerThickness)
                }
            }
        }

        else -> {
            EmptyOrLoading(modifier = modifier, state = state, emptyMessage = emptyMessage) {
                viewModel.invalidateData()
            }
        }
    }
}
