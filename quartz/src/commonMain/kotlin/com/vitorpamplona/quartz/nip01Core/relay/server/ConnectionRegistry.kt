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

import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Tracks the live [RelaySession]s of one relay server and fires the
 * [RelayServerListener] as they come and go. Shared by [NostrServer] and
 * [ReqResponderServer] so the connection bookkeeping — the stable-id keying,
 * the [active] gauge, and the once-only teardown accounting — lives in exactly
 * one place.
 */
@OptIn(ExperimentalAtomicApi::class)
class ConnectionRegistry(
    private val listener: RelayServerListener,
) {
    private val connections = LargeCache<Long, RelaySession>()
    private val activeCount = AtomicLong(0L)

    /** Number of connections currently registered. */
    val active: Long get() = activeCount.load()

    /** Records [session] and fires [RelayServerListener.onConnect]. Returns it for chaining. */
    fun register(session: RelaySession): RelaySession {
        connections.put(session.id, session)
        activeCount.addAndFetch(1L)
        listener.onConnect(session.id)
        return session
    }

    /**
     * Drops the connection with [id], decrementing [active] and firing
     * [RelayServerListener.onDisconnect] — but only on the first call for a
     * given connection, so a double `close()` can't underflow the gauge or
     * double-fire the listener.
     */
    fun unregister(id: Long) {
        if (connections.remove(id) != null) {
            activeCount.addAndFetch(-1L)
            listener.onDisconnect(id)
        }
    }

    /**
     * Cancels every still-open connection's subscriptions and fires
     * [RelayServerListener.onDisconnect] for each, then resets the registry.
     * Used by the server's `close()`.
     */
    fun closeAll() {
        connections.forEach { _, session ->
            session.cancelAllSubscriptions()
            listener.onDisconnect(session.id)
        }
        connections.clear()
        activeCount.store(0L)
    }
}
