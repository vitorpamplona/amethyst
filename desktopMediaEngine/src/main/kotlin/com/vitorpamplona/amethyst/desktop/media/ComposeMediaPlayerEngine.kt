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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.VideoEngine
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface

/**
 * Drives shared playback UI with kdroidFilter's ComposeMediaPlayer.
 *
 * ## One engine, many handles
 *
 * ExoPlayer lets a feed hold several live players at once; the desktop backends
 * behind ComposeMediaPlayer decode one stream at a time. That sounds like a
 * mismatch but is not: Amethyst already elects a single visible video through
 * `VideoPlayerActiveMutex`, so at most one player is ever meant to be playing.
 *
 * This engine leans on that. Every `newPlayer()` hands back a cheap handle;
 * calling `play()` on a handle *binds* it to the single underlying engine,
 * evicting whichever handle held it. Unbound handles keep reporting their own
 * paused state, and [RenderSurface] returns false for them so the caller shows
 * its thumbnail instead of a blank frame.
 *
 * ## Units
 *
 * media3 speaks milliseconds and seeks in milliseconds; ComposeMediaPlayer
 * reports seconds as a Double and seeks in per-mille of the whole media. The
 * conversion lives here, in one place, rather than in shared UI code.
 */
class ComposeMediaPlayerEngine(
    private val newEngineState: () -> VideoPlayerState,
) : VideoEngine {
    @Volatile
    private var engine: VideoPlayerState? = null

    @Volatile
    internal var bound: ComposeMediaPlayerHandle? = null
        private set

    private val lock = Any()

    private fun ensureEngine(): VideoPlayerState =
        engine ?: synchronized(lock) {
            engine ?: newEngineState().also { engine = it }
        }

    override fun newPlayer(): Player = ComposeMediaPlayerHandle(this)

    @Composable
    override fun RenderSurface(
        player: Player,
        modifier: Modifier,
    ): Boolean {
        val state = engine ?: return false
        if (player !== bound) return false
        VideoPlayerSurface(playerState = state, modifier = modifier, contentScale = ContentScale.Fit)
        return true
    }

    /** Binds [handle] to the engine, releasing whoever held it. */
    internal fun bind(handle: ComposeMediaPlayerHandle): VideoPlayerState {
        val state = ensureEngine()
        synchronized(lock) {
            val previous = bound
            if (previous !== handle) {
                previous?.onUnbound()
                bound = handle
            }
        }
        return state
    }

    internal fun unbind(handle: ComposeMediaPlayerHandle) {
        synchronized(lock) {
            if (bound === handle) bound = null
        }
    }

    internal fun engineOrNull(): VideoPlayerState? = engine
}
