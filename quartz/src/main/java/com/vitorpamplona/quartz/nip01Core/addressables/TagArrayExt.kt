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
package com.vitorpamplona.quartz.nip01Core.addressables

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.firstMapTagged
import com.vitorpamplona.quartz.nip01Core.core.isAnyTagged
import com.vitorpamplona.quartz.nip01Core.core.isTagged
import com.vitorpamplona.quartz.nip01Core.core.mapTagged
import com.vitorpamplona.quartz.nip01Core.core.mapValueTagged
import com.vitorpamplona.quartz.nip19Bech32Entities.parse

fun <R> TagArray.mapTaggedAddress(map: (address: String) -> R) = this.mapValueTagged("a", map)

fun TagArray.firstIsTaggedAddressableNote(addressableNotes: Set<String>) =
    this
        .firstOrNull { it.size > 1 && it[0] == "a" && it[1] in addressableNotes }
        ?.getOrNull(1)

fun TagArray.isTaggedAddressableNote(idHex: String) = this.isTagged("a", idHex)

fun TagArray.isTaggedAddressableNotes(idHexes: Set<String>) = this.isAnyTagged("a", idHexes)

fun TagArray.isTaggedAddressableKind(kind: Int): Boolean {
    val kindStr = kind.toString()
    return this.any { it.size > 1 && it[0] == "a" && it[1].startsWith(kindStr) }
}

fun TagArray.getTagOfAddressableKind(kind: Int): ATag? {
    val kindStr = kind.toString()
    val aTag =
        this
            .firstOrNull { it.size > 1 && it[0] == "a" && it[1].startsWith(kindStr) }
            ?.getOrNull(1)
            ?: return null

    return ATag.parse(aTag, null)
}

fun TagArray.taggedAddresses() = this.mapTagged("a") { ATag.parse(it[1], it.getOrNull(2)) }

fun TagArray.firstTaggedAddress() = this.firstMapTagged("a") { ATag.parse(it[1], it.getOrNull(2)) }
