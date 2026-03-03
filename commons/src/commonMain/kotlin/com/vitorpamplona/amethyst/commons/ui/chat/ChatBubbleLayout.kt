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
package com.vitorpamplona.amethyst.commons.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleMaxSizeModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeMe
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeThem
import com.vitorpamplona.amethyst.commons.ui.theme.ChatHalfHalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.ChatPaddingInnerQuoteModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ChatPaddingModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ChatRowColSpacing5dp
import com.vitorpamplona.amethyst.commons.ui.theme.MessageBubbleLimits
import com.vitorpamplona.amethyst.commons.ui.theme.chatBubbleBackground
import com.vitorpamplona.amethyst.commons.ui.theme.chatBubbleDraftBackground
import com.vitorpamplona.amethyst.commons.ui.theme.chatBubbleMeBackground

/**
 * Shared chat bubble layout used by both Android and Desktop.
 *
 * Renders a chat message bubble with appropriate shape, color, and alignment
 * depending on whether the message is from the logged-in user or another user.
 *
 * Content is provided via composable lambdas (slots) so platform-specific
 * implementations can plug in their own action menus, author lines, detail
 * rows, and message content.
 *
 * @param isLoggedInUser Whether this message was sent by the current user
 * @param isDraft Whether this message is a draft
 * @param innerQuote Whether this bubble is rendered as an inner quote (reply preview)
 * @param isComplete Whether the UI is in "complete" mode (always show details)
 * @param hasDetailsToShow Whether there are details (reactions, zaps) to display
 * @param drawAuthorInfo Whether to show the author avatar and name
 * @param parentBackgroundColor Background color of the parent (for compositing)
 * @param onClick Called on tap; return true if the click was consumed
 * @param onAuthorClick Called when the author line is tapped
 * @param actionMenu Composable for the long-press action menu popup
 * @param detailRow Composable for the detail row (time, reactions, relays)
 * @param drawAuthorLine Composable for the author avatar + name row
 * @param inner Composable for the message body content; receives the resolved background color
 */
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
    val loggedInColors = MaterialTheme.colorScheme.chatBubbleMeBackground
    val otherColors = MaterialTheme.colorScheme.chatBubbleBackground
    val defaultBackground = MaterialTheme.colorScheme.background
    val draftColor = MaterialTheme.colorScheme.chatBubbleDraftBackground

    val bgColor =
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
                color = bgColor.value,
                shape = if (isLoggedInUser) ChatBubbleShapeMe else ChatBubbleShapeThem,
                modifier = clickableModifier,
            ) {
                Column(modifier = MessageBubbleLimits, verticalArrangement = ChatRowColSpacing5dp) {
                    if (drawAuthorInfo) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (isLoggedInUser) Arrangement.End else Arrangement.Start,
                            modifier = ChatHalfHalfVertPadding.clickable(onClick = onAuthorClick),
                        ) {
                            drawAuthorLine()
                        }
                    }

                    inner(bgColor)

                    if (showDetails.value) {
                        detailRow()
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
