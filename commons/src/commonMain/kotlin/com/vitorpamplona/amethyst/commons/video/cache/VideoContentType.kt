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
package com.vitorpamplona.amethyst.commons.video.cache

import com.vitorpamplona.amethyst.commons.video.HLS_MIME_TYPE
import com.vitorpamplona.amethyst.commons.video.normalizeStreamMimeType

/**
 * The content type to announce for a cached file. A player that reads through a cache (iOS's
 * resource loader) is not shown the server's headers, so it plays only what it is told the file
 * is, and Blossom servers often send `application/octet-stream` for every blob.
 */
object VideoContentType {
    const val DEFAULT = "video/mp4"

    /**
     * In order: the server's type when it names audio or video, the type the event declared
     * (imeta `m`), what the first bytes look like, the URL's extension, and last mp4, which nearly
     * every feed video is.
     */
    fun of(
        serverMimeType: String?,
        declaredMimeType: String?,
        url: String,
        head: ByteArray?,
    ): String =
        audiovisual(serverMimeType)
            ?: audiovisual(declaredMimeType)
            ?: head?.let { sniff(it) }
            ?: byExtension(url)
            ?: DEFAULT

    /** Whether [head], the start of a file, is an HLS playlist (`#EXTM3U`) and not a media file. */
    fun isPlaylist(head: ByteArray): Boolean = head.startsWith("#EXTM3U")

    private fun audiovisual(mimeType: String?): String? {
        val type = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return null
        if (normalizeStreamMimeType(type) == HLS_MIME_TYPE) return null
        return type.takeIf { it.startsWith("video/") || it.startsWith("audio/") }
    }

    /** The type a file's first bytes show, for the formats AVFoundation and media3 play. */
    fun sniff(head: ByteArray): String? {
        if (head.size >= 12 && head.matches(4, "ftyp")) {
            return when (head.decodeAscii(8, 4)) {
                "qt  " -> "video/quicktime"
                "M4A ", "M4B " -> "audio/mp4"
                "M4V ", "M4VH", "M4VP" -> "video/x-m4v"
                "3gp4", "3gp5", "3gp6", "3g2a" -> "video/3gpp"
                else -> "video/mp4"
            }
        }
        if (head.startsWith("ID3")) return "audio/mpeg"
        if (head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0) {
            // An MPEG audio frame sync; layer bits 00 with an ADTS header is AAC.
            return if ((head[1].toInt() and 0x06) == 0) "audio/aac" else "audio/mpeg"
        }
        if (head.startsWith("RIFF") && head.size >= 12 && head.matches(8, "WAVE")) return "audio/wav"
        if (head.startsWith("fLaC")) return "audio/flac"
        if (head.startsWith("OggS")) return "audio/ogg"
        if (head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()) return "video/webm"
        return null
    }

    fun byExtension(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        val name = path.substringAfterLast('/')
        if (!name.contains('.')) return null
        return when (name.substringAfterLast('.').lowercase()) {
            "mp4" -> "video/mp4"
            "m4v" -> "video/x-m4v"
            "mov", "qt" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "oga", "opus" -> "audio/ogg"
            else -> null
        }
    }

    private fun ByteArray.startsWith(ascii: String): Boolean = matches(0, ascii)

    private fun ByteArray.matches(
        offset: Int,
        ascii: String,
    ): Boolean {
        if (size < offset + ascii.length) return false
        for (i in ascii.indices) if (this[offset + i] != ascii[i].code.toByte()) return false
        return true
    }

    private fun ByteArray.decodeAscii(
        offset: Int,
        length: Int,
    ): String = CharArray(length) { (this[offset + it].toInt() and 0xFF).toChar() }.concatToString()
}
