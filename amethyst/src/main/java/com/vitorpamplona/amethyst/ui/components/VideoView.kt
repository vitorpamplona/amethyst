/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.components

import android.Manifest
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.linc.audiowaveform.infiniteLinearGradient
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.GenericBaseCache
import com.vitorpamplona.amethyst.commons.compose.produceCachedState
import com.vitorpamplona.amethyst.service.playback.PlaybackClientController
import com.vitorpamplona.amethyst.ui.actions.ImageSaver
import com.vitorpamplona.amethyst.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.ui.note.LyricsIcon
import com.vitorpamplona.amethyst.ui.note.LyricsOffIcon
import com.vitorpamplona.amethyst.ui.note.MuteIcon
import com.vitorpamplona.amethyst.ui.note.MutedIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.PinBottomIconSize
import com.vitorpamplona.amethyst.ui.theme.Size110dp
import com.vitorpamplona.amethyst.ui.theme.Size165dp
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.Size75dp
import com.vitorpamplona.amethyst.ui.theme.VolumeBottomIconSize
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.amethyst.ui.theme.videoGalleryModifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

public val DEFAULT_MUTED_SETTING = mutableStateOf(true)

@Composable
fun LoadThumbAndThenVideoView(
    videoUri: String,
    mimeType: String?,
    title: String? = null,
    thumbUri: String,
    authorName: String? = null,
    roundedCorner: Boolean,
    isFiniteHeight: Boolean,
    nostrUriCallback: String? = null,
    accountViewModel: AccountViewModel,
    onDialog: ((Boolean) -> Unit)? = null,
) {
    var loadingFinished by remember { mutableStateOf<Pair<Boolean, Drawable?>>(Pair(false, null)) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        accountViewModel.loadThumb(
            context,
            thumbUri,
            onReady = {
                loadingFinished =
                    if (it != null) {
                        Pair(true, it)
                    } else {
                        Pair(true, null)
                    }
            },
            onError = { loadingFinished = Pair(true, null) },
        )
    }

    if (loadingFinished.first) {
        if (loadingFinished.second != null) {
            VideoView(
                videoUri = videoUri,
                mimeType = mimeType,
                title = title,
                thumb = VideoThumb(loadingFinished.second),
                roundedCorner = roundedCorner,
                isFiniteHeight = isFiniteHeight,
                artworkUri = thumbUri,
                authorName = authorName,
                nostrUriCallback = nostrUriCallback,
                accountViewModel = accountViewModel,
                onDialog = onDialog,
            )
        } else {
            VideoView(
                videoUri = videoUri,
                mimeType = mimeType,
                title = title,
                thumb = null,
                roundedCorner = roundedCorner,
                isFiniteHeight = isFiniteHeight,
                artworkUri = thumbUri,
                authorName = authorName,
                nostrUriCallback = nostrUriCallback,
                accountViewModel = accountViewModel,
                onDialog = onDialog,
            )
        }
    }
}

@Composable
fun VideoView(
    videoUri: String,
    mimeType: String?,
    title: String? = null,
    thumb: VideoThumb? = null,
    roundedCorner: Boolean,
    gallery: Boolean = false,
    isFiniteHeight: Boolean,
    waveform: ImmutableList<Int>? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    dimensions: String? = null,
    blurhash: String? = null,
    nostrUriCallback: String? = null,
    onDialog: ((Boolean) -> Unit)? = null,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    accountViewModel: AccountViewModel,
    alwaysShowVideo: Boolean = false,
) {
    val defaultToStart by remember(videoUri) { mutableStateOf(DEFAULT_MUTED_SETTING.value) }

    val automaticallyStartPlayback =
        remember {
            mutableStateOf<Boolean>(
                if (alwaysShowVideo) true else accountViewModel.settings.startVideoPlayback.value,
            )
        }

    if (blurhash == null) {
        val ratio = aspectRatio(dimensions)
        val modifier =
            if (ratio != null && automaticallyStartPlayback.value) {
                Modifier.aspectRatio(ratio)
            } else {
                Modifier
            }

        Box(modifier) {
            if (!automaticallyStartPlayback.value) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ImageUrlWithDownloadButton(url = videoUri, showImage = automaticallyStartPlayback)
                }
            } else {
                VideoViewInner(
                    videoUri = videoUri,
                    mimeType = mimeType,
                    defaultToStart = defaultToStart,
                    title = title,
                    thumb = thumb,
                    roundedCorner = roundedCorner,
                    gallery = gallery,
                    isFiniteHeight = isFiniteHeight,
                    waveform = waveform,
                    artworkUri = artworkUri,
                    authorName = authorName,
                    nostrUriCallback = nostrUriCallback,
                    automaticallyStartPlayback = automaticallyStartPlayback,
                    onControllerVisibilityChanged = onControllerVisibilityChanged,
                    onDialog = onDialog,
                    accountViewModel = accountViewModel,
                )
            }
        }
    } else {
        val ratio = aspectRatio(dimensions)

        val modifier =
            if (ratio != null) {
                Modifier.aspectRatio(ratio)
            } else {
                Modifier
            }

        Box(modifier, contentAlignment = Alignment.Center) {
            val image =
                if (roundedCorner) {
                    MaterialTheme.colorScheme.imageModifier
                } else {
                    Modifier.fillMaxWidth()
                }

            // Always displays Blurharh to avoid size flickering
            DisplayBlurHash(
                blurhash,
                null,
                if (isFiniteHeight) ContentScale.FillWidth else ContentScale.FillWidth,
                if (ratio != null) image.aspectRatio(ratio) else modifier,
            )

            if (!automaticallyStartPlayback.value) {
                IconButton(
                    modifier = Modifier.size(Size75dp),
                    onClick = { automaticallyStartPlayback.value = true },
                ) {
                    DownloadForOfflineIcon(Size75dp, Color.White)
                }
            } else {
                VideoViewInner(
                    videoUri = videoUri,
                    mimeType = mimeType,
                    defaultToStart = defaultToStart,
                    title = title,
                    thumb = thumb,
                    roundedCorner = roundedCorner,
                    isFiniteHeight = isFiniteHeight,
                    waveform = waveform,
                    artworkUri = artworkUri,
                    authorName = authorName,
                    nostrUriCallback = nostrUriCallback,
                    automaticallyStartPlayback = automaticallyStartPlayback,
                    onControllerVisibilityChanged = onControllerVisibilityChanged,
                    onDialog = onDialog,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

@Composable
@OptIn(androidx.media3.common.util.UnstableApi::class)
fun VideoViewInner(
    videoUri: String,
    mimeType: String?,
    defaultToStart: Boolean = false,
    title: String? = null,
    thumb: VideoThumb? = null,
    roundedCorner: Boolean,
    gallery: Boolean = false,
    isFiniteHeight: Boolean,
    waveform: ImmutableList<Int>? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    nostrUriCallback: String? = null,
    automaticallyStartPlayback: State<Boolean>,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    onDialog: ((Boolean) -> Unit)? = null,
    accountViewModel: AccountViewModel,
) {
    VideoPlayerActiveMutex(videoUri) { modifier, activeOnScreen ->
        GetMediaItem(videoUri, title, artworkUri, authorName) { mediaItem ->
            GetVideoController(
                mediaItem = mediaItem,
                videoUri = videoUri,
                defaultToStart = defaultToStart,
                nostrUriCallback = nostrUriCallback,
            ) { controller, keepPlaying ->
                RenderVideoPlayer(
                    videoUri = videoUri,
                    mimeType = mimeType,
                    controller = controller,
                    thumbData = thumb,
                    roundedCorner = roundedCorner,
                    gallery = gallery,
                    isFiniteHeight = isFiniteHeight,
                    nostrUriCallback = nostrUriCallback,
                    waveform = waveform,
                    keepPlaying = keepPlaying,
                    automaticallyStartPlayback = automaticallyStartPlayback,
                    activeOnScreen = activeOnScreen,
                    modifier = modifier,
                    onControllerVisibilityChanged = onControllerVisibilityChanged,
                    onDialog = onDialog,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

val mediaItemCache = MediaItemCache()

@Immutable
data class MediaItemData(
    val videoUri: String,
    val authorName: String? = null,
    val title: String? = null,
    val artworkUri: String? = null,
)

class MediaItemCache : GenericBaseCache<MediaItemData, MediaItem>(20) {
    override suspend fun compute(key: MediaItemData): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(key.videoUri)
            .setUri(key.videoUri)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setArtist(key.authorName?.ifBlank { null })
                    .setTitle(key.title?.ifBlank { null } ?: key.videoUri)
                    .setArtworkUri(
                        try {
                            if (key.artworkUri != null) {
                                Uri.parse(key.artworkUri)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            null
                        },
                    ).build(),
            ).build()
}

@Composable
fun GetMediaItem(
    videoUri: String,
    title: String?,
    artworkUri: String?,
    authorName: String?,
    inner: @Composable (State<MediaItem>) -> Unit,
) {
    val data =
        remember(videoUri) {
            MediaItemData(
                videoUri = videoUri,
                authorName = authorName,
                title = title,
                artworkUri = artworkUri,
            )
        }
    val mediaItem by produceCachedState(cache = mediaItemCache, key = data)

    mediaItem?.let {
        val myState = remember(videoUri) { mutableStateOf(it) }
        inner(myState)
    }
}

@Composable
@OptIn(UnstableApi::class)
fun GetVideoController(
    mediaItem: State<MediaItem>,
    videoUri: String,
    defaultToStart: Boolean = false,
    nostrUriCallback: String? = null,
    inner: @Composable (controller: MediaController, keepPlaying: MutableState<Boolean>) -> Unit,
) {
    val context = LocalContext.current

    val onlyOnePreparing = AtomicBoolean()

    val controller =
        remember(videoUri) {
            mutableStateOf(
                if (videoUri == keepPlayingMutex?.currentMediaItem?.mediaId) {
                    keepPlayingMutex
                } else {
                    null
                },
            )
        }

    val keepPlaying =
        remember(videoUri) {
            mutableStateOf<Boolean>(
                keepPlayingMutex != null && controller.value == keepPlayingMutex,
            )
        }

    val uid = remember(videoUri) { UUID.randomUUID().toString() }

    val scope = rememberCoroutineScope()

    // Prepares a VideoPlayer from the foreground service.
    DisposableEffect(key1 = videoUri) {
        // If it is not null, the user might have come back from a playing video, like clicking on
        // the notification of the video player.
        if (controller.value == null) {
            // If there is a connection, don't wait.
            if (!onlyOnePreparing.getAndSet(true)) {
                scope.launch(Dispatchers.IO) {
                    Log.d("PlaybackService", "Preparing Video $videoUri ")
                    PlaybackClientController.prepareController(
                        uid,
                        videoUri,
                        nostrUriCallback,
                        context,
                    ) {
                        scope.launch(Dispatchers.Main) {
                            // REQUIRED TO BE RUN IN THE MAIN THREAD
                            if (!it.isPlaying) {
                                if (keepPlayingMutex?.isPlaying == true) {
                                    // There is a video playing, start this one on mute.
                                    it.volume = 0f
                                } else {
                                    // There is no other video playing. Use the default mute state to
                                    // decide if sound is on or not.
                                    it.volume = if (defaultToStart) 0f else 1f
                                }
                            }

                            it.setMediaItem(mediaItem.value)
                            it.prepare()

                            controller.value = it

                            onlyOnePreparing.getAndSet(false)
                        }
                    }
                }
            }
        } else {
            // has been loaded. prepare to play
            controller.value?.let {
                scope.launch(Dispatchers.Main) {
                    if (it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_ENDED) {
                        Log.d("PlaybackService", "Preparing Existing Video $videoUri ")

                        if (it.isPlaying) {
                            // There is a video playing, start this one on mute.
                            it.volume = 0f
                        } else {
                            // There is no other video playing. Use the default mute state to
                            // decide if sound is on or not.
                            it.volume = if (defaultToStart) 0f else 1f
                        }

                        if (mediaItem.value != it.currentMediaItem) {
                            it.setMediaItem(mediaItem.value)
                        }

                        it.prepare()
                    }
                }
            }
        }

        onDispose {
            if (!keepPlaying.value) {
                // Makes sure the variable is cleared before the task is launched
                // to avoid the ON_RELEASE running before ON_PAUSE's coroutine
                val toRelease = controller.value
                controller.value = null

                toRelease?.let {
                    it.pause()

                    GlobalScope.launch(Dispatchers.Main) {
                        // Stops and releases the media.
                        it.stop()
                        it.release()
                        Log.d("PlaybackService", "Releasing Video $videoUri ")
                    }
                }
            }
        }
    }

    // User pauses and resumes the app. What to do with videos?
    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(key1 = lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // if the controller is null, restarts the controller with a new one
                    // if the controller is not null, just continue playing what the controller was playing
                    if (controller.value == null) {
                        if (!onlyOnePreparing.getAndSet(true)) {
                            scope.launch(Dispatchers.IO) {
                                Log.d("PlaybackService", "Preparing Video from Resume $videoUri ")
                                PlaybackClientController.prepareController(
                                    uid,
                                    videoUri,
                                    nostrUriCallback,
                                    context,
                                ) {
                                    scope.launch(Dispatchers.Main) {
                                        // REQUIRED TO BE RUN IN THE MAIN THREAD
                                        // checks again to make sure no other thread has created a controller.
                                        if (!it.isPlaying) {
                                            if (keepPlayingMutex?.isPlaying == true) {
                                                // There is a video playing, start this one on mute.
                                                it.volume = 0f
                                            } else {
                                                // There is no other video playing. Use the default mute state to
                                                // decide if sound is on or not.
                                                it.volume = if (defaultToStart) 0f else 1f
                                            }
                                        }

                                        it.setMediaItem(mediaItem.value)
                                        it.prepare()

                                        controller.value = it
                                        onlyOnePreparing.getAndSet(false)
                                    }
                                }
                            }
                        }
                    }
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    if (!keepPlaying.value) {
                        // Stops and releases the media.
                        // Makes sure the variable is cleared before the task is launched
                        // to avoid the ON_RELEASE running before ON_PAUSE's coroutine
                        val toRelease = controller.value
                        controller.value = null

                        toRelease?.let {
                            it.pause()

                            scope.launch(Dispatchers.Main) {
                                Log.d("PlaybackService", "Releasing Video from Pause $videoUri ")
                                it.stop()
                                it.release()
                                Log.d("PlaybackService", "Released Video from Pause $videoUri ")
                            }
                        }
                    }
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    controller.value?.let {
        inner(it, keepPlaying)
    }
}

// background playing mutex.
var keepPlayingMutex: MediaController? = null

// This keeps the position of all visible videos in the current screen.
val trackingVideos = mutableListOf<VisibilityData>()

@Stable
class VisibilityData {
    var distanceToCenter: Float? = null
}

/**
 * This function selects only one Video to be active. The video that is closest to the center of the
 * screen wins the mutex.
 */
@Composable
fun VideoPlayerActiveMutex(
    videoUri: String,
    inner: @Composable (Modifier, MutableState<Boolean>) -> Unit,
) {
    val myCache = remember(videoUri) { VisibilityData() }

    // Is the current video the closest to the center?
    val active = remember(videoUri) { mutableStateOf<Boolean>(false) }

    // Keep track of all available videos.
    DisposableEffect(key1 = videoUri) {
        trackingVideos.add(myCache)
        onDispose { trackingVideos.remove(myCache) }
    }

    val myModifier =
        remember(videoUri) {
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 70.dp).onVisiblePositionChanges { distanceToCenter ->
                myCache.distanceToCenter = distanceToCenter

                if (distanceToCenter != null) {
                    // finds out of the current video is the closest to the center.
                    var newActive = true
                    for (video in trackingVideos) {
                        val videoPos = video.distanceToCenter
                        if (videoPos != null && videoPos < distanceToCenter) {
                            newActive = false
                            break
                        }
                    }

                    // marks the current video active
                    if (active.value != newActive) {
                        active.value = newActive
                    }
                } else {
                    // got out of screen, marks video as inactive
                    if (active.value) {
                        active.value = false
                    }
                }
            }
        }

    inner(myModifier, active)
}

@Stable
data class VideoThumb(
    val thumb: Drawable?,
)

@Composable
@OptIn(UnstableApi::class)
private fun RenderVideoPlayer(
    videoUri: String,
    mimeType: String?,
    controller: MediaController,
    thumbData: VideoThumb?,
    roundedCorner: Boolean,
    gallery: Boolean = false,
    isFiniteHeight: Boolean,
    nostrUriCallback: String?,
    waveform: ImmutableList<Int>? = null,
    keepPlaying: MutableState<Boolean>,
    automaticallyStartPlayback: State<Boolean>,
    activeOnScreen: MutableState<Boolean>,
    modifier: Modifier,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    onDialog: ((Boolean) -> Unit)?,
    accountViewModel: AccountViewModel,
) {
    ControlWhenPlayerIsActive(controller, keepPlaying, automaticallyStartPlayback, activeOnScreen)

    val controllerVisible = remember(controller) { mutableStateOf(false) }

    Box {
        val borders = MaterialTheme.colorScheme.imageModifier
        val bordersSquare = MaterialTheme.colorScheme.videoGalleryModifier
        val myModifier =
            remember(controller) {
                if (roundedCorner) {
                    modifier.then(
                        borders.defaultMinSize(minHeight = 75.dp).align(Alignment.Center),
                    )
                } else if (gallery) {
                    Modifier
                    modifier.then(
                        bordersSquare.defaultMinSize(minHeight = 75.dp).align(Alignment.Center),
                    )
                } else {
                    modifier.fillMaxWidth().defaultMinSize(minHeight = 75.dp).align(Alignment.Center)
                }
            }

        AndroidView(
            modifier = myModifier,
            factory = { context: Context ->
                PlayerView(context).apply {
                    player = controller
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    setBackgroundColor(Color.Transparent.toArgb())
                    setShutterBackgroundColor(Color.Transparent.toArgb())
                    controllerAutoShow = false
                    useController = !gallery
                    thumbData?.thumb?.let { defaultArtwork = it }
                    hideController()
                    resizeMode =
                        if (isFiniteHeight) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        }
                    if (!gallery) {
                        onDialog?.let { innerOnDialog ->
                            setFullscreenButtonClickListener {
                                controller.pause()
                                innerOnDialog(it)
                            }
                        }
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visible ->
                                controllerVisible.value = visible == View.VISIBLE
                                onControllerVisibilityChanged?.let { callback -> callback(visible == View.VISIBLE) }
                            },
                        )
                    }
                }
            },
        )

        waveform?.let { Waveform(it, controller, remember { Modifier.align(Alignment.Center) }) }
        if (!gallery) {
            val startingMuteState = remember(controller) { controller.volume < 0.001 }

            MuteButton(
                controllerVisible,
                startingMuteState,
                Modifier.align(Alignment.TopEnd),
            ) { mute: Boolean ->
                // makes the new setting the default for new creations.
                DEFAULT_MUTED_SETTING.value = mute

                // if the user unmutes a video and it's not the current playing, switches to that one.
                if (!mute && keepPlayingMutex != null && keepPlayingMutex != controller) {
                    keepPlayingMutex?.stop()
                    keepPlayingMutex?.release()
                    keepPlayingMutex = null
                }

                controller.volume = if (mute) 0f else 1f
            }

            KeepPlayingButton(
                keepPlaying,
                controllerVisible,
                Modifier.align(Alignment.TopEnd).padding(end = Size55dp),
            ) { newKeepPlaying: Boolean ->
                // If something else is playing and the user marks this video to keep playing, stops the other
                // one.
                if (newKeepPlaying) {
                    if (keepPlayingMutex != null && keepPlayingMutex != controller) {
                        keepPlayingMutex?.stop()
                        keepPlayingMutex?.release()
                    }
                    keepPlayingMutex = controller
                } else {
                    if (keepPlayingMutex == controller) {
                        keepPlayingMutex = null
                    }
                }

                keepPlaying.value = newKeepPlaying
            }

            AnimatedSaveButton(controllerVisible, Modifier.align(Alignment.TopEnd).padding(end = Size110dp)) { context ->
                saveImage(videoUri, mimeType, context, accountViewModel)
            }

            AnimatedShareButton(controllerVisible, Modifier.align(Alignment.TopEnd).padding(end = Size165dp)) { popupExpanded, toggle ->
                ShareImageAction(accountViewModel = accountViewModel, popupExpanded, videoUri, null, null, null, null, nostrUriCallback, toggle)
            }
        } else {
            controller.volume = 0f
        }
    }
}

private fun pollCurrentDuration(controller: MediaController) =
    flow {
        while (controller.currentPosition <= controller.duration) {
            emit(controller.currentPosition / controller.duration.toFloat())
            delay(100)
        }
    }.conflate()

@Composable
fun Waveform(
    waveform: ImmutableList<Int>,
    controller: MediaController,
    modifier: Modifier,
) {
    val waveformProgress = remember { mutableFloatStateOf(0F) }

    DrawWaveform(waveform, waveformProgress, modifier)

    val restartFlow = remember { mutableIntStateOf(0) }

    // Keeps the screen on while playing and viewing videos.
    DisposableEffect(key1 = controller) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // doesn't consider the mutex because the screen can turn off if the video
                    // being played in the mutex is not visible.
                    if (isPlaying) {
                        restartFlow.intValue += 1
                    }
                }
            }

        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(key1 = restartFlow.intValue) {
        pollCurrentDuration(controller).collect { value -> waveformProgress.floatValue = value }
    }
}

@Composable
fun DrawWaveform(
    waveform: ImmutableList<Int>,
    waveformProgress: MutableFloatState,
    modifier: Modifier,
) {
    AudioWaveformReadOnly(
        modifier = modifier.padding(start = 10.dp, end = 10.dp),
        amplitudes = waveform,
        progress = waveformProgress.floatValue,
        progressBrush =
            Brush.infiniteLinearGradient(
                colors = listOf(Color(0xff2598cf), Color(0xff652d80)),
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                width = 128F,
            ),
        onProgressChange = { waveformProgress.floatValue = it },
    )
}

@Composable
fun ControlWhenPlayerIsActive(
    controller: Player,
    keepPlaying: MutableState<Boolean>,
    automaticallyStartPlayback: State<Boolean>,
    activeOnScreen: MutableState<Boolean>,
) {
    LaunchedEffect(key1 = activeOnScreen.value) {
        // active means being fully visible
        if (activeOnScreen.value) {
            // should auto start video from settings?
            if (!automaticallyStartPlayback.value) {
                if (controller.isPlaying) {
                    // if it is visible, it's playing but it wasn't supposed to start automatically.
                    controller.pause()
                }
            } else if (!controller.isPlaying) {
                // if it is visible, was supposed to start automatically, but it's not

                // If something else is playing, play on mute.
                if (keepPlayingMutex != null && keepPlayingMutex != controller) {
                    controller.volume = 0f
                }
                controller.play()
            }
        } else {
            // Pauses the video when it becomes invisible.
            // Destroys the video later when it Disposes the element
            // meanwhile if the user comes back, the position in the track is saved.
            if (!keepPlaying.value) {
                controller.pause()
            }
        }
    }

    val view = LocalView.current

    // Keeps the screen on while playing and viewing videos.
    DisposableEffect(key1 = controller, key2 = view) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // doesn't consider the mutex because the screen can turn off if the video
                    // being played in the mutex is not visible.
                    if (view.keepScreenOn != isPlaying) {
                        view.keepScreenOn = isPlaying
                    }
                }
            }

        controller.addListener(listener)
        onDispose {
            if (view.keepScreenOn) {
                view.keepScreenOn = false
            }
            controller.removeListener(listener)
        }
    }
}

fun Modifier.onVisiblePositionChanges(onVisiblePosition: (Float?) -> Unit): Modifier =
    composed {
        val view = LocalView.current

        onGloballyPositioned { coordinates ->
            onVisiblePosition(coordinates.getDistanceToVertCenterIfVisible(view))
        }
    }

fun LayoutCoordinates.getDistanceToVertCenterIfVisible(view: View): Float? {
    if (!isAttached) return null
    // Window relative bounds of our compose root view that are visible on the screen
    val globalRootRect = Rect()
    if (!view.getGlobalVisibleRect(globalRootRect)) {
        // we aren't visible at all.
        return null
    }

    val bounds = boundsInWindow()

    if (bounds.isEmpty) return null

    // Make sure we are completely in bounds.
    if (
        bounds.top >= globalRootRect.top &&
        bounds.left >= globalRootRect.left &&
        bounds.right <= globalRootRect.right &&
        bounds.bottom <= globalRootRect.bottom
    ) {
        return abs(
            ((bounds.top + bounds.bottom) / 2) - ((globalRootRect.top + globalRootRect.bottom) / 2),
        )
    }

    return null
}

@Composable
private fun MuteButton(
    controllerVisible: MutableState<Boolean>,
    startingMuteState: Boolean,
    modifier: Modifier,
    toggle: (Boolean) -> Unit,
) {
    val holdOn =
        remember {
            mutableStateOf<Boolean>(
                true,
            )
        }

    LaunchedEffect(key1 = controllerVisible) {
        launch(Dispatchers.Default) {
            delay(2000)
            holdOn.value = false
        }
    }

    val mutedInstance = remember(startingMuteState) { mutableStateOf(startingMuteState) }

    AnimatedVisibility(
        visible = holdOn.value || controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        Box(modifier = VolumeBottomIconSize) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.6f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background),
            )

            IconButton(
                onClick = {
                    mutedInstance.value = !mutedInstance.value
                    toggle(mutedInstance.value)
                },
                modifier = Size50Modifier,
            ) {
                if (mutedInstance.value) {
                    MutedIcon()
                } else {
                    MuteIcon()
                }
            }
        }
    }
}

@Composable
private fun KeepPlayingButton(
    keepPlayingStart: MutableState<Boolean>,
    controllerVisible: MutableState<Boolean>,
    modifier: Modifier,
    toggle: (Boolean) -> Unit,
) {
    val keepPlaying = remember(keepPlayingStart.value) { mutableStateOf(keepPlayingStart.value) }

    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        Box(modifier = PinBottomIconSize) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.6f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background),
            )

            IconButton(
                onClick = {
                    keepPlaying.value = !keepPlaying.value
                    toggle(keepPlaying.value)
                },
                modifier = Size50Modifier,
            ) {
                if (keepPlaying.value) {
                    LyricsIcon(Size22Modifier, MaterialTheme.colorScheme.onBackground)
                } else {
                    LyricsOffIcon(Size22Modifier, MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

@Composable
fun AnimatedSaveButton(
    controllerVisible: State<Boolean>,
    modifier: Modifier,
    onSaveClick: (localContext: Context) -> Unit,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        SaveButton(onSaveClick)
    }
}

@Composable
fun AnimatedShareButton(
    controllerVisible: State<Boolean>,
    modifier: Modifier,
    innerAction: @Composable (MutableState<Boolean>, () -> Unit) -> Unit,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        ShareButton(innerAction)
    }
}

@Composable
fun ShareButton(innerAction: @Composable (MutableState<Boolean>, () -> Unit) -> Unit) {
    Box(modifier = PinBottomIconSize) {
        Box(
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.6f)
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.background),
        )

        val popupExpanded = remember { mutableStateOf(false) }

        IconButton(
            onClick = {
                popupExpanded.value = true
            },
            modifier = Size50Modifier,
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                modifier = Size20Modifier,
                contentDescription = stringRes(R.string.share_or_save),
            )

            innerAction(popupExpanded) { popupExpanded.value = false }
        }
    }
}

@kotlin.OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SaveButton(onSaveClick: (localContext: Context) -> Unit) {
    Box(modifier = PinBottomIconSize) {
        Box(
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.6f)
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.background),
        )

        val localContext = LocalContext.current

        val writeStoragePermissionState =
            rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) { isGranted ->
                if (isGranted) {
                    onSaveClick(localContext)
                }
            }

        IconButton(
            onClick = {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                    writeStoragePermissionState.status.isGranted
                ) {
                    onSaveClick(localContext)
                } else {
                    writeStoragePermissionState.launchPermissionRequest()
                }
            },
            modifier = Size50Modifier,
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                modifier = Size20Modifier,
                contentDescription = stringRes(R.string.save_to_gallery),
            )
        }
    }
}

private fun saveImage(
    videoUri: String?,
    mimeType: String?,
    localContext: Context,
    accountViewModel: AccountViewModel,
) {
    ImageSaver.saveImage(
        videoUri = videoUri,
        mimeType = mimeType,
        localContext = localContext,
        onSuccess = {
            accountViewModel.toast(R.string.image_saved_to_the_gallery, R.string.image_saved_to_the_gallery)
        },
        onError = {
            accountViewModel.toast(R.string.failed_to_save_the_image, null, it)
        },
    )
}
