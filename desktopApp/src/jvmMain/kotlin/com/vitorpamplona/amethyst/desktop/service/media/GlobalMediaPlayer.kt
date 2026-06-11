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

import androidx.compose.runtime.snapshotFlow
import com.vitorpamplona.amethyst.desktop.ui.media.MediaType
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.createVideoPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    /** Last observed error from the engine, or null. JVM emits only SourceError/UnknownError. */
    val errorReason: String? = null,
)

/**
 * Singleton facade over kdroidFilter's [VideoPlayerState] (MIT, OS-native backends:
 * Media Foundation on Windows, AVFoundation on macOS, GStreamer on Linux).
 *
 * Holds two engine instances (video + audio) for the lifetime of the JVM. The
 * underlying [VideoPlayerState] exposes its state via Compose `mutableStateOf`;
 * a [snapshotFlow] coroutine mirrors that into our public [MediaPlaybackState]
 * `StateFlow`s so non-Compose consumers (and the existing UI) remain unchanged.
 *
 * UI surface for visible video frames is rendered by mounting
 * `VideoPlayerSurface(playerState = [activeVideoPlayerState])` in the active
 * `DesktopVideoPlayer` instance — see that file for the active/inactive dispatch.
 */
object GlobalMediaPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Engine handles. Constructed lazily on first playback request so that app
    // start does not load the native library if the user never plays media.
    @Volatile private var videoPlayer: VideoPlayerState? = null

    @Volatile private var audioPlayer: VideoPlayerState? = null

    private val initLock = Any()

    /**
     * The kdroidFilter player driving currently-active or last-played video.
     * Mounted into a `VideoPlayerSurface(...)` by [DesktopVideoPlayer] when the
     * caller's `url` matches `videoState.value.url`.
     *
     * Lazy: first read constructs the underlying native player.
     */
    val activeVideoPlayerState: VideoPlayerState
        get() = ensureVideoPlayer()

    private val _videoState = MutableStateFlow(MediaPlaybackState())
    val videoState: StateFlow<MediaPlaybackState> = _videoState.asStateFlow()

    private val _audioState = MutableStateFlow(MediaPlaybackState(type = MediaType.AUDIO))
    val audioState: StateFlow<MediaPlaybackState> = _audioState.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    // Stashed pre-mute volume (0..100). kdroidFilter has no isMuted concept, so
    // we emulate by zeroing volume and remembering the prior value.
    private var preMuteVideoVolume: Int = 100
    private var preMuteAudioVolume: Int = 100

    private var videoSyncJob: Job? = null
    private var audioSyncJob: Job? = null

    // --- Verbs ---------------------------------------------------------------

    fun playVideo(
        url: String,
        seekPosition: Float = 0f,
    ) {
        val current = _videoState.value
        val player = ensureVideoPlayer()

        if (current.url == url) {
            if (seekPosition > 0f) player.seekTo(seekPosition * 1000f)
            if (!current.isPlaying) player.play()
            return
        }

        _videoState.value = MediaPlaybackState(url = url, type = MediaType.VIDEO, isBuffering = true)

        scope.launch(Dispatchers.IO) {
            player.openUri(url)
            // openUri auto-plays per InitialPlayerState.PLAY default.
            // For an initial seek we wait for hasMedia=true; cleanest is a
            // one-shot snapshotFlow collector that seeks then completes.
            if (seekPosition > 0f) {
                snapshotFlow { player.hasMedia }
                    .collect { ready ->
                        if (ready) {
                            player.seekTo(seekPosition * 1000f)
                            return@collect
                        }
                    }
            }
        }
    }

    fun playAudio(url: String) {
        val current = _audioState.value
        val player = ensureAudioPlayer()

        if (current.url == url) {
            if (!current.isPlaying) player.play()
            return
        }

        _audioState.value = MediaPlaybackState(url = url, type = MediaType.AUDIO, isBuffering = true)

        scope.launch(Dispatchers.IO) {
            player.openUri(url)
        }
    }

    fun toggleVideoPlayPause() {
        val player = videoPlayer ?: return
        if (_videoState.value.isPlaying) player.pause() else player.play()
    }

    fun toggleAudioPlayPause() {
        val player = audioPlayer ?: return
        if (_audioState.value.isPlaying) player.pause() else player.play()
    }

    /** UI passes position in 0..1. kdroidFilter wants 0..1000. */
    fun seekVideo(position: Float) {
        videoPlayer?.seekTo((position * 1000f).coerceIn(0f, 1000f))
    }

    fun seekAudio(position: Float) {
        audioPlayer?.seekTo((position * 1000f).coerceIn(0f, 1000f))
    }

    /** UI passes volume in 0..100. kdroidFilter wants 0..1. */
    fun setVideoVolume(volume: Int) {
        videoPlayer?.volume = volume.coerceIn(0, 100) / 100f
        val muted = _videoState.value.isMuted && volume == 0
        _videoState.value = _videoState.value.copy(volume = volume, isMuted = muted)
        if (volume > 0) preMuteVideoVolume = volume
    }

    fun setAudioVolume(volume: Int) {
        audioPlayer?.volume = volume.coerceIn(0, 100) / 100f
        val muted = _audioState.value.isMuted && volume == 0
        _audioState.value = _audioState.value.copy(volume = volume, isMuted = muted)
        if (volume > 0) preMuteAudioVolume = volume
    }

    /** kdroidFilter has no mute concept — emulate by stashing volume. */
    fun toggleVideoMute() {
        val state = _videoState.value
        if (state.isMuted) {
            setVideoVolume(preMuteVideoVolume)
            _videoState.value = _videoState.value.copy(isMuted = false)
        } else {
            preMuteVideoVolume = state.volume.coerceAtLeast(1)
            videoPlayer?.volume = 0f
            _videoState.value = _videoState.value.copy(isMuted = true, volume = 0)
        }
    }

    fun toggleAudioMute() {
        val state = _audioState.value
        if (state.isMuted) {
            setAudioVolume(preMuteAudioVolume)
            _audioState.value = _audioState.value.copy(isMuted = false)
        } else {
            preMuteAudioVolume = state.volume.coerceAtLeast(1)
            audioPlayer?.volume = 0f
            _audioState.value = _audioState.value.copy(isMuted = true, volume = 0)
        }
    }

    fun stopVideo() {
        videoPlayer?.stop()
        _videoState.value = MediaPlaybackState()
        _isFullscreen.value = false
    }

    fun stopAudio() {
        audioPlayer?.stop()
        _audioState.value = MediaPlaybackState(type = MediaType.AUDIO)
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun exitFullscreen() {
        _isFullscreen.value = false
    }

    /** Call on app exit. Disposes native handles owned by kdroidFilter. */
    fun shutdown() {
        videoSyncJob?.cancel()
        audioSyncJob?.cancel()
        runCatching { videoPlayer?.stop() }
        runCatching { videoPlayer?.dispose() }
        runCatching { audioPlayer?.stop() }
        runCatching { audioPlayer?.dispose() }
        videoPlayer = null
        audioPlayer = null
        _videoState.value = MediaPlaybackState()
        _audioState.value = MediaPlaybackState(type = MediaType.AUDIO)
        _isFullscreen.value = false
        scope.cancel()
    }

    // --- Engine lifecycle ----------------------------------------------------

    private fun ensureVideoPlayer(): VideoPlayerState =
        videoPlayer ?: synchronized(initLock) {
            videoPlayer ?: createVideoPlayerState().also {
                videoPlayer = it
                startVideoSync(it)
            }
        }

    private fun ensureAudioPlayer(): VideoPlayerState =
        audioPlayer ?: synchronized(initLock) {
            audioPlayer ?: createVideoPlayerState().also {
                audioPlayer = it
                startAudioSync(it)
            }
        }

    private fun startVideoSync(player: VideoPlayerState) {
        videoSyncJob?.cancel()
        videoSyncJob =
            scope.launch {
                snapshotFlow {
                    EngineSnapshot(
                        isPlaying = player.isPlaying,
                        isLoading = player.isLoading,
                        hasMedia = player.hasMedia,
                        currentTime = player.currentTime,
                        duration = player.duration,
                        aspectRatio = player.aspectRatio,
                        errorMessage = player.error?.let(::describeError),
                    )
                }.collect { snap ->
                    val current = _videoState.value
                    val posFraction =
                        if (snap.duration > 0.0) {
                            (snap.currentTime / snap.duration).toFloat().coerceIn(0f, 1f)
                        } else {
                            current.position
                        }
                    _videoState.value =
                        current.copy(
                            isPlaying = snap.isPlaying,
                            isBuffering = snap.isLoading,
                            duration = (snap.duration * 1000.0).toLong().coerceAtLeast(0L),
                            currentTime = (snap.currentTime * 1000.0).toLong().coerceAtLeast(0L),
                            position = posFraction,
                            aspectRatio = if (snap.aspectRatio > 0f) snap.aspectRatio else current.aspectRatio,
                            errorReason = snap.errorMessage,
                        )
                }
            }
    }

    private fun startAudioSync(player: VideoPlayerState) {
        audioSyncJob?.cancel()
        audioSyncJob =
            scope.launch {
                snapshotFlow {
                    EngineSnapshot(
                        isPlaying = player.isPlaying,
                        isLoading = player.isLoading,
                        hasMedia = player.hasMedia,
                        currentTime = player.currentTime,
                        duration = player.duration,
                        aspectRatio = player.aspectRatio,
                        errorMessage = player.error?.let(::describeError),
                    )
                }.collect { snap ->
                    val current = _audioState.value
                    val posFraction =
                        if (snap.duration > 0.0) {
                            (snap.currentTime / snap.duration).toFloat().coerceIn(0f, 1f)
                        } else {
                            current.position
                        }
                    _audioState.value =
                        current.copy(
                            isPlaying = snap.isPlaying,
                            isBuffering = snap.isLoading,
                            duration = (snap.duration * 1000.0).toLong().coerceAtLeast(0L),
                            currentTime = (snap.currentTime * 1000.0).toLong().coerceAtLeast(0L),
                            position = posFraction,
                            errorReason = snap.errorMessage,
                        )
                }
            }
    }

    private fun describeError(error: VideoPlayerError): String =
        when (error) {
            is VideoPlayerError.CodecError -> "Codec: ${error.message}"
            is VideoPlayerError.NetworkError -> "Network: ${error.message}"
            is VideoPlayerError.SourceError -> "Source: ${error.message}"
            is VideoPlayerError.UnknownError -> error.message
        }

    private data class EngineSnapshot(
        val isPlaying: Boolean,
        val isLoading: Boolean,
        val hasMedia: Boolean,
        val currentTime: Double,
        val duration: Double,
        val aspectRatio: Float,
        val errorMessage: String?,
    )
}
