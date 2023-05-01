package com.vitorpamplona.amethyst.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
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
            setMediaSource(
                ProgressiveMediaSource.Factory(VideoCache.get()).createMediaSource(
                    media
                )
            )
            prepare()
        }
    }

    DisposableEffect(
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = {
                StyledPlayerView(context).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    onDialog?.let { innerOnDialog ->
                        setFullscreenButtonClickListener {
                            exoPlayer.pause()
                            innerOnDialog(it)
                        }
                    }
                }
            }
        )
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

@Composable
fun VideoView(videoBytes: ByteArray, description: String? = null, onDialog: ((Boolean) -> Unit)? = null) {
}
