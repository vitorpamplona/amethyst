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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

private val PictureToRowGap = 10.dp

private enum class RepostHeaderSlot { Rows, Pictures }

/**
 * The repost picture's content for the size the rows give it, kept between measure passes so the
 * picture only recomposes when that size changes.
 */
private class RepostPictureSlot(
    val repost: Note,
    val reposted: Note,
    val accountViewModel: AccountViewModel,
    val nav: INav,
) {
    private var size: Dp = Dp.Unspecified
    private var content: @Composable () -> Unit = {}

    fun at(size: Dp): @Composable () -> Unit {
        if (size != this.size) {
            this.size = size
            content = { RepostNoteAuthorPicture(repost, reposted, size, accountViewModel, nav) }
        }
        return content
    }
}

/**
 * The header of a repost in the full-width layouts: two first rows, the booster's and the reposted
 * note's, beside the author column's repost picture ([RepostNoteAuthorPicture]). The picture is
 * sized to the two rows, so its booster sits by the first row and its author by the second at any
 * font size.
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

    // Held across measure passes: a slot whose content lambda changes is recomposed on every pass.
    val rows: @Composable () -> Unit =
        remember(repost, reposted, isPinned, editState, repostedEditState, accountViewModel, nav, moreOptions) {
            {
                FirstUserInfoRow(repost, false, isPinned, editState, accountViewModel, nav, moreOptions)
                FirstUserInfoRow(reposted, false, false, repostedEditState, accountViewModel, nav)
            }
        }
    val picture = remember(repost, reposted, accountViewModel, nav) { RepostPictureSlot(repost, reposted, accountViewModel, nav) }

    SubcomposeLayout { constraints ->
        // The rows are one line each, so their height does not depend on the width the picture
        // leaves them: measure them first and size the picture to them.
        val rowMeasurables = subcompose(RepostHeaderSlot.Rows, rows)
        val rowsHeight = rowMeasurables.sumOf { it.minIntrinsicHeight(constraints.maxWidth) }

        val picturePlaceable =
            subcompose(RepostHeaderSlot.Pictures, picture.at(rowsHeight.toDp()))
                .firstOrNull()
                ?.measure(Constraints())

        val rowX = (picturePlaceable?.width ?: 0) + PictureToRowGap.roundToPx()
        val rowConstraints = Constraints(maxWidth = (constraints.maxWidth - rowX).coerceAtLeast(0))
        val placedRows = rowMeasurables.map { it.measure(rowConstraints) }
        val height = maxOf(placedRows.sumOf { it.height }, picturePlaceable?.height ?: 0)

        layout(constraints.maxWidth, height) {
            picturePlaceable?.placeRelative(0, (height - picturePlaceable.height) / 2)
            var y = (height - placedRows.sumOf { it.height }) / 2
            placedRows.forEach {
                it.placeRelative(rowX, y)
                y += it.height
            }
        }
    }
}
