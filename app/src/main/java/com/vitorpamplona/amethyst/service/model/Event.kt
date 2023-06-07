package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import fr.acinq.secp256k1.Hex
import fr.acinq.secp256k1.Secp256k1
import nostr.postr.Utils
import nostr.postr.toHex
import java.lang.reflect.Type
import java.math.BigDecimal
import java.security.MessageDigest
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
    override fun id(): HexKey = id

    override fun pubKey(): HexKey = pubKey

    override fun createdAt(): Long = createdAt

    override fun kind(): Int = kind

    override fun tags(): List<List<String>> = tags

    override fun content(): String = content

    override fun sig(): HexKey = sig

    override fun toJson(): String = gson.toJson(this)

    fun hasAnyTaggedUser() = tags.any { it.size > 1 && it[0] == "p" }

    fun taggedUsers() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }
    fun taggedEvents() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }

    fun taggedUrls() = tags.filter { it.size > 1 && it[0] == "r" }.map { it[1] }

    override fun isSensitive() = tags.any {
        (it.size > 0 && it[0].equals("content-warning", true)) ||
            (it.size > 1 && it[0] == "t" && it[1].equals("nsfw", true)) ||
            (it.size > 1 && it[0] == "t" && it[1].equals("nude", true))
    }

    override fun zapAddress() = tags.firstOrNull { it.size > 1 && it[0] == "zap" }?.get(1)

    fun taggedAddresses() = tags.filter { it.size > 1 && it[0] == "a" }.mapNotNull {
        val aTagValue = it[1]
        val relay = it.getOrNull(2)

        ATag.parse(aTagValue, relay)
    }

    override fun hashtags() = tags.filter { it.size > 1 && it[0] == "t" }.map { it[1] }

    override fun isTaggedUser(idHex: String) = tags.any { it.size > 1 && it[0] == "p" && it[1] == idHex }

    override fun isTaggedHash(hashtag: String) = tags.any { it.size > 1 && it[0] == "t" && it[1].equals(hashtag, true) }
    override fun isTaggedHashes(hashtags: Set<String>) = tags.any { it.size > 1 && it[0] == "t" && it[1].lowercase() in hashtags }
    override fun firstIsTaggedHashes(hashtags: Set<String>) = tags.firstOrNull { it.size > 1 && it[0] == "t" && it[1].lowercase() in hashtags }?.getOrNull(1)

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

    override fun getReward(): BigDecimal? {
        return try {
            tags.firstOrNull { it.size > 1 && it[0] == "reward" }?.get(1)?.let { BigDecimal(it) }
        } catch (e: Exception) {
            null
        }
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
        if (!secp256k1.verifySchnorr(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))) {
            throw Exception("""Bad signature!""")
        }
    }

    override fun hasValidSignature(): Boolean {
        return try {
            id.contentEquals(generateId()) && secp256k1.verifySchnorr(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))
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

        return MessageDigest.getInstance("SHA-256").digest(rawEventJson.toByteArray()).toHexKey()
    }

    private class EventDeserializer : JsonDeserializer<Event> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Event {
            val jsonObject = json.asJsonObject
            return Event(
                id = jsonObject.get("id").asString,
                pubKey = jsonObject.get("pubkey").asString,
                createdAt = jsonObject.get("created_at").asLong,
                kind = jsonObject.get("kind").asInt,
                tags = jsonObject.get("tags").asJsonArray.map {
                    it.asJsonArray.mapNotNull { s -> if (s.isJsonNull) null else s.asString }
                },
                content = jsonObject.get("content").asString,
                sig = jsonObject.get("sig").asString
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
        ) = JsonPrimitive(src.toHex())
    }

    companion object {
        private val secp256k1 = Secp256k1.get()

        val gson: Gson = GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(Event::class.java, EventSerializer())
            .registerTypeAdapter(Event::class.java, EventDeserializer())
            .registerTypeAdapter(ByteArray::class.java, ByteArraySerializer())
            .registerTypeAdapter(ByteArray::class.java, ByteArrayDeserializer())
            .registerTypeAdapter(Response::class.java, ResponseDeserializer())
            .registerTypeAdapter(Request::class.java, RequestDeserializer())
            .create()

        fun fromJson(json: String, lenient: Boolean = false): Event = gson.fromJson(json, Event::class.java).getRefinedEvent(lenient)

        fun fromJson(json: JsonElement, lenient: Boolean = false): Event = gson.fromJson(json, Event::class.java).getRefinedEvent(lenient)

        fun Event.getRefinedEvent(lenient: Boolean = false): Event = when (kind) {
            AppDefinitionEvent.kind -> AppDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
            AppRecommendationEvent.kind -> AppRecommendationEvent(id, pubKey, createdAt, tags, content, sig)
            AudioTrackEvent.kind -> AudioTrackEvent(id, pubKey, createdAt, tags, content, sig)
            BadgeAwardEvent.kind -> BadgeAwardEvent(id, pubKey, createdAt, tags, content, sig)
            BadgeDefinitionEvent.kind -> BadgeDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
            BadgeProfilesEvent.kind -> BadgeProfilesEvent(id, pubKey, createdAt, tags, content, sig)
            BookmarkListEvent.kind -> BookmarkListEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelCreateEvent.kind -> ChannelCreateEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelHideMessageEvent.kind -> ChannelHideMessageEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelMessageEvent.kind -> ChannelMessageEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelMetadataEvent.kind -> ChannelMetadataEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelMuteUserEvent.kind -> ChannelMuteUserEvent(id, pubKey, createdAt, tags, content, sig)
            ContactListEvent.kind -> ContactListEvent(id, pubKey, createdAt, tags, content, sig)
            DeletionEvent.kind -> DeletionEvent(id, pubKey, createdAt, tags, content, sig)

            FileHeaderEvent.kind -> FileHeaderEvent(id, pubKey, createdAt, tags, content, sig)
            FileStorageEvent.kind -> FileStorageEvent(id, pubKey, createdAt, tags, content, sig)
            FileStorageHeaderEvent.kind -> FileStorageHeaderEvent(id, pubKey, createdAt, tags, content, sig)
            HighlightEvent.kind -> HighlightEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapEvent.kind -> LnZapEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapPaymentRequestEvent.kind -> LnZapPaymentRequestEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapPaymentResponseEvent.kind -> LnZapPaymentResponseEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapRequestEvent.kind -> LnZapRequestEvent(id, pubKey, createdAt, tags, content, sig)
            LongTextNoteEvent.kind -> LongTextNoteEvent(id, pubKey, createdAt, tags, content, sig)
            MetadataEvent.kind -> MetadataEvent(id, pubKey, createdAt, tags, content, sig)
            PeopleListEvent.kind -> PeopleListEvent(id, pubKey, createdAt, tags, content, sig)
            PinListEvent.kind -> PinListEvent(id, pubKey, createdAt, tags, content, sig)
            PollNoteEvent.kind -> PollNoteEvent(id, pubKey, createdAt, tags, content, sig)
            PrivateDmEvent.kind -> PrivateDmEvent(id, pubKey, createdAt, tags, content, sig)
            ReactionEvent.kind -> ReactionEvent(id, pubKey, createdAt, tags, content, sig)
            RecommendRelayEvent.kind -> RecommendRelayEvent(id, pubKey, createdAt, tags, content, sig, lenient)
            RelaySetEvent.kind -> RelaySetEvent(id, pubKey, createdAt, tags, content, sig)
            ReportEvent.kind -> ReportEvent(id, pubKey, createdAt, tags, content, sig)
            RepostEvent.kind -> RepostEvent(id, pubKey, createdAt, tags, content, sig)
            TextNoteEvent.kind -> TextNoteEvent(id, pubKey, createdAt, tags, content, sig)
            else -> this
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

            return MessageDigest.getInstance("SHA-256").digest(rawEventJson.toByteArray())
        }

        fun create(privateKey: ByteArray, kind: Int, tags: List<List<String>> = emptyList(), content: String = "", createdAt: Long = Date().time / 1000): Event {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val id = Companion.generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey).toHexKey()
            return Event(id.toHexKey(), pubKey, createdAt, kind, tags, content, sig)
        }
    }
}

@Immutable
interface AddressableEvent {
    fun dTag(): String
    fun address(): ATag
}
