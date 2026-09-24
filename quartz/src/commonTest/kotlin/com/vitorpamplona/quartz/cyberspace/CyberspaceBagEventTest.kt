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
package com.vitorpamplona.quartz.cyberspace

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §7.6's bag, opened with a key the reference implementation would derive and a
 * payload the reference implementation encrypted.
 *
 * The two vectors below were produced by `cryptography`'s AES-256-GCM, the same
 * primitive `cyberspace-cli`'s `encrypt_with_location_key` calls, under the
 * london height-4 key that `RegionKeyTest` already pins against the reference.
 * So a pass here means the whole chain holds end to end: their coordinate, their
 * Cantor roots, their key derivation, their cipher, our reader.
 *
 * The item list is deliberately mixed — a signed note, an unsigned item, a
 * forged copy of the note with one byte of content changed, and a signed shard —
 * because §7.6's rule about a bad item is the one most likely to be implemented
 * as "reject the bag".
 */
class CyberspaceBagEventTest {
    /** The key of the london vector at height 4, from `RegionKeyTest`. */
    private val key = Hex.decode("314ade123f63601cc69a0addf455ea5c2d84ea848b84285aee5a00941e316ab4")

    private val itemsPayload = "AAECAwQFBgcICQoLikF2BmXHqvoLgsTTZr3X9aZ0QQMXvpkcOPEtBi/voCzJmyQSBWKQ+F72zKwph7HgxRVzBB9h/0AXE3vvfYr9GO1hA39fx+PMxXMpQIIm8mgbAwjXaNHptMO5JoP4BaIK6+/CKlkisTnWlgKZv3c4NaANGgC2A6ul4F/ArroMbfhnt/dgRsQ8+LYmGShg88UbIV5tgSNIyanpd3ORcJy3hEsBmMBIfLetGh42TTbG7K36A7VBmTDpoymJrhqbrGaLbiSYfqgDIeQ6aKo74qBy7vg+YDFdP895RI/VvuiXYFCKk+DaDiWUG4exYsMNR1XZr6ECpn9F9WCFh3o1DATzYmYvDCkwM66euqcwk7D0wURBZTEmHJlfyAliCxWtIK5AdZucelvWO1v1geyoQ/3SaZ6cgfm1qQn3aEuK5cVCEEUiaaBd6zdn1JiLfVkHo/0xCkr4VQNo9dnLNh46iY/LO1IIsgjity5qYYaYMpFgUs9oFUeJ1v1tBMT0cogZ0rbFD2TmJ8MWPYT5BilG/gzjAWLYc8vEXJH4hbxGVVuitnQ5szAdJhD27TpRgEplcSmUBInJwkQQ2iY3F+baZp4M6QBNT3VKDbfL9XejqZECslH6RZ1zpla1+zGRvIT6724IlnuVgnpTy++EUJS8O6oFc6KNFfVryMwASOzd9geCCSQHf7ljhxESf98G36hyFGahMQyRfAorkWA99ulHERtNzUw6Su/WkvTUIcMBeAlbyuB5vluBddq2Z4IcLEbSn9gB2Ey5iRSCA1VifzhGSkZAgUZ7UB16FZdLFRmgjpUIdJBxy3XsL7fnl/TCQLoSat2IO157J7oe4pJ3qfrJX1ihqs0BTzGTdWNIR5slzQZIZiBelOyCxBVDslDCpn/av53CRZUpIkEXTkBQbQ+RNz1RGKVQLt/B01BfpFpAv1RoiWR7/B31/Hu2gHJv++/ey0Q6ByY7T6c/5SvAlDA55rZZwau27SG31cDu92ZLosqmHA+AvYirTjqY16UtAJJHaFMxYyb2TToUkHFK2DbMHIQuYacY3tITbnOpknTUZJgQfI0hFkSBfMZpRlod/1MT+tyzTeA9UCzf/HrEn0piOBvtdPs+eul/g5P7eAKStsY1ztZN37BeQT1LGplelYi/1Bu9Ok8uie3/uDMoixhnD3/taNo6UwCkX9eoS+Dt/y36+IFSEbQ38T5IDoiA9ZiLcESUZdaplWyOdPgPQhSrqFvMBR/iKvpW2gs/8rvgbNKBK08CUBFsTM9xhClBWG2MxHDWBcYzGue/1U687FF7VXU5T2HUOYLw53OZMdg6vw/z5JTssE3y5stjJZ3LTpPgIMinn4p3n5HG18prMfuF2ML9FuuvOvactdwxomW2LHsnM6XWKxEGcw+4/nZGti580uVYZZG5kqXptmyJ7GXXSM+vUEUSeC/wVDEEMPjcKLf/UlJKLrLNPLD92dRYut3xwCj3/v5ZuVlGjd/ZTUqGTLZJTXPMs73KcT+nORsz1JSi+nrmvgVjzuppY67dRP7mH5dB/YxueAT8VFPRr+bf+uii28MAXQk7p3sJ2RNNO/MhNG7eI+rEMhzrEJI0+34VZ1HuUwEk7DxTS7BmOgQQaLXZHMX7nbtGKRIVa9oxQc93ay/OTU66fR1W+UlXT6ED7aUzcpNKr2CaxWnhMpNJ2ELSBPpZIbON3Bjq0NKmI0s8V1cPX5HdzA40QzKOfWcuO5uE6apATYcuYmmQcauNJbZ3iX0QdQV8R4d0YFZh9ycFI6ybwD3yRVGwpM9vJZ1yOJE0gB2L4b9EEazSQB0Np9k67N8lNSuMKfp5TOJKr+Wk39Z+hLW6YMUFGM4e4DU9wqSZP6dlfeQkxHDasPqB2UzGYIb+zpIzfMJUd5zlz+VCdWg="

    private val opaquePayload = "AAECAwQFBgcICQoLu08nGyGW/7Vdw9GJIb/FveUqGhVW7YsSZuQ83LcizhHAJWNnQG/CHIcDKA=="

    private fun bag(
        payload: String,
        vararg extra: Array<String>,
    ): CyberspaceBagEvent =
        CyberspaceBagEvent(
            id = "a".repeat(64),
            pubKey = "b".repeat(64),
            createdAt = 1700000000,
            tags =
                arrayOf(
                    arrayOf("d", "a1d82532c354e690c6bffdb1fb20ccda716e037586feaf70092cbc442a635916"),
                    arrayOf("version", "2"),
                    arrayOf("h", "4"),
                    arrayOf("encrypted", "aes-256-gcm", payload),
                ) + extra,
            content = "",
            sig = "0".repeat(128),
        )

    @Test
    fun aBagTheReferenceEncryptedOpensWithTheKeyTheReferenceDerives() {
        val contents = bag(itemsPayload).open(key)
        assertNotNull(contents, "the reference's own cipher, under the reference's own key")
        assertTrue(contents is CyberspaceBagContents.Items, "a JSON array of events is the item shape (§7.6)")
    }

    @Test
    fun aForgedItemCostsItselfAndNothingElse() {
        // §7.6: "a reader MUST drop an item that fails either check, and only
        // that item, because one corrupt or forged item says nothing about the
        // others."
        val contents = bag(itemsPayload).open(key) as CyberspaceBagContents.Items
        assertEquals(1, contents.dropped, "the forged copy of the note")
        assertEquals(3, contents.items.size, "the signed note, the unsigned item and the signed shard")
    }

    @Test
    fun anUnsignedItemIsKeptAndItsAuthorshipIsNotClaimed() {
        // §7.6: "An item without a `sig` is allowed... its `pubkey` is then a
        // claim, and readers MUST NOT present it as verified."
        val contents = bag(itemsPayload).open(key) as CyberspaceBagContents.Items
        val unsigned = contents.items.single { it.event.sig.isBlank() }
        assertFalse(unsigned.verified)
        assertEquals("f".repeat(64), unsigned.event.pubKey, "the claim is carried, not endorsed")
        // And the signed ones are verified, by the same field.
        assertEquals(2, contents.items.count { it.verified })
    }

    @Test
    fun anItemMayNameItsOwnPointInsideTheRegion() {
        // §7.6: an item MAY carry a `C` tag, "which lets a client render it at a
        // point rather than somewhere in the region".
        val contents = bag(itemsPayload).open(key) as CyberspaceBagContents.Items
        val placed = contents.items.single { it.coordinate() != null }
        assertEquals("c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e33c9940", placed.coordinate())
        assertTrue(contents.items.count { it.coordinate() == null } > 0, "and an item without one is located no more precisely than the region")
    }

    @Test
    fun aKindTheReaderDoesNotKnowIsStillAnItem() {
        // §7.6: "A reader that does not understand an item's `kind` skips it and
        // renders the rest" — skipping is the renderer's job, so the reader
        // hands over everything that verified.
        val contents = bag(itemsPayload).open(key) as CyberspaceBagContents.Items
        assertTrue(contents.items.any { it.event.kind == 3330 }, "the shard came through as an item")
        assertTrue(contents.items.any { it.event.kind == 1 })
    }

    @Test
    fun aPlaintextThatIsNotAListIsOpaque() {
        // §7.6's other shape: "anything that is not a list of items, such as a
        // text note or a file".
        val contents = bag(opaquePayload).open(key)
        assertTrue(contents is CyberspaceBagContents.Opaque)
        assertEquals("just some words, not a list", contents.bytes.decodeToString())
    }

    @Test
    fun theWrongKeyIsSilenceRatherThanAnError() {
        // §7.6: "An attempt with the wrong key fails at the GCM tag check and
        // reveals nothing about the plaintext. A failed decryption therefore
        // means only that the reader does not hold this region's key; it MUST
        // NOT be treated as an error in the bag."
        val wrong = ByteArray(32) { 7 }
        assertNull(bag(itemsPayload).open(wrong))
        // And a key of the wrong size is not a key at all.
        assertNull(bag(itemsPayload).open(ByteArray(16)))
    }

    @Test
    fun aVersionTheReaderDoesNotKnowIsIgnored() {
        // §8.6: "`version` names the rules of §7.6. A reader MUST ignore a bag
        // whose version it does not know."
        val future =
            CyberspaceBagEvent(
                "a".repeat(64),
                "b".repeat(64),
                1700000000,
                arrayOf(
                    arrayOf("d", "abc"),
                    arrayOf("version", "3"),
                    arrayOf("encrypted", "aes-256-gcm", itemsPayload),
                ),
                "",
                "0".repeat(128),
            )
        assertFalse(future.isKnownVersion())
        assertNull(future.open(key), "a bag whose rules are unknown is not opened with this reader's")
    }

    @Test
    fun aCipherThisDoesNotImplementIsNotAMalformedBag() {
        // Only the lookup and the key are normative across the protocol; §7.6's
        // cipher is the convention the CLI and ONOSENDAI share. A bag naming
        // another one is somebody else's format.
        val other =
            CyberspaceBagEvent(
                "a".repeat(64),
                "b".repeat(64),
                1700000000,
                arrayOf(arrayOf("version", "2"), arrayOf("encrypted", "chacha20-poly1305", itemsPayload)),
                "",
                "0".repeat(128),
            )
        assertNull(other.payload())
        assertNull(other.open(key))
    }

    @Test
    fun theTagsThatMakeABagFindableAreRead() {
        val hinted =
            bag(
                itemsPayload,
                arrayOf("hint", "c492492492492492492492edf5bee7267451c787d95ba4d7840c76d000000000", "11", "11", "11"),
            )
        assertEquals("a1d82532c354e690c6bffdb1fb20ccda716e037586feaf70092cbc442a635916", hinted.lookupId())
        assertEquals(4, hinted.height())
        assertTrue(hinted.isKnownVersion())

        val hint = hinted.hint()
        assertNotNull(hint)
        // Read against the bag's own h, as §7.7 requires.
        assertEquals(21, hint.gapBits(4))
        assertFalse(hint.isDestination(4))
    }

    @Test
    fun aPayloadTooShortToHoldANonceAndATagIsNotOpened() {
        // 12 + 16 bytes before there is any ciphertext at all; anything shorter
        // is not this cipher's output.
        val short = bag("AAECAwQFBgcICQoL")
        assertNull(short.open(key))
    }

    @Test
    fun anEventOfThisKindArrivesAsABag() {
        val json = bag(opaquePayload).toJson()
        assertTrue(Event.fromJson(json) is CyberspaceBagEvent, "kind 33330 is registered in the factory")
    }
}
