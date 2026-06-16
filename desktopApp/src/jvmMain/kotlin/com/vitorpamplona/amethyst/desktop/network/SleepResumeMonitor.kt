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
package com.vitorpamplona.amethyst.desktop.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Detects host-machine sleep/wake transitions by watching wall-clock overshoot
 * of a tight delay loop. When the OS suspends the JVM, [delay] returns far past
 * its scheduled deadline; the OkHttp websocket connections we held are dead by
 * then even though [com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient]
 * still reports `isConnected() == true` until the next ping fails — so the
 * standard keep-alive reconnect path is a no-op and the offline banner stays
 * stuck until a manual reload.
 *
 * Lives here in the desktop app (per Vitor's review of #3221) instead of the
 * cross-platform NostrClient because sleep/wake semantics differ across
 * platforms — Android has Doze + network change broadcasts, iOS has app
 * lifecycle events, and macOS/Linux/Windows desktops can grow real OS-level
 * sleep hooks here later (NSWorkspace notifications, D-Bus PrepareForSleep,
 * WM_POWERBROADCAST) without touching Quartz.
 *
 * Real OS sleep events would be more precise, but the wall-clock heuristic
 * needs zero native deps and catches the symptom for v1.
 */
suspend fun runSleepResumeMonitor(
    intervalMs: Long = DEFAULT_INTERVAL_MS,
    wakeThresholdMs: Long = DEFAULT_WAKE_THRESHOLD_MS,
    nowMs: () -> Long = { System.currentTimeMillis() },
    onWake: () -> Unit,
) {
    var lastTickMs = nowMs()
    while (coroutineContext.isActive) {
        delay(intervalMs)
        val now = nowMs()
        val elapsed = now - lastTickMs
        lastTickMs = now
        if (elapsed > wakeThresholdMs) onWake()
    }
}

private const val DEFAULT_INTERVAL_MS: Long = 60_000L

// 5x the tick — wide enough to ignore GC stalls / brief scheduler hiccups, tight
// enough to recover quickly after a real sleep.
private const val DEFAULT_WAKE_THRESHOLD_MS: Long = 5 * DEFAULT_INTERVAL_MS
