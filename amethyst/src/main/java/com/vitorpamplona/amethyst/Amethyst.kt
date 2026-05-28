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
package com.vitorpamplona.amethyst

import android.app.Application
import com.vitorpamplona.amethyst.debug.BootTrace
import com.vitorpamplona.amethyst.service.logging.Logging
import com.vitorpamplona.amethyst.service.nests.AppForegroundRecycleHook
import com.vitorpamplona.amethyst.ui.screen.AccountState
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class Amethyst : Application() {
    init {
        Log.minLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR
        Log.d("AmethystApp") { "Creating App $this" }
    }

    companion object {
        lateinit var instance: AppModules
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("AmethystApp") { "onCreate $this" }
        BootTrace.section("Boot:AppModulesCtor") {
            instance = AppModules(this)
        }

        // Arm boot-tail observers BEFORE initiate() launches IO work, so the
        // SharedFlow (replay=0) for new event bundles can't fire before we
        // subscribe. See `:macrobenchmark` for what these markers measure.
        armBootTraceObservers()

        // After-background foreground recycle: when the app returns to
        // the foreground after spending more than ~5 s in the
        // background, publish a network-change event so every active
        // NestViewModel recycles its underlying QUIC session. Covers
        // the case where Android reclaims our UDP socket FD while
        // backgrounded — the connectivity callback in
        // `NestForegroundService` doesn't fire there because the
        // network itself is still up. See `AppForegroundRecycleHook`'s
        // kdoc for the threshold rationale.
        registerActivityLifecycleCallbacks(AppForegroundRecycleHook())

        if (isDebug) {
            Logging.setup()
            // Auto-enable the Nests session-trace recorder in debug
            // builds so two-phone repros can be captured via
            //   adb logcat -s NestsTraceJsonl:D -v raw > nest-trace.jsonl
            // without rebuilding to flip a flag. Off in release.
            com.vitorpamplona.nestsclient.trace.NestsTrace
                .setRecording(true)
        }

        BootTrace.section("Boot:Initiate") {
            instance.initiate(this)
        }
    }

    private fun armBootTraceObservers() {
        val scope = instance.applicationIOScope
        val sessionManager = instance.sessionManager
        val client = instance.client
        val live = instance.cache.live

        scope.launch {
            sessionManager.accountContent.first { it is AccountState.LoggedIn }
            BootTrace.mark("Boot:FirstAccountLoaded")
        }

        scope.launch {
            client.connectedRelaysFlow().first { it.isNotEmpty() }
            BootTrace.mark("Boot:RelaysConnected")
        }

        scope.launch {
            // Single collector funnels every bundle through a CONFLATED channel
            // (capacity 1, latest wins). Removes the re-subscribe gap that the
            // naive `first()`-in-a-loop pattern had against a SharedFlow with
            // replay=0 + DROP_OLDEST.
            val ticks = Channel<Unit>(Channel.CONFLATED)
            val collector =
                launch {
                    live.newEventBundles.collect { ticks.trySend(Unit) }
                }
            try {
                // First bundle = at least one new event reached LocalCache.
                // Proxy for "the home feed has data to render"; the true
                // first-frame signal would require instrumenting commons'
                // FeedContentState.
                ticks.receive()
                BootTrace.mark("Boot:FirstHomeFeedFrame")

                // Steady = no new bundle for 3 s. Signals end of the cold-start
                // event flood (typically 10–60 s after first frame on a
                // populated account).
                while (true) {
                    val next = withTimeoutOrNull(3_000) { ticks.receive() }
                    if (next == null) {
                        BootTrace.mark("Boot:HomeFeedSteady")
                        break
                    }
                }
            } finally {
                collector.cancel()
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("AmethystApp") { "onTerminate $this" }
        instance.terminate(this)
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d("AmethystApp") { "onTrimMemory $level" }
        instance.trim()
    }
}
