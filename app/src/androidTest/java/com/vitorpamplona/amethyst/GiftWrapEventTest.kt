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

        events.forEach {
            if (it.recipientPubKey() == sender.pubKey.toHexKey()) {
                println(sender.privKey!!.toHexKey())
                println(it.toJson())
            }

            if (it.recipientPubKey() == receiver.pubKey.toHexKey()) {
                println(receiver.privKey!!.toHexKey())
                println(it.toJson())
            }
        }

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
        } else {
            fail()
        }
    }

    @Test
    fun testCaseFromAmethyst1() {
        val json = """
{
    "content":"{\"ciphertext\":\"2kQ019KOsZ53G1nZEzYjmnqRNSSdkJfxpZIomM8NxNx+0v9W9ypQugUrimqgsTyhW91o03g/RO0+C64TgTARicPeBJx2qqPPCMUa1FAyLl7mB+G9JGTlZkLX5D0k/HGBKjRtQSmw+hHzPV2nHMU3JO9AFwzbcGkMRa+I6bnGn7C4fOWgdNuRZSHJ1k7LUUDE4TdCra2fh9eeguEgzEUmBlqyJASnGZjdbGjo62Fa0jZLGjVf+1Q8uO9YXnKbKrjIC5Eds+zRbRiLPRVeCdtEy9rvdCsgHNuhX0oWk21TzUy8QREUko3+VdaCm1twEBzBxBkCreWCH175vB5gfip9GVJoRqnDC3lCpqJJ1knB24m0sVbByRCIkQpznLsWbXspyEWmWA0WhaTn0OQWg6P9exQscAq8IBILp8otxlPPp4/UUhDEmhB8IFhlieMVSpBVHQka/uaHBy1oL/HzEkI/siza86LiJv95kcTfqG3LfJwJaIGoshFZTxuOF7JQcXobzEpodtP9RImbmZIfTcrL29BH3oCAH8o9P5CHeyC+RUK0iX5Uf0guAskrrVGy18BbBceGzAKP09so+kJ56RH3CKaPX0PVMqBiwDISdRAdSSwXkJIl9oCPYqB5O7rRlPhx53M2+Qej77hSwvvYLOlp0HirSRko1a8jrhhVT7npTdfO6QBWjC3Cm1nJUYymFqqqrnb6w2GmlcMW20rpcvUd/x3sQF9CPl22H6XDgcUxtfdgFLmoMUnKkbj5EZUKJXPF6eUOHu/C+YFf/7p/bKT2QloQY7so0Fw5+NTzNnrsuOuk7LJ2yMonpAyjHZ+KR9od/QLzB5QDCqtXisiJM6A1L8agyF6lkZVGEZX7fBhWtI/qMzukvFWBCLb8luPVC+sgCzuVTbgAvGIDagqIhxyVd+k9IduteVrB+2QI4LBsIALWcFhOjwegxkwSFGcTeU5aFfxyjJcLlW5DPuJeb5k3O6gUN1chlb+by5vrUmglE5wGrmcL8TDNay71S50/GKVXVg8/hb8lT2XYTV4h9wMRjbv330b90SZreNGxBJNIYA1UjyYlXAmI\",\"nonce\":\"K99zmxMrz3WaijlFY1KPfF1Z7yKmoTQa\",\"v\":1}",
    "created_at":1690658663,
    "id":"91fd8ea5741ec4bd6633b6500c1466551a4dcc823a32a670481ab35e6f1a3738",
    "kind":1059,
    "pubkey":"6dd4b660790805c77fafe3fabf6fb89e3f18b17246af1b74f1b005759add2776",
    "sig":"93f4ae7782b03d17815d2c67e23cf6d4c0764bac865d0cbd74cf580882632581fc060395a86cad078bea0e33b78fe6dfcd3d04099fe6b51bc6e87c470f508374",
    "tags":[
       [
          "p",
          "4b99b998c76a47f7284e5158092fb4f0795ee05fb04136f8be8b727ad2e5357b"
       ]
    ]
 }
        """.trimIndent()

        val privateKey = "15c4facfb7aafe31d2fb45183c830a95773f8cb37012e35ff5f306c9b97a8cfe".hexToByteArray()
        val wrap = Event.fromJson(json, Client.lenient) as GiftWrapEvent

        wrap.checkSignature()

        assertEquals(CryptoUtils.pubkeyCreate(privateKey).toHexKey(), wrap.recipientPubKey())

        val event = wrap.unwrap(privateKey)
        assertNotNull(event)

        if (event is SealedGossipEvent) {
            val innerData = event.unseal(privateKey)
            assertEquals("Hola, que tal?", innerData?.content)
        } else {
            fail("Inner event is not a Sealed Gossip")
        }
    }

    @Test
    fun testCaseFromAmethyst2() {
        val json = """
 {
    "content":"{\"ciphertext\":\"Z9nuRGldDimFioFx0eoaiRjHWnMxWjgbvDFfn8S84sSrLr+/TZGT/HSlmU4LeisGsvdEps6c7nROQ43Jnri9eDtCwM9X/picSISdfI256wgh+17xHxBNQUY0+mUnttfrbt1IGPlMFAiRPH4CJavXL586ufoybRLFuI0bn9rnPKnqrpocUmYYXfwa5wbIdNeHN0MhQ+t63dwyk8v0zROfUM9UFVqA7Uj28T4jOyxSxi0vrlPPNhPrVqx1F8cTro3aeelkpVJOuEa483BrojqqougYt3bNDWzyHZBk8KMGT27/U8trzbsbiutG/JP0KpckuX/OOQnuC32HxduED9yElHP/thPgD5Tzk1KruexdX78zzGQxTz4288JPAlb0dHLiHCzGo7jcMMDc0W60yRmBIHupVjX677cPXNLb7CZZlBteuzMevcGpDpSAdvft/hzGgdAV3zn3Xsz9V0EYO35dqjburmw17akZ4Zg91bbnjEef15HR4UKHFRTLG5PZLAQSOmJPgpm8FTmL3MxFYRjtOMd+eyFJnhVJKtjqQGDDaiXMNRhubJePltq8oN1ITSfyzxPmpDDimEjySe4PV2kOeOBXEvCq8l/FXOJjtgBpDTWosKr/seSBLxg/VFxdbeJvvq2gUPmEg/vjGkAGKykCuHscTFfnAQRxbNOCtrnHt4ViVLUjDthWZraxfQSXi8bN9eOHPC4/22ehe+itNxfLla6d+qAnaIb22IOjg/stL77HAnv+1X0lvl1swg3dpa4iRWch07+vMZoO5xrNauyC1IaIud7Vd4Xmu/BIk/8n81PBbjd9xbU5Or0dvY3vFb5MoahJn/WdO+eGb2471I2Me+1wRXYBKZpJBJBD6lR6jW248RtKwY1+Tzf+lmw8tIjK26COeSbIFuPZy7y43pY/rYnLydvNNuwHm7RLCDamRxz5d4+mWpaur5Po3XI8BH5/o3Aa2I7G6cS6C6tMxTkqpNjU0hPFD4ngPED2E8PAm95Ut7ZckjXYMSGvj4jXqW61l3RbeG87/U9TtGyOhgE6mFLyitRpzr6qpBz3vatpSuWmqs7bsWGL\",\"nonce\":\"2BmW1spqvh+C63dj9SiW2GVpcPOl1/HQ\",\"v\":1}",
    "created_at":1690658663,
    "id":"6b37ccbd385668e0ef7d2bc2e666c77090c81a0abdf30b66d8af8cd71791679d",
    "kind":1059,
    "pubkey":"49af42b938061529473c68b3a19bb0247063adbd6752f5bd29e75cfa2f4dc80c",
    "sig":"dacd327dabe19014a8e88e1e705a79ea0333cd4104b52ae7bcfd3fcf8cee68733a36cb38e1f9af225a836c2f38a8fcbf9faf07ca75d34419f037f1efbb2df52f",
    "tags":[
       [
          "p",
          "c98a847f6bff5c9917e9de5f641039ca21080d07dfa6434cb109b7587af64aad"
       ]
    ]
 }
        """.trimIndent()

        val privateKey = "f2f8c4b9bba181c83c25eeea336fd214a088a383363dc302e622e1ae9851093e".hexToByteArray()
        val wrap = Event.fromJson(json, Client.lenient) as GiftWrapEvent

        wrap.checkSignature()

        assertEquals(CryptoUtils.pubkeyCreate(privateKey).toHexKey(), wrap.recipientPubKey())

        val event = wrap.unwrap(privateKey)
        assertNotNull(event)

        if (event is SealedGossipEvent) {
            val innerData = event.unseal(privateKey)
            assertEquals("Hola, que tal?", innerData?.content)
        } else {
            fail("Inner event is not a Sealed Gossip")
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
            assertEquals(null, innerData?.content)
        } else {
            println(event?.toJson())
            fail()
        }
    }
}
