package com.vitorpamplona.amethyst

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils
import com.vitorpamplona.amethyst.service.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CryptoUtilsTest {

    @Test
    fun testGetPublicFromPrivateKey() {
        val privateKey = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
        assertEquals("7d4b8806f1fd713c287235411bf95aa81b7242ead892733ec84b3f2719845be6", publicKey)
    }

    @Test
    fun testSharedSecretCompatibilityWithCoracle() {
        val privateKey = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561"
        val publicKey = "765cd7cf91d3ad07423d114d5a39c61d52b2cdbc18ba055ddbbeec71fbe2aa2f"

        val key = CryptoUtils.getSharedSecretNIP24(privateKey = privateKey.hexToByteArray(), pubKey = publicKey.hexToByteArray())

        assertEquals("577c966f499dddd8e8dcc34e8f352e283cc177e53ae372794947e0b8ede7cfd8", key.toHexKey())
    }

    @Test
    fun testSharedSecret() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val sharedSecret1 = CryptoUtils.getSharedSecretNIP24(sender.privKey!!, receiver.pubKey)
        val sharedSecret2 = CryptoUtils.getSharedSecretNIP24(receiver.privKey!!, sender.pubKey)

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

        val encrypted = CryptoUtils.encryptNIP04(msg, privateKey, publicKey)
        val decrypted = CryptoUtils.decryptNIP04(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptDecryptNIP4WithJsonSchemaTest() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)

        val encrypted = CryptoUtils.encryptNIP04Json(msg, privateKey, publicKey)
        val decrypted = CryptoUtils.decryptNIP04(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptDecryptNIP24Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)

        val encrypted = CryptoUtils.encryptNIP24(msg, privateKey, publicKey)
        val decrypted = CryptoUtils.decryptNIP24(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptSharedSecretDecryptNIP4Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)

        val encrypted = CryptoUtils.encryptNIP04(msg, privateKey, publicKey)
        val decrypted = CryptoUtils.decryptNIP04(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptSharedSecretDecryptNIP24Test() {
        val msg = "Hi"

        val privateKey = CryptoUtils.privkeyCreate()
        val publicKey = CryptoUtils.pubkeyCreate(privateKey)
        val sharedSecret = CryptoUtils.getSharedSecretNIP24(privateKey, publicKey)

        val encrypted = CryptoUtils.encryptNIP24(msg, sharedSecret)
        val decrypted = CryptoUtils.decryptNIP24(encrypted, sharedSecret)

        assertEquals(msg, decrypted)
    }
}
