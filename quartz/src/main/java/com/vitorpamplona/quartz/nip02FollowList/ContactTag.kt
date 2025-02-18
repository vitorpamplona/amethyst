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
package com.vitorpamplona.quartz.nip02FollowList

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
data class ContactTag(
    val pubKey: HexKey,
) {
    var relayUri: String? = null
    var petname: String? = null

    constructor(
        pubKey: HexKey,
        relayHint: String?,
        petname: String?,
    ) : this(pubKey) {
        this.relayUri = relayHint
        this.petname = petname
    }

    fun countMemory(): Long =
        3 * pointerSizeInBytes +
            pubKey.bytesUsedInMemory() +
            (relayUri?.bytesUsedInMemory() ?: 0) +
            (petname?.bytesUsedInMemory() ?: 0)

    fun toTagArray() = assemble(pubKey, relayUri, petname)

    companion object {
        const val TAG_NAME = "p"
        const val TAG_SIZE = 2

        @JvmStatic
        fun isTagged(tag: Array<String>) = tag.size >= TAG_SIZE && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        @JvmStatic
        fun parse(tag: Array<String>): ContactTag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return ContactTag(tag[1], tag.getOrNull(2), tag.getOrNull(3))
        }

        @JvmStatic
        fun parseValid(tag: Array<String>): ContactTag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return try {
                ContactTag(decodePublicKey(tag[1]).toHexKey(), tag.getOrNull(2), tag.getOrNull(3))
            } catch (e: Exception) {
                Log.w("ContactTag", "Can't parse contact list p-tag ${tag.joinToString(", ")}", e)
                null
            }
        }

        @JvmStatic
        fun parseValidKey(tag: Array<String>): String? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return try {
                decodePublicKey(tag[1]).toHexKey()
            } catch (e: Exception) {
                Log.w("ContactListEvent", "Can't parse contact list pubkey ${tag.joinToString(", ")}", e)
                null
            }
        }

        @JvmStatic
        fun assemble(
            pubkey: HexKey,
            relayUri: String? = null,
            petname: String? = null,
        ) = arrayOfNotNull(TAG_NAME, pubkey, relayUri, petname)
    }
}
