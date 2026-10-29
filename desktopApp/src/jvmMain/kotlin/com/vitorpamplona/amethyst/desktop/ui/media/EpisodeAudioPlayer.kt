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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodePlayhead
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer

/** How near the end a stopped track must be to count as played to the end: the engine just goes idle there. */
private const val ENDED_FRACTION = 0.99

/**
 * The desktop's player for an episode that keeps the listener's place: the audio card, started at
 * [startAtSeconds], reporting [GlobalMediaPlayer]'s playhead for this file to [onPlayhead]. When
 * another file takes over the engine, this one reports itself paused once.
 */
@Composable
fun EpisodeAudioPlayer(
    audioUrl: String,
    mimeType: String?,
    title: String?,
    image: String?,
    note: Note,
    startAtSeconds: Long?,
    onPlayhead: (EpisodePlayhead) -> Unit,
) {
    val start by rememberUpdatedState(startAtSeconds)
    val report by rememberUpdatedState(onPlayhead)

    LaunchedEffect(audioUrl) {
        var last: EpisodePlayhead? = null
        GlobalMediaPlayer.audioState.collect { state ->
            val playhead =
                if (state.url == audioUrl) {
                    val duration = state.duration.takeIf { it > 0 }
                    EpisodePlayhead(
                        positionMillis = state.currentTime,
                        durationMillis = duration,
                        isPlaying = state.isPlaying,
                        hasEnded = !state.isPlaying && duration != null && state.currentTime >= duration * ENDED_FRACTION,
                    )
                } else {
                    last?.takeIf { it.isPlaying }?.copy(isPlaying = false)
                }
            if (playhead != null) {
                last = playhead
                report(playhead)
            }
        }
    }

    AudioPlayer(
        url = audioUrl,
        modifier = Modifier.fillMaxWidth(),
        title = title,
        mimeType = mimeType,
        artworkUrl = image,
        seed = note.idHex,
        startMillis = { start?.times(1000) },
    )
}
