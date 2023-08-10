package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.service.relays.bytesUsedInMemory
import fr.acinq.secp256k1.Hex
import java.lang.reflect.Type
import java.math.BigDecimal
import java.util.*

@Immutable
open class Event(
    val id: HexKey,
    @SerializedName("pubkey") val pubKey: HexKey,
    @SerializedName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: HexKey
) : EventInterface {

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

    override fun tags(): List<List<String>> = tags

    override fun content(): String = content

    override fun sig(): HexKey = sig

    override fun toJson(): String = gson.toJson(this)

    fun hasAnyTaggedUser() = tags.any { it.size > 1 && it[0] == "p" }

    override fun taggedUsers() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }
    override fun taggedEvents() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }

    override fun taggedUrls() = tags.filter { it.size > 1 && it[0] == "r" }.map { it[1] }

    override fun taggedEmojis() = tags.filter { it.size > 2 && it[0] == "emoji" }.map { EmojiUrl(it[1], it[2]) }

    override fun isSensitive() = tags.any {
        (it.size > 0 && it[0].equals("content-warning", true)) ||
            (it.size > 1 && it[0] == "t" && it[1].equals("nsfw", true)) ||
            (it.size > 1 && it[0] == "t" && it[1].equals("nude", true))
    }

    override fun subject() = tags.firstOrNull() { it.size > 1 && it[0] == "subject" }?.get(1)

    override fun zapraiserAmount() = tags.firstOrNull() {
        (it.size > 1 && it[0] == "zapraiser")
    }?.get(1)?.toLongOrNull()

    override fun zapAddress() = tags.firstOrNull { it.size > 1 && it[0] == "zap" }?.get(1)

    override fun taggedAddresses() = tags.filter { it.size > 1 && it[0] == "a" }.mapNotNull {
        val aTagValue = it[1]
        val relay = it.getOrNull(2)

        ATag.parse(aTagValue, relay)
    }

    override fun hashtags() = tags.filter { it.size > 1 && it[0] == "t" }.map { it[1] }
    override fun geohashes() = tags.filter { it.size > 1 && it[0] == "g" }.map { it[1] }

    override fun matchTag1With(text: String) = tags.any { it.size > 1 && it[1].contains(text, true) }

    override fun isTaggedUser(idHex: String) = tags.any { it.size > 1 && it[0] == "p" && it[1] == idHex }

    override fun isTaggedEvent(idHex: String) = tags.any { it.size > 1 && it[0] == "e" && it[1] == idHex }

    override fun isTaggedAddressableNote(idHex: String) = tags.any { it.size > 1 && it[0] == "a" && it[1] == idHex }

    override fun isTaggedAddressableNotes(idHexes: Set<String>) = tags.any { it.size > 1 && it[0] == "a" && it[1] in idHexes }

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

    override fun getTagOfAddressableKind(kind: Int): ATag? {
        val kindStr = kind.toString()
        val aTag = tags
            .firstOrNull { it.size > 1 && it[0] == "a" && it[1].startsWith(kindStr) }
            ?.getOrNull(1)
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
            Nip19.createNEvent(id, pubKey, kind, null)
        }
    }

    fun toNostrUri(): String {
        return "nostr:${toNIP19()}"
    }

    /**
     * Checks if the ID is correct and then if the pubKey's secret key signed the event.
     */
    override fun checkSignature() {
        if (!id.contentEquals(generateId())) {
            throw Exception(
                """|Unexpected ID.
                   |  Event: ${toJson()}
                   |  Actual ID: $id
                   |  Generated: ${generateId()}
                """.trimIndent()
            )
        }
        if (!CryptoUtils.verifySignature(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))) {
            throw Exception("""Bad signature!""")
        }
    }

    override fun hasValidSignature(): Boolean {
        return try {
            id.contentEquals(generateId()) && CryptoUtils.verifySignature(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))
        } catch (e: Exception) {
            Log.e("Event", "Fail checking if event $id has a valid signature", e)
            false
        }
    }

    private fun generateId(): String {
        val rawEvent = listOf(0, pubKey, createdAt, kind, tags, content)

        // GSON decided to hardcode these replacements.
        // They break Nostr's hash check.
        // These lines revert their code.
        // https://github.com/google/gson/issues/2295
        val rawEventJson = gson.toJson(rawEvent)
            .replace("\\u2028", "\u2028")
            .replace("\\u2029", "\u2029")

        return CryptoUtils.sha256(rawEventJson.toByteArray()).toHexKey()
    }

    private class EventDeserializer : JsonDeserializer<Event> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Event {
            val jsonObject = json.asJsonObject
            return Event(
                id = jsonObject.get("id").asString.intern(),
                pubKey = jsonObject.get("pubkey").asString.intern(),
                createdAt = jsonObject.get("created_at").asLong,
                kind = jsonObject.get("kind").asInt,
                tags = jsonObject.get("tags").asJsonArray.map {
                    it.asJsonArray.mapNotNull { s -> if (s.isJsonNull) null else s.asString.intern() }
                },
                content = jsonObject.get("content").asString,
                sig = jsonObject.get("sig").asString
            )
        }
    }

    private class GossipDeserializer : JsonDeserializer<Gossip> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Gossip {
            val jsonObject = json.asJsonObject
            return Gossip(
                id = jsonObject.get("id")?.asString?.intern(),
                pubKey = jsonObject.get("pubkey")?.asString?.intern(),
                createdAt = jsonObject.get("created_at")?.asLong,
                kind = jsonObject.get("kind")?.asInt,
                tags = jsonObject.get("tags")?.asJsonArray?.mapNotNull {
                    it?.asJsonArray?.mapNotNull { s -> if (s?.isJsonNull != false) null else s.asString.intern() }
                },
                content = jsonObject.get("content")?.asString
            )
        }
    }

    private class EventSerializer : JsonSerializer<Event> {
        override fun serialize(
            src: Event,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonObject().apply {
                addProperty("id", src.id)
                addProperty("pubkey", src.pubKey)
                addProperty("created_at", src.createdAt)
                addProperty("kind", src.kind)
                add(
                    "tags",
                    JsonArray().also { jsonTags ->
                        src.tags.forEach { tag ->
                            jsonTags.add(
                                JsonArray().also { jsonTagElement ->
                                    tag.forEach { tagElement ->
                                        jsonTagElement.add(tagElement)
                                    }
                                }
                            )
                        }
                    }
                )
                addProperty("content", src.content)
                addProperty("sig", src.sig)
            }
        }
    }

    private class GossipSerializer : JsonSerializer<Gossip> {
        override fun serialize(
            src: Gossip,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonObject().apply {
                src.id?.let { addProperty("id", it) }
                src.pubKey?.let { addProperty("pubkey", it) }
                src.createdAt?.let { addProperty("created_at", it) }
                src.kind?.let { addProperty("kind", it) }
                src.tags?.let {
                    add(
                        "tags",
                        JsonArray().also { jsonTags ->
                            it.forEach { tag ->
                                jsonTags.add(
                                    JsonArray().also { jsonTagElement ->
                                        tag.forEach { tagElement ->
                                            jsonTagElement.add(tagElement)
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
                src.content?.let { addProperty("content", it) }
            }
        }
    }

    private class ByteArrayDeserializer : JsonDeserializer<ByteArray> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): ByteArray = Hex.decode(json.asString)
    }

    private class ByteArraySerializer : JsonSerializer<ByteArray> {
        override fun serialize(
            src: ByteArray,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ) = JsonPrimitive(src.toHexKey())
    }

    companion object {
        val gson: Gson = GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(Event::class.java, EventSerializer())
            .registerTypeAdapter(Event::class.java, EventDeserializer())
            .registerTypeAdapter(Gossip::class.java, GossipSerializer())
            .registerTypeAdapter(Gossip::class.java, GossipDeserializer())
            .registerTypeAdapter(ByteArray::class.java, ByteArraySerializer())
            .registerTypeAdapter(ByteArray::class.java, ByteArrayDeserializer())
            .registerTypeAdapter(Response::class.java, ResponseDeserializer())
            .registerTypeAdapter(Request::class.java, RequestDeserializer())
            .create()

        fun fromJson(json: String, lenient: Boolean = false): Event = gson.fromJson(json, Event::class.java).getRefinedEvent(lenient)

        fun fromJson(json: JsonElement, lenient: Boolean = false): Event = gson.fromJson(json, Event::class.java).getRefinedEvent(lenient)

        fun Event.getRefinedEvent(lenient: Boolean = false): Event {
            return EventFactory.create(id, pubKey, createdAt, kind, tags, content, sig, lenient)
        }

        fun generateId(pubKey: HexKey, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): ByteArray {
            val rawEvent = listOf(
                0,
                pubKey,
                createdAt,
                kind,
                tags,
                content
            )

            // GSON decided to hardcode these replacements.
            // They break Nostr's hash check.
            // These lines revert their code.
            // https://github.com/google/gson/issues/2295
            val rawEventJson = gson.toJson(rawEvent)
                .replace("\\u2028", "\u2028")
                .replace("\\u2029", "\u2029")

            return CryptoUtils.sha256(rawEventJson.toByteArray())
        }

        fun create(privateKey: ByteArray, kind: Int, tags: List<List<String>> = emptyList(), content: String = "", createdAt: Long = TimeUtils.now()): Event {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = Companion.generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey).toHexKey()
            return Event(id.toHexKey(), pubKey, createdAt, kind, tags, content, sig)
        }
    }
}

@Immutable
interface AddressableEvent {
    fun dTag(): String
    fun address(): ATag
}
