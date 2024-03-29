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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import java.math.BigDecimal

@Immutable
interface EventInterface {
    fun isContentEncoded(): Boolean

    fun countMemory(): Long

    fun id(): HexKey

    fun pubKey(): HexKey

    fun createdAt(): Long

    fun kind(): Int

    fun tags(): Array<Array<String>>

    fun content(): String

    fun sig(): HexKey

    fun toJson(): String

    fun checkSignature()

    fun hasValidSignature(): Boolean

    fun isTagged(
        key: String,
        tag: String,
    ): Boolean

    fun isAnyTagged(
        key: String,
        tags: Set<String>,
    ): Boolean

    fun isTaggedWord(word: String): Boolean

    fun isTaggedUser(idHex: String): Boolean

    fun isTaggedUsers(idHexes: Set<String>): Boolean

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

    fun hasHashtags(): Boolean

    fun hasGeohashes(): Boolean

    fun hashtags(): List<String>

    fun geohashes(): List<String>

    fun getReward(): BigDecimal?

    fun getPoWRank(): Int

    fun getGeoHash(): String?

    fun zapSplitSetup(): List<ZapSplitSetup>

    fun isSensitive(): Boolean

    fun subject(): String?

    fun zapraiserAmount(): Long?

    fun hasAnyTaggedUser(): Boolean

    fun hasTagWithContent(tagName: String): Boolean

    fun taggedAddresses(): List<ATag>

    fun taggedUsers(): List<HexKey>

    fun taggedEvents(): List<HexKey>

    fun taggedUrls(): List<String>

    fun firstTaggedAddress(): ATag?

    fun firstTaggedUser(): HexKey?

    fun firstTaggedEvent(): HexKey?

    fun firstTaggedUrl(): String?

    fun firstTaggedK(): Int?

    fun taggedEmojis(): List<EmojiUrl>

    fun matchTag1With(text: String): Boolean

    fun isExpired(): Boolean

    fun isExpirationBefore(time: Long): Boolean

    fun hasZapSplitSetup(): Boolean
}
