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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests the one place the trust boundary parses untrusted applet input ([NappletProtocolJson]).
 * Runs as a plain JVM unit test — the codec uses kotlinx.serialization, not Android's `org.json`.
 */
class NappletProtocolJsonTest {
    private val json = Json

    private inline fun assertThrowsAny(block: () -> Unit) {
        try {
            block()
            fail("Expected an exception")
        } catch (_: Exception) {
            // expected
        }
    }

    private fun sampleEvent() =
        Event(
            id = "a".repeat(64),
            pubKey = "b".repeat(64),
            createdAt = 1_700_000_000L,
            kind = 1,
            tags = arrayOf(arrayOf("t", "napplet")),
            content = "hello",
            sig = "c".repeat(128),
        )

    // ---- decode requests ----

    @Test
    fun decodesGetPublicKey() {
        assertEquals(NappletRequest.GetPublicKey, NappletProtocolJson.decodeRequest("""{"op":"getPublicKey"}"""))
    }

    @Test
    fun decodesSignEventWithTagsAndContent() {
        val req = NappletProtocolJson.decodeRequest("""{"op":"signEvent","kind":1,"tags":[["t","napplet"],["e","abc"]],"content":"gm"}""")
        assertEquals(NappletRequest.SignEvent(1, arrayOf(arrayOf("t", "napplet"), arrayOf("e", "abc")), "gm"), req)
    }

    @Test
    fun decodesSignEventWithMissingContentAsEmpty() {
        val req = NappletProtocolJson.decodeRequest("""{"op":"signEvent","kind":7}""")
        assertEquals(NappletRequest.SignEvent(7, emptyArray(), ""), req)
    }

    @Test
    fun decodesEncryptDecryptOps() {
        assertEquals(NappletRequest.Nip04Encrypt("pk", "hi"), NappletProtocolJson.decodeRequest("""{"op":"nip04Encrypt","peer":"pk","plaintext":"hi"}"""))
        assertEquals(NappletRequest.Nip04Decrypt("pk", "ct"), NappletProtocolJson.decodeRequest("""{"op":"nip04Decrypt","peer":"pk","ciphertext":"ct"}"""))
        assertEquals(NappletRequest.Nip44Encrypt("pk", "hi"), NappletProtocolJson.decodeRequest("""{"op":"nip44Encrypt","peer":"pk","plaintext":"hi"}"""))
        assertEquals(NappletRequest.Nip44Decrypt("pk", "ct"), NappletProtocolJson.decodeRequest("""{"op":"nip44Decrypt","peer":"pk","ciphertext":"ct"}"""))
    }

    @Test
    fun decodesPublishPreservingTheEvent() {
        val ev = sampleEvent()
        val req = NappletProtocolJson.decodeRequest("""{"op":"publish","event":${ev.toJson()}}""") as NappletRequest.Publish
        assertEquals(ev.id, req.event.id)
        assertEquals(ev.pubKey, req.event.pubKey)
        assertEquals("hello", req.event.content)
    }

    @Test
    fun decodesQueryEventsFilter() {
        val req =
            NappletProtocolJson.decodeRequest(
                """{"op":"queryEvents","filter":{"kinds":[1,30023],"authors":["aa"],"#t":["nostr"],"since":100,"limit":20}}""",
            ) as NappletRequest.QueryEvents
        val f = req.filter
        assertEquals(listOf(1, 30023), f.kinds)
        assertEquals(listOf("aa"), f.authors)
        assertEquals(listOf("nostr"), f.tags?.get("t"))
        assertEquals(100L, f.since)
        assertEquals(20, f.limit)
    }

    @Test
    fun decodesStorageOps() {
        assertEquals(NappletRequest.StorageGet("k"), NappletProtocolJson.decodeRequest("""{"op":"storageGet","key":"k"}"""))
        assertEquals(NappletRequest.StorageSet("k", "v"), NappletProtocolJson.decodeRequest("""{"op":"storageSet","key":"k","value":"v"}"""))
        assertEquals(NappletRequest.StorageRemove("k"), NappletProtocolJson.decodeRequest("""{"op":"storageRemove","key":"k"}"""))
    }

    @Test
    fun decodesPayInvoice() {
        assertEquals(NappletRequest.PayInvoice("lnbc1"), NappletProtocolJson.decodeRequest("""{"op":"payInvoice","invoice":"lnbc1"}"""))
    }

    @Test
    fun unknownOpDecodesToNull() {
        assertNull(NappletProtocolJson.decodeRequest("""{"op":"deleteEverything"}"""))
        assertNull(NappletProtocolJson.decodeRequest("""{"foo":"bar"}"""))
    }

    @Test
    fun malformedBodyThrows() {
        assertThrowsAny { NappletProtocolJson.decodeRequest("not json at all") }
    }

    @Test
    fun missingRequiredFieldThrows() {
        // signEvent without the required `kind` must not silently succeed.
        assertThrowsAny { NappletProtocolJson.decodeRequest("""{"op":"signEvent","content":"x"}""") }
    }

    // ---- encode responses ----

    @Test
    fun encodesPublicKey() {
        val o = json.parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.PublicKey("pk"))).jsonObject
        assertEquals("publicKey", o["type"]?.jsonPrimitive?.content)
        assertEquals("pk", o["pubkey"]?.jsonPrimitive?.content)
    }

    @Test
    fun encodesSignedEventAsNestedObject() {
        val o = json.parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.SignedEvent(sampleEvent()))).jsonObject
        assertEquals("signedEvent", o["type"]?.jsonPrimitive?.content)
        assertEquals(
            "a".repeat(64),
            o["event"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun encodesEventsArray() {
        val o = json.parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.Events(listOf(sampleEvent())))).jsonObject
        assertEquals("events", o["type"]?.jsonPrimitive?.content)
        assertEquals(1, o["events"]?.jsonArray?.size)
        assertEquals(
            "a".repeat(64),
            o["events"]
                ?.jsonArray
                ?.get(0)
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun encodesPublished() {
        val o = json.parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.Published(listOf("wss://a", "wss://b")))).jsonObject
        assertEquals("published", o["type"]?.jsonPrimitive?.content)
        assertEquals(2, o["relays"]?.jsonArray?.size)
    }

    @Test
    fun encodesStorageValueWithNullAsJsonNull() {
        val present = json.parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.StorageValue("v"))).jsonObject
        assertEquals("v", present["value"]?.jsonPrimitive?.content)

        val absent = json.parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.StorageValue(null))).jsonObject
        assertEquals(JsonNull, absent["value"])
    }

    @Test
    fun encodesPaidAndDone() {
        val paid = json.parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.Paid(null))).jsonObject
        assertEquals("paid", paid["type"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, paid["preimage"])

        val done = json.parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.Done)).jsonObject
        assertEquals("done", done["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun encodesDeniedWithCapabilityName() {
        val o =
            json
                .parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.Denied(NappletCapability.IDENTITY, "nope")))
                .jsonObject
        assertEquals("denied", o["type"]?.jsonPrimitive?.content)
        assertEquals("IDENTITY", o["capability"]?.jsonPrimitive?.content)
        assertEquals("nope", o["reason"]?.jsonPrimitive?.content)
    }

    @Test
    fun encodesUnsupportedAndFailed() {
        val unsupported = json.parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.Unsupported("payInvoice"))).jsonObject
        assertEquals("unsupported", unsupported["type"]?.jsonPrimitive?.content)
        assertEquals("payInvoice", unsupported["operation"]?.jsonPrimitive?.content)

        val failed = json.parseToJsonElement(NappletProtocolJson.encodeResponse(NappletResponse.Failed("boom"))).jsonObject
        assertEquals("failed", failed["type"]?.jsonPrimitive?.content)
        assertEquals("boom", failed["reason"]?.jsonPrimitive?.content)
    }

    @Test
    fun signedEventRoundTripsThroughEventFromJson() {
        // The encoded event must be parseable back into an Event (the applet's wire <-> our model).
        val encoded = NappletProtocolJson.encodeResponse(NappletResponse.SignedEvent(sampleEvent()))
        val eventJson =
            json
                .parseToJsonElement(encoded)
                .jsonObject
                .getValue("event")
                .jsonObject
                .toString()
        val restored = Event.fromJson(eventJson)
        assertEquals("a".repeat(64), restored.id)
        assertEquals(1, restored.kind)
        assertTrue(restored.tags.any { it.firstOrNull() == "t" })
    }
}
