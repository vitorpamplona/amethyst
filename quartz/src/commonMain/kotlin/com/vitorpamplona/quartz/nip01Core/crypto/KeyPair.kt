/*
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
package com.vitorpamplona.quartz.nip01Core.crypto

import com.vitorpamplona.quartz.nip01Core.core.toHexKey

class KeyPair(
    privKey: ByteArray? = null,
    pubKey: ByteArray? = null,
    forceReplacePubkey: Boolean = true,
) {
    val privKey: ByteArray?
    val pubKey: ByteArray

    init {
        if (privKey == null) {
            if (pubKey == null) {
                // create new, random keys
                this.privKey = Nip01Crypto.privKeyCreate()
                this.pubKey = Nip01Crypto.pubKeyCreate(this.privKey)
            } else {
                // this is a read-only account
                check(pubKey.size == 32)
                this.privKey = null
                this.pubKey = pubKey
            }
        } else {
            // as private key is provided, ignore the public key and set keys according to private key
            this.privKey = privKey
            if (pubKey == null || forceReplacePubkey) {
                this.pubKey = Nip01Crypto.pubKeyCreate(privKey)
            } else {
                this.pubKey = pubKey
            }
        }
    }

    override fun toString(): String = "KeyPair(privateKey=${privKey?.toHexKey()}, publicKey=${pubKey.toHexKey()}"
}
