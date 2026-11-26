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
package com.vitorpamplona.amethyst.ios.playback

import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlaybackState
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlayerFailure
import com.vitorpamplona.amethyst.commons.ui.playback.player.TextTrack
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoSize
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoTrack
import com.vitorpamplona.amethyst.commons.video.hls.RelayFailure
import com.vitorpamplona.amethyst.commons.video.hls.VideoRoute
import com.vitorpamplona.amethyst.ios.playback.cache.IosVideoCache
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFoundation.AVMediaCharacteristicLegible
import platform.AVFoundation.AVMediaSelectionGroup
import platform.AVFoundation.AVMediaSelectionOption
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeErrorKey
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerItemTrack
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.CMTimeRangeValue
import platform.AVFoundation.asset
import platform.AVFoundation.automaticallyWaitsToMinimizeStalling
import platform.AVFoundation.currentMediaSelection
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.loadedTimeRanges
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.mediaType
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.playImmediatelyAtRate
import platform.AVFoundation.preferredMaximumResolution
import platform.AVFoundation.preferredPeakBitRate
import platform.AVFoundation.presentationSize
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.seekableTimeRanges
import platform.AVFoundation.selectMediaOption
import platform.AVFoundation.selectedMediaOptionInMediaSelectionGroup
import platform.AVFoundation.timeControlStatus
import platform.AVFoundation.tracks
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.kCMTimeFlags_Indefinite
import platform.CoreMedia.kCMTimeFlags_Valid
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSError
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSUnderlyingErrorKey
import platform.Foundation.NSValue
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * [AmethystPlayer] over an [AVPlayer]. AVFoundation announces its changes through key-value
 * observing, which Kotlin/Native reaches only awkwardly, so this samples the player on the main
 * thread [TICK_MS] apart while anyone listens and fires the listener callbacks for what changed.
 * Ends, loops and failures come from notifications.
 *
 * The picture is drawn by an `AVPlayerLayer` on [avPlayer] ([AvPlayerSurface]). Renditions map to
 * AVPlayer's own preferences: pinning one sets `preferredPeakBitRate` and
 * `preferredMaximumResolution`, which is as close as AVPlayer lets an app get. Captions in the
 * stream (HLS subtitles) are the legible media-selection group; side-loaded ones are
 * [SideLoadedCaptions], fed by [setSideLoadedCaptions].
 *
 * A [cacheable] file reads through the disk cache ([IosVideoCache]); HLS and live streams do not,
 * and play from their URL, or through the loopback relay when they must go through Tor.
 */
@OptIn(ExperimentalForeignApi::class)
class AvAmethystPlayer(
    val url: String,
    private val cacheable: Boolean = false,
) : AmethystPlayer {
    val avPlayer: AVPlayer = AVPlayer()

    private val listeners = mutableListOf<AmethystPlayer.Listener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private val notificationTokens = mutableListOf<NSObjectProtocol>()

    private var item: AVPlayerItem? = null

    // Filled once AVFoundation has loaded these keys of the item's asset in the background. Read
    // before that, the asset properties block the calling thread until the playlist arrives, and
    // the caller here is the main thread: the UI froze while a video loaded.
    private var assetLoaded = false
    private var cachedLegibleGroup: AVMediaSelectionGroup? = null
    private var ended = false
    private var failure: PlayerFailure? = null

    // Counts prepare() calls, so a relay address that arrives late is dropped if another came since.
    private var prepareCount = 0

    // The loopback relay's stream this item plays through, when it plays through Tor that way.
    private var relayStream: String? = null
    private var wantsToPlay = false
    private var speed = 1f
    private var sideLoaded: SideLoadedCaptions? = null
    private var textSelection: TextTrack? = null

    // The last values the listeners heard, to fire only on change.
    private var lastPlaying = false
    private var lastState = PlaybackState.IDLE
    private var lastVolume = 1f
    private var lastSize = VideoSize.UNKNOWN
    private var lastTrackSignature = ""
    private var lastCues: List<String> = emptyList()
    private var lastPosition = 0L
    private var lastDuration = AmethystPlayer.TIME_UNSET

    init {
        avPlayer.automaticallyWaitsToMinimizeStalling = true
        prepare()
    }

    override val mediaId: String get() = url

    override var volume: Float
        get() = avPlayer.volume
        set(value) {
            avPlayer.volume = value.coerceIn(0f, 1f)
            avPlayer.muted = value <= 0f
            tick()
        }

    override val isPlaying: Boolean
        get() = avPlayer.timeControlStatus == AVPlayerTimeControlStatusPlaying

    override var playWhenReady: Boolean
        get() = wantsToPlay
        set(value) {
            if (value) play() else pause()
        }

    override val playbackState: PlaybackState
        get() {
            val current = item ?: return PlaybackState.IDLE
            return when {
                failure != null -> PlaybackState.IDLE
                ended -> PlaybackState.ENDED
                current.status == AVPlayerItemStatusReadyToPlay ->
                    if (avPlayer.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate) PlaybackState.BUFFERING else PlaybackState.READY
                current.status == AVPlayerItemStatusFailed -> PlaybackState.IDLE
                else -> PlaybackState.BUFFERING
            }
        }

    override val currentPosition: Long
        get() = avPlayer.currentTime().useContents { if ((flags.toInt() and kCMTimeFlags_Valid.toInt()) != 0) (CMTimeGetSeconds(avPlayer.currentTime()) * 1000).toLong().coerceAtLeast(0) else 0L }

    override val duration: Long
        get() {
            val current = item ?: return AmethystPlayer.TIME_UNSET
            val time = current.duration
            val known = time.useContents { (flags.toInt() and kCMTimeFlags_Valid.toInt()) != 0 && (flags.toInt() and kCMTimeFlags_Indefinite.toInt()) == 0 }
            return if (known) (CMTimeGetSeconds(time) * 1000).toLong() else AmethystPlayer.TIME_UNSET
        }

    override val bufferedPosition: Long
        get() {
            val ranges = item?.loadedTimeRanges ?: return 0L
            var end = 0.0
            ranges.forEach { value ->
                (value as? NSValue)?.CMTimeRangeValue?.useContents {
                    val rangeEnd = (start.value.toDouble() / start.timescale.toDouble().coerceAtLeast(1.0)) + (duration.value.toDouble() / duration.timescale.toDouble().coerceAtLeast(1.0))
                    if (rangeEnd > end) end = rangeEnd
                }
            }
            return (end * 1000).toLong()
        }

    override val videoSize: VideoSize
        get() {
            val current = item ?: return VideoSize.UNKNOWN
            return current.presentationSize.useContents { if (width > 0 && height > 0) VideoSize(width.toInt(), height.toInt()) else VideoSize.UNKNOWN }
        }

    override val error: PlayerFailure? get() = failure

    override var playbackSpeed: Float
        get() = speed
        set(value) {
            speed = value
            if (wantsToPlay) avPlayer.rate = value
            listeners.toList().forEach { it.onPlaybackSpeedChanged(value) }
        }

    override var repeatOne: Boolean = false

    /**
     * How many full plays [repeatOne] loops before pausing on the first frame, as Android's
     * `AutoReplayLimiter` does for feed videos: a video left looping keeps the decoder, network and
     * screen busy after the user stopped watching. Any resume grants a fresh allowance.
     */
    var maxAutoPlays: Int = 5
    private var playsCompleted = 0

    override fun play() {
        playsCompleted = 0
        if (!wantsToPlay) listeners.toList().forEach { it.onPlayWhenReadyChanged(true) }
        wantsToPlay = true
        if (ended) {
            ended = false
            avPlayer.seekToTime(kCMTimeZero.readValue())
        }
        avPlayer.playImmediatelyAtRate(speed)
        tick()
    }

    override fun pause() {
        if (wantsToPlay) listeners.toList().forEach { it.onPlayWhenReadyChanged(false) }
        wantsToPlay = false
        avPlayer.pause()
        tick()
    }

    override fun prepare() {
        val nsUrl = NSURL.URLWithString(url) ?: return fail(PlayerFailure("Not a URL: $url", "BAD_URL"))
        clearNotifications()
        failure = null
        ended = false
        relayStream = null
        val attempt = ++prepareCount
        when (IosVideoCache.routeFor(url, cacheable)) {
            VideoRoute.CACHE, VideoRoute.PLAIN -> {
                load(IosVideoCache.assetFor(nsUrl, cacheable))
            }

            // AVFoundation would fetch this one itself, outside Tor, although the settings route it
            // through Tor (HLS, a live stream, a server the cache refused): the loopback relay
            // fetches every playlist, segment and key of it over Tor instead. If the relay cannot
            // listen it is refused, never played direct (plans/2026-11-25-ios-tor.md, B12e).
            VideoRoute.TOR_RELAY -> {
                unload()
                scope.launch {
                    val relayed = IosVideoCache.torStreamFor(url)
                    // A newer prepare (a retry) or a release took over meanwhile.
                    if (attempt != prepareCount) return@launch
                    val relayedUrl = relayed?.let { NSURL.URLWithString(it.url) } ?: return@launch fail(PlayerFailure.torRefusal(url))
                    relayStream = relayed.stream
                    load(AVURLAsset(uRL = relayedUrl, options = null))
                }
            }

            VideoRoute.REFUSED -> {
                unload()
                fail(PlayerFailure.torRefusal(url))
            }
        }
    }

    private fun unload() {
        item = null
        avPlayer.replaceCurrentItemWithPlayerItem(null)
    }

    private fun load(asset: AVURLAsset) {
        val newItem = AVPlayerItem(asset = asset)
        item = newItem
        assetLoaded = false
        cachedLegibleGroup = null
        loadAssetKeys(asset, newItem)
        avPlayer.replaceCurrentItemWithPlayerItem(newItem)
        observe(newItem)
        if (wantsToPlay) avPlayer.playImmediatelyAtRate(speed)
        tick()
    }

    override fun seekTo(positionMs: Long) {
        ended = false
        avPlayer.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 600))
        listeners.toList().forEach { it.onPositionDiscontinuity() }
        tick()
    }

    override fun seekToDefaultPosition() {
        val live = duration == AmethystPlayer.TIME_UNSET
        if (live) {
            // The live edge: the end of what is seekable.
            val ranges = item?.seekableTimeRanges
            val last = (ranges?.lastOrNull() as? NSValue)?.CMTimeRangeValue
            if (last != null) {
                val edge = last.useContents { (start.value.toDouble() / start.timescale.toDouble().coerceAtLeast(1.0)) + (duration.value.toDouble() / duration.timescale.toDouble().coerceAtLeast(1.0)) }
                avPlayer.seekToTime(CMTimeMakeWithSeconds(edge, 600))
            }
        } else {
            seekTo(0)
        }
    }

    // Tracks

    private fun trackTypes(): List<String> {
        val current = item ?: return emptyList()
        if (current.status != AVPlayerItemStatusReadyToPlay) return emptyList()
        return current.tracks.mapNotNull { (it as? AVPlayerItemTrack)?.assetTrack?.mediaType }
    }

    override val hasVideoTrack: Boolean get() = AVMediaTypeVideo in trackTypes()

    override val hasAudioTrack: Boolean get() = AVMediaTypeAudio in trackTypes()

    override val isAudioOnly: Boolean
        get() {
            val current = item ?: return false
            if (current.status != AVPlayerItemStatusReadyToPlay) return false
            val types = current.tracks.mapNotNull { (it as? AVPlayerItemTrack)?.assetTrack?.mediaType }
            return types.contains(AVMediaTypeAudio) && !types.contains(AVMediaTypeVideo) && videoSize == VideoSize.UNKNOWN
        }

    private var pinnedVideo: VideoTrack? = null

    override val videoTracks: List<VideoTrack>
        get() {
            val variants = if (assetLoaded) HlsVariants.of(item?.asset as? AVURLAsset, pinnedVideo) else emptyList()
            if (variants.isNotEmpty()) return variants
            // A plain file: its one picture, as media3 reports the single track of its video group.
            val size = videoSize
            if (size == VideoSize.UNKNOWN) return emptyList()
            return listOf(VideoTrack(id = "main", width = size.width, height = size.height, bitrate = 0, selected = true))
        }

    override val hasVideoTrackOverride: Boolean get() = pinnedVideo != null

    override fun selectVideoTrack(track: VideoTrack) {
        pinnedVideo = track
        item?.preferredPeakBitRate = track.bitrate.toDouble()
        item?.preferredMaximumResolution = platform.CoreGraphics.CGSizeMake(track.width.toDouble(), track.height.toDouble())
        listeners.toList().forEach { it.onTracksChanged() }
    }

    override fun clearVideoTrackOverride() {
        pinnedVideo = null
        item?.preferredPeakBitRate = 0.0
        applyViewport()
        listeners.toList().forEach { it.onTracksChanged() }
    }

    private var viewport = 0 to 0

    override fun constrainToViewport(
        width: Int,
        height: Int,
    ) {
        viewport = width to height
        if (pinnedVideo == null) applyViewport()
    }

    private fun applyViewport() {
        val (w, h) = viewport
        item?.preferredMaximumResolution = platform.CoreGraphics.CGSizeMake(w.toDouble(), h.toDouble())
    }

    /** The stream's caption group, once loaded; null before, never a blocking read. */
    private fun legibleGroup(): AVMediaSelectionGroup? = cachedLegibleGroup

    /**
     * Loads what [textTracks] and [videoTracks] read from the asset off the main thread, then
     * caches it and announces the tracks. A load for an item that was replaced meanwhile is dropped.
     */
    private fun loadAssetKeys(
        asset: AVURLAsset,
        forItem: AVPlayerItem,
    ) {
        asset.loadValuesAsynchronouslyForKeys(ASSET_KEYS) {
            dispatch_async(dispatch_get_main_queue()) {
                if (item !== forItem) return@dispatch_async
                cachedLegibleGroup = asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
                assetLoaded = true
                listeners.toList().forEach { it.onTracksChanged() }
            }
        }
    }

    override val textTracks: List<TextTrack>
        get() {
            val fromStream =
                legibleGroup()?.let { group ->
                    val selected = item?.currentMediaSelection?.selectedMediaOptionInMediaSelectionGroup(group)
                    group.options.mapIndexedNotNull { index, option ->
                        (option as? AVMediaSelectionOption)?.let {
                            TextTrack(id = "stream:$index", language = it.extendedLanguageTag, label = it.displayName, selected = it == selected)
                        }
                    }
                } ?: emptyList()
            val sideLoaded = sideLoaded?.tracks(textSelection?.id) ?: emptyList()
            return fromStream + sideLoaded
        }

    override fun selectTextTrack(track: TextTrack?) {
        textSelection = track
        val group = legibleGroup()
        if (group != null) {
            val option =
                track
                    ?.id
                    ?.removePrefix("stream:")
                    ?.toIntOrNull()
                    ?.takeIf { track.id.startsWith("stream:") }
                    ?.let { group.options.getOrNull(it) as? AVMediaSelectionOption }
            item?.selectMediaOption(option, inMediaSelectionGroup = group)
        }
        sideLoaded?.select(track?.id)
        listeners.toList().forEach { it.onTracksChanged() }
    }

    private var captionsOn = true

    override var captionsEnabled: Boolean
        get() = captionsOn
        set(value) {
            if (captionsOn == value) return
            captionsOn = value
            if (!value) {
                legibleGroup()?.let { item?.selectMediaOption(null, inMediaSelectionGroup = it) }
            } else {
                textSelection?.let { selectTextTrack(it) }
            }
            tick()
            listeners.toList().forEach { it.onTracksChanged() }
        }

    override val cues: List<String> get() = lastCues

    /** Captions published beside the video (NIP-71 `text-track`), drawn by the shared UI from [AmethystPlayer.Listener.onCues]. */
    fun setSideLoadedCaptions(captions: SideLoadedCaptions?) {
        sideLoaded = captions
        listeners.toList().forEach { it.onTracksChanged() }
    }

    override fun addListener(listener: AmethystPlayer.Listener) {
        listeners += listener
        if (ticker == null) {
            ticker =
                scope.launch {
                    while (isActive) {
                        tick()
                        delay(TICK_MS)
                    }
                }
        }
    }

    override fun removeListener(listener: AmethystPlayer.Listener) {
        listeners -= listener
        if (listeners.isEmpty()) {
            ticker?.cancel()
            ticker = null
        }
    }

    /** Stops for good: the pool calls it when it drops this player. */
    fun release() {
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        clearNotifications()
        listeners.clear()
        scope.cancel()
    }

    private fun observe(observed: AVPlayerItem) {
        val center = NSNotificationCenter.defaultCenter
        notificationTokens +=
            center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, observed, NSOperationQueue.mainQueue) { _: NSNotification? ->
                if (repeatOne) {
                    playsCompleted++
                    avPlayer.seekToTime(kCMTimeZero.readValue())
                    if (playsCompleted >= maxAutoPlays) {
                        // Left on the first frame with the play button showing.
                        wantsToPlay = false
                        avPlayer.pause()
                        listeners.toList().forEach { it.onPlayWhenReadyChanged(false) }
                    } else {
                        avPlayer.playImmediatelyAtRate(speed)
                    }
                    listeners.toList().forEach { it.onPositionDiscontinuity() }
                } else {
                    ended = true
                    wantsToPlay = false
                }
                tick()
            }
        notificationTokens +=
            center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, observed, NSOperationQueue.mainQueue) { note: NSNotification? ->
                val nsError = note?.userInfo?.get(AVPlayerItemFailedToPlayToEndTimeErrorKey) as? NSError
                fail(failureOfItem(nsError))
            }
    }

    private fun clearNotifications() {
        notificationTokens.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        notificationTokens.clear()
    }

    private fun fail(newFailure: PlayerFailure) {
        Log.w(TAG) { "Playback failed for $url: ${newFailure.code} ${newFailure.message}" }
        failure = newFailure
        listeners.toList().forEach { it.onPlayerErrorChanged(newFailure) }
        tick()
    }

    /** [nsError] explained, by the relay's own account when the item plays through the relay. */
    private fun failureOfItem(nsError: NSError?): PlayerFailure {
        val relayed = relayStream?.let { IosVideoCache.relayFailure(it) }
        return if (relayed != null) failureOf(relayed, nsError) else failureOf(nsError)
    }

    /** Compares the player with what the listeners last heard and tells them what changed. */
    private fun tick() {
        val current = item
        if (current != null && failure == null && current.status == AVPlayerItemStatusFailed) {
            fail(failureOfItem(current.error))
            return
        }
        val snapshot = listeners.toList()
        if (snapshot.isEmpty()) return

        val playing = isPlaying
        if (playing != lastPlaying) {
            lastPlaying = playing
            snapshot.forEach { it.onIsPlayingChanged(playing) }
        }
        val state = playbackState
        if (state != lastState) {
            lastState = state
            snapshot.forEach { it.onPlaybackStateChanged(state) }
        }
        val currentVolume = if (avPlayer.muted) 0f else avPlayer.volume
        if (currentVolume != lastVolume) {
            lastVolume = currentVolume
            snapshot.forEach { it.onVolumeChanged(currentVolume) }
        }
        val size = videoSize
        if (size != lastSize) {
            lastSize = size
            snapshot.forEach { it.onVideoSizeChanged(size) }
        }
        if (state == PlaybackState.READY || state == PlaybackState.BUFFERING) {
            val signature = "$isAudioOnly|${legibleGroup()?.options?.size}|${pinnedVideo?.id}"
            if (signature != lastTrackSignature) {
                lastTrackSignature = signature
                snapshot.forEach { it.onTracksChanged() }
            }
        }
        val knownDuration = duration
        if (knownDuration != lastDuration) {
            lastDuration = knownDuration
            snapshot.forEach { it.onTimelineChanged() }
        }
        val position = currentPosition
        sideLoaded?.let { captions ->
            val cues = if (captionsOn) captions.cuesAt(position) else emptyList()
            if (cues != lastCues) {
                lastCues = cues
                snapshot.forEach { it.onCues(cues) }
            }
        }
        // A jump the player made by itself (a loop restart) moves the position backwards.
        if (position + TICK_MS * 4 < lastPosition) snapshot.forEach { it.onPositionDiscontinuity() }
        lastPosition = position
    }

    companion object {
        private const val TAG = "AvAmethystPlayer"
        const val TICK_MS = 200L

        // The asset properties this reads: caption groups and HLS renditions.
        private val ASSET_KEYS = listOf("availableMediaCharacteristicsWithMediaSelectionOptions", "variants")

        // AVFoundation's errors for media it cannot read or decode.
        private val DECODER_CODES = setOf(-11828L, -11829L, -11833L, -11821L, -11832L, -11834L, -11849L)

        /** A relayed stream's failure: what the relay saw upstream, which AVFoundation reports as a bare 502. */
        fun failureOf(
            relayed: RelayFailure,
            nsError: NSError?,
        ): PlayerFailure {
            val diagnostic = nsError?.let { "${it.domain}(${it.code}) ${it.localizedDescription}" }
            return when (relayed) {
                is RelayFailure.Unroutable -> PlayerFailure.torRefusal(relayed.url).copy(diagnostic = diagnostic)
                is RelayFailure.TorUnavailable -> PlayerFailure("Tor is not connected yet: ${relayed.url}", "TOR_UNAVAILABLE", isNetworkProblem = true, diagnostic = diagnostic)
                is RelayFailure.Http -> PlayerFailure("HTTP ${relayed.status} through Tor: ${relayed.url}", "TOR_RELAY_HTTP(${relayed.status})", isNetworkProblem = true, diagnostic = diagnostic)
                is RelayFailure.Network -> PlayerFailure("${relayed.message} through Tor: ${relayed.url}", "TOR_RELAY_NETWORK", isNetworkProblem = true, diagnostic = diagnostic)
            }
        }

        fun failureOf(nsError: NSError?): PlayerFailure {
            if (nsError == null) return PlayerFailure("Unknown playback error", "UNKNOWN")
            // The cache's refusal of a Tor-routed URL it would have had to hand to AVFoundation,
            // as is or wrapped by AVFoundation in its own error.
            val underlying = nsError.userInfo[NSUnderlyingErrorKey] as? NSError
            if (nsError.domain == IosVideoCache.TOR_REFUSAL_DOMAIN || underlying?.domain == IosVideoCache.TOR_REFUSAL_DOMAIN) {
                return PlayerFailure.torRefusal(nsError.localizedDescription)
            }
            val code = nsError.code
            val domain = nsError.domain ?: "?"
            return PlayerFailure(
                message = nsError.localizedDescription,
                code = "$domain($code)",
                isDecoderProblem = domain == "AVFoundationErrorDomain" && code in DECODER_CODES,
                isNetworkProblem = domain == NSURLErrorDomain,
            )
        }
    }
}
