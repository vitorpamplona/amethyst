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

import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Nip11RelayInformationBuilderTest {
    @Test
    fun buildsTheSotExampleWithoutHandWrittenJson() {
        val info =
            relayInformation {
                name = "sot"
                description = "NIP-50 profile search ranked by Nostr web-of-trust"
                software = "https://github.com/vitorpamplona/sot"
                version = "0.1"
                supports(1, 11, 42, 50)
            }

        assertEquals("sot", info.name)
        assertEquals(listOf("1", "11", "42", "50"), info.supported_nips)

        // Numeric NIPs serialize as JSON integers, exactly as the hand-written string had them.
        val json = info.toJson()
        assertTrue(json.contains("\"supported_nips\":[1,11,42,50]"), json)
        assertTrue(json.contains("\"name\":\"sot\""), json)
        // Unset fields stay out of the document.
        assertTrue(!json.contains("\"limitation\""), json)
        assertTrue(!json.contains("\"fees\""), json)

        // Round-trips back to the same document.
        assertEquals(info, Nip11RelayInformation.fromJson(json))
    }

    @Test
    fun emptyBuilderProducesAllNullFields() {
        val info = relayInformation {}

        assertNull(info.name)
        assertNull(info.supported_nips)
        assertNull(info.limitation)
        assertNull(info.fees)
        assertEquals("{}", info.toJson())
    }

    @Test
    fun nestedLimitationDsl() {
        val info =
            relayInformation {
                name = "R"
                limitation {
                    maxSubscriptions = 20
                    maxFilters = 10
                    maxLimit = 500
                    authRequired = true
                }
            }

        assertEquals(20, info.limitation?.max_subscriptions)
        assertEquals(10, info.limitation?.max_filters)
        assertEquals(500, info.limitation?.max_limit)
        assertEquals(true, info.limitation?.auth_required)
        // Untouched limitation fields remain null (and are omitted from JSON).
        assertNull(info.limitation?.max_message_length)
    }

    @Test
    fun limitationFromEnforcedRelayLimitsStaysInSync() {
        val limits = RelayLimits(maxSubscriptions = 20, maxFilters = 10, maxLimit = 500, authRequired = true)
        val info =
            relayInformation {
                name = "R"
                limitation(limits)
            }

        assertEquals(limits.toNip11Limitation(), info.limitation)
    }

    @Test
    fun feesDslAccumulatesEntries() {
        val info =
            relayInformation {
                name = "Paid Relay"
                fees {
                    admission(amount = 1000, unit = "msats")
                    publication(amount = 100, unit = "msats", kinds = listOf(1, 30023))
                }
            }

        assertEquals(
            1000,
            info.fees
                ?.admission
                ?.first()
                ?.amount,
        )
        assertEquals(
            "msats",
            info.fees
                ?.admission
                ?.first()
                ?.unit,
        )
        assertEquals(
            listOf(1, 30023),
            info.fees
                ?.publication
                ?.first()
                ?.kinds,
        )
        assertNull(info.fees?.subscription)
    }

    @Test
    fun retentionEntriesAreRepeatable() {
        val info =
            relayInformation {
                name = "R"
                retention(kinds = listOf(0, 3), count = 1)
                retention(time = 3600)
            }

        assertEquals(2, info.retention?.size)
        assertEquals(arrayListOf(0, 3), info.retention?.get(0)?.kinds)
        assertEquals(1, info.retention?.get(0)?.count)
        assertEquals(3600, info.retention?.get(1)?.time)
    }

    @Test
    fun advertisesNip29SubgroupSupport() {
        val info =
            relayInformation {
                name = "Groups"
                supports(29)
                subgroups()
            }

        assertEquals(true, info.nip29?.subgroups)
        val json = info.toJson()
        assertTrue(json.contains("\"nip29\":{\"subgroups\":true}"), json)
        assertEquals(info, Nip11RelayInformation.fromJson(json))
    }

    @Test
    fun omitsNip29WhenNotDeclared() {
        val info = relayInformation { name = "R" }
        assertNull(info.nip29)
        assertTrue(!info.toJson().contains("nip29"), info.toJson())
    }

    @Test
    fun listHelpersAreRepeatableAndCollapseWhenEmpty() {
        val info =
            relayInformation {
                name = "R"
                supports(1)
                supports(11, 42)
                supports("custom-ext")
                countries("US", "CA")
                languages("en")
                tags("search")
            }

        assertEquals(listOf("1", "11", "42", "custom-ext"), info.supported_nips)
        assertEquals(listOf("US", "CA"), info.relay_countries)
        assertEquals(listOf("en"), info.language_tags)
        assertEquals(listOf("search"), info.tags)
        // Untouched lists collapse to null instead of empty arrays.
        assertNull(info.nip50)
        assertNull(info.supported_grasps)

        // Mixed numeric + string ids: numbers stay numbers, the extension stays a string.
        assertTrue(info.toJson().contains("\"supported_nips\":[1,11,42,\"custom-ext\"]"), info.toJson())
    }
}
