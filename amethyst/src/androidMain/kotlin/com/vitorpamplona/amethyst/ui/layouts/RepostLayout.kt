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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.note.RepostIcon
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
@Preview
private fun GenericRepostSectionPreview() {
    GenericRepostLayout(
        baseAuthorPicture = { Text("ab") },
        repostAuthorPicture = { Text("cd") },
    )
}

@Composable
fun GenericRepostLayout(
    baseAuthorPicture: @Composable () -> Unit,
    repostAuthorPicture: @Composable () -> Unit,
) {
    Box(modifier = Size55Modifier) {
        Box(remember { Size35Modifier.align(Alignment.TopStart) }) { baseAuthorPicture() }

        Box(
            remember { Size18Modifier.align(Alignment.BottomStart).padding(1.dp) },
        ) {
            RepostIcon(modifier = Size18Modifier, MaterialTheme.colorScheme.placeholderText)
        }

        Box(
            remember { Size35Modifier.align(Alignment.BottomEnd) },
            contentAlignment = Alignment.BottomEnd,
        ) {
            repostAuthorPicture()
        }
    }
}
