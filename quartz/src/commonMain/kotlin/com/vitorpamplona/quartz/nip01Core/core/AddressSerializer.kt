/*
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

import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log

class AddressSerializer {
    companion object {
        fun assemble(
            kind: Int,
            pubKeyHex: HexKey,
            dTag: String = "",
        ) = buildString {
            append(kind)
            append(":")
            append(pubKeyHex)
            append(":")
            append(dTag)
        }

        fun parse(addressId: String): Address? {
            if (addressId.length < 66) return null
            if (addressId.isBlank()) return null
            return try {
                val parts = addressId.split(":", limit = 3)
                if (parts.size > 2 && parts[1].length == 64 && Hex.isHex(parts[1])) {
                    if (parts[0].length > 5) {
                        // invalid kind
                        Log.w("AddressableId") { "Error parsing. invalid kind $addressId" }
                        null
                    } else {
                        Address(parts[0].toInt(), parts[1], parts.getOrNull(2) ?: "")
                    }
                } else {
                    if (addressId.startsWith("naddr1")) {
                        val addr = Nip19Parser.uriToRoute(addressId)?.entity
                        if (addr is NAddress) {
                            addr.address()
                        } else {
                            Log.w("AddressableId") { "Error parsing. naddr1 seems invalid: $addressId" }
                            null
                        }
                    } else {
                        Log.w("AddressableId") { "Error parsing. Not a valid address: $addressId" }
                        null
                    }
                }
            } catch (t: Throwable) {
                Log.w("AddressableId", "Error parsing: $addressId: ${t.message}", t)
                null
            }
        }

        fun isOfKind(
            addressId: String,
            kind: String,
        ) = addressId.length > kind.length && addressId.startsWith(kind) && addressId[kind.length] == ':'

        /**
         * True when [value] has the shape of an address id — `<kind>:<64-hex pubkey>:<d-tag>`, the
         * kind 1 to 5 digits and the d-tag possibly empty — without parsing it.
         *
         * Hint parsers run on every relay copy of every received event and only need to know the
         * value is worth indexing; [parse] splits, allocates and falls back to bech32. A bare
         * `contains(':')` is too weak the other way: `foo:bar` would be indexed as an address.
         * Accepts upper-case hex like [parse] does.
         */
        fun isAddressShape(value: String): Boolean {
            var kindEnd = 0
            while (kindEnd < value.length && kindEnd <= 5 && value[kindEnd] in '0'..'9') kindEnd++
            if (kindEnd == 0 || kindEnd > 5) return false
            val keyEnd = kindEnd + 65
            if (value.length <= keyEnd || value[kindEnd] != ':' || value[keyEnd] != ':') return false
            for (i in kindEnd + 1 until keyEnd) {
                val c = value[i]
                if (c !in '0'..'9' && c !in 'a'..'f' && c !in 'A'..'F') return false
            }
            return true
        }
    }
}
