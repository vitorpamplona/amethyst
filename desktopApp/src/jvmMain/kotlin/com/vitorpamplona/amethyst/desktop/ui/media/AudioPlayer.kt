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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.PlayableLayout
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.audio.mediaFormatLabel
import com.vitorpamplona.amethyst.commons.audio.player.AudioCardInfo
import com.vitorpamplona.amethyst.commons.audio.player.AudioPlaybackUi
import com.vitorpamplona.amethyst.commons.audio.player.PlayableMediaCard
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.media.MediaPlaybackState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The desktop's inline audio player: the shared audio card (the waveform as the seek bar, or
 * [artworkUrl] with the same scrubber over its foot) driven by [GlobalMediaPlayer]'s one audio
 * engine. The card shows this file's progress only while it is the one loaded there; any other
 * card sits idle at the start.
 *
 * [waveform] is the file's own amplitude envelope; without one the card draws the synthetic bars
 * seeded by [seed] (the URL by default), the same ones Android draws for that file. [shape] is the
 * card's outline; a caller that clips the card itself passes `RectangleShape`.
 */
@Composable
fun AudioPlayer(
    url: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    artist: String? = null,
    mimeType: String? = null,
    waveform: WaveformData? = null,
    artworkUrl: String? = null,
    seed: String = url,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    // Only this file's playback: every other audio card on screen would otherwise recompose on each
    // clock tick of whichever track is playing.
    val ownPlayback =
        remember(url) {
            GlobalMediaPlayer.audioState.map { it.playbackFor(url) }.distinctUntilChanged()
        }
    val playback by ownPlayback.collectAsState(GlobalMediaPlayer.audioState.value.playbackFor(url))

    val info =
        remember(url, title, artist, mimeType, waveform, artworkUrl, seed) {
            AudioCardInfo(
                title = title,
                artist = artist,
                format = mediaFormatLabel(mimeType, url),
                sizeBytes = null,
                seed = seed,
                waveform = waveform?.wave,
                artworkUrl = artworkUrl,
            )
        }

    PlayableMediaCard(
        layout = if (artworkUrl != null) PlayableLayout.AUDIO_COVER else PlayableLayout.AUDIO_WAVEFORM,
        info = info,
        playback = playback,
        onPlayPause = {
            if (GlobalMediaPlayer.audioState.value.url == url) GlobalMediaPlayer.toggleAudioPlayPause() else GlobalMediaPlayer.playAudio(url)
        },
        // The card only offers seeking once it knows the duration, which only the loaded file has.
        onSeek = { if (GlobalMediaPlayer.audioState.value.url == url) GlobalMediaPlayer.seekAudio(it) },
        modifier = modifier,
        shape = shape,
    )
}

private fun MediaPlaybackState.playbackFor(url: String): AudioPlaybackUi =
    if (this.url == url) {
        AudioPlaybackUi(isPlaying = isPlaying, positionMs = currentTime, durationMs = duration.takeIf { it > 0L })
    } else {
        AudioPlaybackUi.Idle
    }
