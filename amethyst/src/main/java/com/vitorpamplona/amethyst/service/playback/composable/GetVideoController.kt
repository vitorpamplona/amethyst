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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.service.PlaybackServiceClient
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun GetVideoController(
    mediaItem: LoadedMediaItem,
    muted: Boolean = false,
    inner: @Composable (mediaControllerState: MediaControllerState) -> Unit,
) {
    val context = LocalContext.current

    val onlyOnePreparing = remember { AtomicBoolean() }

    val controllerId = remember(mediaItem.src.videoUri) { MediaControllerState() }

    controllerId.composed = true

    val scope = rememberCoroutineScope()

    // Prepares a VideoPlayer from the foreground service.
    //
    // TODO: Review this code because a new Disposable Effect can run
    // before the onDispose of the previous composable and the onDispose
    // sometimes affects the new variables, not the old ones.
    DisposableEffect(key1 = mediaItem.src.videoUri) {
        // If it is not null, the user might have come back from a playing video, like clicking on
        // the notification of the video player.
        if (controllerId.needsController()) {
            // If there is a connection, don't wait.
            if (!onlyOnePreparing.getAndSet(true)) {
                scope.launch(Dispatchers.IO) {
                    Log.d("PlaybackService", "Preparing Video ${controllerId.id} ${mediaItem.src.videoUri}")
                    PlaybackServiceClient.prepareController(
                        mediaControllerState = controllerId,
                        videoUri = mediaItem.src.videoUri,
                        proxyPort = mediaItem.src.proxyPort,
                        keepPlaying = mediaItem.src.keepPlaying,
                        context = context,
                    ) { controllerId ->
                        scope.launch(Dispatchers.Main) {
                            // checks if the player is still active after requesting to load
                            if (!controllerId.isActive()) {
                                onlyOnePreparing.getAndSet(false)
                                PlaybackServiceClient.removeController(controllerId)
                                return@launch
                            }

                            // REQUIRED TO BE RUN IN THE MAIN THREAD
                            if (!controllerId.isPlaying()) {
                                if (BackgroundMedia.isPlaying()) {
                                    // There is a video playing, start this one on mute.
                                    controllerId.controller?.volume = 0f
                                } else {
                                    // There is no other video playing. Use the default mute state to
                                    // decide if sound is on or not.
                                    controllerId.controller?.volume = if (muted) 0f else 1f
                                }
                            }

                            controllerId.controller?.setMediaItem(mediaItem.item)
                            controllerId.controller?.prepare()

                            // checks if the player is still active after requesting to load
                            if (!controllerId.isActive()) {
                                PlaybackServiceClient.removeController(controllerId)
                                return@launch
                            }

                            controllerId.readyToDisplay.value = true

                            onlyOnePreparing.getAndSet(false)

                            // checks if the player is still active after requesting to load
                            if (!controllerId.isActive()) {
                                PlaybackServiceClient.removeController(controllerId)
                                return@launch
                            }
                        }
                    }
                }
            }
        } else {
            // has been loaded. prepare to play. This happens when the background video switches screens.
            controllerId.controller?.let {
                scope.launch {
                    // checks if the player is still active after requesting to load
                    if (!controllerId.isActive()) {
                        PlaybackServiceClient.removeController(controllerId)
                        return@launch
                    }

                    if (it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_ENDED) {
                        Log.d("PlaybackService", "Preparing Existing Video ${mediaItem.src.videoUri} ")

                        if (it.isPlaying) {
                            // There is a video playing, start this one on mute.
                            it.volume = 0f
                        } else {
                            // There is no other video playing. Use the default mute state to
                            // decide if sound is on or not.
                            it.volume = if (muted) 0f else 1f
                        }

                        if (mediaItem.item != it.currentMediaItem) {
                            it.setMediaItem(mediaItem.item)
                        }

                        it.prepare()

                        // checks if the player is still active after requesting to load
                        if (!controllerId.isActive()) {
                            PlaybackServiceClient.removeController(controllerId)
                            return@launch
                        }
                    }
                }
            }
        }

        onDispose {
            controllerId.composed = false
            if (!controllerId.pictureInPictureActive.value) {
                PlaybackServiceClient.removeController(controllerId)
            }
        }
    }

    // User pauses and resumes the app. What to do with videos?
    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(key1 = lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    controllerId.composed = true
                    // if the controller is null, restarts the controller with a new one
                    // if the controller is not null, just continue playing what the controller was playing
                    if (controllerId.needsController()) {
                        if (!onlyOnePreparing.getAndSet(true)) {
                            scope.launch(Dispatchers.IO) {
                                Log.d("PlaybackService", "Preparing Video from Resume ${controllerId.id} ${mediaItem.src.videoUri} ")
                                PlaybackServiceClient.prepareController(
                                    mediaControllerState = controllerId,
                                    videoUri = mediaItem.src.videoUri,
                                    proxyPort = mediaItem.src.proxyPort,
                                    keepPlaying = mediaItem.src.keepPlaying,
                                    context = context,
                                ) { controllerId ->
                                    scope.launch(Dispatchers.Main) {
                                        // checks if the player is still active after requesting to load
                                        if (!controllerId.isActive()) {
                                            onlyOnePreparing.getAndSet(false)
                                            PlaybackServiceClient.removeController(controllerId)
                                            return@launch
                                        }

                                        // REQUIRED TO BE RUN IN THE MAIN THREAD
                                        // checks again to make sure no other thread has created a controller.
                                        if (!controllerId.isPlaying()) {
                                            if (BackgroundMedia.isPlaying()) {
                                                // There is a video playing, start this one on mute.
                                                controllerId.controller?.volume = 0f
                                            } else {
                                                // There is no other video playing. Use the default mute state to
                                                // decide if sound is on or not.
                                                controllerId.controller?.volume = if (muted) 0f else 1f
                                            }
                                        }

                                        controllerId.controller?.setMediaItem(mediaItem.item)
                                        controllerId.controller?.prepare()

                                        // checks if the player is still active after requesting to load
                                        if (!controllerId.isActive()) {
                                            onlyOnePreparing.getAndSet(false)
                                            PlaybackServiceClient.removeController(controllerId)
                                            return@launch
                                        }

                                        controllerId.readyToDisplay.value = true

                                        onlyOnePreparing.getAndSet(false)

                                        // checks if the player is still active after requesting to load
                                        if (!controllerId.isActive()) {
                                            PlaybackServiceClient.removeController(controllerId)
                                            return@launch
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    controllerId.composed = false
                    PlaybackServiceClient.removeController(controllerId)
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    if (controllerId.readyToDisplay.value && controllerId.active.value) {
        controllerId.controller?.let {
            inner(controllerId)
        }
    }
}
