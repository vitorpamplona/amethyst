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
package com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip55AndroidSigner.api.IResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResult
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.tryAndWait
import kotlin.collections.forEach
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * This class manages the lifecycle of foreground signing requests in a NIP-55 compliant Android signer flow.
 *
 * - It tracks pending signing requests using a unique ID and allows for awaiting their results via coroutines.
 * - Provides a way to launch foreground Intents (to request user approval) and wait for the response.
 * - Handles timeouts on user approval using [tryAndWait].
 * - Collects results via [newResponse] when the user responds to the foreground request.
 *
 * Main components:
 *
 * - `awaitingRequests`: LRU cache mapping request IDs to continuations for async response handling.
 * - `appLauncher`: Function reference to launch foreground Intents (typically provided by an Activity).
 * - `launchAndWait`: Suspend function to send an Intent, wait for an answer, and parse the result.
 * - `newResponse`: Handles incoming results from the foreground activity using a unique ID.
 *
 * Key usage flows:
 *
 * - Request initiated -> store continuation by ID -> launch intent -> wait
 * - User responds -> resume continuation -> remove ID -> return parsed result
 *
 * The class also cleans up pending requests on timeout or cancellation.
 */
class IntentRequestManager(
    val foregroundApprovalTimeout: Long = 30000,
) {
    val activityNotFoundIntent = Intent()

    // LRU cache to store pending requests and their continuations.
    private val awaitingRequests = LruCache<String, Continuation<IntentResult>>(2000)

    // Function to launch an Intent in the foreground.
    private var appLauncher: ((Intent) -> Unit)? = null

    /** Call this function when the launcher becomes available on activity, fragment or compose */
    fun registerForegroundLauncher(launcher: ((Intent) -> Unit)) {
        this.appLauncher = launcher
    }

    /** Call this function when the activity is destroyed or is about to be replaced. */
    fun unregisterForegroundLauncher(launcher: ((Intent) -> Unit)) {
        if (this.appLauncher == launcher) {
            this.appLauncher = null
        }
    }

    fun newResponse(data: Intent) {
        val results = data.getStringExtra("results")
        if (results != null) {
            // This happens when the intent responds to many requests at the same time.
            IntentResult.fromJsonArray(results).forEach { result ->
                if (result.id != null) {
                    awaitingRequests[result.id]?.resume(result)
                    awaitingRequests.remove(result.id)
                }
            }
        } else {
            val result = IntentResult.fromIntent(data)
            if (result.id != null) {
                awaitingRequests[result.id]?.resume(result)
                awaitingRequests.remove(result.id)
            }
        }
    }

    fun hasForegroundActivity() = appLauncher != null

    /**
     * Launches the signer, waits and parses the result
     *
     * @param requestIntent The Intent to be launched.
     * @param parser A function that parses the response Intent into a [SignerResult.RequestAddressed<T>].
     * @return The result after parsing the Intent using the provided parser.
     *
     * This function uses the [tryAndWait] utility to implement a timeout on the foreground approval.
     * It assigns a unique ID to the request and keeps a continuation to resume once the result is received.
     * If the timeout occurs or the continuation is cancelled, the request ID is cleaned up from [awaitingRequests].
     * Flags are added to the Intent to ensure it is brought to the front if already running.
     */
    suspend fun <T : IResult> launchWaitAndParse(
        requestIntentBuilder: () -> Intent,
        parser: (intent: IntentResult) -> SignerResult.RequestAddressed<T>,
    ): SignerResult.RequestAddressed<T> =
        appLauncher?.let { launcher ->
            val requestIntent = requestIntentBuilder()
            val callId = RandomInstance.randomChars(32)

            requestIntent.putExtra("id", callId)
            requestIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            try {
                val resultIntent =
                    tryAndWait(foregroundApprovalTimeout) { continuation ->
                        continuation.invokeOnCancellation {
                            awaitingRequests.remove(callId)
                        }

                        awaitingRequests.put(callId, continuation)

                        try {
                            launcher.invoke(requestIntent)
                        } catch (e: Exception) {
                            Log.e("ExternalSigner", "Error launching intent", e)
                            awaitingRequests.remove(callId)
                            throw e
                        }
                    }

                when (resultIntent) {
                    null -> SignerResult.RequestAddressed.TimedOut()
                    else -> parser(resultIntent)
                }
            } catch (e: ActivityNotFoundException) {
                Log.e("ExternalSigner", "Error launching intent: Signer not found", e)
                SignerResult.RequestAddressed.SignerNotFound()
            }
        } ?: SignerResult.RequestAddressed.NoActivityToLaunchFrom()
}
