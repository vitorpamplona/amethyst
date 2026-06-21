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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-contract conformance against `@napplet/nap@0.15.0` + `@napplet/core@0.15.0`. Each test
 * pins [NappletProtocolJson] to the SDK's *exact* request envelope and result field names, so a
 * regression that drifts from the published message types fails CI. The "gap guard" tests document
 * the known non-conformant surface (see `plans/2026-06-21-napplet-sdk-conformance-audit.md`): when
 * one of those is fixed, its guard flips intentionally and is updated alongside the fix.
 *
 * Field names are quoted from the canonical `<domain>/types.d.ts` message interfaces.
 */
class NappletSdkConformanceTest {
    private val json = Json

    private fun result(
        requestType: String,
        response: NappletResponse,
    ) = json.parseToJsonElement(NappletProtocolJson.encodeResponse(requestType, response)).jsonObject

    private fun sampleEvent() =
        Event(
            id = "a".repeat(64),
            pubKey = "b".repeat(64),
            createdAt = 1_700_000_000L,
            kind = 1,
            tags = arrayOf(arrayOf("t", "x")),
            content = "gm",
            sig = "c".repeat(128),
        )

    // ---------- relay (Relay*Message) ----------

    @Test
    fun relayPublishRequestCarriesTheTemplateInTheEventField() {
        // RelayPublishMessage: { type:'relay.publish', id, event }
        val req = NappletProtocolJson.decodeRequest("""{"type":"relay.publish","id":"1","event":{"kind":1,"tags":[["t","x"]],"content":"gm"}}""")
        assertEquals(NappletRequest.Publish(1, arrayOf(arrayOf("t", "x")), "gm"), req)
    }

    @Test
    fun relayPublishEncryptedRequestMatches() {
        // RelayPublishEncryptedMessage: { type:'relay.publishEncrypted', id, event, recipient, encryption? }
        val req = NappletProtocolJson.decodeRequest("""{"type":"relay.publishEncrypted","id":"1","event":{"kind":4,"tags":[],"content":"hi"},"recipient":"pk","encryption":"nip04"}""")
        assertEquals(NappletRequest.PublishEncrypted(4, emptyArray(), "hi", "pk", "nip04"), req)
    }

    @Test
    fun relayQueryAndSubscribeReadTheFiltersArray() {
        // RelayQueryMessage: { type:'relay.query', id, filters: NostrFilter[] }
        val q = NappletProtocolJson.decodeRequest("""{"type":"relay.query","id":"1","filters":[{"kinds":[1],"authors":["aa"]}]}""") as NappletRequest.QueryEvents
        assertEquals(listOf(1), q.filter.kinds)
        assertEquals(listOf("aa"), q.filter.authors)

        // RelaySubscribeMessage: { type:'relay.subscribe', id, subId, filters, relay? }
        val s = NappletProtocolJson.decodeRequest("""{"type":"relay.subscribe","id":"1","subId":"s1","filters":[{"kinds":[7]}]}""") as NappletRequest.Subscribe
        assertEquals(listOf(7), s.filter.kinds)
        // The subId the pushes are keyed by is read from the raw envelope by the service.
        assertEquals("s1", NappletProtocolJson.readSubId("""{"type":"relay.subscribe","id":"1","subId":"s1","filters":[{}]}"""))
    }

    @Test
    fun relayPublishResultCarriesEventAndEventId() {
        // RelayPublishResultMessage: { type:'relay.publish.result', id, ok, event?, eventId?, error? }
        val o = result("relay.publish", NappletResponse.Published(sampleEvent(), listOf("wss://r")))
        assertEquals("relay.publish.result", o["type"]?.jsonPrimitive?.content)
        assertEquals(
            "a".repeat(64),
            o["event"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("a".repeat(64), o["eventId"]?.jsonPrimitive?.content)
    }

    @Test
    fun relayQueryResultCarriesEvents() {
        // RelayQueryResultMessage: { type:'relay.query.result', id, events, error? }
        val o = result("relay.query", NappletResponse.Events(listOf(sampleEvent())))
        assertEquals(1, o["events"]?.jsonArray?.size)
    }

    @Test
    fun relayEventAndEosePushesMatchTheSdk() {
        // RelayEventMessage (PUSH): { type:'relay.event', subId, event }
        val ev = json.parseToJsonElement(NappletProtocolJson.encodeRelayEvent("s1", sampleEvent())).jsonObject
        assertEquals("relay.event", ev["type"]?.jsonPrimitive?.content)
        assertEquals("s1", ev["subId"]?.jsonPrimitive?.content)
        assertEquals(
            "a".repeat(64),
            ev["event"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content,
        )

        // RelayEoseMessage (PUSH): { type:'relay.eose', subId }
        val eose = json.parseToJsonElement(NappletProtocolJson.encodeRelayEose("s1")).jsonObject
        assertEquals("relay.eose", eose["type"]?.jsonPrimitive?.content)
        assertEquals("s1", eose["subId"]?.jsonPrimitive?.content)
    }

    // ---------- identity (Identity*Message) ----------

    @Test
    fun identityGetPublicKeyKeepsItsOwnRequestAndPubkeyField() {
        assertEquals(NappletRequest.GetPublicKey, NappletProtocolJson.decodeRequest("""{"type":"identity.getPublicKey","id":"1"}"""))
        assertEquals("pk", result("identity.getPublicKey", NappletResponse.PublicKey("pk"))["pubkey"]?.jsonPrimitive?.content)
    }

    @Test
    fun identityReadsDecodeGenericallyAndEncodeMethodSpecificFields() {
        // identity.getProfile/getRelays/getFollows/getMutes/getBlocked/getList → IdentityRead
        assertEquals(NappletRequest.IdentityRead("getProfile"), NappletProtocolJson.decodeRequest("""{"type":"identity.getProfile","id":"1"}"""))
        assertEquals(NappletRequest.IdentityRead("getList", "bookmarks"), NappletProtocolJson.decodeRequest("""{"type":"identity.getList","id":"1","listType":"bookmarks"}"""))

        // Result field per @napplet/nap: getProfile→profile, getRelays→relays,
        // getFollows/getMutes/getBlocked→pubkeys, getList→entries.
        assertTrue(result("identity.getProfile", NappletResponse.Json("""{"name":"x"}""")).containsKey("profile"))
        assertTrue(result("identity.getRelays", NappletResponse.Json("""{"wss://r":{"read":true,"write":true}}""")).containsKey("relays"))
        assertTrue(result("identity.getFollows", NappletResponse.Json("""["aa"]""")).containsKey("pubkeys"))
        assertTrue(result("identity.getMutes", NappletResponse.Json("""["aa"]""")).containsKey("pubkeys"))
        assertTrue(result("identity.getBlocked", NappletResponse.Json("""["aa"]""")).containsKey("pubkeys"))
        assertTrue(result("identity.getList", NappletResponse.Json("""["aa"]""")).containsKey("entries"))
    }

    // ---------- storage (Storage*Message) ----------

    @Test
    fun storageWireTypesAndFieldsMatch() {
        // Wire types are storage.get/set/remove/keys (SDK functions getItem/setItem/removeItem/keys).
        assertEquals(NappletRequest.StorageGet("k"), NappletProtocolJson.decodeRequest("""{"type":"storage.get","id":"1","key":"k"}"""))
        assertEquals(NappletRequest.StorageSet("k", "v"), NappletProtocolJson.decodeRequest("""{"type":"storage.set","id":"1","key":"k","value":"v"}"""))
        assertEquals(NappletRequest.StorageRemove("k"), NappletProtocolJson.decodeRequest("""{"type":"storage.remove","id":"1","key":"k"}"""))
        assertEquals(NappletRequest.StorageKeys, NappletProtocolJson.decodeRequest("""{"type":"storage.keys","id":"1"}"""))

        // StorageGetResultMessage.value, StorageKeysResultMessage.keys
        assertTrue(result("storage.get", NappletResponse.StorageValue("v")).containsKey("value"))
        assertEquals(2, result("storage.keys", NappletResponse.Strings(listOf("a", "b")))["keys"]?.jsonArray?.size)
    }

    // ---------- resource (Resource*Message) ----------

    @Test
    fun resourceBytesRequestAndResultMatch() {
        assertEquals(NappletRequest.ResourceBytes("https://x"), NappletProtocolJson.decodeRequest("""{"type":"resource.bytes","id":"1","url":"https://x"}"""))
        // The host emits base64 bytes + mime; shell.html rebuilds the Blob the SDK expects.
        val o = result("resource.bytes", NappletResponse.Bytes("Hi".encodeToByteArray(), "text/plain"))
        assertEquals("SGk=", o["bytes"]?.jsonPrimitive?.content)
        assertEquals("text/plain", o["mime"]?.jsonPrimitive?.content)
    }

    // ---------- error convention ----------

    @Test
    fun failuresExposeAnErrorFieldTheSdkShimRejectsOn() {
        // The SDK shim rejects when `error` is present, regardless of domain.
        assertTrue(result("relay.publish", NappletResponse.Denied(NappletCapability.RELAY, "no")).containsKey("error"))
        assertTrue(result("identity.getProfile", NappletResponse.Unsupported("identity.getProfile")).containsKey("error"))
        assertTrue(result("relay.query", NappletResponse.Failed("boom")).containsKey("error"))
    }

    // ---------- shell handshake (ShellReadyMessage / ShellInitMessage) ----------

    @Test
    fun shellInitAdvertisesTheCapabilityEnvironment() {
        // shell.ready is answered by the host (not the codec) with this shell.init env, which the
        // SDK caches and answers shell.supports() from locally. ShellInitMessage:
        // { type:'shell.init', capabilities:{ domains, protocols }, services }.
        val o = json.parseToJsonElement(NappletProtocolJson.encodeShellInit(listOf("shell", "relay"), listOf("shell", "relay"))).jsonObject
        assertEquals("shell.init", o["type"]?.jsonPrimitive?.content)
        assertEquals(
            2,
            o["capabilities"]
                ?.jsonObject
                ?.get("domains")
                ?.jsonArray
                ?.size,
        )
        assertTrue(o["capabilities"]?.jsonObject?.containsKey("protocols") == true)
        assertEquals(2, o["services"]?.jsonArray?.size)
        // shell.ready stays a host-layer message — the codec doesn't treat it as a broker request.
        assertNull(NappletProtocolJson.decodeRequest("""{"type":"shell.ready"}"""))
    }

    // ---------- keys (keyboard/command actions) ----------

    @Test
    fun keysRegisterAndUnregisterActionsConform() {
        // keys.registerAction{action:{id,label}} → RegisterAction; result carries actionId.
        assertEquals(
            NappletRequest.RegisterAction("save", "Save"),
            NappletProtocolJson.decodeRequest("""{"type":"keys.registerAction","id":"1","action":{"id":"save","label":"Save"}}"""),
        )
        assertEquals(
            NappletRequest.UnregisterAction("save"),
            NappletProtocolJson.decodeRequest("""{"type":"keys.unregisterAction","actionId":"save"}"""),
        )
        assertEquals("save", result("keys.registerAction", NappletResponse.ActionRegistered("save"))["actionId"]?.jsonPrimitive?.content)
    }

    // ---------- upload (UploadUploadMessage / UploadResult) ----------

    @Test
    fun uploadUploadWireConforms() {
        // upload.upload{request:{data,mimeType,filename}} — shell.html inlines the Blob as dataBase64.
        val up = NappletProtocolJson.decodeRequest("""{"type":"upload.upload","id":"1","request":{"dataBase64":"SGk=","mimeType":"image/png","filename":"a.png"}}""") as NappletRequest.UploadBlob
        assertEquals("image/png", up.contentType)
        assertEquals("a.png", up.filename)

        // UploadResult: { ok, uploadId, status, url?, sha256?, ... }
        val o = result("upload.upload", NappletResponse.Uploaded("https://b/abc", "abc", 2L, "image/png"))
        assertEquals("https://b/abc", o["url"]?.jsonPrimitive?.content)
        assertEquals("abc", o["sha256"]?.jsonPrimitive?.content)
        assertTrue(o.containsKey("uploadId"))
        assertEquals("completed", o["status"]?.jsonPrimitive?.content)
    }

    // ---------- still-open gaps ----------

    @Test
    fun gapIncIsNotModeled() {
        // inter-napplet comms (inc.emit / inc.subscribe / inc.event) are not modeled. Audit ➖.
        assertNull(NappletProtocolJson.decodeRequest("""{"type":"inc.emit","topic":"t"}"""))
    }
}
