package com.vitorpamplona.quartz.nip03Timestamp.ots.attestation;

import com.vitorpamplona.quartz.nip03Timestamp.ots.BlockHeader;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.VerificationException;

import java.util.Arrays;

/**
 * Litecoin Block Header Attestation.
 *
 * @see TimeAttestation
 */
public class LitecoinBlockHeaderAttestation extends TimeAttestation {

    public static byte[] _TAG = {(byte) 0x06, (byte) 0x86, (byte) 0x9a, (byte) 0x0d, (byte) 0x73, (byte) 0xd7, (byte) 0x1b, (byte) 0x45};

    public static String chain = "litecoin";

    @Override
    public byte[] _TAG() {
        return LitecoinBlockHeaderAttestation._TAG;
    }

    private int height = 0;

    public int getHeight() {
        return height;
    }

    public LitecoinBlockHeaderAttestation(int height_) {
        super();
        this.height = height_;
    }

    public static LitecoinBlockHeaderAttestation deserialize(StreamDeserializationContext ctxPayload) {
        int height = ctxPayload.readVaruint();

        return new LitecoinBlockHeaderAttestation(height);
    }

    @Override
    public void serializePayload(StreamSerializationContext ctx) {
        ctx.writeVaruint(this.height);
    }

    public String toString() {
        return "LitecoinBlockHeaderAttestation(" + this.height + ")";
    }

    @Override
    public int compareTo(TimeAttestation o) {
        LitecoinBlockHeaderAttestation ob = (LitecoinBlockHeaderAttestation) o;

        return this.height - ob.height;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LitecoinBlockHeaderAttestation)) {
            return false;
        }

        if (!Arrays.equals(this._TAG(), ((LitecoinBlockHeaderAttestation) obj)._TAG())) {
            return false;
        }

        if (this.height != ((LitecoinBlockHeaderAttestation) obj).height) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this._TAG()) ^ this.height;
    }

    /**
     * Verify attestation against a Litecoin block header.
     * @param digest the digest
     * @param block the Litecoin block header
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
