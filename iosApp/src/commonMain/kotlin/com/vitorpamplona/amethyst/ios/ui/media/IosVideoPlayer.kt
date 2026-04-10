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
package com.vitorpamplona.amethyst.ios.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.volume
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL
import platform.UIKit.UIColor

/**
 * In-app video player using AVPlayer via UIKit interop.
 * Replaces the VideoThumbnail placeholder with actual video playback.
 *
 * Uses AVPlayerViewController for native controls (play/pause, scrub,
 * fullscreen, AirPlay, PiP on supported devices).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun IosVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val playerUrl = remember(url) { NSURL.URLWithString(url) }

    var isPlaying by remember { mutableStateOf(autoPlay) }

    if (playerUrl == null) {
        // Fallback to thumbnail for invalid URLs
        VideoThumbnail(url = url, modifier = modifier)
        return
    }

    val player =
        remember(url) {
            val item = AVPlayerItem(uRL = playerUrl)
            val avPlayer = AVPlayer(playerItem = item)
            avPlayer.volume = 0f // muted by default in feed
            avPlayer
        }

    DisposableEffect(player) {
        onDispose {
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
        }
    }

    LaunchedEffect(autoPlay, player) {
        if (autoPlay) {
            player.play()
            isPlaying = true
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
    ) {
        // AVPlayerViewController provides native playback controls
        UIKitView(
            factory = {
                val controller = AVPlayerViewController()
                controller.player = player
                controller.showsPlaybackControls = true
                controller.view.setBackgroundColor(UIColor.blackColor)
                controller.view
            },
            modifier = Modifier.fillMaxSize(),
            update = { _ ->
                // Player is managed via remember state
            },
        )

        // Tap-to-play overlay when not yet playing
        if (!isPlaying) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable {
                            player.play()
                            isPlaying = true
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }

        // Domain label at bottom
        Text(
            text = MediaUtils.extractDomain(url),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
        )
    }
}
