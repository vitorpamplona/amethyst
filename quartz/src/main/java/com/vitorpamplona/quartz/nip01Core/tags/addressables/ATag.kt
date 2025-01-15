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
package com.vitorpamplona.quartz.nip01Core.tags.addressables

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes
import com.vitorpamplona.quartz.utils.removeTrailingNullsAndEmptyOthers

@Immutable
data class ATag(
    val kind: Int,
    val pubKeyHex: String,
    val dTag: String,
) {
    var relay: String? = null

    constructor(
        kind: Int,
        pubKeyHex: HexKey,
        dTag: String,
        relayHint: String?,
    ) : this(kind, pubKeyHex, dTag) {
        this.relay = relayHint
    }

    fun countMemory(): Long =
        5 * pointerSizeInBytes + // 7 fields, 4 bytes each reference (32bit)
            8L + // kind
            pubKeyHex.bytesUsedInMemory() +
            dTag.bytesUsedInMemory() +
            (relay?.bytesUsedInMemory() ?: 0)

    fun toTag() = assembleATagId(kind, pubKeyHex, dTag)

    fun toATagArray() = removeTrailingNullsAndEmptyOthers(TAG_NAME, toTag(), relay)

    fun toQTagArray() = removeTrailingNullsAndEmptyOthers("q", toTag(), relay)

    companion object {
        const val TAG_NAME = "a"

        fun assembleATagId(
            kind: Int,
            pubKeyHex: HexKey,
            dTag: String,
        ) = "$kind:$pubKeyHex:$dTag"

        fun parse(
            aTagId: String,
            relay: String?,
        ): ATag? =
            try {
                val parts = aTagId.split(":", limit = 3)
                if (Hex.isHex(parts[1])) {
                    ATag(parts[0].toInt(), parts[1], parts[2], relay)
                } else {
                    Log.w("ATag", "Error parsing A Tag. Pubkey is not hex: $aTagId")
                    null
                }
            } catch (t: Throwable) {
                Log.w("ATag", "Error parsing A Tag: $aTagId: ${t.message}")
                null
            }

        @JvmStatic
        fun parse(tags: Array<String>): ATag? {
            require(tags[0] == TAG_NAME)
            return parse(tags[1], tags.getOrNull(2))
        }

        @JvmStatic
        fun assemble(
            aTagId: HexKey,
            relay: String?,
        ) = arrayOfNotNull(TAG_NAME, aTagId, relay)

        @JvmStatic
        fun assemble(
            kind: Int,
            pubKeyHex: String,
            dTag: String,
            relay: String?,
        ) = arrayOfNotNull(TAG_NAME, assembleATagId(kind, pubKeyHex, dTag), relay)
    }
}
