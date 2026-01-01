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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.display.packs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.ClearTextIcon
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.note.externalLinkForNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.display.DrawUser
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.display.PeopleListView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.display.RenderAddUserFieldAndSuggestions
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.HalfVertSpacer
import com.vitorpamplona.amethyst.ui.theme.PopupUpEffect
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowPackScreen(
    selectedDTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: FollowPackViewModel = viewModel()
    viewModel.init(accountViewModel.account, selectedDTag)

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
            }
        },
    ) { padding ->
        ListViewAndEditColumn(
            viewModel = viewModel,
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
private fun TitleAndDescription(viewModel: FollowPackViewModel) {
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
    viewModel: FollowPackViewModel,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = modifier) {
        PeopleListPager(
            viewModel = viewModel,
            modifier = Modifier.weight(1f),
            onDeleteUser = { user ->
                accountViewModel.launchSigner {
                    viewModel.removeUserFromSet(user)
                }
            },
            accountViewModel = accountViewModel,
            nav = nav,
        )

        RenderAddUserFieldAndSuggestions(viewModel, accountViewModel)
    }
}

@Composable
private fun RenderAddUserFieldAndSuggestions(
    viewModel: FollowPackViewModel,
    accountViewModel: AccountViewModel,
) {
    RenderAddUserFieldAndSuggestions(
        viewModel.userSuggestions,
        hasUserFlow = { user ->
            viewModel.hasUserFlow(user)
        },
        addUserToSet = { user ->
            accountViewModel.launchSigner {
                viewModel.addUserToSet(user)
            }
        },
        removeUserFromSet = { user ->
            accountViewModel.launchSigner {
                viewModel.removeUserFromSet(user)
            }
        },
        accountViewModel = accountViewModel,
    )
}

@Composable
private fun PeopleListPager(
    viewModel: FollowPackViewModel,
    modifier: Modifier,
    onDeleteUser: (User) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val selectedSetState = viewModel.selectedList.collectAsStateWithLifecycle()
    selectedSetState.value?.let { selectedSet ->
        PeopleListView(
            memberList = selectedSet.publicMembersList,
            onDeleteUser = onDeleteUser,
            modifier = modifier,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun ListActionsMenuButton(
    viewModel: FollowPackViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ListActionsMenuButton(
        note = viewModel::selectedNote,
        onEditList = {
            nav.nav { Route.FollowPackMetadataEdit(viewModel.selectedDTag.value) }
        },
        onBroadcastList = {
            accountViewModel.launchSigner {
                viewModel.loadNote()?.let { updatedSetNote ->
                    accountViewModel.broadcast(updatedSetNote)
                }
            }
        },
        onDeleteList = {
            accountViewModel.launchSigner {
                viewModel.deleteFollowSet()
            }
            nav.popBack()
        },
    )
}

@Composable
private fun ListActionsMenuButton(
    note: () -> AddressableNote,
    onEditList: () -> Unit,
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

        DropdownMenu(
            expanded = isActionListOpen.value,
            onDismissRequest = { isActionListOpen.value = false },
        ) {
            val context = LocalContext.current
            DropdownMenuItem(
                text = { Text(stringRes(R.string.quick_action_share)) },
                onClick = {
                    val sendIntent =
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                externalLinkForNote(note()),
                            )
                            putExtra(
                                Intent.EXTRA_TITLE,
                                stringRes(context, R.string.quick_action_share_browser_link),
                            )
                        }

                    val shareIntent =
                        Intent.createChooser(sendIntent, stringRes(context, R.string.quick_action_share))
                    ContextCompat.startActivity(context, shareIntent, null)
                    isActionListOpen.value = false
                },
            )
            HorizontalDivider(thickness = DividerThickness)
            DropdownMenuItem(
                text = {
                    Text(stringRes(R.string.follow_pack_edit_list_metadata))
                },
                onClick = {
                    onEditList()
                    isActionListOpen.value = false
                },
            )
            HorizontalDivider(thickness = DividerThickness)
            DropdownMenuItem(
                text = {
                    Text(stringRes(R.string.follow_pack_broadcast))
                },
                onClick = {
                    onBroadcastList()
                    isActionListOpen.value = false
                },
            )
            HorizontalDivider(thickness = DividerThickness)
            DropdownMenuItem(
                text = {
                    Text(stringRes(R.string.follow_pack_delete))
                },
                onClick = {
                    onDeleteList()
                    isActionListOpen.value = false
                },
            )
        }
    }
}

@Composable
@Preview(device = "spec:width=2160px,height=2940px,dpi=440")
fun FollowPackViewPreview() {
    val accountViewModel = mockAccountViewModel()

    val user1: User = LocalCache.getOrCreateUser("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
    val user2: User = LocalCache.getOrCreateUser("ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b")
    val user3: User = LocalCache.getOrCreateUser("7eb29c126b3628077e2e3d863b917a56b74293aa9d8a9abc26a40ba3f2866baf")

    ThemeComparisonRow {
        Column {
            PeopleListView(
                memberList = persistentListOf(user1, user2, user3),
                onDeleteUser = { user -> },
                accountViewModel = accountViewModel,
                nav = EmptyNav(),
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
