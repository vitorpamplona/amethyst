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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a monitor is willing to SAY about someone else's relay. A negative
 * NIP-66 record is signed and public, so only failures of the connection
 * itself may be published as "unreachable".
 */
class UnreachabilityTest {
    private fun proves(e: Exception) = Unreachability.proves(e)

    @Test
    fun `a connection that never opened is unreachable`() {
        assertTrue(proves(UnknownHostException("no such host")))
        assertTrue(proves(ConnectException("connection refused")))
        assertTrue(proves(SSLHandshakeException("cert expired")))
    }

    @Test
    fun `a relay that hung up mid-transfer is not unreachable`() {
        // A relay that answers the handshake in 50ms and then sends EOFException
        // part-way through a large page is reachable; it declined to finish a
        // query. Publishing "unreachable" would be a false statement about a
        // working server.
        assertFalse(proves(EOFException("stream closed")))
    }

    @Test
    fun `our own bug is never the relay's fault`() {
        assertFalse(proves(ConcurrentModificationException()))
        assertFalse(proves(NullPointerException()))
        assertFalse(proves(ClassCastException("HashMap\$Node cannot be cast")))
    }

    @Test
    fun `an unrecognised failure stays quiet`() {
        // Conservative on purpose: staying quiet costs one retry next cycle,
        // being wrong costs a false record carrying the monitor's signature.
        assertFalse(proves(SocketTimeoutException("read timed out")))
        assertFalse(proves(RuntimeException("something new")))
    }
}
