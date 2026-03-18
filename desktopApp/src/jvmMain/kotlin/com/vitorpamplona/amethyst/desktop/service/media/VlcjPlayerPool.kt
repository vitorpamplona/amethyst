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
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    private val initAttempted = AtomicBoolean(false)
    private val initLatch = CountDownLatch(1)
    private var factory: MediaPlayerFactory? = null

    // Video player pool (for actual playback)
    private val allPlayers = mutableListOf<EmbeddedMediaPlayer>()
    private val idlePlayers = ConcurrentLinkedQueue<EmbeddedMediaPlayer>()
    private const val MAX_POOL_SIZE = 1

    // Thumbnail player pool (separate so thumbnails don't compete with playback)
    private val allThumbPlayers = mutableListOf<EmbeddedMediaPlayer>()
    private val idleThumbPlayers = ConcurrentLinkedQueue<EmbeddedMediaPlayer>()
    private const val MAX_THUMB_POOL_SIZE = 2

    // Audio player pool (shared factory with --no-video)
    private var audioFactory: MediaPlayerFactory? = null
    private val allAudioPlayers = mutableListOf<MediaPlayer>()
    private val idleAudioPlayers = ConcurrentLinkedQueue<MediaPlayer>()
    private const val MAX_AUDIO_POOL_SIZE = 1

    /**
     * Initialize the pool. Thread-safe — only runs once.
     * Returns false if VLC is not installed.
     */
    fun init(): Boolean {
        if (available.get()) return true

        // Only one thread performs init; others wait
        if (!initAttempted.compareAndSet(false, true)) {
            initLatch.await(10, TimeUnit.SECONDS)
            return available.get()
        }

        return try {
            // Try bundled VLC first, then fall through to system VLC
            val discovery =
                try {
                    val nd =
                        NativeDiscovery(
                            BundledVlcDiscoverer(),
                            MacOsVlcDiscoverer(),
                        )
                    val found = nd.discover()
                    if (found) {
                        println("VLC: bundled discovery succeeded at ${nd.discoveredPath()}")
                    } else {
                        println("VLC: bundled discovery failed, falling back to system VLC")
                    }
                    found
                } catch (e: Throwable) {
                    println("VLC: bundled discovery threw ${e.message}")
                    false
                }
            if (!discovery) {
                // Try default system discovery
                val systemDiscovery = NativeDiscovery().discover()
                println("VLC: system discovery ${if (systemDiscovery) "succeeded" else "failed"}")
            }
            val f = MediaPlayerFactory("--no-xlib")
            factory = f
            available.set(true)
            println("VLC: MediaPlayerFactory created successfully")
            true
        } catch (e: Throwable) {
            println("VLC: init failed — ${e.message}")
            available.set(false)
            false
        } finally {
            initLatch.countDown()
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
     * Acquire a video player from the pool or create a new one.
     * Returns null if VLC is not available or pool is at capacity.
     */
    fun acquire(): EmbeddedMediaPlayer? {
        if (!available.get()) return null
        val f = factory ?: return null

        synchronized(allPlayers) {
            idlePlayers.poll()?.let { return it }
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
     * Acquire a player dedicated to thumbnail extraction.
     * Separate pool so thumbnails don't compete with playback.
     */
    fun acquireForThumbnail(): EmbeddedMediaPlayer? {
        if (!available.get()) return null
        val f = factory ?: return null

        synchronized(allThumbPlayers) {
            idleThumbPlayers.poll()?.let { return it }
            if (allThumbPlayers.size >= MAX_THUMB_POOL_SIZE) {
                // Fall back to main pool if thumb pool is full
                return acquire()
            }
            return try {
                val player = f.mediaPlayers().newEmbeddedMediaPlayer()
                allThumbPlayers.add(player)
                player
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Acquire an audio-only player from the pool.
     * Uses a separate factory with --no-video for efficiency.
     */
    fun acquireAudioPlayer(): MediaPlayer? {
        if (!init()) return null

        synchronized(allAudioPlayers) {
            idleAudioPlayers.poll()?.let { return it }
            if (allAudioPlayers.size >= MAX_AUDIO_POOL_SIZE) return null

            val af =
                audioFactory ?: try {
                    MediaPlayerFactory("--no-video", "--no-xlib").also { audioFactory = it }
                } catch (_: Throwable) {
                    return null
                }

            return try {
                val player = af.mediaPlayers().newMediaPlayer()
                allAudioPlayers.add(player)
                player
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Return a video player to the pool for reuse.
     */
    fun release(player: EmbeddedMediaPlayer) {
        try {
            player.controls().stop()
            // Return to correct pool
            synchronized(allThumbPlayers) {
                if (player in allThumbPlayers) {
                    idleThumbPlayers.offer(player)
                    return
                }
            }
            idlePlayers.offer(player)
        } catch (_: Exception) {
            // Player may already be disposed
        }
    }

    /**
     * Return an audio player to the pool for reuse.
     */
    fun releaseAudioPlayer(player: MediaPlayer) {
        try {
            player.controls().stop()
            idleAudioPlayers.offer(player)
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
        synchronized(allThumbPlayers) {
            idleThumbPlayers.clear()
            for (player in allThumbPlayers) {
                try {
                    player.controls().stop()
                    player.release()
                } catch (_: Exception) {
                    // Ignore
                }
            }
            allThumbPlayers.clear()
        }
        synchronized(allAudioPlayers) {
            idleAudioPlayers.clear()
            for (player in allAudioPlayers) {
                try {
                    player.controls().stop()
                    player.release()
                } catch (_: Exception) {
                    // Ignore
                }
            }
            allAudioPlayers.clear()
        }
        try {
            factory?.release()
        } catch (_: Exception) {
            // Ignore
        }
        try {
            audioFactory?.release()
        } catch (_: Exception) {
            // Ignore
        }
        factory = null
        audioFactory = null
        available.set(false)
    }
}
