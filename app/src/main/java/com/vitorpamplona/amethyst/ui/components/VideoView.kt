package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.isSpecified
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
import com.linc.audiowaveform.infiniteLinearGradient
import com.vitorpamplona.amethyst.service.playback.PlaybackClientController
import com.vitorpamplona.amethyst.ui.note.LyricsIcon
import com.vitorpamplona.amethyst.ui.note.LyricsOffIcon
import com.vitorpamplona.amethyst.ui.note.MuteIcon
import com.vitorpamplona.amethyst.ui.note.MutedIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.PinBottomIconSize
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.ui.theme.VolumeBottomIconSize
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
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
    roundedCorner: Boolean,
    nostrUriCallback: String? = null,
    accountViewModel: AccountViewModel,
    onDialog: ((Boolean) -> Unit)? = null
) {
    var loadingFinished by remember { mutableStateOf<Pair<Boolean, Drawable?>>(Pair(false, null)) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        accountViewModel.loadThumb(
            context,
            thumbUri,
            onReady = {
                if (it != null) {
                    loadingFinished = Pair(true, it)
                } else {
                    loadingFinished = Pair(true, null)
                }
            },
            onError = {
                loadingFinished = Pair(true, null)
            }
        )
    }

    if (loadingFinished.first) {
        if (loadingFinished.second != null) {
            VideoView(
                videoUri = videoUri,
                title = title,
                thumb = VideoThumb(loadingFinished.second),
                roundedCorner = roundedCorner,
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
                roundedCorner = roundedCorner,
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
    roundedCorner: Boolean,
    topPaddingForControllers: Dp = Dp.Unspecified,
    waveform: ImmutableList<Int>? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    nostrUriCallback: String? = null,
    onDialog: ((Boolean) -> Unit)? = null,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    accountViewModel: AccountViewModel,
    alwaysShowVideo: Boolean = false
) {
    val defaultToStart by remember(videoUri) { mutableStateOf(DefaultMutedSetting.value) }

    VideoViewInner(
        videoUri = videoUri,
        defaultToStart = defaultToStart,
        title = title,
        thumb = thumb,
        roundedCorner = roundedCorner,
        topPaddingForControllers = topPaddingForControllers,
        waveform = waveform,
        artworkUri = artworkUri,
        authorName = authorName,
        nostrUriCallback = nostrUriCallback,
        alwaysShowVideo = alwaysShowVideo,
        accountViewModel = accountViewModel,
        onControllerVisibilityChanged = onControllerVisibilityChanged,
        onDialog = onDialog
    )
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun VideoViewInner(
    videoUri: String,
    defaultToStart: Boolean = false,
    title: String? = null,
    thumb: VideoThumb? = null,
    roundedCorner: Boolean,
    topPaddingForControllers: Dp = Dp.Unspecified,
    waveform: ImmutableList<Int>? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    nostrUriCallback: String? = null,
    alwaysShowVideo: Boolean = false,
    accountViewModel: AccountViewModel,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    onDialog: ((Boolean) -> Unit)? = null
) {
    val automaticallyStartPlayback = remember {
        mutableStateOf<Boolean>(
            if (alwaysShowVideo) true else accountViewModel.settings.startVideoPlayback.value
        )
    }

    if (!automaticallyStartPlayback.value) {
        ImageUrlWithDownloadButton(url = videoUri, showImage = automaticallyStartPlayback)
    } else {
        VideoPlayerActiveMutex(videoUri) { modifier, activeOnScreen ->
            val mediaItem = remember(videoUri) {
                mutableStateOf(
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
                )
            }

            GetVideoController(
                mediaItem = mediaItem,
                videoUri = videoUri,
                defaultToStart = defaultToStart,
                nostrUriCallback = nostrUriCallback
            ) { controller, keepPlaying ->
                RenderVideoPlayer(
                    controller = controller,
                    thumbData = thumb,
                    roundedCorner = roundedCorner,
                    topPaddingForControllers = topPaddingForControllers,
                    waveform = waveform,
                    keepPlaying = keepPlaying,
                    automaticallyStartPlayback = automaticallyStartPlayback,
                    activeOnScreen = activeOnScreen,
                    modifier = modifier,
                    onControllerVisibilityChanged = onControllerVisibilityChanged,
                    onDialog = onDialog
                )
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
fun GetVideoController(
    mediaItem: MutableState<MediaItem>,
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

    val scope = rememberCoroutineScope()

    // Prepares a VideoPlayer from the foreground service.
    DisposableEffect(key1 = videoUri) {
        // If it is not null, the user might have come back from a playing video, like clicking on
        // the notification of the video player.
        if (controller.value == null) {
            scope.launch(Dispatchers.IO) {
                PlaybackClientController.prepareController(
                    uid,
                    videoUri,
                    nostrUriCallback,
                    context
                ) {
                    scope.launch(Dispatchers.Main) {
                        // REQUIRED TO BE RUN IN THE MAIN THREAD

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

                            controller.value?.setMediaItem(mediaItem.value)
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

                                    it.setMediaItem(mediaItem.value)
                                    it.prepare()
                                }
                            }
                        }
                    }
                }
            }
        } else {
            controller.value?.let {
                scope.launch(Dispatchers.Main) {
                    if (it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_ENDED) {
                        if (it.isPlaying) {
                            // There is a video playing, start this one on mute.
                            it.volume = 0f
                        } else {
                            // There is no other video playing. Use the default mute state to
                            // decide if sound is on or not.
                            it.volume = if (defaultToStart) 0f else 1f
                        }

                        it.setMediaItem(mediaItem.value)
                        it.prepare()
                    }
                }
            }
        }

        onDispose {
            if (!keepPlaying.value) {
                // Stops and releases the media.
                controller.value?.stop()
                controller.value?.release()
                controller.value = null
            }
        }
    }

    // User pauses and resumes the app. What to do with videos?
    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(key1 = lifeCycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // if the controller is null, restarts the controller with a new one
                // if the controller is not null, just continue playing what the controller was playing
                scope.launch(Dispatchers.IO) {
                    if (controller.value == null) {
                        PlaybackClientController.prepareController(
                            uid,
                            videoUri,
                            nostrUriCallback,
                            context
                        ) {
                            scope.launch(Dispatchers.Main) {
                                // REQUIRED TO BE RUN IN THE MAIN THREAD

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
                                            controller.value?.volume =
                                                if (defaultToStart) 0f else 1f
                                        }
                                    }

                                    controller.value?.setMediaItem(mediaItem.value)
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
fun VideoPlayerActiveMutex(videoUri: String, inner: @Composable (Modifier, MutableState<Boolean>) -> Unit) {
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

    val myModifier = remember(videoUri) {
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
    }

    inner(myModifier, active)
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
    roundedCorner: Boolean,
    topPaddingForControllers: Dp = Dp.Unspecified,
    waveform: ImmutableList<Int>? = null,
    keepPlaying: MutableState<Boolean>,
    automaticallyStartPlayback: State<Boolean>,
    activeOnScreen: MutableState<Boolean>,
    modifier: Modifier,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    onDialog: ((Boolean) -> Unit)?
) {
    ControlWhenPlayerIsActive(controller, keepPlaying, automaticallyStartPlayback, activeOnScreen)

    val controllerVisible = remember(controller) {
        mutableStateOf(false)
    }

    val parentVideoPlaybackSize = remember {
        mutableStateOf<IntSize>(IntSize.Zero)
    }

    val videoPlaybackSize = remember {
        mutableStateOf<IntSize>(IntSize.Zero)
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().onSizeChanged {
            parentVideoPlaybackSize.value = it
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier.onSizeChanged {
                videoPlaybackSize.value = it
            }
        ) {
            val borders = MaterialTheme.colorScheme.imageModifier

            val myModifier = remember {
                if (roundedCorner) {
                    modifier.then(
                        borders
                            .defaultMinSize(minHeight = 100.dp)
                            .align(Alignment.Center)
                    )
                } else {
                    modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 100.dp)
                        .align(Alignment.Center)
                }
            }

            val factory = remember(controller) {
                { context: Context ->
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
                            PlayerView.ControllerVisibilityListener { visible ->
                                controllerVisible.value = visible == View.VISIBLE
                                onControllerVisibilityChanged?.let { callback ->
                                    callback(visible == View.VISIBLE)
                                }
                            }
                        )
                    }
                }
            }

            AndroidView(
                modifier = myModifier,
                factory = factory
            )

            waveform?.let {
                Waveform(it, controller, remember { Modifier.align(Alignment.Center) })
            }

            val startingMuteState = remember(controller) {
                controller.volume < 0.001
            }

            val spaceModifier = remember {
                if (topPaddingForControllers.isSpecified && videoPlaybackSize.value.height > 0) {
                    val space = (abs(parentVideoPlaybackSize.value.height - videoPlaybackSize.value.height) / 2).dp
                    if (space > topPaddingForControllers) {
                        Modifier
                    } else {
                        Modifier.padding(top = topPaddingForControllers - space)
                    }
                } else {
                    Modifier
                }
            }

            MuteButton(
                controllerVisible,
                startingMuteState,
                spaceModifier
            ) { mute: Boolean ->
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

            KeepPlayingButton(
                keepPlaying,
                controllerVisible,
                spaceModifier.align(Alignment.TopEnd)
            ) { newKeepPlaying: Boolean ->
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
}

private fun pollCurrentDuration(controller: MediaController) = flow {
    while (controller.currentPosition <= controller.duration) {
        emit(controller.currentPosition / controller.duration.toFloat())
        delay(100)
    }
}.conflate()

@Composable
fun Waveform(
    waveform: ImmutableList<Int>,
    controller: MediaController,
    modifier: Modifier
) {
    val waveformProgress = remember { mutableStateOf(0F) }

    DrawWaveform(waveform, waveformProgress, modifier)

    val restartFlow = remember {
        mutableIntStateOf(0)
    }

    // Keeps the screen on while playing and viewing videos.
    DisposableEffect(key1 = controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // doesn't consider the mutex because the screen can turn off if the video
                // being played in the mutex is not visible.
                if (isPlaying) {
                    restartFlow.value += 1
                }
            }
        }

        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
        }
    }

    LaunchedEffect(key1 = restartFlow.value) {
        pollCurrentDuration(controller).collect() { value ->
            waveformProgress.value = value
        }
    }
}

@Composable
fun DrawWaveform(waveform: ImmutableList<Int>, waveformProgress: MutableState<Float>, modifier: Modifier) {
    AudioWaveformReadOnly(
        modifier = modifier.padding(start = 10.dp, end = 10.dp),
        amplitudes = waveform,
        progress = waveformProgress.value,
        progressBrush = Brush.infiniteLinearGradient(
            colors = listOf(Color(0xff2598cf), Color(0xff652d80)),
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            width = 128F
        ),
        onProgressChange = {
            waveformProgress.value = it
        }
    )
}

@Composable
fun ControlWhenPlayerIsActive(
    controller: Player,
    keepPlaying: MutableState<Boolean>,
    automaticallyStartPlayback: State<Boolean>,
    activeOnScreen: MutableState<Boolean>
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
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // doesn't consider the mutex because the screen can turn off if the video
                // being played in the mutex is not visible.
                view.keepScreenOn = isPlaying
            }
        }

        controller.addListener(listener)
        onDispose {
            view.keepScreenOn = false
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
    modifier: Modifier,
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
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() }
    ) {
        Box(modifier = VolumeBottomIconSize) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.6f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background)
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
        enter = remember { fadeIn() },
        exit = remember { fadeOut() }
    ) {
        Box(modifier = PinBottomIconSize) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.6f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background)
            )

            IconButton(
                onClick = {
                    keepPlaying.value = !keepPlaying.value
                    toggle(keepPlaying.value)
                },
                modifier = Size50Modifier
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
