package com.vitorpamplona.amethyst.ui.components

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
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
import coil.imageLoader
import coil.request.ImageRequest
import com.vitorpamplona.amethyst.PlaybackClientController
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.service.connectivitystatus.ConnectivityStatus
import com.vitorpamplona.amethyst.ui.note.LyricsIcon
import com.vitorpamplona.amethyst.ui.note.LyricsOffIcon
import com.vitorpamplona.amethyst.ui.note.MuteIcon
import com.vitorpamplona.amethyst.ui.note.MutedIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.PinBottomIconSize
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.ui.theme.VolumeBottomIconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

public var DefaultMutedSetting = mutableStateOf(true)

@Composable
fun LoadThumbAndThenVideoView(
    videoUri: String,
    title: String? = null,
    thumbUri: String,
    authorName: String? = null,
    nostrUriCallback: String? = null,
    accountViewModel: AccountViewModel,
    onDialog: ((Boolean) -> Unit)? = null
) {
    var loadingFinished by remember { mutableStateOf<Pair<Boolean, Drawable?>>(Pair(false, null)) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context).data(thumbUri).build()
                val myCover = context.imageLoader.execute(request).drawable
                if (myCover != null) {
                    loadingFinished = Pair(true, myCover)
                } else {
                    loadingFinished = Pair(true, null)
                }
            } catch (e: Exception) {
                Log.e("VideoView", "Fail to load cover $thumbUri", e)
                loadingFinished = Pair(true, null)
            }
        }
    }

    if (loadingFinished.first) {
        if (loadingFinished.second != null) {
            VideoView(
                videoUri = videoUri,
                title = title,
                thumb = VideoThumb(loadingFinished.second),
                artworkUri = thumbUri,
                authorName = authorName,
                nostrUriCallback = nostrUriCallback,
                accountViewModel = accountViewModel,
                onDialog = onDialog
            )
        } else {
            VideoView(
                videoUri = videoUri,
                title = title,
                thumb = null,
                artworkUri = thumbUri,
                authorName = authorName,
                nostrUriCallback = nostrUriCallback,
                accountViewModel = accountViewModel,
                onDialog = onDialog
            )
        }
    }
}

@Composable
fun VideoView(
    videoUri: String,
    title: String? = null,
    thumb: VideoThumb? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    nostrUriCallback: String? = null,
    onDialog: ((Boolean) -> Unit)? = null,
    accountViewModel: AccountViewModel,
    alwaysShowVideo: Boolean = false
) {
    val defaultToStart by remember(videoUri) { mutableStateOf(DefaultMutedSetting.value) }

    VideoViewInner(
        videoUri,
        defaultToStart,
        title,
        thumb,
        artworkUri,
        authorName,
        nostrUriCallback,
        alwaysShowVideo,
        accountViewModel,
        onDialog
    )
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun VideoViewInner(
    videoUri: String,
    defaultToStart: Boolean = false,
    title: String? = null,
    thumb: VideoThumb? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    nostrUriCallback: String? = null,
    alwaysShowVideo: Boolean = false,
    accountViewModel: AccountViewModel,
    onDialog: ((Boolean) -> Unit)? = null
) {
    val automaticallyStartPlayback = remember {
        mutableStateOf(
            if (alwaysShowVideo) { true } else {
                when (accountViewModel.account.settings.automaticallyStartPlayback) {
                    ConnectivityType.WIFI_ONLY -> !ConnectivityStatus.isOnMobileData.value
                    ConnectivityType.NEVER -> false
                    ConnectivityType.ALWAYS -> true
                }
            }
        )
    }

    if (!automaticallyStartPlayback.value) {
        ImageUrlWithDownloadButton(url = videoUri, showImage = automaticallyStartPlayback)
    } else {
        VideoPlayerActiveMutex(videoUri) { activeOnScreen ->
            val mediaItem = remember(videoUri) {
                MediaItem.Builder()
                    .setMediaId(videoUri)
                    .setUri(videoUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setArtist(authorName?.ifBlank { null })
                            .setTitle(title?.ifBlank { null } ?: videoUri)
                            .setArtworkUri(
                                try {
                                    if (artworkUri != null) {
                                        Uri.parse(artworkUri)
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            )
                            .build()
                    )
                    .build()
            }

            GetVideoController(
                mediaItem = mediaItem,
                videoUri = videoUri,
                defaultToStart = defaultToStart,
                nostrUriCallback = nostrUriCallback
            ) { controller, keepPlaying ->
                RenderVideoPlayer(controller, thumb, keepPlaying, automaticallyStartPlayback, activeOnScreen, onDialog)
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
fun GetVideoController(
    mediaItem: MediaItem,
    videoUri: String,
    defaultToStart: Boolean = false,
    nostrUriCallback: String? = null,
    inner: @Composable (controller: MediaController, keepPlaying: MutableState<Boolean>) -> Unit
) {
    val context = LocalContext.current

    val controller = remember(videoUri) {
        mutableStateOf<MediaController?>(
            if (videoUri == keepPlayingMutex?.currentMediaItem?.mediaId) keepPlayingMutex else null
        )
    }

    val keepPlaying = remember(videoUri) {
        mutableStateOf<Boolean>(
            keepPlayingMutex != null && controller.value == keepPlayingMutex
        )
    }

    val uid = remember(videoUri) {
        UUID.randomUUID().toString()
    }

    // Prepares a VideoPlayer from the foreground service.
    LaunchedEffect(key1 = videoUri) {
        // If it is not null, the user might have come back from a playing video, like clicking on
        // the notification of the video player.
        if (controller.value == null) {
            launch(Dispatchers.IO) {
                PlaybackClientController.prepareController(
                    uid,
                    videoUri,
                    nostrUriCallback,
                    context
                ) {
                    // checks again because of race conditions.
                    if (controller.value == null) { // still prone to race conditions.
                        controller.value = it

                        if (!it.isPlaying) {
                            if (keepPlayingMutex?.isPlaying == true) {
                                // There is a video playing, start this one on mute.
                                controller.value?.volume = 0f
                            } else {
                                // There is no other video playing. Use the default mute state to
                                // decide if sound is on or not.
                                controller.value?.volume = if (defaultToStart) 0f else 1f
                            }
                        }

                        controller.value?.setMediaItem(mediaItem)
                        controller.value?.prepare()
                    } else if (controller.value != it) {
                        // discards the new controller because there is an existing one
                        it.stop()
                        it.release()

                        controller.value?.let {
                            if (it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_ENDED) {
                                if (it.isPlaying) {
                                    // There is a video playing, start this one on mute.
                                    it.volume = 0f
                                } else {
                                    // There is no other video playing. Use the default mute state to
                                    // decide if sound is on or not.
                                    it.volume = if (defaultToStart) 0f else 1f
                                }

                                it.setMediaItem(mediaItem)
                                it.prepare()
                            }
                        }
                    }
                }
            }
        } else {
            controller.value?.let {
                if (it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_ENDED) {
                    if (it.isPlaying) {
                        // There is a video playing, start this one on mute.
                        it.volume = 0f
                    } else {
                        // There is no other video playing. Use the default mute state to
                        // decide if sound is on or not.
                        it.volume = if (defaultToStart) 0f else 1f
                    }

                    it.setMediaItem(mediaItem)
                    it.prepare()
                }
            }
        }
    }

    // User pauses and resumes the app. What to do with videos?
    val scope = rememberCoroutineScope()
    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(key1 = videoUri) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // if the controller is null, restarts the controller with a new one
                // if the controller is not null, just continue playing what the controller was playing
                if (controller.value == null) {
                    scope.launch(Dispatchers.IO) {
                        PlaybackClientController.prepareController(
                            UUID.randomUUID().toString(),
                            videoUri,
                            nostrUriCallback,
                            context
                        ) {
                            // checks again to make sure no other thread has created a controller.
                            if (controller.value == null) {
                                controller.value = it

                                if (!it.isPlaying) {
                                    if (keepPlayingMutex?.isPlaying == true) {
                                        // There is a video playing, start this one on mute.
                                        controller.value?.volume = 0f
                                    } else {
                                        // There is no other video playing. Use the default mute state to
                                        // decide if sound is on or not.
                                        controller.value?.volume = if (defaultToStart) 0f else 1f
                                    }
                                }

                                controller.value?.setMediaItem(mediaItem)
                                controller.value?.prepare()
                            } else if (controller.value != it) {
                                // discards the new controller because there is an existing one
                                it.stop()
                                it.release()
                            }
                        }
                    }
                }
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (!keepPlaying.value) {
                    // Stops and releases the media.
                    controller.value?.stop()
                    controller.value?.release()
                    controller.value = null
                }
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)

            if (!keepPlaying.value) {
                // Stops and releases the media.
                controller.value?.stop()
                controller.value?.release()
                controller.value = null
            }
        }
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
class VisibilityData() {
    var distanceToCenter: Float? = null
}

/**
 * This function selects only one Video to be active. The video that is closest to the center of
 * the screen wins the mutex.
 */
@Composable
fun VideoPlayerActiveMutex(videoUri: String, inner: @Composable (MutableState<Boolean>) -> Unit) {
    val myCache = remember(videoUri) {
        VisibilityData()
    }

    // Is the current video the closest to the center?
    val active = remember(videoUri) {
        mutableStateOf<Boolean>(false)
    }

    // Keep track of all available videos.
    DisposableEffect(key1 = videoUri) {
        trackingVideos.add(myCache)
        onDispose {
            trackingVideos.remove(myCache)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 70.dp)
            .onVisiblePositionChanges { distanceToCenter ->
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
    ) {
        inner(active)
    }
}

@Stable
data class VideoThumb(
    val thumb: Drawable?
)

@Composable
@OptIn(UnstableApi::class)
private fun RenderVideoPlayer(
    controller: MediaController,
    thumbData: VideoThumb?,
    keepPlaying: MutableState<Boolean>,
    automaticallyStartPlayback: MutableState<Boolean>,
    activeOnScreen: MutableState<Boolean>,
    onDialog: ((Boolean) -> Unit)?
) {
    val context = LocalContext.current

    ControlWhenPlayerIsActive(controller, keepPlaying, automaticallyStartPlayback, activeOnScreen)

    val controllerVisible = remember(controller) {
        mutableStateOf(false)
    }

    BoxWithConstraints() {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 70.dp)
                .align(Alignment.Center),
            factory = {
                PlayerView(context).apply {
                    player = controller
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    controllerAutoShow = false
                    thumbData?.thumb?.let { defaultArtwork = it }
                    hideController()
                    resizeMode =
                        if (maxHeight.isFinite) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    onDialog?.let { innerOnDialog ->
                        setFullscreenButtonClickListener {
                            controller.pause()
                            innerOnDialog(it)
                        }
                    }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener {
                            controllerVisible.value = it == View.VISIBLE
                        }
                    )
                }
            }
        )

        val startingMuteState = remember(controller) {
            controller.volume < 0.001
        }

        MuteButton(controllerVisible, startingMuteState) { mute: Boolean ->
            // makes the new setting the default for new creations.
            DefaultMutedSetting.value = mute

            // if the user unmutes a video and it's not the current playing, switches to that one.
            if (!mute && keepPlayingMutex != null && keepPlayingMutex != controller) {
                keepPlayingMutex?.stop()
                keepPlayingMutex?.release()
                keepPlayingMutex = null
            }

            controller.volume = if (mute) 0f else 1f
        }

        KeepPlayingButton(keepPlaying, controllerVisible, Modifier.align(Alignment.TopEnd)) { newKeepPlaying: Boolean ->
            // If something else is playing and the user marks this video to keep playing, stops the other one.
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
    }
}

@Composable
fun ControlWhenPlayerIsActive(
    controller: Player,
    keepPlaying: MutableState<Boolean>,
    automaticallyStartPlayback: MutableState<Boolean>,
    activeOnScreen: MutableState<Boolean>
) {
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

    val view = LocalView.current

    // Keeps the screen on while playing and viewing videos.
    DisposableEffect(key1 = controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // doesn't consider the mutex because the screen can turn off if the video
                // being played in the mutex is not visible.
                view.keepScreenOn = isPlaying
            }
        }

        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
        }
    }
}

fun Modifier.onVisiblePositionChanges(onVisiblePosition: (Float?) -> Unit): Modifier = composed {
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
    if (bounds.top >= globalRootRect.top &&
        bounds.left >= globalRootRect.left &&
        bounds.right <= globalRootRect.right &&
        bounds.bottom <= globalRootRect.bottom
    ) {
        return abs(((bounds.top + bounds.bottom) / 2) - ((globalRootRect.top + globalRootRect.bottom) / 2))
    }

    return null
}

@Composable
private fun MuteButton(
    controllerVisible: MutableState<Boolean>,
    startingMuteState: Boolean,
    toggle: (Boolean) -> Unit
) {
    val holdOn = remember {
        mutableStateOf<Boolean>(
            true
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
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = VolumeBottomIconSize) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.6f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colors.background)
            )

            IconButton(
                onClick = {
                    mutedInstance.value = !mutedInstance.value
                    toggle(mutedInstance.value)
                },
                modifier = Size50Modifier
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
    alignment: Modifier,
    toggle: (Boolean) -> Unit
) {
    val keepPlaying = remember(keepPlayingStart.value) { mutableStateOf(keepPlayingStart.value) }

    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = alignment,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = PinBottomIconSize) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.6f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colors.background)
            )

            IconButton(
                onClick = {
                    keepPlaying.value = !keepPlaying.value
                    toggle(keepPlaying.value)
                },
                modifier = Size50Modifier
            ) {
                if (keepPlaying.value) {
                    LyricsIcon(Size22Modifier, MaterialTheme.colors.onBackground)
                } else {
                    LyricsOffIcon(Size22Modifier, MaterialTheme.colors.onBackground)
                }
            }
        }
    }
}
