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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.followsets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.FollowSet
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.ListVisibility
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.BackButton
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowSetScreen(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    navigator: INav,
    // TODO: Investigate passing follow set properties rather than follow set object.
    selectedSet: FollowSet,
    onProfileRemove: (String) -> Unit,
    onListSave: () -> Unit,
    onListBroadcast: () -> Unit,
    onListDelete: () -> Unit,
) {
    BackHandler { onClose() }

    // TODO: Investigate moving the mapping function to a VM.(related to above TODO).
    val users = selectedSet.profileList.mapToUsers(accountViewModel).filterNotNull()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedSet.title,
                        )
                        Icon(
                            painter =
                                painterResource(
                                    when (selectedSet.listVisibility) {
                                        ListVisibility.Public -> R.drawable.ic_public
                                        ListVisibility.Private -> R.drawable.lock
                                        ListVisibility.Mixed -> R.drawable.format_list_bulleted_type
                                    },
                                ),
                            contentDescription = null,
                        )
                    }
                },
                navigationIcon = {
                    BackButton(
                        onPress = onClose,
                    )
                },
                actions = {
                    ListActionsMenuButton(
                        onSaveList = onListSave,
                        onBroadcastList = onListBroadcast,
                        onDeleteList = onListDelete,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        FollowSetListView(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ).consumeWindowInsets(padding)
                    .imePadding(),
            followSetList = users,
            onDeleteUser = { onProfileRemove(it) },
            accountViewModel = accountViewModel,
            nav = navigator,
        )
    }
}

fun Set<String>.mapToUsers(accountViewModel: AccountViewModel): List<User?> = map { accountViewModel.checkGetOrCreateUser(it) }

@Composable
private fun FollowSetListView(
    modifier: Modifier = Modifier,
    followSetList: List<User>,
    onDeleteUser: (String) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier,
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(followSetList, key = { _, item -> item.pubkeyHex }) { _, item ->
            Row {
                IconButton(
                    onClick = {
                        onDeleteUser(item.pubkeyHex)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                    )
                }
                UserCompose(item, accountViewModel = accountViewModel, nav = nav)
                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }
        }
    }
}

@Composable
fun ListActionsMenuButton(
    modifier: Modifier = Modifier,
    onSaveList: () -> Unit,
    onBroadcastList: () -> Unit,
    onDeleteList: () -> Unit,
) {
    val isActionListOpen = remember { mutableStateOf(false) }

    ClickableBox(
        onClick = { isActionListOpen.value = true },
    ) {
        VerticalDotsIcon()
        ListActionsMenu(
            onCloseMenu = { isActionListOpen.value = false },
            isOpen = isActionListOpen.value,
            onSaveList = onSaveList,
            onBroadcastList = onBroadcastList,
            onDeleteList = onDeleteList,
        )
    }
}

@Composable
fun ListActionsMenu(
    modifier: Modifier = Modifier,
    onCloseMenu: () -> Unit,
    isOpen: Boolean,
    onSaveList: () -> Unit,
    onBroadcastList: () -> Unit,
    onDeleteList: () -> Unit,
) {
    DropdownMenu(
        expanded = isOpen,
        onDismissRequest = onCloseMenu,
    ) {
        DropdownMenuItem(
            text = {
                Text("Save Changes")
            },
            onClick = {
                onSaveList()
                onCloseMenu()
            },
        )
        DropdownMenuItem(
            text = {
                Text("Broadcast List")
            },
            onClick = {
                onBroadcastList()
                onCloseMenu()
            },
        )
        DropdownMenuItem(
            text = {
                Text("Delete List")
            },
            onClick = {
                onDeleteList()
                onCloseMenu()
            },
        )
    }
}
