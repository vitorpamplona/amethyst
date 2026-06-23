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
package com.vitorpamplona.amethyst.commons.napplet.protocol

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * Marshals the KMP-pure [NappletRequest] / [NappletResponse] types to and from the wire the
 * applet exchanges over `window.napplet.*`. The envelope matches the upstream napplet SDK
 * (`@napplet/shim` / `@napplet/nap`): requests are `{ "type": "<domain>.<action>", "id", ...fields }`
 * and replies are `{ "type": "<domain>.<action>.result", "id", ...fields }`.
 *
 * Lives in `commons/jvmAndroid` (not in any single front end) because the wire contract is identical
 * across hosts: the Android `:napplet` WebView host and a future desktop host both marshal through
 * here. It depends only on `quartz` (Event/Filter), kotlinx.serialization, and `java.util.Base64`
 * (available on Android API 26+ and the JVM) — no platform-UI or process APIs.
 *
 * This is the only place the boundary parses untrusted applet input, so it is deliberately
 * strict: an unrecognized `type` decodes to `null` (the broker denies it) and a malformed/short
 * body throws (the host wraps it into a `Failed`). Uses kotlinx.serialization so it is
 * unit-testable off-device.
 */
object NappletProtocolJson {
    private val json = Json { ignoreUnknownKeys = true }

    /** The `type` discriminant of a request envelope, used to build the matching `.result` type. */
    fun readType(envelopeJson: String): String? = json.parseToJsonElement(envelopeJson).jsonObject.str("type")

    /** The `subId` of a subscription request, used to key the `relay.event`/`relay.eose` pushes back to it. */
    fun readSubId(envelopeJson: String): String? = json.parseToJsonElement(envelopeJson).jsonObject.str("subId")

    /** A `relay.event` push: delivers one matching [event] to the subscription [subId] (no request id). */
    fun encodeRelayEvent(
        subId: String,
        event: Event,
    ): String =
        buildJsonObject {
            put("type", "relay.event")
            put("subId", subId)
            put("event", json.parseToJsonElement(event.toJson()))
        }.toString()

    /** A `relay.eose` push: signals end-of-stored-events for the subscription [subId]. */
    fun encodeRelayEose(subId: String): String =
        buildJsonObject {
            put("type", "relay.eose")
            put("subId", subId)
        }.toString()

    /** A `keys.action` push: the user triggered the registered keyboard/command action [actionId]. */
    fun encodeKeysAction(actionId: String): String =
        buildJsonObject {
            put("type", "keys.action")
            put("actionId", actionId)
        }.toString()

    /**
     * An `identity.changed` push: the active user's public key changed (account switch / connect /
     * disconnect). [pubkey] is the new hex key, or `""` when no account is signed in.
     */
    fun encodeIdentityChanged(pubkey: String): String =
        buildJsonObject {
            put("type", "identity.changed")
            put("pubkey", pubkey)
        }.toString()

    /** A `relay.closed` push: a relay (or the shell) ended the subscription [subId]. */
    fun encodeRelayClosed(
        subId: String,
        reason: String,
    ): String =
        buildJsonObject {
            put("type", "relay.closed")
            put("subId", subId)
            put("reason", reason)
        }.toString()

    /**
     * The `shell.init` handshake reply (`@napplet/core`): the capability environment the napplet
     * caches and answers `shell.supports()` from. [domains] is the set of NAP domains this shell
     * will broker for the applet; [services] mirrors them. We don't advertise numbered protocols.
     */
    fun encodeShellInit(
        domains: List<String>,
        services: List<String>,
    ): String =
        buildJsonObject {
            put("type", "shell.init")
            putJsonObject("capabilities") {
                put("domains", buildJsonArray { domains.forEach { add(it) } })
                putJsonObject("protocols") {}
            }
            put("services", buildJsonArray { services.forEach { add(it) } })
        }.toString()

    /** Parses a request envelope. Returns `null` for an unrecognized `type` so the broker can deny it. */
    fun decodeRequest(envelopeJson: String): NappletRequest? {
        val o = json.parseToJsonElement(envelopeJson).jsonObject
        return when (o.str("type")) {
            "shell.supports" -> NappletRequest.ShellSupports(o.req("domain"), o.str("protocol"))
            "theme.get" -> NappletRequest.ThemeGet
            "identity.getPublicKey" -> NappletRequest.GetPublicKey
            "relay.publish" -> {
                val t = o.eventTemplate()
                NappletRequest.Publish(kind = t.kindOf(), tags = decodeTags(t), content = t.str("content") ?: "")
            }
            "relay.publishEncrypted" -> {
                val t = o.eventTemplate()
                NappletRequest.PublishEncrypted(
                    kind = t.kindOf(),
                    tags = decodeTags(t),
                    content = t.str("content") ?: "",
                    recipient = o.req("recipient"),
                    encryption = o.str("encryption") ?: "nip44",
                )
            }
            "relay.query" -> NappletRequest.QueryEvents(decodeFilterList(o))
            "relay.subscribe" -> NappletRequest.Subscribe(decodeFilterList(o))
            "nostr.signEvent" -> {
                // NIP-07 signEvent: sign-only, honoring the app-supplied created_at (fallback: now).
                val t = o.eventTemplate()
                NappletRequest.SignEvent(
                    kind = t.kindOf(),
                    tags = decodeTags(t),
                    content = t.str("content") ?: "",
                    createdAt = t["created_at"]?.jsonPrimitive?.long ?: (System.currentTimeMillis() / 1000),
                )
            }
            "storage.get" -> NappletRequest.StorageGet(o.req("key"))
            "storage.set" -> NappletRequest.StorageSet(o.req("key"), o.req("value"))
            "storage.remove" -> NappletRequest.StorageRemove(o.req("key"))
            "storage.keys" -> NappletRequest.StorageKeys
            "notify.create" -> NappletRequest.NotifyCreate(o.str("title") ?: "", o.str("body") ?: "")
            "notify.list" -> NappletRequest.NotifyList
            "notify.dismiss" -> NappletRequest.NotifyDismiss(o.str("notificationId") ?: o.str("id") ?: "")
            "keys.registerAction" -> {
                val action = o.getValue("action").jsonObject
                NappletRequest.RegisterAction(action.req("id"), action.str("label") ?: "", action.str("defaultKey"))
            }
            "keys.unregisterAction" -> NappletRequest.UnregisterAction(o.req("actionId"))
            "value.payInvoice" -> NappletRequest.PayInvoice(o.req("invoice"))
            "resource.bytes" -> NappletRequest.ResourceBytes(o.req("url"))
            "upload.upload" -> {
                // UploadUploadMessage: { type, id, request: { data, mimeType?, filename?, ... } }.
                // The Blob in `request.data` is inlined as base64 `request.dataBase64` by shell.html.
                val request = o.getValue("request").jsonObject
                NappletRequest.UploadBlob(
                    bytes = Base64.getDecoder().decode(request.req("dataBase64")),
                    contentType = request.str("mimeType") ?: "application/octet-stream",
                    filename = request.str("filename"),
                )
            }
            else -> {
                // Any other identity.* read (getProfile/getRelays/getFollows/getList/...) routes through
                // a generic IdentityRead; the broker/gateway decides which are implemented.
                val type = o.str("type")
                if (type != null && type.startsWith("identity.")) {
                    NappletRequest.IdentityRead(type.removePrefix("identity."), o.str("listType") ?: o.str("argument"))
                } else {
                    null
                }
            }
        }
    }

    /** Builds the `{type:"<requestType>.result", ok, ...}` reply (the host adds the `id`). */
    fun encodeResponse(
        requestType: String,
        response: NappletResponse,
    ): String =
        buildJsonObject {
            // Most replies are `<requestType>.result`; a few NAP domains use a bespoke past-tense
            // reply type the client listens for instead (e.g. notify.create → notify.created).
            val responseType =
                when (response) {
                    is NappletResponse.NotifyCreated -> "notify.created"
                    is NappletResponse.NotifyListed -> "notify.listed"
                    else -> "$requestType.result"
                }
            put("type", responseType)
            when (response) {
                is NappletResponse.PublicKey -> {
                    put("ok", true)
                    put("pubkey", response.pubkey)
                }
                is NappletResponse.Published -> {
                    // Upstream resolves publish() to the signed NostrEvent; relays are an extra.
                    put("ok", true)
                    put("event", json.parseToJsonElement(response.event.toJson()))
                    put("eventId", response.event.id)
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
                is NappletResponse.ActionRegistered -> {
                    put("ok", true)
                    put("actionId", response.actionId)
                    response.binding?.let { put("binding", it) }
                }
                is NappletResponse.StorageValue -> {
                    put("ok", true)
                    put("value", response.value)
                }
                is NappletResponse.Strings -> {
                    put("ok", true)
                    // storage.keys returns `keys`; other string-list reads use `values`.
                    val field = if (requestType == "storage.keys") "keys" else "values"
                    put(field, buildJsonArray { response.values.forEach { add(it) } })
                }
                is NappletResponse.Json -> {
                    put("ok", true)
                    // identity.* reads return method-specific fields (profile/relays/pubkeys/entries/...).
                    // The host already serialized the value; embed it (fall back to null if malformed).
                    put(identityResultField(requestType), runCatching { json.parseToJsonElement(response.raw) }.getOrDefault(JsonNull))
                }
                is NappletResponse.Bytes -> {
                    put("ok", true)
                    put("bytes", Base64.getEncoder().encodeToString(response.bytes))
                    put("mime", response.contentType)
                }
                is NappletResponse.Uploaded -> {
                    // UploadResult: { ok, uploadId, status, url?, sha256?, size?, mimeType?, ... }.
                    put("ok", true)
                    put("uploadId", response.sha256 ?: response.url)
                    put("status", "completed")
                    put("url", response.url)
                    response.sha256?.let { put("sha256", it) }
                    response.size?.let { put("size", it) }
                    response.mimeType?.let { put("mimeType", it) }
                }
                is NappletResponse.Paid -> {
                    put("ok", true)
                    put("preimage", response.preimage)
                }
                is NappletResponse.Theme -> {
                    put("ok", true)
                    putJsonObject("theme") {
                        putJsonObject("colors") {
                            put("background", response.background)
                            put("text", response.text)
                            put("primary", response.primary)
                        }
                    }
                }
                is NappletResponse.NotifyCreated -> {
                    put("ok", true)
                    put("id", response.id)
                }
                is NappletResponse.NotifyListed -> {
                    put("ok", true)
                    put(
                        "notifications",
                        buildJsonArray {
                            response.notifications.forEach {
                                add(
                                    buildJsonObject {
                                        put("id", it.id)
                                        put("title", it.title)
                                        put("body", it.body)
                                    },
                                )
                            }
                        },
                    )
                }
                is NappletResponse.Done -> {
                    put("ok", true)
                }
                // Subscribe is acknowledged here, but the host streams pushes instead of sending this.
                is NappletResponse.Subscribed -> {
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

    /** Parses every Nostr filter from a `filters` array (each entry) or a single `filter` object. */
    fun decodeFilterList(envelopeJson: String): List<Filter> = decodeFilterList(json.parseToJsonElement(envelopeJson).jsonObject)

    private fun decodeFilterList(o: JsonObject): List<Filter> {
        o["filters"]?.jsonArray?.let { arr -> return arr.map { decodeFilterObject(it.jsonObject) } }
        o["filter"]?.jsonObject?.let { return listOf(decodeFilterObject(it)) }
        return emptyList()
    }

    private fun decodeFilterObject(f: JsonObject): Filter {
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

    /** The unsigned event template, carried in the `event` field (per `@napplet/shim`), else the envelope itself. */
    private fun JsonObject.eventTemplate(): JsonObject = this["event"]?.jsonObject ?: this

    private fun JsonObject.kindOf(): Int = getValue("kind").jsonPrimitive.int

    /** The result field a given identity read returns, matching `@napplet/nap` identity message types. */
    private fun identityResultField(requestType: String): String =
        when (requestType) {
            "identity.getRelays" -> "relays"
            "identity.getFollows", "identity.getMutes", "identity.getBlocked" -> "pubkeys"
            "identity.getProfile" -> "profile"
            "identity.getList" -> "entries"
            "identity.getZaps" -> "zaps"
            "identity.getBadges" -> "badges"
            else -> "result"
        }
}
