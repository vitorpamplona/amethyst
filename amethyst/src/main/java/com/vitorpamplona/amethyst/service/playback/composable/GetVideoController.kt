/**
 * Copyright (c) 2024 Vitor Pamplona
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

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.service.playback.service.PlaybackServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun GetVideoController(
    mediaItem: State<MediaItem>,
    videoUri: String,
    proxyPort: Int?,
    muted: Boolean = false,
    inner: @Composable (mediaControllerState: MediaControllerState) -> Unit,
) {
    val context = LocalContext.current

    val onlyOnePreparing = AtomicBoolean()

    val controllerId = remember(videoUri) { BackgroundMedia.backgroundOrNewController(videoUri) }

    controllerId.composed.value = true

    val scope = rememberCoroutineScope()

    // Prepares a VideoPlayer from the foreground service.
    DisposableEffect(key1 = videoUri) {
        println("AABBCC On DisposableEffect: ${controllerId.id} ${controllerId.controller.value}")
        // If it is not null, the user might have come back from a playing video, like clicking on
        // the notification of the video player.
        if (controllerId.needsController()) {
            // If there is a connection, don't wait.
            if (!onlyOnePreparing.getAndSet(true)) {
                scope.launch {
                    Log.d("PlaybackService", "Preparing Video ${controllerId.id} $videoUri")
                    PlaybackServiceClient.prepareController(
                        controllerId,
                        videoUri,
                        proxyPort,
                        context,
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
                                    controllerId.controller.value?.volume = 0f
                                } else {
                                    // There is no other video playing. Use the default mute state to
                                    // decide if sound is on or not.
                                    controllerId.controller.value?.volume = if (muted) 0f else 1f
                                }
                            }

                            controllerId.controller.value?.setMediaItem(mediaItem.value)
                            controllerId.controller.value?.prepare()

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
            controllerId.controller.value?.let {
                scope.launch {
                    // checks if the player is still active after requesting to load
                    if (!controllerId.isActive()) {
                        PlaybackServiceClient.removeController(controllerId)
                        return@launch
                    }

                    if (it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_ENDED) {
                        Log.d("PlaybackService", "Preparing Existing Video $videoUri ")

                        if (it.isPlaying) {
                            // There is a video playing, start this one on mute.
                            it.volume = 0f
                        } else {
                            // There is no other video playing. Use the default mute state to
                            // decide if sound is on or not.
                            it.volume = if (muted) 0f else 1f
                        }

                        if (mediaItem.value != it.currentMediaItem) {
                            it.setMediaItem(mediaItem.value)
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
            println("AABBCC On Dispose: ${controllerId.id} ${controllerId.controller.value}")
            controllerId.composed.value = false
            if (!controllerId.keepPlaying.value) {
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
                    controllerId.composed.value = true
                    println("AABBCC On Resume: ${controllerId.id} ${controllerId.controller.value}")
                    // if the controller is null, restarts the controller with a new one
                    // if the controller is not null, just continue playing what the controller was playing
                    if (controllerId.controller.value == null) {
                        if (!onlyOnePreparing.getAndSet(true)) {
                            scope.launch(Dispatchers.Main) {
                                Log.d("PlaybackService", "AABBCC Preparing Video from Resume ${controllerId.id} $videoUri ")
                                PlaybackServiceClient.prepareController(
                                    controllerId,
                                    videoUri,
                                    proxyPort,
                                    context,
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
                                                controllerId.controller.value?.volume = 0f
                                            } else {
                                                // There is no other video playing. Use the default mute state to
                                                // decide if sound is on or not.
                                                controllerId.controller.value?.volume = if (muted) 0f else 1f
                                            }
                                        }

                                        controllerId.controller.value?.setMediaItem(mediaItem.value)
                                        controllerId.controller.value?.prepare()

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
                    controllerId.composed.value = false
                    println("AABBCC On Pause: ${controllerId.keepPlaying.value} ${controllerId.id}")
                    if (!controllerId.keepPlaying.value) {
                        // Stops and releases the media.
                        PlaybackServiceClient.removeController(controllerId)
                    }
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    if (controllerId.readyToDisplay.value && controllerId.active.value) {
        controllerId.controller.value?.let {
            inner(controllerId)
        }
    }
}
