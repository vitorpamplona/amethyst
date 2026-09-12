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
package com.vitorpamplona.amethyst.commons.preview

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import java.net.URI

@Immutable
class UrlInfoItem(
    val url: String = "",
    val title: String = "",
    val description: String = "",
    val image: String = "",
    val mimeType: String,
    /** `og:audio` — the audio file an HTML player page is about. Empty when not declared. */
    val audio: String = "",
    /** `og:audio:type` — the MIME the page declares for [audio]. Empty when not declared. */
    val audioType: String = "",
) {
    val verifiedUrl = runCatching { URI(url).toURL() }.getOrNull()

    /** Resolves a possibly page-relative OpenGraph URL against the page's own address. */
    private fun absolute(path: String): String =
        if (path.startsWith("/")) {
            runCatching {
                verifiedUrl
                    ?.toURI()
                    ?.resolve(path)
                    ?.toURL()
                    ?.toString()
            }.getOrNull() ?: path
        } else {
            path
        }

    val imageUrlFullPath = absolute(image)

    /**
     * The declared `og:audio`, absolute, but only when the page gave us grounds to believe it is
     * really audio: an [audioType] in the `audio/` family, or — when the page omitted the type
     * — a genuine audio file extension. Null otherwise.
     *
     * The check is not ceremony. A page can advertise anything under `og:audio`, and handing an
     * unverified URL to the player is precisely the mistake that made nostr.build's HTML player
     * page unplayable in the first place.
     */
    val playableAudioUrl: String? =
        absolute(audio)
            .ifEmpty { null }
            ?.takeIf { RichTextParser.isAudioContent(audioType.ifEmpty { null }, it) }

    /**
     * Whether the fetch produced something worth rendering. An image is the usual evidence, but a
     * page that declared a playable `og:audio` counts too — a track page that ships no cover art
     * would otherwise be thrown away as Empty and fall back to a bare link, which is exactly the
     * player we went to the trouble of finding.
     */
    fun fetchComplete(): Boolean = url.isNotEmpty() && (image.isNotEmpty() || playableAudioUrl != null)

    fun allFetchComplete(): Boolean = title.isNotEmpty() && description.isNotEmpty() && image.isNotEmpty()
}
