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
package com.vitorpamplona.quartz.nip05DnsIdentifiers

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Nip05Test {
    companion object {
        private const val ALL_UPPER_CASE_USER_NAME = "ONETWO"
        private const val ALL_LOWER_CASE_USER_NAME = "onetwo"
    }

    val parser = Nip05Parser()
    val baseTestJson =
        """
        {
          "names": {
            "bob": "b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9"
          },
          "relays": {
            "b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9": [ "wss://relay.example.com", "wss://relay2.example.com" ]
          }
        }
        """.trimIndent()

    @Test
    fun `test with matching case on user name`() =
        runTest {
            // Set-up
            val userNameToTest = ALL_LOWER_CASE_USER_NAME
            val expectedPubKey = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"
            val nostrJson = "{\n  \"names\": {\n    \"$userNameToTest\": \"$expectedPubKey\" \n  }\n}"
            val nip05 = "$userNameToTest@domain.com"

            val parsedNip05 = Nip05Id.parse(nip05)
            assertNotNull(parsedNip05)
            assertEquals(expectedPubKey, parser.parseHexKey(parsedNip05, nostrJson))
        }

    @Test
    fun `test failure of lowercase name with uppercase in the json`() =
        runTest {
            // Set-up
            val expectedPubKey = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"
            val nostrJson = "{ \"names\": { \"$ALL_UPPER_CASE_USER_NAME\": \"$expectedPubKey\" }}"

            val nip05 = "$ALL_LOWER_CASE_USER_NAME@domain.com"
            val parsedNip05 = Nip05Id.parse(nip05)
            assertNotNull(parsedNip05)
            assertEquals(null, parser.parseHexKey(parsedNip05, nostrJson))
        }

    @Test
    fun `test uppercase name with lowercase name in the json`() =
        runTest {
            // Set-up
            val expectedPubKey = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"
            val nostrJson = "{ \"names\": { \"$ALL_LOWER_CASE_USER_NAME\": \"$expectedPubKey\" }}"

            val nip05 = "$ALL_UPPER_CASE_USER_NAME@domain.com"
            val parsedNip05 = Nip05Id.parse(nip05)
            assertNotNull(parsedNip05)
            assertEquals(expectedPubKey, parser.parseHexKey(parsedNip05, nostrJson))
        }

    @Test
    fun `execute assemble url with invalid value returns null`() {
        // given
        val nip05address = "this@that@that.com"

        val parsedNip05 = Nip05Id.parse(nip05address)
        assertNull(parsedNip05)
    }

    @Test
    fun `execute assemble url with valid value returns nip05 url`() {
        // given
        val userName = "TheUser"
        val domain = "domain.com"
        val nip05address = "$userName@$domain"

        val parsedNip05 = Nip05Id.parse(nip05address)
        assertNotNull(parsedNip05)

        // then
        assertEquals("https://$domain/.well-known/nostr.json?name=${userName.lowercase()}", parsedNip05.toUserUrl())
        assertEquals("https://$domain/.well-known/nostr.json", parsedNip05.toDomainUrl())
    }

    @Test
    fun `test json parsing with relays`() {
        val parsedNip05 = Nip05Id.parse("bob@test.com")
        assertNotNull(parsedNip05)

        val result = parser.parseHexKeyAndRelays(parsedNip05, baseTestJson)
        assertNotNull(result)
        assertEquals("b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9", result.pubkey)
        assertEquals(
            listOf("wss://relay.example.com", "wss://relay2.example.com"),
            result.relays,
        )
    }

    @Test
    fun `test full json parsing with relays`() {
        val result = parser.parse(baseTestJson)

        assertNotNull(result)

        assertEquals(1, result.names.size)
        assertEquals(1, result.relays.size)

        assertEquals("bob", result.names.keys.first())
        assertEquals("b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9", result.names["bob"])
        assertEquals("b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9", result.relays.keys.first())
        assertEquals(
            listOf("wss://relay.example.com", "wss://relay2.example.com"),
            result.relays["b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9"],
        )
    }

    @Test
    fun `test full json generation with relays`() {
        val result = parser.parse(baseTestJson)
        val newResult = parser.parse(parser.toJson(result))

        assertEquals(result, newResult)
    }
}
