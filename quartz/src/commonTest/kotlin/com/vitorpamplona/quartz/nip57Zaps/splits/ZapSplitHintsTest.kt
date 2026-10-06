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
package com.vitorpamplona.quartz.nip57Zaps.splits

import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZapSplitHintsTest {
    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val bob = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val carol = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val relay = "wss://relay.damus.io/"

    private val tags =
        arrayOf(
            arrayOf("zap", alice, relay, "1"),
            // No relay hint: linked, but not a hint.
            arrayOf("zap", bob, "", "2"),
            // A zero weight is not a split a zap would pay, so it is not linked either.
            arrayOf("zap", carol, relay, "0"),
            // Lightning-address splits carry no pubkey.
            arrayOf("zap", "someone@getalby.com"),
            // Not a key.
            arrayOf("zap", "deadbeef", relay, "1"),
            arrayOf("p", carol, relay),
        )

    @Test
    fun linksEveryPositiveWeightPubkeySplit() {
        assertEquals(listOf(alice, bob), tags.zapSplitPubKeys())
    }

    @Test
    fun hintsOnlyTheSplitsThatNameARelay() {
        assertEquals(
            listOf(PubKeyHint(alice, RelayUrlNormalizer.normalizeOrNull(relay)!!)),
            tags.zapSplitHints(),
        )
    }

    @Test
    fun aSplitWrittenWithoutARelayRoundTrips() {
        // The serializer used to drop the empty relay slot, writing ["zap", pk, "2.0"], which the
        // parser then read as a zero-weight split and discarded.
        val tag = ZapSplitSetupSerializer.toTagArray(ZapSplitSetup(bob, null, 2.0))
        assertEquals(listOf("zap", bob, "", "2.0"), tag.toList())
        assertEquals(ZapSplitSetup(bob, null, 2.0), ZapSplitSetupParser.parse(tag))
    }

    @Test
    fun theLegacyThreeSlotShapeStillParses() {
        assertEquals(ZapSplitSetup(bob, null, 2.0), ZapSplitSetupParser.parse(arrayOf("zap", bob, "2.0")))
        assertEquals(listOf(bob), arrayOf(arrayOf("zap", bob, "2.0")).zapSplitPubKeys())
        assertTrue(arrayOf(arrayOf("zap", bob, "2.0")).zapSplitHints().isEmpty())
    }

    @Test
    fun a64CharKeyMustAlsoBeHex() {
        val notHex = "g".repeat(64)
        val tags = arrayOf(arrayOf("zap", notHex, relay, "1"), arrayOf("zap", notHex, "1"), arrayOf("zap", alice, relay, "1"))
        assertNull(ZapSplitSetupParser.parseKey(tags[0]))
        assertNull(ZapSplitSetupParser.parseKey(tags[1]))
        assertNull(ZapSplitSetupParser.parseAsHint(tags[0]))
        assertEquals(listOf(alice), tags.zapSplitPubKeys())
        assertEquals(listOf(alice), tags.zapSplitHints().map { it.pubkey })
    }

    @Test
    fun parseKeyAgreesWithParseOnEveryShape() {
        // parseKey reads the tag directly instead of going through parse(); it must accept
        // exactly the pubkey splits parse() accepts.
        val shapes =
            listOf(
                arrayOf("zap", alice, relay, "1"),
                arrayOf("zap", alice, "", "2"),
                arrayOf("zap", alice, "1.5"),
                arrayOf("zap", alice, relay),
                arrayOf("zap", alice),
                arrayOf("zap", alice, relay, "0"),
                arrayOf("zap", alice, relay, "-1"),
                arrayOf("zap", alice, relay, "NaN"),
                arrayOf("zap", alice, relay, "abc"),
                arrayOf("zap", alice, "0"),
                arrayOf("zap", alice, relay, "1", "extra"),
                arrayOf("zap", "someone@getalby.com", relay, "1"),
                arrayOf("zap", "LNURL1DP68GURN8GHJ7", relay, "1"),
                arrayOf("zap", alice.uppercase(), relay, "1"),
                arrayOf("p", alice, relay, "1"),
                arrayOf("zap"),
            )
        for (tag in shapes) {
            val expected = (ZapSplitSetupParser.parse(tag) as? ZapSplitSetup)?.pubKeyHex
            assertEquals(expected, ZapSplitSetupParser.parseKey(tag), tag.toList().toString())
            val expectedHint = (ZapSplitSetupParser.parse(tag) as? ZapSplitSetup)?.let { s -> s.relay?.let { PubKeyHint(s.pubKeyHex, it) } }
            assertEquals(expectedHint, ZapSplitSetupParser.parseAsHint(tag), tag.toList().toString())
        }
    }

    @Test
    fun theCollectorsAppendInTagOrder() {
        val existing = mutableListOf("first")
        assertEquals(listOf("first", alice, bob), tags.zapSplitPubKeysTo(existing))
        assertEquals(1, tags.zapSplitHintsTo(ArrayList()).size)
    }

    @Test
    fun aNonUrlInTheRelaySlotIsNotARelay() {
        val tags = arrayOf(arrayOf("zap", alice, carol, "1"), arrayOf("zap", bob, "myrelay", "1"))
        assertEquals(listOf(alice, bob), tags.zapSplitPubKeys())
        assertTrue(tags.zapSplitHints().isEmpty())
    }
}
