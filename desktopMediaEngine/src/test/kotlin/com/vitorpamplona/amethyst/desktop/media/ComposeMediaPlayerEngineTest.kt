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
package com.vitorpamplona.amethyst.desktop.media

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Exercises the two things most likely to be wrong in this seam: the unit
 * conversion between media3 and ComposeMediaPlayer, and the single-decoder
 * binding rules.
 *
 * The engine is a hand-written fake ([FakeVideoPlayerState]), so no native
 * backend is loaded and the assertions can be about exactly which calls the
 * handle makes.
 */
class ComposeMediaPlayerEngineTest {
    private fun engineWith(state: FakeVideoPlayerState) = ComposeMediaPlayerEngine { state }

    private fun handleFor(
        engine: ComposeMediaPlayerEngine,
        uri: String = "https://example.invalid/v.mp4",
    ): ComposeMediaPlayerHandle =
        (engine.newPlayer() as ComposeMediaPlayerHandle).apply {
            setMediaItem(
                MediaItem
                    .Builder()
                    .setMediaId(uri)
                    .setUri(uri)
                    .build(),
            )
        }

    @Test
    fun `an unbound handle reports idle rather than borrowing the engine's state`() {
        val engine = engineWith(FakeVideoPlayerState())
        val a = handleFor(engine)
        val b = handleFor(engine)

        a.play()
        assertSame(a, engine.bound)

        // b never bound, so it must not report a's position or state.
        assertEquals(0L, b.currentPosition)
        assertEquals(Player.STATE_IDLE, b.playbackState)
        assertFalse(b.isPlaying)
        assertNull(b.playerError)
    }

    @Test
    fun `playing a second handle evicts the first and tells it so`() {
        val engine = engineWith(FakeVideoPlayerState())
        val first = handleFor(engine, "https://example.invalid/1.mp4")
        val second = handleFor(engine, "https://example.invalid/2.mp4")

        var firstToldItStopped = false
        first.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) firstToldItStopped = true
                }
            },
        )

        first.play()
        firstToldItStopped = false
        second.play()

        assertSame(second, engine.bound, "the newest play() owns the decoder")
        assertTrue(firstToldItStopped, "the evicted handle must notify its listeners, not go silently stale")
    }

    @Test
    fun `duration is TIME_UNSET until the engine knows it`() {
        val engine = engineWith(FakeVideoPlayerState())
        val handle = handleFor(engine)
        assertEquals(C.TIME_UNSET, handle.duration, "an unbound handle has no duration")

        handle.play()
        // A freshly opened source reports duration 0.0 until the backend probes
        // it; that is 'unknown', not 'zero-length'.
        assertEquals(C.TIME_UNSET, handle.duration)
    }

    @Test
    fun `seek is dropped while the duration is unknown`() {
        val engine = engineWith(FakeVideoPlayerState())
        val handle = handleFor(engine)
        handle.play()
        val before = engine.engineOrNull()!!.sliderPos
        handle.seekTo(5_000L)
        assertEquals(before, engine.engineOrNull()!!.sliderPos, "seeking without a duration must be a no-op, not a jump to an arbitrary point")
    }

    @Test
    fun `volume is clamped and mirrored onto the engine`() {
        val state = FakeVideoPlayerState()
        val engine = engineWith(state)
        val handle = handleFor(engine)
        handle.play()

        handle.volume = 0.5f
        assertEquals(0.5f, state.volume)

        handle.volume = 2f
        assertEquals(1f, handle.volume, "media3 volume is 0..1; out-of-range input is clamped")
        assertEquals(1f, state.volume)

        handle.volume = -1f
        assertEquals(0f, handle.volume)
    }

    @Test
    fun `release makes the handle inert and frees the decoder`() {
        val engine = engineWith(FakeVideoPlayerState())
        val handle = handleFor(engine)
        handle.play()
        assertSame(handle, engine.bound)

        handle.release()
        assertTrue(handle.isReleased)
        assertNull(engine.bound, "a released handle must not keep holding the single decoder")

        handle.play()
        assertNull(engine.bound, "play() after release must stay a no-op")
    }

    @Test
    fun `a handle with no media item does not bind`() {
        val engine = engineWith(FakeVideoPlayerState())
        val handle = engine.newPlayer() as ComposeMediaPlayerHandle
        handle.play()
        assertNull(engine.bound, "nothing to play means nothing to bind")
    }

    @Test
    fun `position and duration convert from the engine's seconds to media3 milliseconds`() {
        val state = FakeVideoPlayerState()
        val engine = engineWith(state)
        val handle = handleFor(engine)
        handle.play()

        state.reportedDuration = 20.0
        state.reportedCurrentTime = 1.5
        assertEquals(20_000L, handle.duration)
        assertEquals(1_500L, handle.currentPosition)
    }

    @Test
    fun `seek converts milliseconds to the engine's per-mille scale`() {
        val state = FakeVideoPlayerState()
        val engine = engineWith(state)
        val handle = handleFor(engine)
        handle.play()
        state.reportedDuration = 20.0

        handle.seekTo(5_000L)
        assertEquals(250f, state.sliderPos, "5s of 20s is a quarter in, which is 250 per-mille")

        handle.seekTo(20_000L)
        assertEquals(1000f, state.sliderPos, "the very end is 1000 per-mille")

        handle.seekTo(99_000L)
        assertEquals(1000f, state.sliderPos, "a seek past the end clamps rather than overshooting the scale")

        handle.seekTo(-5_000L)
        assertEquals(0f, state.sliderPos, "a negative seek clamps to the start")
    }

    @Test
    fun `playback state follows the engine through loading and ready`() {
        val state = FakeVideoPlayerState()
        val engine = engineWith(state)
        val handle = handleFor(engine)
        handle.play()

        state.reportedIsLoading = true
        assertEquals(Player.STATE_BUFFERING, handle.playbackState)

        state.reportedIsLoading = false
        state.reportedHasMedia = true
        assertEquals(Player.STATE_READY, handle.playbackState)
    }

    @Test
    fun `play opens the media item's uri on the engine`() {
        val state = FakeVideoPlayerState()
        val engine = engineWith(state)
        handleFor(engine, "https://example.invalid/clip.mp4").play()
        assertEquals("https://example.invalid/clip.mp4", state.openedUri)
        assertTrue(state.isPlaying)
    }
}
