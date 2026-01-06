/**
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
        assertEquals(listOf("1", "3", "11"), info.supported_nips)
    }

    @Test
    fun `Parse malformed array with all invalid elements`() {
        // Array contains only invalid elements
        val json = """{"name":"test relay","supported_nips":["one","two","three"]}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        // All elements invalid, should result in empty list
        assertEquals(emptyList(), info.supported_nips)
    }

    @Test
    fun `Parse invalid primitive float`() {
        // Float instead of int
        val json = """{"name":"test relay","supported_nips":1.5}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        // Cannot parse float as integer, should be null
        assertNull(info.supported_nips)
    }

    @Test
    fun `Parse invalid primitive boolean`() {
        // Boolean instead of int/array
        val json = """{"name":"test relay","supported_nips":true}"""
        val info = Nip11RelayInformation.fromJson(json)

        assertNotNull(info)
        assertEquals("test relay", info.name)
        // Cannot parse boolean as integer, should be null
        assertNull(info.supported_nips)
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
}
