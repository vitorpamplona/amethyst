/**
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
package com.vitorpamplona.quartz.nip03Timestamp.ots.attestation

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip03Timestamp.ots.BlockHeader
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.VerificationException

/**
 * Bitcoin Block Header Attestation.
 * The commitment digest will be the merkleroot of the blockheader.
 * The block height is recorded so that looking up the correct block header in
 * an external block header database doesn't require every header to be stored
 * locally (33MB and counting). (remember that a memory-constrained local
 * client can save an MMR that commits to all blocks, and use an external service to fill
 * in pruned details).
 * Otherwise no additional redundant data about the block header is recorded.
 * This is very intentional: since the attestation contains (nearly) the
 * absolute bare minimum amount of data, we encourage implementations to do
 * the correct thing and get the block header from a by-height index, check
 * that the merkleroots match, and then calculate the time from the header
 * information. Providing more data would encourage implementations to cheat.
 * Remember that the only thing that would invalidate the block height is a
 * reorg, but in the event of a reorg the merkleroot will be invalid anyway,
 * so there's no point to recording data in the attestation like the header
 * itself. At best that would just give us extra confirmation that a reorg
 * made the attestation invalid; reorgs deep enough to invalidate timestamps are
 * exceptionally rare events anyway, so better to just tell the user the timestamp
 * can't be verified rather than add almost-never tested code to handle that case
 * more gracefully.
 *
 * @see TimeAttestation
 */
class BitcoinBlockHeaderAttestation(
    val height: Int,
) : TimeAttestation() {
    override fun tag(): ByteArray = TAG

    override fun serializePayload(ctx: StreamSerializationContext) {
        ctx.writeVaruint(this.height)
    }

    override fun toString(): String = "BitcoinBlockHeaderAttestation(" + this.height + ")"

    override fun compareTo(other: TimeAttestation): Int {
        val ob = other as BitcoinBlockHeaderAttestation

        return this.height - ob.height
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BitcoinBlockHeaderAttestation) {
            return false
        }

        if (!this.tag().contentEquals(other.tag())) {
            return false
        }

        if (this.height != other.height) {
            return false
        }

        return true
    }

    override fun hashCode(): Int = this.tag().contentHashCode() xor this.height

    /**
     * Verify attestation against a Bitcoin block header.
     * @param digest the digest
     * @param block the Bitcoin block header
     * @return the block time on success; raises VerificationError on failure.
     * @throws VerificationException verification exception
     */
    @Throws(VerificationException::class)
    fun verifyAgainstBlockheader(
        digest: ByteArray,
        block: BlockHeader,
    ): Long? {
        if (digest.size != 32) {
            throw VerificationException("Expected digest with length 32 bytes; got " + digest.size + " bytes")
        } else if (!digest.contentEquals(block.merkleRoot.hexToByteArray())) {
            throw VerificationException("Digest ${digest.toHexKey()} does not match merkleroot ${block.merkleRoot}")
        }

        return block.getTime()
    }

    companion object {
        val TAG: ByteArray =
            byteArrayOf(
                0x05.toByte(),
                0x88.toByte(),
                0x96.toByte(),
                0x0d.toByte(),
                0x73.toByte(),
                0xd7.toByte(),
                0x19.toByte(),
                0x01.toByte(),
            )

        val chain: String = "bitcoin"

        fun deserialize(ctxPayload: StreamDeserializationContext): BitcoinBlockHeaderAttestation = BitcoinBlockHeaderAttestation(ctxPayload.readVaruint())
    }
}
