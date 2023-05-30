package com.vitorpamplona.amethyst.service

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class Nip05VerifierTest {
    private val ALL_UPPER_CASE_USER_NAME = "ONETWO"
    private val ALL_LOWER_CASE_USER_NAME = "onetwo"

    @SpyK
    var nip05Verifier = Nip05Verifier()

    @Before
    fun setUp() = MockKAnnotations.init(this)

    @Test
    fun `test with matching case on user name`() {
        // Set-up
        val userNameToTest = ALL_UPPER_CASE_USER_NAME
        val expectedPubKey = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"

        val nostrJson = "{\n" +
            "  \"names\": {\n" +
            "    \"$userNameToTest\": \"$expectedPubKey\" \n" +
            "  }\n" +
            "}"

        every { nip05Verifier.fetchNip05Json(any(), any(), any()) } answers {
            secondArg<(String) -> Unit>().invoke(nostrJson)
        }

        val nip05 = "$userNameToTest@domain.com"
        var actualPubkeyHex = ""

        // Execution
        nip05Verifier.verifyNip05(
            nip05,
            onSuccess = {
                actualPubkeyHex = it
            },
            onError = {
                fail("Test failure")
            }
        )

        // Verification
        assertEquals(expectedPubKey, actualPubkeyHex)
    }

    @Test
    fun `test with NOT matching case on user name`() {
        // Set-up
        val expectedPubKey = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"

        val nostrJson = "{\n" +
            "  \"names\": {\n" +
            "    \"$ALL_UPPER_CASE_USER_NAME\": \"$expectedPubKey\" \n" +
            "  }\n" +
            "}"
        every { nip05Verifier.fetchNip05Json(any(), any(), any()) } answers {
            secondArg<(String) -> Unit>().invoke(nostrJson)
        }

        val nip05 = "$ALL_LOWER_CASE_USER_NAME@domain.com"
        var actualPubkeyHex = ""

        // Execution
        nip05Verifier.verifyNip05(
            nip05,
            onSuccess = {
                actualPubkeyHex = it
            },
            onError = {
                fail("Test failure")
            }
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
