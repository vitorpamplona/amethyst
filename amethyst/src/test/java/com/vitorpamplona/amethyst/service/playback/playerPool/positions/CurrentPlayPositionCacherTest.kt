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
package com.vitorpamplona.amethyst.service.playback.playerPool.positions

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.commons.service.playback.MutableVideoViewedPositionCache
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentPlayPositionCacherTest {
    private val url = "https://example.com/clip.mp4"

    private val saved = mutableMapOf<String, Long>()
    private val cache =
        object : MutableVideoViewedPositionCache {
            override fun get(uri: String) = saved[uri]

            override fun add(
                uri: String,
                position: Long,
            ) {
                saved[uri] = position
            }
        }

    private var position = 0L
    private val player =
        mockk<Player> {
            every { currentPosition } answers { position }
            every { duration } returns 20_000L
            every { playbackState } returns Player.STATE_READY
        }

    private val cacher =
        CurrentPlayPositionCacher(player, cache).apply {
            onMediaItemTransition(MediaItem.Builder().setMediaId(url).build(), Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        }

    private fun pausedAt(positionMs: Long): Long? {
        position = positionMs
        cacher.onIsPlayingChanged(false)
        return saved[url]
    }

    @Test
    fun savesWhereAVideoWasPaused() {
        assertEquals(12_000L, pausedAt(12_000))
    }

    @Test
    fun aVideoStoppedOnItsLastFrameIsSavedAsTheStart() {
        // AutoReplayLimiter stops feed videos exactly at the end; coming back must not resume there
        assertEquals(0L, pausedAt(20_000))
        assertEquals(0L, pausedAt(19_200))
    }

    @Test
    fun justBeforeTheEndIsStillAResumePoint() {
        assertEquals(18_900L, pausedAt(18_900))
    }
}
