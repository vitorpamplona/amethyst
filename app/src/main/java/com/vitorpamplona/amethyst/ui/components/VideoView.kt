package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.imageLoader
import coil.request.ImageRequest
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.vitorpamplona.amethyst.VideoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

public var DefaultMutedSetting = mutableStateOf(true)

@Composable
fun VideoView(localFile: File?, description: String? = null, onDialog: ((Boolean) -> Unit)? = null) {
    if (localFile != null) {
        val video = remember(localFile) { localFile.toUri() }

        VideoView(video, description, null, onDialog)
    }
}

@Composable
fun VideoView(videoUri: String, description: String? = null, onDialog: ((Boolean) -> Unit)? = null) {
    val video = remember(videoUri) { Uri.parse(videoUri) }

    VideoView(video, description, null, onDialog)
}

@Composable
fun LoadThumbAndThenVideoView(videoUri: String, description: String? = null, thumbUri: String, onDialog: ((Boolean) -> Unit)? = null) {
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
            val video = remember(videoUri) { Uri.parse(videoUri) }

            VideoView(video, description, loadingFinished.second, onDialog)
        } else {
            VideoView(videoUri, description, onDialog)
        }
    }
}

@Composable
fun VideoView(videoUri: Uri, description: String? = null, thumb: Drawable? = null, onDialog: ((Boolean) -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    val defaultVolume = remember { if (DefaultMutedSetting.value) 0f else 1f }

    LaunchedEffect(key1 = videoUri) {
        if (exoPlayer == null) {
            launch(Dispatchers.Default) {
                exoPlayer = ExoPlayer.Builder(context).build()
            }
        }
    }

    exoPlayer?.let {
        val media = remember { MediaItem.Builder().setUri(videoUri).build() }

        it.apply {
            repeatMode = Player.REPEAT_MODE_ALL
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            volume = defaultVolume
            if (videoUri.scheme?.startsWith("file") == true) {
                setMediaItem(media)
            } else {
                setMediaSource(
                    ProgressiveMediaSource.Factory(VideoCache.get()).createMediaSource(
                        media
                    )
                )
            }
            prepare()
        }

        RenderVideoPlayer(it, context, thumb, onDialog)
    }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer?.pause()
                }
                else -> {}
            }
        }
        val lifecycle = lifecycleOwner.value.lifecycle
        lifecycle.addObserver(observer)

        onDispose {
            exoPlayer?.release()
            lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun RenderVideoPlayer(
    exoPlayer: ExoPlayer,
    context: Context,
    thumb: Drawable?,
    onDialog: ((Boolean) -> Unit)?
) {
    BoxWithConstraints() {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 70.dp)
                .align(Alignment.Center)
                .onVisibilityChanges { visible ->
                    if (visible && !exoPlayer.isPlaying) {
                        exoPlayer.play()
                    } else if (!visible && exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    }
                },
            factory = {
                StyledPlayerView(context).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    controllerAutoShow = false
                    thumb?.let { defaultArtwork = thumb }
                    hideController()
                    resizeMode =
                        if (maxHeight.isFinite) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    onDialog?.let { innerOnDialog ->
                        setFullscreenButtonClickListener {
                            exoPlayer.pause()
                            innerOnDialog(it)
                        }
                    }
                }
            }
        )

        MuteButton() { mute: Boolean ->
            DefaultMutedSetting.value = mute

            exoPlayer.volume = if (mute) 0f else 1f
        }
    }
}

fun Modifier.onVisibilityChanges(onVisibilityChanges: (Boolean) -> Unit): Modifier = composed {
    val view = LocalView.current
    var isVisible: Boolean? by remember { mutableStateOf(null) }

    onGloballyPositioned { coordinates ->
        val newIsVisible = coordinates.isCompletelyVisible(view)
        if (isVisible != newIsVisible) {
            isVisible = newIsVisible
            onVisibilityChanges(isVisible == true)
        }
    }
}

fun LayoutCoordinates.isCompletelyVisible(view: View): Boolean {
    if (!isAttached) return false
    // Window relative bounds of our compose root view that are visible on the screen
    val globalRootRect = android.graphics.Rect()
    if (!view.getGlobalVisibleRect(globalRootRect)) {
        // we aren't visible at all.
        return false
    }
    val bounds = boundsInWindow()
    // Make sure we are completely in bounds.
    return bounds.top >= globalRootRect.top &&
        bounds.left >= globalRootRect.left &&
        bounds.right <= globalRootRect.right &&
        bounds.bottom <= globalRootRect.bottom
}

@Composable
private fun MuteButton(toggle: (Boolean) -> Unit) {
    Box(
        remember {
            Modifier
                .width(70.dp)
                .height(70.dp)
                .padding(10.dp)
        }
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.6f)
                .align(Alignment.Center)
                .background(MaterialTheme.colors.background)
        )

        val mutedInstance = remember { mutableStateOf(DefaultMutedSetting.value) }

        IconButton(
            onClick = {
                mutedInstance.value = !mutedInstance.value
                toggle(mutedInstance.value)
            },
            modifier = Modifier.size(50.dp)
        ) {
            if (mutedInstance.value) {
                Icon(
                    imageVector = Icons.Default.VolumeOff,
                    "Hash Verified",
                    tint = MaterialTheme.colors.onBackground,
                    modifier = Modifier.size(30.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    "Hash Verified",
                    tint = MaterialTheme.colors.onBackground,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}
