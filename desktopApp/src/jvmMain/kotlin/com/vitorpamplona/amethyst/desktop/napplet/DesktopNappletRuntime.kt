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
package com.vitorpamplona.amethyst.desktop.napplet

import com.vitorpamplona.amethyst.commons.napplet.NappletAttendance
import com.vitorpamplona.amethyst.commons.napplet.NappletLiveSubscriptions
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * What every napplet and nSite window shares: the browser engine, the blob cache, the network route,
 * and the live relay subscriptions.
 *
 * [httpClient] fetches blobs, through Tor or not; [torPort] is the Tor SOCKS port while Tor runs
 * (null otherwise); [nappletBlobsUseTor] is whether a napplet's own blob downloads go through Tor,
 * which follows the app's Tor setting as on Android.
 */
class DesktopNappletRuntime(
    val engine: DesktopCefEngine,
    val blobCache: NappletBlobCache,
    val httpClient: (useTor: Boolean) -> OkHttpClient,
    scope: CoroutineScope,
    torPort: StateFlow<Int?> = MutableStateFlow(null),
    val nappletBlobsUseTor: () -> Boolean = { false },
    /** How long a site that asked for Tor waits for it before failing closed. */
    val torWaitMs: Long = 30_000L,
) {
    val attendance = NappletAttendance<DesktopNappletSession>()

    val liveSubscriptions = NappletLiveSubscriptions(scope, attendance)

    val routes = DesktopNappletRoutes(engine.router, torPort, scope)

    // Windows close in bursts (logging out closes them all), and one trim covers them all.
    private val trims = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            for (ignored in trims) blobCache.trimToSize(NappletBlobCache.DEFAULT_MAX_BYTES)
        }
    }

    /** Trims the shared blob cache soon, off the caller's thread. */
    fun trimBlobCache() {
        trims.trySend(Unit)
    }
}
