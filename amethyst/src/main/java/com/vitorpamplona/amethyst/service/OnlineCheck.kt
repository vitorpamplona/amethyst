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
package com.vitorpamplona.amethyst.service

import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.cancellation.CancellationException

@Immutable data class OnlineCheckResult(
    val timeInSecs: Long,
    val online: Boolean,
)

object OnlineChecker {
    val checkOnlineCache = LruCache<String, OnlineCheckResult>(100)

    fun isCachedAndOffline(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val cached = checkOnlineCache.get(url)
        return cached != null && !cached.online && cached.timeInSecs > TimeUtils.fiveMinutesAgo()
    }

    fun isOnlineCached(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val cached = checkOnlineCache.get(url)
        if (cached != null && cached.timeInSecs > TimeUtils.fiveMinutesAgo()) {
            return cached.online
        }
        return false
    }

    fun resetIfOfflineToRetry(url: String) {
        val cached = checkOnlineCache.get(url)
        if (cached != null && !cached.online) {
            checkOnlineCache.remove(url)
        }
    }

    suspend fun isOnline(
        url: String?,
        okHttpClient: (String) -> OkHttpClient,
    ): Boolean {
        if (url.isNullOrBlank()) return false
        val cached = checkOnlineCache.get(url)
        if (cached != null && cached.timeInSecs > TimeUtils.fiveMinutesAgo()) {
            return cached.online
        }

        return try {
            val result =
                if (url.startsWith("wss")) {
                    val request =
                        Request
                            .Builder()
                            .url(url.replace("wss+livekit://", "wss://"))
                            .header("Upgrade", "websocket")
                            .header("Connection", "Upgrade")
                            .header("Sec-WebSocket-Key", RandomInstance.bytes(16).toByteString().base64())
                            .header("Sec-WebSocket-Version", "13")
                            .header("Sec-WebSocket-Extensions", "permessage-deflate")
                            .build()

                    val client =
                        okHttpClient(url)
                            .newBuilder()
                            .eventListener(EventListener.NONE)
                            .protocols(listOf(Protocol.HTTP_1_1))
                            .build()

                    client.newCall(request).executeAsync().use { it.isSuccessful }
                } else {
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .get()
                            .build()
                    val client = okHttpClient(url)

                    client.newCall(request).executeAsync().use {
                        it.isSuccessful
                    }
                }

            checkOnlineCache.put(url, OnlineCheckResult(TimeUtils.now(), result))
            result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            checkOnlineCache.put(url, OnlineCheckResult(TimeUtils.now(), false))
            Log.e("LiveActivities", "Failed to check streaming url $url", e)
            false
        }
    }
}
