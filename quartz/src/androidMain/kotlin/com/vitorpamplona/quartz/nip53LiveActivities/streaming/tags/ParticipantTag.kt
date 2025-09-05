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
package com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.people.PubKeyReferenceTag
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

enum class ROLE(
    val code: String,
) {
    HOST("host"),
    MODERATOR("moderator"),
    SPEAKER("speaker"),
}

@Immutable
data class ParticipantTag(
    override val pubKey: String,
    override val relayHint: NormalizedRelayUrl?,
    val role: String?,
    val proof: String?,
) : PubKeyReferenceTag {
    companion object {
        const val TAG_NAME = "p"

        fun isIn(
            tag: Array<String>,
            keys: Set<HexKey>,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] in keys

        @JvmStatic
        fun isHost(tag: Tag): Boolean {
            ensure(tag.has(3)) { return false }
            ensure(tag[0] == TAG_NAME) { return false }
            ensure(tag[1].length == 64) { return false }
            ensure(tag[3] == ROLE.HOST.code) { return false }
            return true
        }

        @JvmStatic
        fun parse(tag: Tag): ParticipantTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val hint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

            return ParticipantTag(tag[1], hint, tag.getOrNull(3), tag.getOrNull(4))
        }

        @JvmStatic
        fun parseHost(tag: Tag): ParticipantTag? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[3] == ROLE.HOST.code) { return null }

            val hint = RelayUrlNormalizer.normalizeOrNull(tag[2])

            return ParticipantTag(tag[1], hint, tag[3], tag.getOrNull(4))
        }

        @JvmStatic
        fun parseKey(tag: Tag): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return tag[1]
        }

        @JvmStatic
        fun parseAsHint(tag: Array<String>): PubKeyHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val hint = RelayUrlNormalizer.normalizeOrNull(tag[2])
            ensure(hint != null) { return null }

            return PubKeyHint(tag[1], hint)
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
