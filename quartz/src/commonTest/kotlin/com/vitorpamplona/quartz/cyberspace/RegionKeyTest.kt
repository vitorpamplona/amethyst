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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.bigint.UBigInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * §4's Cantor roots and §7.2's key derivation, against the reference
 * implementation.
 *
 * Every expectation below came out of `cyberspace-cli`'s own
 * `compute_subtree_cantor`, `cantor_pair` and `int_to_bytes_be_min`, run over
 * the four coordinates at five heights each. They are keys: if this file passes,
 * a bag the reference hid is one Amethyst can open, and if it fails they are two
 * different networks that happen to share a spec.
 *
 * The **key** is the vector rather than `region_n` because it pins `region_n`
 * exactly in 32 bytes — a root at height 10 is eleven kilobytes of hex — and
 * because it also pins the byte encoding, which is the part most likely to
 * drift: the reference writes minimal big-endian, and a platform big integer
 * that adds a sign byte produces a different key for one number in two.
 */
class RegionKeyTest {
    /** `name | height | key | lookup_id`, from the reference. */
    private val reference =
        listOf(
            "london|0|c9fed7928825bb4192386ad339bfe73df70de5918e4395f29a97762458bfb972|c55b1e044f016744c45b6dc8080e6e3825f9d88b412e2d1a6becad4de8c5f5ef",
            "london|1|6ea9f6b310442cced2ae706468aa90c9c5e217483b727b819c212c231f99b17f|24f83bebbf580937f066df5283abe7386f9abbc38f567b8442fdc7e591ffb053",
            "london|4|314ade123f63601cc69a0addf455ea5c2d84ea848b84285aee5a00941e316ab4|a1d82532c354e690c6bffdb1fb20ccda716e037586feaf70092cbc442a635916",
            "london|8|28939afc70712ce271b74ac7f5b9ed8339c955311cc289c516c63fa6e7a7a489|188ae4d5dccd60a27207f1d46a83959e002ec99cdeec717873ed5dc1a0fd5f69",
            "london|10|83f219e105c3011e0f1b06f1f0fc053b437c2edb5826ff707dbb2cc8ef7dd550|a94422ee1b5423639dac61c203ee3c308c47b86470065587f38abf6a359d946e",
            "nyc|0|1232b3a383492a4051fc7ee67d0332b42fcd113e834f05203f54c709e0f9bc78|80c73abdf82ab69d3e6795b13e0271321bf7ae1348007adf909b6b1e5f80ecfa",
            "nyc|1|847cec141e1f645d296bd3c80bcc32f8b5ef60868f34533958d8917c117d8204|ee1802e91efeea676cc2955d9af0405dba74ad247c1833334efd2d52efb6cb04",
            "nyc|4|bdb14b2c226797a79a2a808cbbf6126ddb5eb4bce27c8880c9bd668c853db4d1|4b3b0c9c1103ca97cff6124dce1752cdfec03a7358e28f6dd734ceb21e32ed3e",
            "nyc|8|71cadf83822c69daafff9ea77ccfe4fb3b9d113b6f2e3bcd5f270862145807a6|1cf5bb35aa98c110b37add7d0bdfbd42474fe7c0cf523f569b06225ab8b0761b",
            "nyc|10|a947d0322e2afad8c4852e2045c00f277bee246f41477212067b9c01f9cb9192|d464d48d1b85eb216d94b3b56654e8ce1a0f7b5c3e6a643096c88632e477aca4",
            "origin|0|692329a8a9893517bfaaa0c1e27f597bc6b498d1371a4c866aa89424f54fe633|6eaedee401f51b2d54c51f86a603888dc1decb14dc872d26a0ffd6f4a4369136",
            "origin|1|cb140125e63937b960fe6ed4a8ef6c9e801918c53a6b03b74897f245e01d12e4|21263dc0e2d46b450aa471aa34114ce21682818c5fbb285b06713f0ad3b26485",
            "origin|4|a41a79fe0e41b1d5519fa71ab36fa0249f3411da72b810471e22274b09647de2|cb5e37e49dbdd02bae07347220635443739704d4177e84fa5b8ff8efbd2d8ce3",
            "origin|8|fd1c6fc0d687db7edfc8747d5e844b79b3bfff1ebe206305e6f73d6ad27697fa|83ffb82c33758464c11482736525f14273eacaa4b0946119fb7202c04a7d30ec",
            "origin|10|7d439417987c3b4c4cdc3bc9d76e1e09986817b9a5f6119d4f8fb4e858572ba3|5e6d4f1d57f9bc65bb07b6aeac02dd39f9a893790bb9788887d9d5f1fdad50c7",
            "ideaspace|0|9d8de1257072196c30d90d540154a8259f1804dddc7dbcb3d99499cc48dec355|3350bd0e23d4b80b6a0e1070169f3e3252ce2d41e1b09134e14fea60fe7aa122",
            "ideaspace|1|1f98a44ace1dbc67c7c45f5de8b010f6c551ba80f4022e9958c5eccf3640746c|ab1fda3988d871b25d45b93e28f971284e57da83ffb3e0e03c03fcbe2ede1ca8",
            "ideaspace|4|bd26b0a550956d90161c21bc96e0adeb3ac262eea59345705eb26adf63345e56|c9d141d4f23590036f9cd3b82de1cd00faa512dc11937e5fc30b18e9ad382150",
            "ideaspace|8|ee7c2c7af8c082705533436aa577e28f9020dc2eaf3bab4425c13560df67d306|2e82777cc6a759f52e76004b312806c1ddd2ee7642260f2cc3e52f1873b6aaef",
            "ideaspace|10|da3f756d61498b59f00cc179a7c643e30d24395fc437091445f686bce64994fb|36b601485a2d28eb41b21bb20e05782106ae8753982196de5f95c3e03dad66a2",
        )

    private val coordinates =
        mapOf(
            "london" to "c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e33c9940",
            "nyc" to "c4924924924924924924921f79235dae293ada913e78294253a235239a332854",
            "origin" to "e000000000000000000001200041040208048040000000000000000000000000",
            "ideaspace" to "a4b64924924924924924924924924924924924924924924924924d84b60d9c8f",
        )

    @Test
    fun everyRegionKeyMatchesTheReference() {
        for (row in reference) {
            val (name, height, key, lookupId) = row.split("|")
            val point = CyberspaceCoordinate.decode(coordinates.getValue(name))
            assertNotNull(point, name)

            val material = RegionKey.at(point, height.toInt())
            assertEquals(key, material.decryptionKey.toHexKey(), "$name at height $height: the key")
            assertEquals(lookupId, material.lookupId, "$name at height $height: the lookup id")
        }
    }

    private operator fun List<String>.component4() = this[3]

    @Test
    fun theCantorPairIsTheOneTheSpecWrites() {
        // §4.6: cantor_pair(a, b) = (a + b)(a + b + 1) / 2 + b, worked by hand.
        // (3 + 5) * 9 / 2 + 5 = 41.
        assertEquals(UBigInt.of(41), CantorTree.cantorPair(UBigInt.of(3), UBigInt.of(5)))
        assertEquals(UBigInt.of(0), CantorTree.cantorPair(UBigInt.ZERO, UBigInt.ZERO))
        // It is a bijection, so no two pairs share a value. The classic witness
        // is that it is not symmetric.
        assertTrue(CantorTree.cantorPair(UBigInt.of(5), UBigInt.of(3)) != CantorTree.cantorPair(UBigInt.of(3), UBigInt.of(5)))
    }

    @Test
    fun aHeightOfZeroIsTheBaseItself() {
        // The reference returns `base` unchanged at height 0, so a region of one
        // leaf is that leaf's own number and costs nothing.
        assertEquals(UBigInt.of(12345), CantorTree.subtreeRoot(UBigInt.of(12345), 0))
    }

    @Test
    fun theSpecsOwnOneDimensionalExampleHolds() {
        // §4.5: "LCA(0, 3) => subtree [0..3] => root = 228", and the same root
        // for LCA(1, 2) and LCA(0, 2) — all three movements see one region,
        // "which is exactly what enables location-based discovery".
        assertEquals(UBigInt.of(228), CantorTree.subtreeRoot(UBigInt.ZERO, 2))
    }

    @Test
    fun aSubtreeTallerThanTheCeilingIsRefusedRatherThanAttempted() {
        // Both references raise instead of trying, and for the same reason: one
        // height past the ceiling is twice the leaves and a root twice as wide,
        // and the number is a stranger's to choose.
        assertFailsWith<IllegalArgumentException> {
            CantorTree.subtreeRoot(UBigInt.ZERO, CantorTree.DEFAULT_MAX_COMPUTE_HEIGHT + 1)
        }
        assertFailsWith<IllegalArgumentException> { CantorTree.subtreeRoot(UBigInt.ZERO, -1) }
        // And a caller that means it can say so.
        assertEquals(UBigInt.of(228), CantorTree.subtreeRoot(UBigInt.ZERO, 2, maxComputeHeight = 2))
    }

    @Test
    fun theLcaHeightIsTheBitLengthOfTheDifference() {
        // §4.5: "h = find_lca_height(v1, v2)", bit_length(v1 XOR v2). Moving
        // from 0 to 5 gives 3, from 4 to 7 gives 2.
        fun axis(v: Long) = CyberspaceAxis(0L, v)
        assertEquals(3, CantorTree.lcaHeight(axis(0), axis(5)))
        assertEquals(2, CantorTree.lcaHeight(axis(4), axis(7)))
        assertEquals(0, CantorTree.lcaHeight(axis(9), axis(9)))
        // Across the 64-bit split, where an axis stops fitting one Long.
        assertEquals(65, CantorTree.lcaHeight(CyberspaceAxis(1L, 0L), CyberspaceAxis(0L, 0L)))
    }
}
