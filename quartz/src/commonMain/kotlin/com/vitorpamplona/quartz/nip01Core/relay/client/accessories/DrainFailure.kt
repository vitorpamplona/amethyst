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
 *  - [TRANSIENT]: a failure that might clear — connection refused / reset, host
 *    unreachable, or a temporary 429/5xx on the upgrade. Struck a few times
 *    before we give up.
 *
 * A pure connect **timeout** is neither. The relay is most likely just busy, so
 * we retry it and never mark it dead — [classifyDrainFailure] returns null for
 * it (and for any non-failure terminal reason).
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
    // Busy, not dead: a connect/read timeout means the handshake just didn't
    // finish in time. Retry it — the relay is probably fine, only slow or loaded.
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
