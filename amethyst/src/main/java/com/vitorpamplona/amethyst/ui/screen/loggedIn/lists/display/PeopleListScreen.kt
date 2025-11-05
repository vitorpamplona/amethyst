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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.display

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.searchCommand.UserSearchDataSourceSubscription
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.AboutDisplay
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.ClearTextIcon
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UserComposeNoAction
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.AnimateOnNewSearch
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.HalfHalfHorzModifier
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.HalfVertSpacer
import com.vitorpamplona.amethyst.ui.theme.LightRedColor
import com.vitorpamplona.amethyst.ui.theme.PopupUpEffect
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.lang.reflect.Modifier.isPrivate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(
    selectedDTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: PeopleListViewModel = viewModel()
    viewModel.init(accountViewModel.account, selectedDTag)

    val pagerState = rememberPagerState { 2 }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        TitleAndDescription(viewModel)
                    },
                    navigationIcon = {
                        IconButton(nav::popBack) {
                            ArrowBackIcon()
                        }
                    },
                    actions = {
                        ListActionsMenuButton(viewModel, accountViewModel, nav)
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
                TabRow(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    selectedTabIndex = pagerState.currentPage,
                    modifier = TabRowHeight,
                ) {
                    val scope = rememberCoroutineScope()
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(text = stringRes(R.string.private_members)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(text = stringRes(R.string.public_members)) },
                    )
                }
            }
        },
    ) { padding ->
        ListViewAndEditColumn(
            viewModel = viewModel,
            pagerState = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ).consumeWindowInsets(padding)
                    .imePadding(),
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun TitleAndDescription(viewModel: PeopleListViewModel) {
    val selectedSetState = viewModel.selectedList.collectAsStateWithLifecycle()
    selectedSetState.value?.let { selectedSet ->
        Text(
            text = selectedSet.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListViewAndEditColumn(
    viewModel: PeopleListViewModel,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = modifier) {
        FollowSetListView(
            viewModel = viewModel,
            pagerState = pagerState,
            modifier = Modifier.weight(1f),
            onDeleteUser = { user, isPrivate ->
                accountViewModel.runIOCatching {
                    viewModel.removeUserFromSet(user, isPrivate)
                }
            },
            accountViewModel = accountViewModel,
            nav = nav,
        )

        RenderAddUserFieldAndSuggestions(viewModel, pagerState, accountViewModel)
    }
}

@Composable
private fun RenderAddUserFieldAndSuggestions(
    viewModel: PeopleListViewModel,
    pagerState: PagerState,
    accountViewModel: AccountViewModel,
) {
    UserSearchDataSourceSubscription(viewModel.userSuggestions, accountViewModel)

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                viewModel.userSuggestions.invalidateData()
            }
        }
        launch(Dispatchers.IO) {
            LocalCache.live.deletedEventBundles.collect {
                viewModel.userSuggestions.invalidateData()
            }
        }
    }

    Spacer(HalfVertSpacer)

    UserSearchField(viewModel.userSuggestions)

    ShowUserSuggestions(
        userSuggestions = viewModel.userSuggestions,
        hasUserFlow = { user ->
            viewModel.hasUserFlow(user, pagerState.currentPage == 0)
        },
        onSelect = { user ->
            accountViewModel.runIOCatching {
                viewModel.addUserToSet(user, pagerState.currentPage == 0)
            }
        },
        onDelete = { user ->
            accountViewModel.runIOCatching {
                viewModel.removeUserFromSet(user, pagerState.currentPage == 0)
            }
        },
        accountViewModel = accountViewModel,
    )
}

@Composable
private fun FollowSetListView(
    viewModel: PeopleListViewModel,
    pagerState: PagerState,
    modifier: Modifier,
    onDeleteUser: (User, Boolean) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val selectedSetState = viewModel.selectedList.collectAsStateWithLifecycle()
    selectedSetState.value?.let { selectedSet ->
        HorizontalPager(state = pagerState, modifier) { page ->
            when (page) {
                0 ->
                    FollowSetListView(
                        memberList = selectedSet.privateMembersList,
                        onDeleteUser = { user ->
                            onDeleteUser(user, true)
                        },
                        modifier = Modifier.fillMaxSize(),
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )

                1 ->
                    FollowSetListView(
                        memberList = selectedSet.publicMembersList,
                        onDeleteUser = { user ->
                            onDeleteUser(user, false)
                        },
                        modifier = Modifier.fillMaxSize(),
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
            }
        }
    }
}

@Composable
@Preview(device = "spec:width=2160px,height=2940px,dpi=440")
fun FollowSetListViewPreview() {
    val accountViewModel = mockAccountViewModel()

    val user1: User = LocalCache.getOrCreateUser("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
    val user2: User = LocalCache.getOrCreateUser("ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b")
    val user3: User = LocalCache.getOrCreateUser("7eb29c126b3628077e2e3d863b917a56b74293aa9d8a9abc26a40ba3f2866baf")

    ThemeComparisonRow {
        Column {
            FollowSetListView(
                memberList = persistentListOf(user1, user2, user3),
                onDeleteUser = { user -> },
                accountViewModel = accountViewModel,
                nav = EmptyNav,
            )

            Spacer(HalfVertSpacer)

            var userName by remember { mutableStateOf("") }
            OutlinedTextField(
                label = { Text(text = stringRes(R.string.search_and_add_a_user)) },
                modifier =
                    Modifier
                        .padding(horizontal = Size10dp)
                        .fillMaxWidth(),
                value = userName,
                onValueChange = {
                    userName = it
                },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {},
                    ) {
                        ClearTextIcon()
                    }
                },
            )

            Card(
                modifier = Modifier.padding(horizontal = 10.dp),
                elevation = cardElevation(5.dp),
                shape = PopupUpEffect,
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(top = 10.dp),
                    modifier = Modifier.heightIn(0.dp, 200.dp),
                ) {
                    itemsIndexed(persistentListOf(user1, user2, user3), key = { _, item -> item.pubkeyHex }) { _, baseUser ->
                        DrawUser(
                            baseUser,
                            { MutableStateFlow(false) },
                            {},
                            {},
                            accountViewModel,
                        )

                        HorizontalDivider(
                            thickness = DividerThickness,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowSetListView(
    memberList: ImmutableList<User>,
    modifier: Modifier = Modifier,
    onDeleteUser: (User) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier,
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(memberList, key = { _, item -> "u" + item.pubkeyHex }) { _, item ->
            FollowSetListItem(
                modifier = Modifier.animateContentSize(),
                user = item,
                accountViewModel = accountViewModel,
                nav = nav,
                onDeleteUser = onDeleteUser,
            )
        }
    }
}

@Composable
fun ShowUserSuggestions(
    userSuggestions: UserSuggestionState,
    hasUserFlow: (User) -> Flow<Boolean>,
    onSelect: (User) -> Unit,
    onDelete: (User) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val listState = rememberLazyListState()

    AnimateOnNewSearch(userSuggestions, listState)

    val suggestions by userSuggestions.results.collectAsStateWithLifecycle(emptyList())

    if (suggestions.isNotEmpty()) {
        Card(
            modifier = Modifier.padding(start = 11.dp, end = 11.dp),
            elevation = cardElevation(5.dp),
            shape = PopupUpEffect,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(top = 10.dp),
                modifier =
                    Modifier
                        .heightIn(0.dp, 200.dp),
                state = listState,
            ) {
                itemsIndexed(suggestions, key = { _, item -> item.pubkeyHex }) { _, baseUser ->
                    DrawUser(baseUser, hasUserFlow, onSelect, onDelete, accountViewModel)

                    HorizontalDivider(
                        thickness = DividerThickness,
                    )
                }
            }
        }
    }

    Spacer(StdVertSpacer)
}

@Composable
private fun DrawUser(
    baseUser: User,
    hasUserFlow: (User) -> Flow<Boolean>,
    onSelect: (User) -> Unit,
    onDelete: (User) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = { onSelect(baseUser) })
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp,
                    bottom = 10.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClickableUserPicture(baseUser, 55.dp, accountViewModel, Modifier, null)

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsernameDisplay(
                    baseUser,
                    accountViewModel = accountViewModel,
                )
                HasUserTag(baseUser, hasUserFlow, onDelete)
            }

            AboutDisplay(baseUser, accountViewModel)
        }
    }
}

@Composable
fun RowScope.HasUserTag(
    baseUser: User,
    hasUserFlow: (User) -> Flow<Boolean>,
    onDelete: (User) -> Unit,
) {
    val hasUserState by hasUserFlow(baseUser).collectAsStateWithLifecycle(false)
    if (hasUserState) {
        Spacer(StdHorzSpacer)
        Text(
            text = stringRes(id = R.string.in_the_list),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                remember {
                    Modifier
                        .clip(SmallBorder)
                        .background(Color.Black)
                        .padding(horizontal = 5.dp)
                },
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            modifier = Modifier.size(30.dp).padding(start = 10.dp),
            onClick = { onDelete(baseUser) },
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = stringRes(id = R.string.remove),
                modifier = Modifier.size(15.dp),
                tint = LightRedColor,
            )
        }
    }
}

@Composable
fun FollowSetListItem(
    modifier: Modifier = Modifier,
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDeleteUser: (User) -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        Row(HalfHalfHorzModifier) {
            UserComposeNoAction(
                user,
                modifier = HalfPadding.weight(1f, fill = false),
                accountViewModel = accountViewModel,
                nav = nav,
            )
            IconButton(
                onClick = {
                    onDeleteUser(user)
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
        HorizontalDivider(thickness = DividerThickness)
    }
}

@Composable
fun ListActionsMenuButton(
    viewModel: PeopleListViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ListActionsMenuButton(
        onBroadcastList = {
            accountViewModel.runIOCatching {
                viewModel.loadNote()?.let { updatedSetNote ->
                    accountViewModel.broadcast(updatedSetNote)
                }
            }
        },
        onDeleteList = {
            accountViewModel.runIOCatching {
                viewModel.deleteFollowSet()
            }
            nav.popBack()
        },
    )
}

@Composable
fun ListActionsMenuButton(
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
        HorizontalDivider(thickness = DividerThickness)
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

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun UserSearchField(
    userSuggestions: UserSuggestionState,
    modifier: Modifier = Modifier,
) {
    var userName by remember(userSuggestions) { mutableStateOf(userSuggestions.currentWord.value) }
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        label = { Text(text = stringRes(R.string.search_and_add_a_user)) },
        modifier =
            modifier
                .padding(horizontal = Size10dp)
                .fillMaxWidth(),
        value = userName,
        onValueChange = {
            userName = it
            userSuggestions.processCurrentWord(it)
        },
        singleLine = true,
        trailingIcon = {
            IconButton(
                onClick = {
                    userName = ""
                    userSuggestions.processCurrentWord("")
                    focusManager.clearFocus()
                },
            ) {
                ClearTextIcon()
            }
        },
    )
}
