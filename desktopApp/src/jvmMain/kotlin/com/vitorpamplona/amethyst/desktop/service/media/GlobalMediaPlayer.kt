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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.ui.media.MediaType
import com.vitorpamplona.quartz.utils.Log
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.createVideoPlayerState
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Collections

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
    private const val TAG = "GlobalMediaPlayer"

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

    // How long a retired engine may take to finish an open still in flight before it goes anyway.
    private const val RETIRE_LOAD_TIMEOUT_MS = 15_000L

    // Engines being retired, so shutdown can release the ones whose delay has not run out.
    private val retiring: MutableSet<VideoPlayerState> = Collections.synchronizedSet(mutableSetOf())

    @Volatile private var shutDown = false

    // Engines opened ahead, paused and muted, on the videos the feeds are about to show, by URL
    // (see warmVideos). Guarded by initLock. They have no surface: only videoPlayer is ever drawn.
    private val warmEngines = LinkedHashMap<String, VideoPlayerState>()

    // What each feed asked to warm, most recent last.
    private val warmRequests = LinkedHashMap<Any, List<String>>()

    // How many videos to keep warm across all feeds: each is a native engine buffering a stream.
    private const val MAX_WARM_ENGINES = 2

    private const val CLOCK_POLL_MS = 250L

    /**
     * The kdroidFilter player driving currently-active or last-played video, or
     * `null` if the native player failed to initialize (e.g. missing GStreamer
     * on Linux). UI code reads this lazily; consumers must handle null by
     * showing the thumbnail/error fallback rather than mounting a surface.
     */
    val activeVideoPlayerState: VideoPlayerState?
        get() = videoPlayer

    private val _videoSurfaceOwner = MutableStateFlow<Any?>(null)

    /**
     * The one composable that draws the video engine: whoever last started it, or claimed it once
     * the last owner left. Two surfaces on one engine resize it from two places, which is the
     * Linux engine's use-after-free, and fight over the scale it renders at.
     */
    val videoSurfaceOwner: StateFlow<Any?> = _videoSurfaceOwner.asStateFlow()

    /** Makes [owner] the composable that draws the video, as [playVideo] does for its caller. */
    fun claimVideoSurface(owner: Any) {
        _videoSurfaceOwner.value = owner
    }

    /** Gives the video up when [owner] leaves, so another card showing the same video can draw it. */
    fun releaseVideoSurface(owner: Any) {
        _videoSurfaceOwner.compareAndSet(owner, null)
    }

    // Set by a pause, cleared by a play. A video still loading is not playing yet, so a pause then
    // cannot stop the engine, which starts on its own once loaded: the sync loop pauses it then.
    @Volatile private var videoPauseRequested = false

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
        owner: Any? = null,
    ) {
        // No owner (the now-playing bar, a full-screen request): whichever card shows it claims it.
        _videoSurfaceOwner.value = owner
        videoPauseRequested = false
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
            Log.d(TAG) { "playVideo reuse url=$url isPlaying=${current.isPlaying}" }
            if (startAt > 0f) loaded.seekTo(startAt * 1000f)
            if (!current.isPlaying) loaded.play()
            return
        }

        // The video being replaced picks up where it was when it comes back.
        rememberPosition(current)

        // Opened ahead while it was coming up the feed: it takes over, already buffered.
        val warm = synchronized(initLock) { warmEngines.remove(url) }
        if (warm != null) {
            if (warm.error == null) {
                startWarmVideo(warm, url, startAt)
                return
            }
            retireVideoPlayer(warm)
        }

        val player =
            replaceVideoPlayer(previousUrl = current.url.takeIf { current.errorReason == null }, nextUrl = url) ?: run {
                startVideoSync(null, url)
                _videoState.value =
                    MediaPlaybackState(
                        url = url,
                        type = MediaType.VIDEO,
                        isBuffering = false,
                        errorReason = "Video playback unavailable",
                    )
                return
            }

        Log.d(TAG) { "playVideo open url=$url (was=${current.url} err=${current.errorReason})" }

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
        // Only now, so the new engine's first report lands on the new video's state.
        startVideoSync(player, url)

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

    /**
     * Makes [player], opened paused ahead of time on [url], the playing engine: the one it replaces
     * retires, and it plays (from [startAt]) as soon as its open has the media, which it usually
     * already has.
     */
    private fun startWarmVideo(
        player: VideoPlayerState,
        url: String,
        startAt: Float,
    ) {
        Log.d(TAG) { "playVideo warm url=$url" }
        val previousUrl = _videoState.value.url
        val previous = synchronized(initLock) { videoPlayer.also { videoPlayer = player } }
        if (previous != null) parkOrRetire(previous, previousUrl, url)

        videoSeekWhilePaused = null
        videoDownloading = false
        val muted = defaultMuted
        if (muted) preMuteVideoVolume = 100
        player.volume = if (muted) 0f else 1f
        player.playbackSpeed = 1f

        _videoState.value =
            MediaPlaybackState(
                url = url,
                type = MediaType.VIDEO,
                isBuffering = !player.hasMedia,
                volume = if (muted) 0 else 100,
                isMuted = muted,
            )
        startVideoSync(player, url)

        videoOpenJob?.cancel()
        videoOpenJob =
            scope.launch(Dispatchers.Main) {
                snapshotFlow { player.hasMedia || player.error != null }.first { it }
                if (player.error != null || _videoState.value.url != url) return@launch
                if (startAt > 0f) player.seekTo(startAt * 1000f)
                if (!videoPauseRequested) player.play()
            }
    }

    /**
     * Opens the videos a feed is about to show ([urls], most wanted first) ahead of time, paused and
     * muted, so each starts at once when it scrolls into the middle of the window instead of only
     * then starting to download. [owner] is the feed asking: every feed on screen keeps its own list,
     * an empty one releases its videos, and the most recent request is served first. At most
     * [MAX_WARM_ENGINES] stay open; the playing video and encrypted blobs (which must download
     * whole before they open) are never warmed.
     */
    fun warmVideos(
        owner: Any,
        urls: List<String>,
    ) {
        if (shutDown) return
        scope.launch(Dispatchers.Main) {
            val playing = _videoState.value.url
            val (stale, fresh) =
                synchronized(initLock) {
                    warmRequests.remove(owner)
                    if (urls.isNotEmpty()) warmRequests[owner] = urls
                    val wanted = wantedWarm(playing)
                    val stale = warmEngines.filterKeys { it !in wanted }.values.toList()
                    warmEngines.keys.retainAll(wanted.toSet())
                    stale to wanted.filter { it !in warmEngines }
                }
            stale.forEach { retireVideoPlayer(it) }
            fresh.forEach { url -> openWarm(url) }
        }
    }

    /**
     * The videos to keep warm while [playing] plays: from the most recent feed's list first, the
     * ones nearest the playing video in it (the next, then the previous, and so on outward), since
     * those are the ones a scroll reaches. Callers hold [initLock].
     */
    private fun wantedWarm(playing: String?): List<String> =
        warmRequests.values
            .reversed()
            .flatMap { list -> list.nearest(playing) }
            .filter { it != playing && !MediaHttp.isEncrypted(it) }
            .distinct()
            .take(MAX_WARM_ENGINES)

    /** This list ordered by distance from [center] (after before before at a tie), or as is when [center] isn't in it. */
    private fun List<String>.nearest(center: String?): List<String> {
        val at = if (center == null) -1 else indexOf(center)
        if (at < 0) return this
        return indices.sortedBy { i -> if (i > at) (i - at) * 2 - 1 else (at - i) * 2 }.map { this[it] }
    }

    /**
     * Keeps [player], which was playing [url] and just gave way to another video, open and paused
     * when [url] is one of the videos to keep warm (the user scrolled just past it and may come
     * back); retires it otherwise.
     */
    private fun parkOrRetire(
        player: VideoPlayerState,
        url: String?,
        nowPlaying: String,
    ) {
        val parked =
            url != null &&
                player.error == null &&
                synchronized(initLock) {
                    if (url in wantedWarm(nowPlaying) && url !in warmEngines) {
                        warmEngines[url] = player
                        true
                    } else {
                        false
                    }
                }
        if (parked) {
            Log.d(TAG) { "park url=$url" }
            runCatching {
                player.volume = 0f
                player.pause()
            }
        } else {
            retireVideoPlayer(player)
        }
    }

    private fun openWarm(url: String) {
        if (shutDown) return
        val streaming = MediaRelay.streamingUrl(url) ?: return
        val engine =
            runCatching { createVideoPlayerState() }
                .onFailure { Log.w(TAG, "Warm video engine init failed", it) }
                .getOrNull() ?: return
        runCatching {
            engine.volume = 0f
            engine.playbackSpeed = 1f
        }
        synchronized(initLock) { warmEngines[url] = engine }
        Log.d(TAG) { "warm open url=$url" }
        scope.launch(Dispatchers.IO) {
            runCatching { engine.openUri(streaming, InitialPlayerState.PAUSE) }
                .onFailure { Log.w(TAG) { "Warm open failed for $url: ${it.message}" } }
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

        if (current.url == url && current.errorReason == null) {
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
        val state = _videoState.value
        // A video still loading counts as playing: it starts on its own once loaded.
        if (state.isPlaying || (state.isBuffering && !videoPauseRequested)) {
            pauseVideo()
        } else {
            videoPauseRequested = false
            player.play()
        }
    }

    /** Pauses the video, or keeps it from starting if it is still loading. */
    fun pauseVideo() {
        videoPauseRequested = true
        videoPlayer?.pause()
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
        _videoState.update { it.copy(position = target, currentTime = targetMillis) }
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
        _videoState.update { it.copy(speed = player.playbackSpeed) }
    }

    fun seekAudio(position: Float) {
        val target = position.coerceIn(0f, 1f)
        audioPlayer?.seekTo(target * 1000f)
        val state = _audioState.value
        val targetMillis = (target * state.duration).toLong()
        audioSeekWhilePaused = if (state.isPlaying) null else targetMillis
        _audioState.update { it.copy(position = target, currentTime = targetMillis) }
    }

    /** UI passes volume in 0..100. kdroidFilter wants 0..1. */
    fun setVideoVolume(volume: Int) {
        videoPlayer?.volume = volume.coerceIn(0, 100) / 100f
        _videoState.update { it.copy(volume = volume, isMuted = it.isMuted && volume == 0) }
        if (volume > 0) {
            preMuteVideoVolume = volume
            defaultMuted = false
        }
    }

    fun setAudioVolume(volume: Int) {
        audioPlayer?.volume = volume.coerceIn(0, 100) / 100f
        _audioState.update { it.copy(volume = volume, isMuted = it.isMuted && volume == 0) }
        if (volume > 0) preMuteAudioVolume = volume
    }

    /** kdroidFilter has no mute concept — emulate by stashing volume. */
    fun toggleVideoMute() {
        val state = _videoState.value
        if (state.isMuted) {
            setVideoVolume(preMuteVideoVolume)
            _videoState.update { it.copy(isMuted = false) }
        } else {
            preMuteVideoVolume = state.volume.coerceAtLeast(1)
            videoPlayer?.volume = 0f
            _videoState.update { it.copy(isMuted = true, volume = 0) }
            defaultMuted = true
        }
    }

    fun toggleAudioMute() {
        val state = _audioState.value
        if (state.isMuted) {
            setAudioVolume(preMuteAudioVolume)
            _audioState.update { it.copy(isMuted = false) }
        } else {
            preMuteAudioVolume = state.volume.coerceAtLeast(1)
            audioPlayer?.volume = 0f
            _audioState.update { it.copy(isMuted = true, volume = 0) }
        }
    }

    fun stopVideo() {
        videoOpenJob?.cancel()
        videoSyncJob?.cancel()
        // Retired rather than stopped: an open still in flight would start it again, unseen.
        synchronized(initLock) { videoPlayer.also { videoPlayer = null } }?.let(::retireVideoPlayer)
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

    /**
     * Where, in the window, the video that went full screen was shown: the full-screen view grows
     * out of it and shrinks back into it. Null when full screen started elsewhere (a plain fade).
     */
    @Volatile
    var fullscreenSourceBounds: Rect? = null
        private set

    /** The corner radius of [fullscreenSourceBounds]'s card. */
    @Volatile
    var fullscreenSourceCornerRadius: Dp = 0.dp
        private set

    fun toggleFullscreen() {
        if (!_isFullscreen.value) fullscreenSourceBounds = null
        _isFullscreen.value = !_isFullscreen.value
    }

    /** Full screen, growing out of [sourceBounds] (window coordinates, [cornerRadius]) when given. */
    fun enterFullscreen(
        sourceBounds: Rect?,
        cornerRadius: Dp = 0.dp,
    ) {
        fullscreenSourceBounds = sourceBounds
        fullscreenSourceCornerRadius = cornerRadius
        _isFullscreen.value = true
    }

    fun exitFullscreen() {
        _isFullscreen.value = false
    }

    /** Call on app exit. Disposes native handles owned by kdroidFilter. */
    fun shutdown() {
        shutDown = true
        videoSyncJob?.cancel()
        audioSyncJob?.cancel()
        runCatching { videoPlayer?.dispose() }
        runCatching { audioPlayer?.dispose() }
        // Their delayed release dies with the scope below.
        synchronized(retiring) { retiring.toList().also { retiring.clear() } }.forEach { runCatching { it.dispose() } }
        synchronized(initLock) {
            warmEngines.values.toList().also {
                warmEngines.clear()
                warmRequests.clear()
            }
        }.forEach { runCatching { it.dispose() } }
        videoPlayer = null
        audioPlayer = null
        _videoState.value = MediaPlaybackState()
        _audioState.value = MediaPlaybackState(type = MediaType.AUDIO)
        _isFullscreen.value = false
        scope.cancel()
    }

    // --- Engine lifecycle ----------------------------------------------------

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
                    Log.w(TAG) { "Could not fetch encrypted $url: ${e.message}" }
                    if (state.value.url == url) {
                        state.update { it.copy(isBuffering = false, errorReason = "Could not download the encrypted file") }
                    }
                    return false
                } finally {
                    // Unless a newer request already started its own download.
                    if (state.value.url == url) downloading(false)
                }
            } else {
                null
            }
        // A newer request took the engine over while this one was downloading.
        if (state.value.url != url) return false
        // Anything else streams from the engine's own HTTP stack, unless it goes over Tor: then
        // through the relay, so the app's Tor-routed client fetches it, as on Android.
        if (decrypted != null) {
            player.openFile(PlatformFile(decrypted))
            return true
        }
        val streaming = MediaRelay.streamingUrl(url)
        if (streaming == null) {
            // It should go over Tor, which the relay can only do for http(s).
            state.update { if (it.url == url) it.copy(isBuffering = false, errorReason = "This video can't be played over Tor") else it }
            return false
        }
        player.openUri(streaming)
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
    private fun replaceVideoPlayer(
        previousUrl: String? = null,
        nextUrl: String? = null,
    ): VideoPlayerState? {
        if (shutDown) return null
        val fresh =
            runCatching { createVideoPlayerState() }
                .onFailure { Log.w(TAG, "Video engine init failed", it) }
                .getOrNull() ?: return null
        val previous = synchronized(initLock) { videoPlayer.also { videoPlayer = fresh } }
        if (previous != null) {
            if (nextUrl != null) parkOrRetire(previous, previousUrl, nextUrl) else retireVideoPlayer(previous)
        }
        return fresh
    }

    /**
     * Silences [player] now and releases it once no surface can still be drawing it (the cards
     * showing it swap to the new engine on their next frame, well inside [RETIRE_DELAY_MS]) and no
     * open is still running on it: an open does not stop with its coroutine, it runs on the
     * engine's own, and would start the old video once loaded, or be cut off mid-call by dispose.
     * No stop() first: it queues native work the dispose right after would race.
     */
    private fun retireVideoPlayer(player: VideoPlayerState) {
        runCatching {
            player.volume = 0f
            player.pause()
        }
        retiring += player
        scope.launch(Dispatchers.Main) {
            delay(RETIRE_DELAY_MS)
            withTimeoutOrNull(RETIRE_LOAD_TIMEOUT_MS) { snapshotFlow { player.isLoading }.first { !it } }
            runCatching { player.pause() }
            if (retiring.remove(player)) runCatching { player.dispose() }
        }
    }

    private fun ensureAudioPlayer(): VideoPlayerState? {
        if (shutDown) return null
        return audioPlayer ?: synchronized(initLock) {
            audioPlayer ?: runCatching { createVideoPlayerState() }
                .onSuccess { startAudioSync(it) }
                .onFailure { Log.w(TAG, "Audio engine init failed", it) }
                .getOrNull()
                ?.also { audioPlayer = it }
        }
    }

    /**
     * Mirrors [player], the engine opened for [url], into the video state. Its reports apply only
     * while it is still the engine and [url] still the video: a retired engine, or one whose last
     * report races a newer [playVideo], must not write the old video's state over the new one.
     */
    private fun startVideoSync(
        player: VideoPlayerState?,
        url: String,
    ) {
        videoSyncJob?.cancel()
        if (player == null) return
        videoSyncJob =
            scope.launch {
                engineSnapshots(player).collect { raw ->
                    if (player !== videoPlayer) return@collect
                    if (raw.isPlaying) {
                        videoSeekWhilePaused = null
                        // It loaded and started on its own after the user paused it.
                        if (videoPauseRequested) player.pause()
                    }
                    val held = videoSeekWhilePaused
                    val snap = if (held != null && raw.currentTime <= 0.0) raw.copy(currentTime = held / 1000.0) else raw
                    _videoState.update { current ->
                        if (current.url != url) return@update current
                        current.copy(
                            isPlaying = snap.isPlaying,
                            isBuffering = snap.isLoading || videoDownloading,
                            duration = (snap.duration * 1000.0).toLong().coerceAtLeast(0L),
                            currentTime = (snap.currentTime * 1000.0).toLong().coerceAtLeast(0L),
                            position = snap.positionOr(current.position),
                            aspectRatio = if (snap.aspectRatio > 0f) snap.aspectRatio else current.aspectRatio,
                            errorReason = snap.errorMessage,
                        )
                    }
                }
            }
    }

    private fun startAudioSync(player: VideoPlayerState) {
        audioSyncJob?.cancel()
        audioSyncJob =
            scope.launch {
                engineSnapshots(player).collect { raw ->
                    if (player !== audioPlayer) return@collect
                    if (raw.isPlaying) audioSeekWhilePaused = null
                    val held = audioSeekWhilePaused
                    val snap = if (held != null && raw.currentTime <= 0.0) raw.copy(currentTime = held / 1000.0) else raw
                    _audioState.update { current ->
                        current.copy(
                            isPlaying = snap.isPlaying,
                            isBuffering = snap.isLoading || audioDownloading,
                            duration = (snap.duration * 1000.0).toLong().coerceAtLeast(0L),
                            currentTime = (snap.currentTime * 1000.0).toLong().coerceAtLeast(0L),
                            position = snap.positionOr(current.position),
                            errorReason = snap.errorMessage,
                        )
                    }
                }
            }
    }

    private fun EngineSnapshot.positionOr(fallback: Float): Float = if (duration > 0.0) (currentTime / duration).toFloat().coerceIn(0f, 1f) else fallback

    /**
     * The engine's state as it changes. Its flags are Compose state; its clock is not on Linux
     * (each read asks GStreamer) and is per-frame state on macOS and Windows, so watching it would
     * either miss the time moving or report every decoded frame. Only the flags are watched, and
     * while playing the clock is sampled every [CLOCK_POLL_MS].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun engineSnapshots(player: VideoPlayerState): Flow<EngineSnapshot> =
        merge(
            snapshotFlow { player.engineFlags() }.map { player.engineSnapshot() },
            snapshotFlow { player.isPlaying }.flatMapLatest { playing ->
                if (playing) {
                    flow {
                        while (true) {
                            delay(CLOCK_POLL_MS)
                            emit(player.engineSnapshot())
                        }
                    }
                } else {
                    emptyFlow()
                }
            },
        ).distinctUntilChanged()

    // Everything the snapshot holds but the clock: what is worth reacting to as soon as it changes.
    private fun VideoPlayerState.engineFlags() = listOf(isPlaying, isLoading, hasMedia, aspectRatio, error)

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
