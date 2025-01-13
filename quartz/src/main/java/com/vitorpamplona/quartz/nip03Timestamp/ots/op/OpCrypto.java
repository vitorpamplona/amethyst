package com.vitorpamplona.quartz.nip03Timestamp.ots.op;

import android.util.Log;

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cryptographic transformations.
 * These transformations have the unique property that for any length message,
 * the size of the result they return is fixed. Additionally, they're the only
 * type of operation that can be applied directly to a stream.
 *
 * @see OpUnary
 */
public class OpCrypto extends OpUnary {

    public String _TAG_NAME = "";

    public String _HASHLIB_NAME() {
        return "";
    }

    public int _DIGEST_LENGTH() {
        return 0;
    }

    OpCrypto() {
        super();
    }

    public static Op deserializeFromTag(StreamDeserializationContext ctx, byte tag) {
        return OpUnary.deserializeFromTag(ctx, tag);
    }

    @Override
    public byte[] call(byte[] msg) {
        // For Sha1 & Sha256 use java.security.MessageDigest library
        try {
            MessageDigest digest = MessageDigest.getInstance(this._HASHLIB_NAME());
            byte[] hash = digest.digest(msg);

            return hash;
        } catch (NoSuchAlgorithmException e) {
            Log.e("OpenTimestamp", "NoSuchAlgorithmException");
            e.printStackTrace();

            return new byte[]{};     // TODO: Is this OK? Won't it blow up later? Better to throw?
        }
    }

    public byte[] hashFd(StreamDeserializationContext ctx) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(this._HASHLIB_NAME());
        byte[] chunk = ctx.read(1048576);

        while (chunk != null && chunk.length > 0) {
            digest.update(chunk);
            chunk = ctx.read(1048576);
        }

        byte[] hash = digest.digest();

        return hash;
    }

    public byte[] hashFd(File file) throws IOException, NoSuchAlgorithmException {
        return hashFd(new FileInputStream(file));
    }

    public byte[] hashFd(byte[] bytes) throws IOException, NoSuchAlgorithmException {
        StreamDeserializationContext ctx = new StreamDeserializationContext(bytes);

        return hashFd(ctx);
    }

    public byte[] hashFd(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(this._HASHLIB_NAME());
        byte[] chunk = new byte[1048576];
        int count = inputStream.read(chunk, 0, 1048576);

        while (count >= 0) {
            digest.update(chunk, 0, count);
            count = inputStream.read(chunk, 0, 1048576);
        }

        inputStream.close();
        byte[] hash = digest.digest();

        return hash;
    }
}
