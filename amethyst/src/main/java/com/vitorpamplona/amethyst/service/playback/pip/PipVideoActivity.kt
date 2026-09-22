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
package com.vitorpamplona.amethyst.service.playback.pip

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.service.playback.background.HoldPromotedPlayback
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.isAudioOnly
import com.vitorpamplona.amethyst.service.playback.coordinator.VideoRequest

class PipVideoActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val videoData by rememberVideoDataFromIntents()
            val playback = Amethyst.instance.videoPlayback
            val promoted by playback.promoted.collectAsStateWithLifecycle()

            videoData?.let { mediaItemData ->
                GetMediaItem(mediaItemData) { mediaItem ->
                    // Normally the player was already promoted by the button that opened this
                    // window, and the handover cost nothing: same player, same buffer, same
                    // decoder. Arriving without one means the promotion did not survive (a stale
                    // PiP intent after the process was reclaimed), so take a player out now.
                    LaunchedEffect(mediaItem) {
                        val pooled =
                            playback.promoted.value
                                ?: playback.promoteDetached(
                                    VideoRequest(mediaItem.src.proxyPort, mediaItem.item.mediaId, mediaItem.src.repeatMode),
                                    applicationContext,
                                    claimFocus = mediaItem.src.isAudioOnly(),
                                )

                        if (pooled.player.currentMediaItem?.mediaId != mediaItem.item.mediaId) {
                            pooled.player.setMediaItem(mediaItem.item)
                            pooled.player.prepare()
                        }

                        // Applied on both paths, not just the fallback: opening the window is a
                        // request to play, and the handed-over player may well arrive paused —
                        // autoplay switched off, AutoReplayLimiter having stopped the loop, or the
                        // user having paused it before tapping the button.
                        pooled.player.volume = if (DEFAULT_MUTED_SETTING.value) 0f else 1f
                        pooled.player.playWhenReady = true
                    }

                    promoted?.let { pooled ->
                        val controllerState =
                            remember(pooled) {
                                MediaControllerState(controller = pooled.player, pooled = pooled)
                            }

                        // Leaving this window gives the surface up, which stops PlaybackService and
                        // takes the notification down with it.
                        HoldPromotedPlayback(pooled.player)
                        RegisterControllerReceiver(controllerState)
                        WatchControllerForActions(mediaItemData, controllerState)
                        RenderPipVideo(controllerState, mediaItemData, mediaItemData.waveformData)
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            // User dismissed PiP (swiped away or expanded).
            finishAndRemoveTask()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            // Only finish if we're not in PiP mode.
            // When the screen locks while in PiP, we stay alive
            // so the PlaybackService can continue audio playback.
            finishAndRemoveTask()
        }
    }

    companion object {
        fun callIn(
            videoData: MediaItemData,
            videoBounds: Rect?,
            context: Context,
        ) {
            val options =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityOptions.makeLaunchIntoPip(
                        makePipParams(videoData.aspectRatio, videoBounds),
                    )
                } else {
                    ActivityOptions.makeBasic()
                }

            context.startActivity(
                Intent(context.applicationContext, PipVideoActivity::class.java).apply {
                    putExtras(IntentExtras.createBundle(videoData, videoBounds))
                },
                options.toBundle(),
            )
        }
    }
}
