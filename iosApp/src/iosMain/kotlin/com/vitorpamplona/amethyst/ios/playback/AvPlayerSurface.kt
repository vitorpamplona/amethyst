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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView

/** A view whose [playerLayer] always covers it. */
@OptIn(ExperimentalForeignApi::class)
class AvPlayerView : UIView(frame = CGRectZero.readValue()) {
    val playerLayer: AVPlayerLayer = AVPlayerLayer()

    init {
        backgroundColor = UIColor.clearColor
        userInteractionEnabled = false
        // A cropped picture (aspect fill) must not spill past the view.
        clipsToBounds = true
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // Without disabling actions the layer animates to each new size, trailing the view.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.frame = bounds
        CATransaction.commit()
    }
}

internal fun ContentScale.videoGravity() =
    when (this) {
        ContentScale.Crop -> AVLayerVideoGravityResizeAspectFill
        ContentScale.FillBounds -> AVLayerVideoGravityResize
        else -> AVLayerVideoGravityResizeAspect
    }

/**
 * Draws [player]'s picture. [onView] hands the view out (picture in picture needs its layer) and
 * gets null when it leaves.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun AvPlayerSurface(
    player: AVPlayer,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    onView: (AvPlayerView?) -> Unit = {},
) {
    UIKitView(
        factory = { AvPlayerView().also(onView) },
        modifier = modifier,
        update = { view ->
            if (view.playerLayer.player != player) view.playerLayer.player = player
            view.playerLayer.videoGravity = contentScale.videoGravity()
        },
        onRelease = { view ->
            view.playerLayer.player = null
            onView(null)
        },
        // The picture never takes touches: the Compose controls above it do.
        properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false),
    )
}
