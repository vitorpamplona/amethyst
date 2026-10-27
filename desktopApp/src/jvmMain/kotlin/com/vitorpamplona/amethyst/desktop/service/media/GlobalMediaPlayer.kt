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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.service.playback.VideoViewedPositionCache
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.amethyst.desktop.service.media.BackgroundOpenGate.Verdict
import com.vitorpamplona.amethyst.desktop.ui.media.MediaType
import com.vitorpamplona.quartz.utils.Log
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.audio.AudioPlayer
import io.github.kdroidfilter.composemediaplayer.audio.AudioPlayerState
import io.github.kdroidfilter.composemediaplayer.audio.ErrorListener
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs

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
    /**
     * Whether the user asked for this video (pressed play, opened it, or unmuted it) rather than
     * the feed starting it on its own. Only such a video keeps playing once its screen closes.
     */
    val isChosen: Boolean = false,
    /**
     * The card that plays it, or null when none does (the now-playing bar, full screen, a card that
     * left). The same video can be on screen in many cards (a note and its quotes, the same file
     * posted again), each with its own engine: only this one shows this state and drives it.
     */
    val owner: Any? = null,
)

/**
 * Singleton facade over kdroidFilter's players (MIT): [VideoPlayerState] for video (OS-native
 * backends: Media Foundation on Windows, AVFoundation on macOS, GStreamer on Linux) and its
 * audio-only [AudioPlayer] (Rodio) for audio, which the video engine cannot play on macOS and
 * Windows without a video track.
 *
 * The video engine exposes its state via Compose `mutableStateOf`, which a [snapshotFlow]
 * coroutine mirrors into [videoState]; the audio engine has none, so it is polled into
 * [audioState]. Both are `StateFlow`s so non-Compose consumers (and the existing UI) remain
 * unchanged.
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

    // Audio has its own engine: the video one needs a video track on macOS and Windows and fails
    // every audio-only file there ("Failed to retrieve video size").
    @Volatile private var audioPlayer: AudioPlayer? = null

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

    // Every engine retired so far, so an open still held back (see holdBehindPlayingVideo) never
    // runs on one that is gone. Weak: a disposed engine is not kept alive by it.
    private val retired: MutableSet<VideoPlayerState> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    // The longest a paused engine waits for the playing video to start before opening its own.
    private const val BACKGROUND_OPEN_HOLD_MS = 5_000L

    // How long, with no video open, a paused engine waits for one to start (see BackgroundOpenGate).
    private const val IDLE_OPEN_DELAY_MS = 1_000L

    // Engines whose paused open is still held back, so a card waiting on one's frame counts from the open.
    private val heldEngines = MutableStateFlow<Set<VideoPlayerState>>(emptySet())

    @Volatile private var shutDown = false

    // Engines opened ahead, paused and muted, on the videos the feeds are about to show, by URL
    // (see warmVideos). Guarded by initLock. They have no surface: only videoPlayer is ever drawn.
    private val warmEngines = LinkedHashMap<String, VideoPlayerState>()

    // What each feed asked to warm, most recent last.
    private val warmRequests = LinkedHashMap<Any, List<String>>()

    // How many videos to keep warm across all feeds: each is a native engine buffering a stream.
    private const val MAX_WARM_ENGINES = 2

    private class CardEngine(
        val url: String,
        val engine: VideoPlayerState,
    )

    // One engine per video card on screen, keyed by the card (see attachCard), as Android gives each
    // video composable its own player. The card nearest the middle plays its own; the others stay
    // paused where they stopped, so a card that gets the middle back carries on from there with no
    // reload or seek. Guarded by initLock.
    private val cardEngines = HashMap<Any, CardEngine>()

    private val _cardPlayers = MutableStateFlow<Map<Any, VideoPlayerState>>(emptyMap())

    /** Each card's own engine, by the card's owner key, for the card to draw. */
    val cardPlayers: StateFlow<Map<Any, VideoPlayerState>> = _cardPlayers.asStateFlow()

    /**
     * Whether an engine opened paused draws its video's first frame. kdroidFilter's Linux engine
     * (GStreamer) makes frames only while it plays, so a paused card engine there shows nothing:
     * its card needs a poster.
     */
    val pausedEngineShowsFrame: Boolean = !PlatformInfo.host.isLinux

    // A ceiling on those: more video cards than this are rarely on screen at once, and each is a
    // native engine holding its buffer. A card past it opens its video only when it plays.
    private const val MAX_CARD_ENGINES = 8

    private const val CLOCK_POLL_MS = 250L

    // From here on a video counts as played to its end: play starts it over. The same mark the
    // cards use to keep an ended video from starting again on its own.
    internal const val ENDED_POSITION = 0.99f

    // How long publishShape waits for an engine to read a video's size before giving up.
    private const val SHAPE_WAIT_MS = 30_000L

    // The ratio an engine reports before it knows the video's.
    private const val ENGINE_DEFAULT_RATIO = 16f / 9f

    // How long a seek waits for a track still opening to learn its length before it gives up.
    private const val AUDIO_OPEN_TIMEOUT_MS = 30_000L

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

    // The video each engine opened, so whatever sets an engine aside or releases it can save where
    // that video was in [VideoViewedPositionCache], as Android's players do. Weak: an engine that is
    // gone needs no entry.
    private val engineUrls: MutableMap<VideoPlayerState, String> = Collections.synchronizedMap(WeakHashMap())
    private var preMuteAudioVolume: Int = 100

    private var videoSyncJob: Job? = null

    // Where a seek made while paused went, in ms. The Linux engine reports 0 while a paused seek
    // settles, so the controls show this target until the engine plays and reports again.
    @Volatile private var videoSeekWhilePaused: Long? = null

    // The open running on the audio engine. It applies [pendingAudioStartMillis] once the track has
    // loaded, and the next track cancels it, so a seek meant for one never lands on another.
    @Volatile private var audioOpenJob: Job? = null

    // Where the opening track should start, in ms: set by the soundbite or chapter that opened it, or
    // by one tapped while it was still loading.
    @Volatile private var pendingAudioStartMillis: Long? = null

    // While an encrypted file downloads the engine has not started, so it reports not loading.
    @Volatile private var videoDownloading = false

    @Volatile private var audioDownloading = false

    // From handing a track to the audio engine until it reports connecting or playing: the engine
    // says idle until then, which would read as stopped.
    @Volatile private var audioStarting = false

    // The audio engine reports errors through a listener, not its state.
    @Volatile private var audioError: String? = null
    private var audioSyncJob: Job? = null

    // --- Verbs ---------------------------------------------------------------

    fun playVideo(
        url: String,
        seekPosition: Float = 0f,
        owner: Any? = null,
        chosen: Boolean = false,
    ) {
        // No owner (the now-playing bar, a full-screen request): whichever card shows it claims it.
        _videoSurfaceOwner.value = owner
        videoPauseRequested = false
        // Only an explicit start (full screen, the lightbox): every engine otherwise seeks back to
        // where the video was itself, once it has loaded it (see restorePosition).
        val startAt = seekPosition
        val current = _videoState.value

        // Reuse the engine only when it's already on this URL AND healthy. If the last attempt
        // errored (a common transient for live HLS — a dead segment, a 403, a stream that just
        // went live), fall through and re-open instead of leaving a stuck/errored surface. Not
        // when another card holds it: play on one copy of a video must not start or stop another.
        val loaded = videoPlayer
        if (loaded != null && current.url == url && current.errorReason == null && !isHeldByOtherCard(loaded, owner)) {
            Log.d(TAG) { "playVideo reuse url=$url isPlaying=${current.isPlaying}" }
            if (owner != null) _videoState.update { it.copy(owner = owner) }
            if (startAt > 0f) {
                loaded.seekTo(startAt * 1000f)
            } else if (chosen && !current.isPlaying && current.position >= ENDED_POSITION) {
                // Pressed play on it after it ended: start over.
                loaded.seekTo(0f)
            }
            if (!current.isPlaying) loaded.play()
            if (chosen) _videoState.update { it.copy(isChosen = true) }
            return
        }

        // The card's own engine, or, without a card (full screen, the now-playing bar), the engine
        // of a card showing this video: the picture on screen is the one that plays. A card never
        // takes another card's engine, even on the same video.
        val card =
            synchronized(initLock) {
                if (owner != null) {
                    cardEngines[owner]?.takeIf { it.url == url }?.let { owner to it.engine }
                } else {
                    cardEngines.entries.firstOrNull { it.value.url == url }?.let { it.key to it.value.engine }
                }
            }
        if (card != null && card.second.error == null) {
            startWarmVideo(card.second, url, startAt, chosen, card.first)
            return
        }

        // Opened ahead while it was coming up the feed: it takes over, already buffered.
        val warm = synchronized(initLock) { warmEngines.remove(url) }
        if (warm != null) {
            if (warm.error == null) {
                startWarmVideo(warm, url, startAt, chosen, owner)
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
                        isChosen = chosen,
                    )
                return
            }

        Log.d(TAG) { "playVideo open url=$url (was=${current.url} err=${current.errorReason})" }
        engineUrls[player] = url

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
                isChosen = chosen,
                owner = owner,
            )
        // Only now, so the new engine's first report lands on the new video's state.
        startVideoSync(player, url)

        // Cancel any in-flight open so a rapid switch can't interleave openUri on the shared engine.
        videoOpenJob?.cancel()
        videoOpenJob =
            scope.launch(Dispatchers.IO) {
                if (!openSource(player, url, _videoState) { videoDownloading = it }) return@launch
                launch { publishShape(player, url) }
                // openUri auto-plays per InitialPlayerState.PLAY default. Once it has the media: the
                // explicit start, else back to where this video was the last time it played.
                snapshotFlow { player.hasMedia }.first { it }
                if (startAt > 0f) player.seekTo(startAt * 1000f) else restorePosition(player, url, restartIfEnded = chosen)
            }
    }

    /**
     * Makes [player], opened paused ahead of time on [url], the playing engine of the card [owner]:
     * the one it replaces retires, and it plays (from [startAt]) as soon as its open has the media,
     * which it usually already has.
     */
    private fun startWarmVideo(
        player: VideoPlayerState,
        url: String,
        startAt: Float,
        chosen: Boolean,
        owner: Any?,
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
                isChosen = chosen,
                owner = owner,
            )
        startVideoSync(player, url)

        videoOpenJob?.cancel()
        videoOpenJob =
            scope.launch(Dispatchers.Main) {
                snapshotFlow { player.hasMedia || player.error != null }.first { it }
                if (player.error != null || _videoState.value.url != url) return@launch
                if (startAt > 0f) {
                    player.seekTo(startAt * 1000f)
                } else if (chosen && player.engineSnapshot().positionOr(0f) >= ENDED_POSITION) {
                    // Pressed play on a card whose engine played to its end: start over. Not when
                    // the feed brings it back on its own: it then stays on the last frame.
                    player.seekTo(0f)
                } else if (player.currentTime <= 0.0) {
                    // Where this video was, if its paused open had not restored it before it took
                    // over. Not for an engine already somewhere in it: a card's own, paused where
                    // it stopped, carries on from there, not from where another card showing the
                    // same video stopped.
                    restorePosition(player, url, restartIfEnded = chosen)
                }
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
            .filter { it != playing && !MediaHttp.isEncrypted(it) && cardEngines.values.none { card -> card.url == it } }
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
        if (isCardEngine(player)) {
            // Its card keeps it, paused where it is.
            pauseAside(player)
            return
        }
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
            pauseAside(player)
        } else {
            retireVideoPlayer(player)
        }
    }

    /**
     * Mutes and pauses [player], which gave the screen to another video but stays open. One still
     * loading ignores that pause and starts once loaded, unseen (a card's video then played to its
     * end off screen, and had nothing left to play when it got the middle back): pause it again
     * then, unless it became the playing video meanwhile.
     */
    private fun pauseAside(player: VideoPlayerState) {
        savePosition(player)
        runCatching {
            player.volume = 0f
            player.pause()
        }
        if (player.isLoading) {
            scope.launch(Dispatchers.Main) {
                withTimeoutOrNull(RETIRE_LOAD_TIMEOUT_MS) { snapshotFlow { player.isLoading }.first { !it } }
                // Retired meanwhile: its own release pauses it, and it may be disposed by now.
                if (player !== videoPlayer && player !in retiring) runCatching { player.pause() }
            }
        }
    }

    private fun openWarm(url: String) {
        openPaused(url) { engine -> warmEngines[url] = engine }
    }

    /** Opens [url] on a new engine, paused and muted, once [keep] (called under [initLock]) has stored it. */
    private fun openPaused(
        url: String,
        keep: (VideoPlayerState) -> Unit,
    ) {
        if (shutDown) return
        val streaming = MediaRelay.streamingUrl(url) ?: return
        val engine =
            runCatching { newVideoEngine() }
                .onFailure { Log.w(TAG, "Paused video engine init failed", it) }
                .getOrNull() ?: return
        runCatching {
            engine.volume = 0f
            engine.playbackSpeed = 1f
        }
        // Before the card can see it: one that sees it unheld counts its open from now.
        heldEngines.update { it + engine }
        synchronized(initLock) { keep(engine) }
        engineUrls[engine] = url
        Log.d(TAG) { "paused open url=$url" }
        scope.launch(Dispatchers.IO) {
            try {
                holdBehindPlayingVideo(engine)
            } finally {
                heldEngines.update { it - engine }
            }
            if (shutDown || engine in retired) return@launch
            runCatching { engine.openUri(streaming, InitialPlayerState.PAUSE) }
                .onSuccess {
                    // Paused on the frame where this video was the last time it played, so its card
                    // shows that frame and time, and plays on from there.
                    // Only while it is still set aside: once it is the playing engine, the play that made
                    // it so decides where it starts (startWarmVideo), and a restore landing up to
                    // SHAPE_WAIT_MS later would overrule a full-screen start or a replay.
                    if (withTimeoutOrNull(SHAPE_WAIT_MS) { snapshotFlow { engine.hasMedia }.first { it } } != null && engine !== videoPlayer) {
                        restorePosition(engine, url, restartIfEnded = false)
                    }
                    publishShape(engine, url)
                }.onFailure { Log.w(TAG) { "Paused open failed for $url: ${it.message}" } }
        }
    }

    /**
     * Waits, at most [BACKGROUND_OPEN_HOLD_MS], until the video the user watches is moving before
     * [engine], opened paused for a card or ahead of a scroll, starts its own download. A paused
     * engine reads the whole video: a Shorts screen opened four of them beside the playing one, and
     * when they came from one host they shared its single HTTP/2 connection, so the video on screen
     * got a fifth of it and took 8.7 s to move where it alone needed under 2 s. Held at the open,
     * not throttled mid-stream: a stream that stalls makes the engine slow to play it later. An
     * engine that becomes the playing one, or is retired, stops waiting at once, and none waits for
     * a video the user paused, or long when none is open (see [BackgroundOpenGate]).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun holdBehindPlayingVideo(engine: VideoPlayerState) {
        val gate = BackgroundOpenGate()
        withTimeoutOrNull(BACKGROUND_OPEN_HOLD_MS) {
            // Latest: a video that starts during the idle wait cancels it, and is waited on instead.
            _videoState
                .transformLatest { state ->
                    val verdict = if (engine === videoPlayer || engine in retired) Verdict.OPEN else gate.check(state, videoPauseRequested)
                    when (verdict) {
                        Verdict.OPEN -> {
                            emit(Unit)
                        }

                        Verdict.IDLE -> {
                            delay(IDLE_OPEN_DELAY_MS)
                            emit(Unit)
                        }

                        Verdict.HOLD -> {}
                    }
                }.first()
        }
    }

    /** Returns once [engine], a card's, has started opening its video: its paused open may be held back. */
    suspend fun awaitOpenStarted(engine: VideoPlayerState) {
        heldEngines.first { engine !in it }
    }

    /**
     * Gives the video card [owner], now on screen showing [url], its own engine: the one already
     * playing [url] when no card holds it, one opened ahead for it, or a new one opened paused.
     * Only feeds that play videos on their own call this; an encrypted video, which must download
     * whole first, opens when it plays instead.
     */
    fun attachCard(
        owner: Any,
        url: String,
    ) {
        if (shutDown || MediaHttp.isEncrypted(url)) return
        var stale: VideoPlayerState? = null
        val needsOpen =
            synchronized(initLock) {
                val existing = cardEngines[owner]
                if (existing?.url == url) return
                if (existing != null) {
                    cardEngines.remove(owner)
                    stale = existing.engine
                }
                if (cardEngines.size >= MAX_CARD_ENGINES) {
                    publishCards()
                    false
                } else {
                    // The playing engine goes to the card that started it, not to whichever card
                    // showing the same video attaches first.
                    val state = _videoState.value
                    val playing =
                        videoPlayer?.takeIf {
                            state.url == url && state.errorReason == null && (state.owner == null || state.owner === owner) && !isCardEngine(it)
                        }
                    val adopted = playing ?: warmEngines.remove(url)?.takeIf { it.error == null }
                    if (adopted != null) cardEngines[owner] = CardEngine(url, adopted)
                    if (playing != null) _videoState.update { it.copy(owner = owner) }
                    publishCards()
                    adopted == null
                }
            }
        stale?.let { if (it !== videoPlayer) retireVideoPlayer(it) }
        if (needsOpen) {
            openPaused(url) { engine ->
                cardEngines[owner] = CardEngine(url, engine)
                publishCards()
            }
        }
    }

    /**
     * Takes back the engine of the video card [owner], which left the screen. The playing one stays
     * for [releaseVideo] to decide: the now-playing bar keeps a video the user chose going.
     */
    fun detachCard(owner: Any) {
        val card =
            synchronized(initLock) {
                cardEngines.remove(owner)?.also { publishCards() }
            } ?: return
        if (card.engine !== videoPlayer) {
            retireVideoPlayer(card.engine)
        } else {
            // Still playing (in the now-playing bar), but no card plays it now.
            _videoState.update { if (it.owner === owner) it.copy(owner = null) else it }
        }
    }

    /**
     * Whether [state], the playing video, is the one the card [owner] showing [url] plays. A card
     * holding an engine of its own shows only what its engine plays; one without (a feed that does
     * not play videos on its own, or past [MAX_CARD_ENGINES]) also shows a video no card plays.
     */
    fun isPlayedBy(
        state: MediaPlaybackState,
        url: String,
        owner: Any,
        holdsEngine: Boolean,
    ): Boolean = state.url == url && (state.owner === owner || (state.owner == null && !holdsEngine))

    private fun isCardEngine(engine: VideoPlayerState?) = engine != null && synchronized(initLock) { cardEngines.values.any { it.engine === engine } }

    // Whether a card other than [owner] holds [engine]. Without an owner (full screen, the
    // now-playing bar) any card's engine will do.
    private fun isHeldByOtherCard(
        engine: VideoPlayerState,
        owner: Any?,
    ) = owner != null && synchronized(initLock) { cardEngines.any { (key, card) -> key !== owner && card.engine === engine } }

    // Callers hold [initLock].
    private fun publishCards() {
        _cardPlayers.value = cardEngines.mapValues { it.value.engine }
    }

    /**
     * Records [url]'s shape in [MediaAspectRatioCache] once [engine] has read it. An engine reads it
     * when it opens the video, paused or not, so a video opened ahead (see [warmVideos]) gives its
     * card the right shape before it scrolls in, instead of a 16:9 box that changes when it plays.
     */
    private suspend fun publishShape(
        engine: VideoPlayerState,
        url: String,
    ) {
        // The engine sets the size a moment before the ratio, starting from a 16:9 default: wait for
        // a ratio that has caught up, unless the video is 16:9 itself.
        val ratio =
            withTimeoutOrNull(SHAPE_WAIT_MS) {
                snapshotFlow {
                    val w = engine.metadata.width ?: 0
                    val h = engine.metadata.height ?: 0
                    val ratio = engine.aspectRatio
                    when {
                        w <= 0 || h <= 0 -> null
                        abs(ratio - ENGINE_DEFAULT_RATIO) > 0.01f -> ratio
                        abs(w.toFloat() / h - ENGINE_DEFAULT_RATIO) < 0.01f -> ratio
                        else -> null
                    }
                }.filterNotNull().first()
            }
        if (ratio != null) MediaAspectRatioCache.addMeasuredRatio(url, VideoPixelAspect.displayRatio(url, ratio))
    }

    /**
     * Plays [url], from [startMillis] in when given: a podcast soundbite or chapter tapped before
     * or during playback (0 jumps back to the start).
     */
    fun playAudio(
        url: String,
        startMillis: Long? = null,
    ) {
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
            if (startMillis != null) {
                if (audioOpenJob?.isActive == true) {
                    // Still opening: the open seeks there once the track has loaded.
                    pendingAudioStartMillis = startMillis
                } else {
                    scope.launch { seekAudioTo(player, startMillis) }
                }
            }
            if (!current.isPlaying) resumeAudio(player, url)
            return
        }

        audioError = null
        audioDownloading = MediaHttp.isEncrypted(url)
        audioStarting = true
        // Unless muted, a new track plays at the volume the bar shows.
        val volume = if (current.isMuted) 0 else current.volume
        player.setVolume(volume / 100f)
        _audioState.value =
            MediaPlaybackState(url = url, type = MediaType.AUDIO, isBuffering = true, volume = volume, isMuted = current.isMuted)

        // A seek waiting on the previous track's open must not land on this one.
        audioOpenJob?.cancel()
        pendingAudioStartMillis = startMillis
        audioOpenJob = scope.launch(Dispatchers.IO) { openAudio(player, url) }
    }

    /** Hands [url] to the engine, then applies the start position it was opened with, if any. */
    private suspend fun openAudio(
        player: AudioPlayer,
        url: String,
    ) {
        val source = resolveSource(url, _audioState) { audioDownloading = it }
        if (source == null) {
            audioStarting = false
            return
        }
        player.play(source.uri, loop = false)
        pendingAudioStartMillis?.let { start ->
            pendingAudioStartMillis = null
            seekAudioTo(player, start)
        }
    }

    /**
     * Plays the loaded track on. One that finished, or was stopped, has left the engine, which then
     * only replays it from the start.
     */
    private fun resumeAudio(
        player: AudioPlayer,
        url: String,
    ) {
        if (player.currentPlayerState() == AudioPlayerState.IDLE) {
            audioStarting = true
            _audioState.update { it.copy(isBuffering = true) }
            audioOpenJob?.cancel()
            audioOpenJob = scope.launch(Dispatchers.IO) { openAudio(player, url) }
        } else {
            player.play()
        }
    }

    /**
     * Seeks the loaded audio to [startMillis]. The engine only knows the length once the track has
     * started, so it is polled until then.
     */
    private suspend fun seekAudioTo(
        player: AudioPlayer,
        startMillis: Long,
    ) {
        val durationMillis =
            withTimeoutOrNull(AUDIO_OPEN_TIMEOUT_MS) {
                var length = player.currentDuration() ?: 0L
                while (length <= 0L) {
                    delay(CLOCK_POLL_MS)
                    length = player.currentDuration() ?: 0L
                }
                length
            } ?: return
        if (player !== audioPlayer) return
        seekAudio((startMillis.toDouble() / durationMillis).toFloat())
    }

    fun toggleVideoPlayPause() {
        val player = videoPlayer ?: return
        val state = _videoState.value
        if (isVideoGoing(state)) {
            pauseVideo()
        } else {
            videoPauseRequested = false
            // Play on a video that played to its end starts it over: the engine would sit on the
            // last frame.
            if (state.position >= ENDED_POSITION) player.seekTo(0f)
            player.play()
            _videoState.update { it.copy(isChosen = true) }
        }
    }

    // A video still loading counts as playing: it starts on its own once loaded.
    private fun isVideoGoing(state: MediaPlaybackState) = state.isPlaying || (state.isBuffering && !videoPauseRequested)

    /** Pauses the video, or keeps it from starting if it is still loading. */
    fun pauseVideo() {
        videoPauseRequested = true
        videoPlayer?.pause()
    }

    fun toggleAudioPlayPause() {
        val player = audioPlayer ?: return
        val state = _audioState.value
        val url = state.url ?: return
        if (state.isPlaying) player.pause() else resumeAudio(player, url)
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
        val player = audioPlayer ?: return
        val durationMillis = player.currentDuration() ?: _audioState.value.duration
        if (durationMillis <= 0L) return
        val targetMillis = (target * durationMillis).toLong()
        player.seekTo(targetMillis)
        _audioState.update { it.copy(position = target, currentTime = targetMillis) }
    }

    /** UI passes volume in 0..100. kdroidFilter wants 0..1. */
    fun setVideoVolume(volume: Int) {
        videoPlayer?.volume = volume.coerceIn(0, 100) / 100f
        // Turning the sound up is asking to watch it.
        _videoState.update { it.copy(volume = volume, isMuted = it.isMuted && volume == 0, isChosen = it.isChosen || volume > 0) }
        if (volume > 0) {
            preMuteVideoVolume = volume
            defaultMuted = false
        }
    }

    fun setAudioVolume(volume: Int) {
        audioPlayer?.setVolume(volume.coerceIn(0, 100) / 100f)
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
            audioPlayer?.setVolume(0f)
            _audioState.update { it.copy(isMuted = true, volume = 0) }
        }
    }

    fun stopVideo() {
        videoOpenJob?.cancel()
        videoSyncJob?.cancel()
        // Retired rather than stopped: an open still in flight would start it again, unseen. One a
        // card holds stays with it, paused.
        val stopped = synchronized(initLock) { videoPlayer.also { videoPlayer = null } }
        if (stopped != null && isCardEngine(stopped)) pauseAside(stopped) else stopped?.let(::retireVideoPlayer)
        _videoState.value = MediaPlaybackState()
        _isFullscreen.value = false
    }

    // How many cards show each video. Only touched from composition effects, on the UI thread.
    private val shownVideos = HashMap<String, Int>()

    /** A card showing [url] came on screen. */
    fun showVideo(url: String) {
        shownVideos[url] = (shownVideos[url] ?: 0) + 1
    }

    /** A card showing [url] left; the last one to leave lets it go when it asks to [release] it. */
    fun hideVideo(
        url: String,
        release: Boolean,
    ) {
        val left = (shownVideos[url] ?: 1) - 1
        if (left > 0) {
            shownVideos[url] = left
        } else {
            shownVideos.remove(url)
            if (release) releaseVideo(url)
        }
    }

    /**
     * Lets go of [url] once nothing shows it any more, as Android releases a player whose card left
     * the screen: stops it if it is the loaded video, keeping its position (as [playVideo] does for
     * a video it replaces) for when it comes back. One the user chose that is still playing keeps
     * going in the now-playing bar, and one on screen in full screen is left alone.
     */
    private fun releaseVideo(url: String) {
        val state = _videoState.value
        if (state.url != url || _isFullscreen.value) return
        if (state.isChosen && isVideoGoing(state)) return
        stopVideo()
    }

    /**
     * Saves where [player]'s video is in [VideoViewedPositionCache], as Android's players do when they
     * pause or stop: read from the engine, so exact. Not for a live stream, which has no position to
     * come back to, nor for one that never started.
     */
    private fun savePosition(player: VideoPlayerState) {
        val url = engineUrls[player] ?: return
        if (isHlsMedia(url, null)) return
        val millis = runCatching { (player.currentTime * 1000.0).toLong() }.getOrNull() ?: return
        if (millis > 1L) VideoViewedPositionCache.add(url, millis)
    }

    /**
     * Seeks [player], which just loaded [url], back to where that video was the last time it played,
     * when that is meaningfully far from where it is (see [VideoViewedPositionCache.shouldRestore]).
     * [restartIfEnded] is for a play the user pressed: a video saved at its end then starts over
     * instead of resuming on its last frame, where it would have nothing left to play.
     */
    private fun restorePosition(
        player: VideoPlayerState,
        url: String,
        restartIfEnded: Boolean,
    ) {
        if (isHlsMedia(url, null)) return
        val saved = VideoViewedPositionCache.get(url) ?: return
        val length = (player.duration * 1000.0).toLong()
        if (length <= 0L) return
        if (restartIfEnded && saved >= length * ENDED_POSITION) {
            if (player.engineSnapshot().positionOr(0f) >= ENDED_POSITION) player.seekTo(0f)
            return
        }
        val current = (player.currentTime * 1000.0).toLong()
        if (!VideoViewedPositionCache.shouldRestore(current, saved)) return
        player.seekTo(saved.coerceIn(0L, length).toFloat() / length * 1000f)
    }

    fun stopAudio() {
        audioOpenJob?.cancel()
        pendingAudioStartMillis = null
        audioStarting = false
        audioPlayer?.stop()
        _audioState.value = MediaPlaybackState(type = MediaType.AUDIO)
    }

    /**
     * The card the video that went full screen plays in: the full-screen view grows out of it and
     * shrinks back into it. Null when full screen started elsewhere (a plain fade).
     */
    @Volatile
    var fullscreenSource: ZoomSourceAnchor? = null
        private set

    /** The corner radius of [fullscreenSource]'s card. */
    @Volatile
    var fullscreenSourceCornerRadius: Dp = 0.dp
        private set

    fun toggleFullscreen() {
        if (!_isFullscreen.value) fullscreenSource = null
        _isFullscreen.value = !_isFullscreen.value
    }

    /** Full screen, growing out of the card [source] tracks (with [cornerRadius]) when given. */
    fun enterFullscreen(
        source: ZoomSourceAnchor?,
        cornerRadius: Dp = 0.dp,
    ) {
        fullscreenSource = source
        fullscreenSourceCornerRadius = cornerRadius
        _isFullscreen.value = true
    }

    fun exitFullscreen() {
        _isFullscreen.value = false
        // The overlay holds its own reference while it closes; don't keep the card's node alive.
        fullscreenSource = null
    }

    /** Call on app exit. Disposes native handles owned by kdroidFilter. */
    fun shutdown() {
        shutDown = true
        videoSyncJob?.cancel()
        audioSyncJob?.cancel()
        runCatching { videoPlayer?.dispose() }
        runCatching { audioPlayer?.release() }
        // Their delayed release dies with the scope below.
        synchronized(retiring) { retiring.toList().also { retiring.clear() } }.forEach { runCatching { it.dispose() } }
        synchronized(initLock) {
            // The playing one may be a card's, disposed above.
            (warmEngines.values + cardEngines.values.map { it.engine }).filter { it !== videoPlayer }.also {
                warmEngines.clear()
                warmRequests.clear()
                cardEngines.clear()
                publishCards()
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
     * Opens [url] on [player] (see [resolveSource]). Returns false, with the error on [state], when
     * it cannot be opened.
     */
    private suspend fun openSource(
        player: VideoPlayerState,
        url: String,
        state: MutableStateFlow<MediaPlaybackState>,
        downloading: (Boolean) -> Unit,
    ): Boolean {
        val source = resolveSource(url, state, downloading) ?: return false
        if (source.file != null) player.openFile(PlatformFile(source.file)) else player.openUri(source.uri)
        return true
    }

    /** What an engine opens for a URL: a local [file], or a [uri] it streams. */
    private class MediaSource(
        val uri: String,
        val file: File? = null,
    )

    /**
     * What an engine should open for [url]: the decrypted copy for an encrypted blob (see
     * [DecryptedMediaFiles]; the engine would otherwise stream ciphertext), the URL itself for
     * everything else. Null, with the error on [state], when the blob cannot be fetched, the URL
     * cannot go over Tor, or a newer request took the engine over meanwhile.
     */
    private suspend fun resolveSource(
        url: String,
        state: MutableStateFlow<MediaPlaybackState>,
        downloading: (Boolean) -> Unit,
    ): MediaSource? {
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
                    return null
                } finally {
                    // Unless a newer request already started its own download.
                    if (state.value.url == url) downloading(false)
                }
            } else {
                null
            }
        // A newer request took the engine over while this one was downloading.
        if (state.value.url != url) return null
        if (decrypted != null) return MediaSource(decrypted.absolutePath, decrypted)
        // Anything else streams from the engine's own HTTP stack, unless it goes over Tor: then
        // through the relay, so the app's Tor-routed client fetches it, as on Android.
        val streaming = MediaRelay.streamingUrl(url)
        if (streaming == null) {
            // It should go over Tor, which the relay can only do for http(s).
            state.update { if (it.url == url) it.copy(isBuffering = false, errorReason = "This video can't be played over Tor") else it }
            return null
        }
        return MediaSource(streaming)
    }

    /** Every video engine is made here, so GStreamer's segfault trap is off before any starts it. */
    private fun newVideoEngine(): VideoPlayerState {
        GStreamerSegtrap.disable()
        return createVideoPlayerState()
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
            runCatching { newVideoEngine() }
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
        savePosition(player)
        runCatching {
            player.volume = 0f
            player.pause()
        }
        retiring += player
        retired += player
        scope.launch(Dispatchers.Main) {
            delay(RETIRE_DELAY_MS)
            withTimeoutOrNull(RETIRE_LOAD_TIMEOUT_MS) { snapshotFlow { player.isLoading }.first { !it } }
            runCatching { player.pause() }
            if (retiring.remove(player)) runCatching { player.dispose() }
        }
    }

    private fun ensureAudioPlayer(): AudioPlayer? {
        if (shutDown) return null
        return audioPlayer ?: synchronized(initLock) {
            audioPlayer ?: runCatching { AudioPlayer() }
                .onSuccess { player ->
                    player.setOnErrorListener(
                        object : ErrorListener {
                            override fun onError(message: String?) {
                                Log.w(TAG) { "Audio playback failed: $message" }
                                audioStarting = false
                                audioError = message ?: "Can't play this audio"
                            }
                        },
                    )
                    startAudioSync(player)
                }.onFailure { Log.w(TAG, "Audio engine init failed", it) }
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

    /**
     * Mirrors the audio engine into [audioState]. It has no observable state, so it is read every
     * [CLOCK_POLL_MS] while a track is loaded.
     */
    private fun startAudioSync(player: AudioPlayer) {
        audioSyncJob?.cancel()
        audioSyncJob =
            scope.launch {
                while (true) {
                    delay(CLOCK_POLL_MS)
                    if (player !== audioPlayer) return@launch
                    if (_audioState.value.url == null) continue
                    val engine = player.currentPlayerState()
                    if (engine != null && engine != AudioPlayerState.IDLE) audioStarting = false
                    val duration = (player.currentDuration() ?: 0L).coerceAtLeast(0L)
                    val currentTime = (player.currentPosition() ?: 0L).coerceAtLeast(0L)
                    val error = audioError
                    _audioState.update { current ->
                        if (current.url == null) return@update current
                        current.copy(
                            isPlaying = engine == AudioPlayerState.PLAYING,
                            isBuffering = error == null && (engine == AudioPlayerState.BUFFERING || audioStarting || audioDownloading),
                            duration = if (duration > 0L) duration else current.duration,
                            currentTime = currentTime,
                            position = if (duration > 0L) (currentTime.toFloat() / duration).coerceIn(0f, 1f) else current.position,
                            errorReason = error ?: current.errorReason,
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
