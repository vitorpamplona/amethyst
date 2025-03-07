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
package com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.isNotName
import com.vitorpamplona.quartz.nip01Core.tags.people.PubKeyReferenceTag
import com.vitorpamplona.quartz.utils.arrayOfNotNull

enum class ROLE(
    val code: String,
) {
    HOST("host"),
    SPEAKER("speaker"),
}

@Immutable
data class ParticipantTag(
    override val pubKey: String,
    override val relayHint: String?,
    val role: String?,
    val proof: String?,
) : PubKeyReferenceTag {
    companion object {
        const val TAG_NAME = "p"
        const val TAG_SIZE = 2

        fun isIn(
            tag: Array<String>,
            keys: Set<HexKey>,
        ) = tag.size >= TAG_SIZE && tag[0] == TAG_NAME && tag[1] in keys

        @JvmStatic
        fun isHost(tag: Tag): Boolean {
            if (tag.isNotName(TAG_NAME, TAG_SIZE)) return false
            if (tag[1].length != 64) return false
            if (tag.getOrNull(3).equals(ROLE.HOST.code)) return true
            return false
        }

        @JvmStatic
        fun parse(tag: Tag): ParticipantTag? {
            if (tag.isNotName(TAG_NAME, TAG_SIZE)) return null
            if (tag[1].length != 64) return null
            return ParticipantTag(tag[1], tag.getOrNull(2), tag.getOrNull(3), tag.getOrNull(4))
        }

        @JvmStatic
        fun parseHost(tag: Tag): ParticipantTag? {
            if (tag.isNotName(TAG_NAME, TAG_SIZE)) return null
            if (tag[1].length != 64) return null
            if (!tag.getOrNull(3).equals(ROLE.HOST.code)) return null
            return ParticipantTag(tag[1], tag.getOrNull(2), tag.getOrNull(3), tag.getOrNull(4))
        }

        @JvmStatic
        fun parseKey(tag: Tag): String? {
            if (tag.isNotName(TAG_NAME, TAG_SIZE)) return null
            if (tag[1].length != 64) return null
            return tag[1]
        }

        @JvmStatic
        fun assemble(
            pubkey: HexKey,
            relayHint: String?,
            role: String?,
            proof: String?,
        ) = arrayOfNotNull(TAG_NAME, pubkey, relayHint, role, proof)
    }
}
