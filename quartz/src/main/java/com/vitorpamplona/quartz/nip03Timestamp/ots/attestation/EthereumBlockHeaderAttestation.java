package com.vitorpamplona.quartz.nip03Timestamp.ots.attestation;

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext;

import java.util.Arrays;

/**
 * Ethereum Block Header Attestation.
 *
 * @see TimeAttestation
 */
public class EthereumBlockHeaderAttestation extends TimeAttestation {

    public static byte[] _TAG = {(byte) 0x30, (byte) 0xfe, (byte) 0x80, (byte) 0x87, (byte) 0xb5, (byte) 0xc7, (byte) 0xea, (byte) 0xd7};
    public static String chain = "ethereum";

    @Override
    public byte[] _TAG() {
        return EthereumBlockHeaderAttestation._TAG;
    }

    private int height = 0;

    public int getHeight() {
        return height;
    }

    EthereumBlockHeaderAttestation(int height_) {
        super();
        this.height = height_;
    }

    public static EthereumBlockHeaderAttestation deserialize(StreamDeserializationContext ctxPayload) {
        int height = ctxPayload.readVaruint();

        return new EthereumBlockHeaderAttestation(height);
    }

    @Override
    public void serializePayload(StreamSerializationContext ctx) {
        ctx.writeVaruint(this.height);
    }

    public String toString() {
        return "EthereumBlockHeaderAttestation(" + this.height + ")";
    }

    @Override
    public int compareTo(TimeAttestation o) {
        EthereumBlockHeaderAttestation ob = (EthereumBlockHeaderAttestation) o;

        return this.height - ob.height;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EthereumBlockHeaderAttestation)) {
            return false;
        }

        if (!Arrays.equals(this._TAG(), ((EthereumBlockHeaderAttestation) obj)._TAG())) {
            return false;
        }

        if (this.height != ((EthereumBlockHeaderAttestation) obj).height) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this._TAG()) ^ this.height;
    }
}
