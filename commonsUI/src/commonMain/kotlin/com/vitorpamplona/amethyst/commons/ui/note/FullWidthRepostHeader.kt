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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

// The pictures sit in the same 35dp column as a full-width note's author picture, so the names
// of a repost line up with the names of every other post.
private val PictureColumnWidth = 35.dp
private val PictureToRowGap = 10.dp
private val PictureSize = 22.dp
private val RepostIconModifier = Modifier.size(12.dp)

/**
 * The header of a repost in the full-width layouts: two first rows, the booster's and the reposted
 * note's, each beside its author's picture. The pictures step down and to the right like the
 * author column's repost picture, and each is centered on its own row, so they stay with their
 * names at any font size.
 */
@Composable
fun FullWidthRepostHeader(
    repost: Note,
    reposted: Note,
    isPinned: Boolean,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    val repostedEditState = observeEdits(baseNote = reposted, accountViewModel = accountViewModel)
    val iconColor = MaterialTheme.colorScheme.placeholderText

    Layout(
        contents =
            listOf(
                { NoteAuthorPicture(repost, PictureSize, accountViewModel = accountViewModel, nav = nav) },
                { NoteAuthorPicture(reposted, PictureSize, accountViewModel = accountViewModel, nav = nav) },
                { RepostIcon(modifier = RepostIconModifier, iconColor) },
                { FirstUserInfoRow(repost, false, isPinned, editState, accountViewModel, nav, moreOptions) },
                { FirstUserInfoRow(reposted, false, false, repostedEditState, accountViewModel, nav) },
            ),
    ) { (boosterPicture, authorPicture, icon, boosterRow, authorRow), constraints ->
        val columnWidth = PictureColumnWidth.roundToPx()
        val rowX = columnWidth + PictureToRowGap.roundToPx()
        val rowConstraints = Constraints(maxWidth = (constraints.maxWidth - rowX).coerceAtLeast(0))

        val booster = boosterPicture.firstOrNull()?.measure(Constraints())
        val author = authorPicture.firstOrNull()?.measure(Constraints())
        val repostIcon = icon.firstOrNull()?.measure(Constraints())
        val firstRow = boosterRow.firstOrNull()?.measure(rowConstraints)
        val secondRow = authorRow.firstOrNull()?.measure(rowConstraints)

        val firstRowHeight = firstRow?.height ?: 0
        val firstCenter = firstRowHeight / 2
        val secondCenter = firstRowHeight + (secondRow?.height ?: 0) / 2
        val authorY = secondCenter - (author?.height ?: 0) / 2
        val height = maxOf(firstRowHeight + (secondRow?.height ?: 0), authorY + (author?.height ?: 0))

        layout(constraints.maxWidth, height) {
            booster?.placeRelative(0, firstCenter - booster.height / 2)
            repostIcon?.placeRelative(0, secondCenter - repostIcon.height / 2)
            author?.placeRelative(columnWidth - author.width, authorY)
            firstRow?.placeRelative(rowX, 0)
            secondRow?.placeRelative(rowX, firstRowHeight)
        }
    }
}
