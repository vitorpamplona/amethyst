package com.vitorpamplona.amethyst.service.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

object PlaybackClientController {
    val cache = LruCache<Int, SessionToken>(1)

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun prepareController(
        controllerID: String,
        videoUri: String,
        callbackUri: String?,
        context: Context,
        onReady: (MediaController) -> Unit
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

            val controllerFuture = MediaController
                .Builder(context, session)
                .setConnectionHints(bundle)
                .buildAsync()

            controllerFuture.addListener(
                {
                    try {
                        onReady(controllerFuture.get())
                    } catch (e: Exception) {
                        Log.e("Playback Client", "Failed to load Playback Client for $videoUri", e)
                    }
                },
                MoreExecutors.directExecutor()
            )
        } catch (e: Exception) {
            Log.e("Playback Client", "Failed to load Playback Client for $videoUri", e)
        }
    }
}
