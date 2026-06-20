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

import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * Marshals the KMP-pure [NappletRequest] / [NappletResponse] types to and from the wire the
 * applet exchanges over `window.napplet.*`. The envelope matches the upstream napplet SDK
 * (`@napplet/web`): requests are `{ "type": "<domain>.<action>", "id", ...fields }` and replies
 * are `{ "type": "<domain>.<action>.result", "id", "ok", ...fields }`.
 *
 * This is the only place the boundary parses untrusted applet input, so it is deliberately
 * strict: an unrecognized `type` decodes to `null` (the broker denies it) and a malformed/short
 * body throws (the broker wraps it into a `Failed`). Uses kotlinx.serialization so it is
 * unit-testable off-device.
 */
object NappletProtocolJson {
    private val json = Json { ignoreUnknownKeys = true }

    /** The `type` discriminant of a request envelope, used to build the matching `.result` type. */
    fun readType(envelopeJson: String): String? = json.parseToJsonElement(envelopeJson).jsonObject.str("type")

    /** Parses a request envelope. Returns `null` for an unrecognized `type` so the broker can deny it. */
    fun decodeRequest(envelopeJson: String): NappletRequest? {
        val o = json.parseToJsonElement(envelopeJson).jsonObject
        return when (o.str("type")) {
            "shell.supports" -> NappletRequest.ShellSupports(o.req("domain"))
            "identity.getPublicKey" -> NappletRequest.GetPublicKey
            "keys.signEvent" ->
                NappletRequest.SignEvent(
                    kind = o.getValue("kind").jsonPrimitive.int,
                    tags = decodeTags(o),
                    content = o.str("content") ?: "",
                )
            "keys.nip04Encrypt" -> NappletRequest.Nip04Encrypt(o.req("peer"), o.req("plaintext"))
            "keys.nip04Decrypt" -> NappletRequest.Nip04Decrypt(o.req("peer"), o.req("ciphertext"))
            "keys.nip44Encrypt" -> NappletRequest.Nip44Encrypt(o.req("peer"), o.req("plaintext"))
            "keys.nip44Decrypt" -> NappletRequest.Nip44Decrypt(o.req("peer"), o.req("ciphertext"))
            "relay.publish" -> NappletRequest.Publish(Event.fromJson(o.getValue("event").jsonObject.toString()))
            "relay.query" -> NappletRequest.QueryEvents(decodeFilter(o))
            "storage.get" -> NappletRequest.StorageGet(o.req("key"))
            "storage.set" -> NappletRequest.StorageSet(o.req("key"), o.req("value"))
            "storage.remove" -> NappletRequest.StorageRemove(o.req("key"))
            "value.payInvoice" -> NappletRequest.PayInvoice(o.req("invoice"))
            "resource.bytes" -> NappletRequest.ResourceBytes(o.req("url"))
            "upload" -> NappletRequest.UploadBlob(Base64.getDecoder().decode(o.req("bytes")), o.req("contentType"))
            else -> null
        }
    }

    /** Builds the `{type:"<requestType>.result", ok, ...}` reply (the host adds the `id`). */
    fun encodeResponse(
        requestType: String,
        response: NappletResponse,
    ): String =
        buildJsonObject {
            put("type", "$requestType.result")
            when (response) {
                is NappletResponse.PublicKey -> {
                    put("ok", true)
                    put("pubkey", response.pubkey)
                }
                is NappletResponse.SignedEvent -> {
                    put("ok", true)
                    put("event", json.parseToJsonElement(response.event.toJson()))
                }
                is NappletResponse.Text -> {
                    put("ok", true)
                    put("value", response.value)
                }
                is NappletResponse.Published -> {
                    put("ok", true)
                    put("relays", buildJsonArray { response.relays.forEach { add(it) } })
                }
                is NappletResponse.Events -> {
                    put("ok", true)
                    put("events", buildJsonArray { response.events.forEach { add(json.parseToJsonElement(it.toJson())) } })
                }
                is NappletResponse.Supported -> {
                    put("ok", true)
                    put("supported", response.supported)
                }
                is NappletResponse.StorageValue -> {
                    put("ok", true)
                    put("value", response.value)
                }
                is NappletResponse.Bytes -> {
                    put("ok", true)
                    put("bytes", Base64.getEncoder().encodeToString(response.bytes))
                    put("contentType", response.contentType)
                }
                is NappletResponse.Uploaded -> {
                    put("ok", true)
                    put("url", response.url)
                }
                is NappletResponse.Paid -> {
                    put("ok", true)
                    put("preimage", response.preimage)
                }
                is NappletResponse.Done -> {
                    put("ok", true)
                }
                is NappletResponse.Denied -> {
                    put("ok", false)
                    put("error", "denied")
                    put("capability", response.capability.name)
                    put("reason", response.reason)
                }
                is NappletResponse.Unsupported -> {
                    put("ok", false)
                    put("error", "unsupported")
                    put("operation", response.operation)
                }
                is NappletResponse.Failed -> {
                    put("ok", false)
                    put("error", "failed")
                    put("reason", response.reason)
                }
            }
        }.toString()

    /** Parses a Nostr filter from `filter` (object) or the first of `filters` (array). */
    private fun decodeFilter(o: JsonObject): Filter {
        val f = o["filter"]?.jsonObject ?: o["filters"]?.jsonArray?.firstOrNull()?.jsonObject ?: JsonObject(emptyMap())

        val tags = mutableMapOf<String, List<String>>()
        for ((key, value) in f) {
            if (key.startsWith("#") && key.length == 2) {
                tags[key.substring(1)] = value.jsonArray.map { it.jsonPrimitive.content }
            }
        }

        return Filter(
            ids = f.strList("ids"),
            authors = f.strList("authors"),
            kinds = f["kinds"]?.jsonArray?.map { it.jsonPrimitive.int },
            tags = tags.ifEmpty { null },
            since = f["since"]?.jsonPrimitive?.long,
            until = f["until"]?.jsonPrimitive?.long,
            limit = f["limit"]?.jsonPrimitive?.int,
            search = f.str("search"),
        )
    }

    private fun decodeTags(o: JsonObject): Array<Array<String>> {
        val tags = o["tags"]?.jsonArray ?: return emptyArray()
        return tags
            .map { inner -> inner.jsonArray.map { it.jsonPrimitive.content }.toTypedArray() }
            .toTypedArray()
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun JsonObject.req(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.strList(key: String): List<String>? = this[key]?.jsonArray?.map { it.jsonPrimitive.content }
}
