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
package com.vitorpamplona.quartz.nip02FollowList.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
data class ContactTag(
    val pubKey: HexKey,
) {
    var relayUri: NormalizedRelayUrl? = null
    var petname: String? = null

    constructor(
        pubKey: HexKey,
        relayHint: NormalizedRelayUrl?,
        petname: String?,
    ) : this(pubKey) {
        this.relayUri = relayHint
        this.petname = petname
    }

    fun toTagArray() = assemble(pubKey, relayUri, petname)

    companion object {
        const val TAG_NAME = "p"

        fun isTagged(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): ContactTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val hint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

            return ContactTag(tag[1], hint, tag.getOrNull(3))
        }

        fun parseValid(tag: Array<String>): ContactTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val hint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

            return try {
                ContactTag(decodePublicKey(tag[1]).toHexKey(), hint, tag.getOrNull(3))
            } catch (e: Exception) {
                Log.w("ContactTag", "Can't parse contact list p-tag ${tag.joinToString(", ")}", e)
                null
            }
        }

        fun parseKey(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return tag[1]
        }

        fun parseValidKey(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return try {
                if (Hex.isHex64(tag[1])) {
                    tag[1]
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w("ContactListEvent", "Can't parse contact list pubkey ${tag.joinToString(", ")}", e)
                null
            }
        }

        fun parseAsHint(tag: Array<String>): PubKeyHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val hint = RelayUrlNormalizer.normalizeOrNull(tag[2])
            ensure(hint != null) { return null }

            return PubKeyHint(tag[1], hint)
        }

        fun assemble(
            pubkey: HexKey,
            relayUri: NormalizedRelayUrl? = null,
            petname: String? = null,
        ) = arrayOfNotNull(TAG_NAME, pubkey, relayUri?.url, petname)
    }
}
