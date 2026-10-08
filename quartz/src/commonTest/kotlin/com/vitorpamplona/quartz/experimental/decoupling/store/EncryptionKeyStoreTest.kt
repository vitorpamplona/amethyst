/*
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.quartz.experimental.decoupling.store

import com.vitorpamplona.quartz.experimental.decoupling.DecoupledCipher
import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.request.EncryptionKeyRequestEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.response.EncryptionKeyTransferEvent
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The NIP-4E lifecycle end to end: Alice's first device generates the encryption key, her second
 * device asks for it (4454) and receives it (4455), and both can then read and write messages Bob
 * sends to the key Alice announced in her kind 10044.
 */
class EncryptionKeyStoreTest {
    private val alice = NostrSignerInternal(KeyPair())
    private val bob = NostrSignerInternal(KeyPair())

    @Test
    fun theStoreKeepsKeysPerOwnerUnderTheirOwnPublicKey() =
        runTest {
            val store = InMemoryEncryptionKeyStore()
            val key = KeyPair()
            val pub = store.put(alice.pubKey, key.privKey!!)

            assertEquals(key.pubKey.toHexKey(), pub)
            assertContentEquals(key.privKey, store.get(alice.pubKey, pub))
            assertNull(store.get(bob.pubKey, pub), "keys are scoped to their owner")
            assertTrue(store.delete(alice.pubKey, pub))
            assertNull(store.get(alice.pubKey, pub))
            assertFalse(store.delete(alice.pubKey, pub))
        }

    @Test
    fun aKeyTravelsToANewDeviceAndBothDevicesReadAndWrite() =
        runTest {
            val firstDevice = InMemoryEncryptionKeyStore()
            val secondDevice = InMemoryEncryptionKeyStore()

            // Device 1: generate and announce.
            val encryptionPub = firstDevice.generate(alice.pubKey)
            val aliceList = alice.sign(EncryptionKeyListEvent.build(encryptionPub))
            val bobList = bob.sign(EncryptionKeyListEvent.build(emptyList()))

            // Device 2: ask for the key with a fresh client key.
            val client2 = KeyPair()
            val request = alice.sign(EncryptionKeyRequestEvent.build(client2.pubKey.toHexKey()))

            // Device 1 answers (after the user compared request.authorizationCode()).
            val client1 = KeyPair()
            val transfer = alice.sign(assertNotNull(firstDevice.answerRequest(request, aliceList, client1.privKey!!)))

            // Device 2 accepts it.
            assertEquals(encryptionPub, secondDevice.acceptTransfer(transfer, client2.privKey!!, aliceList))

            // Bob writes to Alice's announced key; the second device reads it.
            val bobCipher = DecoupledCipher()
            val toAlice = assertNotNull(bobCipher.encrypt("hi alice", alice.pubKey, bobList, aliceList, bob))
            val onSecondDevice = DecoupledCipher(secondDevice).decrypt(toAlice, bob.pubKey, encryptionPub, bobList, aliceList, alice)
            assertEquals("hi alice", onSecondDevice)

            // The first device writes back from the encryption key; Bob reads it with his identity.
            val toBob = assertNotNull(DecoupledCipher(firstDevice).encrypt("hi bob", bob.pubKey, aliceList, bobList, alice))
            assertEquals("hi bob", bobCipher.decrypt(toBob, alice.pubKey, bob.pubKey, aliceList, bobList, bob))
        }

    @Test
    fun aDeviceWithoutTheAnnouncedKeyNeitherWritesNorReads() =
        runTest {
            val holder = InMemoryEncryptionKeyStore()
            val encryptionPub = holder.generate(alice.pubKey)
            val aliceList = alice.sign(EncryptionKeyListEvent.build(encryptionPub))
            val bobList = bob.sign(EncryptionKeyListEvent.build(emptyList()))

            val empty = DecoupledCipher(InMemoryEncryptionKeyStore())
            // Falling back to the identity key would write a message Bob decrypts against the wrong key.
            assertNull(empty.encrypt("hi", bob.pubKey, aliceList, bobList, alice))

            val toAlice = assertNotNull(DecoupledCipher().encrypt("hi", alice.pubKey, bobList, aliceList, bob))
            assertNull(empty.decrypt(toAlice, bob.pubKey, encryptionPub, bobList, aliceList, alice))
        }

    @Test
    fun transfersThatDoNotMatchTheAnnouncedKeyAreRefused() =
        runTest {
            val holder = InMemoryEncryptionKeyStore()
            val announced = holder.generate(alice.pubKey)
            val aliceList = alice.sign(EncryptionKeyListEvent.build(announced))

            // A transfer carrying some other key (e.g. a rotated-out one) is ignored.
            val other = KeyPair()
            val client2 = KeyPair()
            val stale =
                alice.sign(
                    EncryptionKeyTransferEvent.build(
                        encryptionPrivKey = other.privKey!!.toHexKey(),
                        senderClientPrivKey = KeyPair().privKey!!,
                        requesterClientPubKey = client2.pubKey.toHexKey(),
                    ),
                )
            val receiver = InMemoryEncryptionKeyStore()
            assertNull(receiver.acceptTransfer(stale, client2.privKey!!, aliceList))

            // A transfer signed by someone else is ignored even if it carries the right key.
            val request = alice.sign(EncryptionKeyRequestEvent.build(client2.pubKey.toHexKey()))
            val genuine = assertNotNull(holder.answerRequest(request, aliceList, KeyPair().privKey!!))
            val forged = bob.sign(genuine)
            assertNull(receiver.acceptTransfer(forged, client2.privKey!!, aliceList))
            assertNull(receiver.get(alice.pubKey, announced))
        }

    @Test
    fun requestsAreOnlyAnsweredWithKeysThisDeviceHolds() =
        runTest {
            val aliceList = alice.sign(EncryptionKeyListEvent.build(KeyPair().pubKey.toHexKey()))
            val request = alice.sign(EncryptionKeyRequestEvent.build(KeyPair().pubKey.toHexKey()))
            assertNull(InMemoryEncryptionKeyStore().answerRequest(request, aliceList, KeyPair().privKey!!))

            // Someone else's request for Alice's key gets nothing.
            val holder = InMemoryEncryptionKeyStore()
            val announced = holder.generate(alice.pubKey)
            val list = alice.sign(EncryptionKeyListEvent.build(announced))
            val bobsRequest = bob.sign(EncryptionKeyRequestEvent.build(KeyPair().pubKey.toHexKey()))
            assertNull(holder.answerRequest(bobsRequest, list, KeyPair().privKey!!))
        }
}
