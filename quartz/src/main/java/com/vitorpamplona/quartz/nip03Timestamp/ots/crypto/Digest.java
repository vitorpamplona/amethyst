package com.vitorpamplona.quartz.nip03Timestamp.ots.crypto;

/**
 * Message digest interface
 */
public interface Digest {
    /**
     * Return the algorithm name
     *
     * @return the algorithm name
     */
    public String getAlgorithmName();

    /**
     * Return the size, in bytes, of the digest produced by this message digest.
     *
     * @return the size, in bytes, of the digest produced by this message digest.
     */
    public int getDigestSize();

    /**
     * Update the message digest with a single byte.
     *
     * @param in the input byte to be entered.
     */
    public void update(byte in);

    /**
     * Update the message digest with a block of bytes.
     *
     * @param in    the byte array containing the data.
     * @param inOff the offset into the byte array where the data starts.
     * @param len   the length of the data.
     */
    public void update(byte[] in, int inOff, int len);

    /**
     * Close the digest, producing the final digest value. The doFinal
     * call also resets the digest.
     *
     * @param out    the array the digest is to be copied into.
     * @param outOff the offset into the out array the digest is to start at.
     * @return something
     * @see #reset()
     */
    public int doFinal(byte[] out, int outOff);

    /**
     * Reset the digest back to it's initial state.
     */
    public void reset();
}
