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
package com.vitorpamplona.amethyst.commons.service.uploads

import com.vitorpamplona.amethyst.commons.audio.mayHoldVideo
import com.vitorpamplona.amethyst.commons.audio.probeMp4AudioOnly

/**
 * [media] labelled by what it holds. A WebP whose header flags animation comes back
 * [SelectedMedia.animated]. An MP4-family file whose label doesn't say audio (`.mp4`,
 * `video/mp4`, `.mov`) and whose tracks are all sound comes back as `audio/mp4`: phones save voice
 * memos and screen-off recordings that way, and as a video it would get video tools, a failed
 * encode and a "couldn't remove metadata" prompt. Anything else, or a file this platform can't
 * read ([MediaUploader.readRange]), comes back as it was.
 */
suspend fun MediaUploader.refineMediaType(media: SelectedMedia): SelectedMedia {
    // An animated WebP says image/webp like a photo: only its header says it moves.
    if (media.mimeType.equals(WEBP, ignoreCase = true) && !media.animated) {
        val header = readRange(media.uri, 0, PictureHeaders.WEBP_HEADER_BYTES) ?: return media
        return if (PictureHeaders.isAnimatedWebp(header)) SelectedMedia(media.uri, media.mimeType, animated = true, temporary = media.temporary) else media
    }
    if (!mayHoldVideo(media.mimeType, media.uri.toString())) return media
    val audioOnly = probeMp4AudioOnly { offset, length -> readRange(media.uri, offset, length) }
    return if (audioOnly == true) SelectedMedia(media.uri, AUDIO_MP4, temporary = media.temporary) else media
}

private const val AUDIO_MP4 = "audio/mp4"
private const val WEBP = "image/webp"
