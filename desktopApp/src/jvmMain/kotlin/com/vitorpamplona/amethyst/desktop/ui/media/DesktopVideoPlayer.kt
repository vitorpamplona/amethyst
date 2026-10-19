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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_download_for_offline
import com.vitorpamplona.amethyst.commons.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.commons.ui.components.LocalShortVideo
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.zoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.media.VideoThumbnailCache
import com.vitorpamplona.quartz.utils.Log
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

// Shorter than this, a video is a still image (one frame); see the poster fade below.
private const val STILL_VIDEO_MILLIS = 300L

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
    // Only this video's state: every other card on screen would otherwise recompose on each tick
    // of whichever video is playing.
    val ownState =
        remember(url) {
            GlobalMediaPlayer.videoState.map { it.takeIf { state -> state.url == url } }.distinctUntilChanged()
        }
    val videoState by ownState.collectAsState(GlobalMediaPlayer.videoState.value.takeIf { it.url == url })
    val isShortVideo = LocalShortVideo.current
    val isActiveVideo = videoState != null
    val scope = rememberCoroutineScope()

    // This card, as the one composable allowed to draw the engine (see videoSurfaceOwner).
    val surfaceOwner = remember { Any() }
    val currentSurfaceOwner by GlobalMediaPlayer.videoSurfaceOwner.collectAsState()
    val isFullscreen by GlobalMediaPlayer.isFullscreen.collectAsState()
    LaunchedEffect(isActiveVideo, currentSurfaceOwner) {
        // The video is this one and nobody draws it (its owner left): take it over.
        if (isActiveVideo && currentSurfaceOwner == null) GlobalMediaPlayer.claimVideoSurface(surfaceOwner)
    }
    DisposableEffect(surfaceOwner) {
        onDispose { GlobalMediaPlayer.releaseVideoSurface(surfaceOwner) }
    }

    var loaded by remember(url) { mutableStateOf(!loadOnDemand || autoPlay) }
    var thumbnail by remember(url) { mutableStateOf(VideoThumbnailCache.getCached(url)) }
    var aspectRatio by remember(url) { mutableFloatStateOf(16f / 9f) }

    LaunchedEffect(url, loaded) {
        // A note's own poster is drawn straight from the image loader (see VideoPoster); only a
        // video without one has a frame extracted, which downloads part of the video.
        if (loaded && thumbnail == null && posterUrl == null) {
            VideoThumbnailCache.getThumbnail(url)?.let { thumbnail = it }
        }
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
                if (isWinner.value) {
                    hasBeenVisible[0] = true
                    // The card in view draws the video, also when another card showing it started it.
                    if (state.url == url) GlobalMediaPlayer.claimVideoSurface(surfaceOwner)
                    val ended = state.url == url && state.position >= 0.99f
                    if (autoPlayWhenVisible && loaded && !ended && (state.url != url || !state.isPlaying)) {
                        GlobalMediaPlayer.playVideo(url, owner = surfaceOwner)
                    }
                } else if (hasBeenVisible[0] && pauseWhenHidden && state.url == url) {
                    // A screen sliding or scaling away takes its cards out of view too. Waiting
                    // out that transition lets the card leave first, and releaseVideo decide.
                    delay(SCREEN_TRANSITION_MS)
                    // Meanwhile another card showing the same video may have come into view.
                    val owner = GlobalMediaPlayer.videoSurfaceOwner.value
                    val handedOver = owner != null && owner !== surfaceOwner
                    if (handedOver || GlobalMediaPlayer.isFullscreen.value || GlobalMediaPlayer.videoState.value.url != url) {
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

    videoState?.aspectRatio?.let { if (it != 16f / 9f) aspectRatio = it }
    // The shape before this video plays, best source first: what the playing engine reports, what
    // the note declares (imeta `dim`), what an engine that opened it ahead or earlier recorded (read
    // in composition, so this card resizes when that lands), what its thumbnail shows. 16:9 only
    // when nothing knows: without these a portrait Short sat in a landscape box until it played.
    val targetRatio =
        if (aspectRatio != 16f / 9f) {
            aspectRatio
        } else {
            declaredRatio
                ?: MediaAspectRatioCache.get(url)
                ?: thumbnail?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height }
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
    val activePlayer = if (drawsEngine) GlobalMediaPlayer.activeVideoPlayerState else null

    // Black behind a picture, as any player letterboxes; the theme's placeholder until there is one,
    // and behind the error message, which is drawn in the theme's colors.
    val backdrop =
        if (loaded && errorReason == null && (activePlayer != null || thumbnail != null || posterUrl != null)) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    BoxWithConstraints(modifier = modifier.then(visibilityModifier), contentAlignment = Alignment.TopCenter) {
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
                VideoPlayerSurface(
                    playerState = activePlayer,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
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
                    VideoPoster(posterUrl, thumbnail, Modifier.fillMaxSize().graphicsLayer { alpha = thumbnailAlpha })
                }
            } else {
                VideoPoster(posterUrl, thumbnail, Modifier.fillMaxSize())
            }

            if (errorReason == null) {
                val state = videoState
                VideoControls(
                    // A Short (a short-form event, or any vertical video) that plays on its own
                    // arrives without controls.
                    hideWhenIdle = autoPlayWhenVisible && loaded && (isShortVideo || ratio < 1f),
                    isPlaying = state?.isPlaying ?: false,
                    isBuffering = state?.isBuffering ?: false,
                    position = state?.position ?: 0f,
                    duration = state?.duration ?: 0L,
                    currentTime = state?.currentTime ?: 0L,
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
                text = "Can't play this video",
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
                Text("Open in default player")
            }
        }
    }
}

/**
 * The picture a video shows before it plays: the note's own [posterUrl] through the shared image
 * loader, which the feed's prefetch has usually filled already, else the [thumbnail] extracted from
 * the video.
 */
@Composable
private fun VideoPoster(
    posterUrl: String?,
    thumbnail: ImageBitmap?,
    modifier: Modifier,
) {
    if (posterUrl != null) {
        AsyncImage(model = posterUrl, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    } else if (thumbnail != null) {
        Image(bitmap = thumbnail, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}
