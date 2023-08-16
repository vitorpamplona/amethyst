package com.vitorpamplona.quartz.crypto

import com.vitorpamplona.quartz.encoders.toHexKey

class KeyPair(
    privKey: ByteArray? = null,
    pubKey: ByteArray? = null
) {
    val privKey: ByteArray?
    val pubKey: ByteArray

    init {
        if (privKey == null) {
            if (pubKey == null) {
                // create new, random keys
                this.privKey = CryptoUtils.privkeyCreate()
                this.pubKey = CryptoUtils.pubkeyCreate(this.privKey)
            } else {
                // this is a read-only account
                check(pubKey.size == 32)
                this.privKey = null
                this.pubKey = pubKey
            }
        } else {
            // as private key is provided, ignore the public key and set keys according to private key
            this.privKey = privKey
            this.pubKey = CryptoUtils.pubkeyCreate(privKey)
        }
    }

    override fun toString(): String {
        return "KeyPair(privateKey=${privKey?.toHexKey()}, publicKey=${pubKey.toHexKey()}"
    }
}
