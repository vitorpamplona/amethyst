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
package com.vitorpamplona.quartz.nip01Core.core

actual data class Address actual constructor(
    actual val kind: Kind,
    actual val pubKeyHex: HexKey,
    actual val dTag: String,
) : Comparable<Address> {
    actual fun toValue() = assemble(kind, pubKeyHex, dTag)

    actual override fun compareTo(other: Address): Int {
        val result = kind.compareTo(other.kind)
        return if (result == 0) {
            val result2 = pubKeyHex.compareTo(other.pubKeyHex)
            if (result2 == 0) {
                dTag.compareTo(other.dTag)
            } else {
                result2
            }
        } else {
            result
        }
    }

    actual companion object {
        actual fun assemble(
            kind: Int,
            pubKeyHex: HexKey,
            dTag: String,
        ) = AddressSerializer.assemble(kind, pubKeyHex, dTag)

        actual fun parse(addressId: String) = AddressSerializer.parse(addressId)

        actual fun isOfKind(
            addressId: String,
            kind: String,
        ) = addressId.startsWith(kind) && addressId[kind.length] == ':'
    }
}
