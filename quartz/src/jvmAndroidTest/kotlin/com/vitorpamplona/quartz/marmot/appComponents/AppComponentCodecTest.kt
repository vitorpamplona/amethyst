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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Per-component validation rules that the MDK-generated fixture cannot reach:
 * it only ever contains valid, happy-path state.
 */
class AppComponentCodecTest {
    private fun key(b: Int) = ByteArray(32) { b.toByte() }

    // --- profile --------------------------------------------------------------

    @Test
    fun profileRoundTripsIncludingEmptyAndUnicode() {
        for (
        profile in
        listOf(
            GroupProfileV1("", ""),
            GroupProfileV1("Marmot", "a group"),
            GroupProfileV1("groupe café ☕", "descrição"),
        )
        ) {
            assertEquals(profile, GroupProfileV1.decode(profile.encode()))
        }
    }

    @Test
    fun profileEqualityIsByteEqualityNotUnicodeEquivalence() {
        // U+00E9 vs "e" + U+0301 render identically and are canonically
        // equivalent, but they are DIFFERENT group states. Normalizing either
        // way would make us disagree with a peer that did not normalize.
        val precomposed = GroupProfileV1("caf\u00e9", "")
        val decomposed = GroupProfileV1("cafe\u0301", "")
        assertTrue(precomposed != decomposed)
        assertTrue(!precomposed.encode().contentEquals(decomposed.encode()))
    }

    @Test
    fun profileLengthLimitsAreEnforcedOnBothSides() {
        assertFailsWith<IllegalArgumentException> { GroupProfileV1("x".repeat(257), "") }
        assertFailsWith<IllegalArgumentException> { GroupProfileV1("", "x".repeat(4097)) }
        // Limits are in BYTES, not characters: a 3-byte character trips the
        // limit sooner than its length suggests.
        assertFailsWith<IllegalArgumentException> { GroupProfileV1("☕".repeat(86), "") }
        GroupProfileV1("x".repeat(256), "x".repeat(4096))
    }

    @Test
    fun profileRejectsTrailingBytes() {
        assertFailsWith<IllegalArgumentException> {
            GroupProfileV1("a", "b").encode().let { GroupProfileV1.decode(it + 0x00) }
        }
    }

    // --- admin policy ---------------------------------------------------------

    @Test
    fun adminPolicySortsAndDeduplicatesOnBuild() {
        val policy = AdminPolicyV1.of(listOf(key(3), key(1), key(3), key(2)))
        assertEquals(listOf(key(1), key(2), key(3)).map { it.toHexKey() }, policy.adminHexKeys)
        assertEquals(policy, AdminPolicyV1.decode(policy.encode()))
    }

    @Test
    fun adminPolicyRejectsUnsortedOrDuplicateWireBytes() {
        // Decoding must not repair what it reads: an unsorted admin list is
        // invalid signed state, and normalizing it would leave two peers
        // holding different bytes they each considered valid.
        val unsorted = AdminPolicyV1.of(listOf(key(1), key(2))).encode()
        val swapped = unsorted.copyOf()
        // Swap the two 32-byte keys inside the payload (1 varint prefix byte).
        for (i in 0 until 32) {
            val a = swapped[1 + i]
            swapped[1 + i] = swapped[33 + i]
            swapped[33 + i] = a
        }
        assertFailsWith<IllegalArgumentException> { AdminPolicyV1.decode(swapped) }

        val duplicated = swapped.copyOf()
        for (i in 0 until 32) duplicated[33 + i] = duplicated[1 + i]
        assertFailsWith<IllegalArgumentException> { AdminPolicyV1.decode(duplicated) }
    }

    @Test
    fun adminPolicyRejectsAnEmptyListAndRaggedPayloads() {
        assertFailsWith<IllegalArgumentException> { AdminPolicyV1(emptyList()) }
        assertFailsWith<IllegalArgumentException> { AdminPolicyV1.of(emptyList()) }
        assertFailsWith<IllegalArgumentException> { AdminPolicyV1.decode("00".hexToByteArray()) }
        assertFailsWith<IllegalArgumentException> { AdminPolicyV1(listOf(ByteArray(31))) }
        // 33 payload bytes is not a whole number of keys.
        assertFailsWith<IllegalArgumentException> {
            AdminPolicyV1.decode(byteArrayOf(33) + ByteArray(33))
        }
    }

    @Test
    fun adminPolicySortsByUnsignedByteValue() {
        // 0x80 must sort AFTER 0x01 — a signed comparison would invert this,
        // and roughly half of all x-only keys start above 0x7f.
        val low = ByteArray(32).also { it[0] = 0x01 }
        val high = ByteArray(32).also { it[0] = 0x80.toByte() }
        assertEquals(listOf(low, high).map { it.toHexKey() }, AdminPolicyV1.of(listOf(high, low)).adminHexKeys)
    }

    // --- nostr routing --------------------------------------------------------

    @Test
    fun routingRoundTripsAndSorts() {
        val routing = NostrRoutingV1.of(key(0x5a), listOf("wss://relay.damus.io", "wss://nos.lol"))
        assertEquals(listOf("wss://nos.lol", "wss://relay.damus.io"), routing.relays)
        assertEquals(routing, NostrRoutingV1.decode(routing.encode()))
    }

    @Test
    fun routingEnforcesItsStructuralLimits() {
        assertFailsWith<IllegalArgumentException> { NostrRoutingV1(ByteArray(31), listOf("wss://a.example")) }
        assertFailsWith<IllegalArgumentException> { NostrRoutingV1(key(1), emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            NostrRoutingV1.of(key(1), (1..17).map { "wss://r$it.example" })
        }
        assertFailsWith<IllegalArgumentException> {
            NostrRoutingV1(key(1), listOf("wss://b.example", "wss://a.example"))
        }
    }

    @Test
    fun routingEnforcesTheRelayUrlProfile() {
        for (
        bad in
        listOf(
            "https://relay.example",
            "relay.example",
            "wss://",
            "wss://user:pass@relay.example",
            "wss://relay.example#frag",
            "wss://" + "a".repeat(600),
            "",
        )
        ) {
            assertFailsWith<IllegalArgumentException>("must reject $bad") {
                NostrRoutingV1(key(1), listOf(bad))
            }
        }
        for (
        good in
        listOf(
            "wss://relay.example",
            "ws://127.0.0.1:8080",
            "wss://relay.example/path",
            "wss://relay.example:443/?x=1",
        )
        ) {
            NostrRoutingV1(key(1), listOf(good))
        }
    }

    // --- message retention ----------------------------------------------------

    @Test
    fun retentionIsAFixedWidthUint64() {
        val retention = MessageRetentionV1(86_400uL)
        assertEquals(8, retention.encode().size, "no length prefix, unlike most component fields")
        assertEquals("0000000000015180", retention.encode().toHexKey())
        assertEquals(retention, MessageRetentionV1.decode(retention.encode()))
        assertFailsWith<IllegalArgumentException> { MessageRetentionV1.decode(ByteArray(7)) }
        assertFailsWith<IllegalArgumentException> { MessageRetentionV1.decode(ByteArray(9)) }
    }

    @Test
    fun retentionZeroMeansDisabledRatherThanInvalid() {
        // MIP-01 rejected 0; the component defines it as "disabled", and
        // removing the component is equivalent.
        assertTrue(!MessageRetentionV1.DISABLED.isEnabled)
        assertEquals(MessageRetentionV1.DISABLED, MessageRetentionV1.decode(ByteArray(8)))
        assertNull(MessageRetentionV1.DISABLED.expiryTimestamp(1_700_000_000L))
    }

    @Test
    fun retentionExpiryIsCheckedNotWrapped() {
        val hour = MessageRetentionV1(3_600uL)
        assertEquals(1_700_003_600uL, hour.expiryTimestamp(1_700_000_000L))

        // A duration near the uint64 ceiling must yield "undefined" rather than
        // wrapping to a small timestamp that would expire the message at once.
        val huge = MessageRetentionV1(ULong.MAX_VALUE)
        assertNull(huge.expiryTimestamp(1L))
        assertEquals(ULong.MAX_VALUE, huge.expiryTimestamp(0L))
        assertNull(hour.expiryTimestamp(-1L))
    }

    @Test
    fun retentionCarriesDurationsAboveTheSignedLongRange() {
        // uint64 on the wire; a value above 2^63 must survive the round trip
        // rather than reading back as a negative Long.
        val big = MessageRetentionV1(ULong.MAX_VALUE)
        assertEquals("ffffffffffffffff", big.encode().toHexKey())
        assertEquals(big, MessageRetentionV1.decode(big.encode()))
    }

    // --- lifecycle ------------------------------------------------------------

    @Test
    fun lifecycleIsExactlyOneByte() {
        assertContentEquals(byteArrayOf(0), GroupLifecycleV1.ACTIVE.encode())
        assertContentEquals(byteArrayOf(1), GroupLifecycleV1.DISBANDED.encode())
        assertEquals(GroupLifecycleV1.ACTIVE, GroupLifecycleV1.decode(byteArrayOf(0)))
        assertEquals(GroupLifecycleV1.DISBANDED, GroupLifecycleV1.decode(byteArrayOf(1)))
        assertFailsWith<IllegalArgumentException> { GroupLifecycleV1.decode(byteArrayOf(2)) }
        assertFailsWith<IllegalArgumentException> { GroupLifecycleV1.decode(byteArrayOf(0, 0)) }
        assertFailsWith<IllegalArgumentException> { GroupLifecycleV1.decode(ByteArray(0)) }
    }

    // --- blossom image --------------------------------------------------------

    @Test
    fun absentImageIsFiveEmptyFieldsNotZeroBytes() {
        val encoded = GroupBlossomImageV1.ABSENT.encode()
        assertContentEquals("0000000000".hexToByteArray(), encoded)
        assertEquals(GroupBlossomImageV1.ABSENT, GroupBlossomImageV1.decode(encoded))
        assertTrue(!GroupBlossomImageV1.ABSENT.hasImage)
    }

    @Test
    fun presentImageRoundTripsWithItsMediaType() {
        val image =
            GroupBlossomImageV1(
                imageHash = key(0xaa),
                imageKey = key(0xbb),
                imageNonce = ByteArray(12) { 0xcc.toByte() },
                imageUploadKey = key(0xdd),
                mediaType = "image/png",
            )
        assertTrue(image.hasImage)
        assertEquals(image, GroupBlossomImageV1.decode(image.encode()))
    }

    @Test
    fun imageFieldsAreAllOrNothing() {
        assertFailsWith<IllegalArgumentException>("a half-populated image is invalid") {
            GroupBlossomImageV1(key(1), key(2), ByteArray(12), null, "image/png")
        }
        assertFailsWith<IllegalArgumentException>("a present image must name its media type") {
            GroupBlossomImageV1(key(1), key(2), ByteArray(12), key(3), null)
        }
        assertFailsWith<IllegalArgumentException>("an absent image carries no media type") {
            GroupBlossomImageV1(null, null, null, null, "image/png")
        }
    }

    @Test
    fun imageAadBindsTheMediaTypeWithNoLengthPrefixes() {
        // "marmot-group-image-v1" || 0x00 || media_type. MIP-01 used an empty
        // AAD and had no media type at all, so this is a wire-visible break.
        val aad = GroupBlossomImageV1.aad("image/png")
        assertEquals(
            "marmot-group-image-v1".encodeToByteArray().size + 1 + "image/png".length,
            aad.size,
        )
        assertEquals(0, aad["marmot-group-image-v1".length].toInt())
        assertTrue(!GroupBlossomImageV1.aad("image/png").contentEquals(GroupBlossomImageV1.aad("image/jpeg")))
    }
}
