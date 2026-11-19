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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.service.uploads.UploadStatus
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier

/**
 * How far one file's upload is: a ring that fills, or one that spins while the file is
 * [UploadingState.Sending], when its bytes are on their way and how far they got is unknown.
 * Every upload view draws this one, so each file of a batch shows its own.
 */
@Composable
fun UploadWheel(
    status: UploadStatus,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    trackColor: Color = ProgressIndicatorDefaults.circularDeterminateTrackColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
) {
    if (status.state is UploadingState.Sending) {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth, trackColor = trackColor)
    } else {
        val animated by animateFloatAsState(status.progress.toFloat(), ProgressIndicatorDefaults.ProgressAnimationSpec)
        CircularProgressIndicator(progress = { animated }, modifier = modifier, color = color, strokeWidth = strokeWidth, trackColor = trackColor)
    }
}

/** The [UploadWheel] with the stage's word inside it: one file's upload over its preview, or on its own. */
@Composable
fun LabelledUploadWheel(
    status: UploadStatus,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(55.dp), contentAlignment = Alignment.Center) {
        UploadWheel(
            status = status,
            modifier = Size55Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.background),
            strokeWidth = 5.dp,
        )
        Text(
            uploadingStateLabel(status.state),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}
