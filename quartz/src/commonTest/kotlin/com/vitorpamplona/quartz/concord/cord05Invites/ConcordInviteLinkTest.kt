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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordInviteLinkTest {
    private val token = ByteArray(16) { it.toByte() }
    private val signer = KeyPair().pubKey.toHexKey()

    @Test
    fun stockFragmentRoundTrips() {
        val frag = ConcordInviteLink.decodeFragment(ConcordInviteLink.encodeFragment(token, relays = null))
        assertContentEquals(token, frag.token)
        assertTrue(frag.usedStockRelays)
        assertEquals(InviteRelayDictionary.STOCK, frag.relays)

        // passing the exact stock set also collapses to the stock flag
        val fromStockList = ConcordInviteLink.decodeFragment(ConcordInviteLink.encodeFragment(token, InviteRelayDictionary.STOCK))
        assertTrue(fromStockList.usedStockRelays)
    }

    @Test
    fun dictionaryRelaysRoundTrip() {
        val relays = listOf("wss://relay.ditto.pub", "wss://jskitty.com/nostr") // ids 3 and 1
        val frag = ConcordInviteLink.decodeFragment(ConcordInviteLink.encodeFragment(token, relays))
        assertFalse(frag.usedStockRelays)
        assertEquals(relays, frag.relays)
        assertContentEquals(token, frag.token)
    }

    @Test
    fun literalHostAndFullUrlRelaysRoundTrip() {
        val relays = listOf("wss://myrelay.example/nostr", "ws://plain.example")
        val frag = ConcordInviteLink.decodeFragment(ConcordInviteLink.encodeFragment(token, relays))
        assertEquals(relays, frag.relays)
    }

    @Test
    fun buildUrlTruncatesAnOversizedListToTheBootstrapCap() {
        // A community with five relays used to crash the mint ("at most 3 relays, was 5"), and was
        // then fixed by carrying all five — which the reference client's decoder refuses. The
        // fragment only has to find the bundle (CORD-05 §3), so the first three make the trip.
        val relays =
            listOf(
                "wss://one.example",
                "wss://two.example",
                "wss://three.example",
                "wss://four.example",
                "wss://five.example",
            )
        val parsed = ConcordInviteLink.parseUrl(ConcordInviteLink.buildUrl("https://vector.chat", signer, token, relays))
        assertNotNull(parsed)
        assertEquals(relays.take(3), parsed.fragment.relays)
        assertContentEquals(token, parsed.fragment.token)
    }

    @Test
    fun buildUrlKeepsTheFourStockRelaysAsTheStockFlag() {
        // The stock set still collapses to flag 0x01 and zero relay bytes rather than
        // being spelled out as four dictionary ids.
        val parsed = ConcordInviteLink.parseUrl(ConcordInviteLink.buildUrl("https://vector.chat", signer, token, InviteRelayDictionary.STOCK))
        assertNotNull(parsed)
        assertTrue(parsed.fragment.usedStockRelays)
        assertEquals(InviteRelayDictionary.STOCK, parsed.fragment.relays)
    }

    @Test
    @OptIn(ExperimentalEncodingApi::class)
    fun rejectsWrongVersion() {
        // craft a version-3 fragment: [3, 0x01, token...]
        val bytes = byteArrayOf(3, 0x01) + token
        val legacy = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
        assertFailsWith<IllegalArgumentException> { ConcordInviteLink.decodeFragment(legacy) }
    }

    @Test
    fun fullUrlRoundTripsThroughNaddr() {
        val url = ConcordInviteLink.buildUrl("https://vector.chat", signer, token)
        assertTrue(url.startsWith("https://vector.chat/invite/naddr"))

        val parsed = ConcordInviteLink.parseUrl(url)
        assertNotNull(parsed)
        assertEquals(signer, parsed.linkSignerPubKey)
        assertEquals(ConcordInviteBundleEvent.KIND, parsed.kind)
        assertContentEquals(token, parsed.fragment.token)
    }

    @Test
    fun inviteBundleKeyIsDeterministicAndTokenBound() {
        val k = ConcordKeyDerivation.inviteBundleKey(token)
        assertEquals(32, k.size)
        assertContentEquals(k, ConcordKeyDerivation.inviteBundleKey(token))
        assertFalse(k.toHexKey() == ConcordKeyDerivation.inviteBundleKey(ByteArray(16) { 0x09 }).toHexKey())
    }

    // ---- I11: at most 3 bootstrap relays (CORD-05 §3) -------------------------------------

    @Test
    fun encodingTruncatesToThreeBootstrapRelays() {
        val token = ByteArray(16) { 7 }
        val relays = listOf("wss://a.example", "wss://b.example", "wss://c.example", "wss://d.example", "wss://e.example")
        val decoded = ConcordInviteLink.decodeFragment(ConcordInviteLink.encodeFragment(token, relays))
        assertEquals(relays.take(ConcordInviteLink.MAX_BOOTSTRAP_RELAYS), decoded.relays)
    }

    @Test
    fun theStockSetIsExemptFromTheCap() {
        val decoded = ConcordInviteLink.decodeFragment(ConcordInviteLink.encodeFragment(ByteArray(16), InviteRelayDictionary.STOCK))
        assertEquals(InviteRelayDictionary.STOCK, decoded.relays)
        assertTrue(decoded.usedStockRelays)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun decodingRefusesMoreThanThreeBootstrapRelays() {
        // version 4, flags 0, count 4, four dictionary ids, then the token — what a non-conforming
        // encoder would emit and the reference client's decoder throws on.
        val bytes = byteArrayOf(4, 0, 4, 1, 2, 3, 4) + ByteArray(16)
        val fragment = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
        assertFailsWith<IllegalArgumentException> { ConcordInviteLink.decodeFragment(fragment) }
        assertNull(ConcordInviteLink.parseUrl("https://x/invite/" + ConcordInviteLink.buildUrl("https://x", "aa".repeat(32), ByteArray(16)).substringAfter("/invite/").substringBefore('#') + "#" + fragment))
    }
}
