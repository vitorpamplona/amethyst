package com.vitorpamplona.amethyst

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

object PlaybackClientController {
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

            val sessionTokenLocal = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controllerFuture = MediaController
                .Builder(context, sessionTokenLocal)
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
