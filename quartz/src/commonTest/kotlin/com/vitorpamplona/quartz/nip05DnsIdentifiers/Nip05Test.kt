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
package com.vitorpamplona.quartz.nip05DnsIdentifiers

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class Nip05Test {
    companion object {
        private val ALL_UPPER_CASE_USER_NAME = "ONETWO"
        private val ALL_LOWER_CASE_USER_NAME = "onetwo"
    }

    var nip05Verifier = Nip05()

    @Test
    fun `test with matching case on user name`() =
        runBlocking {
            // Set-up
            val userNameToTest = ALL_UPPER_CASE_USER_NAME
            val expectedPubKey = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"
            val nostrJson = "{\n  \"names\": {\n    \"$userNameToTest\": \"$expectedPubKey\" \n  }\n}"
            val nip05 = "$userNameToTest@domain.com"

            nip05Verifier.parseHexKeyFor(nip05, nostrJson).fold(
                onSuccess = { assertEquals(expectedPubKey, it) },
                onFailure = { fail("Test failure") },
            )
        }

    @Test
    fun `test with NOT matching case on user name`() =
        runBlocking {
            // Set-up
            val expectedPubKey = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"
            val nostrJson = "{ \"names\": { \"$ALL_UPPER_CASE_USER_NAME\": \"$expectedPubKey\" }}"

            val nip05 = "$ALL_LOWER_CASE_USER_NAME@domain.com"

            nip05Verifier.parseHexKeyFor(nip05, nostrJson).fold(
                onSuccess = { assertEquals(expectedPubKey, it) },
                onFailure = { fail("Test failure") },
            )
        }

    @Test
    fun `execute assemble url with invalid value returns null`() {
        // given
        val nip05address = "this@that@that.com"

        // when
        val actualValue = nip05Verifier.assembleUrl(nip05address)

        // then
        assertNull(actualValue)
    }

    @Test
    fun `execute assemble url with valid value returns nip05 url`() {
        // given
        val userName = "TheUser"
        val domain = "domain.com"
        val nip05address = "$userName@$domain"
        val expectedValue = "https://$domain/.well-known/nostr.json?name=$userName"

        // when
        val actualValue = nip05Verifier.assembleUrl(nip05address)

        // then
        assertEquals(expectedValue, actualValue)
    }
}
