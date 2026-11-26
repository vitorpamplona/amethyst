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
package com.vitorpamplona.amethyst.commons.tor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TorFailureLedgerTest {
    private var clock = 1_000L
    private val ledger = TorFailureLedger(now = { clock }, keepMs = 30_000)

    @Test
    fun readsArtisStreamFailureLines() {
        ledger.record(
            "ERROR: Failed to connect through Tor to relay.varke.eu:443: kind=RemoteHostResolutionFailed socks_reply=0x04 Error { detail: x }",
        )
        assertEquals(TorConnectFailure("RemoteHostResolutionFailed", 4), ledger.explain("relay.varke.eu", 443))
        // Read once: the next failure of that host needs its own line.
        assertNull(ledger.explain("relay.varke.eu", 443))
    }

    @Test
    fun keysByHostAndPort() {
        ledger.record("Failed to connect through Tor to abc.onion:7778: kind=OnionServiceNotFound socks_reply=0xf0")
        assertNull(ledger.explain("abc.onion", 80))
        assertEquals(TorConnectFailure("OnionServiceNotFound", 0xf0), ledger.explain("ABC.onion", 7778))
    }

    @Test
    fun forgetsOldFailuresAndIgnoresOtherLines() {
        ledger.record("Arti client created (unbootstrapped; directory downloading in background)")
        ledger.record("Failed to connect through Tor to nos.lol:443: kind=RemoteNetworkTimeout")
        clock += 30_001
        assertNull(ledger.explain("nos.lol", 443))
    }

    @Test
    fun speaksJavasSocksMessages() {
        assertEquals("SOCKS: Host unreachable", socksReplyMessage(4))
        assertEquals("SOCKS: Connection refused", socksReplyMessage(5))
        assertEquals("SOCKS: Reply error", socksReplyMessage(null))
    }
}
