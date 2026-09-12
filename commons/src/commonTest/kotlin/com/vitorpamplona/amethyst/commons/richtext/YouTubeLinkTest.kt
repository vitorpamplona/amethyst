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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YouTubeLinkTest {
    private fun id(url: String) = YouTubeLink.parse(url)?.id

    @Test
    fun readsTheSpellingsThatShowUpInNotes() {
        val expected = "dQw4w9WgXcQ"

        assertEquals(expected, id("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(expected, id("https://youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(expected, id("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(expected, id("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(expected, id("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(expected, id("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(expected, id("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals(expected, id("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertEquals(expected, id("https://www.youtube.com/v/dQw4w9WgXcQ"))
        assertEquals(expected, id("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
        assertEquals(expected, id("http://youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun survivesTheUsualUrlNoise() {
        val expected = "dQw4w9WgXcQ"

        assertEquals(expected, id("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1234&index=2"))
        assertEquals(expected, id("https://www.youtube.com/watch?app=desktop&v=dQw4w9WgXcQ"))
        assertEquals(expected, id("https://youtu.be/dQw4w9WgXcQ?si=abcdef"))
        assertEquals(expected, id("https://www.youtube.com/embed/dQw4w9WgXcQ/"))
        assertEquals(expected, id("https://www.youtube.com/watch?v=dQw4w9WgXcQ#fragment"))
        // The parser never sees a scheme-less URL from the detector today, but it costs nothing.
        assertEquals(expected, id("youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun aPageThatIsNotAVideoIsNotAVideo() {
        // The 11-character id rule is what keeps these out: without it every youtube.com path
        // would look like a video and open a player showing nothing.
        assertNull(YouTubeLink.parse("https://www.youtube.com/feed/subscriptions"))
        assertNull(YouTubeLink.parse("https://www.youtube.com/@SomeChannel"))
        assertNull(YouTubeLink.parse("https://www.youtube.com/"))
        assertNull(YouTubeLink.parse("https://www.youtube.com/watch"))
        assertNull(YouTubeLink.parse("https://www.youtube.com/watch?v="))
        assertNull(YouTubeLink.parse("https://www.youtube.com/watch?v=tooshort"))
        assertNull(YouTubeLink.parse("https://www.youtube.com/watch?v=waaaaaaaaaaytoolong"))
        assertNull(YouTubeLink.parse("https://www.youtube.com/watch?v=has+bad+chars"))
    }

    @Test
    fun otherHostsAreNotYouTube() {
        assertNull(YouTubeLink.parse("https://vimeo.com/148751763"))
        assertNull(YouTubeLink.parse("https://e.nostr.build/v_ETvKzX2OdOGmFEp1avRlm5_mp4"))
        assertNull(YouTubeLink.parse("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeLink.parse("https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeLink.parse(""))
    }

    @Test
    fun readsTheStartOffsetInEverySpellingYouTubeUses() {
        assertEquals(90, YouTubeLink.parse("https://youtu.be/dQw4w9WgXcQ?t=90")?.startSeconds)
        assertEquals(90, YouTubeLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90s")?.startSeconds)
        assertEquals(3723, YouTubeLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1h2m3s")?.startSeconds)
        assertEquals(150, YouTubeLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=2m30s")?.startSeconds)
        assertEquals(90, YouTubeLink.parse("https://www.youtube.com/embed/dQw4w9WgXcQ?start=90")?.startSeconds)

        assertNull(YouTubeLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")?.startSeconds)
        assertNull(YouTubeLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=soon")?.startSeconds)
    }

    @Test
    fun buildsThePrivacyEnhancedPlayerUrl() {
        assertEquals(
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&playsinline=1&rel=0",
            YouTubeLink.embedUrlFor("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&playsinline=1&rel=0&start=90",
            YouTubeLink.embedUrlFor("https://youtu.be/dQw4w9WgXcQ?t=90"),
        )
        assertNull(YouTubeLink.embedUrlFor("https://vimeo.com/148751763"))
    }

    @Test
    fun aZeroOffsetIsNotWorthAParameter() {
        assertEquals(
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&playsinline=1&rel=0",
            YouTubeLink.embedUrlFor("https://youtu.be/dQw4w9WgXcQ?t=0"),
        )
    }
}
