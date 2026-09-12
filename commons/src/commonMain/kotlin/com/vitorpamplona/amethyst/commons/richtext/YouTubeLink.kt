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
package com.vitorpamplona.amethyst.commons.richtext

import androidx.compose.runtime.Immutable

/** A YouTube video a link resolves to, plus the offset the link asked to start at. */
@Immutable
data class YouTubeVideo(
    val id: String,
    val startSeconds: Int? = null,
)

/**
 * Recognises the YouTube link spellings that show up in notes and turns them into the one URL a
 * WebView can actually play: the IFrame player page.
 *
 * This exists because YouTube publishes no media file. A `watch?v=` page is an application, and the
 * `og:video` it advertises is `youtube.com/embed/<id>` typed `text/html` -- markup, which is why
 * [UrlInfoItem][com.vitorpamplona.amethyst.commons.preview.UrlInfoItem] refuses to hand it to
 * ExoPlayer. The embed page below is the officially supported way to play a video outside
 * youtube.com, and the only one that does not depend on scraping a stream URL.
 *
 * Kept free of UI and of `java.net.URI` so it stays CLI-safe and multiplatform; the parsing is a
 * hand-rolled split, which is cheap and total (a malformed URL returns null rather than throwing).
 */
object YouTubeLink {
    /**
     * YouTube ids have been exactly 11 characters of this alphabet for the platform's whole life,
     * and requiring that is what keeps `youtube.com/feed/subscriptions` or a channel page from
     * being mistaken for a video. If the format ever widens, the failure is safe: [parse] returns
     * null and the link opens the way it does today, in the external app.
     */
    private const val ID_LENGTH = 11

    private val WATCH_HOSTS =
        setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtube-nocookie.com",
            "www.youtube-nocookie.com",
        )

    private val SHORT_HOSTS = setOf("youtu.be", "www.youtu.be")

    /** Path prefixes that carry the id as the next segment: /embed/ID, /shorts/ID, /live/ID, /v/ID. */
    private val ID_IN_PATH = setOf("embed", "shorts", "live", "v")

    /**
     * The privacy-enhanced embed host. YouTube sets no tracking cookie here until playback actually
     * starts, which is the right default for a client that ships a Tor toggle.
     */
    private const val EMBED_HOST = "https://www.youtube-nocookie.com/embed/"

    fun isYouTube(url: String): Boolean = parse(url) != null

    /** The video [url] points at, or null when it is not a recognisable YouTube video link. */
    fun parse(url: String): YouTubeVideo? {
        val afterScheme = url.substringAfter("://", url)
        val hostEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val host = (if (hostEnd < 0) afterScheme else afterScheme.substring(0, hostEnd)).lowercase()
        val rest = if (hostEnd < 0) "" else afterScheme.substring(hostEnd)

        val path = rest.substringBefore('?').substringBefore('#').trim('/')
        val query = if ('?' in rest) rest.substringAfter('?').substringBefore('#') else ""

        val id =
            when (host) {
                in SHORT_HOSTS -> path.substringBefore('/')
                in WATCH_HOSTS -> {
                    val segments = path.split('/')
                    when {
                        segments.size >= 2 && segments[0] in ID_IN_PATH -> segments[1]
                        segments.firstOrNull() == "watch" -> param(query, "v")
                        else -> null
                    }
                }
                else -> null
            }

        if (id == null || !isVideoId(id)) return null

        // `t` on watch/youtu.be links, `start` on embed links; both mean the same thing.
        val start = param(query, "t") ?: param(query, "start")

        return YouTubeVideo(id, start?.let { parseSeconds(it) })
    }

    /**
     * The player page for [video], to be opened as a top-level URL in a WebView.
     *
     * `autoplay=1` is deliberate but will usually be ignored: the in-app browser keeps
     * `mediaPlaybackRequiresUserGesture = true`, so the viewer taps play once inside the player.
     * That setting is shared with every other site in that browser and must not be relaxed just to
     * save a tap -- it is what stops any page from playing sound on open.
     *
     * `playsinline=1` keeps the video in the page instead of demanding fullscreen, and `rel=0`
     * limits the end-of-video suggestions to the same channel.
     */
    fun embedUrl(video: YouTubeVideo): String {
        val start = video.startSeconds?.takeIf { it > 0 }?.let { "&start=$it" } ?: ""
        return "$EMBED_HOST${video.id}?autoplay=1&playsinline=1&rel=0$start"
    }

    /** Convenience for the common call: the player URL for [url], or null if it is not a video link. */
    fun embedUrlFor(url: String): String? = parse(url)?.let { embedUrl(it) }

    private fun isVideoId(id: String): Boolean =
        id.length == ID_LENGTH &&
            id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }

    private fun param(
        query: String,
        name: String,
    ): String? {
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            if (eq == name.length && pair.startsWith(name)) {
                return pair.substring(eq + 1).ifEmpty { null }
            }
        }
        return null
    }

    /**
     * YouTube writes offsets as bare seconds (`t=90`), seconds with a unit (`t=90s`), or a
     * duration (`t=1h2m3s`, `t=2m30s`). Anything else yields null and the video starts at 0.
     */
    private fun parseSeconds(raw: String): Int? {
        raw.toIntOrNull()?.let { return if (it >= 0) it else null }

        var total = 0
        var digits = 0
        var seen = false
        for (c in raw) {
            when {
                c.isDigit() -> {
                    digits = digits * 10 + (c - '0')
                    seen = true
                }
                c == 'h' || c == 'H' -> {
                    total += digits * 3600
                    digits = 0
                }
                c == 'm' || c == 'M' -> {
                    total += digits * 60
                    digits = 0
                }
                c == 's' || c == 'S' -> {
                    total += digits
                    digits = 0
                }
                else -> return null
            }
        }
        // A trailing number with no unit (`1m30`) is seconds.
        total += digits
        return if (seen) total else null
    }
}
