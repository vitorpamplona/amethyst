/**
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
package com.vitorpamplona.amethyst.ui.actions.uploads

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Animated expanding circles that pulse outward from the recording button
 */
@Composable
fun ExpandingCirclesAnimation(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expanding_circles")

    // First circle animation
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2.5f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "circle1_scale",
    )

    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "circle1_alpha",
    )

    // Second circle animation (offset by 500ms)
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2.5f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, delayMillis = 500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "circle2_scale",
    )

    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, delayMillis = 500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "circle2_alpha",
    )

    // Third circle animation (offset by 1000ms)
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2.5f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, delayMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "circle3_scale",
    )

    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, delayMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "circle3_alpha",
    )

    if (!isRecording) return

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Circle 1
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .scale(scale1)
                    .alpha(alpha1)
                    .background(primaryColor.copy(alpha = 0.3f), CircleShape),
        )

        // Circle 2
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .scale(scale2)
                    .alpha(alpha2)
                    .background(primaryColor.copy(alpha = 0.2f), CircleShape),
        )

        // Circle 3
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .scale(scale3)
                    .alpha(alpha3)
                    .background(primaryColor.copy(alpha = 0.1f), CircleShape),
        )
    }
}

/**
 * Floating recording indicator showing elapsed time
 */
@Composable
fun FloatingRecordingIndicator(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    elapsedSeconds: Int,
) {
    if (!isRecording) return

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            // Pulsing red dot
            val infiniteTransition = rememberInfiniteTransition(label = "recording_dot")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.5f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "dot_alpha",
            )

            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = "Recording",
                tint = Color.White,
                modifier =
                    Modifier
                        .alpha(dotAlpha)
                        .padding(end = 8.dp),
            )

            Text(
                text = "Recording ${formatSecondsToTime(elapsedSeconds)}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
