/**
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip49PrivKeyEnc

import java.security.MessageDigest
import java.security.spec.KeySpec
import javax.crypto.SecretKey

/**
 * This class specifies a secret key in a provider-independent fashion.
 *
 *
 * It can be used to construct a `SecretKey` from a byte array,
 * without having to go through a (provider-based)
 * `SecretKeyFactory`.
 *
 *
 * This class is only useful for raw secret keys that can be represented as
 * a byte array and have no key parameters associated with them, e.g., DES or
 * Triple DES keys.
 *
 * @author Jan Luehe
 *
 * @see SecretKey
 *
 * @see javax.crypto.SecretKeyFactory
 *
 * @since 1.4
 */
class SecretKeyOrEmptySpec :
    KeySpec,
    SecretKey {
    /**
     * The secret key.
     *
     * @serial
     */
    private val key: ByteArray

    /**
     * The name of the algorithm associated with this key.
     *
     * @serial
     */
    private val algorithm: String

    /**
     * Constructs a secret key from the given byte array.
     *
     *
     * This constructor does not check if the given bytes indeed specify a
     * secret key of the specified algorithm. For example, if the algorithm is
     * DES, this constructor does not check if `key` is 8 bytes
     * long, and also does not check for weak or semi-weak keys.
     * In order for those checks to be performed, an algorithm-specific
     * *key specification* class
     *
     * @param key the key material of the secret key. The contents of
     * the array are copied to protect against subsequent modification.
     * @param algorithm the name of the secret-key algorithm to be associated
     * with the given key material.
     * See Appendix A in the [
 * Java Cryptography Architecture Reference Guide]({@docRoot}/../technotes/guides/security/crypto/CryptoSpec.html#AppA)
     * for information about standard algorithm names.
     * @exception IllegalArgumentException if `algorithm`
     * is null or `key` is null or empty.
     */
    constructor(key: ByteArray, algorithm: String) {
        this.key = key.clone()
        this.algorithm = algorithm
    }

    /**
     * Returns the name of the algorithm associated with this secret key.
     *
     * @return the secret key algorithm.
     */
    override fun getAlgorithm(): String = this.algorithm

    /**
     * Returns the name of the encoding format for this secret key.
     *
     * @return the string "RAW".
     */
    override fun getFormat(): String = "RAW"

    /**
     * Returns the key material of this secret key.
     *
     * @return the key material. Returns a new array
     * each time this method is called.
     */
    override fun getEncoded(): ByteArray = this.key.clone()

    /**
     * Calculates a hash code value for the object.
     * Objects that are equal will also have the same hashcode.
     */
    override fun hashCode(): Int {
        var retval = 0
        for (i in 1..<this.key.size) {
            retval += this.key[i] * i
        }
        if (this.algorithm.equals("TripleDES", ignoreCase = true)) {
            return (
                "desede"
                    .hashCode()
                    .let {
                        retval = retval xor it
                        retval
                    }
            )
        } else {
            return (
                this.algorithm.lowercase().hashCode().let {
                    retval = retval xor it
                    retval
                }
            )
        }
    }

    /**
     * Tests for equality between the specified object and this
     * object. Two SecretKeySpec objects are considered equal if
     * they are both SecretKey instances which have the
     * same case-insensitive algorithm name and key encoding.
     *
     * @param other the object to test for equality with this object.
     *
     * @return true if the objects are considered equal, false if
     * `obj` is null or otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is SecretKey) return false

        val thatAlg = other.algorithm
        if (!(thatAlg.equals(this.algorithm, ignoreCase = true))) {
            if ((
                    !(thatAlg.equals("DESede", ignoreCase = true)) ||
                        !(this.algorithm.equals("TripleDES", ignoreCase = true))
                ) &&
                (
                    !(thatAlg.equals("TripleDES", ignoreCase = true)) ||
                        !(this.algorithm.equals("DESede", ignoreCase = true))
                )
            ) {
                return false
            }
        }

        val thatKey = other.encoded

        return MessageDigest.isEqual(this.key, thatKey)
    }

    companion object {
        private const val serialVersionUID = 6577238317307289933L
    }
}
