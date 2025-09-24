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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import kotlin.let

@Composable
fun CustomSetItem(
    modifier: Modifier = Modifier,
    followSet: FollowSet,
    onFollowSetClick: () -> Unit,
    onFollowSetRename: (String) -> Unit,
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
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = {
                            Text(text = "${followSet.profileList.size}")
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
                Spacer(modifier = StdVertSpacer)
                Text(
                    followSet.description ?: "",
                    fontWeight = FontWeight.Light,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
            }

            followSet.visibility.let {
                val text by derivedStateOf {
                    when (it) {
                        ListVisibility.Public -> stringRes(context, R.string.follow_set_type_public)
                        ListVisibility.Private -> stringRes(context, R.string.follow_set_type_private)
                        ListVisibility.Mixed -> stringRes(context, R.string.follow_set_type_mixed)
                    }
                }
                Column(
                    modifier = Modifier.padding(top = 15.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                when (it) {
                                    ListVisibility.Public -> R.drawable.ic_public
                                    ListVisibility.Private -> R.drawable.lock
                                    ListVisibility.Mixed -> R.drawable.format_list_bulleted_type
                                },
                            ),
                        contentDescription = stringRes(R.string.follow_set_type_description, text),
                    )
                    Text(text, color = Color.Gray, fontWeight = FontWeight.Light)
                }
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
                onListRename = onFollowSetRename,
                onListDelete = onFollowSetDelete,
            )
        }
    }
}

@Composable
fun SetOptionsButton(
    modifier: Modifier = Modifier,
    followSetName: String,
    onListRename: (String) -> Unit,
    onListDelete: () -> Unit,
) {
    val isMenuOpen = remember { mutableStateOf(false) }

    ClickableBox(
        onClick = { isMenuOpen.value = true },
    ) {
        VerticalDotsIcon()

        SetOptionsMenu(
            listName = followSetName,
            isExpanded = isMenuOpen.value,
            onDismiss = { isMenuOpen.value = false },
            onListRename = onListRename,
            onDelete = onListDelete,
        )
    }
}

@Composable
private fun SetOptionsMenu(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    listName: String,
    onListRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isRenameDialogOpen = remember { mutableStateOf(false) }
    val renameString = remember { mutableStateOf("") }

    DropdownMenu(
        expanded = isExpanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = {
                Text(text = stringRes(R.string.quick_action_delete))
            },
            onClick = {
                onDelete()
            },
        )
        DropdownMenuItem(
            text = {
                Text(text = stringRes(R.string.follow_set_rename_btn_label))
            },
            onClick = {
                isRenameDialogOpen.value = true
                onDismiss()
            },
        )
    }

    if (isRenameDialogOpen.value) {
        RenameDialog(
            currentName = listName,
            newName = renameString.value,
            onStringRenameChange = {
                renameString.value = it
            },
            onDismissDialog = { isRenameDialogOpen.value = false },
            onListRename = {
                onListRename(renameString.value)
            },
        )
    }
}

@Composable
private fun RenameDialog(
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
