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
package com.vitorpamplona.amethyst.commons.devtools

import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DevToolsInspectorsTest {
    private val secret = "0000000000000000000000000000000000000000000000000000000000000007"
    private val signer = NostrSignerInternal(KeyPair(secret.hexToByteArray()))
    private val pubKey = signer.pubKey

    @Test
    fun npubDecodesToItsKeyAndConvertsToNprofile() {
        val result = Nip19Inspector.inspect(NPub.create(pubKey))
        assertIs<Nip19Inspection.Decoded>(result)
        assertEquals("npub", result.prefix)
        assertEquals(Nip19Row(Nip19Field.PUBLIC_KEY, pubKey), result.rows.single())
        assertEquals(listOf("nprofile", "nostr"), result.encodings.map { it.prefix })
    }

    @Test
    fun nostrUriPrefixIsAccepted() {
        val result = Nip19Inspector.inspect("nostr:" + NPub.create(pubKey))
        assertIs<Nip19Inspection.Decoded>(result)
        assertEquals(pubKey, result.rows.single().value)
    }

    @Test
    fun nsecIsFlaggedSecretAndShowsItsPublicKey() {
        val result = Nip19Inspector.inspect(secret.hexToByteArray().toNsec())
        assertIs<Nip19Inspection.Decoded>(result)
        assertTrue(result.isSecret)
        assertEquals(pubKey, result.rows.first { it.field == Nip19Field.PUBLIC_KEY }.value)
    }

    @Test
    fun neventCarriesAuthorKindAndRelays() {
        val id = "a".repeat(64)
        val nevent = NEvent.create(id, pubKey, 1, emptyList())
        val result = Nip19Inspector.inspect(nevent)
        assertIs<Nip19Inspection.Decoded>(result)
        assertEquals(id, result.rows.first { it.field == Nip19Field.EVENT_ID }.value)
        assertEquals(pubKey, result.rows.first { it.field == Nip19Field.AUTHOR }.value)
        assertEquals("1", result.rows.first { it.field == Nip19Field.KIND }.value)
    }

    @Test
    fun naddrShowsTheATag() {
        val naddr = NAddress.create(30023, pubKey, "my-article", null)
        val result = Nip19Inspector.inspect(naddr)
        assertIs<Nip19Inspection.Decoded>(result)
        assertEquals("30023:$pubKey:my-article", result.rows.first { it.field == Nip19Field.ADDRESS }.value)
    }

    @Test
    fun rawHexOffersEveryReading() {
        val result = Nip19Inspector.inspect(pubKey.uppercase())
        assertIs<Nip19Inspection.RawHex>(result)
        assertEquals(pubKey, result.hex)
        assertEquals(NPub.create(pubKey), result.encodings.first { it.prefix == "npub" }.value)
        assertEquals(4, result.encodings.size)
    }

    @Test
    fun garbageIsInvalidAndBlankIsEmpty() {
        assertEquals(Nip19Inspection.Invalid, Nip19Inspector.inspect("hello world"))
        assertEquals(Nip19Inspection.Empty, Nip19Inspector.inspect("   "))
    }

    @Test
    fun signedEventVerifies() =
        runTest {
            val event = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, emptyArray(), "hi")
            val result = EventInspector.inspect(event.toJson())
            assertIs<EventInspectResult.Parsed>(result)
            assertTrue(result.inspection.isValid)
            assertEquals(EventSource.JSON, result.inspection.source)
            assertEquals(KindNames.nipFor(TextNoteEvent.KIND), result.inspection.kindNip)
        }

    @Test
    fun editedContentBreaksTheIdAndSignature() =
        runTest {
            val event = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, emptyArray(), "hi")
            val tampered = event.toJson().replace("\"hi\"", "\"bye\"")
            val result = EventInspector.inspect(tampered)
            assertIs<EventInspectResult.Parsed>(result)
            assertFalse(result.inspection.idValid)
            assertTrue(EventIssue.ID_MISMATCH in result.inspection.issues)
            assertEquals(event.id, result.inspection.event.id)
        }

    @Test
    fun relayFrameAndClientCommandAreUnwrapped() =
        runTest {
            val event = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, emptyArray(), "hi")
            val fromRelay = EventInspector.inspect("""["EVENT","sub1",${event.toJson()}]""")
            assertIs<EventInspectResult.Parsed>(fromRelay)
            assertEquals(EventSource.RELAY_MESSAGE, fromRelay.inspection.source)

            val fromClient = EventInspector.inspect("""["EVENT",${event.toJson()}]""")
            assertIs<EventInspectResult.Parsed>(fromClient)
            assertEquals(EventSource.CLIENT_COMMAND, fromClient.inspection.source)
        }

    @Test
    fun nembedIsUnwrapped() =
        runTest {
            val event = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, emptyArray(), "hi")
            val result = EventInspector.inspect(NEmbed.create(event))
            assertIs<EventInspectResult.Parsed>(result)
            assertEquals(EventSource.NEMBED, result.inspection.source)
            assertTrue(result.inspection.isValid)
        }

    @Test
    fun notJsonIsUnparseable() {
        assertIs<EventInspectResult.Unparseable>(EventInspector.inspect("{not json"))
        assertIs<EventInspectResult.Unparseable>(EventInspector.inspect("""["NOTICE","hi"]"""))
    }

    @Test
    fun consoleReadsFrameLabelsAndChallenges() {
        assertEquals("EVENT", RelayConsoleViewModel.labelOf("""["EVENT","s",{}]"""))
        assertEquals("EOSE", RelayConsoleViewModel.labelOf("""  [ "EOSE", "s"]"""))
        assertEquals(null, RelayConsoleViewModel.labelOf("""{"id":"x"}"""))
        assertEquals("abc-123", RelayConsoleViewModel.challengeOf("""["AUTH","abc-123"]"""))
    }
}
