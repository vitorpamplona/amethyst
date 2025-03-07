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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleMaxSizeModifier
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleShapeMe
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleShapeThem
import com.vitorpamplona.amethyst.ui.theme.ChatPaddingInnerQuoteModifier
import com.vitorpamplona.amethyst.ui.theme.ChatPaddingModifier
import com.vitorpamplona.amethyst.ui.theme.HalfHalfVertPadding
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeightChat
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing5dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.chatBackground
import com.vitorpamplona.amethyst.ui.theme.chatDraftBackground
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.messageBubbleLimits

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubbleLayout(
    isLoggedInUser: Boolean,
    isDraft: Boolean,
    innerQuote: Boolean,
    isComplete: Boolean,
    hasDetailsToShow: Boolean,
    drawAuthorInfo: Boolean,
    parentBackgroundColor: MutableState<Color>? = null,
    onClick: () -> Boolean,
    onAuthorClick: () -> Unit,
    actionMenu: @Composable (onDismiss: () -> Unit) -> Unit,
    detailRow: @Composable () -> Unit,
    drawAuthorLine: @Composable () -> Unit,
    inner: @Composable (MutableState<Color>) -> Unit,
) {
    val loggedInColors = MaterialTheme.colorScheme.mediumImportanceLink
    val otherColors = MaterialTheme.colorScheme.chatBackground
    val defaultBackground = MaterialTheme.colorScheme.background
    val draftColor = MaterialTheme.colorScheme.chatDraftBackground

    val backgroundBubbleColor =
        remember {
            if (isLoggedInUser) {
                if (isDraft) {
                    mutableStateOf(
                        draftColor.compositeOver(parentBackgroundColor?.value ?: defaultBackground),
                    )
                } else {
                    mutableStateOf(
                        loggedInColors.compositeOver(parentBackgroundColor?.value ?: defaultBackground),
                    )
                }
            } else {
                mutableStateOf(otherColors.compositeOver(parentBackgroundColor?.value ?: defaultBackground))
            }
        }

    Row(
        modifier = if (innerQuote) ChatPaddingInnerQuoteModifier else ChatPaddingModifier,
        horizontalArrangement = if (isLoggedInUser) Arrangement.End else Arrangement.Start,
    ) {
        val popupExpanded = remember { mutableStateOf(false) }

        val showDetails =
            remember {
                mutableStateOf(
                    if (isComplete) {
                        true
                    } else {
                        hasDetailsToShow
                    },
                )
            }

        val clickableModifier =
            remember {
                Modifier.combinedClickable(
                    onClick = {
                        if (!onClick()) {
                            if (!isComplete) {
                                showDetails.value = !showDetails.value
                            }
                        }
                    },
                    onLongClick = { popupExpanded.value = true },
                )
            }

        Row(
            horizontalArrangement = if (isLoggedInUser) Arrangement.End else Arrangement.Start,
            modifier = if (innerQuote) Modifier else ChatBubbleMaxSizeModifier,
        ) {
            Surface(
                color = backgroundBubbleColor.value,
                shape = if (isLoggedInUser) ChatBubbleShapeMe else ChatBubbleShapeThem,
                modifier = clickableModifier,
            ) {
                Column(modifier = messageBubbleLimits, verticalArrangement = RowColSpacing5dp) {
                    if (drawAuthorInfo) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (isLoggedInUser) Arrangement.End else Arrangement.Start,
                            modifier = HalfHalfVertPadding.clickable(onClick = onAuthorClick),
                        ) {
                            drawAuthorLine()
                        }
                    }

                    inner(backgroundBubbleColor)

                    if (showDetails.value) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = ReactionRowHeightChat,
                        ) {
                            detailRow()
                        }
                    }
                }
            }
        }

        if (popupExpanded.value) {
            actionMenu {
                popupExpanded.value = false
            }
        }
    }
}

@Preview
@Composable
private fun BubblePreview() {
    val backgroundBubbleColor =
        remember {
            mutableStateOf<Color>(Color.Transparent)
        }

    Column {
        ChatBubbleLayout(
            isLoggedInUser = false,
            isDraft = false,
            innerQuote = false,
            isComplete = true,
            hasDetailsToShow = true,
            drawAuthorInfo = true,
            parentBackgroundColor = backgroundBubbleColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray),
                        )
                    },
                    name = {
                        Text("Someone else", fontWeight = FontWeight.Bold)
                    },
                )
            },
            detailRow = { Text("Relays and Actions") },
        ) { backgroundBubbleColor ->
            Text("This is my note")
        }

        ChatBubbleLayout(
            isLoggedInUser = true,
            isDraft = false,
            innerQuote = false,
            isComplete = true,
            hasDetailsToShow = true,
            drawAuthorInfo = true,
            parentBackgroundColor = backgroundBubbleColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape),
                        )
                    },
                    name = {
                        Text("Me", fontWeight = FontWeight.Bold)
                    },
                )
            },
            detailRow = { Text("Relays and Actions") },
        ) { backgroundBubbleColor ->
            Text("This is a very long long loong note")
        }

        ChatBubbleLayout(
            isLoggedInUser = true,
            isDraft = true,
            innerQuote = false,
            isComplete = true,
            hasDetailsToShow = true,
            drawAuthorInfo = true,
            parentBackgroundColor = backgroundBubbleColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape),
                        )
                    },
                    name = {
                        Text("Me", fontWeight = FontWeight.Bold)
                    },
                )
            },
            detailRow = { Text("Relays and Actions") },
        ) { backgroundBubbleColor ->
            Text("This is a draft note")
        }

        ChatBubbleLayout(
            isLoggedInUser = true,
            isDraft = false,
            innerQuote = false,
            isComplete = false,
            hasDetailsToShow = false,
            drawAuthorInfo = false,
            parentBackgroundColor = backgroundBubbleColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape),
                        )
                    },
                    name = {
                        Text("Me", fontWeight = FontWeight.Bold)
                    },
                )
            },
            detailRow = { Text("Relays and Actions") },
        ) { backgroundBubbleColor ->
            Text("Short note")
        }
    }
}
