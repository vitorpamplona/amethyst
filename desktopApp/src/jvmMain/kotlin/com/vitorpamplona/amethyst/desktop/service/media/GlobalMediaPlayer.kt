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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.vitorpamplona.amethyst.desktop.ui.media.MediaType
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.createVideoPlayerState
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.io.IOException

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
    /** Playback rate, 1 = normal. */
    val speed: Float = 1f,
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

    // In-flight `openUri` for the current video track. Cancelled before a new open so two rapid
    // track switches (e.g. opening one live stream while another is still loading) can't interleave
    // openUri calls on the single shared engine — that race left the player stuck / black.
    @Volatile private var videoOpenJob: Job? = null

    private val initLock = Any()

    private const val RETIRE_DELAY_MS = 2_000L

    private const val CLOCK_POLL_MS = 250L

    /**
     * The kdroidFilter player driving currently-active or last-played video, or
     * `null` if the native player failed to initialize (e.g. missing GStreamer
     * on Linux). UI code reads this lazily; consumers must handle null by
     * showing the thumbnail/error fallback rather than mounting a surface.
     */
    val activeVideoPlayerState: VideoPlayerState?
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

    /**
     * Whether a newly opened video starts muted. Starts true, as on Android, so a feed that
     * autoplays does not blast sound; every mute or unmute the user makes becomes the default
     * for the next video.
     */
    var defaultMuted: Boolean by mutableStateOf(true)
        private set

    /** Sets whether the next video starts muted, from a card that is not the loaded video. */
    fun setDefaultMute(muted: Boolean) {
        defaultMuted = muted
    }

    // Where each video was when [releaseVideo] let it go, as a 0..1 fraction, so coming back to it
    // resumes there instead of from the start. Bounded: only the most recent few are worth keeping.
    private val savedPositions =
        object : LinkedHashMap<String, Float>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > 64
        }
    private var preMuteAudioVolume: Int = 100

    private var videoSyncJob: Job? = null

    // Where a seek made while paused went, in ms. The Linux engine reports 0 while a paused seek
    // settles, so the controls show this target until the engine plays and reports again.
    @Volatile private var videoSeekWhilePaused: Long? = null

    @Volatile private var audioSeekWhilePaused: Long? = null

    // While an encrypted file downloads the engine has not started, so it reports not loading.
    @Volatile private var videoDownloading = false

    @Volatile private var audioDownloading = false
    private var audioSyncJob: Job? = null

    // --- Verbs ---------------------------------------------------------------

    fun playVideo(
        url: String,
        seekPosition: Float = 0f,
    ) {
        val startAt =
            if (seekPosition > 0f) {
                seekPosition
            } else {
                synchronized(savedPositions) { savedPositions.remove(url) } ?: 0f
            }
        val current = _videoState.value

        // Reuse the engine only when it's already on this URL AND healthy. If the last attempt
        // errored (a common transient for live HLS — a dead segment, a 403, a stream that just
        // went live), fall through and re-open instead of leaving a stuck/errored surface.
        val loaded = videoPlayer
        if (loaded != null && current.url == url && current.errorReason == null) {
            println("GlobalMediaPlayer.playVideo REUSE url=$url isPlaying=${current.isPlaying}")
            if (startAt > 0f) loaded.seekTo(startAt * 1000f)
            if (!current.isPlaying) loaded.play()
            return
        }

        // The video being replaced picks up where it was when it comes back.
        rememberPosition(current)

        val player =
            replaceVideoPlayer() ?: run {
                _videoState.value =
                    MediaPlaybackState(
                        url = url,
                        type = MediaType.VIDEO,
                        isBuffering = false,
                        errorReason = "Video playback unavailable",
                    )
                return
            }

        println("GlobalMediaPlayer.playVideo OPEN url=$url (was=${current.url} err=${current.errorReason})")

        // The kdroidFilter player retains `volume` and speed across openUri calls, so set both for
        // the new track: normal speed, and muted or not as the user last chose.
        videoSeekWhilePaused = null
        // Set before the engine's first report, which would otherwise clear the spinner.
        videoDownloading = MediaHttp.isEncrypted(url)
        val muted = defaultMuted
        if (muted) preMuteVideoVolume = 100
        player.volume = if (muted) 0f else 1f
        player.playbackSpeed = 1f

        _videoState.value =
            MediaPlaybackState(
                url = url,
                type = MediaType.VIDEO,
                isBuffering = true,
                volume = if (muted) 0 else 100,
                isMuted = muted,
            )

        // Cancel any in-flight open so a rapid switch can't interleave openUri on the shared engine.
        videoOpenJob?.cancel()
        videoOpenJob =
            scope.launch(Dispatchers.IO) {
                if (!openSource(player, url, _videoState) { videoDownloading = it }) return@launch
                // openUri auto-plays per InitialPlayerState.PLAY default. For an
                // initial seek we wait for the first hasMedia=true emission then
                // stop collecting (Flow.first terminates the collector cleanly,
                // unlike `return@collect` which only exits the lambda).
                if (startAt > 0f) {
                    snapshotFlow { player.hasMedia }.first { it }
                    player.seekTo(startAt * 1000f)
                }
            }
    }

    fun playAudio(url: String) {
        val current = _audioState.value
        val player =
            ensureAudioPlayer() ?: run {
                _audioState.value =
                    MediaPlaybackState(
                        url = url,
                        type = MediaType.AUDIO,
                        isBuffering = false,
                        errorReason = "Audio playback unavailable",
                    )
                return
            }

        if (current.url == url) {
            if (!current.isPlaying) player.play()
            return
        }

        // Reset engine volume — see playVideo() for rationale.
        player.volume = 1f

        audioSeekWhilePaused = null
        audioDownloading = MediaHttp.isEncrypted(url)
        _audioState.value = MediaPlaybackState(url = url, type = MediaType.AUDIO, isBuffering = true)

        scope.launch(Dispatchers.IO) {
            openSource(player, url, _audioState) { audioDownloading = it }
        }
    }

    fun toggleVideoPlayPause() {
        val player = videoPlayer ?: return
        if (_videoState.value.isPlaying) player.pause() else player.play()
    }

    fun pauseVideo() {
        if (_videoState.value.isPlaying) videoPlayer?.pause()
    }

    fun toggleAudioPlayPause() {
        val player = audioPlayer ?: return
        if (_audioState.value.isPlaying) player.pause() else player.play()
    }

    /** UI passes position in 0..1. kdroidFilter wants 0..1000. */
    fun seekVideo(position: Float) {
        val target = position.coerceIn(0f, 1f)
        videoPlayer?.seekTo(target * 1000f)
        val state = _videoState.value
        val targetMillis = (target * state.duration).toLong()
        videoSeekWhilePaused = if (state.isPlaying) null else targetMillis
        _videoState.value = state.copy(position = target, currentTime = targetMillis)
    }

    /** Moves the video [deltaMillis] forward (or back, when negative), within its length. */
    fun skipVideo(deltaMillis: Long) {
        val state = _videoState.value
        if (state.duration <= 0L) return
        val target = (state.currentTime + deltaMillis).coerceIn(0L, state.duration)
        seekVideo(target.toFloat() / state.duration)
    }

    fun setVideoSpeed(speed: Float) {
        val player = videoPlayer ?: return
        player.playbackSpeed = speed
        _videoState.value = _videoState.value.copy(speed = player.playbackSpeed)
    }

    fun seekAudio(position: Float) {
        val target = position.coerceIn(0f, 1f)
        audioPlayer?.seekTo(target * 1000f)
        val state = _audioState.value
        val targetMillis = (target * state.duration).toLong()
        audioSeekWhilePaused = if (state.isPlaying) null else targetMillis
        _audioState.value = state.copy(position = target, currentTime = targetMillis)
    }

    /** UI passes volume in 0..100. kdroidFilter wants 0..1. */
    fun setVideoVolume(volume: Int) {
        videoPlayer?.volume = volume.coerceIn(0, 100) / 100f
        val muted = _videoState.value.isMuted && volume == 0
        _videoState.value = _videoState.value.copy(volume = volume, isMuted = muted)
        if (volume > 0) {
            preMuteVideoVolume = volume
            defaultMuted = false
        }
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
            defaultMuted = true
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
        videoOpenJob?.cancel()
        videoPlayer?.stop()
        _videoState.value = MediaPlaybackState()
        _isFullscreen.value = false
    }

    /**
     * Lets go of [url] once nothing shows it any more, as Android releases a player whose card left
     * the screen: stops it if it is the loaded video and paused, keeping its position (as
     * [playVideo] does for a video it replaces) for when it comes back. One still playing (resumed
     * from the now-playing bar) or on screen in full screen is left alone.
     */
    fun releaseVideo(url: String) {
        val state = _videoState.value
        if (state.url != url || state.isPlaying || _isFullscreen.value) return
        rememberPosition(state)
        stopVideo()
    }

    /** Keeps where [state]'s video is, unless it has not started, has finished or failed. */
    private fun rememberPosition(state: MediaPlaybackState) {
        val url = state.url ?: return
        if (state.errorReason == null && state.position > 0f && state.position < 0.98f) {
            synchronized(savedPositions) { savedPositions[url] = state.position }
        }
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

    /**
     * Returns the video player, creating it on first call. Returns `null` if
     * native initialization throws (e.g. missing GStreamer on Linux, broken
     * `libNativeVideoPlayer.dylib` extraction). On failure, subsequent calls
     * keep returning `null` until the JVM is restarted — re-trying mid-session
     * is unlikely to recover from a missing native dependency.
     */
    private fun ensureVideoPlayer(): VideoPlayerState? =
        videoPlayer ?: synchronized(initLock) {
            videoPlayer ?: runCatching { createVideoPlayerState() }
                .onSuccess { startVideoSync(it) }
                .onFailure { println("kdroidFilter: video engine init failed: ${it.message}") }
                .getOrNull()
                ?.also { videoPlayer = it }
        }

    /**
     * Opens [url] on [player]: the decrypted copy for an encrypted blob (see
     * [DecryptedMediaFiles]; the engine would otherwise stream ciphertext), the URL itself for
     * everything else. Returns false, with the error on [state], when the blob cannot be fetched.
     */
    private suspend fun openSource(
        player: VideoPlayerState,
        url: String,
        state: MutableStateFlow<MediaPlaybackState>,
        downloading: (Boolean) -> Unit,
    ): Boolean {
        val decrypted =
            if (MediaHttp.isEncrypted(url)) {
                downloading(true)
                try {
                    DecryptedMediaFiles.fileFor(url)
                } catch (e: IOException) {
                    println("GlobalMediaPlayer: could not fetch encrypted $url: ${e.message}")
                    if (state.value.url == url) {
                        state.value = state.value.copy(isBuffering = false, errorReason = "Could not download the encrypted file")
                    }
                    return false
                } finally {
                    downloading(false)
                }
            } else {
                null
            }
        // A newer request took the engine over while this one was downloading.
        if (state.value.url != url) return false
        // Anything else streams from the engine's own HTTP stack, unless it goes over Tor: then
        // through the relay, so the app's Tor-routed client fetches it, as on Android.
        if (decrypted != null) player.openFile(PlatformFile(decrypted)) else player.openUri(MediaRelay.streamingUrl(url))
        return true
    }

    /**
     * A new engine for a newly opened video, retiring the one before it. kdroidFilter's Linux
     * player frees and reallocates its frame bitmaps on its own thread when the picture size
     * changes, so reusing one engine across videos of different sizes let a surface still drawing
     * the previous video's last frame read freed memory (a SIGSEGV in
     * `SkImages::RasterFromBitmap`) — routine once the feed switches videos as it scrolls. A fresh
     * engine has no previous frame to free.
     */
    private fun replaceVideoPlayer(): VideoPlayerState? {
        val fresh =
            runCatching { createVideoPlayerState() }
                .onFailure { println("kdroidFilter: video engine init failed: ${it.message}") }
                .getOrNull() ?: return null
        val previous = synchronized(initLock) { videoPlayer.also { videoPlayer = fresh } }
        startVideoSync(fresh)
        if (previous != null) retireVideoPlayer(previous)
        return fresh
    }

    /**
     * Silences [player] now and releases it once no surface can still be drawing it: the cards
     * showing it swap to the new engine on their next frame, well inside [RETIRE_DELAY_MS].
     */
    private fun retireVideoPlayer(player: VideoPlayerState) {
        runCatching { player.pause() }
        scope.launch(Dispatchers.Main) {
            delay(RETIRE_DELAY_MS)
            runCatching { player.stop() }
            runCatching { player.dispose() }
        }
    }

    private fun ensureAudioPlayer(): VideoPlayerState? =
        audioPlayer ?: synchronized(initLock) {
            audioPlayer ?: runCatching { createVideoPlayerState() }
                .onSuccess { startAudioSync(it) }
                .onFailure { println("kdroidFilter: audio engine init failed: ${it.message}") }
                .getOrNull()
                ?.also { audioPlayer = it }
        }

    private fun startVideoSync(player: VideoPlayerState) {
        videoSyncJob?.cancel()
        videoSyncJob =
            scope.launch {
                engineSnapshots(player).collect { raw ->
                    if (raw.isPlaying) videoSeekWhilePaused = null
                    val held = videoSeekWhilePaused
                    val snap = if (held != null && raw.currentTime <= 0.0) raw.copy(currentTime = held / 1000.0) else raw
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
                            isBuffering = snap.isLoading || videoDownloading,
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
                engineSnapshots(player).collect { raw ->
                    if (raw.isPlaying) audioSeekWhilePaused = null
                    val held = audioSeekWhilePaused
                    val snap = if (held != null && raw.currentTime <= 0.0) raw.copy(currentTime = held / 1000.0) else raw
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
                            isBuffering = snap.isLoading || audioDownloading,
                            duration = (snap.duration * 1000.0).toLong().coerceAtLeast(0L),
                            currentTime = (snap.currentTime * 1000.0).toLong().coerceAtLeast(0L),
                            position = posFraction,
                            errorReason = snap.errorMessage,
                        )
                }
            }
    }

    /**
     * The engine's state as it changes. Its flags are Compose state, but its clock is not on every
     * platform (the Linux engine asks GStreamer on each read of `currentTime`), so a snapshotFlow
     * alone saw the time move only when a flag flipped: the counter sat at 0:00 through playback.
     * While playing, the clock is also sampled every [CLOCK_POLL_MS].
     */
    private fun engineSnapshots(player: VideoPlayerState): Flow<EngineSnapshot> =
        merge(
            snapshotFlow { player.engineSnapshot() },
            flow {
                while (true) {
                    delay(CLOCK_POLL_MS)
                    if (player.isPlaying) emit(player.engineSnapshot())
                }
            },
        ).distinctUntilChanged()

    private fun VideoPlayerState.engineSnapshot() =
        EngineSnapshot(
            isPlaying = isPlaying,
            isLoading = isLoading,
            hasMedia = hasMedia,
            currentTime = currentTime,
            duration = duration,
            aspectRatio = aspectRatio,
            errorMessage = error?.let(::describeError),
        )

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
