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
package com.vitorpamplona.quartz.nip01Core.store

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.utils.Log

/**
 * Verify [event]'s NIP-01 id + signature and, if valid, persist it to this store.
 * Returns `true` when the event was accepted (verified) — even if the insert was a
 * no-op — so callers can gate "surface this event" on the return.
 *
 * A UNIQUE-constraint rejection is normal, not a failure: the store already holds
 * this id, or a newer version of a replaceable (kind 0/3/10000-19999). The outbox
 * model routinely delivers the same event from several of a user's write relays, so
 * a crawl produces these by the hundred-thousand — so only genuine persistence
 * failures (I/O, full disk, corruption) are logged. Persistence is best-effort: an
 * insert error is swallowed, not propagated, so it can't break a live subscription.
 *
 * This is the single verify-then-store sink every event-arrival path should funnel
 * through, so the store stays the authoritative cache of what has been seen.
 */
suspend fun IEventStore.verifyAndInsert(event: Event): Boolean {
    if (!event.verify()) {
        Log.w("EventStore") { "dropped event ${event.id.take(8)} kind=${event.kind} — bad signature" }
        return false
    }
    try {
        insert(event)
    } catch (t: Throwable) {
        if (t.message?.contains("UNIQUE constraint", ignoreCase = true) != true) {
            Log.w("EventStore") { "store insert failed for ${event.id.take(8)}: ${t.message}" }
        }
    }
    return true
}
