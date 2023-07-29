package com.vitorpamplona.amethyst

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils
import com.vitorpamplona.amethyst.service.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CryptoUtilsTest {
    @Test()
    fun testSharedSecret() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val sharedSecret1 = CryptoUtils.getSharedSecret(sender.privKey!!, receiver.pubKey)
        val sharedSecret2 = CryptoUtils.getSharedSecret(receiver.privKey!!, sender.pubKey)

        assertEquals(sharedSecret1.toHexKey(), sharedSecret2.toHexKey())

        val secretKey1 = KeyPair(privKey = sharedSecret1)
        val secretKey2 = KeyPair(privKey = sharedSecret2)

        assertEquals(secretKey1.pubKey.toHexKey(), secretKey2.pubKey.toHexKey())
        assertEquals(secretKey1.privKey?.toHexKey(), secretKey2.privKey?.toHexKey())
    }

    @Test
    fun encryptDecryptNIP4Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)

        val encrypted = CryptoUtils.encrypt(msg, privateKey, publicKey)
        val decrypted = CryptoUtils.decrypt(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptDecryptNIP24Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)

        val encrypted = CryptoUtils.encryptXChaCha(msg, privateKey, publicKey)
        val decrypted = CryptoUtils.decryptXChaCha(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptSharedSecretDecryptNIP4Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)
        val sharedSecret = CryptoUtils.getSharedSecret(privateKey, publicKey)

        val encrypted = CryptoUtils.encrypt(msg, sharedSecret)
        val decrypted = CryptoUtils.decrypt(encrypted, sharedSecret)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptSharedSecretDecryptNIP24Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)
        val sharedSecret = CryptoUtils.getSharedSecret(privateKey, publicKey)

        val encrypted = CryptoUtils.encryptXChaCha(msg, sharedSecret)
        val decrypted = CryptoUtils.decryptXChaCha(encrypted, sharedSecret)

        assertEquals(msg, decrypted)
    }
}
