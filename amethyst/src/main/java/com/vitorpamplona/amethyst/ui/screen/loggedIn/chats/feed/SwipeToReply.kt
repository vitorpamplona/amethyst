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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val SwipeThreshold = 80.dp

@Composable
fun SwipeToReplyWrapper(
    isLoggedInUser: Boolean,
    onSwipeToReply: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var dragAmount by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { SwipeThreshold.toPx() }

    Box {
        // Reply icon that appears during swipe
        val swipeProgress = (abs(offsetX.value) / thresholdPx).coerceIn(0f, 1f)
        if (swipeProgress > 0.1f) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Reply",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = swipeProgress),
                modifier = Modifier
                    .align(if (isLoggedInUser) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 16.dp)
                    .alpha(swipeProgress),
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dragAmount) >= thresholdPx) {
                                onSwipeToReply()
                            }
                            scope.launch {
                                offsetX.animateTo(0f, animationSpec = tween(200))
                            }
                            dragAmount = 0f
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, animationSpec = tween(200))
                            }
                            dragAmount = 0f
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            // For logged-in user (right-aligned), allow swipe left (negative)
                            // For others (left-aligned), allow swipe right (positive)
                            val newOffset = if (isLoggedInUser) {
                                (offsetX.value + delta).coerceIn(-thresholdPx * 1.2f, 0f)
                            } else {
                                (offsetX.value + delta).coerceIn(0f, thresholdPx * 1.2f)
                            }
                            dragAmount = newOffset
                            scope.launch {
                                offsetX.snapTo(newOffset)
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
