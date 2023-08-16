package com.vitorpamplona.quartz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.Gossip
import com.vitorpamplona.quartz.events.NIP24Factory
import com.vitorpamplona.quartz.events.SealedGossipEvent
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
        } else {
            fail()
        }
    }

    @Test()
    fun testInternalsGroupMessage() {
        val sender = KeyPair()
        val receiverA = KeyPair()
        val receiverB = KeyPair()

        val senderMessage = ChatMessageEvent.create(
            msg = "Who is going to the party tonight?",
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

            assertEquals("Who is going to the party tonight?", unwrappedGossipToReceiverAByReceiverA?.content)
            assertEquals("Who is going to the party tonight?", unwrappedGossipToReceiverBByReceiverB?.content)
            assertEquals("Who is going to the party tonight?", unwrappedGossipToSenderBySender?.content)
        } else {
            fail()
        }
    }

    @Test
    fun testCaseFromAmethyst1() {
        val json = """
 {
    "content":"{\"ciphertext\":\"AaTN5Mt7AOeMosjHeLfai89kmvW/qJ7W2VMttAwuh6hwRGV+ylJhpDbdVRhVmkCotbDjBgS6xioLrSDcdSngFOiVMHS5dTAP0MkQv09aZlBh/NgdmyfHHd24YlHPkDuF5Yb4Vmz7kq/vmjsNZvDrTen3TG2DcEoTV9GKexdMEqyBA4LsB2DLnWfpvOi0olDkGjPGSteTaU1nCdOtN8knoEKumrxwevvbygKphorvKX/j3ojMMb0AceJM6Cr6TRIvSsQnKGEv5V8qbC/uIrQoH3N108Fd/2SY2MWuyLKRnuak9F/w82MV13elq8ngyjcktLYM5yrPg5nrxZlyJsV8D7V/g/bvhoL+UmWe0XoCR5LXzy77SfIkgA1ePKEfGp5sD2CVIzXt9zHdFwGxAKZuyB4qwrRaAFrS2xx+Bw4nnEmF6V9NhfheSCmGzTILuTePx4ubvnYw/j8Hmqd6UvM3DBNnlJ3D6po0blirfWvMe/ea+Em4CMXfq8Iq+7r4gRx8azADygKeJ+C89GTBEvS9EvgrXCVfTMVTcFc44YAZhekOqYY1BOZgfxIV4gUiJfpMMd4B9MQv/tmnewrpTsq1reSQQcEW/mXT2cnMeCZbAIJSPg8usZ30QlrH+np+YSzFKWYDP1kThcV0ElEE2Ne8KaUUFIRE5KmhBQc/qtORefCpne5s7V7J5vLjT5rinsDzzENB1XVlmY1Icx42raP5tGAL1gOK5gRHLvtcgFQR3WcDRYaNqELiYxx41j9w9lz5e00Ttla255rZkb760KSLaBFBss6wYGiYCabVgtBNpkExpCFPPEd5eAZa5rNK2QrnojYsdxEnlicF6A+zSChLy/TbzxYwyQywDfoF9F8kBakPZkAhsciQViCii2KlieRq4OgJFZGndmnS82hyPqsoJIm22vWr1iqMvSBHo/9cLj/r+lfmGVOdgM62JHckPZjOLS0QWIb9gQiT+zXZG22+eZElMYbGXVpR1dyMaQtde8ivEVVLas6kMCVKaDTHEFglaCBXjJ3RNJv73HsG1kb0rMmOj8ltbBakjHpv7M59amavuu6SReYt\",\"nonce\":\"6anNjUdNwW6MNfoKzRZcz1R09N1h8G4L\",\"v\":1}",
    "created_at":1690660515,
    "id":"d90739741c2f5a8c1a03aab5dc219c0b708ed6b0566044495731cd0307cf19a5",
    "kind":1059,
    "pubkey":"a79b7162f8ebb9c9f7aa65a48977ab7f32aa097520bc543e4d625812154ff6af",
    "sig":"9b012504e779632a2a1f55562fa9a85f8ae6245cbc149b83d25b2971249053abc77f65cc068e5d025b871d743678265fede70de4eaf5af642e675a5b6210077d",
    "tags":[
       [
          "p",
          "c55f0b0cb4dd180dd4395867b28dd4c208b709144a69fb7c31a46c369a5ad1c6"
       ]
    ]
 }
        """.trimIndent()

        val privateKey = "de6152a85a0dea3b09a08a6f8139a314d498a7b52f7e5c28858b64270abd4c70"
        val gossip = unwrapUnsealGossip(json, privateKey)

        assertNotNull(gossip)
        assertEquals("Hola, que tal?", gossip?.content)
    }

    @Test
    fun testCaseFromAmethyst2() {
        val json = """
{
    "content":"{\"ciphertext\":\"Zb0ZNYAcDG5y7BiCWgbxY/i7rN7TxPwr3Oaste6em4VcetuenaMu2SyH6OuCCxxmIa7kFennJD8ZCrev0086azsPNutl9I6OCoOfDQb2GoFaLoJAkE/FuW0uEoEJuN72KsKj05HEjOM6nqL2KiW0pxTCNmlGpweMwpXQdm2ItWkybNpq8+b4NJUDee2czBUd9Kr2ELbPISTYzA17z1IzPXGQw8c73NL+QX9I/QZjM/agqX2x5q11SU52xiRyVd9zHf7TMctZI4QEsqDB6xi54D1bAeZlMhVdcpQRpGDfqRz3KXFlhB3Bwdc8GLgY0aLTn6tJs4qrHP3mQkxFYk0mju0afoc0rloMEUHcBVtM18S9OrTPqfmSqFTQsjaT8g+PkmeiLBo1sXsMCS62w0abSZD9OzQtciMz70ZpcWoLjx5f8panjFClvg4tJ8czMURIHM/IFS1uKAUHBArGN8QpCw8MXQBblpyLDiEkFcSX334Zdps0OIw4z328JSdeejyRh4ks+NHDt9FcjC4iicEqfEh8OTkXuKqEAVkRyfAioNQxWQPnXDzMX0Q+BXvKzBA7NaEBDpbV36H/KnrpBBQwokV9/Byb6Seh3g6GSqRAWD3U6Nk2aBMXkD0xY8vnIqMckBeYHxn8BW7k1FdXFC9lE5xCxWZHkmksJ4f0NVaF37O6d8qOe6RK7bfUeF8/SouJEu+eEX1f4KCMboslwkdk8QA8bThGcRGn8GQBMrPKrpZwHYNyyH8jwt9pywigXJejRLDDnDp3FH/3dbZy5CfuNH6KGydf/O5xx1r316so1UPO1mL5LHJUFZVIaMaMMUsgq12gpI0lLEh5NJPpsi9e3ibkzEZGf7FlAJjJQURbQ8xacN7R+w3GWKbJNHiQbUZ2lXo6fwz33t0DrSqEW970yWPHlqxcpd27EI+qqb5IqfklQZ3RObZZBhzDvImaCPG+U7SmgLhPxnilpGjd5lw/ttiqJhPG9mYFMf1eJXSG+Q9VVkGzN7jxXYtx0q0WGjVq98ZGv5RSnF1d9+QVGCd1fiPS3rsaWdYWly8l0y2quYObJ6Mv3Wh3\",\"nonce\":\"/Q2UTTjVZthm/atcCuDjU1e4reF+ZSgZ\",\"v\":1}",
    "created_at":1690660515,
    "id":"087d9627d63135a5050758a69222e566c86702e930c9905f0b93ccd6bebeca3f",
    "kind":1059,
    "pubkey":"e59c00796ae2aa9077fc8bcd57fe8d32c0fc363f7c8b93d055c70804ffff3772",
    "sig":"807cb641c314ca6910aaeefadcf87d859137520be1039eb40e39832ed59d456fdd800c5f88bba09e1b395ee90c66d5330847bdd010b63be9919bf091adbc2c2a",
    "tags":[
       [
          "p",
          "f85f315c06aaf19c2b30a96ca80d9644720655ee8d3ec43b84657a7c98f36a23"
       ]
    ]
}
        """.trimIndent()

        val privateKey = "409ff7654141eaa16cd2161fe5bd127aeaef71f270c67587474b78998a8e3533"
        val gossip = unwrapUnsealGossip(json, privateKey)

        assertNotNull(gossip)
        assertEquals("Hola, que tal?", gossip?.content)
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

        val privateKey = "09e0051fdf5fdd9dd7a54713583006442cbdbf87bdcdab1a402f26e527d56771"
        val gossip = unwrapUnsealGossip(json, privateKey)

        assertNotNull(gossip)
        assertEquals("test", gossip?.content)
    }

    @Test
    fun testFromCoracle2() {
        val json = """
{
  "content": "{\"ciphertext\":\"Hn/dHo/I8Qk6QWWAiKyo/SfKJqQfHdV0O5tMmgqMyfHrsFoDY6IhGQP2EgCJ/6HsNQyO/8EMAmLW8w0PbDKlBKYGKGpaMwCA6B1r0rLjvu+149RJZuggRNm9rd7tNVNkNs38iqt1KYD++bohePm52q+VhAQikbX2gTONV82ROwZylAg9vjvMnYkDt45g6N97s9FRB6V7YMiUEtJnneMixa6klucpUuenQ4569tyt5vnUMD2VNhKYCc2jit2hf7k0DIhvZrVC3OdopUvxIuYYWr3r7XpuEB3HJ6Ji3ajHPzgGeFcItBR7uKZ9s6XU34F3keyZbxrv3yWHFM5NrOctAdZexSGpqWRW93M0KZUAp9HgQh3YzMLl8xt0mcrVywCgjU6Kx8IwkI0bjPU+Am8acY3cItted6hZQ4Vy1xFITdKVfPWDl3Ab59iBg9+IkY5C31wqsKPgPVVycwQE6UpaGW74gy3qZshwyoo01owvEIbVvrSJWXH7EUVvndDPvUbo+f+EVa84IEwVjPmY2oR7VsxVfqRBdmPg23OSw/9rzVybmruqaQHd3xrTTEcnG0qBc/ugCXsiuILTeScOovEnqIlKKK3KB36jMtdScdJB+b4YrzJInY1AvqU7IAgqe0vmo1LdbMtj7kjuxkXJhhQsunAbTvPigTrsOfJ08P9l7r/95kpxudgagEaW7XAjYVfLphseJT3Iy1IuQEyG5sshQ+pl/CYvkGide7ykHwm9pjSBVkD9Mdcn5X6lSnLNJEcwY43pz43r6Kq3L09qneILY3DSKyQ16Zcu1MiAMAM5r6JGvpAHqcMmixi9ORuiryjteTmY4L0vI7b3W/0RSUblXxUrb8IpeysBrFmiKJBgCoU0r/D/8tgR+Eewyp1qxKI4SfKG5GFH40zZ2oVvKyoHAR4x1oVDp/MttcnxkzAsCFL6QuJC9A/vImjsumpmYB/EChcZCOAsfqkuzH4VSjZx\",\"nonce\":\"K537d+7m5tUcXZfkr3Qk2J2G86vdBMmY\",\"v\":1}",
  "created_at": 1690655012,
  "id": "c4f97c6332b0a63912c44c9e1f8c7b23581dc67a8489ec1522ec205fea7133db",
  "kind": 1059,
  "pubkey": "8def03a22b1039256a3883d46c7ccd5562f61743100db401344284547de7ec61",
  "sig": "25dcf24bdda99c04abc72274d9f7a30538a4a00a70ac4b39db4082b73823979858df93cd649c25edfb759857eac46ed70bb9ad0598f2e011d733a5a382bc4def",
  "tags": [
    [
      "p",
      "e7764a227c12ac1ef2db79ae180392c90903b2cec1e37f5c1a4afed38117185e"
    ]
  ]
}
        """.trimIndent()

        val privateKey = "09e0051fdf5fdd9dd7a54713583006442cbdbf87bdcdab1a402f26e527d56771"

        val gossip = unwrapUnsealGossip(json, privateKey)

        assertEquals("asdfasdfasdf", gossip?.content)
        assertEquals(1690659269L, gossip?.createdAt)
        assertEquals("827ba09d32ab81d62c60f657b350198c8aaba84372dab9ad3f4f6b8b7274b707", gossip?.id)
        assertEquals(14, gossip?.kind)
        assertEquals("subject", gossip?.tags?.firstOrNull()?.get(0))
        assertEquals("test", gossip?.tags?.firstOrNull()?.get(1))
    }

    @Test
    fun testFromCoracle3() {
        val json = """
{
  "content": "{\"ciphertext\":\"PGCodiacmCB/sClw0C6DQRpP/XIfNAKCVZdKLQpOqytgbFryhs9Z+cPldq2FajXzxs3jQsF7/EVQyWpuV8pXetvFT9tvzjg4Xcm7ZcooLUnAeAo2xZNcJontN4cGubuDqKuXy5n59yXP1fIxfnJxRTRRdCZ2edhsKeNR5NSByUi+StjV10rnfHt8AhZCpiXiZ/giTOsC4wdaeONPgMzMeljaJWLvl6n11VjmXhkx1mXIQt43CNB1hIqO3p89Mbd9p+nlLrOsR+Xs0TB4DCh4XTPbvgf7B7Z+PgOfl3GZfJy9x6TciLcF4E3Ba1zrPe4f79czCIEiJ1yrIKrzzYvv+it35DZQ8fgveFXpyHnNL29hml8PNjyOsFbCHVYLMGw88evI5PijOcpe1TtdoioX8kX5kVEQSKJXuoSjTorvbRPCgGzaa1m0J0uTpzri5VD22a/Jh2CcAnubg6w4JDdUWCogdSV3NqiJllo7ZF7WnZ3apPdRD23MEfphVBJrcLBUNlmwajnY5IvVTKTkZOP50r9dBapvMWXIo6M6zhy/5vVWJz57863pelYCRG4upaXZuNK9sMBtbiphxmFR83i8RML8KN8Q391Cd/xBN7TxJNo5p2YU25VeGZUAmHY8DYlMQDm8Br0nStAXp3T+DzTRL8FTECa8DJV+KTAPoCxqhv3B28Ehr0XAP75CsHoLU00G48cR7h3vQ0CnfKh6KXU6nnDA5OWfpMYpirACCpsnpSD0OaCQ3gkQp3zZNMS3HcOpnPK/IY7R0esbzgAkvNhkyxaIfPDdf+eRUSOA9+2Ji28MwjjY8Dw3SLdUqCOzIDjQeR/T5oNmaQJm3lZ8G0FxxC6ejD4VJX/NI/x+STeB9jWHWmHZvqKzV6JHNh6qmZb6TKSIPOHpafWFoeJFOmiiigf46sju9vRXmVEAx59HXWnvnvCBNJg877yCMulB6xyQuSdVDuotQU4tQZwCKedTHJ6GqjesM98UlJrDtdWQURwwW1qc7N8tS6PukmUVEf0jmbIWVIBmUlkcVuiSs1g1h1kjt8c4MnGTz3CSgpOd1MqxLrl9WwrTqM+YnE+yeZYUjFoewyKZIQ==\",\"nonce\":\"OdCZczJiUGR4bOGIElQ4UUH4dQmG5U/3\",\"v\":1}",
  "kind": 1059,
  "created_at": 1690772945,
  "pubkey": "e01475e87896800b7285eb0daf263c59f811c8fc5bc8daa105d2c98b6d7c4952",
  "tags": [
    [
      "p",
      "b08d8857a92b4d6aa580ff55cc3c18c4edf313c83388c34abc118621f74f1a78"
    ]
  ],
  "id": "d9fc85ece892ce45ffa737b3ddc0f8b752623181d75363b966191f8c03d2debe",
  "sig": "1b20416b83f4b5b8eead11e29c185f46b5e76d1960e4505210ddd00f7a6973cc11268f52a8989e3799b774d5f3a55db95bed4d66a1b6e88ab54becec5c771c17"
}
        """.trimIndent()

        val privateKey = "7dd22cafc512c0bc363a259f6dcda515b13ae3351066d7976fd0bb79cbd0d700"

        val gossip = unwrapUnsealGossip(json, privateKey)

        assertEquals("8d1a56008d4e31dae2fb8bef36b3efea519eff75f57033107e2aa16702466ef2", gossip?.id)
        assertEquals("Howdy", gossip?.content)
        assertEquals(1690833960L, gossip?.createdAt)
        assertEquals(14, gossip?.kind)
        assertEquals("p", gossip?.tags?.firstOrNull()?.get(0))
        assertEquals("b08d8857a92b4d6aa580ff55cc3c18c4edf313c83388c34abc118621f74f1a78", gossip?.tags?.firstOrNull()?.get(1))
        assertEquals("subject", gossip?.tags?.getOrNull(1)?.get(0))
        assertEquals("Stuff", gossip?.tags?.getOrNull(1)?.get(1))
    }

    fun unwrapUnsealGossip(json: String, privateKey: HexKey): Gossip? {
        val pkBytes = privateKey.hexToByteArray()

        val wrap = Event.fromJson(json) as GiftWrapEvent
        wrap.checkSignature()

        assertEquals(CryptoUtils.pubkeyCreate(pkBytes).toHexKey(), wrap.recipientPubKey())

        val event = wrap.unwrap(pkBytes)
        assertNotNull(event)

        return if (event is SealedGossipEvent) {
            return event.unseal(pkBytes)
        } else {
            println(event?.toJson())
            fail("Event is not a Sealed Gossip")
            null
        }
    }
}
