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
package com.vitorpamplona.quartz.nip01Core.relay.server

/**
 * Observability hook for the relay server connection lifecycle. Both
 * [NostrServer] and [EventSourceServer] accept one and invoke it as
 * connections open and close, keyed by the stable per-connection
 * [RelaySession.id], so an operator can drive metrics (active-connection
 * gauges, churn counters) and per-connection logging without patching the
 * engine.
 *
 * Both callbacks default to no-ops; override only what you need. They may fire
 * from any transport coroutine, so implementations must be thread-safe and
 * cheap — do not block (no synchronous I/O); hand off to your metrics/logging
 * pipeline and return.
 */
interface RelayServerListener {
    /** A new connection was registered. [connectionId] is [RelaySession.id]. */
    fun onConnect(connectionId: Long) {}

    /**
     * A connection was torn down (client CLOSE, transport drop, or server
     * shutdown). Fires at most once per connection.
     */
    fun onDisconnect(connectionId: Long) {}

    companion object {
        /** Shared no-op listener used as the default. */
        val None: RelayServerListener = object : RelayServerListener {}
    }
}
