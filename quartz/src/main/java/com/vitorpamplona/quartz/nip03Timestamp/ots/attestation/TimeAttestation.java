package com.vitorpamplona.quartz.nip03Timestamp.ots.attestation;

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.Timestamp;
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException;

import java.util.Arrays;

/**
 * Class representing {@link Timestamp} signature verification
 */
public abstract class TimeAttestation implements Comparable<TimeAttestation> {

    public static int _TAG_SIZE = 8;

    public static int _MAX_PAYLOAD_SIZE = 8192;

    public byte[] _TAG;

    public byte[] _TAG() {
        return new byte[]{};
    }

    /**
     * Deserialize a general Time Attestation to the specific subclass Attestation.
     *
     * @param ctx The stream deserialization context.
     * @return The specific subclass Attestation.
     */
    public static TimeAttestation deserialize(StreamDeserializationContext ctx) throws DeserializationException {
        // console.log('attestation deserialize');

        byte[] tag = ctx.readBytes(_TAG_SIZE);
        // console.log('tag: ', com.vitorpamplona.quartz.ots.Utils.bytesToHex(tag));

        byte[] serializedAttestation = ctx.readVarbytes(_MAX_PAYLOAD_SIZE);
        // console.log('serializedAttestation: ', com.vitorpamplona.quartz.ots.Utils.bytesToHex(serializedAttestation));

        StreamDeserializationContext ctxPayload = new StreamDeserializationContext(serializedAttestation);

        /* eslint no-use-before-define: ["error", { "classes": false }] */
        if (Arrays.equals(tag, PendingAttestation._TAG) == true) {
            return PendingAttestation.deserialize(ctxPayload);
        } else if (Arrays.equals(tag, BitcoinBlockHeaderAttestation._TAG) == true) {
            return BitcoinBlockHeaderAttestation.deserialize(ctxPayload);
        } else if (Arrays.equals(tag, LitecoinBlockHeaderAttestation._TAG) == true) {
            return LitecoinBlockHeaderAttestation.deserialize(ctxPayload);
        } else if (Arrays.equals(tag, EthereumBlockHeaderAttestation._TAG) == true) {
            return EthereumBlockHeaderAttestation.deserialize(ctxPayload);
        }

        return new UnknownAttestation(tag, serializedAttestation);
    }

    /**
     * Serialize a a general Time Attestation to the specific subclass Attestation.
     *
     * @param ctx The output stream serialization context.
     */
    public void serialize(StreamSerializationContext ctx) {
        ctx.writeBytes(this._TAG());
        StreamSerializationContext ctxPayload = new StreamSerializationContext();
        serializePayload(ctxPayload);
        ctx.writeVarbytes(ctxPayload.getOutput());
    }

    public void serializePayload(StreamSerializationContext ctxPayload) {
        // TODO: Is this intentional?
    }

    @Override
    public int compareTo(TimeAttestation o) {
        int deltaTag = Utils.compare(this._TAG(), o._TAG());

        if (deltaTag == 0) {
            return this.compareTo(o);
        } else {
            return deltaTag;
        }
    }
}
