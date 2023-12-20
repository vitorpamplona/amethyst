package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import junit.framework.TestCase.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CryptoBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun getSharedKeyNip04() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.getSharedSecretNIP04(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun getSharedKeyNip44() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.getSharedSecretNIP44v1(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun computeSharedKeyNip04() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.computeSharedSecretNIP04(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun computeSharedKeyNip44() {
        val keyPair1 = KeyPair()
        val keyPair2 = KeyPair()

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.computeSharedSecretNIP44v1(keyPair1.privKey!!, keyPair2.pubKey))
        }
    }

    @Test
    fun random() {
        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.random(1000))
        }
    }

    @Test
    fun sha256() {
        val keyPair = KeyPair()

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.sha256(keyPair.pubKey))
        }
    }

    @Test
    fun sign() {
        val keyPair = KeyPair()
        val msg = CryptoUtils.sha256(CryptoUtils.random(1000))

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.sign(msg, keyPair.privKey!!))
        }
    }

    @Test
    fun verify() {
        val keyPair = KeyPair()
        val msg = CryptoUtils.sha256(CryptoUtils.random(1000))
        val signature = CryptoUtils.sign(msg, keyPair.privKey!!)

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.verifySignature(signature, msg, keyPair.pubKey))
        }
    }

}