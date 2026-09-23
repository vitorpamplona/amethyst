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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.bigint.UBigInt
import com.vitorpamplona.quartz.utils.sha256.sha256

/** What deriving a region at one height produces (`CYBERSPACE_V2.md` §7.2). */
class RegionKeyMaterial(
    val height: Int,
    /** §4.7's stable spatial region integer. */
    val regionN: UBigInt,
    /** The 32 bytes a bag's payload is encrypted under. Not to be published. */
    val decryptionKey: ByteArray,
    /** The `d` tag a bag carrying this region is addressed by. Safe to publish. */
    val lookupId: HexKey,
)

/**
 * `CYBERSPACE_V2.md` §7.2 — turning a place into a key.
 *
 * ```
 * region_bytes            = int_to_bytes_be_min(region_n)
 * location_decryption_key = sha256(region_bytes)
 * lookup_id               = sha256(location_decryption_key)
 * ```
 *
 * **Two layers, and the second one is the design.** The lookup id is published
 * so that people can find the content; it is a hash *of* the key, so seeing it
 * buys nothing without the region preimage. §7.2: "Seeing `lookup_id` does not
 * allow deriving `location_decryption_key` without the region preimage. The
 * lookup ID is safe to publish; the decryption key requires work."
 *
 * Note what is deliberately absent: the temporal axis that hop proofs use (§5)
 * is not part of this. Location identifiers stay a stable function of space, so
 * they do not change when somebody walks through.
 */
object RegionKey {
    /**
     * §7.4: the region integer for the aligned cube of [height] holding [point].
     *
     * The three axes are independent all the way to the combine, which is why a
     * §7.7 sweep of a box costs one tree per distinct base *per axis* rather
     * than one per candidate region.
     */
    fun regionN(
        point: CyberspacePoint,
        height: Int,
        maxComputeHeight: Int = CantorTree.DEFAULT_MAX_COMPUTE_HEIGHT,
    ): UBigInt {
        val base = point.alignedBase(height)
        val x = CantorTree.subtreeRoot(base.x.toUBigInt(), height, maxComputeHeight)
        val y = CantorTree.subtreeRoot(base.y.toUBigInt(), height, maxComputeHeight)
        val z = CantorTree.subtreeRoot(base.z.toUBigInt(), height, maxComputeHeight)
        // §4.7: region_n = pi(pi(cantor_x, cantor_y), cantor_z).
        return CantorTree.cantorPair(CantorTree.cantorPair(x, y), z)
    }

    /** The key and the lookup id a region integer yields. */
    fun derive(
        regionN: UBigInt,
        height: Int = 0,
    ): RegionKeyMaterial {
        val key = sha256(regionN.toMinimalBytes())
        return RegionKeyMaterial(height, regionN, key, sha256(key).toHexKey())
    }

    /** Both halves at once, for the common case. */
    fun at(
        point: CyberspacePoint,
        height: Int,
        maxComputeHeight: Int = CantorTree.DEFAULT_MAX_COMPUTE_HEIGHT,
    ): RegionKeyMaterial = derive(regionN(point, height, maxComputeHeight), height)
}

/**
 * This axis as a number the Cantor tree can add to.
 *
 * The one conversion from the split representation into arbitrary precision,
 * done at the boundary where arithmetic starts and nowhere earlier — the same
 * rule the SNO lattice follows for the same reason.
 */
fun CyberspaceAxis.toUBigInt(): UBigInt {
    val bytes = ByteArray(16)
    for (i in 0..7) {
        bytes[i] = (high ushr (56 - i * 8)).toByte()
        bytes[8 + i] = (low ushr (56 - i * 8)).toByte()
    }
    return UBigInt.ofBytes(bytes)
}
