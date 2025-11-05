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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.NewPeopleListCreationDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.SpacedBy10dp
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
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
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.follow_set_man_dialog_title), nav::popBack)
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
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

    FollowSetsCreationMenu(
        userName = userToAddOrRemove.toBestDisplayName(),
        onSetCreate = { setName, memberShouldBePrivate, description ->
            accountViewModel.runIOCatching {
                accountViewModel.account.peopleLists.addFollowList(
                    listName = setName,
                    listDescription = description,
                    isPrivate = memberShouldBePrivate,
                    member = userToAddOrRemove,
                    account = accountViewModel.account,
                )
            }
        },
    )
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
            userName = "User",
            userIsPrivateMember = true,
            userIsPublicMember = true,
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
            userName = "User",
            userIsPrivateMember = false,
            userIsPublicMember = false,
            onAddUserToList = {},
            onRemoveUser = {},
        )
    }
}

@Composable
fun FollowSetItem(
    modifier: Modifier = Modifier,
    listHeader: String,
    userName: String,
    userIsPrivateMember: Boolean,
    userIsPublicMember: Boolean,
    onAddUserToList: (shouldBePrivateMember: Boolean) -> Unit,
    onRemoveUser: () -> Unit,
) {
    val isUserInList = userIsPrivateMember || userIsPublicMember
    Row(
        modifier = modifier.padding(all = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = SpacedBy10dp,
    ) {
        Column(
            modifier = modifier.weight(1f),
            verticalArrangement = SpacedBy5dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = SpacedBy5dp,
            ) {
                Icon(
                    painter = painterResource(R.drawable.format_list_bulleted_type),
                    contentDescription = stringRes(R.string.follow_set_icon_description),
                )
                Text(listHeader, fontWeight = FontWeight.Bold)
            }

            FilterChip(
                selected = true,
                onClick = {},
                label = {
                    Text(
                        text =
                            if (isUserInList) {
                                if (userIsPublicMember) {
                                    stringRes(R.string.follow_set_public_presence_indicator, userName)
                                } else {
                                    stringRes(R.string.follow_set_private_presence_indicator, userName)
                                }
                            } else {
                                stringRes(R.string.follow_set_absence_indicator, userName)
                            },
                    )
                },
                leadingIcon =
                    if (isUserInList) {
                        {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                shape = ButtonBorder,
            )
        }

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
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null,
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
    }
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

@Composable
fun FollowSetsCreationMenu(
    modifier: Modifier = Modifier,
    userName: String,
    onSetCreate: (setName: String, memberShouldBePrivate: Boolean, description: String?) -> Unit,
) {
    val isListAdditionDialogOpen = remember { mutableStateOf(false) }
    val isPrivateOptionTapped = remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(vertical = 30.dp),
    ) {
        Text(
            stringRes(R.string.follow_set_creation_menu_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.5f),
            thickness = 3.dp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = StdVertSpacer)
        FollowSetCreationItem(
            setIsPrivate = false,
            userName = userName,
            onClick = {
                isListAdditionDialogOpen.value = true
            },
        )
        FollowSetCreationItem(
            setIsPrivate = true,
            userName = userName,
            onClick = {
                isPrivateOptionTapped.value = true
                isListAdditionDialogOpen.value = true
            },
        )
    }

    if (isListAdditionDialogOpen.value) {
        NewPeopleListCreationDialog(
            onDismiss = {
                isListAdditionDialogOpen.value = false
                isPrivateOptionTapped.value = false
            },
            onCreateList = { name, description ->
                onSetCreate(name, isPrivateOptionTapped.value, description)
            },
        )
    }
}

@Composable
fun FollowSetCreationItem(
    modifier: Modifier = Modifier,
    setIsPrivate: Boolean,
    userName: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val setTypeLabel = stringRes(context, if (setIsPrivate) R.string.follow_set_type_private else R.string.follow_set_type_public)

    HorizontalDivider(thickness = DividerThickness)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color =
                        ButtonDefaults
                            .filledTonalButtonColors()
                            .containerColor
                            .copy(alpha = 0.2f),
                ).padding(vertical = 15.dp)
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringRes(
                        R.string.follow_set_creation_item_label,
                        setTypeLabel,
                    ),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = StdHorzSpacer)
            Icon(
                painter =
                    painterResource(
                        if (setIsPrivate) R.drawable.lock_plus else R.drawable.earth_plus,
                    ),
                contentDescription = null,
            )
        }
        Spacer(modifier = StdVertSpacer)
        Text(
            stringRes(
                R.string.follow_set_creation_item_description,
                userName,
                setTypeLabel.lowercase(Locale.current.platformLocale),
            ),
            fontWeight = FontWeight.Light,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            color = Color.Gray,
        )
    }
    HorizontalDivider(thickness = DividerThickness)
}
