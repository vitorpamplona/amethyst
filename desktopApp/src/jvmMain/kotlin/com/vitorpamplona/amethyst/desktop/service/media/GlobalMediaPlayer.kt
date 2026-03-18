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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.vitorpamplona.amethyst.desktop.ui.media.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import org.jetbrains.skia.Image as SkiaImage

data class MediaPlaybackState(
    val url: String? = null,
    val type: MediaType = MediaType.VIDEO,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val position: Float = 0f,
    val duration: Long = 0L,
    val currentTime: Long = 0L,
    val aspectRatio: Float = 16f / 9f,
    val volume: Int = 100,
    val isMuted: Boolean = false,
)

object GlobalMediaPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Video state
    private val _videoFrame = MutableStateFlow<ImageBitmap?>(null)
    val videoFrame: StateFlow<ImageBitmap?> = _videoFrame.asStateFlow()

    private val _videoState = MutableStateFlow(MediaPlaybackState())
    val videoState: StateFlow<MediaPlaybackState> = _videoState.asStateFlow()

    // Audio state
    private val _audioState = MutableStateFlow(MediaPlaybackState(type = MediaType.AUDIO))
    val audioState: StateFlow<MediaPlaybackState> = _audioState.asStateFlow()

    // Fullscreen
    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    // VLC players — kept alive between plays
    private var videoPlayer: EmbeddedMediaPlayer? = null
    private var audioPlayer: MediaPlayer? = null

    // Skia bitmap for video rendering
    private var skBitmap: Bitmap? = null
    private var pixelBytes: ByteArray? = null

    // Position polling job
    private var videoPollingJob: Job? = null
    private var audioPollingJob: Job? = null

    fun playVideo(
        url: String,
        seekPosition: Float = 0f,
    ) {
        // If already playing this URL, just seek
        val current = _videoState.value
        if (current.url == url && videoPlayer != null) {
            if (seekPosition > 0f) {
                videoPlayer?.controls()?.setPosition(seekPosition)
            }
            if (!current.isPlaying) {
                videoPlayer?.controls()?.play()
            }
            return
        }

        // Stop current video if different URL
        if (current.url != null && current.url != url) {
            videoPlayer?.controls()?.stop()
        }

        _videoState.value =
            MediaPlaybackState(
                url = url,
                type = MediaType.VIDEO,
                isBuffering = true,
            )

        scope.launch(Dispatchers.IO) {
            if (!VlcjPlayerPool.init()) {
                _videoState.value = _videoState.value.copy(isBuffering = false)
                return@launch
            }

            val player =
                videoPlayer ?: VlcjPlayerPool.acquire() ?: run {
                    _videoState.value = _videoState.value.copy(isBuffering = false)
                    return@launch
                }

            // Only set up surface on first acquisition
            if (videoPlayer == null) {
                setupVideoSurface(player)
                setupVideoEventListener(player)
                videoPlayer = player
            }

            var didSeek = seekPosition <= 0f

            // Temporary listener for initial seek
            if (!didSeek) {
                val seekListener =
                    object : MediaPlayerEventAdapter() {
                        override fun playing(mediaPlayer: MediaPlayer) {
                            if (!didSeek) {
                                didSeek = true
                                mediaPlayer.controls().setPosition(seekPosition)
                                mediaPlayer.events().removeMediaPlayerEventListener(this)
                            }
                        }
                    }
                player.events().addMediaPlayerEventListener(seekListener)
            }

            player.media().play(url)
            // Set volume after play — VLC resets volume on new media
            player.audio().setVolume(_videoState.value.volume)
            startVideoPolling()
        }
    }

    fun playAudio(url: String) {
        val current = _audioState.value
        if (current.url == url && audioPlayer != null) {
            if (!current.isPlaying) {
                audioPlayer?.controls()?.play()
            }
            return
        }

        if (current.url != null && current.url != url) {
            audioPlayer?.controls()?.stop()
        }

        _audioState.value =
            MediaPlaybackState(
                url = url,
                type = MediaType.AUDIO,
                isBuffering = true,
            )

        scope.launch(Dispatchers.IO) {
            val player =
                audioPlayer ?: VlcjPlayerPool.acquireAudioPlayer() ?: run {
                    _audioState.value = _audioState.value.copy(isBuffering = false)
                    return@launch
                }

            if (audioPlayer == null) {
                setupAudioEventListener(player)
                audioPlayer = player
            }

            player.media().play(url)
            // Set volume after play — VLC resets volume on new media
            player.audio().setVolume(_audioState.value.volume)
            startAudioPolling()
        }
    }

    fun toggleVideoPlayPause() {
        val player = videoPlayer ?: return
        val state = _videoState.value
        if (state.url == null) return

        if (state.isPlaying) {
            player.controls().pause()
        } else {
            if (state.position <= 0f && !player.status().isPlaying) {
                state.url.let { player.media().play(it) }
            } else {
                player.controls().play()
            }
        }
    }

    fun toggleAudioPlayPause() {
        val player = audioPlayer ?: return
        val state = _audioState.value
        if (state.url == null) return

        if (state.isPlaying) {
            player.controls().pause()
        } else {
            if (state.position <= 0f && !player.status().isPlaying) {
                state.url.let { player.media().play(it) }
            } else {
                player.controls().play()
            }
        }
    }

    fun seekVideo(position: Float) {
        videoPlayer?.controls()?.setPosition(position)
    }

    fun seekAudio(position: Float) {
        audioPlayer?.controls()?.setPosition(position)
    }

    fun setVideoVolume(volume: Int) {
        videoPlayer?.audio()?.setVolume(volume)
        _videoState.value = _videoState.value.copy(volume = volume)
    }

    fun setAudioVolume(volume: Int) {
        audioPlayer?.audio()?.setVolume(volume)
        _audioState.value = _audioState.value.copy(volume = volume)
    }

    fun toggleVideoMute() {
        val muted = !_videoState.value.isMuted
        videoPlayer?.audio()?.isMute = muted
        _videoState.value = _videoState.value.copy(isMuted = muted)
    }

    fun toggleAudioMute() {
        val muted = !_audioState.value.isMuted
        audioPlayer?.audio()?.isMute = muted
        _audioState.value = _audioState.value.copy(isMuted = muted)
    }

    fun stopVideo() {
        videoPollingJob?.cancel()
        videoPollingJob = null
        videoPlayer?.controls()?.stop()
        _videoState.value = MediaPlaybackState()
        _videoFrame.value = null
        _isFullscreen.value = false
    }

    fun stopAudio() {
        audioPollingJob?.cancel()
        audioPollingJob = null
        audioPlayer?.controls()?.stop()
        _audioState.value = MediaPlaybackState(type = MediaType.AUDIO)
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun exitFullscreen() {
        _isFullscreen.value = false
    }

    fun shutdown() {
        videoPollingJob?.cancel()
        audioPollingJob?.cancel()

        videoPlayer?.let { p ->
            try {
                p.controls().stop()
            } catch (_: Exception) {
            }
            VlcjPlayerPool.release(p)
        }
        videoPlayer = null

        audioPlayer?.let { p ->
            try {
                p.controls().stop()
            } catch (_: Exception) {
            }
            VlcjPlayerPool.releaseAudioPlayer(p)
        }
        audioPlayer = null

        _videoState.value = MediaPlaybackState()
        _audioState.value = MediaPlaybackState(type = MediaType.AUDIO)
        _videoFrame.value = null
        _isFullscreen.value = false

        scope.cancel()
    }

    private fun setupVideoSurface(player: EmbeddedMediaPlayer) {
        val bufferFormatCallback =
            object : BufferFormatCallback {
                override fun getBufferFormat(
                    sourceWidth: Int,
                    sourceHeight: Int,
                ): BufferFormat {
                    if (sourceHeight > 0) {
                        _videoState.value =
                            _videoState.value.copy(
                                aspectRatio = sourceWidth.toFloat() / sourceHeight.toFloat(),
                            )
                    }
                    val bmp = Bitmap()
                    bmp.allocPixels(ImageInfo.makeN32(sourceWidth, sourceHeight, ColorAlphaType.PREMUL))
                    skBitmap = bmp
                    pixelBytes = ByteArray(sourceWidth * sourceHeight * 4)
                    return RV32BufferFormat(sourceWidth, sourceHeight)
                }

                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
            }

        val renderCallback =
            RenderCallback { _, nativeBuffers, _ ->
                val bmp = skBitmap ?: return@RenderCallback
                val bytes = pixelBytes ?: return@RenderCallback
                val buffer = nativeBuffers[0]
                buffer.rewind()
                buffer.get(bytes)
                bmp.installPixels(bytes)
                _videoFrame.value = SkiaImage.makeFromBitmap(bmp).toComposeImageBitmap()
            }

        val surface = VlcjPlayerPool.createVideoSurface(bufferFormatCallback, renderCallback)
        player.videoSurface().set(surface)
    }

    private fun setupVideoEventListener(player: EmbeddedMediaPlayer) {
        player.events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    val state = _videoState.value
                    _videoState.value =
                        state.copy(
                            isPlaying = true,
                            isBuffering = false,
                            duration = mediaPlayer.status().length(),
                        )
                    // Enforce volume — VLC can reset it on new media
                    mediaPlayer.audio().setVolume(state.volume)
                    mediaPlayer.audio().isMute = state.isMuted
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    _videoState.value = _videoState.value.copy(isPlaying = false)
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    _videoState.value = _videoState.value.copy(isPlaying = false, isBuffering = false)
                }

                override fun buffering(
                    mediaPlayer: MediaPlayer,
                    newCache: Float,
                ) {
                    _videoState.value = _videoState.value.copy(isBuffering = newCache < 100f)
                }

                override fun positionChanged(
                    mediaPlayer: MediaPlayer,
                    newPosition: Float,
                ) {
                    _videoState.value =
                        _videoState.value.copy(
                            position = newPosition,
                            currentTime = (newPosition * _videoState.value.duration).toLong(),
                        )
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    _videoState.value =
                        _videoState.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            position = 0f,
                            currentTime = 0L,
                        )
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    _videoState.value = _videoState.value.copy(isBuffering = false)
                    println("VLC: playback error for ${_videoState.value.url}")
                }
            },
        )
    }

    private fun setupAudioEventListener(player: MediaPlayer) {
        player.events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    val state = _audioState.value
                    _audioState.value =
                        state.copy(
                            isPlaying = true,
                            isBuffering = false,
                            duration = mediaPlayer.status().length(),
                        )
                    // Enforce volume — VLC can reset it on new media
                    mediaPlayer.audio().setVolume(state.volume)
                    mediaPlayer.audio().isMute = state.isMuted
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    _audioState.value = _audioState.value.copy(isPlaying = false)
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    _audioState.value = _audioState.value.copy(isPlaying = false, isBuffering = false)
                }

                override fun positionChanged(
                    mediaPlayer: MediaPlayer,
                    newPosition: Float,
                ) {
                    _audioState.value =
                        _audioState.value.copy(
                            position = newPosition,
                            currentTime = (newPosition * _audioState.value.duration).toLong(),
                        )
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    _audioState.value =
                        _audioState.value.copy(
                            isPlaying = false,
                            position = 0f,
                            currentTime = 0L,
                        )
                }
            },
        )
    }

    private fun startVideoPolling() {
        videoPollingJob?.cancel()
        videoPollingJob =
            scope.launch {
                while (true) {
                    delay(500)
                    val player = videoPlayer ?: break
                    val state = _videoState.value
                    if (state.isPlaying) {
                        try {
                            _videoState.value =
                                state.copy(
                                    position = player.status().position(),
                                    currentTime = player.status().time(),
                                )
                        } catch (_: Exception) {
                        }
                    }
                }
            }
    }

    private fun startAudioPolling() {
        audioPollingJob?.cancel()
        audioPollingJob =
            scope.launch {
                while (true) {
                    delay(500)
                    val player = audioPlayer ?: break
                    val state = _audioState.value
                    if (state.isPlaying) {
                        try {
                            _audioState.value =
                                state.copy(
                                    position = player.status().position(),
                                    currentTime = player.status().time(),
                                )
                        } catch (_: Exception) {
                        }
                    }
                }
            }
    }
}
