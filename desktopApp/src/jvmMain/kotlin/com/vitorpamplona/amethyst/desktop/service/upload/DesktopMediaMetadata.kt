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
package com.vitorpamplona.amethyst.desktop.service.upload

import com.vitorpamplona.amethyst.commons.blurhash.toBlurhash
import com.vitorpamplona.amethyst.commons.blurhash.toPlatformImage
import com.vitorpamplona.amethyst.commons.thumbhash.toThumbhash
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.io.File
import javax.imageio.ImageIO

data class MediaMetadata(
    val sha256: String,
    val size: Long,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val blurhash: String? = null,
    val thumbhash: String? = null,
)

object DesktopMediaMetadata {
    fun compute(file: File): MediaMetadata {
        val bytes = file.readBytes()
        val hash = sha256(bytes).toHexKey()
        val mimeType = guessMimeType(file)
        var width: Int? = null
        var height: Int? = null
        var blurhash: String? = null
        var thumbhash: String? = null

        if (mimeType.startsWith("image/")) {
            try {
                val image = ImageIO.read(file)
                if (image != null) {
                    width = image.width
                    height = image.height
                    val platformImage = image.toPlatformImage()
                    blurhash = runCatching { platformImage.toBlurhash() }.getOrNull()
                    thumbhash = runCatching { platformImage.toThumbhash() }.getOrNull()
                }
            } catch (_: Exception) {
            }
        }

        return MediaMetadata(
            sha256 = hash,
            size = bytes.size.toLong(),
            mimeType = mimeType,
            width = width,
            height = height,
            blurhash = blurhash,
            thumbhash = thumbhash,
        )
    }

    fun guessMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "avif" -> "image/avif"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }
    }
}
