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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Nip11RelayInformationTest {
    // Test case for properly formatted relay info with array of supported NIPs
    val standardFormatJson =
        """
        {
          "contact":"_@f7z.io",
          "description":"Nostr's Purple Pages",
          "limitation":{
            "max_limit":5000000,
            "max_message_length":131072,
            "max_subscriptions":50000
          },
          "name":"purplepag.es",
          "negentropy":1,
          "pubkey":"fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52",
          "software":"git+https://github.com/hoytech/strfry.git",
          "supported_nips":[1,2,9,11],
          "version":"1.0.4"
        }
        """.trimIndent()

    // Test case for malformed relay info where supported_nips is a single integer instead of an array
    val malformedSingleIntegerJson =
        """
        {
          "contact":"_@f7z.io",
          "description":"Nostr's Purple Pages",
          "limitation":{
            "max_limit":5000000,
            "max_message_length":131072,
            "max_subscriptions":50000
          },
          "name":"purplepag.es",
          "negentropy":1,
          "pubkey":"fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52",
          "software":"git+https://github.com/hoytech/strfry.git",
          "supported_nips":1,
          "version":"1.0.4"
        }
        """.trimIndent()

    @Test
    fun `Parse standard format`() {
        val info = Nip11RelayInformation.fromJson(standardFormatJson)

        assertNotNull(info)
        assertEquals("purplepag.es", info.name)
        assertEquals("Nostr's Purple Pages", info.description)
        assertEquals("fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52", info.pubkey)
        assertEquals(listOf("1", "2", "9", "11"), info.supported_nips)
        assertEquals("1.0.4", info.version)
        assertEquals("git+https://github.com/hoytech/strfry.git", info.software)
    }

    @Test
    fun `Parse malformed single integer`() {
        val info = Nip11RelayInformation.fromJson(malformedSingleIntegerJson)

        assertNotNull(info)
        assertEquals("purplepag.es", info.name)
        assertEquals("Nostr's Purple Pages", info.description)
        assertEquals("fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52", info.pubkey)
        // Single integer should be converted to a list with one element
        assertEquals(listOf("1"), info.supported_nips)
        assertEquals("1.0.4", info.version)
        assertEquals("git+https://github.com/hoytech/strfry.git", info.software)
    }

    @Test
    fun `Parse null value`() {
        val json = """{"name":"test relay","supported_nips":null}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        assertNull(info.supported_nips)
    }

    @Test
    fun `Parse missing field`() {
        val json = """{"name":"test relay","version":"1.0"}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        assertNull(info.supported_nips) // Field is optional with default null
    }

    @Test
    fun `Parse empty array`() {
        val json = """{"name":"test relay","supported_nips":[]}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        assertEquals(emptyList(), info.supported_nips)
    }

    @Test
    fun `Parse malformed array With invalid elements`() {
        // Array contains valid integers mixed with invalid elements (strings, floats, booleans)
        val json = """{"name":"test relay","supported_nips":[1,"invalid",3,4.5,true,11]}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        // Should skip invalid elements and keep only valid integers
        assertEquals(listOf("1", "invalid", "3", "4.5", "true", "11"), info.supported_nips)
    }

    @Test
    fun `Parse malformed array with all invalid elements`() {
        // Array contains only invalid elements
        val json = """{"name":"test relay","supported_nips":["one","two","three"]}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        // All elements invalid, should result in empty list
        assertEquals(listOf("one", "two", "three"), info.supported_nips)
    }

    @Test
    fun `Parse invalid primitive float`() {
        // Float instead of int
        val json = """{"name":"test relay","supported_nips":1.5}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        // Cannot parse float as integer, should be null
        assertEquals(listOf("1.5"), info.supported_nips)
    }

    @Test
    fun `Parse invalid primitive boolean`() {
        // Boolean instead of int/array
        val json = """{"name":"test relay","supported_nips":true}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        // Cannot parse boolean as integer, should be null
        assertEquals(listOf("true"), info.supported_nips)
    }

    @Test
    fun `Parse invalid primitive string`() {
        // String instead of int/array
        val json = """{"name":"test relay","supported_nips":"1"}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        // Strings are explicitly excluded (element.isString check), should be null
        assertNull(info.supported_nips)
    }

    @Test
    fun `Parse invalid object`() {
        // Object instead of int/array
        val json = """{"name":"test relay","supported_nips":{"nip":1}}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        // Objects are unsupported format, should be null
        assertNull(info.supported_nips)
    }

    @Test
    fun `Parse large array`() {
        // Test with a large array to ensure no performance issues
        val nips = (1..100).joinToString(",")
        val json = """{"name":"test relay","supported_nips":[$nips]}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        assertEquals((1..100).map { it.toString() }, info.supported_nips)
    }

    @Test()
    fun testNostr1Com() {
        val json =
            """
            {
                "description": "Vitor's read relay",
                "name": "vitor.nostr1.com",
                "pubkey": "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                "software": "git+https://github.com/hoytech/strfry.git",
                "supported_nips": [
                    1,
                    2,
                    4,
                    9,
                    11,
                    12,
                    16,
                    20,
                    22,
                    28,
                    33,
                    40,
                    17,
                    86,
                    77
                ],
                "version": "strfry v315-3cff8c9",
                "posting_policy": "https://vitor.nostr1.com#policy",
                "limitation": {
                    "payment_required": false,
                    "max_message_length": 262200,
                    "max_subscriptions": 80,
                    "max_filters": 1000,
                    "max_limit": 10000,
                    "max_event_tags": 10000,
                    "max_content_length": 262140,
                    "min_pow_difficulty": 0,
                    "auth_required": false,
                    "created_at_lower_limit": 94608000,
                    "created_at_upper_limit": 900
                },
                "icon": "https://image.nostr.build/6e291df3474cab17a7c079b35e704fde319fa7cd44086684a27cddcc53c3ba07.png"
            }
            """.trimIndent()

        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("vitor.nostr1.com", info.name)
        assertEquals("Vitor's read relay", info.description)
        assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", info.pubkey)
        assertEquals(
            listOf(
                "1",
                "2",
                "4",
                "9",
                "11",
                "12",
                "16",
                "20",
                "22",
                "28",
                "33",
                "40",
                "17",
                "86",
                "77",
            ),
            info.supported_nips,
        )
        assertEquals("strfry v315-3cff8c9", info.version)
        assertEquals("git+https://github.com/hoytech/strfry.git", info.software)
        assertEquals("https://vitor.nostr1.com#policy", info.posting_policy)
        assertEquals("https://image.nostr.build/6e291df3474cab17a7c079b35e704fde319fa7cd44086684a27cddcc53c3ba07.png", info.icon)
    }

    // NIP-11's own `retention` example: a kinds entry may be a `[from, to]` range.
    val retentionWithRangesJson =
        """
        {
          "name": "ranged relay",
          "retention": [
            {"kinds": [0, 1, [5, 7], [40, 49]], "time": 3600},
            {"kinds": [[40000, 49999]], "time": 100},
            {"kinds": [[30000, 39999]], "count": 1000},
            {"time": 3600, "count": 10000}
          ]
        }
        """.trimIndent()

    @Test
    fun `Parse retention kind ranges`() {
        val info = Nip11RelayInformation.fromJson(retentionWithRangesJson)

        assertEquals("ranged relay", info.name)
        val retention = assertNotNull(info.retention)
        assertEquals(4, retention.size)
        assertEquals<List<Int>?>(listOf(0, 1), retention[0].kinds)
        assertEquals(listOf(5..7, 40..49), retention[0].kindRanges)
        assertEquals(3600, retention[0].time)
        assertEquals<List<Int>?>(emptyList(), retention[1].kinds)
        assertEquals(listOf(40000..49999), retention[1].kindRanges)
        assertEquals(1000, retention[2].count)
        assertNull(retention[3].kinds)
        assertNull(retention[3].kindRanges)
        assertEquals(10000, retention[3].count)
    }

    @Test
    fun `Retention kind ranges round trip`() {
        val info = Nip11RelayInformation.fromJson(retentionWithRangesJson)
        assertEquals(info, Nip11RelayInformation.fromJson(info.toJson()))
    }

    @Test
    fun `Parse fee amount above Int range`() {
        // 21 BTC in msats: past Int.MAX_VALUE, which used to fail the whole document.
        val json =
            """
            {
              "name": "pricey relay",
              "fees": {
                "admission": [{"amount": 2100000000000, "unit": "msats"}],
                "subscription": [{"amount": 5000000, "unit": "msats", "period": 2592000}],
                "publication": [{"kinds": [4], "amount": 100, "unit": "msats"}]
              }
            }
            """.trimIndent()

        val info = Nip11RelayInformation.fromJson(json)

        assertEquals("pricey relay", info.name)
        assertEquals(
            2_100_000_000_000L,
            info.fees
                ?.admission
                ?.first()
                ?.amount,
        )
        assertEquals(
            5_000_000L,
            info.fees
                ?.subscription
                ?.first()
                ?.amount,
        )
        assertEquals(
            2592000,
            info.fees
                ?.subscription
                ?.first()
                ?.period,
        )
        assertEquals(
            listOf(4),
            info.fees
                ?.publication
                ?.first()
                ?.kinds,
        )
    }
}
