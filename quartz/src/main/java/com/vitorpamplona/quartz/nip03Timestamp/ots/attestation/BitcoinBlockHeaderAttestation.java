package com.vitorpamplona.quartz.nip03Timestamp.ots.attestation;

import com.vitorpamplona.quartz.nip03Timestamp.ots.BlockHeader;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.VerificationException;

import java.util.Arrays;

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
public class BitcoinBlockHeaderAttestation extends TimeAttestation {

    public static byte[] _TAG = {(byte) 0x05, (byte) 0x88, (byte) 0x96, (byte) 0x0d, (byte) 0x73, (byte) 0xd7, (byte) 0x19, (byte) 0x01};
    public static String chain = "bitcoin";

    @Override
    public byte[] _TAG() {
        return BitcoinBlockHeaderAttestation._TAG;
    }

    private int height = 0;

    public int getHeight() {
        return height;
    }

    public BitcoinBlockHeaderAttestation(int height_) {
        super();
        this.height = height_;
    }

    public static BitcoinBlockHeaderAttestation deserialize(StreamDeserializationContext ctxPayload) {
        int height = ctxPayload.readVaruint();

        return new BitcoinBlockHeaderAttestation(height);
    }

    @Override
    public void serializePayload(StreamSerializationContext ctx) {
        ctx.writeVaruint(this.height);
    }

    public String toString() {
        return "BitcoinBlockHeaderAttestation(" + this.height + ")";
    }

    @Override
    public int compareTo(TimeAttestation o) {
        BitcoinBlockHeaderAttestation ob = (BitcoinBlockHeaderAttestation) o;

        return this.height - ob.height;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BitcoinBlockHeaderAttestation)) {
            return false;
        }

        if (!Arrays.equals(this._TAG(), ((BitcoinBlockHeaderAttestation) obj)._TAG())) {
            return false;
        }

        if (this.height != ((BitcoinBlockHeaderAttestation) obj).height) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this._TAG()) ^ this.height;
    }

    /**
     * Verify attestation against a Bitcoin block header.
     * @param digest the digest
     * @param block the Bitcoin block header
     * @return the block time on success; raises VerificationError on failure.
     * @throws VerificationException verification exception
     */
    public Long verifyAgainstBlockheader(byte[] digest, BlockHeader block) throws VerificationException {
        if (digest.length != 32) {
            throw new VerificationException("Expected digest with length 32 bytes; got " + digest.length + " bytes");
        } else if (!Arrays.equals(digest, Utils.hexToBytes(block.getMerkleroot()))) {
            throw new VerificationException("Digest does not match merkleroot");
        }

        return block.getTime();
    }
}
