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
package com.vitorpamplona.amethyst.ui.note.creators.contentWarning

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun MarkAsSensitiveButton(
    isActive: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        Box(
            Modifier
                .height(20.dp)
                .width(23.dp),
        ) {
            if (!isActive) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = stringRes(R.string.add_content_warning),
                    modifier =
                        Modifier
                            .size(18.dp)
                            .align(Alignment.BottomStart),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = stringRes(R.string.add_content_warning),
                    modifier =
                        Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = stringRes(id = R.string.remove_content_warning),
                    modifier =
                        Modifier
                            .size(18.dp)
                            .align(Alignment.BottomStart),
                    tint = Color.Red,
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = stringRes(id = R.string.remove_content_warning),
                    modifier =
                        Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd),
                    tint = Color.Yellow,
                )
            }
        }
    }
}
