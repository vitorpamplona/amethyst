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
package com.vitorpamplona.amethyst.ios.playback

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.AVKit.AVRoutePickerView
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIColor
import platform.darwin.NSObject

/**
 * Picture in picture for one player view. While the floating window is up its player is the
 * background one: [IosPlayerPool] keeps it playing when the app leaves the screen.
 */
@OptIn(ExperimentalForeignApi::class)
object PictureInPicture {
    val isSupported: Boolean get() = AVPictureInPictureController.isPictureInPictureSupported()

    private var controller: AVPictureInPictureController? = null
    private var controllerView: AvPlayerView? = null
    private val delegate = Delegate()

    /** The player in the floating window, if any. */
    var activePlayer: AvAmethystPlayer? = null
        private set

    /**
     * Gets [view] ready, so the system can also float it on its own when the app leaves the screen
     * while it plays with sound ([autoStart]).
     */
    fun attach(
        view: AvPlayerView,
        autoStart: Boolean,
    ) {
        if (!isSupported) return
        if (controllerView != view) {
            controller = AVPictureInPictureController(playerLayer = view.playerLayer).also { it.delegate = delegate }
            controllerView = view
        }
        controller?.canStartPictureInPictureAutomaticallyFromInline = autoStart
    }

    fun detach(view: AvPlayerView) {
        if (controllerView == view && controller?.pictureInPictureActive != true) {
            controller = null
            controllerView = null
        }
    }

    fun start(player: AvAmethystPlayer) {
        val current = controller ?: return
        // The floating window needs the playback session, even for a muted video.
        AudioSessionMode.playback()
        activePlayer = player
        if (!player.isPlaying) player.play()
        current.startPictureInPicture()
    }

    private class Delegate :
        NSObject(),
        AVPictureInPictureControllerDelegateProtocol {
        override fun pictureInPictureControllerDidStopPictureInPicture(pictureInPictureController: AVPictureInPictureController) {
            activePlayer = null
        }

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            failedToStartPictureInPictureWithError: platform.Foundation.NSError,
        ) {
            Log.w("PictureInPicture") { "Could not start: ${failedToStartPictureInPictureWithError.localizedDescription}" }
            activePlayer = null
        }
    }
}

/** AirPlay: Apple's route picker, which lists the speakers and TVs the player can go to. */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun AirPlayButton(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    UIKitView(
        factory = {
            AVRoutePickerView(frame = CGRectZero.readValue()).apply {
                backgroundColor = UIColor.clearColor
                prioritizesVideoDevices = true
            }
        },
        modifier = modifier,
        update = { view ->
            val argb = tint.toArgb()
            view.tintColor =
                UIColor.colorWithRed(
                    red = ((argb shr 16) and 0xFF) / 255.0,
                    green = ((argb shr 8) and 0xFF) / 255.0,
                    blue = (argb and 0xFF) / 255.0,
                    alpha = ((argb ushr 24) and 0xFF) / 255.0,
                )
        },
        properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
    )
}
