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
package com.vitorpamplona.amethyst.desktop.service.media

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages a pool of VLCJ media players to avoid costly create/destroy cycles.
 * Keeps strong references to prevent GC crashes from native callbacks.
 *
 * IMPORTANT: Never let player instances be garbage collected while native
 * callbacks are active — this causes JVM segfaults.
 */
object VlcjPlayerPool {
    private val available = AtomicBoolean(false)
    private var factory: MediaPlayerFactory? = null

    // Strong references to ALL created players (prevents GC crash)
    private val allPlayers = mutableListOf<EmbeddedMediaPlayer>()
    private val idlePlayers = ConcurrentLinkedQueue<EmbeddedMediaPlayer>()

    private const val MAX_POOL_SIZE = 3

    /**
     * Initialize the pool. Returns false if VLC is not installed.
     */
    fun init(): Boolean {
        if (available.get()) return true
        return try {
            val f = MediaPlayerFactory("--no-xlib")
            factory = f
            available.set(true)
            true
        } catch (_: Exception) {
            available.set(false)
            false
        }
    }

    fun isAvailable(): Boolean = available.get()

    /**
     * Create a callback video surface using the factory's API.
     */
    fun createVideoSurface(
        bufferFormatCallback: BufferFormatCallback,
        renderCallback: RenderCallback,
    ): VideoSurface? {
        val f = factory ?: return null
        return f.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true)
    }

    /**
     * Acquire a player from the pool or create a new one.
     * Returns null if VLC is not available or pool is at capacity.
     */
    fun acquire(): EmbeddedMediaPlayer? {
        if (!available.get()) return null
        val f = factory ?: return null

        // Reuse idle player
        idlePlayers.poll()?.let { return it }

        // Create new if under limit
        synchronized(allPlayers) {
            if (allPlayers.size >= MAX_POOL_SIZE) return null
            return try {
                val player = f.mediaPlayers().newEmbeddedMediaPlayer()
                allPlayers.add(player)
                player
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Return a player to the pool for reuse. Stops playback first.
     */
    fun release(player: EmbeddedMediaPlayer) {
        try {
            player.controls().stop()
            // Remove any event listeners to prevent stale callbacks
            player.events().addMediaPlayerEventListener(
                object : MediaPlayerEventAdapter() {},
            )
            idlePlayers.offer(player)
        } catch (_: Exception) {
            // Player may already be disposed
        }
    }

    /**
     * Shut down the entire pool. Call on app exit.
     */
    fun shutdown() {
        synchronized(allPlayers) {
            idlePlayers.clear()
            for (player in allPlayers) {
                try {
                    player.controls().stop()
                    player.release()
                } catch (_: Exception) {
                    // Ignore
                }
            }
            allPlayers.clear()
        }
        try {
            factory?.release()
        } catch (_: Exception) {
            // Ignore
        }
        factory = null
        available.set(false)
    }
}
