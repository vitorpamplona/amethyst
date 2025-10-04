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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.nip51Lists.followSets.FollowSet
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.FollowSetFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.BackButton
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.VertPadding
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowSetScreen(
    selectedSetIdentifier: String,
    accountViewModel: AccountViewModel,
    navigator: INav,
) {
    val followSetViewModel: FollowSetFeedViewModel =
        viewModel(
            key = "FollowSetFeedViewModel",
            factory = FollowSetFeedViewModel.Factory(accountViewModel.account),
        )

    FollowSetScreen(selectedSetIdentifier, followSetViewModel, accountViewModel, navigator)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowSetScreen(
    selectedSetIdentifier: String,
    followSetViewModel: FollowSetFeedViewModel,
    accountViewModel: AccountViewModel,
    navigator: INav,
) {
    val followSetState by followSetViewModel.feedContent.collectAsState()
    val uiScope = rememberCoroutineScope()

    val selectedSetState =
        remember(followSetState) {
            derivedStateOf {
                uiScope.launch {
                    delay(500L)
                }
                val note =
                    followSetViewModel.getFollowSetNote(
                        selectedSetIdentifier,
                        accountViewModel.account,
                    )

                if (note != null) {
                    val event = note.event as PeopleListEvent
                    println("Found list, with title: ${event.nameOrTitle()}")
                    val selectedFollowSet =
                        FollowSet.mapEventToSet(
                            event,
                            accountViewModel.account.signer,
                        )
                    return@derivedStateOf selectedFollowSet
                } else {
                    null
                }
            }
        }

    BackHandler { navigator.popBack() }

    when {
        selectedSetState.value != null -> {
            val selectedSet = selectedSetState.value
            val publicMembers = selectedSet!!.publicProfiles.mapToUsers(accountViewModel).filterNotNull()
            val privateMembers = selectedSet.privateProfiles.mapToUsers(accountViewModel).filterNotNull()
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            TitleAndDescription(followSet = selectedSet)
                        },
                        navigationIcon = {
                            BackButton(
                                onPress = { navigator.popBack() },
                            )
                        },
                        actions = {
                            ListActionsMenuButton(
                                onBroadcastList = {
                                    val updatedSetNote =
                                        followSetViewModel.getFollowSetNote(
                                            selectedSet.identifierTag,
                                            accountViewModel.account,
                                        )
                                    accountViewModel.broadcast(updatedSetNote!!)
                                },
                                onDeleteList = {
                                    followSetViewModel.deleteFollowSet(
                                        selectedSet,
                                        accountViewModel.account,
                                    )
                                    navigator.popBack()
                                },
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
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding(),
                            ).consumeWindowInsets(padding)
                            .imePadding(),
                    publicMemberList = publicMembers,
                    privateMemberList = privateMembers,
                    onDeleteUser = {
                        followSetViewModel.removeUserFromSet(
                            it,
                            selectedSet,
                            accountViewModel.account,
                        )
                    },
                    accountViewModel = accountViewModel,
                    nav = navigator,
                )
            }
        }

        selectedSetState.value == null -> {
            accountViewModel.toastManager.toast(
                "Follow Set Error",
                "Could not find requested follow set",
            ) {
                navigator.popBack()
            }
        }
    }
}

fun Set<String>.mapToUsers(accountViewModel: AccountViewModel): List<User?> = map { accountViewModel.checkGetOrCreateUser(it) }

@Composable
fun TitleAndDescription(
    modifier: Modifier = Modifier,
    followSet: FollowSet,
) {
    Column(
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = followSet.title,
            )
            Spacer(modifier = StdHorzSpacer)
            Icon(
                painter = painterResource(R.drawable.format_list_bulleted_type),
                contentDescription = null,
            )
        }

        if (followSet.description != null) {
            Text(
                text = followSet.description,
                fontSize = 18.sp,
                fontWeight = FontWeight.Thin,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FollowSetListView(
    modifier: Modifier = Modifier,
    publicMemberList: List<User>,
    privateMemberList: List<User>,
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
        if (publicMemberList.isNotEmpty()) {
            stickyHeader {
                Column(
                    modifier = VertPadding,
                ) {
                    Text(
                        text = "Public Profiles",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            itemsIndexed(publicMemberList, key = { _, item -> item.pubkeyHex }) { _, item ->
                FollowSetListItem(
                    modifier = Modifier.animateItem(),
                    user = item,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onDeleteUser = onDeleteUser,
                )
            }
            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
        if (privateMemberList.isNotEmpty()) {
            stickyHeader {
                Text(
                    text = "Private Profiles",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            itemsIndexed(privateMemberList, key = { _, item -> item.pubkeyHex }) { _, item ->
                FollowSetListItem(
                    modifier = Modifier.animateItem(),
                    user = item,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onDeleteUser = onDeleteUser,
                )
            }
            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun FollowSetListItem(
    modifier: Modifier = Modifier,
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDeleteUser: (String) -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        Row {
            UserCompose(
                user,
                overallModifier = HalfPadding.weight(1f, fill = false),
                accountViewModel = accountViewModel,
                nav = nav,
            )
            IconButton(
                onClick = {
                    onDeleteUser(user.pubkeyHex)
                },
                modifier =
                    HalfPadding
                        .align(Alignment.CenterVertically)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(percent = 80),
                        ),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
fun ListActionsMenuButton(
    modifier: Modifier = Modifier,
    onBroadcastList: () -> Unit,
    onDeleteList: () -> Unit,
) {
    val isActionListOpen = remember { mutableStateOf(false) }

    ClickableBox(
        modifier =
            StdPadding
                .size(30.dp)
                .border(
                    width = Dp.Hairline,
                    color = ButtonDefaults.filledTonalButtonColors().containerColor,
                    shape = ButtonBorder,
                ).background(
                    color = ButtonDefaults.filledTonalButtonColors().containerColor,
                    shape = ButtonBorder,
                ),
        onClick = { isActionListOpen.value = true },
    ) {
        VerticalDotsIcon()
        ListActionsMenu(
            onCloseMenu = { isActionListOpen.value = false },
            isOpen = isActionListOpen.value,
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
    onBroadcastList: () -> Unit,
    onDeleteList: () -> Unit,
) {
    DropdownMenu(
        expanded = isOpen,
        onDismissRequest = onCloseMenu,
    ) {
        DropdownMenuItem(
            text = {
                Text("Broadcast List")
            },
            onClick = {
                onBroadcastList()
                onCloseMenu()
            },
        )
        HorizontalDivider()
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
