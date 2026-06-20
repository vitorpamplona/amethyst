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

/**
 * Marshals the KMP-pure [NappletRequest] / [NappletResponse] types to and from the JSON the
 * applet exchanges over `window.napplet.*`. This is the only place the boundary parses applet
 * input, so it is deliberately strict: an unrecognized `op` decodes to `null` (the broker
 * rejects it) and a malformed/short body throws (the broker wraps it into a `Failed`).
 *
 * The host process only ever *shuttles* these strings; decoding/encoding happens in the
 * main-process broker so a compromised host cannot fabricate a typed request that skips a field.
 * Uses kotlinx.serialization (a pure-JVM JSON impl) so it is unit-testable off-device.
 */
object NappletProtocolJson {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses an applet request. Returns `null` for an unrecognized `op` so the broker can deny it. */
    fun decodeRequest(jsonText: String): NappletRequest? {
        val o = json.parseToJsonElement(jsonText).jsonObject
        return when (o.str("op")) {
            "getPublicKey" -> NappletRequest.GetPublicKey
            "signEvent" ->
                NappletRequest.SignEvent(
                    kind = o.getValue("kind").jsonPrimitive.int,
                    tags = decodeTags(o),
                    content = o.str("content") ?: "",
                )
            "nip04Encrypt" -> NappletRequest.Nip04Encrypt(o.req("peer"), o.req("plaintext"))
            "nip04Decrypt" -> NappletRequest.Nip04Decrypt(o.req("peer"), o.req("ciphertext"))
            "nip44Encrypt" -> NappletRequest.Nip44Encrypt(o.req("peer"), o.req("plaintext"))
            "nip44Decrypt" -> NappletRequest.Nip44Decrypt(o.req("peer"), o.req("ciphertext"))
            "publish" -> NappletRequest.Publish(Event.fromJson(o.getValue("event").jsonObject.toString()))
            "queryEvents" -> NappletRequest.QueryEvents(decodeFilter(o.getValue("filter").jsonObject))
            "storageGet" -> NappletRequest.StorageGet(o.req("key"))
            "storageSet" -> NappletRequest.StorageSet(o.req("key"), o.req("value"))
            "storageRemove" -> NappletRequest.StorageRemove(o.req("key"))
            "payInvoice" -> NappletRequest.PayInvoice(o.req("invoice"))
            else -> null
        }
    }

    fun encodeResponse(response: NappletResponse): String =
        buildJsonObject {
            when (response) {
                is NappletResponse.PublicKey -> {
                    put("type", "publicKey")
                    put("pubkey", response.pubkey)
                }
                is NappletResponse.SignedEvent -> {
                    put("type", "signedEvent")
                    put("event", json.parseToJsonElement(response.event.toJson()))
                }
                is NappletResponse.Text -> {
                    put("type", "text")
                    put("value", response.value)
                }
                is NappletResponse.Published -> {
                    put("type", "published")
                    put("relays", buildJsonArray { response.relays.forEach { add(it) } })
                }
                is NappletResponse.Events -> {
                    put("type", "events")
                    put("events", buildJsonArray { response.events.forEach { add(json.parseToJsonElement(it.toJson())) } })
                }
                is NappletResponse.StorageValue -> {
                    put("type", "storageValue")
                    put("value", response.value)
                }
                is NappletResponse.Paid -> {
                    put("type", "paid")
                    put("preimage", response.preimage)
                }
                is NappletResponse.Done -> {
                    put("type", "done")
                }
                is NappletResponse.Denied -> {
                    put("type", "denied")
                    put("capability", response.capability.name)
                    put("reason", response.reason)
                }
                is NappletResponse.Unsupported -> {
                    put("type", "unsupported")
                    put("operation", response.operation)
                }
                is NappletResponse.Failed -> {
                    put("type", "failed")
                    put("reason", response.reason)
                }
            }
        }.toString()

    /** Parses a standard Nostr filter object (kinds/authors/ids/since/until/limit/search + `#x` tags). */
    private fun decodeFilter(o: JsonObject): Filter {
        val tags = mutableMapOf<String, List<String>>()
        for ((key, value) in o) {
            if (key.startsWith("#") && key.length == 2) {
                tags[key.substring(1)] = value.jsonArray.map { it.jsonPrimitive.content }
            }
        }

        return Filter(
            ids = o.strList("ids"),
            authors = o.strList("authors"),
            kinds = o["kinds"]?.jsonArray?.map { it.jsonPrimitive.int },
            tags = tags.ifEmpty { null },
            since = o["since"]?.jsonPrimitive?.long,
            until = o["until"]?.jsonPrimitive?.long,
            limit = o["limit"]?.jsonPrimitive?.int,
            search = o.str("search"),
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
