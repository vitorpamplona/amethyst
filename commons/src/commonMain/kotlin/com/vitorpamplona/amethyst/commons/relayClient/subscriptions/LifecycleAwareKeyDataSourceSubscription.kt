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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableQueryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val UNSUBSCRIBE_GRACE_MILLIS = 30_000L

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
    )
}

@Composable
private fun LifecycleAwareSubscription(
    key: Any?,
    subscribe: () -> Unit,
    unsubscribe: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(key, lifecycle) {
        // Background scope so the grace timer is not gated by the UI frame clock,
        // which stops ticking while the app is backgrounded.
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        scope.launch {
            // `subscribed` is confined to this single collector coroutine, so no
            // cross-thread synchronization is needed for it.
            var subscribed = false
            lifecycle.currentStateFlow.collectLatest { current ->
                if (current.isAtLeast(Lifecycle.State.STARTED)) {
                    if (!subscribed) {
                        subscribe()
                        subscribed = true
                    }
                } else if (subscribed) {
                    // Stopped: keep the REQ alive for a short grace period.
                    // collectLatest cancels this delay if we return to STARTED first.
                    delay(UNSUBSCRIBE_GRACE_MILLIS)
                    unsubscribe()
                    subscribed = false
                }
            }
        }

        onDispose {
            scope.cancel()
            // Idempotent: removing an absent key is a cheap no-op. Guarantees the
            // subscription is released even if the grace timer was still pending.
            unsubscribe()
        }
    }
}
