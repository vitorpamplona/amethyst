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
package com.vitorpamplona.amethyst.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.layouts.NoteComposeLayout as CommonsNoteComposeLayout

// Backward-compat wrapper: NoteComposeLayout moved to commons
// Preview functions stay here (depend on ThemeComparisonColumn from amethyst.ui.theme)

@Composable
fun NoteComposeLayout(
    modifier: Modifier = Modifier,
    addPadding: Boolean = true,
    showAuthorColumn: Boolean = true,
    showSecondRow: Boolean = false,
    showContentSpacer: Boolean = true,
    authorPicture: @Composable () -> Unit,
    relayBadges: @Composable () -> Unit,
    firstRow: @Composable () -> Unit,
    secondRow: @Composable () -> Unit,
    noteContent: @Composable () -> Unit,
    reactionsRow: @Composable () -> Unit,
) = CommonsNoteComposeLayout(
    modifier,
    addPadding,
    showAuthorColumn,
    showSecondRow,
    showContentSpacer,
    authorPicture,
    relayBadges,
    firstRow,
    secondRow,
    noteContent,
    reactionsRow,
)

@Composable
@Preview
private fun NoteComposeLayoutPreview() {
    ThemeComparisonColumn {
        NoteComposeLayoutPreviewCard()
    }
}

@Composable
private fun NoteComposeLayoutPreviewCard() {
    NoteComposeLayout(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        showSecondRow = true,
        authorPicture = {
            Box(
                Modifier
                    .size(55.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF9575CD)),
            )
        },
        relayBadges = {
            Text("R1  R2  R3", fontSize = 10.sp, color = Color.Gray)
        },
        firstRow = {
            Text(
                "Alice  @alice  2h  ...",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondRow = {
            Text(
                "alice@nostr.com",
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
            )
        },
        noteContent = {
            Text(
                "This is a sample note content that demonstrates the custom " +
                    "NoteComposeLayout with all slots populated. The layout handles " +
                    "author picture, relay badges, header rows, content, and reactions.",
            )
        },
        reactionsRow = {
            Text("  Reply 3     Boost 5     Like 12     Zap 1.2k", fontSize = 12.sp, color = Color.Gray)
        },
    )
}

@Composable
@Preview
private fun NoteComposeLayoutBoostedPreview() {
    ThemeComparisonColumn {
        NoteComposeLayout(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
            addPadding = false,
            showAuthorColumn = false,
            authorPicture = {},
            relayBadges = {},
            firstRow = {
                Text("Bob boosted  2h  ...")
            },
            secondRow = {},
            noteContent = {
                Text("Boosted note content without author column or padding.")
            },
            reactionsRow = {
                Text("  Reply     Boost     Like     Zap", fontSize = 12.sp, color = Color.Gray)
            },
        )
    }
}
