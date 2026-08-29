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
package androidx.media3

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoEngine
import androidx.media3.common.VideoSize
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberMuteButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickCount
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A Player driven by the test, standing in for whatever engine is installed. */
private class FakePlayer : Player {
    val listeners = mutableListOf<Player.Listener>()

    override var isPlaying = false
        private set
    override var currentMediaItem: MediaItem? = null
    override var currentPosition = 0L
    override var bufferedPosition = 0L
    override var duration = 0L
    override var playbackState = Player.STATE_READY
    override val playerError: PlaybackException? = null
    override val currentTracks = Tracks.EMPTY
    override val videoSize = VideoSize.UNKNOWN
    override var isReleased = false
        private set
    override var volume = 1f
        set(value) {
            field = value
            listeners.forEach { it.onVolumeChanged(value) }
        }
    override var playWhenReady = false
    override var repeatMode = Player.REPEAT_MODE_OFF
    override var trackSelectionParameters = TrackSelectionParameters.DEFAULT

    override fun play() = setPlaying(true)

    override fun pause() = setPlaying(false)

    override fun stop() = setPlaying(false)

    override fun prepare() = Unit

    override fun release() {
        isReleased = true
    }

    override fun seekTo(positionMs: Long) {
        currentPosition = positionMs
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        currentMediaItem = mediaItem
    }

    override fun setWakeMode(wakeMode: Int) = Unit

    override fun setVideoScalingMode(scalingMode: Int) = Unit

    override fun addListener(listener: Player.Listener) {
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
    }

    private fun setPlaying(value: Boolean) {
        isPlaying = value
        listeners.forEach { it.onIsPlayingChanged(value) }
    }
}

class Media3ShimTest {
    @AfterTest
    fun clearEngine() {
        VideoEngine.installed = null
    }

    private fun render(content: @Composable () -> Unit) {
        val scene = ImageComposeScene(64, 64, Density(1f)) { content() }
        try {
            scene.render()
        } finally {
            scene.close()
        }
    }

    @Test
    fun `play pause state follows the player and toggles it`() {
        val player = FakePlayer()
        lateinit var captured: Any
        render {
            val state = rememberPlayPauseButtonState(player)
            captured = state
            assertTrue(state.showPlay, "a paused player must offer Play")
            assertTrue(state.isEnabled)
        }
        (captured as androidx.media3.ui.compose.state.PlayPauseButtonState).onClick()
        assertTrue(player.isPlaying, "clicking Play must start the player")
    }

    @Test
    fun `mute state reflects volume and toggles it`() {
        val player = FakePlayer()
        lateinit var captured: androidx.media3.ui.compose.state.MuteButtonState
        render {
            captured = rememberMuteButtonState(player)
            assertFalse(captured.showMuted)
        }
        captured.onClick()
        assertEquals(0f, player.volume, "clicking mute must zero the volume")
        captured.onClick()
        assertEquals(1f, player.volume, "clicking again must restore it")
    }

    @Test
    fun `progress is a fraction of duration and survives an unknown duration`() {
        val player = FakePlayer()
        player.duration = 200L
        player.currentPosition = 50L
        player.bufferedPosition = 100L
        render {
            val p = rememberProgressStateWithTickCount(player, 10)
            assertEquals(0.25f, p.currentPositionProgress)
            assertEquals(0.5f, p.bufferedPositionProgress)
        }

        val unknown = FakePlayer()
        render {
            val p = rememberProgressStateWithTickCount(unknown, 10)
            assertEquals(0f, p.currentPositionProgress, "an unknown duration must not divide by zero")
        }
    }

    @Test
    fun `state holders unsubscribe when they leave composition`() {
        val player = FakePlayer()
        render { rememberPlayPauseButtonState(player) }
        assertTrue(player.listeners.isEmpty(), "listener leaked after the scene closed")
    }

    @Test
    fun `ContentFrame shows the shutter when no engine is installed`() {
        var shutterDrawn = false
        render {
            ContentFrame(player = FakePlayer(), shutter = {
                shutterDrawn = true
                Box(Modifier)
            })
        }
        assertTrue(shutterDrawn, "with no VideoEngine the caller's placeholder must show")
    }

    @Test
    fun `ContentFrame shows the shutter when the engine is driving another player`() {
        val bound = FakePlayer()
        VideoEngine.installed =
            object : VideoEngine {
                override fun newPlayer(): Player = bound

                @Composable
                override fun RenderSurface(
                    player: Player,
                    modifier: Modifier,
                ): Boolean = player === bound
            }

        var shutterDrawn = false
        render {
            ContentFrame(player = FakePlayer(), shutter = {
                shutterDrawn = true
                Box(Modifier)
            })
        }
        assertTrue(shutterDrawn, "a player the engine is not driving must fall back to the placeholder")

        shutterDrawn = false
        render {
            ContentFrame(player = bound, shutter = {
                shutterDrawn = true
                Box(Modifier)
            })
        }
        assertFalse(shutterDrawn, "the bound player must render through the engine instead")
    }
}
