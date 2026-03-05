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
package com.vitorpamplona.amethyst.desktop.account

import com.vitorpamplona.amethyst.desktop.ui.auth.validateBunkerUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BunkerUriUtilsTest {
    private val validHex = "a".repeat(64)

    // --- validateBunkerUri ---

    @Test
    fun validUriReturnsNull() {
        val result = validateBunkerUri("bunker://$validHex?relay=wss://r.com")
        assertNull(result)
    }

    @Test
    fun validWithMultipleRelays() {
        val result = validateBunkerUri("bunker://$validHex?relay=wss://a.com&relay=wss://b.com")
        assertNull(result)
    }

    @Test
    fun validCaseInsensitiveScheme() {
        val result = validateBunkerUri("Bunker://$validHex?relay=wss://r.com")
        assertNull(result)
    }

    @Test
    fun validWithSecret() {
        val result = validateBunkerUri("bunker://$validHex?relay=wss://r.com&secret=abc")
        assertNull(result)
    }

    @Test
    fun missingSchemeReturnsError() {
        val result = validateBunkerUri("npub1$validHex")
        assertNotNull(result)
    }

    @Test
    fun invalidPubkeyShortReturnsError() {
        val result = validateBunkerUri("bunker://abcd?relay=wss://r.com")
        assertNotNull(result)
    }

    @Test
    fun invalidPubkeyNonHexReturnsError() {
        val result = validateBunkerUri("bunker://${"g".repeat(64)}?relay=wss://r.com")
        assertNotNull(result)
    }

    @Test
    fun invalidPubkeyTooLongReturnsError() {
        val result = validateBunkerUri("bunker://${"a".repeat(65)}?relay=wss://r.com")
        assertNotNull(result)
    }

    @Test
    fun missingRelayReturnsError() {
        val result = validateBunkerUri("bunker://$validHex?secret=abc")
        assertNotNull(result)
    }

    @Test
    fun emptyInputReturnsError() {
        val result = validateBunkerUri("")
        assertNotNull(result)
    }

    @Test
    fun blankInputReturnsError() {
        val result = validateBunkerUri("   ")
        assertNotNull(result)
    }

    // --- stripBunkerSecret ---

    @Test
    fun stripsSecretPreservesRelay() {
        val input = "bunker://$validHex?relay=wss://r.com&secret=mysecret"
        val result = stripBunkerSecret(input)
        assertEquals("bunker://$validHex?relay=wss://r.com", result)
    }

    @Test
    fun stripsSecretPreservesMultipleRelays() {
        val input = "bunker://$validHex?relay=wss://a.com&secret=mysecret&relay=wss://b.com"
        val result = stripBunkerSecret(input)
        assertEquals("bunker://$validHex?relay=wss://a.com&relay=wss://b.com", result)
    }

    @Test
    fun noSecretReturnsSameUri() {
        val input = "bunker://$validHex?relay=wss://r.com"
        val result = stripBunkerSecret(input)
        assertEquals(input, result)
    }

    @Test
    fun noQueryReturnsUnchanged() {
        val input = "bunker://$validHex"
        val result = stripBunkerSecret(input)
        assertEquals(input, result)
    }

    @Test
    fun caseInsensitiveSecretRemoval() {
        val input = "bunker://$validHex?relay=wss://r.com&Secret=foo"
        val result = stripBunkerSecret(input)
        assertEquals("bunker://$validHex?relay=wss://r.com", result)
    }

    @Test
    fun secretOnlyParamReturnsBareUri() {
        val input = "bunker://$validHex?secret=mysecret"
        val result = stripBunkerSecret(input)
        assertEquals("bunker://$validHex", result)
    }
}
