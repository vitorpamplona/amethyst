package com.vitorpamplona.amethyst.ui.components

import android.media.browse.MediaBrowser
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView

@Composable
fun VideoView(videoUri: String) {
  val context = LocalContext.current

  val exoPlayer = ExoPlayer.Builder(LocalContext.current).build().apply {
    repeatMode = Player.REPEAT_MODE_ALL
    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
    setMediaItem(MediaItem.fromUri(videoUri))
    prepare()
  }

  DisposableEffect(exoPlayer) {
    onDispose {
      exoPlayer.release()
    }
  }

  AndroidView(
    modifier = Modifier
      .fillMaxWidth(),
    factory = {
      StyledPlayerView(context).apply {
        player = exoPlayer
        layoutParams = FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
      }
    })
}