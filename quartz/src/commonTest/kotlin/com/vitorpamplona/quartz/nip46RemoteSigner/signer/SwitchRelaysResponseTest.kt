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
package com.vitorpamplona.quartz.nip46RemoteSigner.signer

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseAck
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SwitchRelaysResponseTest {
    private fun relaysOf(result: String?): Set<NormalizedRelayUrl>? {
        val parsed = SwitchRelaysResponse.parse(BunkerResponse("1", result, null))
        assertIs<SignerResult.RequestAddressed.Successful<SwitchRelaysResult>>(parsed)
        return parsed.result.relays
    }

    @Test
    fun nullMeansTheSignerDidNotMove() {
        assertNull(relaysOf("null"))
        assertNull(relaysOf(null))
        assertNull(relaysOf(""))
    }

    @Test
    fun aListIsTheSignersNewRelays() {
        assertEquals(
            setOf(NormalizedRelayUrl("wss://relay.one/"), NormalizedRelayUrl("wss://relay.two/")),
            relaysOf("""["wss://relay.one","wss://relay.two/","wss://relay.one"]"""),
        )
    }

    @Test
    fun entriesThatAreNotRelaysAreDropped() {
        assertEquals(setOf(NormalizedRelayUrl("wss://relay.one/")), relaysOf("""["wss://relay.one", "not a relay", null]"""))
    }

    @Test
    fun aListWithNoRelayLeftChangesNothing() {
        // Moving to nothing would leave the client listening nowhere.
        assertNull(relaysOf("""[]"""))
        assertNull(relaysOf("""["not a relay"]"""))
    }

    @Test
    fun aLongListIsCut() {
        val many = (1..50).joinToString(",", "[", "]") { "\"wss://relay$it.example.com\"" }
        assertEquals(SwitchRelaysResponse.MAX_RELAYS, relaysOf(many)!!.size)
    }

    @Test
    fun anErrorIsARejection() {
        val parsed = SwitchRelaysResponse.parse(BunkerResponseError("1", "unsupported method: switch_relays"))
        assertIs<SignerResult.RequestAddressed.Rejected<SwitchRelaysResult>>(parsed)
        assertEquals("unsupported method: switch_relays", parsed.message)
    }

    @Test
    fun anAnswerThatIsNotAListCouldNotBeUsed() {
        assertIs<SignerResult.RequestAddressed.ReceivedButCouldNotPerform<SwitchRelaysResult>>(
            SwitchRelaysResponse.parse(BunkerResponse("1", """{"wss://relay.one":{"read":true}}""", null)),
        )
    }

    @Test
    fun logoutAckIsSuccess() {
        assertIs<SignerResult.RequestAddressed.Successful<LogoutResult>>(LogoutResponse.parse(BunkerResponseAck("1")))
        assertIs<SignerResult.RequestAddressed.Rejected<LogoutResult>>(LogoutResponse.parse(BunkerResponseError("1", "no")))
    }
}
