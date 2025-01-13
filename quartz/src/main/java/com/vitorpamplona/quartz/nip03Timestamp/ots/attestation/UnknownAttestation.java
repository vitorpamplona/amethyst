package com.vitorpamplona.quartz.nip03Timestamp.ots.attestation;

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException;

import java.util.Arrays;

/**
 * Placeholder for attestations that don't support
 *
 * @see TimeAttestation
 */
public class UnknownAttestation extends TimeAttestation {

    byte[] payload;

    public static byte[] _TAG = new byte[]{};

    @Override
    public byte[] _TAG() {
        return _TAG;
    }

    UnknownAttestation(byte[] tag, byte[] payload) {
        super();
        this._TAG = tag;
        this.payload = payload;
    }

    @Override
    public void serializePayload(StreamSerializationContext ctx) {
        ctx.writeBytes(this.payload);
    }

    public static UnknownAttestation deserialize(StreamDeserializationContext ctxPayload, byte[] tag) throws DeserializationException {
        byte[] payload = ctxPayload.readVarbytes(_MAX_PAYLOAD_SIZE);

        return new UnknownAttestation(tag, payload);
    }

    public String toString() {
        return "UnknownAttestation " + Utils.bytesToHex(this._TAG()) + ' ' + Utils.bytesToHex(this.payload);
    }

    @Override
    public int compareTo(TimeAttestation o) {
        UnknownAttestation ota = (UnknownAttestation) o;

        return Utils.compare(this.payload, ota.payload);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UnknownAttestation)) {
            return false;
        }

        if (!Arrays.equals(this._TAG(), ((UnknownAttestation) obj)._TAG())) {
            return false;
        }

        if (!Arrays.equals(this.payload, ((UnknownAttestation) obj).payload)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this._TAG()) ^ Arrays.hashCode(this.payload);
    }
}
