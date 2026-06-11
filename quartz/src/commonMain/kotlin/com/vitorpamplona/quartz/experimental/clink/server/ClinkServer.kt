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
package com.vitorpamplona.quartz.experimental.clink.server

import com.vitorpamplona.quartz.experimental.clink.debits.DebitEvent
import com.vitorpamplona.quartz.experimental.clink.manage.ManageEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.math.abs

/**
 * Service-side helpers for the three CLINK protocols. A CLINK service listens for
 * requests addressed to it (`#p` = its pubkey) on its relays, validates freshness,
 * then replies with `Event.createResponse(...)`.
 *
 * Amethyst is consume-only and does not run a service; these live in quartz so `amy`
 * and interop tests can exercise the responder side.
 */
object ClinkServer {
    /** Requests older/newer than this (vs. the service clock) must be rejected as stale. */
    const val MAX_REQUEST_AGE_SECONDS = 30L

    fun isFresh(
        requestCreatedAt: Long,
        now: Long = TimeUtils.now(),
    ): Boolean = abs(now - requestCreatedAt) <= MAX_REQUEST_AGE_SECONDS

    /**
     * Subscribes for incoming requests addressed to [servicePubKey]. Note the kind also
     * carries responses, so the caller should skip events where [OfferEvent.isResponse]
     * is true (they reference a request via `e`).
     */
    fun offerRequestFilter(
        servicePubKey: HexKey,
        since: Long? = null,
    ): Filter = requestFilter(OfferEvent.KIND, servicePubKey, since)

    fun debitRequestFilter(
        servicePubKey: HexKey,
        since: Long? = null,
    ): Filter = requestFilter(DebitEvent.KIND, servicePubKey, since)

    fun manageRequestFilter(
        serverPubKey: HexKey,
        since: Long? = null,
    ): Filter = requestFilter(ManageEvent.KIND, serverPubKey, since)

    private fun requestFilter(
        kind: Int,
        recipient: HexKey,
        since: Long?,
    ): Filter =
        Filter(
            kinds = listOf(kind),
            tags = mapOf("p" to listOf(recipient)),
            since = since,
        )
}

/**
 * Tracks single-use Debit session identifiers (`k1`). Per the Debits spec, a `k1` is
 * consumed once the service accepts a request for it, scoped per `pointer` (or per
 * service pubkey when no pointer is present). Structural/validation failures should
 * NOT consume it, so callers consume only after a request validates.
 *
 * In-memory and not synchronized; a real service should back this with durable,
 * concurrency-safe storage.
 */
class K1Tracker {
    private val consumed = mutableSetOf<String>()

    private fun key(
        scope: String,
        k1: HexKey,
    ) = "$scope:$k1"

    fun isConsumed(
        scope: String,
        k1: HexKey,
    ): Boolean = consumed.contains(key(scope, k1))

    /** Marks [k1] consumed for [scope]; returns false if it was already consumed. */
    fun consume(
        scope: String,
        k1: HexKey,
    ): Boolean = consumed.add(key(scope, k1))
}
