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
package com.vitorpamplona.amethyst.ui.components.namecoin

import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinResolveState
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNostrResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NamecoinResolutionRowTest {
    // ── looksLikeNamecoinIdentifier ────────────────────────────────────────

    @Test
    fun `bare bit hostname is a namecoin identifier`() {
        assertTrue(looksLikeNamecoinIdentifier("testls.bit"))
        assertTrue(looksLikeNamecoinIdentifier("Example.BIT"))
    }

    @Test
    fun `user at bit hostname is a namecoin identifier`() {
        assertTrue(looksLikeNamecoinIdentifier("m@testls.bit"))
        assertTrue(looksLikeNamecoinIdentifier("ALICE@example.BIT"))
    }

    @Test
    fun `leading at sign is tolerated like the dropdown does`() {
        assertTrue(looksLikeNamecoinIdentifier("@testls.bit"))
        assertTrue(looksLikeNamecoinIdentifier("@m@testls.bit"))
    }

    @Test
    fun `dns nip05 is not a namecoin identifier`() {
        assertFalse(looksLikeNamecoinIdentifier("alice@example.com"))
        assertFalse(looksLikeNamecoinIdentifier("example.com"))
    }

    @Test
    fun `npub-shaped input is not a namecoin identifier`() {
        assertFalse(looksLikeNamecoinIdentifier("npub1w90qq8jq8x0z6nyz3vqgsk9vnp0w9p4ldwc0wq4xv2n7v8jch2nq3p6wrx"))
    }

    @Test
    fun `short or empty input is not a namecoin identifier`() {
        // Below the minimum length threshold the dropdown also uses
        // (UserSuggestionState.userSearchTermOrNull requires >2 chars).
        assertFalse(looksLikeNamecoinIdentifier(""))
        assertFalse(looksLikeNamecoinIdentifier(".bit"))
    }

    @Test
    fun `single-label bit name is still considered a namecoin identifier`() {
        // Namecoin allows single-character labels; "a.bit" is a valid
        // (if expensive) registration. Don't filter it out client-side.
        assertTrue(looksLikeNamecoinIdentifier("a.bit"))
    }

    @Test
    fun `bit substring elsewhere does not trigger`() {
        // "habit" or "rabbit.example.com" shouldn't match.
        assertFalse(looksLikeNamecoinIdentifier("rabbit.example.com"))
        assertFalse(looksLikeNamecoinIdentifier("ihabit"))
    }

    @Test
    fun `at without bit suffix does not match`() {
        // "foo@bar" with no .bit on the right side should not match.
        assertFalse(looksLikeNamecoinIdentifier("foo@bar"))
        assertFalse(looksLikeNamecoinIdentifier("foo@bar.com"))
    }

    // ── mapOutcomeToResolveState ───────────────────────────────────────────
    // Reuses the shared NamecoinResolveState already used by
    // NamecoinNameService and the desktop SearchScreen so all surfaces
    // produce the same diagnostic strings for the same outcome.

    @Test
    fun `name not found maps to NotFound`() {
        val state = mapOutcomeToResolveState(NamecoinResolveOutcome.NameNotFound("d/testls"))
        assertSame(NamecoinResolveState.NotFound, state)
    }

    @Test
    fun `no nostr field maps to Error and mentions the name`() {
        val state = mapOutcomeToResolveState(NamecoinResolveOutcome.NoNostrField("d/noname"))
        require(state is NamecoinResolveState.Error)
        assertTrue(state.message.contains("d/noname"))
        assertTrue(state.message.contains("Nostr"))
    }

    @Test
    fun `malformed record preserves underlying parser detail`() {
        val state =
            mapOutcomeToResolveState(
                NamecoinResolveOutcome.MalformedRecord(
                    "d/broken",
                    "Unfinished JSON term at EOF at line 1, column 474",
                ),
            )
        require(state is NamecoinResolveState.Error)
        assertTrue(state.message.contains("d/broken"))
        assertTrue(state.message.contains("Unfinished JSON"))
    }

    @Test
    fun `servers unreachable maps to a generic Error`() {
        val state =
            mapOutcomeToResolveState(NamecoinResolveOutcome.ServersUnreachable("Connection refused"))
        require(state is NamecoinResolveState.Error)
        assertTrue(state.message.contains("ElectrumX"))
    }

    @Test
    fun `invalid identifier maps to a generic Error`() {
        val state =
            mapOutcomeToResolveState(NamecoinResolveOutcome.InvalidIdentifier("not_a_name"))
        require(state is NamecoinResolveState.Error)
        assertEquals("Invalid Namecoin identifier", state.message)
    }

    @Test
    fun `timeout maps to a timeout Error`() {
        val state = mapOutcomeToResolveState(NamecoinResolveOutcome.Timeout)
        require(state is NamecoinResolveState.Error)
        assertTrue(state.message.contains("timed out"))
    }

    @Test(expected = IllegalStateException::class)
    fun `success outcome must be handled by callers, not mapOutcomeToResolveState`() {
        // mapOutcomeToResolveState is documented as failure-only; callers must
        // route NamecoinResolveOutcome.Success through LocalCache themselves.
        mapOutcomeToResolveState(
            NamecoinResolveOutcome.Success(
                NamecoinNostrResult(
                    pubkey = "deadbeef".repeat(8),
                    relays = emptyList(),
                    namecoinName = "d/testls",
                    localPart = "_",
                ),
            ),
        )
    }
}
