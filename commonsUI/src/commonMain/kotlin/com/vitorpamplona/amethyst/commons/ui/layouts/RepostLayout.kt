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
package com.vitorpamplona.amethyst.commons.ui.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.note.RepostIcon
import com.vitorpamplona.amethyst.commons.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

@Composable
@Preview
private fun GenericRepostSectionPreview() {
    GenericRepostLayout(
        baseAuthorPicture = { Text("ab") },
        repostAuthorPicture = { Text("cd") },
    )
}

/** The pictures of a repost's author and of the reposted note's author, [size] wide. */
@Composable
fun GenericRepostLayout(
    baseAuthorPicture: @Composable () -> Unit,
    repostAuthorPicture: @Composable () -> Unit,
    size: Dp = Size55dp,
) {
    if (size == Size55dp) {
        GenericRepostLayout(Size55Modifier, Size35Modifier, Size18Modifier, baseAuthorPicture, repostAuthorPicture)
    } else {
        // Keeps the proportions of the 55dp layout: two 35dp pictures and an 18dp icon.
        val modifiers =
            remember(size) {
                Triple(Modifier.size(size), Modifier.size(size * 35 / 55), Modifier.size(size * 18 / 55))
            }
        GenericRepostLayout(modifiers.first, modifiers.second, modifiers.third, baseAuthorPicture, repostAuthorPicture)
    }
}

@Composable
private fun GenericRepostLayout(
    outer: Modifier,
    picture: Modifier,
    icon: Modifier,
    baseAuthorPicture: @Composable () -> Unit,
    repostAuthorPicture: @Composable () -> Unit,
) {
    Box(modifier = outer) {
        Box(remember(picture) { picture.align(Alignment.TopStart) }) { baseAuthorPicture() }

        Box(
            remember(icon) { icon.align(Alignment.BottomStart).padding(1.dp) },
        ) {
            RepostIcon(modifier = icon, MaterialTheme.colorScheme.placeholderText)
        }

        Box(
            remember(picture) { picture.align(Alignment.BottomEnd) },
            contentAlignment = Alignment.BottomEnd,
        ) {
            repostAuthorPicture()
        }
    }
}
