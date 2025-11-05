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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.memberEdit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.DisplayParticipantNumberAndStatus
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.NewPeopleListCreationDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.HalfHalfVertPadding
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50ModifierOffset10
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPeopleListScreen(
    userToAddOrRemove: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var userBase by remember { mutableStateOf(LocalCache.getUserIfExists(userToAddOrRemove)) }

    if (userBase == null) {
        LaunchedEffect(userToAddOrRemove) {
            val newUserBase = LocalCache.checkGetOrCreateUser(userToAddOrRemove)
            if (newUserBase != userBase) {
                userBase = newUserBase
            }
        }
    }

    userBase?.let {
        EditPeopleListScreen(it, accountViewModel, nav)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPeopleListScreen(
    userToAddOrRemove: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .recalculateWindowInsets(),
        floatingActionButton = {
            PeopleListFabsAndMenu(accountViewModel)
        },
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.follow_set_man_dialog_title2, userToAddOrRemove.toBestDisplayName()), nav::popBack)
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                    ).consumeWindowInsets(contentPadding)
                    .imePadding(),
        ) {
            FollowSetManagementScreenBody(userToAddOrRemove, accountViewModel, nav)
        }
    }
}

@Composable
private fun PeopleListFabsAndMenu(accountViewModel: AccountViewModel) {
    var isOpen by remember { mutableStateOf(false) }

    ExtendedFloatingActionButton(
        text = {
            Text(text = stringRes(R.string.follow_set_create_btn_label))
        },
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
            )
        },
        onClick = { isOpen = !isOpen },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
    )

    if (isOpen) {
        NewPeopleListCreationDialog(
            onDismiss = {
                isOpen = false
            },
            onCreateList = { name, description ->
                accountViewModel.runIOCatching {
                    accountViewModel.account.peopleLists.addFollowList(
                        listName = name,
                        listDescription = description,
                        account = accountViewModel.account,
                    )
                }
                isOpen = false
            },
        )
    }
}

@Composable
private fun FollowSetManagementScreenBody(
    userToAddOrRemove: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val followSetsState by accountViewModel.account.peopleLists.uiListFlow
        .collectAsStateWithLifecycle()

    if (followSetsState.isEmpty()) {
        EmptyOrNoneFound()
    } else {
        val userName by observeUserName(userToAddOrRemove, accountViewModel)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(followSetsState, key = { _, item -> item.identifierTag }) { _, list ->
                FollowSetItem(
                    modifier = Modifier.fillMaxWidth(),
                    listHeader = list.title,
                    listDescription = list.description ?: "",
                    userName = userName,
                    userIsPrivateMember = list.privateMembers.contains(userToAddOrRemove),
                    userIsPublicMember = list.publicMembers.contains(userToAddOrRemove),
                    onRemoveUser = {
                        accountViewModel.runIOCatching {
                            accountViewModel.account.peopleLists.removeUserFromSet(
                                userToAddOrRemove,
                                isPrivate = list.privateMembers.contains(userToAddOrRemove),
                                list.identifierTag,
                                accountViewModel.account,
                            )
                        }
                    },
                    privateMemberSize = list.privateMembers.size,
                    publicMemberSize = list.publicMembers.size,
                    onAddUserToList = { userShouldBePrivate ->
                        accountViewModel.runIOCatching {
                            accountViewModel.account.peopleLists.addUserToSet(
                                userToAddOrRemove,
                                list.identifierTag,
                                userShouldBePrivate,
                                accountViewModel.account,
                            )
                        }
                    },
                )
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}

@Composable
private fun EmptyOrNoneFound() {
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringRes(R.string.follow_set_empty_dialog_msg))
        Spacer(modifier = StdVertSpacer)
    }
}

@Preview
@Composable
fun FollowSetItemMemberPreview() {
    ThemeComparisonColumn {
        FollowSetItem(
            modifier = Modifier.fillMaxWidth(),
            listHeader = "list title",
            listDescription = "desc",
            userName = "User",
            userIsPrivateMember = true,
            userIsPublicMember = true,
            privateMemberSize = 3,
            publicMemberSize = 2,
            onAddUserToList = {},
            onRemoveUser = {},
        )
    }
}

@Preview
@Composable
fun FollowSetItemNotMemberPreview() {
    ThemeComparisonColumn {
        FollowSetItem(
            modifier = Modifier.fillMaxWidth(),
            listHeader = "list title",
            listDescription = "desc",
            userName = "User",
            userIsPrivateMember = false,
            userIsPublicMember = false,
            privateMemberSize = 3,
            publicMemberSize = 2,
            onAddUserToList = {},
            onRemoveUser = {},
        )
    }
}

@Composable
fun FollowSetItem(
    modifier: Modifier = Modifier,
    listHeader: String,
    listDescription: String,
    userName: String,
    userIsPrivateMember: Boolean,
    userIsPublicMember: Boolean,
    publicMemberSize: Int,
    privateMemberSize: Int,
    onAddUserToList: (shouldBePrivateMember: Boolean) -> Unit,
    onRemoveUser: () -> Unit,
) {
    val isUserInList = userIsPrivateMember || userIsPublicMember

    ListItem(
        modifier = modifier,
        headlineContent = {
            Text(listHeader, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(
                modifier = HalfHalfVertPadding,
                horizontalArrangement = SpacedBy5dp,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val text =
                    if (isUserInList) {
                        if (userIsPublicMember) {
                            stringRes(R.string.follow_set_public_presence_indicator, userName)
                        } else {
                            stringRes(R.string.follow_set_private_presence_indicator, userName)
                        }
                    } else {
                        stringRes(R.string.follow_set_absence_indicator2, userName)
                    }

                if (isUserInList) {
                    if (userIsPublicMember) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = text,
                            modifier = Size15Modifier,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else if (userIsPrivateMember) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = text,
                            modifier = Size15Modifier,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.RemoveCircleOutline,
                        contentDescription = text,
                        modifier = Size15Modifier,
                    )
                }
                Text(
                    text = text,
                    overflow = TextOverflow.MiddleEllipsis,
                    maxLines = 1,
                )
            }
        },
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Groups,
                    contentDescription = stringRes(R.string.follow_set_icon_description),
                    modifier = Size50ModifierOffset10,
                )
                DisplayParticipantNumberAndStatus(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    privateMembersSize = privateMemberSize,
                    publicMembersSize = publicMemberSize,
                )
            }
        },
        trailingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val isUserAddTapped = remember { mutableStateOf(false) }
                IconButton(
                    onClick = {
                        if (isUserInList) {
                            onRemoveUser()
                        } else {
                            isUserAddTapped.value = true
                        }
                    },
                    modifier =
                        Modifier
                            .background(
                                color =
                                    if (isUserInList) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                shape = RoundedCornerShape(percent = 80),
                            ),
                ) {
                    if (isUserInList) {
                        Icon(
                            imageVector = Icons.Filled.PersonRemove,
                            contentDescription = stringRes(R.string.remove_user_from_the_list),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = stringRes(R.string.add_user_to_the_list),
                            tint = Color.White,
                        )
                    }
                }

                UserAdditionOptionsMenu(
                    isExpanded = isUserAddTapped.value,
                    onUserAdd = { shouldBePrivateMember ->
                        onAddUserToList(shouldBePrivateMember)
                    },
                    onDismiss = { isUserAddTapped.value = false },
                )
            }
        },
    )
}

@Composable
private fun UserAdditionOptionsMenu(
    isExpanded: Boolean,
    onUserAdd: (asPrivateMember: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = isExpanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = {
                Text(text = stringRes(R.string.follow_set_public_member_add_label))
            },
            onClick = {
                onUserAdd(false)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = {
                Text(text = stringRes(R.string.follow_set_private_member_add_label))
            },
            onClick = {
                onUserAdd(true)
                onDismiss()
            },
        )
    }
}
