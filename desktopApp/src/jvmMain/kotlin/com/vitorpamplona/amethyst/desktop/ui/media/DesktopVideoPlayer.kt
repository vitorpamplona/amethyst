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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_download_for_offline
import com.vitorpamplona.amethyst.commons.resources.error_video_playback_failed
import com.vitorpamplona.amethyst.commons.resources.video_error_open_in_default_player
import com.vitorpamplona.amethyst.commons.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.commons.ui.components.LocalShortVideo
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.zoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.media.MediaPlaybackState
import com.vitorpamplona.amethyst.desktop.service.media.VideoPixelAspect
import com.vitorpamplona.amethyst.desktop.service.media.VideoThumbnailCache
import com.vitorpamplona.quartz.utils.Log
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.net.URI

// Shorter than this, a video is a still image (one frame); see the poster fade below.
private const val STILL_VIDEO_MILLIS = 300L

// How long a card stays on screen before it opens its own engine.
private const val CARD_ENGINE_DELAY_MS = 300L

/**
 * An inline video. Every instance draws the one [GlobalMediaPlayer] engine when its [url] is the
 * loaded one, and its thumbnail otherwise.
 *
 * A feed video follows Android's rules when its caller opts in. With [autoPlayWhenVisible] the
 * video nearest the middle of the window starts on its own (muted, unless the user unmuted the
 * last one). With [pauseWhenHidden] it pauses once it scrolls out of view, and when its card
 * leaves the screen (another screen opens) the engine lets it go, remembering where it was, unless
 * the user pressed play or unmuted it: that one keeps playing in the now-playing bar. With
 * [loadOnDemand] (the "start video playback" setting off) nothing downloads until the user clicks
 * the download button.
 */
@Composable
fun DesktopVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    initialSeekPosition: Float = 0f,
    onFullscreen: ((Float) -> Unit)? = null,
    viewMode: ViewMode = ViewMode.DEFAULT,
    onViewModeChange: ((ViewMode) -> Unit)? = null,
    trailingControls: @Composable (() -> Unit)? = null,
    isLive: Boolean = false,
    autoPlayWhenVisible: Boolean = false,
    pauseWhenHidden: Boolean = false,
    loadOnDemand: Boolean = false,
    shape: Shape = MaterialTheme.shapes.small,
    // Where the picture itself sits in the window, for a full-screen view that grows out of it.
    pictureSource: ZoomSourceAnchor? = null,
    // The shape the note declares for this video (its imeta `dim`), when it has one.
    declaredRatio: Float? = null,
    // The note's own poster for this video (its imeta `image`), shown until the video plays.
    posterUrl: String? = null,
    // The note's blurhash/thumbhash: drawn at once, under the poster or frame, while those load.
    blurhash: String? = null,
    thumbhash: String? = null,
) {
    // This card, as the one composable allowed to draw the engine (see videoSurfaceOwner).
    val surfaceOwner = remember { Any() }

    // Whether this card holds an engine of its own (see attachCard).
    val holdsOwnEngine = remember(surfaceOwner) { GlobalMediaPlayer.cardPlayers.map { surfaceOwner in it }.distinctUntilChanged() }
    // Only the state of the video this card plays: every other card on screen would otherwise
    // recompose on each tick of whichever video is playing, and a card showing the same video as
    // another would take that one's state, and its play button that one's engine.
    val ownState =
        remember(url, surfaceOwner) {
            combine(GlobalMediaPlayer.videoState, holdsOwnEngine) { state, holds ->
                state.takeIf { GlobalMediaPlayer.isPlayedBy(it, url, surfaceOwner, holds) }
            }.distinctUntilChanged()
        }

    // The same check, for the effects below, which read the state once rather than observe it.
    fun isPlayedHere(state: MediaPlaybackState) = GlobalMediaPlayer.isPlayedBy(state, url, surfaceOwner, surfaceOwner in GlobalMediaPlayer.cardPlayers.value)
    val videoState by ownState.collectAsState(GlobalMediaPlayer.videoState.value.takeIf(::isPlayedHere))
    val isShortVideo = LocalShortVideo.current
    val isActiveVideo = videoState != null
    val scope = rememberCoroutineScope()
    val currentSurfaceOwner by GlobalMediaPlayer.videoSurfaceOwner.collectAsState()
    val isFullscreen by GlobalMediaPlayer.isFullscreen.collectAsState()
    LaunchedEffect(isActiveVideo, currentSurfaceOwner) {
        // The video is this one and nobody draws it (its owner left): take it over.
        if (isActiveVideo && currentSurfaceOwner == null) GlobalMediaPlayer.claimVideoSurface(surfaceOwner)
    }
    DisposableEffect(surfaceOwner) {
        onDispose {
            GlobalMediaPlayer.releaseVideoSurface(surfaceOwner)
            GlobalMediaPlayer.detachCard(surfaceOwner)
        }
    }

    var loaded by remember(url) { mutableStateOf(!loadOnDemand || autoPlay) }

    // A feed that plays videos on its own gives each card on screen its own engine, as Android
    // gives each video composable its player: opened paused a moment after the card shows (so a
    // fling past it opens nothing), released once it leaves. The card in the middle plays its own;
    // the rest keep theirs paused where they stopped.
    var onScreen by remember { mutableStateOf(false) }
    val holdsEngine = autoPlayWhenVisible && loaded
    LaunchedEffect(holdsEngine, onScreen, url) {
        if (holdsEngine && onScreen) {
            delay(CARD_ENGINE_DELAY_MS)
            GlobalMediaPlayer.attachCard(surfaceOwner, url)
        } else {
            GlobalMediaPlayer.detachCard(surfaceOwner)
        }
    }
    val onScreenModifier =
        if (autoPlayWhenVisible) {
            Modifier.onGloballyPositioned { coordinates ->
                // Clipped by every ancestor: a card scrolled out of its list measures empty.
                val shown = coordinates.boundsInWindow()
                onScreen = coordinates.isAttached && shown.width > 0f && shown.height > 0f
            }
        } else {
            Modifier
        }
    val cardPlayer by remember(surfaceOwner) {
        GlobalMediaPlayer.cardPlayers.map { it[surfaceOwner] }.distinctUntilChanged()
    }.collectAsState(null)
    var thumbnail by remember(url) { mutableStateOf(VideoThumbnailCache.getCached(url)) }
    var aspectRatio by remember(url) { mutableFloatStateOf(16f / 9f) }

    // Whether the note's poster failed to load (a dead link): then a frame is extracted instead.
    var posterFailed by remember(url, posterUrl) { mutableStateOf(false) }
    val poster = posterUrl?.takeUnless { posterFailed }

    LaunchedEffect(url, loaded, poster, holdsEngine) {
        // A note's own poster is drawn straight from the image loader (see VideoPoster); only a
        // video without one has a frame extracted, which downloads part of the video.
        if (!loaded || thumbnail != null || poster != null) return@LaunchedEffect
        // Where a paused engine draws nothing (Linux), the card would stay black until it played:
        // it extracts a poster as a card without an engine does.
        if (holdsEngine && GlobalMediaPlayer.pausedEngineShowsFrame) {
            // This card's own engine opens the video anyway, and its paused open shows the first
            // frame: extracting one too downloaded the same video's first 4 MB a second time. Only
            // a poster already kept is worth showing until then.
            VideoThumbnailCache.getStored(url)?.let {
                thumbnail = it
                return@LaunchedEffect
            }
            if (engineShowsFrame({ onScreen }, { cardPlayer })) {
                // The engine goes once the card leaves the screen, and its frame with it: make the
                // poster from what it streamed, and show that from then on.
                VideoThumbnailCache.keepFromStream(url)
                snapshotFlow { cardPlayer }.first { it == null }
                VideoThumbnailCache.getCached(url)?.let {
                    thumbnail = it
                    return@LaunchedEffect
                }
            }
        }
        VideoThumbnailCache.getThumbnail(url)?.let { thumbnail = it }
    }

    LaunchedEffect(url, autoPlay) {
        if (autoPlay) {
            // Only views the user opened to watch it (the lightbox, a full-screen view) pass autoPlay.
            GlobalMediaPlayer.playVideo(url, initialSeekPosition, owner = surfaceOwner, chosen = true)
        }
    }

    val visibilityModifier =
        if (autoPlayWhenVisible || pauseWhenHidden) {
            val (positionModifier, isWinner) = rememberVideoVisibilityWinner(url)
            // Only a video that was on screen pauses on leaving it, so one that first composes off
            // screen leaves alone whatever the engine is playing.
            val hasBeenVisible = remember(url) { booleanArrayOf(false) }
            LaunchedEffect(isWinner.value, loaded) {
                // Going full screen resizes the window and reshuffles who is in view; the video on
                // screen there is the one that matters.
                if (GlobalMediaPlayer.isFullscreen.value) return@LaunchedEffect
                val state = GlobalMediaPlayer.videoState.value
                val mine = isPlayedHere(state)
                if (isWinner.value) {
                    hasBeenVisible[0] = true
                    // The card in view draws the video, also when another card showing it started it.
                    if (mine) GlobalMediaPlayer.claimVideoSurface(surfaceOwner)
                    val ended = mine && state.position >= 0.99f
                    if (autoPlayWhenVisible && loaded && !ended && (!mine || !state.isPlaying)) {
                        GlobalMediaPlayer.playVideo(url, owner = surfaceOwner)
                    }
                } else if (hasBeenVisible[0] && pauseWhenHidden && mine) {
                    // A screen sliding or scaling away takes its cards out of view too. Waiting
                    // out that transition lets the card leave first, and releaseVideo decide.
                    delay(SCREEN_TRANSITION_MS)
                    // Meanwhile another card showing the same video may have come into view.
                    val owner = GlobalMediaPlayer.videoSurfaceOwner.value
                    val handedOver = owner != null && owner !== surfaceOwner
                    if (handedOver || GlobalMediaPlayer.isFullscreen.value || !isPlayedHere(GlobalMediaPlayer.videoState.value)) {
                        return@LaunchedEffect
                    }
                    // Also while it is still loading, or it would start once loaded, off screen.
                    GlobalMediaPlayer.pauseVideo()
                }
            }
            positionModifier
        } else {
            Modifier
        }

    // Counts every card showing this video, so only the last one to leave lets it go: the same
    // video can be on screen twice (a note and its repost, a feed card and the thread it opens).
    DisposableEffect(url) {
        GlobalMediaPlayer.showVideo(url)
        onDispose { GlobalMediaPlayer.hideVideo(url, release = pauseWhenHidden) }
    }

    // Surface playback errors to the log — kdroidFilter reports these only via state, so without
    // this a stream that fails to start ("live doesn't start") leaves no trace.
    val errorReason = videoState?.errorReason
    LaunchedEffect(errorReason) {
        if (errorReason != null) Log.w("DesktopVideoPlayer") { "Can't play $url: $errorReason" }
    }

    // Read in composition, so an anamorphic video's pixel ratio corrects the shape when it lands.
    videoState?.aspectRatio?.let { if (it != 16f / 9f) aspectRatio = VideoPixelAspect.displayRatio(url, it) }
    // The shape before this video plays, best source first: what the playing engine reports, what
    // an engine that opened it ahead or earlier measured (read in composition, so this card resizes
    // when that lands), what the frame extracted for its thumbnail shows, what the note declares
    // (imeta `dim`, which can be the stored size of an anamorphic video), any other estimate. 16:9
    // only when nothing knows: without these a portrait Short sat in a landscape box until it played.
    val targetRatio =
        if (aspectRatio != 16f / 9f) {
            aspectRatio
        } else {
            MediaAspectRatioCache.measured(url)
                ?: thumbnail?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height }
                ?: declaredRatio
                ?: MediaAspectRatioCache.get(url)
                ?: aspectRatio
        }
    // A shape learned late (the engine's own report) eases in instead of snapping the card.
    val ratio by animateFloatAsState(targetValue = targetRatio, animationSpec = tween(durationMillis = 200), label = "videoShape")

    // In full screen the overlay draws the engine; a second surface would resize it too.
    val drawsEngine = isActiveVideo && currentSurfaceOwner === surfaceOwner && !isFullscreen

    // Whether this video has shown a picture yet: its clock moves only once frames are coming. Kept
    // per URL, so a video scrolled back to (whose engine still holds its frame) shows it at once.
    var pictureShown by remember(url) { mutableStateOf(false) }
    if (!pictureShown && isActiveVideo && (videoState?.currentTime ?: 0L) > 0L) pictureShown = true
    // The card's own engine, playing or paused where it stopped, except in full screen, where the
    // overlay draws the playing one; without one, the shared engine when this card draws it.
    val ownPlayer = cardPlayer
    val activePlayer =
        when {
            ownPlayer != null -> ownPlayer.takeUnless { isFullscreen && it === GlobalMediaPlayer.activeVideoPlayerState }
            drawsEngine -> GlobalMediaPlayer.activeVideoPlayerState
            else -> null
        }

    // Black behind a picture, as any player letterboxes; the theme's placeholder until there is one,
    // and behind the error message, which is drawn in the theme's colors.
    val backdrop =
        if (loaded && errorReason == null && (activePlayer != null || thumbnail != null || poster != null)) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    BoxWithConstraints(modifier = modifier.then(visibilityModifier).then(onScreenModifier), contentAlignment = Alignment.TopCenter) {
        val desiredHeight = maxWidth / ratio
        val constrainedHeight = if (constraints.hasBoundedHeight) minOf(desiredHeight, maxHeight) else desiredHeight
        // Only as wide as the picture at that height, as an image is, so the controls and their
        // gradients sit on the video instead of across empty pillars in the theme's color. Never so
        // narrow that the controls stop fitting.
        val width = (constrainedHeight * ratio).coerceIn(minOf(MinControlsWidth, maxWidth), maxWidth)

        Box(
            modifier =
                Modifier
                    .width(width)
                    .height(constrainedHeight)
                    .then(if (pictureSource != null) Modifier.zoomSourceAnchor(pictureSource) else Modifier)
                    .clip(shape)
                    .background(backdrop),
            contentAlignment = Alignment.Center,
        ) {
            if (!loaded) {
                IconButton(
                    onClick = {
                        loaded = true
                        // A click on the download button is a request to watch it.
                        GlobalMediaPlayer.playVideo(url, owner = surfaceOwner, chosen = true)
                    },
                    modifier = Modifier.size(75.dp),
                ) {
                    Icon(
                        MaterialSymbols.DownloadForOffline,
                        contentDescription = stringRes(Res.string.accessibility_download_for_offline),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(75.dp),
                    )
                }
                return@Box
            }

            if (errorReason == null && (blurhash != null || thumbhash != null)) {
                DisplayBlurHash(blurhash, null, ContentScale.Crop, Modifier.fillMaxSize(), thumbhash = thumbhash)
            }

            if (errorReason != null) {
                PlaybackErrorMessage(url = url, reason = errorReason)
            } else if (activePlayer != null) {
                VideoSurface(playerState = activePlayer, url = url, modifier = Modifier.fillMaxSize())
                // The surface is black until the engine has decoded a frame: the thumbnail stays over
                // it until the video is moving, then fades, instead of the card blinking to black as
                // it starts.
                val thumbnailAlpha by animateFloatAsState(
                    // A "video" a frame or two long is a still: the engine shows nothing for it, so
                    // its poster stays instead of fading into black.
                    targetValue = if (pictureShown && (videoState?.duration ?: 0L) !in 1L until STILL_VIDEO_MILLIS) 0f else 1f,
                    animationSpec = tween(durationMillis = 150),
                    label = "thumbnailOverVideo",
                )
                if (thumbnailAlpha > 0f) {
                    VideoPoster(poster, thumbnail, Modifier.fillMaxSize().graphicsLayer { alpha = thumbnailAlpha }) { posterFailed = true }
                }
            } else {
                VideoPoster(poster, thumbnail, Modifier.fillMaxSize()) { posterFailed = true }
            }

            if (errorReason == null) {
                val state = videoState
                // A card whose engine another card's video paused shows where it stopped.
                val paused = if (state == null) ownPlayer?.takeIf { pictureShown } else null
                val pausedMillis = paused?.let { (it.currentTime * 1000.0).toLong().coerceAtLeast(0L) } ?: 0L
                val pausedLength = paused?.let { (it.duration * 1000.0).toLong().coerceAtLeast(0L) } ?: 0L
                VideoControls(
                    // A Short (a short-form event, or any vertical video) that plays on its own
                    // arrives without controls.
                    // The target, not the animated ratio: one easing in from 16:9 would briefly count
                    // as landscape and flash its controls.
                    hideWhenIdle = autoPlayWhenVisible && loaded && (isShortVideo || targetRatio < 1f),
                    isPlaying = state?.isPlaying ?: false,
                    isBuffering = state?.isBuffering ?: false,
                    position = state?.position ?: if (pausedLength > 0L) (pausedMillis.toFloat() / pausedLength).coerceIn(0f, 1f) else 0f,
                    duration = state?.duration ?: pausedLength,
                    currentTime = state?.currentTime ?: pausedMillis,
                    volume = state?.volume ?: 100,
                    // Before it loads, show the state it would start in.
                    isMuted = state?.isMuted ?: GlobalMediaPlayer.defaultMuted,
                    viewMode = viewMode,
                    onPlayPause = {
                        if (isActiveVideo) {
                            GlobalMediaPlayer.claimVideoSurface(surfaceOwner)
                            GlobalMediaPlayer.toggleVideoPlayPause()
                        } else {
                            GlobalMediaPlayer.playVideo(url, initialSeekPosition, owner = surfaceOwner, chosen = true)
                        }
                    },
                    onSeek = { pos ->
                        if (isActiveVideo) {
                            GlobalMediaPlayer.seekVideo(pos)
                        }
                    },
                    onSkip = { delta -> if (isActiveVideo) GlobalMediaPlayer.skipVideo(delta) },
                    speed = state?.speed ?: 1f,
                    onSpeedChange = { speed -> if (isActiveVideo) GlobalMediaPlayer.setVideoSpeed(speed) },
                    onVolumeChange = { vol -> if (isActiveVideo) GlobalMediaPlayer.setVideoVolume(vol) },
                    onMuteToggle = {
                        if (isActiveVideo) {
                            GlobalMediaPlayer.toggleVideoMute()
                        } else {
                            GlobalMediaPlayer.setDefaultMute(!GlobalMediaPlayer.defaultMuted)
                        }
                    },
                    onSave = { scope.launch { SaveMediaAction.saveMedia(url = url) } },
                    onFullscreen =
                        if (onFullscreen != null) {
                            {
                                onFullscreen(state?.position ?: 0f)
                            }
                        } else {
                            null
                        },
                    onViewModeChange = onViewModeChange,
                    trailingControls = trailingControls,
                    isLive = isLive,
                )
            }
        }
    }
}

/**
 * Waits for the card's own engine ([player]) to open its video, and says whether it got a frame. A
 * card that stays on screen ([onScreen]) without an engine, because the feed has already opened as
 * many as it keeps, or one whose engine errors or takes too long, gets false: it extracts a
 * thumbnail as before. A card off screen just waits: it opens an engine once it shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun engineShowsFrame(
    onScreen: () -> Boolean,
    player: () -> VideoPlayerState?,
): Boolean =
    snapshotFlow { onScreen() to player() }
        .transformLatest { (shown, engine) ->
            when {
                engine != null -> {
                    // Counted from the open: a paused one waits behind the playing video first.
                    GlobalMediaPlayer.awaitOpenStarted(engine)
                    val opened = withTimeoutOrNull(ENGINE_FRAME_WAIT_MS) { snapshotFlow { engine.hasMedia || engine.error != null }.first { it } }
                    emit(opened != null && engine.error == null)
                }

                shown -> {
                    delay(CARD_ENGINE_DELAY_MS + ENGINE_ATTACH_GRACE_MS)
                    emit(false)
                }
            }
        }.first()

// How long past CARD_ENGINE_DELAY_MS a card on screen waits for its engine before it extracts a
// thumbnail itself.
private const val ENGINE_ATTACH_GRACE_MS = 500L

// How long a card's engine may take to open its video, once it starts, before the card extracts a
// thumbnail anyway.
private const val ENGINE_FRAME_WAIT_MS = 10_000L

/** Below this the top, middle and bottom rows of [VideoControls] overflow. */
private val MinControlsWidth = 280.dp

// Longer than the navigation's push and pop transitions (the default 300 ms tween).
private const val SCREEN_TRANSITION_MS = 400L

@Composable
private fun PlaybackErrorMessage(
    url: String,
    reason: String,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringRes(Res.string.error_video_playback_failed),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    runCatching { Desktop.getDesktop().browse(URI(url)) }
                },
            ) {
                Text(stringRes(Res.string.video_error_open_in_default_player))
            }
        }
    }
}

/**
 * The picture a video shows before it plays: the note's own [posterUrl] through the shared image
 * loader, which the feed's prefetch has usually filled already, else the [thumbnail] extracted from
 * the video. [onPosterFailed] reports a poster that would not load.
 */
@Composable
private fun VideoPoster(
    posterUrl: String?,
    thumbnail: ImageBitmap?,
    modifier: Modifier,
    onPosterFailed: () -> Unit,
) {
    if (posterUrl != null) {
        AsyncImage(model = posterUrl, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit, onError = { onPosterFailed() })
    } else if (thumbnail != null) {
        Image(bitmap = thumbnail, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}
