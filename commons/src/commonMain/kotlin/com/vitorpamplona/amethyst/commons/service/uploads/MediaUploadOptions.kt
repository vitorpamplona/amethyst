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

import androidx.compose.runtime.Immutable

/**
 * What one picked file is uploaded with. Each file carries its own, so a post can mix a
 * compressed photo with an uncompressed one, and every image has its own alt text, which is what
 * NIP-92 `imeta` describes: one `alt` per file.
 */
@Immutable
data class MediaUploadOptions(
    val alt: String = "",
    /** [alt] came from the on-device suggester and the user hasn't edited it. */
    val altSuggested: Boolean = false,
    val sensitive: Boolean = false,
    val quality: CompressorQuality = CompressorQuality.fromSlider(CompressorQuality.defaultSliderPosition),
    val stripMetadata: Boolean = true,
    val useH265: Boolean = false,
    val convertGifToMp4: Boolean = false,
    /** The part of a still picture to send, as it displays; null sends it whole. */
    val crop: ImageCrop? = null,
    /** The part of a video to send; null sends all of it. */
    val trim: VideoTrim? = null,
) {
    /** The `alt` to publish: null when blank. */
    val altOrNull: String? get() = alt.trim().ifBlank { null }

    /** The `content-warning` to publish: empty (a warning with no reason) when sensitive. */
    val contentWarningReason: String? get() = if (sensitive) "" else null

    companion object {
        /**
         * The starting options for [media]: the platform's default quality, or uncompressed for a
         * file the compressor doesn't touch (a PDF, an archive). No platform strips metadata from
         * such a file, so none is owed for it: asking would only end in "couldn't remove it".
         */
        fun defaultFor(
            media: SelectedMedia,
            stripMetadata: Boolean = true,
        ) = MediaUploadOptions(
            quality = if (media.isNotMedia()) CompressorQuality.UNCOMPRESSED else CompressorQuality.fromSlider(CompressorQuality.defaultSliderPosition),
            stripMetadata = stripMetadata && !media.isNotMedia(),
        )
    }
}
