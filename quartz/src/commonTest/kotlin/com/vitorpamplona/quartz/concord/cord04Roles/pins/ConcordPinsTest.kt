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
package com.vitorpamplona.quartz.concord.cord04Roles.pins

import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeys
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordPinsTest {
    private val alice = NostrSignerInternal(KeyPair())
    private val mallory = NostrSignerInternal(KeyPair())
    private val root = ByteArray(32) { 7 }
    private val channelA = "aa".repeat(32)
    private val channelB = "bb".repeat(32)
    private val plane = ConcordChannelKeys.publicChannel(root, channelA.hexToByteArray(), 3)

    private suspend fun sendAndOpen(
        rumor: Event,
        signer: NostrSignerInternal = alice,
        encrypted: Boolean = true,
    ) = ConcordStreamEnvelope.wrap(rumor, plane, signer, encrypted = encrypted).let { wrap -> wrap.id to ConcordStreamEnvelope.open(wrap, plane) }

    @Test
    fun theCoordinateMatchesTheSpecDerivation() {
        // A.6: hkdf(ikm = community_id, salt = empty, info = "concord/pins" ‖ 0x00 ‖ channel_id), 32 bytes.
        // Expected value computed independently with Node's crypto.hkdfSync.
        assertEquals(
            "13ff5d549aa39c9f11993e2066c9122700a622553fddf22e68c107d3ea00d242",
            ConcordPinLists.coordinate("11".repeat(32), "22".repeat(32)),
        )
    }

    @Test
    fun aPinnedMessageVerifiesFromTheEntryAlone() =
        runTest {
            val rumor = ChannelChat.message(alice.pubKey, channelA, 3, "ship it", 1_700_000_000)
            val (wrapId, opened) = sendAndOpen(rumor)
            val entry = assertNotNull(ConcordPins.buildEntry(opened, plane.conversationKey, channelA, wrapId))

            // A reader holding nothing but the entry and the list's channel.
            val pin = assertNotNull(ConcordPins.verify(entry, channelA))
            assertEquals(rumor.id, pin.rumorId)
            assertEquals(alice.pubKey, pin.author)
            assertEquals("ship it", pin.content)
            assertEquals("3", pin.epoch)
            assertEquals(wrapId, pin.wrapHint)
            assertFalse(pin.edited)
        }

    @Test
    fun theDisclosureIsOneMessagesKeysNotTheConversationKey() =
        runTest {
            val (_, opened) = sendAndOpen(ChannelChat.message(alice.pubKey, channelA, 3, "hi", 1))
            val entry = ConcordPins.buildEntry(opened, plane.conversationKey, channelA, null)!!
            val keys = (entry["keys"] as JsonPrimitive).content
            assertEquals(152, keys.length)
            assertFalse(keys.contains(plane.conversationKey.toHexKey()))
            // The same keys do not open a second message on the same plane.
            val (_, other) = sendAndOpen(ChannelChat.message(alice.pubKey, channelA, 3, "other", 2))
            assertNull(PinKeyDisclosure.decryptWith(other.seal.content, PinKeyDisclosure.decode(keys)!!))
        }

    @Test
    fun aPinReplayedIntoAnotherChannelsListFails() =
        runTest {
            val (_, opened) = sendAndOpen(ChannelChat.message(alice.pubKey, channelA, 3, "private words", 1))
            val entry = ConcordPins.buildEntry(opened, plane.conversationKey, channelA, null)!!
            assertNull(ConcordPins.verify(entry, channelB), "the channel binding is step 4")
        }

    @Test
    fun tamperedKeysOrSealAreDroppedAlone() =
        runTest {
            val (_, opened) = sendAndOpen(ChannelChat.message(alice.pubKey, channelA, 3, "x", 1))
            val entry = ConcordPins.buildEntry(opened, plane.conversationKey, channelA, null)!!
            val badKeys = JsonObject(entry + ("keys" to JsonPrimitive("00".repeat(76))))
            assertNull(ConcordPins.verify(badKeys, channelA))
            val seal = entry["seal"] as JsonObject
            val forgedSeal = JsonObject(seal + ("pubkey" to JsonPrimitive(mallory.pubKey)))
            assertNull(ConcordPins.verify(JsonObject(entry + ("seal" to forgedSeal)), channelA))
            assertNull(ConcordPins.verify(JsonObject(entry - "keys"), channelA))
        }

    @Test
    fun onlyMessagesAndRepliesArePinnable() =
        runTest {
            val reaction = ChannelChat.reaction(alice.pubKey, channelA, 3, "cc".repeat(32), alice.pubKey, 9, "+", 1)
            val (_, opened) = sendAndOpen(reaction)
            assertNull(ConcordPins.buildEntry(opened, plane.conversationKey, channelA, null))
        }

    @Test
    fun aPlaintextSealCannotBePinned() =
        runTest {
            val (_, opened) = sendAndOpen(ChannelChat.message(alice.pubKey, channelA, 3, "x", 1), encrypted = false)
            assertNull(ConcordPins.buildEntry(opened, plane.conversationKey, channelA, null))
        }

    @Test
    fun anEditByTheAuthorRevisesThePinAndAForeignEditIsIgnored() =
        runTest {
            val original = ChannelChat.message(alice.pubKey, channelA, 3, "teh plan", 1)
            val (_, opened) = sendAndOpen(original)
            val entry = ConcordPins.buildEntry(opened, plane.conversationKey, channelA, null)!!

            val (_, edit) = sendAndOpen(ChannelChat.edit(alice.pubKey, channelA, 3, original.id, "the plan", 2))
            val edited = ConcordPins.withEdit(entry, edit, plane.conversationKey, channelA)
            val pin = ConcordPins.verify(edited, channelA)!!
            assertTrue(pin.edited)
            assertEquals("the plan", pin.content)
            assertEquals(2_000L, pin.editOrderMs, "the proven Edit's send time, to judge a newer local Edit against")
            assertNull(ConcordPins.verify(entry, channelA)!!.editOrderMs)
            assertEquals(original.id, pin.rumorId, "the identity stays the original's")

            val (_, foreign) = sendAndOpen(ChannelChat.edit(mallory.pubKey, channelA, 3, original.id, "pwned", 3), signer = mallory)
            assertEquals(edited, ConcordPins.withEdit(edited, foreign, plane.conversationKey, channelA), "an unprovable edit never downgrades the entry")
        }

    @Test
    fun publicAndSealedListsRoundTrip() =
        runTest {
            val (_, opened) = sendAndOpen(ChannelChat.message(alice.pubKey, channelA, 3, "pinned", 1))
            val entry = ConcordPins.buildEntry(opened, plane.conversationKey, channelA, null)!!

            val public = ConcordPins.read(ConcordPins.serializePublic(listOf(entry))) { null }
            assertEquals(listOf(entry), public.entries)

            val sealed = ConcordPins.serializeSealed(listOf(entry), plane.conversationKey, 3)
            val opened3 = ConcordPins.read(sealed) { epoch -> plane.conversationKey.takeIf { epoch == 3L } }
            assertEquals(listOf(entry), opened3.entries)
            assertTrue(ConcordPins.isSealedForm(sealed))
            assertFalse(ConcordPins.isSealedForm(ConcordPins.serializePublic(listOf(entry))))
            assertFalse(ConcordPins.isSealedForm("not json"))
            val noKey = ConcordPins.read(sealed) { null }
            assertTrue(noKey.sealedUnavailable, "unreadable is not empty: a writer must not build on it")
            assertTrue(noKey.entries.isEmpty())

            // The read reports the form itself, matching isSealedForm, so nobody parses twice.
            for (content in listOf(sealed, ConcordPins.serializePublic(listOf(entry)), "not json", """{"sealed":1}""", """{"epoch":"x","sealed":"y"}""")) {
                assertEquals(ConcordPins.isSealedForm(content), ConcordPins.read(content) { plane.conversationKey }.sealedForm, content)
                assertEquals(ConcordPins.isSealedForm(content), ConcordPins.read(content) { null }.sealedForm, content)
            }
        }

    @Test
    fun capsReadAsEmptyAndRefuseToWrite() =
        runTest {
            val (_, opened) = sendAndOpen(ChannelChat.message(alice.pubKey, channelA, 3, "p", 1))
            val entry = ConcordPins.buildEntry(opened, plane.conversationKey, channelA, null)!!
            val tooMany = List(ConcordPins.MAX_ENTRIES + 1) { entry }
            assertFailsWith<ConcordPins.PinListTooLargeException> { ConcordPins.serializePublic(tooMany) }
            val handMade = """{"entries":[${tooMany.joinToString(",") { it.toString() }}]}"""
            assertTrue(ConcordPins.read(handMade) { null }.violating)
            assertTrue(ConcordPins.read("x".repeat(ConcordPins.MAX_CONTENT_BYTES + 1)) { null }.violating)
            assertTrue(ConcordPins.read("not json") { null }.violating)
        }

    @Test
    fun onlyTheAuthorsDeleteKillsAPin() =
        runTest {
            val rumor = ChannelChat.message(alice.pubKey, channelA, 3, "oops", 1)
            val (_, opened) = sendAndOpen(rumor)
            val pin = ConcordPins.verify(ConcordPins.buildEntry(opened, plane.conversationKey, channelA, null)!!, channelA)!!
            val tags = arrayOf(arrayOf("e", rumor.id), arrayOf("k", "9"))
            assertTrue(ConcordPins.killedBy(pin, alice.pubKey, tags))
            assertFalse(ConcordPins.killedBy(pin, mallory.pubKey, tags))
        }
}
