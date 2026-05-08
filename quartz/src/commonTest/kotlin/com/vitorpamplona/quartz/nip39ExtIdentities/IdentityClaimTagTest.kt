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
package com.vitorpamplona.quartz.nip39ExtIdentities

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IdentityClaimTagTest {
    @Test
    fun parseGitHubIdentity() {
        val parsed = IdentityClaimTag.parse(arrayOf("i", "github:alice", "proof123"))

        assertNotNull(parsed)
        assertIs<GitHubIdentity>(parsed)
        assertEquals("alice", parsed.identity)
        assertEquals("proof123", parsed.proof)
    }

    @Test
    fun parseTwitterIdentity() {
        val parsed = IdentityClaimTag.parse(arrayOf("i", "twitter:bob", "1234567890"))

        assertNotNull(parsed)
        assertIs<TwitterIdentity>(parsed)
        assertEquals("bob", parsed.identity)
        assertEquals("1234567890", parsed.proof)
    }

    @Test
    fun parseTelegramIdentity() {
        val parsed = IdentityClaimTag.parse(arrayOf("i", "telegram:carol", "proof"))

        assertNotNull(parsed)
        assertIs<TelegramIdentity>(parsed)
        assertEquals("carol", parsed.identity)
    }

    @Test
    fun parseMastodonIdentity() {
        val parsed = IdentityClaimTag.parse(arrayOf("i", "mastodon:dave@example.social", "proof"))

        assertNotNull(parsed)
        assertIs<MastodonIdentity>(parsed)
        assertEquals("dave@example.social", parsed.identity)
    }

    @Test
    fun parseUnknownPlatformReturnsUnsupportedIdentity() {
        val parsed = IdentityClaimTag.parse(arrayOf("i", "matrix:erin", "proof"))

        assertNotNull(parsed)
        assertIs<UnsupportedIdentity>(parsed)
        assertEquals("matrix", parsed.platform)
        assertEquals("erin", parsed.identity)
        assertEquals("proof", parsed.proof)
    }

    @Test
    fun parsePlatformIsCaseInsensitive() {
        val parsed = IdentityClaimTag.parse(arrayOf("i", "GitHub:frank", "proof"))

        assertNotNull(parsed)
        assertIs<GitHubIdentity>(parsed)
        assertEquals("frank", parsed.identity)
    }

    @Test
    fun parseReturnsNullForShortTags() {
        assertNull(IdentityClaimTag.parse(arrayOf()))
        assertNull(IdentityClaimTag.parse(arrayOf("i")))
        assertNull(IdentityClaimTag.parse(arrayOf("i", "github:alice")))
    }

    @Test
    fun parseReturnsNullForWrongTagName() {
        assertNull(IdentityClaimTag.parse(arrayOf("e", "github:alice", "proof")))
        assertNull(IdentityClaimTag.parse(arrayOf("p", "github:alice", "proof")))
        assertNull(IdentityClaimTag.parse(arrayOf("", "github:alice", "proof")))
    }

    @Test
    fun parseReturnsNullForEmptyPlatformIdentity() {
        assertNull(IdentityClaimTag.parse(arrayOf("i", "", "proof")))
    }

    @Test
    fun parseReturnsNullForEmptyProof() {
        assertNull(IdentityClaimTag.parse(arrayOf("i", "github:alice", "")))
    }

    /**
     * Regression: malformed `i` tags whose platform-identity field has no `:`
     * (observed in production logcat as e.g. `["i", " ", " "]`) used to make
     * `create()` throw `IndexOutOfBoundsException` from destructuring `split(':')`.
     * `parse()` caught the exception but logged a noisy stack trace per offending event.
     * Now `parse()` rejects such tags up front and returns null silently.
     */
    @Test
    fun parseReturnsNullWhenPlatformIdentityHasNoColon() {
        assertNull(IdentityClaimTag.parse(arrayOf("i", "githubalice", "proof")))
        assertNull(IdentityClaimTag.parse(arrayOf("i", " ", " ")))
        assertNull(IdentityClaimTag.parse(arrayOf("i", "no-colon-here", "proof")))
    }

    @Test
    fun parseExtraTagFieldsAreIgnored() {
        val parsed = IdentityClaimTag.parse(arrayOf("i", "github:alice", "proof", "extra", "fields"))

        assertNotNull(parsed)
        assertIs<GitHubIdentity>(parsed)
        assertEquals("alice", parsed.identity)
        assertEquals("proof", parsed.proof)
    }

    @Test
    fun roundTripGitHub() {
        val original = GitHubIdentity("alice", "proof123")
        val parsed = IdentityClaimTag.parse(original.toTagArray())

        assertNotNull(parsed)
        assertIs<GitHubIdentity>(parsed)
        assertEquals(original.identity, parsed.identity)
        assertEquals(original.proof, parsed.proof)
    }

    @Test
    fun roundTripUnsupportedPreservesPlatform() {
        val original = UnsupportedIdentity("matrix", "erin", "proof")
        val parsed = IdentityClaimTag.parse(original.toTagArray())

        assertNotNull(parsed)
        assertIs<UnsupportedIdentity>(parsed)
        assertEquals("matrix", parsed.platform)
        assertEquals("erin", parsed.identity)
        assertEquals("proof", parsed.proof)
    }
}
