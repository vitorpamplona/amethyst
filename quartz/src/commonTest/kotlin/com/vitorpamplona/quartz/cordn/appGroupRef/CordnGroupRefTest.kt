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
package com.vitorpamplona.quartz.cordn.appGroupRef

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `spec/applications/group-ref.md`.
 *
 * The three golden strings are copied from the reference implementation's own
 * suite (`packages/core/src/groupRef.test.ts`), where they are cross-checked
 * against an independent TLV+bech32 assembly. That makes them a genuine
 * cross-implementation vector rather than a record of what our encoder happens
 * to emit — the thing a round-trip test can never tell you.
 */
class CordnGroupRefTest {
    private val gid = "550e8400-e29b-41d4-a716-446655440000"
    private val pubKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val relays = listOf("wss://relay.example.com", "wss://backup.example.com")

    private val goldenGidOnly =
        "cordn1qqjr2dfsv5urgvps94jnywtz956rzep594snwvfk956rgd3kx56ngdpsxqcrqy4yh7d"
    private val goldenGidPubKey =
        "cordn1qysqzg69v7y6hn00qy352euf40x77qfrg4ncn27dauqjx3t83x4ummcqys6n2vr98q6rqvpdv5erjc3dxsckgdpdvymnzd3dxs6rvd34x56rgvpsxqcqfqnsvd"
    private val goldenFull =
        "cordn1qgthwumn8ghj7un9d3shjtn90psk6urvv5hxxmmdqgv8wumn8ghj7cnpvd4h2upwv4uxzmtsd3jjucm0d5qjqqfrg4ncn27dauqjx3t83x4ummcpydzk0zdtehhszg69v7y6hn00qqjr2dfsv5urgvps94jnywtz956rzep594snwvfk956rgd3kx56ngdpsxqcrqv7vzv4"

    @Test
    fun weEncodeByteForByteWithTheReferenceImplementation() {
        assertEquals(goldenGidOnly, CordnGroupRef(gid).encode())
        assertEquals(goldenGidPubKey, CordnGroupRef(gid, pubKey).encode())
        assertEquals(goldenFull, CordnGroupRef(gid, pubKey, relays).encode())
    }

    @Test
    fun weDecodeTheReferenceImplementationsOutput() {
        assertEquals(CordnGroupRef(gid), CordnGroupRef.decode(goldenGidOnly))
        assertEquals(CordnGroupRef(gid, pubKey), CordnGroupRef.decode(goldenGidPubKey))
        assertEquals(CordnGroupRef(gid, pubKey, relays), CordnGroupRef.decode(goldenFull))
    }

    @Test
    fun theGidRoundTripsByteForByte() {
        // §4.1: no trimming, no re-encoding. A gid is the coordinator's cursor
        // key, so a decoder that tidied one would silently address another
        // stream — or none.
        val awkward = listOf(" leading", "trailing ", "  ", "emoji-👍", "MiXeDcAsE", "a/b?c=d")
        awkward.forEach {
            assertEquals(it, CordnGroupRef.decode(CordnGroupRef(it).encode()).gid, "gid '$it' must survive the round trip")
        }
    }

    @Test
    fun aRelayWithoutACoordinatorIsInvalid() {
        // §5: a relay names where to reach *a coordinator*; with none named it
        // has no referent.
        assertFailsWith<IllegalArgumentException> {
            CordnGroupRef(gid, coordinatorPubKey = null, relays = listOf("wss://relay.example.com"))
        }
    }

    @Test
    fun aWrongPrefixIsRejectedEvenWhenTheChecksumIsFine() {
        // An `nprofile` is a perfectly valid bech32 string. It is not a group
        // ref, and the prefix is the only thing that says so.
        val notCordn = CordnGroupRef(gid).encode().replaceFirst("cordn1", "nprofile1")
        assertFailsWith<IllegalArgumentException> { CordnGroupRef.decode(notCordn) }
    }

    @Test
    fun corruptionIsRejectedRatherThanTruncated() {
        // quartz's NIP-19 Tlv.parse stops silently at a bad tuple, which is
        // right there and wrong here: dropping the tail of this ref turns one
        // that names a coordinator into one that reaches for a default.
        val valid = CordnGroupRef(gid, pubKey).encode()
        val truncated = valid.dropLast(10)
        assertNull(CordnGroupRef.decodeOrNull(truncated), "a truncated ref must not decode")
    }

    @Test
    fun mixedCaseIsRejected() {
        val valid = CordnGroupRef(gid).encode()
        val mixed = valid.take(valid.length / 2) + valid.drop(valid.length / 2).uppercase()
        assertFailsWith<IllegalArgumentException> { CordnGroupRef.decode(mixed) }
        // All-upper is legal bech32 and must still decode.
        assertEquals(gid, CordnGroupRef.decode(valid.uppercase()).gid)
    }

    @Test
    fun anEmptyOrOversizedGidIsRejected() {
        assertFailsWith<IllegalArgumentException> { CordnGroupRef("") }
        // The TLV length field is one byte, so 255 is the hard ceiling.
        assertTrue(CordnGroupRef("a".repeat(255)).encode().isNotEmpty())
        assertFailsWith<IllegalArgumentException> { CordnGroupRef("a".repeat(256)) }
    }
}
