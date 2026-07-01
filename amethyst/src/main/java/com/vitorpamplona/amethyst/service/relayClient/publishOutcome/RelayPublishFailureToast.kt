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
package com.vitorpamplona.amethyst.service.relayClient.publishOutcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient

/**
 * Surfaces a toast when the relay client gives up delivering one of our events to a relay after
 * exhausting its retry budget, so a failed send is visible instead of silently lost. The toast
 * channel keeps only the latest message, so a burst of per-relay failures won't stack up.
 */
@Composable
fun RelayPublishFailureToastSubscription(accountViewModel: AccountViewModel) {
    val client = remember { Amethyst.instance.client }

    DisposableEffect(accountViewModel) {
        val listener =
            object : RelayConnectionListener {
                override fun onEventGaveUp(
                    relay: IRelayClient,
                    event: Event,
                ) {
                    accountViewModel.toastManager.toast(
                        R.string.relay_send_failed_title,
                        R.string.relay_send_failed_message,
                        relay.url.url,
                    )
                }
            }
        client.addConnectionListener(listener)
        onDispose { client.removeConnectionListener(listener) }
    }
}
