package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.Gossip
import com.vitorpamplona.quartz.events.SealedGossipEvent
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
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
class GiftWrapSigningBenchmark {

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
    fun createMessageEvent() {
        val sender = KeyPair()
        val receiver = KeyPair()

        benchmarkRule.measureRepeated {
            createMessage(sender, receiver)
        }
    }

    @Test
    fun sealMessage() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderMessage = createMessage(sender, receiver)

        benchmarkRule.measureRepeated {
            val senderPublicKey = CryptoUtils.pubkeyCreate(sender.privKey!!).toHexKey()
            SealedGossipEvent.create(
                event = senderMessage,
                encryptTo = senderPublicKey,
                privateKey = sender.privKey!!
            )
        }
    }

    @Test
    fun wrapSeal() {
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
            GiftWrapEvent.create(
                event = seal,
                recipientPubKey = senderPublicKey
            )
        }
    }

    @Test
    fun wrapToString() {
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

        benchmarkRule.measureRepeated {
            wrap.toJson()
        }
    }
}