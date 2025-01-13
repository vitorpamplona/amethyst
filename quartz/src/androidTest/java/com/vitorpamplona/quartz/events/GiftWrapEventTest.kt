/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.crypto.Hex
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.checkSignature
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.people.isTaggedUser
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip17Dm.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip59Giftwrap.GiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.SealedGossipEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GiftWrapEventTest {
    @Test()
    fun testNip17Utils() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())
        val message = "Hola, que tal?"

        // Requires 3 tests
        val countDownLatch = CountDownLatch(3)

        NIP17Factory().createMsgNIP17(
            message,
            listOf(receiver.pubKey),
            sender,
        ) { events ->
            countDownLatch.countDown()

            // Simulate Receiver
            val eventsReceiverGets = events.wraps.filter { it.isTaggedUser(receiver.pubKey) }
            eventsReceiverGets.forEach {
                it.cachedGift(receiver) { event ->
                    if (event is SealedGossipEvent) {
                        event.cachedGossip(receiver) { innerData ->
                            countDownLatch.countDown()
                            assertEquals(message, innerData.content)
                        }
                    } else {
                        fail("Wrong Event")
                    }
                }
            }

            // Simulate Sender
            val eventsSenderGets = events.wraps.filter { it.isTaggedUser(sender.pubKey) }
            eventsSenderGets.forEach {
                it.cachedGift(sender) { event ->
                    if (event is SealedGossipEvent) {
                        event.cachedGossip(sender) { innerData ->
                            countDownLatch.countDown()
                            assertEquals(message, innerData.content)
                        }
                    } else {
                        fail("Wrong Event")
                    }
                }
            }
        }

        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test()
    fun testNip17UtilsForGroups() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver1 = NostrSignerInternal(KeyPair())
        val receiver2 = NostrSignerInternal(KeyPair())
        val receiver3 = NostrSignerInternal(KeyPair())
        val receiver4 = NostrSignerInternal(KeyPair())
        val message = "Hola, que tal?"

        val receivers =
            listOf(
                receiver1,
                receiver2,
                receiver3,
                receiver4,
            )

        val countDownLatch = CountDownLatch(receivers.size + 2)

        NIP17Factory().createMsgNIP17(
            message,
            receivers.map { it.pubKey },
            sender,
        ) { events ->
            countDownLatch.countDown()

            // Simulate Receiver
            receivers.forEach { receiver ->
                val eventsReceiverGets = events.wraps.filter { it.isTaggedUser(receiver.pubKey) }
                eventsReceiverGets.forEach {
                    it.cachedGift(receiver) { event ->
                        if (event is SealedGossipEvent) {
                            event.cachedGossip(receiver) { innerData ->
                                countDownLatch.countDown()
                                assertEquals(message, innerData.content)
                            }
                        } else {
                            fail("Wrong Event")
                        }
                    }
                }
            }

            // Simulate Sender
            val eventsSenderGets = events.wraps.filter { it.isTaggedUser(sender.pubKey) }
            eventsSenderGets.forEach {
                it.cachedGift(sender) { event ->
                    if (event is SealedGossipEvent) {
                        event.cachedGossip(sender) { innerData ->
                            countDownLatch.countDown()
                            assertEquals(message, innerData.content)
                        }
                    } else {
                        fail("Wrong Event")
                    }
                }
            }
        }

        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test()
    fun testInternalsSimpleMessage() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val countDownLatch = CountDownLatch(2)

        var giftWrapEventToSender: GiftWrapEvent? = null
        var giftWrapEventToReceiver: GiftWrapEvent? = null

        ChatMessageEvent.create(
            msg = "Hi There!",
            isDraft = false,
            to = listOf(receiver.pubKey),
            signer = sender,
        ) { senderMessage ->
            // MsgFor the Receiver

            SealedGossipEvent.create(
                event = senderMessage,
                encryptTo = receiver.pubKey,
                signer = sender,
            ) { encMsgFromSenderToReceiver ->
                // Should expose sender
                assertEquals(encMsgFromSenderToReceiver.pubKey, sender.pubKey)
                // Should not expose receiver
                assertTrue(encMsgFromSenderToReceiver.tags.isEmpty())

                GiftWrapEvent.create(
                    event = encMsgFromSenderToReceiver,
                    recipientPubKey = receiver.pubKey,
                ) { giftWrapToReceiver ->
                    // Should not be signed by neither sender nor receiver
                    assertNotEquals(giftWrapToReceiver.pubKey, sender.pubKey)
                    assertNotEquals(giftWrapToReceiver.pubKey, receiver.pubKey)

                    // Should not include sender as recipient
                    assertNotEquals(giftWrapToReceiver.recipientPubKey(), sender.pubKey)

                    // Should be addressed to the receiver
                    assertEquals(giftWrapToReceiver.recipientPubKey(), receiver.pubKey)

                    giftWrapEventToReceiver = giftWrapToReceiver

                    countDownLatch.countDown()
                }
            }

            // MsgFor the Sender
            SealedGossipEvent.create(
                event = senderMessage,
                encryptTo = sender.pubKey,
                signer = sender,
            ) { encMsgFromSenderToSender ->
                // Should expose sender
                assertEquals(encMsgFromSenderToSender.pubKey, sender.pubKey)
                // Should not expose receiver
                assertTrue(encMsgFromSenderToSender.tags.isEmpty())

                GiftWrapEvent.create(
                    event = encMsgFromSenderToSender,
                    recipientPubKey = sender.pubKey,
                ) { giftWrapToSender ->
                    // Should not be signed by neither the sender, not the receiver
                    assertNotEquals(giftWrapToSender.pubKey, sender.pubKey)
                    assertNotEquals(giftWrapToSender.pubKey, receiver.pubKey)

                    // Should not be addressed to the receiver
                    assertNotEquals(giftWrapToSender.recipientPubKey(), receiver.pubKey)
                    // Should be addressed to the sender
                    assertEquals(giftWrapToSender.recipientPubKey(), sender.pubKey)

                    giftWrapEventToSender = giftWrapToSender

                    countDownLatch.countDown()
                }
            }
        }

        // Done
        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        // Receiver's side
        // Makes sure it can only be decrypted by the target user

        assertNotNull(giftWrapEventToSender)
        assertNotNull(giftWrapEventToReceiver)

        val countDownDecryptLatch = CountDownLatch(2)

        giftWrapEventToSender!!.cachedGift(sender) { unwrappedMsgForSenderBySender ->
            assertEquals(SealedGossipEvent.KIND, unwrappedMsgForSenderBySender.kind)
            assertTrue(unwrappedMsgForSenderBySender is SealedGossipEvent)

            if (unwrappedMsgForSenderBySender is SealedGossipEvent) {
                unwrappedMsgForSenderBySender.cachedGossip(sender) { unwrappedGossipToSenderBySender ->
                    assertEquals("Hi There!", unwrappedGossipToSenderBySender.content)
                    countDownDecryptLatch.countDown()
                }

                unwrappedMsgForSenderBySender.cachedGossip(receiver) { _ ->
                    fail(
                        "Should not be able to decrypt msg for the sender by the sender but decrypted with receiver",
                    )
                }
            }
        }

        giftWrapEventToReceiver!!.cachedGift(sender) { _ ->
            fail("Should not be able to decrypt msg for the receiver decrypted by the sender")
        }

        giftWrapEventToSender!!.cachedGift(receiver) { _ ->
            fail("Should not be able to decrypt msg for the sender decrypted by the receiver")
        }

        giftWrapEventToReceiver!!.cachedGift(receiver) { unwrappedMsgForReceiverByReceiver ->
            assertEquals(SealedGossipEvent.KIND, unwrappedMsgForReceiverByReceiver.kind)
            assertTrue(unwrappedMsgForReceiverByReceiver is SealedGossipEvent)

            if (unwrappedMsgForReceiverByReceiver is SealedGossipEvent) {
                unwrappedMsgForReceiverByReceiver.cachedGossip(receiver) { unwrappedGossipToReceiverByReceiver ->
                    assertEquals("Hi There!", unwrappedGossipToReceiverByReceiver?.content)
                    countDownDecryptLatch.countDown()
                }

                unwrappedMsgForReceiverByReceiver.cachedGossip(sender) { unwrappedGossipToReceiverBySender ->
                    fail(
                        "Should not be able to decrypt msg for the receiver by the receiver but decrypted with the sender",
                    )
                }
            }
        }

        assertTrue(countDownDecryptLatch.await(1, TimeUnit.SECONDS))
    }

    @Test()
    fun testInternalsGroupMessage() {
        val sender = NostrSignerInternal(KeyPair())
        val receiverA = NostrSignerInternal(KeyPair())
        val receiverB = NostrSignerInternal(KeyPair())

        val countDownLatch = CountDownLatch(3)

        var giftWrapEventToSender: GiftWrapEvent? = null
        var giftWrapEventToReceiverA: GiftWrapEvent? = null
        var giftWrapEventToReceiverB: GiftWrapEvent? = null

        ChatMessageEvent.create(
            msg = "Who is going to the party tonight?",
            isDraft = false,
            to = listOf(receiverA.pubKey, receiverB.pubKey),
            signer = sender,
        ) { senderMessage ->
            SealedGossipEvent.create(
                event = senderMessage,
                encryptTo = receiverA.pubKey,
                signer = sender,
            ) { msgFromSenderToReceiverA ->
                // Should expose sender
                assertEquals(msgFromSenderToReceiverA.pubKey, sender.pubKey)
                // Should not expose receiver
                assertTrue(msgFromSenderToReceiverA.tags.isEmpty())

                GiftWrapEvent.create(
                    event = msgFromSenderToReceiverA,
                    recipientPubKey = receiverA.pubKey,
                ) { giftWrapForReceiverA ->
                    // Should not be signed by neither sender nor receiver
                    assertNotEquals(giftWrapForReceiverA.pubKey, sender.pubKey)
                    assertNotEquals(giftWrapForReceiverA.pubKey, receiverA.pubKey)
                    assertNotEquals(giftWrapForReceiverA.pubKey, receiverB.pubKey)

                    // Should not include sender as recipient
                    assertNotEquals(giftWrapForReceiverA.recipientPubKey(), sender.pubKey)

                    // Should be addressed to the receiver
                    assertEquals(giftWrapForReceiverA.recipientPubKey(), receiverA.pubKey)

                    giftWrapEventToReceiverA = giftWrapForReceiverA

                    countDownLatch.countDown()
                }
            }

            SealedGossipEvent.create(
                event = senderMessage,
                encryptTo = receiverB.pubKey,
                signer = sender,
            ) { msgFromSenderToReceiverB ->
                // Should expose sender
                assertEquals(msgFromSenderToReceiverB.pubKey, sender.pubKey)
                // Should not expose receiver
                assertTrue(msgFromSenderToReceiverB.tags.isEmpty())

                GiftWrapEvent.create(
                    event = msgFromSenderToReceiverB,
                    recipientPubKey = receiverB.pubKey,
                ) { giftWrapForReceiverB ->
                    // Should not be signed by neither sender nor receiver
                    assertNotEquals(giftWrapForReceiverB.pubKey, sender.pubKey)
                    assertNotEquals(giftWrapForReceiverB.pubKey, receiverA.pubKey)
                    assertNotEquals(giftWrapForReceiverB.pubKey, receiverB.pubKey)

                    // Should not include sender as recipient
                    assertNotEquals(giftWrapForReceiverB.recipientPubKey(), sender.pubKey)

                    // Should be addressed to the receiver
                    assertEquals(giftWrapForReceiverB.recipientPubKey(), receiverB.pubKey)

                    giftWrapEventToReceiverB = giftWrapForReceiverB

                    countDownLatch.countDown()
                }
            }

            SealedGossipEvent.create(
                event = senderMessage,
                encryptTo = sender.pubKey,
                signer = sender,
            ) { msgFromSenderToSender ->
                // Should expose sender
                assertEquals(msgFromSenderToSender.pubKey, sender.pubKey)
                // Should not expose receiver
                assertTrue(msgFromSenderToSender.tags.isEmpty())

                GiftWrapEvent.create(
                    event = msgFromSenderToSender,
                    recipientPubKey = sender.pubKey,
                ) { giftWrapToSender ->
                    // Should not be signed by neither the sender, not the receiver
                    assertNotEquals(giftWrapToSender.pubKey, sender.pubKey)
                    assertNotEquals(giftWrapToSender.pubKey, receiverA.pubKey)
                    assertNotEquals(giftWrapToSender.pubKey, receiverB.pubKey)

                    // Should not be addressed to the receiver
                    assertNotEquals(giftWrapToSender.recipientPubKey(), receiverA.pubKey)
                    assertNotEquals(giftWrapToSender.recipientPubKey(), receiverB.pubKey)
                    // Should be addressed to the sender
                    assertEquals(giftWrapToSender.recipientPubKey(), sender.pubKey)

                    giftWrapEventToSender = giftWrapToSender

                    countDownLatch.countDown()
                }
            }
        }

        // Done
        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        // Receiver's side
        // Makes sure it can only be decrypted by the target user

        assertNotNull(giftWrapEventToSender)
        assertNotNull(giftWrapEventToReceiverA)
        assertNotNull(giftWrapEventToReceiverB)

        val countDownDecryptLatch = CountDownLatch(3)

        giftWrapEventToSender?.cachedGift(sender) { unwrappedMsgForSenderBySender ->
            assertEquals(SealedGossipEvent.KIND, unwrappedMsgForSenderBySender.kind)

            if (unwrappedMsgForSenderBySender is SealedGossipEvent) {
                unwrappedMsgForSenderBySender.cachedGossip(receiverA) { unwrappedGossipToSenderByReceiverA ->
                    fail()
                }

                unwrappedMsgForSenderBySender.cachedGossip(receiverB) { unwrappedGossipToSenderByReceiverB ->
                    fail()
                }

                unwrappedMsgForSenderBySender.cachedGossip(sender) { unwrappedGossipToSenderBySender ->
                    assertEquals(
                        "Who is going to the party tonight?",
                        unwrappedGossipToSenderBySender.content,
                    )
                }
            }

            countDownDecryptLatch.countDown()
        }

        giftWrapEventToReceiverA!!.cachedGift(sender) { unwrappedMsgForReceiverBySenderA ->
            fail("Should not be able to decode msg to the receiver A with the sender's key")
        }

        giftWrapEventToReceiverB!!.cachedGift(sender) { unwrappedMsgForReceiverBySenderB ->
            fail("Should not be able to decode msg to the receiver B with the sender's key")
        }

        giftWrapEventToSender!!.cachedGift(receiverA) {
            fail("Should not be able to decode msg to sender with the receiver A's key")
        }

        giftWrapEventToReceiverA!!.cachedGift(receiverA) { unwrappedMsgForReceiverAByReceiverA ->
            assertEquals(SealedGossipEvent.KIND, unwrappedMsgForReceiverAByReceiverA.kind)

            if (unwrappedMsgForReceiverAByReceiverA is SealedGossipEvent) {
                unwrappedMsgForReceiverAByReceiverA.cachedGossip(receiverA) { unwrappedGossipToReceiverAByReceiverA ->
                    assertEquals(
                        "Who is going to the party tonight?",
                        unwrappedGossipToReceiverAByReceiverA.content,
                    )
                }

                unwrappedMsgForReceiverAByReceiverA.cachedGossip(sender) { unwrappedGossipToReceiverABySender ->
                    fail()
                }

                unwrappedMsgForReceiverAByReceiverA.cachedGossip(receiverB) { unwrappedGossipToReceiverAByReceiverB ->
                    fail()
                }
            }

            countDownDecryptLatch.countDown()
        }

        giftWrapEventToReceiverB!!.cachedGift(receiverA) {
            fail("Should not be able to decode msg to sender with the receiver A's key")
        }

        giftWrapEventToSender!!.cachedGift(receiverB) { unwrappedMsgForSenderByReceiverB ->
            fail("Should not be able to decode msg to sender with the receiver B's key")
        }
        giftWrapEventToReceiverA!!.cachedGift(receiverB) { unwrappedMsgForReceiverAByReceiverB ->
            fail("Should not be able to decode msg to receiver A with the receiver B's key")
        }
        giftWrapEventToReceiverB!!.cachedGift(receiverB) { unwrappedMsgForReceiverBByReceiverB ->
            assertEquals(SealedGossipEvent.KIND, unwrappedMsgForReceiverBByReceiverB.kind)

            if (unwrappedMsgForReceiverBByReceiverB is SealedGossipEvent) {
                unwrappedMsgForReceiverBByReceiverB.cachedGossip(receiverA) { unwrappedGossipToReceiverBByReceiverA ->
                    fail()
                }

                unwrappedMsgForReceiverBByReceiverB.cachedGossip(receiverB) { unwrappedGossipToReceiverBByReceiverB ->
                    assertEquals(
                        "Who is going to the party tonight?",
                        unwrappedGossipToReceiverBByReceiverB.content,
                    )

                    countDownDecryptLatch.countDown()
                }

                unwrappedMsgForReceiverBByReceiverB.cachedGossip(sender) { unwrappedGossipToReceiverBBySender ->
                    fail()
                }
            }
        }

        assertTrue(countDownDecryptLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testCaseFromAmethyst1() {
        val json =
            """
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

        var gossip: Event? = null

        wait1SecondForResult { onDone ->
            val privateKey = "de6152a85a0dea3b09a08a6f8139a314d498a7b52f7e5c28858b64270abd4c70"
            unwrapUnsealGossip(json, privateKey) {
                gossip = it
                onDone()
            }
        }

        assertNotNull(gossip)
        assertEquals("Hola, que tal?", gossip?.content)
    }

    @Test
    fun testCaseFromAmethyst2() {
        val json =
            """
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

        var gossip: Event? = null

        wait1SecondForResult { onDone ->
            unwrapUnsealGossip(json, privateKey) {
                gossip = it
                onDone()
            }
        }

        assertNotNull(gossip)
        assertEquals("Hola, que tal?", gossip?.content)
    }

    @Test
    fun testDecryptFromCoracle() {
        val json =
            """
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
        var gossip: Event? = null

        wait1SecondForResult { onDone ->
            unwrapUnsealGossip(json, privateKey) {
                gossip = it
                onDone()
            }
        }

        assertNotNull(gossip)
        assertEquals("test", gossip?.content)
    }

    @Test
    fun testFromCoracle2() {
        val json =
            """
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

        var gossip: Event? = null

        wait1SecondForResult { onDone ->
            unwrapUnsealGossip(json, privateKey) {
                gossip = it
                onDone()
            }
        }

        assertEquals("asdfasdfasdf", gossip?.content)
        assertEquals(1690659269L, gossip?.createdAt)
        assertEquals("827ba09d32ab81d62c60f657b350198c8aaba84372dab9ad3f4f6b8b7274b707", gossip?.id)
        assertEquals(14, gossip?.kind)
        assertEquals("subject", gossip?.tags?.firstOrNull()?.get(0))
        assertEquals("test", gossip?.tags?.firstOrNull()?.get(1))
    }

    @Test
    fun testFromCoracle3() {
        val json =
            """
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

        var gossip: Event? = null

        wait1SecondForResult { onDone ->
            unwrapUnsealGossip(json, privateKey) {
                gossip = it
                onDone()
            }
        }

        assertEquals("8d1a56008d4e31dae2fb8bef36b3efea519eff75f57033107e2aa16702466ef2", gossip?.id)
        assertEquals("Howdy", gossip?.content)
        assertEquals(1690833960L, gossip?.createdAt)
        assertEquals(14, gossip?.kind)
        assertEquals("p", gossip?.tags?.firstOrNull()?.get(0))
        assertEquals(
            "b08d8857a92b4d6aa580ff55cc3c18c4edf313c83388c34abc118621f74f1a78",
            gossip?.tags?.firstOrNull()?.get(1),
        )
        assertEquals("subject", gossip?.tags?.getOrNull(1)?.get(0))
        assertEquals("Stuff", gossip?.tags?.getOrNull(1)?.get(1))
    }

    fun unwrapUnsealGossip(
        json: String,
        privateKey: HexKey,
        onReady: (Event) -> Unit,
    ) {
        val pkBytes = NostrSignerInternal(KeyPair(privateKey.hexToByteArray()))

        val wrap = Event.fromJson(json) as GiftWrapEvent
        wrap.checkSignature()

        assertEquals(pkBytes.pubKey, wrap.recipientPubKey())

        wrap.cachedGift(pkBytes) { event ->
            if (event is SealedGossipEvent) {
                event.cachedGossip(pkBytes, onReady)
            } else {
                println(event.toJson())
                fail("Event is not a Sealed Gossip")
            }
        }
    }

    @Test
    fun decryptMsgFromNostrTools() {
        val receiversPrivateKey =
            NostrSignerInternal(
                KeyPair(Hex.decode("df51ec558372612918a83446d279d683039bece79b7a721274b1d3cb612dc6af")),
            )
        val msg =
            """
            {
              "tags": [],
              "content": "AUC1i3lHsEOYQZaqav8jAw/Dv25r6BpUX4r7ARaj/7JEqvtHkbtaWXEx3LvMlDJstNX1C90RIelgYTzxb4Xnql7zFmXtxGGd/gXOZzW/OCNWECTrhFTruZUcsyn2ssJMgEMBZKY3PgbAKykHlGCuWR3KI9bo+IA5sTqHlrwDGAysxBypRuAxTdtEApw1LSu2A+1UQsdHK/4HcW/fQLPguWGyPv09dftJIJkFWM8VYBQT7b5FeAEMhjlUM+lEmLMnx6qb07Ji/YMESkhzFlgGjHNVl1Q/BT4i6X+Skogl6Si3lWQzlS9oebUim1BQW+RO0IOyQLalZwjzGP+eE7Ry62ukQg7cPiqk62p7NNula17SF2Q8aVFLxr8WjbLXoWhZOWY25uFbTl7OPGGQb5TewRsjHoFeU4h05Ien3Ymf1VPqJVJCMIxU+yFZ1IMZh/vQW4BSx8VotRdNA05fz03ST88GzGxUvqEm4VW/Yp5q4UUkCDQTKmUImaSFmTser39WmvS5+dHY6ne4RwnrZR0ZYrG1bthRHycnPmaJiYsHn9Ox37EzgLR07pmNxr2+86NR3S3TLAVfTDN3XaXRee/7UfW/MXULVyuyweksIHOYBvANC0PxmGSs4UiFoCbwNi45DT2y0SwP6CxzDuM=",
              "kind": 1059,
              "created_at": 1694192155914,
              "pubkey": "8253eb518413b57f0df329d3d4287bdef4031fd71c32ad1952d854e703dae6a7",
              "id": "ae625fd43612127d63bfd1967ba32ae915100842a205fc2c3b3fc02ab3827f08",
              "sig": "2807a7ab5728984144676fd34686267cbe6fe38bc2f65a3640ba9243c13e8a1ae5a9a051e8852aa0c997a3623d7fa066cf2073a233c6d7db46fb1a0d4c01e5a3"
            }
            """.trimIndent()

        val wrap = Event.fromJson(msg) as GiftWrapEvent
        wrap.checkSignature()

        var event: Event? = null

        wait1SecondForResult { onDone ->
            wrap.cachedGift(receiversPrivateKey) {
                event = it
                onDone()
            }
        }

        assertNotNull(event)
    }
}

fun wait1SecondForResult(run: (onDone: () -> Unit) -> Unit) {
    val countDownLatch = CountDownLatch(1)

    run { countDownLatch.countDown() }

    assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
}
