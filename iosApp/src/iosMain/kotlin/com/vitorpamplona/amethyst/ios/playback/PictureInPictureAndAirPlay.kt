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

    // The view on screen for each player: picture in picture grows out of its layer.
    private val views = mutableMapOf<AvAmethystPlayer, AvPlayerView>()
    private var controller: AVPictureInPictureController? = null
    private val delegate = Delegate()

    /** The player in the floating window, if any. */
    var activePlayer: AvAmethystPlayer? = null
        private set

    /** Called by the surface while it shows [player]. */
    fun register(
        player: AvAmethystPlayer,
        view: AvPlayerView,
    ) {
        views[player] = view
    }

    fun unregister(
        player: AvAmethystPlayer,
        view: AvPlayerView,
    ) {
        if (views[player] == view) views.remove(player)
    }

    /** Floats [player] if it is on screen now; false when there is no view to grow out of. */
    fun start(player: AvAmethystPlayer): Boolean {
        if (!isSupported) return false
        val view = views[player] ?: return false
        // The floating window needs the playback session, even for a muted video.
        AudioSessionMode.playback()
        val created = AVPictureInPictureController(playerLayer = view.playerLayer)
        created.delegate = delegate
        controller = created
        activePlayer = player
        if (!player.isPlaying) player.play()
        created.startPictureInPicture()
        return true
    }

    private fun stopped() {
        val player = activePlayer
        activePlayer = null
        controller = null
        player?.let { onStopped?.invoke(it) }
    }

    /** Told which player's floating window closed (the platform drops it as the background media). */
    var onStopped: ((AvAmethystPlayer) -> Unit)? = null

    private class Delegate :
        NSObject(),
        AVPictureInPictureControllerDelegateProtocol {
        override fun pictureInPictureControllerDidStopPictureInPicture(pictureInPictureController: AVPictureInPictureController) = stopped()

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            failedToStartPictureInPictureWithError: platform.Foundation.NSError,
        ) {
            Log.w("PictureInPicture") { "Could not start: ${failedToStartPictureInPictureWithError.localizedDescription}" }
            stopped()
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
