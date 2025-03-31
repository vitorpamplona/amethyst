/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.FollowSet
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.ListVisibility
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun FollowSetsActionMenu(
    userHex: String,
    followLists: List<FollowSet>,
    modifier: Modifier = Modifier,
    addUser: (followListItemIndex: Int, list: FollowSet) -> Unit,
    removeUser: (followListItemIndex: Int) -> Unit,
) {
    val (isMenuOpen, setMenuValue) = remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()

    Column {
        TextButton(
            onClick = { setMenuValue(true) },
            shape = ButtonBorder.copy(topStart = CornerSize(0f), bottomStart = CornerSize(0f)),
            colors =
                ButtonDefaults
                    .buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            contentPadding = ZeroPadding,
        ) {
            Icon(
                imageVector = if (isMenuOpen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = "",
            )
        }

//        Icon(
//            imageVector = if (isMenuOpen.value) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
//            contentDescription = "",
//            modifier =
//                Modifier
//                    .fillMaxHeight()
//                    .background(
//                        color = MaterialTheme.colorScheme.primary,
//                        shape = ButtonBorder.copy(topStart = CornerSize(0f), bottomStart = CornerSize(0f)),
//                    ).border(
//                        width = Dp.Hairline,
//                        color = MaterialTheme.colorScheme.primary,
//                        shape =
//                            ButtonBorder
//                                .copy(topStart = CornerSize(0f), bottomStart = CornerSize(0f)),
//                    ).clickable(role = Role.DropdownList) {
//                        isMenuOpen.value = !isMenuOpen.value
//                    },
//        )

        DropdownMenu(
            expanded = isMenuOpen,
            onDismissRequest = {
                uiScope.launch {
                    setMenuValue(false)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            properties = PopupProperties(usePlatformDefaultWidth = true),
        ) {
            DropDownMenuHeader(headerText = "Add to lists")
            followLists.forEachIndexed { index, list ->
                Spacer(StdVertSpacer)
                DropdownMenuItem(
                    text = {
                        FollowSetItem(
                            modifier = Modifier.fillMaxWidth(),
                            listHeader = list.title,
                            listVisibility = list.visibility,
                            isUserInList = list.profileList.contains(userHex),
                            onRemoveUser = {
                                removeUser(index)
                            },
                            onAddUser = {
                                println("List contains user -> ${list.profileList.contains(userHex)}")
                                println("Adding user to List -> ${list.title}")
                                addUser(index, list)
                                println("List contains user -> ${list.profileList.contains(userHex)}")
                            },
                        )
                    },
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DropDownMenuHeader(
    modifier: Modifier = Modifier,
    headerText: String,
) {
    Column {
        DropdownMenuItem(
            text = {
                Text(text = headerText, fontWeight = FontWeight.SemiBold)
            },
            onClick = {},
            enabled = false,
        )
        HorizontalDivider()
    }
}

fun generateFollowLists(): List<FollowSet> =
    List(10) { index: Int ->
        FollowSet(
            identifierTag = UUID.randomUUID().toString(),
            title = "List No $index",
            description = null,
            visibility =
                when {
                    index % 2 == 0 -> ListVisibility.Private
                    index in listOf(3, 7, 9) -> ListVisibility.Mixed
                    else -> ListVisibility.Public
                },
            profileList = emptySet(),
        )
    }

@Composable
fun FollowSetItem(
    modifier: Modifier = Modifier,
    listHeader: String,
    listVisibility: ListVisibility,
    isUserInList: Boolean,
    onAddUser: () -> Unit,
    onRemoveUser: () -> Unit,
) {
    Row(
        modifier =
            modifier
//                .clickable(onClick = onAddUser)
                .border(
                    width = Dp.Hairline,
                    color = Color.Gray,
                    shape = RoundedCornerShape(percent = 20),
                ).padding(all = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(listHeader, fontWeight = FontWeight.Bold)
            Spacer(modifier = StdVertSpacer)
            Row {
                FilterChip(
                    selected = isUserInList,
                    enabled = isUserInList,
                    onClick = {},
                    label = {
                        Text(text = if (isUserInList) "In List" else "Not in List")
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
                Spacer(modifier = StdHorzSpacer)
                AssistChip(
                    onClick = {
                        if (isUserInList) onRemoveUser() else onAddUser()
                    },
                    label = {
                        Text(text = if (isUserInList) "Remove" else "Add")
                    },
                    leadingIcon = {
                        if (isUserInList) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    shape = ButtonBorder,
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor =
                                if (isUserInList) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                        ),
                    border =
                        AssistChipDefaults
                            .assistChipBorder(
                                enabled = true,
                                borderColor =
                                    if (!isUserInList) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    },
                            ),
                )
            }
        }

        listVisibility.let {
            val text by derivedStateOf {
                when (it) {
                    ListVisibility.Public -> "Public"
                    ListVisibility.Private -> "Private"
                    ListVisibility.Mixed -> "Mixed"
                }
            }

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter =
                        painterResource(
                            when (it) {
                                ListVisibility.Public -> R.drawable.ic_public
                                ListVisibility.Private -> R.drawable.incognito
                                ListVisibility.Mixed -> R.drawable.format_list_bulleted_type
                            },
                        ),
                    contentDescription = "Icon for $text List",
                )
                Text(text, color = Color.Gray)
            }
        }
    }
}
