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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkList
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.BookmarkType
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Font10SP
import com.vitorpamplona.amethyst.ui.theme.NoSoTinyBorders
import com.vitorpamplona.amethyst.ui.theme.Size10Modifier
import com.vitorpamplona.amethyst.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.SpacedBy2dp
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer

@Composable
fun BookmarkGroupItem(
    modifier: Modifier = Modifier,
    bookmarkList: LabeledBookmarkList,
    onClick: (bookmarkItemType: BookmarkType) -> Unit,
    onRename: (String) -> Unit,
    onDescriptionChange: (String?) -> Unit,
    onClone: (customName: String?, customDescription: String?) -> Unit,
    onDelete: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.clickable(onClick = { isExpanded = !isExpanded }),
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ListItem(
                headlineContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(bookmarkList.title, maxLines = 1, overflow = TextOverflow.Ellipsis)

                        Column(
                            modifier = NoSoTinyBorders,
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.End,
                        ) {
                            BookmarkGroupOptionsButton(
                                bookmarkGroupName = bookmarkList.title,
                                bookmarkGroupDescription = bookmarkList.description,
                                onGroupRename = onRename,
                                onGroupDescriptionChange = onDescriptionChange,
                                onGroupCloneCreate = onClone,
                                onGroupDelete = onDelete,
                            )
                        }
                    }
                },
                supportingContent = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            bookmarkList.description ?: "",
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                        )
                    }
                },
                leadingContent = {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CollectionsBookmark,
                            contentDescription = "Icon for bookmark group",
                            modifier = Size40Modifier,
                        )
                        Spacer(StdVertSpacer)
                        BookmarkMembershipStatusAndNumberDisplay(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            privateBookmarksSize = bookmarkList.privateBookmarks.size,
                            publicBookmarksSize = bookmarkList.publicBookmarks.size,
                        )
                    }
                },
            )
            if (isExpanded) {
                BookmarkGroupActions(
                    modifier = Modifier.fillMaxWidth(),
                    openPostBookmarks = { onClick(BookmarkType.PostBookmark) },
                    openArticleBookmarks = { onClick(BookmarkType.ArticleBookmark) },
                )
            }
        }
    }
}

@Composable
private fun BookmarkGroupActions(
    modifier: Modifier = Modifier,
    openPostBookmarks: () -> Unit = {},
    openArticleBookmarks: () -> Unit = {},
    openLinkBookmarks: () -> Unit = {},
    openHashtagBookmarks: () -> Unit = {},
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.SpaceAround,
        itemVerticalAlignment = Alignment.CenterVertically,
        maxLines = 2,
        maxItemsInEachRow = 2,
    ) {
        FilledTonalButton(
            onClick = openPostBookmarks,
        ) {
            Icon(
                painter = painterResource(R.drawable.post),
                contentDescription = null,
            )
            Text("View Posts")
        }
        FilledTonalButton(
            onClick = openArticleBookmarks,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Article,
                contentDescription = null,
            )
            Text("View Articles")
        }
        FilledTonalButton(
            onClick = openLinkBookmarks,
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
            )
            Text("View Links")
        }
        FilledTonalButton(
            onClick = openHashtagBookmarks,
        ) {
            Icon(
                imageVector = Icons.Outlined.Numbers,
                contentDescription = null,
            )
            Text("View Hashtags")
        }
    }
}

@Composable
fun BookmarkMembershipStatusAndNumberDisplay(
    modifier: Modifier,
    privateBookmarksSize: Int,
    publicBookmarksSize: Int,
) {
    Row(
        modifier = modifier.offset(y = (-5).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = SpacedBy5dp,
    ) {
        if (privateBookmarksSize <= 0 && publicBookmarksSize <= 0) {
            Text(
                text = stringRes(R.string.follow_set_empty_label2),
                fontSize = Font10SP,
            )
        } else {
            if (privateBookmarksSize > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = SpacedBy2dp,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        modifier = Size10Modifier,
                        contentDescription = null,
                    )
                    Text(
                        text = privateBookmarksSize.toString(),
                        fontSize = Font10SP,
                    )
                }
            }
            if (publicBookmarksSize > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = SpacedBy2dp,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        modifier = Size10Modifier,
                        contentDescription = null,
                    )
                    Text(
                        text = publicBookmarksSize.toString(),
                        fontSize = Font10SP,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkGroupOptionsButton(
    modifier: Modifier = Modifier,
    bookmarkGroupName: String,
    bookmarkGroupDescription: String?,
    onGroupRename: (String) -> Unit,
    onGroupDescriptionChange: (String?) -> Unit,
    onGroupCloneCreate: (optionalName: String?, optionalDec: String?) -> Unit,
    onGroupDelete: () -> Unit,
) {
    val isMenuOpen = remember { mutableStateOf(false) }

    ClickableBox(
        onClick = { isMenuOpen.value = true },
    ) {
        VerticalDotsIcon()

        GroupOptionsMenu(
            groupName = bookmarkGroupName,
            groupDescription = bookmarkGroupDescription,
            isExpanded = isMenuOpen.value,
            onDismiss = { isMenuOpen.value = false },
            onGroupRename = onGroupRename,
            onGroupDescriptionChange = onGroupDescriptionChange,
            onGroupClone = onGroupCloneCreate,
            onDelete = onGroupDelete,
        )
    }
}

@Composable
private fun GroupOptionsMenu(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    groupName: String,
    groupDescription: String?,
    onGroupRename: (String) -> Unit,
    onGroupDescriptionChange: (String?) -> Unit,
    onGroupClone: (optionalNewName: String?, optionalNewDesc: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isRenameDialogOpen = remember { mutableStateOf(false) }
    val renameString = remember { mutableStateOf("") }

    val isDescriptionModDialogOpen = remember { mutableStateOf(false) }

    val isCopyDialogOpen = remember { mutableStateOf(false) }
    val optionalCloneName = remember { mutableStateOf<String?>(null) }
    val optionalCloneDescription = remember { mutableStateOf<String?>(null) }

    DropdownMenu(
        expanded = isExpanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = {
                Text(text = stringRes(R.string.follow_set_rename_btn_label))
            },
            onClick = {
                isRenameDialogOpen.value = true
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = {
                Text(text = stringRes(R.string.follow_set_desc_modify_label))
            },
            onClick = {
                isDescriptionModDialogOpen.value = true
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = {
                Text(text = stringRes(R.string.follow_set_copy_action_btn_label))
            },
            onClick = {
                isCopyDialogOpen.value = true
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = {
                Text(text = stringRes(R.string.quick_action_delete))
            },
            onClick = {
                onDelete()
            },
        )
    }

    if (isRenameDialogOpen.value) {
        GroupRenameDialog(
            currentName = groupName,
            newName = renameString.value,
            onStringRenameChange = {
                renameString.value = it
            },
            onDismissDialog = { isRenameDialogOpen.value = false },
            onGroupRename = {
                onGroupRename(renameString.value)
            },
        )
    }

    if (isDescriptionModDialogOpen.value) {
        GroupModifyDescriptionDialog(
            currentDescription = groupDescription,
            onDismissDialog = { isDescriptionModDialogOpen.value = false },
            onModifyDescription = onGroupDescriptionChange,
        )
    }

    if (isCopyDialogOpen.value) {
        GroupCloneDialog(
            optionalNewName = optionalCloneName.value,
            optionalNewDesc = optionalCloneDescription.value,
            onCloneNameChange = {
                optionalCloneName.value = it
            },
            onCloneDescChange = {
                optionalCloneDescription.value = it
            },
            onCloneCreate = { name, description ->
                onGroupClone(optionalCloneName.value, optionalCloneDescription.value)
            },
            onDismiss = { isCopyDialogOpen.value = false },
        )
    }
}

@Composable
private fun GroupRenameDialog(
    modifier: Modifier = Modifier,
    currentName: String,
    newName: String,
    onStringRenameChange: (String) -> Unit,
    onDismissDialog: () -> Unit,
    onGroupRename: (String) -> Unit,
) {
    val renameIndicator =
        buildAnnotatedString {
            append(stringRes(R.string.follow_set_rename_dialog_indicator_first_part) + " ")
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Normal,
                    fontSize = 15.sp,
                ),
            ) {
                append("\"" + currentName + "\"")
            }
            append(" " + stringRes(R.string.follow_set_rename_dialog_indicator_second_part))
        }

    AlertDialog(
        onDismissRequest = onDismissDialog,
        title = {
            Text(text = stringRes(R.string.follow_set_rename_btn_label))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Size5dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = renameIndicator,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic,
                )
                TextField(
                    value = newName,
                    onValueChange = onStringRenameChange,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onGroupRename(newName)
                    onDismissDialog()
                },
            ) { Text(text = stringRes(R.string.rename)) }
        },
        dismissButton = {
            Button(onClick = onDismissDialog) { Text(text = stringRes(R.string.cancel)) }
        },
    )
}

@Composable
private fun GroupModifyDescriptionDialog(
    modifier: Modifier = Modifier,
    currentDescription: String?,
    onDismissDialog: () -> Unit,
    onModifyDescription: (String?) -> Unit,
) {
    val updatedDescription = remember { mutableStateOf<String?>(null) }

    val modifyIndicatorLabel =
        if (currentDescription == null) {
            stringRes(R.string.follow_set_empty_desc_label)
        } else {
            buildAnnotatedString {
                append(stringRes(R.string.follow_set_current_desc_label) + " ")
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Normal,
                        fontSize = 15.sp,
                    ),
                ) {
                    append("\"" + currentDescription + "\"")
                }
            }.text
        }

    AlertDialog(
        onDismissRequest = onDismissDialog,
        title = {
            Text(text = stringRes(R.string.follow_set_desc_modify_label))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Size5dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = modifyIndicatorLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic,
                )
                TextField(
                    value = updatedDescription.value ?: "",
                    onValueChange = { updatedDescription.value = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onModifyDescription(updatedDescription.value)
                    onDismissDialog()
                },
            ) { Text(text = stringRes(R.string.follow_set_desc_modify_btn_label)) }
        },
        dismissButton = {
            Button(onClick = onDismissDialog) { Text(text = stringRes(R.string.cancel)) }
        },
    )
}

@Composable
private fun GroupCloneDialog(
    modifier: Modifier = Modifier,
    optionalNewName: String?,
    optionalNewDesc: String?,
    onCloneNameChange: (String?) -> Unit,
    onCloneDescChange: (String?) -> Unit,
    onCloneCreate: (customName: String?, customDescription: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Clone Bookmark Group",
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Size5dp),
            ) {
                Text(
                    text = stringRes(R.string.follow_set_copy_indicator_description),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic,
                )
                // For the group clone name
                TextField(
                    value = optionalNewName ?: "",
                    onValueChange = onCloneNameChange,
                    label = {
                        Text(text = stringRes(R.string.follow_set_copy_name_label))
                    },
                )
                Spacer(modifier = DoubleVertSpacer)
                // For the group clone description
                TextField(
                    value = optionalNewDesc ?: "",
                    onValueChange = onCloneDescChange,
                    label = {
                        Text(text = stringRes(R.string.follow_set_copy_desc_label))
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCloneCreate(optionalNewName, optionalNewDesc)
                    onDismiss()
                },
            ) {
                Text(stringRes(R.string.follow_set_copy_action_btn_label))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
            ) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}
