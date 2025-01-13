package com.vitorpamplona.quartz.nip03Timestamp.ots;

import com.vitorpamplona.quartz.utils.Hex;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpCrypto;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpKECCAK256;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpRIPEMD160;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA1;
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA256;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

public class Hash {

    private byte[] value;
    private byte algorithm;

    /**
     * Create a Hash object.
     *
     * @param value     - The byte array of the hash
     * @param algorithm - The hashlib tag of crypto operation
     */
    public Hash(byte[] value, byte algorithm) {
        this.value = value;
        this.algorithm = algorithm;
    }

    /**
     * Create a Hash object.
     *
     * @param value - The byte array of the hash
     * @param label - The hashlib name of crypto operation
     */
    public Hash(byte[] value, String label) {
        this.value = value;
        this.algorithm = getOp(label)._TAG();
    }

    /**
     * Get Value.
     *
     * @return value - The hash in byte array.
     */
    public byte[] getValue() {
        return value;
    }

    /**
     * Set Value tag.
     *
     * @param value - The hash in byte array.
     */
    public void setValue(byte[] value) {
        this.value = value;
    }

    /**
     * Get Algorithm tag.
     *
     * @return algorithm - The algorithm tag of crypto operation.
     */
    public byte getAlgorithm() {
        return algorithm;
    }

    /**
     * Set Algorithm tag.
     *
     * @param algorithm - The algorithm tag of crypto operation.
     */
    public void setAlgorithm(byte algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Get the current Crypto operation.
     *
     * @return The generated com.vitorpamplona.quartz.ots.OpCrypto object.
     */
    public OpCrypto getOp() {
        if (this.algorithm == OpSHA1._TAG) {
            return new OpSHA1();
        } else if (this.algorithm == OpSHA256._TAG) {
            return new OpSHA256();
        } else if (this.algorithm == OpRIPEMD160._TAG) {
            return new OpRIPEMD160();
        } else if (this.algorithm == OpKECCAK256._TAG) {
            return new OpKECCAK256();
        }

        return new OpSHA256();
    }

    /**
     * Get Crypto operation from hashlib tag.
     *
     * @param algorithm The hashlib tag.
     * @return The generated com.vitorpamplona.quartz.ots.OpCrypto object.
     */
    public static OpCrypto getOp(byte algorithm) {
        if (algorithm == OpSHA1._TAG) {
            return new OpSHA1();
        } else if (algorithm == OpSHA256._TAG) {
            return new OpSHA256();
        } else if (algorithm == OpRIPEMD160._TAG) {
            return new OpRIPEMD160();
        } else if (algorithm == OpKECCAK256._TAG) {
            return new OpKECCAK256();
        }

        return new OpSHA256();
    }

    /**
     * Get Crypto operation from hashlib name.
     *
     * @param label The hashlib name.
     * @return The generated com.vitorpamplona.quartz.ots.OpCrypto object.
     */
    public static OpCrypto getOp(String label) {
        if (label.toLowerCase().equals(new OpSHA1()._TAG_NAME())) {
            return new OpSHA1();
        } else if (label.toLowerCase().equals(new OpSHA256()._TAG_NAME())) {
            return new OpSHA256();
        } else if (label.toLowerCase().equals(new OpRIPEMD160()._TAG_NAME())) {
            return new OpRIPEMD160();
        } else if (label.toLowerCase().equals(new OpKECCAK256()._TAG_NAME())) {
            return new OpKECCAK256();
        }

        return new OpSHA256();
    }

    /**
     * Build hash from data.
     *
     * @param bytes     The byte array of data to hash.
     * @param algorithm The hash file.
     * @return The generated com.vitorpamplona.quartz.ots.Hash object.
     * @throws IOException              desc
     * @throws NoSuchAlgorithmException desc
     */
    public static Hash from(byte[] bytes, byte algorithm) throws IOException, NoSuchAlgorithmException {
        OpCrypto opCrypto = getOp(algorithm);
        byte[] value = opCrypto.hashFd(bytes);

        return new Hash(value, algorithm);
    }

    /**
     * Build hash from File.
     *
     * @param file      The File of data to hash.
     * @param algorithm The hash file.
     * @return The generated com.vitorpamplona.quartz.ots.Hash object.
     * @throws IOException              desc
     * @throws NoSuchAlgorithmException desc
     */
    public static Hash from(File file, byte algorithm) throws IOException, NoSuchAlgorithmException {
        OpCrypto opCrypto = getOp(algorithm);
        byte[] value = opCrypto.hashFd(file);

        return new Hash(value, algorithm);
    }

    /**
     * Build hash from InputStream.
     *
     * @param inputStream The InputStream of data to hash.
     * @param algorithm   The hash file.
     * @return The generated com.vitorpamplona.quartz.ots.Hash object.
     * @throws IOException              desc
     * @throws NoSuchAlgorithmException desc
     */
    public static Hash from(InputStream inputStream, byte algorithm) throws IOException, NoSuchAlgorithmException {
        OpCrypto opCrypto = getOp(algorithm);
        byte[] value = opCrypto.hashFd(inputStream);

        return new Hash(value, algorithm);
    }

    /**
     * Print the object.
     *
     * @return The output.
     */
    @Override
    public String toString() {
        String output = "com.vitorpamplona.quartz.ots.Hash\n";
        output += "algorithm: " + this.getOp()._HASHLIB_NAME() + '\n';
        output += "value: " + Hex.encode(this.value) + '\n';

        return output;
    }
}
