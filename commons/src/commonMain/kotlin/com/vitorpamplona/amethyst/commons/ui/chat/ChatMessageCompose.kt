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

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.model.Note

/**
 * Shared chat message composable that provides structure without platform-specific dependencies.
 *
 * This is a simplified, slot-based version of the Android ChatroomMessageCompose.
 * Platform implementations provide the actual content via lambda slots, allowing
 * both Android and Desktop to use the same bubble layout with different content renderers.
 *
 * The Android app can wrap this or continue using its own deeper implementation.
 * The Desktop app uses this directly with simpler content slots.
 *
 * @param note The chat message note
 * @param isLoggedInUser Whether this note was authored by the current user
 * @param isDraft Whether this is a draft message
 * @param innerQuote Whether this is rendered as a quoted reply
 * @param isComplete Whether to always show detail row
 * @param hasDetailsToShow Whether there are reactions/zaps to display
 * @param drawAuthorInfo Whether to show author avatar and name
 * @param parentBackgroundColor Parent background for color compositing
 * @param onClick Called on tap; return true if consumed
 * @param onAuthorClick Called when author info is tapped
 * @param actionMenu Long-press action menu popup slot
 * @param authorLine Author avatar and name slot
 * @param detailRow Detail row slot (time, reactions)
 * @param messageContent Message body slot; receives the resolved background color
 */
@Composable
fun ChatMessageCompose(
    note: Note,
    isLoggedInUser: Boolean,
    isDraft: Boolean,
    innerQuote: Boolean = false,
    isComplete: Boolean = true,
    hasDetailsToShow: Boolean = false,
    drawAuthorInfo: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    onClick: () -> Boolean = { false },
    onAuthorClick: () -> Unit = {},
    actionMenu: @Composable (onDismiss: () -> Unit) -> Unit = {},
    authorLine: @Composable () -> Unit = {},
    detailRow: @Composable () -> Unit = {},
    messageContent: @Composable (MutableState<Color>) -> Unit,
) {
    ChatBubbleLayout(
        isLoggedInUser = isLoggedInUser,
        isDraft = isDraft,
        innerQuote = innerQuote,
        isComplete = isComplete,
        hasDetailsToShow = hasDetailsToShow,
        drawAuthorInfo = drawAuthorInfo,
        parentBackgroundColor = parentBackgroundColor,
        onClick = onClick,
        onAuthorClick = onAuthorClick,
        actionMenu = actionMenu,
        drawAuthorLine = authorLine,
        detailRow = detailRow,
    ) { bgColor ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            messageContent(bgColor)
        }
    }
}
