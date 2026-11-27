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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.prepareGet
import io.ktor.http.isSuccess
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException

/**
 * Whether a stream answers, as the JVM's OnlineChecker: a GET for HTTP streams, a WebSocket
 * handshake for `wss:` ones (LiveKit rooms), the answer kept in [OnlineStatusCache].
 */
actual suspend fun IRoleBasedHttpClientBuilder.isVideoOnline(url: String): Boolean {
    if (url.isBlank()) return false
    OnlineStatusCache.fresh(url)?.let { return it.online }
    return try {
        val client = ktorClientForVideo(url)
        val online =
            if (url.startsWith("wss")) {
                client.webSocketSession(url.replace("wss+livekit://", "wss://")).close()
                true
            } else {
                // Only the status: the body of a live stream never ends.
                client.prepareGet(url).execute { it.status.isSuccess() }
            }
        OnlineStatusCache.record(url, online)
        online
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        OnlineStatusCache.record(url, false)
        Log.e("LiveActivities", "Failed to check streaming url $url", e)
        false
    }
}
