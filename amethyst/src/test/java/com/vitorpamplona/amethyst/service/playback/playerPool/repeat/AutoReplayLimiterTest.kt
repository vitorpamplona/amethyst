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
package com.vitorpamplona.amethyst.service.playback.playerPool.repeat

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReplayLimiterTest {
    // Every value pushed to the player's pause-at-end-of-media-item flag, in order.
    private val pushes = mutableListOf<Boolean>()
    private val limiter = AutoReplayLimiter(maxAutoPlays = 5) { pushes += it }

    private val armed get() = pushes.lastOrNull() ?: false

    private fun startPlaying() = limiter.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

    private fun userPauses() = limiter.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

    // What ExoPlayer reports when the armed flag stops it at the end of the item.
    private fun stopsAtTheEnd() = limiter.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM)

    private fun loopsOnce() = limiter.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)

    private fun newMediaItem() = limiter.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

    private fun seeks() {
        val position = Player.PositionInfo(null, 0, null, null, 0, 1_000, 1_000, -1, -1)
        limiter.onPositionDiscontinuity(position, position, Player.DISCONTINUITY_REASON_SEEK)
    }

    @Test
    fun stopsAtTheEndOfTheFifthPlay() {
        startPlaying()

        // repeat transitions 1..3 start plays 2..4
        repeat(3) { loopsOnce() }
        assertFalse(armed)

        // the 4th starts play 5, the last one: the player must stop when it ends, not loop into a 6th
        loopsOnce()
        assertTrue(armed)
    }

    @Test
    fun resumingFromTheStopAtTheEndGrantsFiveMorePlays() {
        startPlaying()
        repeat(4) { loopsOnce() }
        stopsAtTheEnd()

        // user presses play on the last frame: the stop is lifted and the player loops into play 1
        startPlaying()
        assertFalse(armed)
        loopsOnce()

        repeat(3) { loopsOnce() }
        assertFalse(armed)

        loopsOnce()
        assertTrue(armed)
    }

    @Test
    fun resumingMidPlayCountsThatPlayAsTheFirst() {
        startPlaying()
        repeat(2) { loopsOnce() }
        userPauses()

        startPlaying()
        repeat(3) { loopsOnce() }
        assertFalse(armed)

        loopsOnce()
        assertTrue(armed)
    }

    @Test
    fun seekingAwayFromTheStopAtTheEndContinuesThatPlay() {
        startPlaying()
        repeat(4) { loopsOnce() }
        stopsAtTheEnd()

        // the user drags the progress bar back, then plays the rest of that loop as play 1
        seeks()
        startPlaying()
        repeat(3) { loopsOnce() }
        assertFalse(armed)

        loopsOnce()
        assertTrue(armed)
    }

    @Test
    fun switchingMediaItemsResetsTheCount() {
        startPlaying()
        repeat(4) { loopsOnce() }
        assertTrue(armed)

        // the pooled player is handed a different video
        newMediaItem()
        assertFalse(armed)

        repeat(3) { loopsOnce() }
        assertFalse(armed)

        loopsOnce()
        assertTrue(armed)
    }

    @Test
    fun pausingMidPlayDoesNotResetTheCount() {
        startPlaying()
        repeat(3) { loopsOnce() }

        // a pause alone (e.g. scrolled off screen) does not reset; only a resume does
        userPauses()

        loopsOnce()
        assertTrue(armed)
    }

    @Test
    fun onlyPushesChanges() {
        startPlaying()
        repeat(10) { loopsOnce() }
        startPlaying()
        startPlaying()

        // armed once at the 5th play, disarmed once by the first resume
        assertEquals(listOf(true, false), pushes)
    }
}
