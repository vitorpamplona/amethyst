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
package com.vitorpamplona.amethyst.ios.ui.media

/**
 * Media URL detection and content-segment parsing for note rendering.
 *
 * Mirrors the extension lists from commons RichTextParser (jvmAndroid-only)
 * so the iOS module can classify URLs without pulling in the full parser.
 */
object MediaUtils {
    private val imageExt =
        setOf(
            "png",
            "jpg",
            "gif",
            "bmp",
            "jpeg",
            "webp",
            "svg",
            "avif",
        )
    private val videoExt =
        setOf(
            "mp4",
            "avi",
            "wmv",
            "mpg",
            "amv",
            "webm",
            "mov",
            "m3u8",
        )
    private val audioExt =
        setOf(
            "mp3",
            "ogg",
            "wav",
            "flac",
            "aac",
            "opus",
            "m4a",
        )

    private val allImageExt = imageExt + imageExt.map { it.uppercase() }
    private val allVideoExt = videoExt + videoExt.map { it.uppercase() }

    /** Strip query / fragment before comparing extensions. */
    private fun bareUrl(url: String): String {
        val q = url.indexOf('?')
        val h = url.indexOf('#')
        return when {
            q >= 0 -> url.substring(0, q)
            h >= 0 -> url.substring(0, h)
            else -> url
        }
    }

    fun isImageUrl(url: String): Boolean = allImageExt.any { bareUrl(url).endsWith(".$it") }

    fun isVideoUrl(url: String): Boolean = allVideoExt.any { bareUrl(url).endsWith(".$it") }

    fun isAudioUrl(url: String): Boolean = audioExt.any { bareUrl(url).endsWith(".$it", ignoreCase = true) }

    fun isMediaUrl(url: String): Boolean = isImageUrl(url) || isVideoUrl(url) || isAudioUrl(url)

    /**
     * Very simple URL regex – good enough for inline detection in note text.
     * Handles http(s) URLs; does *not* handle bare domains.
     */
    private val URL_REGEX =
        Regex(
            """https?://[^\s<\])"']+""",
            RegexOption.IGNORE_CASE,
        )

    /** A segment of note content – either plain text or a URL of a known type. */
    sealed class ContentSegment {
        data class Text(
            val text: String,
        ) : ContentSegment()

        data class ImageUrl(
            val url: String,
        ) : ContentSegment()

        data class VideoUrl(
            val url: String,
        ) : ContentSegment()

        data class AudioUrl(
            val url: String,
        ) : ContentSegment()

        data class LinkUrl(
            val url: String,
        ) : ContentSegment()
    }

    /**
     * Parse [content] into an ordered list of segments, splitting on detected URLs.
     */
    fun parseContent(content: String): List<ContentSegment> {
        if (content.isBlank()) return emptyList()

        val segments = mutableListOf<ContentSegment>()
        var lastEnd = 0

        for (match in URL_REGEX.findAll(content)) {
            // Emit preceding text
            if (match.range.first > lastEnd) {
                val text = content.substring(lastEnd, match.range.first)
                if (text.isNotBlank()) segments.add(ContentSegment.Text(text.trim()))
            }
            val url = match.value.trimEnd('.', ',', ')', ';', ':', '!', '?')
            when {
                isImageUrl(url) -> segments.add(ContentSegment.ImageUrl(url))
                isVideoUrl(url) -> segments.add(ContentSegment.VideoUrl(url))
                isAudioUrl(url) -> segments.add(ContentSegment.AudioUrl(url))
                else -> segments.add(ContentSegment.LinkUrl(url))
            }
            lastEnd = match.range.last + 1
        }

        // Trailing text
        if (lastEnd < content.length) {
            val text = content.substring(lastEnd)
            if (text.isNotBlank()) segments.add(ContentSegment.Text(text.trim()))
        }

        return segments
    }

    /** Extract just the hostname from a URL for display. */
    fun extractDomain(url: String): String {
        val withoutScheme =
            url
                .removePrefix("https://")
                .removePrefix("http://")
        return withoutScheme.substringBefore('/')
    }
}
