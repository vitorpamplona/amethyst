package com.vitorpamplona.quartz.nip03Timestamp.ots.attestation;

import android.util.Log;

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext;
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils;
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Pending attestations.
 * Commitment has been recorded in a remote calendar for future attestation,
 * and we have a URI to find a more complete timestamp in the future.
 * Nothing other than the URI is recorded, nor is there provision made to add
 * extra metadata (other than the URI) in future upgrades. The rational here
 * is that remote calendars promise to keep commitments indefinitely, so from
 * the moment they are created it should be possible to find the commitment in
 * the calendar. Thus if you're not satisfied with the local verifiability of
 * a timestamp, the correct thing to do is just ask the remote calendar if
 * additional attestations are available and/or when they'll be available.
 * While we could additional metadata like what types of attestations the
 * remote calendar expects to be able to provide in the future, that metadata
 * can easily change in the future too. Given that we don't expect timestamps
 * to normally have more than a small number of remote calendar attestations,
 * it'd be better to have verifiers get the most recent status of such
 * information (possibly with appropriate negative response caching).
 *
 * @see TimeAttestation
 */
public class PendingAttestation extends TimeAttestation {

    public static byte[] _TAG = {(byte) 0x83, (byte) 0xdf, (byte) 0xe3, (byte) 0x0d, (byte) 0x2e, (byte) 0xf9, (byte) 0x0c, (byte) 0x8e};

    @Override
    public byte[] _TAG() {
        return PendingAttestation._TAG;
    }

    public static int _MAX_URI_LENGTH = 1000;

    public static String _ALLOWED_URI_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._/:";

    private byte[] uri;

    public byte[] getUri() {
        return uri;
    }

    public PendingAttestation(byte[] uri_) {
        super();
        this.uri = uri_;
    }

    public static boolean checkUri(byte[] uri) {
        if (uri.length > PendingAttestation._MAX_URI_LENGTH) {
            System.err.print("URI exceeds maximum length");

            return false;
        }

        for (int i = 0; i < uri.length; i++) {
            Character c = String.format("%c", uri[i]).charAt(0);

            if (PendingAttestation._ALLOWED_URI_CHARS.indexOf(c) < 0) {
                Log.e("OpenTimestamp","URI contains invalid character ");

                return false;
            }
        }

        return true;
    }

    public static PendingAttestation deserialize(StreamDeserializationContext ctxPayload)
        throws DeserializationException {

        byte[] utf8Uri;
        try {
            utf8Uri = ctxPayload.readVarbytes(PendingAttestation._MAX_URI_LENGTH);
        } catch (DeserializationException e) {
            Log.e("OpenTimestamp","URI too long and thus invalid: ");
            throw new DeserializationException("Invalid URI: ");
        }

        if (PendingAttestation.checkUri(utf8Uri) == false) {
            Log.e("OpenTimestamp","Invalid URI: ");
            throw new DeserializationException("Invalid URI: ");
        }

        return new PendingAttestation(utf8Uri);
    }

    @Override
    public void serializePayload(StreamSerializationContext ctx) {
        ctx.writeVarbytes(this.uri);
    }

    public String toString() {
        return "PendingAttestation(\'" + new String(this.uri, StandardCharsets.UTF_8) + "\')";
    }

    @Override
    public int compareTo(TimeAttestation o) {
        PendingAttestation opa = (PendingAttestation) o;

        return Utils.compare(this.uri, opa.uri);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PendingAttestation)) {
            return false;
        }

        if (!Arrays.equals(this._TAG(), ((PendingAttestation) obj)._TAG())) {
            return false;
        }

        if (!Arrays.equals(this.uri, ((PendingAttestation) obj).uri)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this._TAG()) ^ Arrays.hashCode(this.uri);
    }
}
