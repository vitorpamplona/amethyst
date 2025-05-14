/**
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
package com.vitorpamplona.amethyst.service.playback.pip

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.ui.components.getActivity

@Composable
fun PipVideo() {
    val activity = LocalContext.current.getActivity()
    var videoData by remember(activity) {
        mutableStateOf(IntentExtras.loadBundle(activity.intent.extras))
    }

    DisposableEffect(activity) {
        val consumer =
            Consumer<Intent> { intent ->
                videoData = IntentExtras.loadBundle(intent.extras)
                val bounds = IntentExtras.loadBounds(intent.extras)
                val ratio = videoData?.aspectRatio ?: videoData?.videoUri?.let { MediaAspectRatioCache.get(it) }

                activity.enterPipMode(ratio, bounds)
            }

        activity.addOnNewIntentListener(consumer)
        onDispose { activity.removeOnNewIntentListener(consumer) }
    }

    videoData?.let {
        GetMediaItem(it) { mediaItem ->
            GetVideoController(mediaItem, false) { controller ->
                PipVideo(controller)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PipVideo(controller: MediaControllerState) {
    DisposableEffect(controller) {
        BackgroundMedia.switchKeepPlaying(controller)
        onDispose {
            BackgroundMedia.clearBackground()
        }
    }

    val ratio =
        controller.currrentMedia()?.let {
            MediaAspectRatioCache.get(it)
        }

    val modifier =
        if (ratio != null) {
            Modifier.aspectRatio(ratio)
        } else {
            Modifier
        }

    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier,
            factory = { context: Context ->
                PlayerView(context).apply {
                    clipToOutline = true
                    player = controller.controller
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)

                    controllerAutoShow = false
                    useController = false

                    hideController()

                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

                    controller.controller?.playWhenReady = true
                }
            },
        )
    }
}
