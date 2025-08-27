/**
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
package com.vitorpamplona.quartz.nip17Dm.base

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import kotlinx.collections.immutable.toImmutableSet

@Immutable
open class BaseDMGroupEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : WrappedEvent(id, pubKey, createdAt, kind, tags, content, sig),
    ChatroomKeyable,
    NIP17Group,
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    /** Recipients intended to receive this conversation */
    fun recipients() = tags.mapNotNull(PTag::parse)

    /** Recipients intended to receive this conversation */
    fun recipientsPubKey() = tags.mapNotNull(PTag::parseKey)

    fun talkingWith(oneSideHex: String): Set<HexKey> {
        val listedPubKeys = recipientsPubKey()

        val result =
            if (pubKey == oneSideHex) {
                listedPubKeys.toSet().minus(oneSideHex)
            } else {
                listedPubKeys.plus(pubKey).toSet().minus(oneSideHex)
            }

        if (result.isEmpty()) {
            // talking to myself
            return setOf(pubKey)
        }

        return result
    }

    override fun isIncluded(pubKey: HexKey) = pubKey == this.pubKey || tags.any(PTag::isTagged, pubKey)

    override fun groupMembers() = recipientsPubKey().plus(pubKey).toSet()

    override fun chatroomKey(toRemove: String): ChatroomKey = ChatroomKey(talkingWith(toRemove).toImmutableSet())
}
