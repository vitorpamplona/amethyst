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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.vitorpamplona.amethyst.model.nip51Lists.followSets.FollowSet
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer

@Composable
fun CustomSetItem(
    modifier: Modifier = Modifier,
    followSet: FollowSet,
    onFollowSetClick: () -> Unit,
    onFollowSetRename: (String) -> Unit,
    onFollowSetDescriptionChange: (String?) -> Unit,
    onFollowSetClone: (customName: String?, customDescription: String?) -> Unit,
    onFollowSetDelete: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier =
            modifier
                .clickable(onClick = onFollowSetClick),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(bottom = 12.dp)
                    .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(followSet.title, fontWeight = FontWeight.Bold)
                    Spacer(modifier = StdHorzSpacer)
                    if (followSet.publicProfiles.isEmpty() && followSet.privateProfiles.isEmpty()) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = {
                                Text(text = stringRes(R.string.follow_set_empty_label))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                )
                            },
                            shape = ButtonBorder,
                        )
                    }
                    if (followSet.publicProfiles.isNotEmpty()) {
                        val publicMemberSize = followSet.publicProfiles.size
                        val membersLabel =
                            stringRes(
                                context,
                                if (publicMemberSize == 1) {
                                    R.string.follow_set_single_member_label
                                } else {
                                    R.string.follow_set_multiple_member_label
                                },
                            )
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = {
                                Text(text = "$publicMemberSize")
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_public),
                                    contentDescription = null,
                                )
                            },
                            shape = ButtonBorder,
                        )
                        Spacer(modifier = StdHorzSpacer)
                    }
                    if (followSet.privateProfiles.isNotEmpty()) {
                        val privateMemberSize = followSet.privateProfiles.size
                        val membersLabel =
                            stringRes(
                                context,
                                if (privateMemberSize == 1) {
                                    R.string.follow_set_single_member_label
                                } else {
                                    R.string.follow_set_multiple_member_label
                                },
                            )
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = {
                                Text(text = "$privateMemberSize")
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.lock),
                                    contentDescription = null,
                                )
                            },
                            shape = ButtonBorder,
                        )
                    }
                }
                Spacer(modifier = StdVertSpacer)
                Text(
                    followSet.description ?: "",
                    fontWeight = FontWeight.Light,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .padding(start = 5.dp)
                    .padding(vertical = 7.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.End,
        ) {
            SetOptionsButton(
                followSetName = followSet.title,
                followSetDescription = followSet.description,
                onSetRename = onFollowSetRename,
                onSetDescriptionChange = onFollowSetDescriptionChange,
                onSetCloneCreate = onFollowSetClone,
                onListDelete = onFollowSetDelete,
            )
        }
    }
}

@Composable
private fun SetOptionsButton(
    modifier: Modifier = Modifier,
    followSetName: String,
    followSetDescription: String?,
    onSetRename: (String) -> Unit,
    onSetDescriptionChange: (String?) -> Unit,
    onSetCloneCreate: (optionalName: String?, optionalDec: String?) -> Unit,
    onListDelete: () -> Unit,
) {
    val isMenuOpen = remember { mutableStateOf(false) }

    ClickableBox(
        onClick = { isMenuOpen.value = true },
    ) {
        VerticalDotsIcon()

        SetOptionsMenu(
            setName = followSetName,
            setDescription = followSetDescription,
            isExpanded = isMenuOpen.value,
            onDismiss = { isMenuOpen.value = false },
            onSetRename = onSetRename,
            onSetDescriptionChange = onSetDescriptionChange,
            onSetClone = onSetCloneCreate,
            onDelete = onListDelete,
        )
    }
}

@Composable
private fun SetOptionsMenu(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    setName: String,
    setDescription: String?,
    onSetRename: (String) -> Unit,
    onSetDescriptionChange: (String?) -> Unit,
    onSetClone: (optionalNewName: String?, optionalNewDesc: String?) -> Unit,
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
                Text(text = "Modify description")
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
        SetRenameDialog(
            currentName = setName,
            newName = renameString.value,
            onStringRenameChange = {
                renameString.value = it
            },
            onDismissDialog = { isRenameDialogOpen.value = false },
            onListRename = {
                onSetRename(renameString.value)
            },
        )
    }

    if (isDescriptionModDialogOpen.value) {
        SetModifyDescriptionDialog(
            currentDescription = setDescription,
            onDismissDialog = { isDescriptionModDialogOpen.value = false },
            onModifyDescription = onSetDescriptionChange,
        )
    }

    if (isCopyDialogOpen.value) {
        SetCloneDialog(
            optionalNewName = optionalCloneName.value,
            optionalNewDesc = optionalCloneDescription.value,
            onCloneNameChange = {
                optionalCloneName.value = it
            },
            onCloneDescChange = {
                optionalCloneDescription.value = it
            },
            onCloneCreate = { name, description ->
                onSetClone(optionalCloneName.value, optionalCloneDescription.value)
            },
            onDismiss = { isCopyDialogOpen.value = false },
        )
    }
}

@Composable
private fun SetRenameDialog(
    modifier: Modifier = Modifier,
    currentName: String,
    newName: String,
    onStringRenameChange: (String) -> Unit,
    onDismissDialog: () -> Unit,
    onListRename: (String) -> Unit,
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
                    onListRename(newName)
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
private fun SetModifyDescriptionDialog(
    modifier: Modifier = Modifier,
    currentDescription: String?,
    onDismissDialog: () -> Unit,
    onModifyDescription: (String?) -> Unit,
) {
    val updatedDescription = remember { mutableStateOf<String?>(null) }

    val modifyIndicatorLabel =
        if (currentDescription == null) {
            "This list doesn't have a description"
        } else {
            buildAnnotatedString {
                append("Current description: ")
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
            Text(text = "Modify description")
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
            ) { Text(text = "Modify") }
        },
        dismissButton = {
            Button(onClick = onDismissDialog) { Text(text = stringRes(R.string.cancel)) }
        },
    )
}

@Composable
private fun SetCloneDialog(
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
                    text = stringRes(R.string.follow_set_copy_dialog_title),
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
                // For the set clone name
                TextField(
                    value = optionalNewName ?: "",
                    onValueChange = onCloneNameChange,
                    label = {
                        Text(text = stringRes(R.string.follow_set_copy_name_label))
                    },
                )
                Spacer(modifier = DoubleVertSpacer)
                // For the set clone description
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
