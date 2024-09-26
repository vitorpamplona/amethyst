/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service

import android.os.Looper
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class Nip05NostrAddressVerifierTest {
    companion object {
        private val ALL_UPPER_CASE_USER_NAME = "ONETWO"
        private val ALL_LOWER_CASE_USER_NAME = "onetwo"
    }

    @SpyK var nip05Verifier = Nip05NostrAddressVerifier()

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
        MockKAnnotations.init(this)
    }

    @Test
    fun `test with matching case on user name`() =
        runBlocking {
            // Set-up
            val userNameToTest = ALL_UPPER_CASE_USER_NAME
            val expectedPubKey = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"

            val nostrJson =
                "{\n  \"names\": {\n    \"$userNameToTest\": \"$expectedPubKey\" \n  }\n}"

            coEvery { nip05Verifier.fetchNip05Json(any(), any(), any(), any()) } answers
                {
                    runBlocking {
                        thirdArg<suspend (String) -> Unit>().invoke(nostrJson)
                    }
                }

            val nip05 = "$userNameToTest@domain.com"
            var actualPubkeyHex = ""

            // Execution
            nip05Verifier.verifyNip05(
                nip05,
                forceProxy = { false },
                onSuccess = { actualPubkeyHex = it },
                onError = { fail("Test failure") },
            )

            // Verification
            assertEquals(expectedPubKey, actualPubkeyHex)
        }

    @Test
    fun `test with NOT matching case on user name`() =
        runBlocking {
            // Set-up
            val expectedPubKey = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"

            val nostrJson = "{ \"names\": { \"$ALL_UPPER_CASE_USER_NAME\": \"$expectedPubKey\" }}"
            coEvery { nip05Verifier.fetchNip05Json(any(), any(), any(), any()) } answers
                {
                    runBlocking {
                        thirdArg<suspend (String) -> Unit>().invoke(nostrJson)
                    }
                }

            val nip05 = "$ALL_LOWER_CASE_USER_NAME@domain.com"
            var actualPubkeyHex = ""

            // Execution
            nip05Verifier.verifyNip05(
                nip05,
                forceProxy = { false },
                onSuccess = { actualPubkeyHex = it },
                onError = { fail("Test failure") },
            )

            // Verification
            assertEquals(expectedPubKey, actualPubkeyHex)
        }

    @After
    fun tearDown() {
        unmockkAll()
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
