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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DrainFailureTest {
    // Non-failure and non-"cannot" terminals are never dead signals.
    @Test
    fun nonFailureTerminalsAreNull() {
        assertNull(classifyDrainFailure("eose"))
        assertNull(classifyDrainFailure("closed:duplicate: sub"))
        assertNull(classifyDrainFailure("timeout"))
    }

    // A READ timeout (or generic post-handshake timeout) is alive-but-slow: never
    // dead. Measured 67% of these relays were reachable when re-probed fresh.
    @Test
    fun readTimeoutsStayRetryable() {
        assertNull(classifyDrainFailure("cannot:Read timed out (SocketTimeoutException)"))
        assertNull(classifyDrainFailure("cannot:timeout (SocketTimeoutException)"))
    }

    // An HTTP 429 rate-limit is alive and will serve us after backoff: never dead.
    // Measured 4/4 such relays reachable when re-probed fresh.
    @Test
    fun rateLimitStaysRetryable() {
        assertNull(classifyDrainFailure("cannot:Server Misconfigured. Response: 429 Too Many Requests (ProtocolException)"))
    }

    // Failing to ESTABLISH the connection is dead (0/30 reachable fresh). "connect
    // timed out" must be caught as DEAD and NOT slip into the read-timeout branch.
    @Test
    fun connectEstablishmentFailuresAreDead() {
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Connect timed out (SocketTimeoutException)"))
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Unexpected response code for CONNECT:  (IOException)"))
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Connection refused (ConnectException)"))
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Failed to connect to /1.2.3.4:443"))
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:No route to host (NoRouteToHostException)"))
    }

    // DNS and TLS misconfig can never work: DEAD.
    @Test
    fun dnsAndTlsAreDead() {
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Unable to resolve host (UnknownHostException)"))
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Received fatal alert: unrecognized_name (SSLHandshakeException)"))
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:PKIX path building failed: certificate (CertificateException)"))
    }

    // Every other bad HTTP upgrade won't serve us this run (measured 503 0%, 502 20%
    // reachable; 402/403 gated; 200 not a relay) — DEAD, dropped on the first strike.
    @Test
    fun deadOrGatedHttpUpgradesAreDead() {
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Server Misconfigured. not a websocket"))
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Server Misconfigured. Response: 503 Service Unavailable (ProtocolException)"))
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Server Misconfigured. Response: 502 Bad Gateway (ProtocolException)"))
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Server Misconfigured. Response: 402 Payment Required (ProtocolException)"))
    }

    // A mid-stream reset won't hand us events this run either: DEAD.
    @Test
    fun midStreamResetIsDead() {
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Connection reset (SocketException)"))
        assertEquals(DrainFailure.DEAD, classifyDrainFailure("cannot:Broken pipe (SocketException)"))
    }
}
