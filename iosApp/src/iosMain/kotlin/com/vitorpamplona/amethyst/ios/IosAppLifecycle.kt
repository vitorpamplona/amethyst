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
package com.vitorpamplona.amethyst.ios

import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.darwin.NSObjectProtocol

/**
 * The app's trips to the background, from UIApplication's notifications.
 *
 * - `didEnterBackground`: nothing is torn down. Tor's client, its SOCKS listener and the relay pool
 *   stay as they are, as on Android, but iOS suspends the process a few seconds later and may
 *   reclaim its listening sockets while it sleeps.
 * - `willEnterForeground`: the way back from the background (never sent at launch, nor after a
 *   mere interruption like Control Center): [onReturnToForeground] runs, which checks what the
 *   suspension may have killed.
 * - `didBecomeActive`: also follows interruptions that never left the foreground, so it only marks
 *   the app as in the foreground; a launch has no trip to recover from.
 *
 * Notifications arrive on the main queue.
 */
class IosAppLifecycle(
    private val onReturnToForeground: (awayMs: Long?) -> Unit,
    private val nowMs: () -> Long = TimeUtils::nowMillis,
) {
    private val _isForeground = MutableStateFlow(true)

    /** Whether the app is in the foreground (active or about to be). */
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private var backgroundedAtMs: Long? = null
    private val observers = mutableListOf<NSObjectProtocol>()

    fun start() {
        if (observers.isNotEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        observers +=
            center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, main) { _ ->
                backgroundedAtMs = nowMs()
                _isForeground.value = false
                Log.i(TAG, "Entered the background: Tor and the relays stay up until iOS suspends the app")
            }
        observers +=
            center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, main) { _ ->
                val away = backgroundedAtMs?.let { nowMs() - it }
                backgroundedAtMs = null
                _isForeground.value = true
                Log.i(TAG) { "Returning to the foreground after ${away ?: "?"} ms: checking Tor's SOCKS listener and the media relay" }
                onReturnToForeground(away)
            }
        observers +=
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, main) { _ ->
                _isForeground.value = true
            }
    }

    fun stop() {
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
    }

    private companion object {
        const val TAG = "IosAppLifecycle"
    }
}
