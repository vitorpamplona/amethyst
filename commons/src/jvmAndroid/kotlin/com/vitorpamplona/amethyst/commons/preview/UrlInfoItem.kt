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
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
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
    /** `og:video` — the video file an HTML player page is about. Empty when not declared. */
    val video: String = "",
    /** `og:video:type` — the MIME the page declares for [video]. Empty when not declared. */
    val videoType: String = "",
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
     * The declared `og:video`, absolute, but only when the declaration holds up: a [videoType] the
     * renderer can actually play (a `video/` MIME, an `audio/` one, or an HLS playlist type), or
     * — when the page omitted the type — a genuine media file extension.
     *
     * The type check is the entire safeguard, and it is not theoretical. YouTube declares
     * `og:video:url` = `youtube.com/embed/<id>` with `og:video:type` = `text/html`: an embed page,
     * not a video. Playing that is the same mistake, in the same shape, as the one that made
     * nostr.build's player page unplayable — an HTML document handed to ExoPlayer. `text/html`
     * classifies as nothing, so it is refused here and the page falls through to its link card.
     */
    private val playableVideoUrl: String? =
        absolute(video)
            .ifEmpty { null }
            ?.takeIf { RichTextParser.classifyMedia(it, videoType.ifEmpty { null }) == MediaContentKind.VIDEO }

    /**
     * The declared `og:audio`, absolute, under the same rule: an [audioType] in the `audio/`
     * family, or a genuine audio file extension when the page omitted the type.
     */
    private val playableAudioUrl: String? =
        absolute(audio)
            .ifEmpty { null }
            ?.takeIf { RichTextParser.isAudioContent(audioType.ifEmpty { null }, it) }

    /**
     * The media file this page declares itself to be a player for, or null when it declared none
     * we are willing to play.
     *
     * `og:video` wins a page that declares both, because it is the richer render: the audio path
     * deliberately strips the picture.
     */
    val playableMediaUrl: String? = playableVideoUrl ?: playableAudioUrl

    /** The MIME the page declared for [playableMediaUrl], or null when it named none. */
    val playableMediaType: String? =
        if (playableVideoUrl != null) videoType.ifEmpty { null } else audioType.ifEmpty { null }

    /**
     * Whether the fetch produced something worth rendering. An image is the usual evidence, but a
     * page that declared playable media counts too — a track page that ships no cover art would
     * otherwise be thrown away as Empty and fall back to a bare link, which is exactly the player
     * we went to the trouble of finding.
     */
    fun fetchComplete(): Boolean = url.isNotEmpty() && (image.isNotEmpty() || playableMediaUrl != null)

    fun allFetchComplete(): Boolean = title.isNotEmpty() && description.isNotEmpty() && image.isNotEmpty()
}
