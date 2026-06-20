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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests the one place the trust boundary parses untrusted applet input ([NappletProtocolJson]).
 * Verifies the upstream `{type:"domain.action", id}` envelope round-trips. Runs as a plain JVM
 * unit test — the codec uses kotlinx.serialization, not Android's `org.json`.
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

    // ---- decode requests (upstream "domain.action" envelope) ----

    @Test
    fun decodesGetPublicKey() {
        assertEquals(NappletRequest.GetPublicKey, NappletProtocolJson.decodeRequest("""{"type":"identity.getPublicKey","id":"1"}"""))
    }

    @Test
    fun decodesShellSupports() {
        assertEquals(NappletRequest.ShellSupports("relay"), NappletProtocolJson.decodeRequest("""{"type":"shell.supports","id":"1","domain":"relay"}"""))
    }

    @Test
    fun decodesSignEventUnderKeysDomain() {
        val req = NappletProtocolJson.decodeRequest("""{"type":"keys.signEvent","id":"1","kind":1,"tags":[["t","x"]],"content":"gm"}""")
        assertEquals(NappletRequest.SignEvent(1, arrayOf(arrayOf("t", "x")), "gm"), req)
    }

    @Test
    fun decodesEncryptDecryptUnderKeysDomain() {
        assertEquals(NappletRequest.Nip04Encrypt("pk", "hi"), NappletProtocolJson.decodeRequest("""{"type":"keys.nip04Encrypt","peer":"pk","plaintext":"hi"}"""))
        assertEquals(NappletRequest.Nip44Decrypt("pk", "ct"), NappletProtocolJson.decodeRequest("""{"type":"keys.nip44Decrypt","peer":"pk","ciphertext":"ct"}"""))
    }

    @Test
    fun decodesPublishPreservingTheEvent() {
        val ev = sampleEvent()
        val req = NappletProtocolJson.decodeRequest("""{"type":"relay.publish","id":"1","event":${ev.toJson()}}""") as NappletRequest.Publish
        assertEquals(ev.id, req.event.id)
        assertEquals("hello", req.event.content)
    }

    @Test
    fun decodesQueryFromFilterObjectOrFiltersArray() {
        val single = NappletProtocolJson.decodeRequest("""{"type":"relay.query","filter":{"kinds":[1],"#t":["nostr"],"limit":5}}""") as NappletRequest.QueryEvents
        assertEquals(listOf(1), single.filter.kinds)
        assertEquals(listOf("nostr"), single.filter.tags?.get("t"))
        assertEquals(5, single.filter.limit)

        val array = NappletProtocolJson.decodeRequest("""{"type":"relay.query","filters":[{"authors":["aa"]}]}""") as NappletRequest.QueryEvents
        assertEquals(listOf("aa"), array.filter.authors)
    }

    @Test
    fun decodesStorageOps() {
        assertEquals(NappletRequest.StorageGet("k"), NappletProtocolJson.decodeRequest("""{"type":"storage.get","key":"k"}"""))
        assertEquals(NappletRequest.StorageSet("k", "v"), NappletProtocolJson.decodeRequest("""{"type":"storage.set","key":"k","value":"v"}"""))
        assertEquals(NappletRequest.StorageRemove("k"), NappletProtocolJson.decodeRequest("""{"type":"storage.remove","key":"k"}"""))
    }

    @Test
    fun decodesValueResourceUpload() {
        assertEquals(NappletRequest.PayInvoice("lnbc1"), NappletProtocolJson.decodeRequest("""{"type":"value.payInvoice","invoice":"lnbc1"}"""))
        assertEquals(NappletRequest.ResourceBytes("https://x"), NappletProtocolJson.decodeRequest("""{"type":"resource.bytes","url":"https://x"}"""))
        // "SGk=" is base64 for "Hi"
        val up = NappletProtocolJson.decodeRequest("""{"type":"upload","bytes":"SGk=","contentType":"text/plain"}""") as NappletRequest.UploadBlob
        assertEquals("text/plain", up.contentType)
        assertEquals("Hi", up.bytes.decodeToString())
    }

    @Test
    fun unknownTypeDecodesToNull() {
        assertNull(NappletProtocolJson.decodeRequest("""{"type":"inc.emit","id":"1"}"""))
        assertNull(NappletProtocolJson.decodeRequest("""{"foo":"bar"}"""))
    }

    @Test
    fun malformedOrMissingFieldThrows() {
        assertThrowsAny { NappletProtocolJson.decodeRequest("not json") }
        assertThrowsAny { NappletProtocolJson.decodeRequest("""{"type":"keys.signEvent","content":"x"}""") }
    }

    @Test
    fun readTypeReturnsTheDiscriminant() {
        assertEquals("relay.publish", NappletProtocolJson.readType("""{"type":"relay.publish","id":"9"}"""))
    }

    // ---- encode responses (".result" envelope) ----

    @Test
    fun encodesSuccessWithResultTypeAndOkTrue() {
        val o = json.parseToJsonElement(NappletProtocolJson.encodeResponse("identity.getPublicKey", NappletResponse.PublicKey("pk"))).jsonObject
        assertEquals("identity.getPublicKey.result", o["type"]?.jsonPrimitive?.content)
        assertTrue(o["ok"]!!.jsonPrimitive.boolean)
        assertEquals("pk", o["pubkey"]?.jsonPrimitive?.content)
    }

    @Test
    fun encodesSupported() {
        val o = json.parseToJsonElement(NappletProtocolJson.encodeResponse("shell.supports", NappletResponse.Supported(true))).jsonObject
        assertEquals("shell.supports.result", o["type"]?.jsonPrimitive?.content)
        assertTrue(o["supported"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun encodesSignedEventAndEvents() {
        val signed = json.parseToJsonElement(NappletProtocolJson.encodeResponse("keys.signEvent", NappletResponse.SignedEvent(sampleEvent()))).jsonObject
        assertEquals(
            "a".repeat(64),
            signed["event"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )

        val events = json.parseToJsonElement(NappletProtocolJson.encodeResponse("relay.query", NappletResponse.Events(listOf(sampleEvent())))).jsonObject
        assertEquals(1, events["events"]?.jsonArray?.size)
    }

    @Test
    fun encodesStorageNullAsJsonNull() {
        val absent = json.parseToJsonElement(NappletProtocolJson.encodeResponse("storage.get", NappletResponse.StorageValue(null))).jsonObject
        assertEquals(JsonNull, absent["value"])
    }

    @Test
    fun encodesBytesAsBase64() {
        val o = json.parseToJsonElement(NappletProtocolJson.encodeResponse("resource.bytes", NappletResponse.Bytes("Hi".encodeToByteArray(), "text/plain"))).jsonObject
        assertEquals("SGk=", o["bytes"]?.jsonPrimitive?.content)
        assertEquals("text/plain", o["contentType"]?.jsonPrimitive?.content)
    }

    @Test
    fun encodesErrorsWithOkFalse() {
        val denied = json.parseToJsonElement(NappletProtocolJson.encodeResponse("keys.signEvent", NappletResponse.Denied(NappletCapability.KEYS, "no"))).jsonObject
        assertFalse(denied["ok"]!!.jsonPrimitive.boolean)
        assertEquals("denied", denied["error"]?.jsonPrimitive?.content)
        assertEquals("KEYS", denied["capability"]?.jsonPrimitive?.content)

        val unsupported = json.parseToJsonElement(NappletProtocolJson.encodeResponse("upload", NappletResponse.Unsupported("upload"))).jsonObject
        assertFalse(unsupported["ok"]!!.jsonPrimitive.boolean)
        assertEquals("unsupported", unsupported["error"]?.jsonPrimitive?.content)

        val failed = json.parseToJsonElement(NappletProtocolJson.encodeResponse("relay.publish", NappletResponse.Failed("boom"))).jsonObject
        assertEquals("boom", failed["reason"]?.jsonPrimitive?.content)
    }
}
