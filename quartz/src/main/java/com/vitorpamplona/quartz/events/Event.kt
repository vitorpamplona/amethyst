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

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import java.math.BigDecimal

@Immutable
open class Event(
    val id: HexKey,
    @JsonProperty("pubkey") val pubKey: HexKey,
    @JsonProperty("created_at") val createdAt: Long,
    val kind: Int,
    val tags: Array<Array<String>>,
    val content: String,
    val sig: HexKey,
) : EventInterface {
    override fun isContentEncoded() = false

    override fun countMemory(): Long {
        return 12L +
            id.bytesUsedInMemory() +
            pubKey.bytesUsedInMemory() +
            tags.sumOf { it.sumOf { it.bytesUsedInMemory() } } +
            content.bytesUsedInMemory() +
            sig.bytesUsedInMemory()
    }

    override fun id(): HexKey = id

    override fun pubKey(): HexKey = pubKey

    override fun createdAt(): Long = createdAt

    override fun kind(): Int = kind

    override fun tags(): Array<Array<String>> = tags

    override fun content(): String = content

    override fun sig(): HexKey = sig

    override fun toJson(): String = mapper.writeValueAsString(toJsonObject())

    override fun hasAnyTaggedUser() = hasTagWithContent("p")

    override fun hasTagWithContent(tagName: String) = tags.any { it.size > 1 && it[0] == tagName }

    override fun taggedUsers() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }

    override fun taggedEvents() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }

    override fun taggedUrls() = tags.filter { it.size > 1 && it[0] == "r" }.map { it[1] }

    override fun firstTaggedUser() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.let { it[1] }

    override fun firstTaggedEvent() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.let { it[1] }

    override fun firstTaggedUrl() = tags.firstOrNull { it.size > 1 && it[0] == "r" }?.let { it[1] }

    override fun firstTaggedK() = tags.firstOrNull { it.size > 1 && it[0] == "k" }?.let { it[1].toIntOrNull() }

    override fun firstTaggedAddress() =
        tags
            .firstOrNull { it.size > 1 && it[0] == "a" }
            ?.let {
                val aTagValue = it[1]
                val relay = it.getOrNull(2)

                ATag.parse(aTagValue, relay)
            }

    override fun taggedEmojis() = tags.filter { it.size > 2 && it[0] == "emoji" }.map { EmojiUrl(it[1], it[2]) }

    override fun isSensitive() =
        tags.any {
            (it.size > 0 && it[0].equals("content-warning", true)) ||
                (it.size > 1 && it[0] == "t" && it[1].equals("nsfw", true)) ||
                (it.size > 1 && it[0] == "t" && it[1].equals("nude", true))
        }

    override fun subject() = tags.firstOrNull { it.size > 1 && it[0] == "subject" }?.get(1)

    override fun zapraiserAmount() = tags.firstOrNull { (it.size > 1 && it[0] == "zapraiser") }?.get(1)?.toLongOrNull()

    override fun hasZapSplitSetup() = tags.any { it.size > 1 && it[0] == "zap" }

    override fun zapSplitSetup(): List<ZapSplitSetup> {
        return tags
            .filter { it.size > 1 && it[0] == "zap" }
            .mapNotNull {
                val isLnAddress = it[0].contains("@") || it[0].startsWith("LNURL", true)
                val weight = if (isLnAddress) 1.0 else (it.getOrNull(3)?.toDoubleOrNull() ?: 0.0)

                if (weight > 0) {
                    ZapSplitSetup(
                        it[1],
                        it.getOrNull(2),
                        weight,
                        isLnAddress,
                    )
                } else {
                    null
                }
            }
    }

    override fun taggedAddresses() =
        tags
            .filter { it.size > 1 && it[0] == "a" }
            .mapNotNull {
                val aTagValue = it[1]
                val relay = it.getOrNull(2)

                ATag.parse(aTagValue, relay)
            }

    override fun hasHashtags() = tags.any { it.size > 1 && it[0] == "t" }

    override fun hasGeohashes() = tags.any { it.size > 1 && it[0] == "g" }

    override fun hashtags() = tags.filter { it.size > 1 && it[0] == "t" }.map { it[1] }

    override fun geohashes() = tags.filter { it.size > 1 && it[0] == "g" }.map { it[1] }

    override fun matchTag1With(text: String) = tags.any { it.size > 1 && it[1].contains(text, true) }

    override fun isTagged(
        key: String,
        tag: String,
    ) = tags.any { it.size > 1 && it[0] == key && it[1] == tag }

    override fun isAnyTagged(
        key: String,
        tags: Set<String>,
    ) = this.tags.any { it.size > 1 && it[0] == key && it[1] in tags }

    override fun isTaggedWord(word: String) = isTagged("word", word)

    override fun isTaggedUser(idHex: String) = isTagged("p", idHex)

    override fun isTaggedUsers(idHexes: Set<String>) = isAnyTagged("p", idHexes)

    override fun isTaggedEvent(idHex: String) = isTagged("e", idHex)

    override fun isTaggedAddressableNote(idHex: String) = isTagged("a", idHex)

    override fun isTaggedAddressableNotes(idHexes: Set<String>) = isAnyTagged("a", idHexes)

    override fun isTaggedHash(hashtag: String) = tags.any { it.size > 1 && it[0] == "t" && it[1].equals(hashtag, true) }

    override fun isTaggedGeoHash(hashtag: String) = tags.any { it.size > 1 && it[0] == "g" && it[1].startsWith(hashtag, true) }

    override fun isTaggedHashes(hashtags: Set<String>) = tags.any { it.size > 1 && it[0] == "t" && it[1].lowercase() in hashtags }

    override fun isTaggedGeoHashes(hashtags: Set<String>) = tags.any { it.size > 1 && it[0] == "g" && it[1].lowercase() in hashtags }

    override fun firstIsTaggedHashes(hashtags: Set<String>) = tags.firstOrNull { it.size > 1 && it[0] == "t" && it[1].lowercase() in hashtags }?.getOrNull(1)

    override fun firstIsTaggedAddressableNote(addressableNotes: Set<String>) = tags.firstOrNull { it.size > 1 && it[0] == "a" && it[1] in addressableNotes }?.getOrNull(1)

    override fun isTaggedAddressableKind(kind: Int): Boolean {
        val kindStr = kind.toString()
        return tags.any { it.size > 1 && it[0] == "a" && it[1].startsWith(kindStr) }
    }

    override fun expiration() =
        try {
            tags.firstOrNull { it.size > 1 && it[0] == "expiration" }?.get(1)?.toLongOrNull()
        } catch (_: Exception) {
            null
        }

    override fun isExpired() = (expiration() ?: Long.MAX_VALUE) < TimeUtils.now()

    override fun isExpirationBefore(time: Long) = (expiration() ?: Long.MAX_VALUE) < time

    override fun getTagOfAddressableKind(kind: Int): ATag? {
        val kindStr = kind.toString()
        val aTag =
            tags.firstOrNull { it.size > 1 && it[0] == "a" && it[1].startsWith(kindStr) }?.getOrNull(1)
                ?: return null

        return ATag.parse(aTag, null)
    }

    override fun getPoWRank(): Int {
        var rank = 0
        for (i in 0..id.length) {
            if (id[i] == '0') {
                rank += 4
            } else if (id[i] in '4'..'7') {
                rank += 1
                break
            } else if (id[i] in '2'..'3') {
                rank += 2
                break
            } else if (id[i] == '1') {
                rank += 3
                break
            } else {
                break
            }
        }
        return rank
    }

    override fun getGeoHash(): String? {
        return tags.firstOrNull { it.size > 1 && it[0] == "g" }?.get(1)?.ifBlank { null }
    }

    override fun getReward(): BigDecimal? {
        return try {
            tags.firstOrNull { it.size > 1 && it[0] == "reward" }?.get(1)?.let { BigDecimal(it) }
        } catch (e: Exception) {
            null
        }
    }

    open fun toNIP19(): String {
        return if (this is AddressableEvent) {
            ATag(kind, pubKey, dTag(), null).toNAddr()
        } else {
            Nip19Bech32.createNEvent(id, pubKey, kind, null)
        }
    }

    fun toNostrUri(): String {
        return "nostr:${toNIP19()}"
    }

    fun hasCorrectIDHash(): Boolean {
        if (id.isEmpty()) return false
        return id.equals(generateId())
    }

    fun hasVerifiedSignature(): Boolean {
        if (id.isEmpty() || sig.isEmpty()) return false
        return CryptoUtils.verifySignature(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))
    }

    /** Checks if the ID is correct and then if the pubKey's secret key signed the event. */
    override fun checkSignature() {
        if (!hasCorrectIDHash()) {
            throw Exception(
                """
                |Unexpected ID.
                |  Event: ${toJson()}
                |  Actual ID: $id
                |  Generated: ${generateId()}
                """
                    .trimIndent(),
            )
        }
        if (!hasVerifiedSignature()) {
            throw Exception("""Bad signature!""")
        }
    }

    override fun hasValidSignature(): Boolean {
        return try {
            hasCorrectIDHash() && hasVerifiedSignature()
        } catch (e: Exception) {
            Log.w("Event", "Event $id does not have a valid signature: ${toJson()}", e)
            false
        }
    }

    fun makeJsonForId(): String {
        return makeJsonForId(pubKey, createdAt, kind, tags, content)
    }

    private fun generateId(): String {
        return CryptoUtils.sha256(makeJsonForId().toByteArray()).toHexKey()
    }

    private class EventDeserializer : StdDeserializer<Event>(Event::class.java) {
        override fun deserialize(
            jp: JsonParser,
            ctxt: DeserializationContext,
        ): Event {
            return fromJson(jp.codec.readTree(jp))
        }
    }

    private class GossipDeserializer : StdDeserializer<Gossip>(Gossip::class.java) {
        override fun deserialize(
            jp: JsonParser,
            ctxt: DeserializationContext,
        ): Gossip {
            val jsonObject: JsonNode = jp.codec.readTree(jp)
            return Gossip(
                id = jsonObject.get("id")?.asText()?.intern(),
                pubKey = jsonObject.get("pubkey")?.asText()?.intern(),
                createdAt = jsonObject.get("created_at")?.asLong(),
                kind = jsonObject.get("kind")?.asInt(),
                tags =
                    jsonObject.get("tags").toTypedArray {
                        it.toTypedArray { s -> if (s.isNull) "" else s.asText().intern() }
                    },
                content = jsonObject.get("content")?.asText(),
            )
        }
    }

    private class EventSerializer : StdSerializer<Event>(Event::class.java) {
        override fun serialize(
            event: Event,
            gen: JsonGenerator,
            provider: SerializerProvider,
        ) {
            gen.writeStartObject()
            gen.writeStringField("id", event.id)
            gen.writeStringField("pubkey", event.pubKey)
            gen.writeNumberField("created_at", event.createdAt)
            gen.writeNumberField("kind", event.kind)
            gen.writeArrayFieldStart("tags")
            event.tags.forEach { tag -> gen.writeArray(tag, 0, tag.size) }
            gen.writeEndArray()
            gen.writeStringField("content", event.content)
            gen.writeStringField("sig", event.sig)
            gen.writeEndObject()
        }
    }

    private class GossipSerializer : StdSerializer<Gossip>(Gossip::class.java) {
        override fun serialize(
            event: Gossip,
            gen: JsonGenerator,
            provider: SerializerProvider,
        ) {
            gen.writeStartObject()
            event.id?.let { gen.writeStringField("id", it) }
            event.pubKey?.let { gen.writeStringField("pubkey", it) }
            event.createdAt?.let { gen.writeNumberField("created_at", it) }
            event.kind?.let { gen.writeNumberField("kind", it) }
            event.tags?.let {
                gen.writeArrayFieldStart("tags")
                event.tags.forEach { tag -> gen.writeArray(tag, 0, tag.size) }
                gen.writeEndArray()
            }
            event.content?.let { gen.writeStringField("content", it) }
            gen.writeEndObject()
        }
    }

    fun toJsonObject(): JsonNode {
        val factory = mapper.nodeFactory

        return factory.objectNode().apply {
            put("id", id)
            put("pubkey", pubKey)
            put("created_at", createdAt)
            put("kind", kind)
            replace(
                "tags",
                factory.arrayNode(tags.size).apply {
                    tags.forEach { tag ->
                        add(
                            factory.arrayNode(tag.size).apply { tag.forEach { add(it) } },
                        )
                    }
                },
            )
            put("content", content)
            put("sig", sig)
        }
    }

    companion object {
        val mapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .registerModule(
                    SimpleModule()
                        .addSerializer(Event::class.java, EventSerializer())
                        .addDeserializer(Event::class.java, EventDeserializer())
                        .addSerializer(Gossip::class.java, GossipSerializer())
                        .addDeserializer(Gossip::class.java, GossipDeserializer())
                        .addDeserializer(Response::class.java, ResponseDeserializer())
                        .addDeserializer(Request::class.java, RequestDeserializer()),
                )

        fun fromJson(jsonObject: JsonNode): Event {
            return EventFactory.create(
                id = jsonObject.get("id").asText().intern(),
                pubKey = jsonObject.get("pubkey").asText().intern(),
                createdAt = jsonObject.get("created_at").asLong(),
                kind = jsonObject.get("kind").asInt(),
                tags =
                    jsonObject.get("tags").toTypedArray {
                        it.toTypedArray { s -> if (s.isNull) "" else s.asText().intern() }
                    },
                content = jsonObject.get("content").asText(),
                sig = jsonObject.get("sig").asText(),
            )
        }

        private inline fun <reified R> JsonNode.toTypedArray(transform: (JsonNode) -> R): Array<R> {
            return Array(size()) { transform(get(it)) }
        }

        fun fromJson(json: String): Event = mapper.readValue(json, Event::class.java)

        fun toJson(event: Event): String = mapper.writeValueAsString(event)

        fun makeJsonForId(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): String {
            val factory = mapper.nodeFactory
            val rawEvent =
                factory.arrayNode(6).apply {
                    add(0)
                    add(pubKey)
                    add(createdAt)
                    add(kind)
                    add(
                        factory.arrayNode(tags.size).apply {
                            tags.forEach { tag ->
                                add(
                                    factory.arrayNode(tag.size).apply { tag.forEach { add(it) } },
                                )
                            }
                        },
                    )
                    add(content)
                }

            return mapper.writeValueAsString(rawEvent)
        }

        fun generateId(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): ByteArray {
            return CryptoUtils.sha256(makeJsonForId(pubKey, createdAt, kind, tags, content).toByteArray())
        }

        fun create(
            signer: NostrSigner,
            kind: Int,
            tags: Array<Array<String>> = emptyArray(),
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            onReady: (Event) -> Unit,
        ) {
            return signer.sign(createdAt, kind, tags, content, onReady)
        }
    }
}

@Immutable
open class WrappedEvent(
    id: HexKey,
    @JsonProperty("pubkey") pubKey: HexKey,
    @JsonProperty("created_at") createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient var host: Event? = null // host event to broadcast when needed
}

@Immutable
interface AddressableEvent {
    fun dTag(): String

    fun address(): ATag

    fun addressTag(): String
}

@Immutable
open class BaseAddressableEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {
    override fun dTag() = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""

    override fun address() = ATag(kind, pubKey, dTag(), null)

    /**
     * Creates the tag in a memory effecient way (without creating the ATag class
     */
    override fun addressTag() = ATag.assembleATag(kind, pubKey, dTag())
}

fun String.bytesUsedInMemory(): Int {
    return (8 * ((((this.length) * 2) + 45) / 8))
}

data class ZapSplitSetup(
    val lnAddressOrPubKeyHex: String,
    val relay: String?,
    val weight: Double,
    val isLnAddress: Boolean,
)
