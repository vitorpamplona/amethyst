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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.DisplayParticipantNumberAndStatus
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.HalfHalfVertPadding
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50ModifierOffset10
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

@Preview
@Composable
fun FollowPackAndUserMemberPreview() {
    ThemeComparisonColumn {
        FollowPackAndUserItem(
            modifier = Modifier.fillMaxWidth(),
            listHeader = "list title",
            userName = "User",
            isMember = true,
            memberSize = 2,
            onAddUserToList = {},
            onRemoveUser = {},
        )
    }
}

@Preview
@Composable
fun FollowPackAndUserNotMemberPreview() {
    ThemeComparisonColumn {
        FollowPackAndUserItem(
            modifier = Modifier.fillMaxWidth(),
            listHeader = "list title",
            userName = "User",
            isMember = false,
            memberSize = 2,
            onAddUserToList = {},
            onRemoveUser = {},
        )
    }
}

@Composable
fun FollowPackAndUserItem(
    modifier: Modifier = Modifier,
    listHeader: String,
    userName: String,
    isMember: Boolean,
    memberSize: Int,
    onAddUserToList: () -> Unit,
    onRemoveUser: () -> Unit,
) {
    ListItem(
        modifier = modifier,
        headlineContent = {
            Text(
                text = listHeader,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            UserStatusInList(userName, isMember)
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
                    privateMembersSize = 0,
                    publicMembersSize = memberSize,
                )
            }
        },
        trailingContent = {
            UserAdditionOptions(isMember, onAddUserToList, onRemoveUser)
        },
    )
}

@Composable
private fun UserStatusInList(
    userName: String,
    isMember: Boolean,
) {
    Row(
        modifier = HalfHalfVertPadding,
        horizontalArrangement = SpacedBy5dp,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text =
            if (isMember) {
                stringRes(R.string.follow_set_public_presence_indicator, userName)
            } else {
                stringRes(R.string.follow_set_absence_indicator2, userName)
            }

        val icon =
            if (isMember) {
                Icons.Outlined.Public
            } else {
                Icons.Outlined.RemoveCircleOutline
            }

        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Size15Modifier,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            overflow = TextOverflow.MiddleEllipsis,
            maxLines = 1,
        )
    }
}

@Composable
private fun UserAdditionOptions(
    isUserInList: Boolean,
    onAddUserToList: () -> Unit,
    onRemoveUser: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = {
                if (isUserInList) {
                    onRemoveUser()
                } else {
                    onAddUserToList()
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
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = stringRes(R.string.add_user_to_the_list),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
