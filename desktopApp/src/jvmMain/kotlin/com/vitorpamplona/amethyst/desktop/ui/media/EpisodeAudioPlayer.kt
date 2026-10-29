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
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodeCommand
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodeControl
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodePlayhead
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.media.MediaPlaybackState

/**
 * How near the end a track that stops playing must last have been to count as played to the end.
 * The engine just goes idle there and may report any position after, so the last position heard
 * while playing decides, not the one after it stopped. The state is read every quarter second.
 */
private const val ENDED_WITHIN_MS = 3_000L

/**
 * The desktop's player for an episode that keeps the listener's place: the audio card, started at
 * [startAtSeconds] and played at [speed], obeying [control], reporting [GlobalMediaPlayer]'s
 * playhead for this file to [onPlayhead]. When another file takes over the engine, this one
 * reports itself paused once.
 */
@Composable
fun EpisodeAudioPlayer(
    audioUrl: String,
    mimeType: String?,
    title: String?,
    image: String?,
    note: Note,
    startAtSeconds: Long?,
    speed: Float,
    control: EpisodeControl,
    onPlayhead: (EpisodePlayhead) -> Unit,
) {
    val start by rememberUpdatedState(startAtSeconds)
    val report by rememberUpdatedState(onPlayhead)
    val rate by rememberUpdatedState(speed)

    LaunchedEffect(audioUrl, control) {
        control.commands.collect { command ->
            val playing = GlobalMediaPlayer.audioState.value
            val isThis = playing.url == audioUrl
            when (command) {
                is EpisodeCommand.Seek -> {
                    if (isThis && !command.play && !playing.isPlaying) {
                        GlobalMediaPlayer.seekAudioMillis(command.millis)
                    } else {
                        GlobalMediaPlayer.playAudio(audioUrl, command.millis)
                    }
                }
                EpisodeCommand.Play -> if (!isThis || !playing.isPlaying) GlobalMediaPlayer.playAudio(audioUrl, if (isThis) null else start?.times(1000))
                EpisodeCommand.Pause -> if (isThis) GlobalMediaPlayer.pauseAudio()
            }
        }
    }

    // The engine starts each file at 1x: the speed is set again whenever this file plays at another.
    LaunchedEffect(audioUrl) {
        GlobalMediaPlayer.audioState.collect { state ->
            if (state.url == audioUrl && state.isPlaying && state.speed != rate) GlobalMediaPlayer.setAudioSpeed(rate)
        }
    }
    LaunchedEffect(speed) {
        if (GlobalMediaPlayer.audioState.value.url == audioUrl) GlobalMediaPlayer.setAudioSpeed(speed)
    }

    LaunchedEffect(audioUrl) {
        var last: EpisodePlayhead? = null
        GlobalMediaPlayer.audioState.collect { state ->
            val playhead = episodePlayhead(audioUrl, state, last)
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

/**
 * Where [audioUrl] is, from the engine's [state] and the playhead reported before it ([last]), or
 * null when there is nothing new to say. Another file in the engine pauses this one, once.
 */
internal fun episodePlayhead(
    audioUrl: String,
    state: MediaPlaybackState,
    last: EpisodePlayhead?,
): EpisodePlayhead? {
    if (state.url != audioUrl) return last?.takeIf { it.isPlaying }?.copy(isPlaying = false)
    val duration = state.duration.takeIf { it > 0 }
    val playedToTheEnd =
        !state.isPlaying &&
            !state.isBuffering &&
            duration != null &&
            last?.let { it.hasEnded || (it.isPlaying && it.positionMillis >= duration - ENDED_WITHIN_MS) } == true
    return if (playedToTheEnd && duration != null) {
        EpisodePlayhead(positionMillis = duration, durationMillis = duration, isPlaying = false, hasEnded = true)
    } else {
        EpisodePlayhead(positionMillis = state.currentTime, durationMillis = duration, isPlaying = state.isPlaying, hasEnded = false)
    }
}
