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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The published caller-identity seam between a relay layer and an
 * [IEventStore]: who is asking, carried on the coroutine context so it
 * crosses the store boundary — and any decorator in between — without
 * widening every `query`/`count` signature.
 *
 * The storage-backed relay path (`LiveEventStore`) installs this element
 * around every REQ/COUNT-driven store call when the connection has
 * NIP-42-authenticated pubkeys. A store whose results are
 * observer-relative — web-of-trust ranking, "for-you" relevance, trust
 * floors — reads it back:
 *
 * ```
 * val observer = coroutineContext[StoreQueryContext]?.observer
 * ```
 *
 * Contract: this is **ranking context only**. It may reorder or score
 * results; it must never change *which* events match a filter — access
 * control belongs to the relay policy layer, not the store. The element
 * is absent for unauthenticated callers (and for direct store use outside
 * a relay), so every read needs a null-tolerant fallback such as an
 * operator-configured default observer.
 */
class StoreQueryContext(
    /**
     * The pubkeys authenticated on the calling connection via NIP-42, in
     * no particular order. Never empty — the relay layer skips installing
     * the element instead of installing an empty one.
     */
    val authenticatedUsers: Set<HexKey>,
) : AbstractCoroutineContextElement(StoreQueryContext) {
    companion object Key : CoroutineContext.Key<StoreQueryContext>

    /**
     * Convenience for the common single-identity case: one of
     * [authenticatedUsers], or `null` when the set is empty.
     */
    val observer: HexKey? get() = authenticatedUsers.firstOrNull()
}
