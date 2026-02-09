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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.membershipManagement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list.BookmarkMembershipStatusAndNumberDisplay
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.HalfHalfVertPadding
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer

@Composable
fun BookmarkGroupManagementItem(
    modifier: Modifier = Modifier,
    listTitle: String,
    isPrivateMemberBookmark: Boolean,
    isPublicMemberBookmark: Boolean,
    totalPostBookmarkSize: Int,
    totalArticleBookmarkSize: Int,
    onClick: () -> Unit,
    onAddBookmarkToGroup: (shouldBookmarkBePrivate: Boolean) -> Unit,
    onRemoveBookmarkFromGroup: () -> Unit,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = listTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            BookmarkStatusInList(isPublicMemberBookmark, isPrivateMemberBookmark)
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CollectionsBookmark,
                    contentDescription = stringRes(R.string.bookmark_list_icon_label),
                    modifier = Size50Modifier,
                )
                Spacer(StdVertSpacer)
                BookmarkMembershipStatusAndNumberDisplay(
                    postBookmarksSize = totalPostBookmarkSize,
                    articleBookmarksSize = totalArticleBookmarkSize,
                )
            }
        },
        trailingContent = {
            val isBookmarkInGroup = isPrivateMemberBookmark || isPublicMemberBookmark
            BookmarkManagementOptions(
                isBookmarkInList = isBookmarkInGroup,
                onAddBookmark = onAddBookmarkToGroup,
                onRemoveBookmark = onRemoveBookmarkFromGroup,
            )
        },
    )
}

@Composable
fun BookmarkStatusInList(
    isPublicMemberBookmark: Boolean,
    isPrivateMemberBookmark: Boolean,
) {
    Row(
        modifier = HalfHalfVertPadding,
        horizontalArrangement = SpacedBy5dp,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text =
            if (isPublicMemberBookmark) {
                stringRes(R.string.public_bookmark_presence_indicator)
            } else if (isPrivateMemberBookmark) {
                stringRes(R.string.private_bookmark_presence_indicator)
            } else {
                stringRes(R.string.bookmark_absence_indicator)
            }

        val icon =
            if (isPublicMemberBookmark) {
                Icons.Outlined.Public
            } else if (isPrivateMemberBookmark) {
                Icons.Outlined.Lock
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
fun BookmarkManagementOptions(
    isBookmarkInList: Boolean,
    onAddBookmark: (shouldBePrivate: Boolean) -> Unit,
    onRemoveBookmark: () -> Unit,
) {
    val isBookmarkAddTapped = remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = {
                if (isBookmarkInList) {
                    onRemoveBookmark()
                } else {
                    isBookmarkAddTapped.value = true
                }
            },
            modifier =
                Modifier
                    .background(
                        color =
                            if (isBookmarkInList) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        shape = RoundedCornerShape(percent = 80),
                    ),
        ) {
            if (isBookmarkInList) {
                Icon(
                    imageVector = Icons.Filled.BookmarkRemove,
                    contentDescription = stringRes(R.string.bookmark_remove_action_desc),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.BookmarkAdd,
                    contentDescription = stringRes(R.string.bookmark_add_action_desc),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        DropdownMenu(
            expanded = isBookmarkAddTapped.value,
            onDismissRequest = { isBookmarkAddTapped.value = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(text = stringRes(R.string.public_bookmark_add_action_label))
                },
                onClick = {
                    onAddBookmark(false)
                    isBookmarkAddTapped.value = false
                },
            )
            DropdownMenuItem(
                text = {
                    Text(text = stringRes(R.string.private_bookmark_add_action_label))
                },
                onClick = {
                    onAddBookmark(true)
                    isBookmarkAddTapped.value = false
                },
            )
        }
    }
}
