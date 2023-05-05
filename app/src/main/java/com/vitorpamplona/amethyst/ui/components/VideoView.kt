package com.vitorpamplona.amethyst.ui.components

import android.net.Uri
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
import androidx.compose.runtime.MutableState
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
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.vitorpamplona.amethyst.VideoCache
import java.io.File

public var muted = mutableStateOf(true)

@Composable
fun VideoView(localFile: File, description: String? = null, onDialog: ((Boolean) -> Unit)? = null) {
    VideoView(localFile.toUri(), description, onDialog)
}

@Composable
fun VideoView(videoUri: String, description: String? = null, onDialog: ((Boolean) -> Unit)? = null) {
    VideoView(Uri.parse(videoUri), description, onDialog)
}

@Composable
fun VideoView(videoUri: Uri, description: String? = null, onDialog: ((Boolean) -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    val exoPlayer = remember(videoUri) {
        val mediaBuilder = MediaItem.Builder().setUri(videoUri)

        description?.let {
            mediaBuilder.setMediaMetadata(
                MediaMetadata.Builder().setDisplayTitle(it).build()
            )
        }

        val media = mediaBuilder.build()

        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            volume = 0f
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
    }

    LaunchedEffect(key1 = muted.value) {
        exoPlayer.volume = if (muted.value) 0f else 1f
    }

    DisposableEffect(
        BoxWithConstraints() {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 70.dp)
                    .align(Alignment.Center)
                    .onVisibilityChanges { visible ->
                        if (visible) {
                            exoPlayer.play()
                        } else {
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
                        hideController()
                        resizeMode = if (maxHeight.isFinite) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        onDialog?.let { innerOnDialog ->
                            setFullscreenButtonClickListener {
                                exoPlayer.pause()
                                innerOnDialog(it)
                            }
                        }
                    }
                }
            )

            MuteButton(muted, Modifier) {
                muted.value = !muted.value
            }
        }

    ) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                else -> {}
            }
        }
        val lifecycle = lifecycleOwner.value.lifecycle
        lifecycle.addObserver(observer)

        onDispose {
            exoPlayer.release()
            lifecycle.removeObserver(observer)
        }
    }
}

fun Modifier.onVisibilityChanges(onVisibilityChanges: (Boolean) -> Unit): Modifier = composed {
    val view = LocalView.current
    var isVisible: Boolean? by remember { mutableStateOf(null) }

    LaunchedEffect(isVisible) {
        onVisibilityChanges(isVisible == true)
    }

    onGloballyPositioned { coordinates ->
        isVisible = coordinates.isCompletelyVisible(view)
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
private fun MuteButton(muted: MutableState<Boolean>, modifier: Modifier, toggle: () -> Unit) {
    Box(
        modifier
            .width(70.dp)
            .height(70.dp)
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.6f)
                .align(Alignment.Center)
                .background(MaterialTheme.colors.background)
        )

        if (muted.value) {
            IconButton(
                onClick = toggle,
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeOff,
                    "Hash Verified",
                    tint = MaterialTheme.colors.onBackground,
                    modifier = Modifier.size(30.dp)
                )
            }
        } else {
            IconButton(
                onClick = toggle,
                modifier = Modifier.size(50.dp)
            ) {
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
