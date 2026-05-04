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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableQueryState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    DisposableEffect(state, lifecycleOwner) {
        var isSubscribed = false
        var pendingUnsubscribe: Job? = null

        fun subscribeNow() {
            pendingUnsubscribe?.cancel()
            pendingUnsubscribe = null
            if (!isSubscribed) {
                dataSource.subscribe(state)
                isSubscribed = true
            }
        }

        fun scheduleUnsubscribe() {
            if (!isSubscribed || pendingUnsubscribe != null) return
            pendingUnsubscribe =
                scope.launch {
                    delay(UNSUBSCRIBE_GRACE_MILLIS)
                    if (isSubscribed) {
                        dataSource.unsubscribe(state)
                        isSubscribed = false
                    }
                    pendingUnsubscribe = null
                }
        }

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        subscribeNow()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        scheduleUnsubscribe()
                    }

                    else -> {}
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            subscribeNow()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pendingUnsubscribe?.cancel()
            pendingUnsubscribe = null
            if (isSubscribed) {
                dataSource.unsubscribe(state)
                isSubscribed = false
            }
        }
    }
}

@Composable
fun <T : MutableQueryState> LifecycleAwareKeyDataSourceSubscription(
    state: T,
    dataSource: MutableComposeSubscriptionManager<T>,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    DisposableEffect(state, lifecycleOwner) {
        var isSubscribed = false
        var pendingUnsubscribe: Job? = null

        fun subscribeNow() {
            pendingUnsubscribe?.cancel()
            pendingUnsubscribe = null
            if (!isSubscribed) {
                dataSource.subscribe(state)
                isSubscribed = true
            }
        }

        fun scheduleUnsubscribe() {
            if (!isSubscribed || pendingUnsubscribe != null) return
            pendingUnsubscribe =
                scope.launch {
                    delay(UNSUBSCRIBE_GRACE_MILLIS)
                    if (isSubscribed) {
                        dataSource.unsubscribe(state)
                        isSubscribed = false
                    }
                    pendingUnsubscribe = null
                }
        }

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        subscribeNow()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        scheduleUnsubscribe()
                    }

                    else -> {}
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            subscribeNow()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pendingUnsubscribe?.cancel()
            pendingUnsubscribe = null
            if (isSubscribed) {
                dataSource.unsubscribe(state)
                isSubscribed = false
            }
        }
    }
}
