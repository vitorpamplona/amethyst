package com.vitorpamplona.quartz.nip03Timestamp.ots.op;

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;

/**
 * Cryptographic SHA1 operation.
 * Cryptographic operation tag numbers taken from RFC4880, although it's not
 * guaranteed that they'll continue to match that RFC in the future.
 * Remember that for timestamping, hash algorithms with collision attacks
 * *are* secure! We've still proven that both messages existed prior to some
 * point in time - the fact that they both have the same hash digest doesn't
 * change that.
 * Heck, even md5 is still secure enough for timestamping... but that's
 * pushing our luck...
 *
 * @see OpCrypto
 */
public class OpSHA1 extends OpCrypto {


    public static byte _TAG = 0x02;

    @Override
    public byte _TAG() {
        return OpSHA1._TAG;
    }

    @Override
    public String _TAG_NAME() {
        return "sha1";
    }

    @Override
    public String _HASHLIB_NAME() {
        return "SHA-1";
    }

    @Override
    public int _DIGEST_LENGTH() {
        return 20;
    }

    public OpSHA1() {
        super();
    }

    @Override
    public byte[] call(byte[] msg) {
        return super.call(msg);
    }

    public static Op deserializeFromTag(StreamDeserializationContext ctx, byte tag) {
        return OpCrypto.deserializeFromTag(ctx, tag);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof OpSHA1);
    }

    @Override
    public int hashCode() {
        return _TAG;
    }
}
