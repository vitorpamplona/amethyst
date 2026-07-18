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
package com.vitorpamplona.quartz.nip01Core.relay.client.single

import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

interface IRelayClient {
    val url: NormalizedRelayUrl

    fun connect()

    fun needsToReconnect(): Boolean

    fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean = false)

    /**
     * Forgets the accumulated reconnect backoff without touching the socket.
     *
     * Call when the conditions that produced the failures no longer apply — the
     * device moved to a different network, or the relay's transport changed (Tor
     * came up, or the user re-classified this relay). Past failures were measured
     * against an environment that no longer exists, so holding a relay at a 5-minute
     * delay would keep it dark for minutes on a network that might reach it instantly.
     *
     * Unlike [disconnect] this leaves a live connection alone: it only clears the
     * penalty a *disconnected* relay would otherwise have to wait out.
     *
     * No-op by default: only transports that actually throttle reconnects (see
     * [com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient])
     * have anything to forget.
     */
    fun resetBackoff() { }

    fun isConnected(): Boolean

    fun sendOrConnectAndSync(cmd: Command)

    fun sendIfConnected(cmd: Command)

    fun disconnect()
}
