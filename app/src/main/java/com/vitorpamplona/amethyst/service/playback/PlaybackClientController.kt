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
package com.vitorpamplona.amethyst.service.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import java.util.concurrent.Executors

object PlaybackClientController {
    var executorService = Executors.newCachedThreadPool()
    val cache = LruCache<Int, SessionToken>(1)

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun prepareController(
        controllerID: String,
        videoUri: String,
        callbackUri: String?,
        context: Context,
        onReady: (MediaController) -> Unit,
    ) {
        try {
            // creating a bundle object
            // creating a bundle object
            val bundle = Bundle()
            bundle.putString("id", controllerID)
            bundle.putString("uri", videoUri)
            bundle.putString("callbackUri", callbackUri)

            var session = cache.get(context.hashCode())
            if (session == null) {
                session = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                cache.put(context.hashCode(), session)
            }

            val controllerFuture =
                MediaController.Builder(context, session).setConnectionHints(bundle).buildAsync()

            controllerFuture.addListener(
                {
                    try {
                        onReady(controllerFuture.get())
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
