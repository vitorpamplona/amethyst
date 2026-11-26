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
package com.vitorpamplona.amethyst.commons.relayClient.diagnostics

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log

/**
 * One DEBUG line per relay connection attempt, saying which route it took and how it ended:
 *
 * ```
 * RelayRoute: wss://nos.lol/ dialing via tor:17392
 * RelayRoute: wss://nos.lol/ open via tor:17392 (412 ms)
 * RelayRoute: wss://relay.example/ failed via direct: WebSocket Failure: …
 * ```
 *
 * So that which relays went through Tor, and what happened to them, can be read off a log and
 * compared between platforms. [routeOf] names the route a dial takes now (`tor:<port>`, `direct`,
 * `bypass`, …); it is asked once, when the dial starts, and the outcome is reported against that
 * answer even if the route moved meanwhile. A dial the socket builder holds back (Tor not ready)
 * never starts, so it has no line.
 *
 * Everything is behind `Log.d`'s lambda: below DEBUG it costs a listener call per connection event
 * and nothing else.
 */
class RelayRouteLogger(
    private val client: INostrClient,
    private val routeOf: (NormalizedRelayUrl) -> String,
) {
    // The route each relay's current attempt took. Guarded by [lock].
    private val lock = KmpLock()
    private val routes = HashMap<String, String>()

    private val listener =
        object : RelayConnectionListener {
            override fun onConnecting(relay: IRelayClient) {
                Log.d(TAG) {
                    val route = routeOf(relay.url)
                    lock.withLock { routes[relay.url.url] = route }
                    "${relay.url.url} dialing via $route"
                }
            }

            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                Log.d(TAG) { "${relay.url.url} open via ${routeFor(relay.url)} ($pingMillis ms)" }
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                Log.d(TAG) { "${relay.url.url} failed via ${routeFor(relay.url)}: $errorMessage" }
            }

            override fun onDisconnected(relay: IRelayClient) {
                Log.d(TAG) { "${relay.url.url} closed (was via ${routeFor(relay.url)})" }
            }
        }

    private fun routeFor(url: NormalizedRelayUrl): String = lock.withLock { routes[url.url] } ?: routeOf(url)

    fun register() {
        client.addConnectionListener(listener)
    }

    fun unregister() {
        client.removeConnectionListener(listener)
    }

    companion object {
        const val TAG = "RelayRoute"
    }
}
