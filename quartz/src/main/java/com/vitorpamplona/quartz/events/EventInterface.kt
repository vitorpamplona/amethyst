package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import java.math.BigDecimal

@Immutable
interface EventInterface {
    fun countMemory(): Long

    fun id(): HexKey

    fun pubKey(): HexKey

    fun createdAt(): Long

    fun kind(): Int

    fun tags(): List<List<String>>

    fun content(): String

    fun sig(): HexKey

    fun toJson(): String

    fun checkSignature()

    fun hasValidSignature(): Boolean

    fun isTaggedUser(idHex: String): Boolean
    fun isTaggedUsers(idHex: Set<String>): Boolean

    fun isTaggedEvent(idHex: String): Boolean

    fun isTaggedAddressableNote(idHex: String): Boolean
    fun isTaggedAddressableNotes(idHexes: Set<String>): Boolean

    fun isTaggedHash(hashtag: String): Boolean
    fun isTaggedGeoHash(hashtag: String): Boolean

    fun isTaggedHashes(hashtags: Set<String>): Boolean
    fun isTaggedGeoHashes(hashtags: Set<String>): Boolean

    fun firstIsTaggedHashes(hashtags: Set<String>): String?
    fun firstIsTaggedAddressableNote(addressableNotes: Set<String>): String?

    fun isTaggedAddressableKind(kind: Int): Boolean
    fun getTagOfAddressableKind(kind: Int): ATag?

    fun expiration(): Long?

    fun hashtags(): List<String>
    fun geohashes(): List<String>

    fun getReward(): BigDecimal?
    fun getPoWRank(): Int
    fun getGeoHash(): String?

    fun zapAddress(): String?
    fun isSensitive(): Boolean
    fun subject(): String?
    fun zapraiserAmount(): Long?

    fun taggedAddresses(): List<ATag>
    fun taggedUsers(): List<HexKey>
    fun taggedEvents(): List<HexKey>
    fun taggedUrls(): List<String>

    fun firstTaggedAddress(): ATag?
    fun firstTaggedUser(): HexKey?
    fun firstTaggedEvent(): HexKey?
    fun firstTaggedUrl(): String?

    fun taggedEmojis(): List<EmojiUrl>
    fun matchTag1With(text: String): Boolean
    fun isExpired(): Boolean
}
