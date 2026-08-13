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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

/**
 * A drain per-relay failure worth acting on: the relay will not serve us THIS run.
 * Whether that also means "drop it from routing" is [dropFromRouting], and only
 * [DEAD] says yes — re-probing hop-8's failed relays fresh, outside the crawl,
 * showed the old "might clear, retry a few times" (TRANSIENT) bucket almost
 * never clears: 503 Service Unavailable was 0/12 reachable, 502 Bad Gateway 3/15,
 * connection-establishment failures 0/30, and the codes that WERE alive (403/402)
 * are gated and will never hand us events. Spending extra dials on them was waste.
 *
 * [AUTH_REQUIRED] is the deliberate exception: a relay behind a NIP-42 wall is not a
 * failed relay, it is one we turned up to without an identity it accepts.
 *
 * The only two connect failures that genuinely recover are kept OUT of this verdict
 * by [classifyDrainFailure] returning null (retry, never dead):
 *  - a **read** timeout — the relay accepted the handshake but is slow to serve;
 *    12/18 (67%) were reachable fresh, only overloaded by the crawl's fan-out. The
 *    crawler's per-authority timeout strikes, which CLEAR on success, shed the gone.
 *  - an HTTP **429 / too many requests** — alive and rate-limiting; 4/4 reachable
 *    fresh. Retrying (spaced by the rate limiter) is how we eventually get its data.
 */
enum class DrainFailure {
    DEAD,

    /**
     * The relay answered, and answered with a NIP-42 wall we could not get over: no
     * responder, a responder that declined, a signer that never came back, or an AUTH
     * the relay rejected. See [DONE_REASON_AUTH_REFUSED].
     *
     * The opposite of [DEAD] in the only way that matters for routing. A dead relay is
     * a property of the relay; this is a property of *this connection's identity*, and
     * the very same query succeeds on a socket carrying a key the relay accepts — which
     * is why [dropFromRouting] is false for it and why it must not be folded into DEAD.
     * A caller that drops these keeps re-learning nothing about a set of relays that were
     * never broken.
     */
    AUTH_REQUIRED,
    ;

    /**
     * Whether this verdict justifies pruning the relay from future routing.
     *
     * True for [DEAD] (measured overwhelmingly non-recovering — see the class KDoc) and
     * false for [AUTH_REQUIRED], which is fixed by an identity rather than by time.
     * Read this instead of testing for a specific value, so a caller written today keeps
     * behaving when the enum grows.
     */
    val dropFromRouting: Boolean get() = this == DEAD
}

/**
 * Classify a drain per-relay terminal reason. Returns null when the relay should be
 * retried rather than dropped — a read/generic timeout, an alive 429 rate-limit, or
 * a non-failure like eose/closed. Any other `cannot:<message>` (see
 * `BasicRelayClient.onCannotConnect`) is [DrainFailure.DEAD]: it will not serve us
 * this run, so drop it now instead of paying repeated connect attempts.
 *
 * An `auth-refused:<message>` is [DrainFailure.AUTH_REQUIRED] — recorded so the caller
 * learns *why* it got nothing, but flagged [DrainFailure.dropFromRouting] `false` because
 * the relay is alive and serves the same query to an accepted identity.
 */
fun classifyDrainFailure(reason: String): DrainFailure? {
    if (reason.startsWith(DONE_REASON_AUTH_REFUSED)) return DrainFailure.AUTH_REQUIRED
    if (!reason.startsWith("cannot")) return null
    val m = reason.removePrefix("cannot:").lowercase()
    // Alive, only asking us to slow down: an HTTP 429 / "too many requests" reliably
    // clears — 4/4 such relays were reachable when re-probed fresh. Retry it (the
    // rate limiter spaces our opens); never drop it.
    if ("429" in m || "too many requests" in m) return null
    // A READ timeout means the relay accepted the handshake but is slow to serve —
    // 12/18 (67%) reachable fresh, alive but overloaded by the fan-out. Retry; the
    // crawler's per-authority timeout strikes, which clear on success, shed the gone.
    // A *connect* timeout is the opposite (the socket never opened, 0/30 reachable),
    // so it is excluded here and falls through to DEAD with every other failure.
    if (("timeout" in m || "timed out" in m) && "connect timed out" !in m) return null
    // Everything else won't serve us this run: connect refused / unroutable / the
    // proxy couldn't tunnel the CONNECT, a DNS or TLS failure, a dead-or-not-a-relay
    // HTTP upgrade (502/503/500/504/410/404/200/…), or a mid-stream reset. Measured
    // mostly dead (503 0%, 502 20% reachable) and, when alive, gated (402/403) or not
    // a relay (200). Drop it now rather than burn more dials on it.
    return DrainFailure.DEAD
}
