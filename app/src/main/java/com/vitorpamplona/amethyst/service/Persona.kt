package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.toHexKey

class Persona(
    privKey: ByteArray? = null,
    pubKey: ByteArray? = null
) {
    val privKey: ByteArray?
    val pubKey: ByteArray

    init {
        if (privKey == null) {
            if (pubKey == null) {
                // create new, random keys
                this.privKey = Utils.privkeyCreate()
                this.pubKey = Utils.pubkeyCreate(this.privKey)
            } else {
                // this is a read-only account
                check(pubKey.size == 32)
                this.privKey = null
                this.pubKey = pubKey
            }
        } else {
            // as private key is provided, ignore the public key and set keys according to private key
            this.privKey = privKey
            this.pubKey = Utils.pubkeyCreate(privKey)
        }
    }

    override fun toString(): String {
        return "Persona(privateKey=${privKey?.toHexKey()}, publicKey=${pubKey.toHexKey()}"
    }
}
