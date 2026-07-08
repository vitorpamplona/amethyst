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

    // Failing to ESTABLISH the connection is a strong dead signal (0/30 reachable
    // fresh): HARD, so one strike drops it. "connect timed out" must be caught here
    // and NOT fall through to the alive-but-slow read-timeout branch.
    @Test
    fun connectEstablishmentFailuresAreHard() {
        assertEquals(DrainFailure.HARD, classifyDrainFailure("cannot:Connect timed out (SocketTimeoutException)"))
        assertEquals(DrainFailure.HARD, classifyDrainFailure("cannot:Unexpected response code for CONNECT:  (IOException)"))
        assertEquals(DrainFailure.HARD, classifyDrainFailure("cannot:Connection refused (ConnectException)"))
        assertEquals(DrainFailure.HARD, classifyDrainFailure("cannot:Failed to connect to /1.2.3.4:443"))
        assertEquals(DrainFailure.HARD, classifyDrainFailure("cannot:No route to host (NoRouteToHostException)"))
    }

    // DNS and TLS misconfig can never work: HARD.
    @Test
    fun dnsAndTlsAreHard() {
        assertEquals(DrainFailure.HARD, classifyDrainFailure("cannot:Unable to resolve host (UnknownHostException)"))
        assertEquals(DrainFailure.HARD, classifyDrainFailure("cannot:Received fatal alert: unrecognized_name (SSLHandshakeException)"))
        assertEquals(DrainFailure.HARD, classifyDrainFailure("cannot:PKIX path building failed: certificate (CertificateException)"))
    }

    // A bad HTTP upgrade is HARD unless the status is a retryable 429/5xx.
    @Test
    fun httpUpgradeSplitsOnStatus() {
        assertEquals(DrainFailure.HARD, classifyDrainFailure("cannot:Server Misconfigured. not a websocket"))
        assertEquals(DrainFailure.TRANSIENT, classifyDrainFailure("cannot:Server Misconfigured. Response: 503 (ProtocolException)"))
    }

    // A mid-stream reset (connection already established) might clear: TRANSIENT.
    @Test
    fun midStreamResetIsTransient() {
        assertEquals(DrainFailure.TRANSIENT, classifyDrainFailure("cannot:Connection reset (SocketException)"))
        assertEquals(DrainFailure.TRANSIENT, classifyDrainFailure("cannot:Broken pipe (SocketException)"))
    }
}
