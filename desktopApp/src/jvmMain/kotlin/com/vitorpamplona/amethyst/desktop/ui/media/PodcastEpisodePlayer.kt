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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.PodcastSoundbites
import com.vitorpamplona.amethyst.commons.ui.note.types.PodcastChaptersView
import com.vitorpamplona.amethyst.commons.ui.note.types.getAudioMetaWithWaveform
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import com.vitorpamplona.quartz.podcasts.PodcastEpisode

/**
 * The desktop's inline player for a podcast episode (NIP-F4 kind 54 or Podcasting-2.0 kind 30054):
 * the audio player shaped by [borderModifier], then the episode's soundbites and chapters, which
 * play the episode from where they start. Android's per-minute value-for-value streaming control
 * has no desktop counterpart yet.
 */
@Composable
fun PodcastEpisodePlayer(
    audio: PodcastAudio,
    note: Note,
    title: String?,
    borderModifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    // The episode's own waveform when its imeta ships one; NIP-F4 usually doesn't, so the card
    // falls back to the synthetic bars seeded by the note, as on Android.
    // Keyed on the event, not the note: an addressable 30054 episode keeps its Note across edits.
    val event = note.event
    val waveform =
        remember(event) {
            event
                ?.getAudioMetaWithWaveform()
                ?.waveform
                ?.let { WaveformData(it) }
        }
    val soundbites = remember(event) { (event as? PodcastEpisode)?.episodeSoundbites().orEmpty() }
    val chaptersUrl = remember(event) { (event as? PodcastEpisode)?.episodeChaptersUrl() }
    val playFrom: (Long) -> Unit = remember(audio.url) { { startMillis -> GlobalMediaPlayer.playAudio(audio.url, startMillis) } }

    Column(Modifier.fillMaxWidth()) {
        AudioPlayer(
            url = audio.url,
            modifier = borderModifier.fillMaxWidth(),
            title = title,
            mimeType = audio.mediaType,
            waveform = waveform,
            seed = note.idHex,
            // borderModifier already gives the strip its outline.
            shape = RectangleShape,
        )

        PodcastSoundbites(soundbites, playFrom)

        chaptersUrl?.let { url ->
            PodcastChaptersView(
                chaptersUrl = url,
                onSeek = playFrom,
                accountViewModel = accountViewModel,
            )
        }
    }
}
