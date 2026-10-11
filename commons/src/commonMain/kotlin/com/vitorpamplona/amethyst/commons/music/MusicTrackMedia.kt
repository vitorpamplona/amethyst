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
package com.vitorpamplona.amethyst.commons.music

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent

/**
 * What a music track card plays: a music video through the video player, or audio through the
 * waveform strip under the cover.
 */
@Immutable
sealed interface MusicTrackMedia {
    @Immutable
    data class Video(
        val url: String,
    ) : MusicTrackMedia

    @Immutable
    data class Audio(
        val url: String,
        val mimeType: String?,
    ) : MusicTrackMedia
}

/**
 * Picks the media a music track card plays, or null when the track names none.
 *
 * The `video` tag wins. Without one, `url` is audio by spec, but some publishers put the music
 * video itself there (Nostria uploads a 1080p `.mp4` as `url`). Drawn as audio, that video renders
 * inside the waveform strip, so a `url` whose extension is video-only (`.mp4`, `.webm`, `.mov`, …,
 * not an audio one like `.m4a`) plays as a video. A `format` tag declares `url` audio, so such a
 * track keeps the audio path whatever the extension.
 */
fun MusicTrackEvent.media(): MusicTrackMedia? {
    videoUrl()?.let { return MusicTrackMedia.Video(it) }
    val url = url() ?: return null
    val format = format()
    if (format == null && RichTextParser.isVideoUrl(url) && !RichTextParser.isAudioUrl(url)) {
        return MusicTrackMedia.Video(url)
    }
    // Per spec, `format` is the audio format (mp3/flac/m4a/ogg).
    return MusicTrackMedia.Audio(url, format?.let { "audio/$it" })
}
