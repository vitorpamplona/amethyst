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
package com.vitorpamplona.myapplication

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisappearingFloatingButton(
    scrollBehavior: BottomAppBarScrollBehavior,
    content: @Composable (BoxScope.() -> Unit),
) {
    // We calculate the scale/alpha based on how much the bar is expanded
    // 1.0 = fully visible, 0.0 = fully hidden
    val progress = (1f - scrollBehavior.state.collapsedFraction).coerceAtLeast(0.001f)

    Box(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // Adjust the height of the layout so the FAB doesn't leave a "hole"
                    val currentHeight = (placeable.height * progress).toInt()
                    layout(placeable.width, currentHeight) {
                        placeable.placeRelative(0, 0)
                    }
                }.graphicsLayer {
                    this.scaleX = progress
                    this.scaleY = progress
                    this.alpha = progress
                    // Clip the content as it shrinks to prevent overflow
                    clip = true
                },
        content = content,
    )
}
