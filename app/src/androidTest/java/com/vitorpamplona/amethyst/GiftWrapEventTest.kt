package com.vitorpamplona.amethyst

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils
import com.vitorpamplona.amethyst.service.KeyPair
import com.vitorpamplona.amethyst.service.model.ChatMessageEvent
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.GiftWrapEvent
import com.vitorpamplona.amethyst.service.model.NIP24Factory
import com.vitorpamplona.amethyst.service.model.SealedGossipEvent
import com.vitorpamplona.amethyst.service.relays.Client
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GiftWrapEventTest {
    @Test()
    fun testNip24Utils() {
        val sender = KeyPair()
        val receiver = KeyPair()
        val message = "Hola, que tal?"

        val events = NIP24Factory().createMsgNIP24(
            message,
            listOf(receiver.pubKey.toHexKey()),
            sender.privKey!!
        )

        // Simulate Receiver
        val eventsReceiverGets = events.filter { it.isTaggedUser(receiver.pubKey.toHexKey()) }
        eventsReceiverGets.forEach {
            val event = it.unwrap(receiver.privKey!!)
            if (event is SealedGossipEvent) {
                val innerData = event.unseal(receiver.privKey!!)
                assertEquals(message, innerData?.content)
            } else {
                fail("Wrong Event")
            }
        }

        // Simulate Sender
        val eventsSenderGets = events.filter { it.isTaggedUser(sender.pubKey.toHexKey()) }
        eventsSenderGets.forEach {
            val event = it.unwrap(sender.privKey!!)
            if (event is SealedGossipEvent) {
                val innerData = event.unseal(sender.privKey!!)
                assertEquals(message, innerData?.content)
            } else {
                fail("Wrong Event")
            }
        }
    }

    @Test()
    fun testNip24UtilsForGroups() {
        val sender = KeyPair()
        println("AAAA - ${sender.privKey?.toHexKey()}")
        val receiver1 = KeyPair()
        val receiver2 = KeyPair()
        val receiver3 = KeyPair()
        val receiver4 = KeyPair()
        val message = "Hola, que tal?"

        val receivers = listOf(
            receiver1,
            receiver2,
            receiver3,
            receiver4
        )

        val events = NIP24Factory().createMsgNIP24(
            message,
            receivers.map { it.pubKey.toHexKey() },
            sender.privKey!!
        )

        // Simulate Receiver
        receivers.forEach { receiver ->
            val eventsReceiverGets = events.filter { it.isTaggedUser(receiver.pubKey.toHexKey()) }
            eventsReceiverGets.forEach {
                val event = it.unwrap(receiver.privKey!!)
                if (event is SealedGossipEvent) {
                    val innerData = event.unseal(receiver.privKey!!)
                    assertEquals(message, innerData?.content)
                } else {
                    fail("Wrong Event")
                }
            }
        }

        // Simulate Sender
        val eventsSenderGets = events.filter { it.isTaggedUser(sender.pubKey.toHexKey()) }
        eventsSenderGets.forEach {
            val event = it.unwrap(sender.privKey!!)
            if (event is SealedGossipEvent) {
                val innerData = event.unseal(sender.privKey!!)
                assertEquals(message, innerData?.content)
            } else {
                fail("Wrong Event")
            }
        }
    }

    @Test()
    fun testInternalsSimpleMessage() {
        val sender = KeyPair()
        val receiver = KeyPair()

        val senderMessage = ChatMessageEvent.create(
            msg = "Hi There!",
            to = listOf(receiver.pubKey.toHexKey()),
            privateKey = sender.privKey!!
        )

        // MsgFor the Receiver

        val encMsgFromSenderToReceiver = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = receiver.pubKey.toHexKey(),
            privateKey = sender.privKey!!
        )

        // Should expose sender
        assertEquals(encMsgFromSenderToReceiver.pubKey, sender.pubKey.toHexKey())
        // Should not expose receiver
        assertTrue(encMsgFromSenderToReceiver.tags.isEmpty())

        val giftWrapEventToReceiver = GiftWrapEvent.create(
            event = encMsgFromSenderToReceiver,
            recipientPubKey = receiver.pubKey.toHexKey()
        )

        // Should not be signed by neither sender nor receiver
        assertNotEquals(giftWrapEventToReceiver.pubKey, sender.pubKey.toHexKey())
        assertNotEquals(giftWrapEventToReceiver.pubKey, receiver.pubKey.toHexKey())

        // Should not include sender as recipient
        assertNotEquals(giftWrapEventToReceiver.recipientPubKey(), sender.pubKey.toHexKey())

        // Should be addressed to the receiver
        assertEquals(giftWrapEventToReceiver.recipientPubKey(), receiver.pubKey.toHexKey())

        // MsgFor the Sender

        val encMsgFromSenderToSender = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = sender.pubKey.toHexKey(),
            privateKey = sender.privKey!!
        )

        // Should expose sender
        assertEquals(encMsgFromSenderToSender.pubKey, sender.pubKey.toHexKey())
        // Should not expose receiver
        assertTrue(encMsgFromSenderToSender.tags.isEmpty())

        val giftWrapEventToSender = GiftWrapEvent.create(
            event = encMsgFromSenderToSender,
            recipientPubKey = sender.pubKey.toHexKey()
        )

        // Should not be signed by neither the sender, not the receiver
        assertNotEquals(giftWrapEventToSender.pubKey, sender.pubKey.toHexKey())
        assertNotEquals(giftWrapEventToSender.pubKey, receiver.pubKey.toHexKey())

        // Should not be addressed to the receiver
        assertNotEquals(giftWrapEventToSender.recipientPubKey(), receiver.pubKey.toHexKey())
        // Should be addressed to the sender
        assertEquals(giftWrapEventToSender.recipientPubKey(), sender.pubKey.toHexKey())

        // Done

        println(senderMessage.toJson())
        println(encMsgFromSenderToReceiver.toJson())
        println(giftWrapEventToReceiver.toJson())
        println(giftWrapEventToSender.toJson())

        // Receiver's side
        // Unwrapping

        val unwrappedMsgForSenderBySender = giftWrapEventToSender.unwrap(sender.privKey!!)
        val unwrappedMsgForReceiverBySender = giftWrapEventToReceiver.unwrap(sender.privKey!!)

        assertNotNull(unwrappedMsgForSenderBySender)
        assertNull(unwrappedMsgForReceiverBySender)

        val unwrappedMsgForSenderByReceiver = giftWrapEventToSender.unwrap(receiver.privKey!!)
        val unwrappedMsgForReceiverByReceiver = giftWrapEventToReceiver.unwrap(receiver.privKey!!)

        assertNull(unwrappedMsgForSenderByReceiver)
        assertNotNull(unwrappedMsgForReceiverByReceiver)

        assertEquals(SealedGossipEvent.kind, unwrappedMsgForSenderBySender?.kind)
        assertEquals(SealedGossipEvent.kind, unwrappedMsgForReceiverByReceiver?.kind)

        assertTrue(unwrappedMsgForSenderBySender is SealedGossipEvent)
        assertTrue(unwrappedMsgForReceiverByReceiver is SealedGossipEvent)

        if (unwrappedMsgForSenderBySender is SealedGossipEvent &&
            unwrappedMsgForReceiverByReceiver is SealedGossipEvent
        ) {
            val unwrappedGossipToSenderByReceiver = unwrappedMsgForSenderBySender.unseal(receiver.privKey!!)
            val unwrappedGossipToReceiverByReceiver = unwrappedMsgForReceiverByReceiver.unseal(receiver.privKey!!)

            assertNull(unwrappedGossipToSenderByReceiver)
            assertNotNull(unwrappedGossipToReceiverByReceiver)

            val unwrappedGossipToSenderBySender = unwrappedMsgForSenderBySender.unseal(sender.privKey!!)
            val unwrappedGossipToReceiverBySender = unwrappedMsgForReceiverByReceiver.unseal(sender.privKey!!)

            assertNotNull(unwrappedGossipToSenderBySender)
            assertNull(unwrappedGossipToReceiverBySender)

            assertEquals("Hi There!", unwrappedGossipToReceiverByReceiver?.content)
            assertEquals("Hi There!", unwrappedGossipToSenderBySender?.content)
        }
    }

    @Test()
    fun testInternalsGroupMessage() {
        val sender = KeyPair()
        val receiverA = KeyPair()
        val receiverB = KeyPair()

        val senderMessage = ChatMessageEvent.create(
            msg = "Hi There!",
            to = listOf(receiverA.pubKey.toHexKey(), receiverB.pubKey.toHexKey()),
            privateKey = sender.privKey!!
        )

        val encMsgFromSenderToReceiverA = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = receiverA.pubKey.toHexKey(),
            privateKey = sender.privKey!!
        )

        val encMsgFromSenderToReceiverB = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = receiverB.pubKey.toHexKey(),
            privateKey = sender.privKey!!
        )

        val encMsgFromSenderToSender = SealedGossipEvent.create(
            event = senderMessage,
            encryptTo = sender.pubKey.toHexKey(),
            privateKey = sender.privKey!!
        )

        // Should expose sender
        assertEquals(encMsgFromSenderToReceiverA.pubKey, sender.pubKey.toHexKey())
        // Should not expose receiver
        assertTrue(encMsgFromSenderToReceiverA.tags.isEmpty())

        // Should expose sender
        assertEquals(encMsgFromSenderToReceiverB.pubKey, sender.pubKey.toHexKey())
        // Should not expose receiver
        assertTrue(encMsgFromSenderToReceiverB.tags.isEmpty())

        // Should expose sender
        assertEquals(encMsgFromSenderToSender.pubKey, sender.pubKey.toHexKey())
        // Should not expose receiver
        assertTrue(encMsgFromSenderToSender.tags.isEmpty())

        val giftWrapEventForReceiverA = GiftWrapEvent.create(
            event = encMsgFromSenderToReceiverA,
            recipientPubKey = receiverA.pubKey.toHexKey()
        )

        val giftWrapEventForReceiverB = GiftWrapEvent.create(
            event = encMsgFromSenderToReceiverB,
            recipientPubKey = receiverB.pubKey.toHexKey()
        )

        // Should not be signed by neither sender nor receiver
        assertNotEquals(giftWrapEventForReceiverA.pubKey, sender.pubKey.toHexKey())
        assertNotEquals(giftWrapEventForReceiverA.pubKey, receiverA.pubKey.toHexKey())
        assertNotEquals(giftWrapEventForReceiverA.pubKey, receiverB.pubKey.toHexKey())

        // Should not include sender as recipient
        assertNotEquals(giftWrapEventForReceiverA.recipientPubKey(), sender.pubKey.toHexKey())

        // Should be addressed to the receiver
        assertEquals(giftWrapEventForReceiverA.recipientPubKey(), receiverA.pubKey.toHexKey())

        // Should not be signed by neither sender nor receiver
        assertNotEquals(giftWrapEventForReceiverB.pubKey, sender.pubKey.toHexKey())
        assertNotEquals(giftWrapEventForReceiverB.pubKey, receiverA.pubKey.toHexKey())
        assertNotEquals(giftWrapEventForReceiverB.pubKey, receiverB.pubKey.toHexKey())

        // Should not include sender as recipient
        assertNotEquals(giftWrapEventForReceiverB.recipientPubKey(), sender.pubKey.toHexKey())

        // Should be addressed to the receiver
        assertEquals(giftWrapEventForReceiverB.recipientPubKey(), receiverB.pubKey.toHexKey())

        val giftWrapEventToSender = GiftWrapEvent.create(
            event = encMsgFromSenderToSender,
            recipientPubKey = sender.pubKey.toHexKey()
        )

        // Should not be signed by neither the sender, not the receiver
        assertNotEquals(giftWrapEventToSender.pubKey, sender.pubKey.toHexKey())
        assertNotEquals(giftWrapEventToSender.pubKey, receiverA.pubKey.toHexKey())
        assertNotEquals(giftWrapEventToSender.pubKey, receiverB.pubKey.toHexKey())

        // Should not be addressed to the receiver
        assertNotEquals(giftWrapEventToSender.recipientPubKey(), receiverA.pubKey.toHexKey())
        assertNotEquals(giftWrapEventToSender.recipientPubKey(), receiverB.pubKey.toHexKey())
        // Should be addressed to the sender
        assertEquals(giftWrapEventToSender.recipientPubKey(), sender.pubKey.toHexKey())

        println(senderMessage.toJson())
        println(encMsgFromSenderToReceiverA.toJson())
        println(encMsgFromSenderToReceiverB.toJson())
        println(giftWrapEventForReceiverA.toJson())
        println(giftWrapEventForReceiverB.toJson())
        println(giftWrapEventToSender.toJson())

        val unwrappedMsgForSenderBySender = giftWrapEventToSender.unwrap(sender.privKey!!)
        val unwrappedMsgForReceiverBySenderA = giftWrapEventForReceiverA.unwrap(sender.privKey!!)
        val unwrappedMsgForReceiverBySenderB = giftWrapEventForReceiverB.unwrap(sender.privKey!!)

        assertNotNull(unwrappedMsgForSenderBySender)
        assertNull(unwrappedMsgForReceiverBySenderA)
        assertNull(unwrappedMsgForReceiverBySenderB)

        val unwrappedMsgForSenderByReceiverA = giftWrapEventToSender.unwrap(receiverA.privKey!!)
        val unwrappedMsgForReceiverAByReceiverA = giftWrapEventForReceiverA.unwrap(receiverA.privKey!!)
        val unwrappedMsgForReceiverBByReceiverA = giftWrapEventForReceiverB.unwrap(receiverA.privKey!!)

        assertNull(unwrappedMsgForSenderByReceiverA)
        assertNotNull(unwrappedMsgForReceiverAByReceiverA)
        assertNull(unwrappedMsgForReceiverBByReceiverA)

        val unwrappedMsgForSenderByReceiverB = giftWrapEventToSender.unwrap(receiverB.privKey!!)
        val unwrappedMsgForReceiverAByReceiverB = giftWrapEventForReceiverA.unwrap(receiverB.privKey!!)
        val unwrappedMsgForReceiverBByReceiverB = giftWrapEventForReceiverB.unwrap(receiverB.privKey!!)

        assertNull(unwrappedMsgForSenderByReceiverB)
        assertNull(unwrappedMsgForReceiverAByReceiverB)
        assertNotNull(unwrappedMsgForReceiverBByReceiverB)

        assertEquals(SealedGossipEvent.kind, unwrappedMsgForSenderBySender?.kind)
        assertEquals(SealedGossipEvent.kind, unwrappedMsgForReceiverAByReceiverA?.kind)
        assertEquals(SealedGossipEvent.kind, unwrappedMsgForReceiverBByReceiverB?.kind)

        assertTrue(unwrappedMsgForSenderBySender is SealedGossipEvent)
        assertTrue(unwrappedMsgForReceiverAByReceiverA is SealedGossipEvent)
        assertTrue(unwrappedMsgForReceiverBByReceiverB is SealedGossipEvent)

        if (unwrappedMsgForSenderBySender is SealedGossipEvent &&
            unwrappedMsgForReceiverAByReceiverA is SealedGossipEvent &&
            unwrappedMsgForReceiverBByReceiverB is SealedGossipEvent
        ) {
            val unwrappedGossipToSenderByReceiverA = unwrappedMsgForSenderBySender.unseal(receiverA.privKey!!)
            val unwrappedGossipToReceiverAByReceiverA = unwrappedMsgForReceiverAByReceiverA.unseal(receiverA.privKey!!)
            val unwrappedGossipToReceiverBByReceiverA = unwrappedMsgForReceiverBByReceiverB.unseal(receiverA.privKey!!)

            assertNull(unwrappedGossipToSenderByReceiverA)
            assertNotNull(unwrappedGossipToReceiverAByReceiverA)
            assertNull(unwrappedGossipToReceiverBByReceiverA)

            val unwrappedGossipToSenderByReceiverB = unwrappedMsgForSenderBySender.unseal(receiverB.privKey!!)
            val unwrappedGossipToReceiverAByReceiverB = unwrappedMsgForReceiverAByReceiverA.unseal(receiverB.privKey!!)
            val unwrappedGossipToReceiverBByReceiverB = unwrappedMsgForReceiverBByReceiverB.unseal(receiverB.privKey!!)

            assertNull(unwrappedGossipToSenderByReceiverB)
            assertNull(unwrappedGossipToReceiverAByReceiverB)
            assertNotNull(unwrappedGossipToReceiverBByReceiverB)

            val unwrappedGossipToSenderBySender = unwrappedMsgForSenderBySender.unseal(sender.privKey!!)
            val unwrappedGossipToReceiverABySender = unwrappedMsgForReceiverAByReceiverA.unseal(sender.privKey!!)
            val unwrappedGossipToReceiverBBySender = unwrappedMsgForReceiverBByReceiverB.unseal(sender.privKey!!)

            assertNotNull(unwrappedGossipToSenderBySender)
            assertNull(unwrappedGossipToReceiverABySender)
            assertNull(unwrappedGossipToReceiverBBySender)

            assertEquals("Hi There!", unwrappedGossipToReceiverAByReceiverA?.content)
            assertEquals("Hi There!", unwrappedGossipToReceiverBByReceiverB?.content)
            assertEquals("Hi There!", unwrappedGossipToSenderBySender?.content)
        }
    }

    @Test
    fun testDecryptFromCoracle() {
        val json = """
{
  "content": "{\"ciphertext\":\"fo0/Ywyfu86cXKoOOeFFlMRv+LYM6GJUL+F/J4ARv6EcAufOZP46vimlurPBLPjNgzuGemGjlTFfC3vtq84AqIsqFo3dqKunq8Vp+mmubvxIQUDzOGYvM0WE/XOiW5LEe3U3Vq399Dq07xRpXELcp4EZxGyu4Fowv2Ppb4SKpH8g+9N3z2+bwYcSxBvI6SrL+hgmVMrRlgvsUHN1d53ni9ehRseBqrLj/DqyyEiygsKm6vnEZAPKnl1MrBaVOBZmGsyjAa/G4SBVVmk78sW7xWWvo4cV+C22FluDWUOdj/bYabH4aR4scnBX3GLYqfLuzGnuQlRNsb5unXVX41+39uXzROrmNP6iYVyYxy5tfoyN7PPZ4osoKpLDUGldmXHD6RjMcAFuou4hXt2JlTPmXpj/x8qInXId5mkmU4nTGiasvsCIpJljbCujwCjbjLTcD4QrjuhMdtSsAzjT0CDv5Lmc632eKRYtDu/9B+lkqBBkp7amhzbqp8suNTnybkvbGFQQGEQnsLfNJw/GGopAuthfi8zkTgUZR/LxFR7ZKAX73G+5PQSDSjPuGH/dQEnsFo45zsh1Xro8SfUQBsPphbX2GS31Lwu5vA30O922T4UiWuU+EdNgZR0JankQ5NPgvr1uS56C3v84VwdrNWQUCwC4eYJl4Mb/OdpEy9qwsisisppq6uuzxmxd1qx3JfocnGsvB7h2g2sG+0lyZADDSobOEZEKHaBP3w+dRcJW9D95EmzPym9GO0n+33OfqFQbda7G0rzUWfPDV0gXIuZcKs/HmDqepgIZN8FG7JhRBeAv0bCbKQACre0c8tzVEn5yCYemltScdKop3pC/r6gH50jRhAlFAiIKx8R+XwuMmJRqOcH4WfkpZlfVU85/I0XJOCHWKk6BnJi/NPP9zYiZiJe+5LecqMUVjtO0YAlv138+U/3FIT/anQ4H5bjVWBZmajwf\",\"nonce\":\"Mv70S6jgrs4D1rlqV9b5DddiymGAcVVe\",\"v\":1}",
  "created_at": 1690528373,
  "id": "6b108e4236c3c338236ee589388ce0f91f921e1532ae52e75d1d2add6f8e691a",
  "kind": 1059,
  "pubkey": "627dc0248335e2bf9adac14be9494139ebbeb12c422d7df5b0e3cd72d04c209c",
  "sig": "be11da487196db298e4ffb7a03e74176c37441c88b39c95b518fadce6fd02f23c58b2c435ca38c24d512713935ab719dae80bf952267630809e1f84be8e95174",
  "tags": [
    [
      "p",
      "e7764a227c12ac1ef2db79ae180392c90903b2cec1e37f5c1a4afed38117185e"
    ]
  ],
  "seenOn": [
    "wss://relay.damus.io/"
  ]
}
        """.trimIndent()

        val privateKey = "09e0051fdf5fdd9dd7a54713583006442cbdbf87bdcdab1a402f26e527d56771".hexToByteArray()
        val wrap = Event.fromJson(json, Client.lenient) as GiftWrapEvent

        wrap.checkSignature()

        assertEquals(CryptoUtils.pubkeyCreate(privateKey).toHexKey(), wrap.recipientPubKey())

        val event = wrap.unwrap(privateKey)
        assertNotNull(event)

        if (event is SealedGossipEvent) {
            val innerData = event.unseal(privateKey)
            assertEquals("Hi", innerData?.content)
        } else {
            println(event?.toJson())
            fail()
        }
    }
}
