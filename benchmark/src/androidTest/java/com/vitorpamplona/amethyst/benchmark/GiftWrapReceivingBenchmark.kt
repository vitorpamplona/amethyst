package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.crypto.decodeNIP44
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.Gossip
import com.vitorpamplona.quartz.events.NIP24Factory
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will
 * output the result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class GiftWrapReceivingBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    fun createWrap(sender: NostrSigner, receiver: NostrSigner): GiftWrapEvent {
        val countDownLatch = CountDownLatch(1)
        var wrap: GiftWrapEvent? = null

        ChatMessageEvent.create(
            msg = "Hi there! This is a test message",
            to = listOf(receiver.pubKey),
            subject = "Party Tonight",
            replyTos = emptyList(),
            mentions = emptyList(),
            zapReceiver = null,
            markAsSensitive = true,
            zapRaiserAmount = 10000,
            geohash = null,
            signer = sender
        ) {
            SealedGossipEvent.create(
                event = it,
                encryptTo = receiver.pubKey,
                signer = sender
            ) {
                GiftWrapEvent.create(
                    event = it,
                    recipientPubKey = receiver.pubKey
                ) {
                    wrap = it
                    countDownLatch.countDown()
                }
            }
        }

        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        return wrap!!
    }

    fun createSeal(sender: NostrSigner, receiver: NostrSigner): SealedGossipEvent {
        val countDownLatch = CountDownLatch(1)
        var seal: SealedGossipEvent? = null

        ChatMessageEvent.create(
            msg = "Hi there! This is a test message",
            to = listOf(receiver.pubKey),
            subject = "Party Tonight",
            replyTos = emptyList(),
            mentions = emptyList(),
            zapReceiver = null,
            markAsSensitive = true,
            zapRaiserAmount = 10000,
            geohash = null,
            signer = sender
        ) {
            SealedGossipEvent.create(
                event = it,
                encryptTo = receiver.pubKey,
                signer = sender
            ) {
                seal = it
                countDownLatch.countDown()
            }
        }

        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        return seal!!
    }

    @Test
    fun parseWrapFromString() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val str = createWrap(sender, receiver).toJson()

        benchmarkRule.measureRepeated {
            Event.fromJson(str)
        }
    }

    @Test
    fun checkId() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val wrap = createWrap(sender, receiver)

        benchmarkRule.measureRepeated {
            wrap.hasCorrectIDHash()
        }
    }

    @Test
    fun checkSignature() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val wrap = createWrap(sender, receiver)

        benchmarkRule.measureRepeated {
            wrap.hasVerifiedSignature()
        }
    }

    @Test
    fun decodeWrapEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val wrap = createWrap(sender, receiver)

        benchmarkRule.measureRepeated {
            assertNotNull(decodeNIP44(wrap.content))
        }
    }

    @Test
    fun decryptWrapEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val wrap = createWrap(sender, receiver)

        val toDecrypt = decodeNIP44(wrap.content) ?: return

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.decryptNIP44(toDecrypt, sender.keyPair.privKey!!, wrap.pubKey.hexToByteArray()))
        }
    }

    @Test
    fun parseWrappedEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val wrap = createWrap(sender, receiver)

        val toDecrypt = decodeNIP44(wrap.content) ?: return
        val innerJson = CryptoUtils.decryptNIP44(toDecrypt, receiver.keyPair.privKey!!, wrap.pubKey.hexToByteArray())

        benchmarkRule.measureRepeated {
            assertNotNull(innerJson?.let { Event.fromJson(it) })
        }
    }

    @Test
    fun decodeSealEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val seal = createSeal(sender, receiver)

        benchmarkRule.measureRepeated {
            assertNotNull(decodeNIP44(seal.content))
        }
    }

    @Test
    fun decryptSealedEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val seal = createSeal(sender, receiver)

        val toDecrypt = decodeNIP44(seal.content) ?: return

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.decryptNIP44(toDecrypt, sender.keyPair.privKey!!, seal.pubKey.hexToByteArray()))
        }
    }

    @Test
    fun parseSealedEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val seal = createSeal(sender, receiver)

        val toDecrypt = decodeNIP44(seal.content) ?: return
        val innerJson = CryptoUtils.decryptNIP44(toDecrypt, receiver.keyPair.privKey!!, seal.pubKey.hexToByteArray())

        benchmarkRule.measureRepeated {
            assertNotNull(innerJson?.let { Gossip.fromJson(it) })
        }
    }

}