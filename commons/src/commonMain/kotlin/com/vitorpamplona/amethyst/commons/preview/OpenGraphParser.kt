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

class OpenGraphParser {
    class Result(
        val title: String,
        val description: String,
        val image: String,
        /** The `og:audio` URL the page declares for itself, verbatim (may be relative). */
        val audio: String = "",
        /** The `og:audio:type` MIME the page declares for [audio], e.g. `audio/mpeg`. */
        val audioType: String = "",
    )

    companion object {
        val ATTRIBUTE_VALUE_PROPERTY = "property"
        val ATTRIBUTE_VALUE_NAME = "name"
        val ATTRIBUTE_VALUE_ITEMPROP = "itemprop"

        // for <meta itemprop=... to get title
        private val META_X_TITLE =
            arrayOf(
                "og:title",
                "twitter:title",
                "title",
            )

        // for <meta itemprop=... to get description
        private val META_X_DESCRIPTION =
            arrayOf(
                "og:description",
                "twitter:description",
                "description",
            )

        // for <meta itemprop=... to get image
        private val META_X_IMAGE =
            arrayOf(
                "og:image",
                "twitter:image",
                "image",
            )

        // The audio file a media-host page is *about*. Media hosts publish a human-facing HTML
        // player page and point at the real file from here: nostr.build's
        // `e.nostr.build/a_<id>_mp3` page carries `og:audio` = `a.nostr.build/<id>.mp3`. Without
        // this the page can only be rendered as a card that links out.
        //
        // Deliberately `og:` only. `twitter:player:stream` is not included because it is usually a
        // video stream, and a player pointed at the wrong thing is worse than a link.
        private val META_X_AUDIO =
            arrayOf(
                "og:audio",
                "og:audio:secure_url",
            )

        private val META_X_AUDIO_TYPE =
            arrayOf(
                "og:audio:type",
            )

        private val CONTENT = "content"
    }

    /** Which field of [Result] a meta tag's key fills, or null when the key is not one we read. */
    private enum class Field {
        TITLE,
        DESCRIPTION,
        IMAGE,
        AUDIO,
        AUDIO_TYPE,
    }

    private fun fieldFor(key: String): Field? =
        when (key) {
            in META_X_TITLE -> Field.TITLE
            in META_X_DESCRIPTION -> Field.DESCRIPTION
            in META_X_IMAGE -> Field.IMAGE
            in META_X_AUDIO -> Field.AUDIO
            in META_X_AUDIO_TYPE -> Field.AUDIO_TYPE
            else -> null
        }

    fun extractUrlInfo(metaTags: Sequence<MetaTag>): Result {
        var title = ""
        var description = ""
        var image = ""
        var audio = ""
        var audioType = ""

        metaTags.forEach {
            // A meta tag names its key in exactly one of these three attributes, but which one
            // varies by site, so each is tried in turn until one is a key we read.
            val field =
                fieldFor(it.attr(ATTRIBUTE_VALUE_PROPERTY))
                    ?: fieldFor(it.attr(ATTRIBUTE_VALUE_NAME))
                    ?: fieldFor(it.attr(ATTRIBUTE_VALUE_ITEMPROP))

            // First value wins, so `og:audio` is preferred over the `og:audio:secure_url` that
            // follows it, and `og:title` over `twitter:title`.
            when (field) {
                Field.TITLE -> if (title.isEmpty()) title = it.attr(CONTENT)
                Field.DESCRIPTION -> if (description.isEmpty()) description = it.attr(CONTENT)
                Field.IMAGE -> if (image.isEmpty()) image = it.attr(CONTENT)
                Field.AUDIO -> if (audio.isEmpty()) audio = it.attr(CONTENT)
                Field.AUDIO_TYPE -> if (audioType.isEmpty()) audioType = it.attr(CONTENT)
                null -> Unit
            }

            // The early exit now has to wait for the audio pair too: pages put `og:audio` after
            // `og:image` (nostr.build does), so exiting on title+description+image alone dropped
            // the very field that makes the page playable. Nothing is scanned twice for it — the
            // sequence stops at `</head>` either way.
            if (title.isNotEmpty() &&
                description.isNotEmpty() &&
                image.isNotEmpty() &&
                audio.isNotEmpty() &&
                audioType.isNotEmpty()
            ) {
                return Result(title, description, image, audio, audioType)
            }
        }
        return Result(title, description, image, audio, audioType)
    }
}
