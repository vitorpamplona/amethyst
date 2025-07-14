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

import android.content.Intent
import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.NewResultProcessor
import com.vitorpamplona.quartz.utils.RandomInstance

class IntentRequestDatabase {
    private val awaitingRequests = LruCache<String, NewResultProcessor>(2000)
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
        val callId = data.getStringExtra("id")
        if (callId != null) {
            awaitingRequests[callId]?.process(data)
            awaitingRequests.remove(callId)
        }
    }

    fun launch(
        requestIntent: Intent,
        responseProcessor: NewResultProcessor,
    ) {
        appLauncher?.let {
            val callId = RandomInstance.randomChars(32)
            awaitingRequests.put(callId, responseProcessor)

            requestIntent.putExtra("id", callId)
            requestIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            it.invoke(requestIntent)
        }
    }
}
