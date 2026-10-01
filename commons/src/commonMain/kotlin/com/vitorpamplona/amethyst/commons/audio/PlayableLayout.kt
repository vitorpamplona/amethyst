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
package com.vitorpamplona.amethyst.commons.audio

import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.normalizeMimeType

/** How a playable file inside a post is presented. */
enum class PlayableLayout {
    /** Known video: the video player. */
    VIDEO,

    /** Known audio that carries artwork: the cover, with the waveform scrubber over its foot. */
    AUDIO_COVER,

    /** Known audio without artwork: the waveform scrubber — the file's own waveform, else a synthetic one. */
    AUDIO_WAVEFORM,

    /** Could be either until the player probes it: the neutral track card, which commits to neither shape. */
    UNDECIDED,
}

/**
 * Picks the presentation for a playable file in a post.
 *
 * The declaration decides up front, so the card has its final shape before the player loads a byte. Once the
 * player has probed the file, [probedAudioOnly] (true when every track is audio) overrides the declaration:
 * that is what moves an [PlayableLayout.UNDECIDED] stream to its real shape, and what corrects a file whose
 * declaration was wrong.
 */
fun playableLayout(
    mimeType: String?,
    url: String,
    hasArtwork: Boolean,
    probedAudioOnly: Boolean? = null,
): PlayableLayout {
    val isAudio = probedAudioOnly ?: declaredAsAudio(mimeType, url) ?: return PlayableLayout.UNDECIDED
    return when {
        !isAudio -> PlayableLayout.VIDEO
        hasArtwork -> PlayableLayout.AUDIO_COVER
        else -> PlayableLayout.AUDIO_WAVEFORM
    }
}

/**
 * True when the declaration says audio, false when it says video, null when it can't tell.
 *
 * A declared `audio/` or `video/` MIME wins over the URL. HLS playlists are undecided whichever way they are
 * spelled — `audio/mpegurl` included — because a playlist carries either, and so are URLs whose extension
 * names neither, such as a bare Blossom hash.
 */
fun declaredAsAudio(
    mimeType: String?,
    url: String,
): Boolean? {
    val mime = normalizeMimeType(mimeType)
    if (mime != null) {
        if (RichTextParser.isHlsMimeType(mime)) return null
        if (mime.startsWith("audio/")) return true
        if (mime.startsWith("video/")) return false
    }
    return when {
        RichTextParser.hasExtensionIn(url, HLS_EXTENSIONS) -> null
        RichTextParser.isAudioUrl(url) -> true
        RichTextParser.isVideoUrl(url) -> false
        else -> null
    }
}

private val HLS_EXTENSIONS = listOf("m3u8")
