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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Height4dpModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatHeaderLayout(
    channelPicture: @Composable () -> Unit,
    firstRow: @Composable RowScope.() -> Unit,
    secondRow: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Size55Modifier) { channelPicture() }

        Spacer(modifier = DoubleHorzSpacer)

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                firstRow()
            }

            Spacer(modifier = Height4dpModifier)

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                secondRow()
            }
        }
    }
}
