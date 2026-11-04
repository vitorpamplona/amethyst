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
package com.vitorpamplona.amethyst.desktop.ui.media

import com.vitorpamplona.amethyst.desktop.service.media.MediaPlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodePlayheadTest {
    private val url = "https://example.com/episode.mp3"
    private val minutes45 = 45 * 60 * 1000L

    private fun state(
        at: Long,
        playing: Boolean,
        buffering: Boolean = false,
        playingUrl: String? = url,
    ) = MediaPlaybackState(url = playingUrl, type = MediaType.AUDIO, isPlaying = playing, isBuffering = buffering, duration = minutes45, currentTime = at)

    @Test
    fun playingToTheEndEndsEvenWhenTheIdleEngineReportsZero() {
        val last = episodePlayhead(url, state(minutes45 - 400, playing = true), null)
        val after = episodePlayhead(url, state(0, playing = false), last)!!
        assertTrue(after.hasEnded)
        assertEquals(minutes45, after.positionMillis)
        // It stays ended until playback starts again.
        assertTrue(episodePlayhead(url, state(0, playing = false), after)!!.hasEnded)
        assertFalse(episodePlayhead(url, state(1_000, playing = true), after)!!.hasEnded)
    }

    @Test
    fun aPauseShortlyBeforeTheEndIsNoEnd() {
        val last = episodePlayhead(url, state(minutes45 - 27_000, playing = true), null)
        val paused = episodePlayhead(url, state(minutes45 - 27_000, playing = false), last)!!
        assertFalse(paused.hasEnded)
        assertEquals(minutes45 - 27_000, paused.positionMillis)
    }

    @Test
    fun bufferingAtTheEndIsNoEnd() {
        val last = episodePlayhead(url, state(minutes45 - 1_000, playing = true), null)
        assertFalse(episodePlayhead(url, state(minutes45 - 1_000, playing = false, buffering = true), last)!!.hasEnded)
    }

    @Test
    fun anotherFilePausesThisOneOnce() {
        val last = episodePlayhead(url, state(60_000, playing = true), null)
        val other = state(0, playing = true, playingUrl = "https://example.com/other.mp3")
        val paused = episodePlayhead(url, other, last)!!
        assertFalse(paused.isPlaying)
        assertNull(episodePlayhead(url, other, paused))
    }
}
