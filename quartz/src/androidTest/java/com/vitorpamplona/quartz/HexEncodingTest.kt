package com.vitorpamplona.quartz

import com.vitorpamplona.quartz.crypto.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class HexEncodingTest {

    val TestHex = "48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf"

    @Test
    fun testHexEncodeDecodeOurs() {
        assertEquals(
            TestHex,
            com.vitorpamplona.quartz.encoders.Hex.encode(
                com.vitorpamplona.quartz.encoders.Hex.decode(TestHex)
            )
        )
    }

    @Test
    fun testHexEncodeDecodeSecp256k1() {
        assertEquals(
            TestHex,
            fr.acinq.secp256k1.Hex.encode(
                fr.acinq.secp256k1.Hex.decode(TestHex)
            )
        )
    }

    @Test
    fun testRandoms() {
        for (i in 0..1000) {
            val bytes = CryptoUtils.privkeyCreate()
            val hex = fr.acinq.secp256k1.Hex.encode(bytes)
            assertEquals(
                fr.acinq.secp256k1.Hex.encode(bytes),
                com.vitorpamplona.quartz.encoders.Hex.encode(bytes)
            )
            assertEquals(
                bytes.toList(),
                com.vitorpamplona.quartz.encoders.Hex.decode(hex).toList()
            )
        }
    }

}