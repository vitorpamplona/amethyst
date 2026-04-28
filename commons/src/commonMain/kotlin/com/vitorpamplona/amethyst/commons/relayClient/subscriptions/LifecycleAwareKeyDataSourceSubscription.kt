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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager

/**
 * A lifecycle-aware version of [KeyDataSourceSubscription] that subscribes
 * when the lifecycle reaches STARTED and unsubscribes when it reaches STOPPED.
 *
 * Use this for heavy feed subscriptions (home, video, discovery, chatroom list)
 * that should NOT run when the app is in the background. When an always-on
 * notification service keeps the relay client connected, these subscriptions
 * would otherwise leak bandwidth on feeds nobody is viewing.
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
    var isStarted by remember { mutableStateOf(false) }

    DisposableEffect(state, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        if (!isStarted) {
                            dataSource.subscribe(state)
                            isStarted = true
                        }
                    }

                    Lifecycle.Event.ON_STOP -> {
                        if (isStarted) {
                            dataSource.unsubscribe(state)
                            isStarted = false
                        }
                    }

                    else -> {}
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        // If already started (e.g., recomposition while visible), subscribe immediately
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            dataSource.subscribe(state)
            isStarted = true
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (isStarted) {
                dataSource.unsubscribe(state)
                isStarted = false
            }
        }
    }
}
