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
package com.vitorpamplona.quartz.nip11RelayInfo

import com.vitorpamplona.quartz.nip01Core.relay.server.RelayLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Nip11ServingTest {
    @Test
    fun contentTypeIsTheNostrJsonMediaType() {
        assertEquals("application/nostr+json", Nip11RelayInformation.CONTENT_TYPE)
    }

    @Test
    fun toJsonOmitsNullFields() {
        val json = Nip11RelayInformation(name = "Relay", supported_nips = listOf("1", "45")).toJson()
        assertTrue(json.contains("\"name\":\"Relay\""))
        // Unset fields are not emitted.
        assertTrue(!json.contains("\"description\""))
        assertTrue(!json.contains("\"limitation\""))
    }

    @Test
    fun parsesAndEmitsBanner() {
        val info = Nip11RelayInformation(name = "R", banner = "https://example.com/banner.png")
        val json = info.toJson()
        assertTrue(json.contains("\"banner\":\"https://example.com/banner.png\""))
        assertEquals("https://example.com/banner.png", Nip11RelayInformation.fromJson(json).banner)
    }

    @Test
    fun documentRoundTripsCarryingTheEnforcedLimits() {
        // The same RelayLimits object that the server enforces feeds the document.
        val limits = RelayLimits(maxSubscriptions = 20, maxFilters = 10, maxLimit = 500, authRequired = true)
        val info =
            Nip11RelayInformation(
                name = "My Relay",
                supported_nips = listOf("1", "11", "42", "45"),
                limitation = limits.toNip11Limitation(),
            )

        val parsed = Nip11RelayInformation.fromJson(info.toJson())

        assertEquals("My Relay", parsed.name)
        assertEquals(20, parsed.limitation?.max_subscriptions)
        assertEquals(10, parsed.limitation?.max_filters)
        assertEquals(500, parsed.limitation?.max_limit)
        assertEquals(true, parsed.limitation?.auth_required)
    }
}
