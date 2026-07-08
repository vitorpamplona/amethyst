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
 * Why a relay could not be used for a one-shot drain — when the reason is worth
 * acting on (dropping the relay from further routing).
 *
 *  - [HARD]: the relay answered wrong, or cannot exist. A bad HTTP upgrade (not a
 *    websocket / dead status code), an unresolvable domain, or a TLS misconfig.
 *    This will not fix itself, so one strike is enough to drop it.
 *  - [TRANSIENT]: a failure that might clear — a connection reset mid-stream or a
 *    temporary 429/5xx on the upgrade. Struck a few times before we give up.
 *
 * The split between the two connect failures is drawn on measured reachability. On
 * a hop-8 crawl, relays that failed to ESTABLISH a connection (connect timed out,
 * refused, unroutable, or the proxy couldn't tunnel the CONNECT) were 0/30 reachable
 * when re-probed fresh outside the crawl — genuinely dead, so they are [HARD] and one
 * strike drops them. But relays that hit a *read* timeout (handshake accepted, slow
 * to serve) were 12/18 (67%) reachable fresh — alive, only overloaded by the crawl's
 * fan-out. Those must NOT be marked dead: [classifyDrainFailure] returns null for a
 * read/generic timeout (and any non-failure terminal reason), and the crawler's
 * per-authority timeout strikes, which CLEAR on any success, shed only the truly gone.
 */
enum class DrainFailure { HARD, TRANSIENT }

/**
 * Classify a drain per-relay terminal reason. Returns null when the relay should
 * simply be retried (a timeout, or a non-failure like eose/closed). The reason
 * shape is `cannot:<message>` for a connect failure (see
 * `BasicRelayClient.onCannotConnect`), or `eose` / `closed:…` / `timeout`.
 */
fun classifyDrainFailure(reason: String): DrainFailure? {
    if (!reason.startsWith("cannot")) return null
    val m = reason.removePrefix("cannot:").lowercase()
    // The message now carries the exception class name (see BasicRelayClient), so
    // we can key on the stable *type* rather than localized message text.
    // Couldn't even open the socket: the connect timed out, was refused, the host is
    // unroutable, or the proxy couldn't tunnel the CONNECT. Measured 0/30 such relays
    // reachable when re-probed fresh outside the crawl — dead, so one strike is enough.
    // Checked BEFORE the timeout branch so "connect timed out" lands here and is not
    // mistaken for the alive-but-slow *read* timeout below.
    if ("connect timed out" in m ||
        "unexpected response code for connect" in m || // proxy couldn't CONNECT-tunnel
        "connection refused" in m ||
        "econnrefused" in m ||
        "failed to connect" in m ||
        "no route to host" in m ||
        "network is unreachable" in m ||
        "network is down" in m
    ) {
        return DrainFailure.HARD
    }
    // Busy, not dead: a READ timeout means the relay accepted the handshake but was
    // slow to serve — measured 12/18 (67%) reachable fresh outside the crawl, only
    // overloaded by its fan-out. Retry (never mark dead); per-authority timeout
    // strikes that clear on success shed the truly gone.
    if ("timeout" in m || "timed out" in m) return null // SocketTimeoutException, etc.
    // Cannot ever work: unresolvable domain (DNS) or a TLS misconfiguration.
    // Dead for good — one strike is enough.
    if ("unknownhost" in m || // UnknownHostException
        "unable to resolve host" in m ||
        "no address associated" in m ||
        "nodename nor servname" in m ||
        "sslhandshake" in m || // SSLHandshakeException
        "sslpeerunverified" in m ||
        "sslexception" in m ||
        "certificate" in m || // CertificateException
        "trust anchor" in m ||
        "certpath" in m
    ) {
        return DrainFailure.HARD
    }
    // Wrong HTTP upgrade. Usually a misconfigured endpoint (not a relay), but
    // 429 / 5xx mean "busy, come back later", so those stay transient.
    if ("server misconfigured" in m || "not a websocket" in m || "expected http 101" in m) {
        val transientCode = Regex("response: (429|500|502|503|504)").containsMatchIn(m)
        return if (transientCode) DrainFailure.TRANSIENT else DrainFailure.HARD
    }
    // Refused / reset / unreachable / anything else: might clear — retry a few times.
    return DrainFailure.TRANSIENT
}
