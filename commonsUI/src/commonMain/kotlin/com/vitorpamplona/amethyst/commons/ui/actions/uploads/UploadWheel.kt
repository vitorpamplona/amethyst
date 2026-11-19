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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.service.uploads.UploadStatus
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState

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

/** The colours of an upload's wheel and of the word that names its stage. */
@Immutable
class UploadProgressColors(
    val wheel: Color,
    val track: Color,
    val wordBackground: Color,
    val word: Color,
)

object UploadProgressDefaults {
    /** Over a file's preview, darkened by [UploadProgressOverMedia]'s scrim: white reads on any picture. */
    val overMedia = UploadProgressColors(Color.White, Color.White.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.6f), Color.White)

    /** On the screen's own background, where white would vanish in a light theme. */
    @Composable
    fun onBackground() =
        UploadProgressColors(
            wheel = MaterialTheme.colorScheme.primary,
            track = ProgressIndicatorDefaults.circularDeterminateTrackColor,
            wordBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
            word = MaterialTheme.colorScheme.onSurface,
        )
}

/**
 * One file's upload: the [UploadWheel] and the stage's word under it, or beside it when [inline]
 * (a file's row is one line tall). The word sits in its own pill, so a long translation
 * ("Téléversement en cours") widens the pill instead of spilling out of the wheel.
 */
@Composable
fun UploadProgress(
    status: UploadStatus,
    modifier: Modifier = Modifier,
    colors: UploadProgressColors = UploadProgressDefaults.overMedia,
    wheelSize: Dp = 44.dp,
    inline: Boolean = false,
) {
    val wheelAndWord =
        @Composable {
            UploadWheel(status, Modifier.size(wheelSize), color = colors.wheel, trackColor = colors.track)
            Spacer(Modifier.size(8.dp))
            Text(
                uploadingStateLabel(status.state),
                color = colors.word,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = if (inline) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.wordBackground).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    if (inline) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) { wheelAndWord() }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { wheelAndWord() }
    }
}

/** A file's preview darkened, with its [UploadProgress] in the middle: the cards, the Studio, the upload grids. */
@Composable
fun UploadProgressOverMedia(
    status: UploadStatus,
    modifier: Modifier = Modifier,
    wheelSize: Dp = 44.dp,
    inline: Boolean = false,
) {
    // On a file's row the name fills the left: the progress takes the right end, where Remove sits.
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = if (inline) Alignment.CenterEnd else Alignment.Center) {
        UploadProgress(status, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), wheelSize = wheelSize, inline = inline)
    }
}
