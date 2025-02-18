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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

data class Address(
    val kind: Int,
    val pubKeyHex: HexKey,
    val dTag: String,
) {
    fun toValue() = assemble(kind, pubKeyHex, dTag)

    fun countMemory(): Long =
        3 * pointerSizeInBytes +
            8L + // kind
            pubKeyHex.bytesUsedInMemory() +
            dTag.bytesUsedInMemory()

    companion object {
        fun assemble(
            kind: Int,
            pubKeyHex: HexKey,
            dTag: String,
        ) = "$kind:$pubKeyHex:$dTag"

        @JvmStatic
        fun parse(addressId: String): Address? =
            try {
                val parts = addressId.split(":", limit = 3)
                if (parts[1].length == 64 && Hex.isHex(parts[1])) {
                    Address(parts[0].toInt(), parts[1], parts[2])
                } else {
                    Log.w("AddressableId", "Error parsing. Pubkey is not hex: $addressId")
                    null
                }
            } catch (t: Throwable) {
                Log.e("AddressableId", "Error parsing: $addressId: ${t.message}", t)
                null
            }

        fun isOfKind(
            addressId: String,
            kind: String,
        ) = addressId.startsWith(kind) && addressId[kind.length] == ':'
    }
}
