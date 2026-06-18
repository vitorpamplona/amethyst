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
package com.vitorpamplona.amethyst.commons.relayClient.subscriptions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableQueryState
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// DIAGNOSTIC: temporarily 0 to test whether unsubscribing the moment the app
// pauses actually disconnects the feed's outbox relays in the background. If the
// 30s grace's delay() was being starved on Dispatchers.Default once backgrounded
// (Doze/app-standby suspends timers), firing immediately on ON_STOP both proves it
// and fixes the leak. Restore to 30_000L (with a wakelock-/foreground-safe timer)
// once confirmed, to keep absorbing short app switches.
private const val UNSUBSCRIBE_GRACE_MILLIS = 0L

/**
 * A lifecycle-aware version of [KeyDataSourceSubscription] that subscribes
 * when the lifecycle reaches STARTED and unsubscribes 30 seconds after it
 * reaches STOPPED. If the lifecycle returns to STARTED before the grace
 * period elapses, the pending unsubscribe is cancelled and the subscription
 * keeps running uninterrupted.
 *
 * The grace window absorbs short app switches (copy a snippet from another
 * app, dismiss a notification, glance at recents) without tearing down and
 * rebuilding the relay REQ — which would otherwise lose EOSE state and
 * trigger a refetch on return.
 *
 * The grace timer runs on a dedicated [Dispatchers.Default] scope driven by
 * [Lifecycle.currentStateFlow] rather than on the composition's frame-clock
 * coupled scope (`rememberCoroutineScope`). On a backgrounded app the UI
 * frame clock stops ticking, so a timer scheduled there could be starved and
 * the unsubscribe — and therefore the relay disconnect it triggers — might
 * never run. This is most visible on the relay feed, whose dedicated one-off
 * relay is kept connected by nothing else and would leak forever. Using a
 * plain coroutine dispatcher keeps the timer firing while backgrounded;
 * [collectLatest] cancels the pending delay automatically the moment the
 * lifecycle returns to STARTED.
 *
 * Use this for heavy feed subscriptions (home, video, discovery, chatroom list)
 * that should NOT run when the app is truly in the background. When an
 * always-on notification service keeps the relay client connected, these
 * subscriptions would otherwise leak bandwidth on feeds nobody is viewing.
 *
 * Lightweight subscriptions that should always run (account metadata, notifications,
 * gift wraps) should continue using the regular [KeyDataSourceSubscription].
 */
@Composable
fun <T> LifecycleAwareKeyDataSourceSubscription(
    state: T,
    dataSource: ComposeSubscriptionManager<T>,
) {
    LifecycleAwareSubscription(
        key = state,
        subscribe = { dataSource.subscribe(state) },
        unsubscribe = { dataSource.unsubscribe(state) },
        label = dataSource::class.simpleName ?: "?",
    )
}

@Composable
fun <T> LifecycleAwareKeyDataSourceSubscription(
    states: List<T>,
    dataSource: ComposeSubscriptionManager<T>,
) {
    LifecycleAwareSubscription(
        key = states,
        subscribe = { dataSource.subscribe(states) },
        unsubscribe = { dataSource.unsubscribe(states) },
        label = dataSource::class.simpleName ?: "?",
    )
}

@Composable
fun <T : MutableQueryState> LifecycleAwareKeyDataSourceSubscription(
    state: T,
    dataSource: MutableComposeSubscriptionManager<T>,
) {
    LifecycleAwareSubscription(
        key = state,
        subscribe = { dataSource.subscribe(state) },
        unsubscribe = { dataSource.unsubscribe(state) },
        label = dataSource::class.simpleName ?: "?",
    )
}

@Composable
private fun LifecycleAwareSubscription(
    key: Any?,
    subscribe: () -> Unit,
    unsubscribe: () -> Unit,
    label: String,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(key, lifecycle) {
        // Detect lifecycle transitions with a main-thread LifecycleEventObserver, NOT by
        // collecting currentStateFlow on a background dispatcher. On a real backgrounded
        // device the latter delivered ON_STOP ~60s late — it only woke on the next
        // NostrClient keep-alive tick — leaving heavy feeds (and ~150 relays) connected
        // for that whole minute after the app was paused. A LifecycleEventObserver fires
        // synchronously during onStop, so teardown starts immediately.
        //
        // The grace *delay* still runs on a background scope so it isn't gated by the UI
        // frame clock, which stops ticking while the app is backgrounded.
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // graceJob is only ever read/written from the main thread (observer callbacks),
        // so no synchronization is needed. subscribe()/unsubscribe() are idempotent
        // (reference-counted map ops), so re-issuing subscribe() on each ON_START is safe.
        var graceJob: Job? = null

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        graceJob?.cancel()
                        graceJob = null
                        Log.d("BgRelayTrace") { "subscribe($label)" }
                        subscribe()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        graceJob?.cancel()
                        graceJob =
                            scope.launch {
                                if (UNSUBSCRIBE_GRACE_MILLIS > 0) delay(UNSUBSCRIBE_GRACE_MILLIS)
                                Log.d("BgRelayTrace") { "unsubscribe($label)" }
                                unsubscribe()
                            }
                    }

                    else -> {}
                }
            }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            scope.cancel()
            Log.d("BgRelayTrace") { "dispose-unsubscribe($label)" }
            // Idempotent: removing an absent key is a cheap no-op. Guarantees the
            // subscription is released even if the grace timer was still pending.
            unsubscribe()
        }
    }
}
