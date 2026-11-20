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

import androidx.compose.runtime.Stable

@Stable
class SelectedMedia(
    val uri: MediaUri,
    val mimeType: String?,
    /**
     * A picture that moves although its type doesn't say so (an animated WebP), found by
     * [refineMediaType]. It is never cropped or re-encoded, which would keep only its first frame.
     */
    val animated: Boolean = false,
    /**
     * A copy made for this upload (a crop the avatar, banner or icon step wrote), deleted once it
     * has gone up; never the user's own file.
     */
    val temporary: Boolean = false,
) {
    /** A picture that plays: a GIF, or one found [animated]. */
    fun isAnimated() = isGif() || animated

    fun isImage() = mimeType?.startsWith("image")

    fun isGif() = mimeType?.equals("image/gif", ignoreCase = true) == true

    fun isVideo() = mimeType?.startsWith("video")

    fun isAudio() = mimeType?.startsWith("audio")

    fun isNotMedia() =
        mimeType?.let {
            !(it.startsWith("image") || it.startsWith("video") || it.startsWith("audio"))
        } ?: true

    fun isDocument() = mimeType == "application/pdf"

    /** Whether a crop can apply: a still picture (a GIF's animation and an SVG would not survive one). */
    fun canCrop(): Boolean = isImage() == true && !isAnimated() && mimeType?.contains("svg", ignoreCase = true) != true

    /**
     * Returns true if [MediaCompressor.compress] would actually compress this file when a
     * non-UNCOMPRESSED quality is selected. AVIF, GIF, SVG, and unknown MIME types pass
     * through MediaCompressor unchanged — the compression-quality slider has no effect on
     * them, so the UI hides the slider when no selected files are compressible.
     *
     * Keep this in sync with the branching in MediaCompressor.compress() — if either side
     * drifts, the UI will lie to the user.
     */
    fun isCompressible(): Boolean {
        val mt = mimeType?.lowercase() ?: return false
        return when {
            mt.startsWith("video") -> true
            mt.startsWith("image") ->
                !animated &&
                    !mt.contains("gif") &&
                    !mt.contains("svg") &&
                    !isAvif(mt)
            else -> false
        }
    }
}
