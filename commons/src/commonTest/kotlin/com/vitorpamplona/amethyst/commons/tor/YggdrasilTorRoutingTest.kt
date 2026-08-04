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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * GAP 4 — an Yggdrasil relay is classified as a plain clearnet relay, so with Tor on it is
 * dialed through the SOCKS proxy. Tor cannot route `0200::/7`: the connection can only fail.
 *
 * Compare `ws://192.168.1.100:8080/`, which [TorRelayEvaluation] correctly keeps off Tor
 * because `isLocalHost()` recognizes the LAN prefix. Yggdrasil has no such recognition.
 */
class YggdrasilTorRoutingTest {
    private val yggdrasilRelay = NormalizedRelayUrl("ws://[201:d0e:9ba5:8bbc::1]:8080/")
    private val lanRelay = NormalizedRelayUrl("ws://192.168.1.100:8080/")

    private fun evaluation(newViaTor: Boolean) =
        TorRelayEvaluation(
            torSettings =
                TorRelaySettings(
                    torType = TorType.INTERNAL,
                    onionRelaysViaTor = true,
                    dmRelaysViaTor = true,
                    newRelaysViaTor = newViaTor,
                    trustedRelaysViaTor = false,
                    moneyOperationsViaTor = false,
                ),
            trustedRelayList = emptySet(),
            dmRelayList = emptySet(),
        )

    @Test
    fun yggdrasilRelayIsSentThroughTorWhileLanRelayIsNot() {
        val eval = evaluation(newViaTor = true)
        assertTrue(eval.useTor(yggdrasilRelay), "Yggdrasil relay is routed via Tor, which cannot reach 0200::/7")
        assertFalse(eval.useTor(lanRelay), "LAN relay is correctly kept off Tor")
    }

    @Test
    fun yggdrasilRelayWorksOnlyWhenNewRelaysViaTorIsOff() {
        assertFalse(evaluation(newViaTor = false).useTor(yggdrasilRelay))
    }
}
