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
import com.vitorpamplona.quartz.events.SealedGossipEvent
import junit.framework.TestCase.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    fun createMessage(sender: KeyPair, receiver: KeyPair): ChatMessageEvent {
        val to = listOf(receiver.pubKey.toHexKey())

        return ChatMessageEvent.create(
            msg = "Hi there! This is a test message",
            to = to,
            subject = "Party Tonight",
            replyTos = emptyList(),
            mentions = emptyList(),
            zapReceiver = null,
            markAsSensitive = true,
            zapRaiserAmount = 10000,
            geohash = null,
            keyPair = sender
        )
    }

    @Test
    fun parseWrapFromString() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderPublicKey = CryptoUtils.pubkeyCreate(sender.privKey!!).toHexKey()

        val senderMessage = createMessage(sender, receiver)

        val seal = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = senderPublicKey,
            privateKey = sender.privKey!!
        )

        val wrap = GiftWrapEvent.create(
            event = seal,
            recipientPubKey = senderPublicKey
        )

        val str = wrap.toJson()

        benchmarkRule.measureRepeated {
            Event.fromJson(str)
        }
    }

    @Test
    fun checkId() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderPublicKey = CryptoUtils.pubkeyCreate(sender.privKey!!).toHexKey()
        val senderMessage = createMessage(sender, receiver)

        val wrap = GiftWrapEvent.create(
            event = SealedGossipEvent.create(
                event = senderMessage,
                encryptTo = senderPublicKey,
                privateKey = sender.privKey!!
            ),
            recipientPubKey = senderPublicKey
        )

        benchmarkRule.measureRepeated {
            wrap.hasCorrectIDHash()
        }
    }

    @Test
    fun checkSignature() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderPublicKey = CryptoUtils.pubkeyCreate(sender.privKey!!).toHexKey()
        val senderMessage = createMessage(sender, receiver)

        val wrap = GiftWrapEvent.create(
            event = SealedGossipEvent.create(
                event = senderMessage,
                encryptTo = senderPublicKey,
                privateKey = sender.privKey!!
            ),
            recipientPubKey = senderPublicKey
        )

        benchmarkRule.measureRepeated {
            wrap.hasVerifiedSignature()
        }
    }

    @Test
    fun decodeWrapEvent() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderPublicKey = CryptoUtils.pubkeyCreate(sender.privKey!!).toHexKey()

        val senderMessage = createMessage(sender, receiver)

        val seal = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = senderPublicKey,
            privateKey = sender.privKey!!
        )

        val wrappedEvent = GiftWrapEvent.create(
            event = seal,
            recipientPubKey = senderPublicKey
        )

        benchmarkRule.measureRepeated {
            assertNotNull(decodeNIP44(wrappedEvent.content))
        }
    }

    @Test
    fun decryptWrapEvent() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderPublicKey = CryptoUtils.pubkeyCreate(sender.privKey!!).toHexKey()

        val senderMessage = createMessage(sender, receiver)

        val seal = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = senderPublicKey,
            privateKey = sender.privKey!!
        )

        val wrappedEvent = GiftWrapEvent.create(
            event = seal,
            recipientPubKey = senderPublicKey
        )

        val toDecrypt = decodeNIP44(wrappedEvent.content) ?: return

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.decryptNIP44(toDecrypt, sender.privKey!!, wrappedEvent.pubKey.hexToByteArray()))
        }
    }

    @Test
    fun parseWrappedEvent() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderPublicKey = CryptoUtils.pubkeyCreate(sender.privKey!!).toHexKey()

        val senderMessage = createMessage(sender, receiver)

        val seal = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = senderPublicKey,
            privateKey = sender.privKey!!
        )

        val wrappedEvent = GiftWrapEvent.create(
            event = seal,
            recipientPubKey = senderPublicKey
        )

        val toDecrypt = decodeNIP44(wrappedEvent.content) ?: return
        val innerJson = CryptoUtils.decryptNIP44(toDecrypt, sender.privKey!!, wrappedEvent.pubKey.hexToByteArray())

        benchmarkRule.measureRepeated {
            assertNotNull(innerJson?.let { Event.fromJson(it) })
        }
    }

    @Test
    fun decodeSealEvent() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderPublicKey = CryptoUtils.pubkeyCreate(sender.privKey!!).toHexKey()

        val senderMessage = createMessage(sender, receiver)

        val seal = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = senderPublicKey,
            privateKey = sender.privKey!!
        )

        benchmarkRule.measureRepeated {
            assertNotNull(decodeNIP44(seal.content))
        }
    }

    @Test
    fun decryptSealedEvent() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderPublicKey = CryptoUtils.pubkeyCreate(sender.privKey!!).toHexKey()

        val senderMessage = createMessage(sender, receiver)

        val seal = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = senderPublicKey,
            privateKey = sender.privKey!!
        )

        val toDecrypt = decodeNIP44(seal.content) ?: return

        benchmarkRule.measureRepeated {
            assertNotNull(CryptoUtils.decryptNIP44(toDecrypt, sender.privKey!!, seal.pubKey.hexToByteArray()))
        }
    }

    @Test
    fun parseSealedEvent() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderPublicKey = CryptoUtils.pubkeyCreate(sender.privKey!!).toHexKey()

        val senderMessage = createMessage(sender, receiver)

        val seal = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = senderPublicKey,
            privateKey = sender.privKey!!
        )

        val toDecrypt = decodeNIP44(seal.content) ?: return
        val innerJson = CryptoUtils.decryptNIP44(toDecrypt, sender.privKey!!, seal.pubKey.hexToByteArray())

        benchmarkRule.measureRepeated {
            assertNotNull(innerJson?.let { Gossip.fromJson(it) })
        }
    }

}