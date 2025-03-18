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
package com.vitorpamplona.amethyst.service.playback.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object PlaybackServiceClient {
    val executorService: ExecutorService = Executors.newCachedThreadPool()

    fun removeController(mediaControllerState: MediaControllerState) {
        mediaControllerState.active.value = false
        mediaControllerState.readyToDisplay.value = false

        val myController = mediaControllerState.controller.value
        // release when can
        if (myController != null) {
            mediaControllerState.controller.value = null
            GlobalScope.launch(Dispatchers.Main) {
                // myController.pause()
                // myController.stop()
                myController.release()
                Log.d("PlaybackService", "Releasing Video $mediaControllerState")
            }
        }
    }

    fun prepareController(
        mediaControllerState: MediaControllerState,
        videoUri: String,
        proxyPort: Int? = 0,
        context: Context,
        onReady: (MediaControllerState) -> Unit,
    ) {
        mediaControllerState.active.value = true

        try {
            val bundle =
                Bundle().apply {
                    // link the id with the client's id to make sure it can return the
                    // same session on background media.
                    putString("id", mediaControllerState.id)
                    proxyPort?.let {
                        putInt("proxyPort", it)
                    }
                }

            val session = SessionToken(context, ComponentName(context, PlaybackService::class.java))

            val controllerFuture =
                MediaController
                    .Builder(context, session)
                    .setConnectionHints(bundle)
                    .buildAsync()

            Log.d("PlaybackService", "Preparing Controller ${mediaControllerState.id} $videoUri")

            controllerFuture.addListener(
                {
                    try {
                        val controller = controllerFuture.get()
                        mediaControllerState.controller.value = controller

                        // checks if the player is still active before engaging further
                        if (mediaControllerState.isActive()) {
                            onReady(mediaControllerState)
                        } else {
                            removeController(mediaControllerState)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("Playback Client", "Failed to load Playback Client for $videoUri", e)
                    }
                },
                executorService,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Playback Client", "Failed to load Playback Client for $videoUri", e)
        }
    }
}
