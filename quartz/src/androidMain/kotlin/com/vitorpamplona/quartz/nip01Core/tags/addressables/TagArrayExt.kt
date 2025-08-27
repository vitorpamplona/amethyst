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
package com.vitorpamplona.quartz.nip01Core.tags.addressables

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.core.mapValueTagged

fun <R> TagArray.mapTaggedAddress(map: (address: String) -> R) = this.mapValueTagged(ATag.TAG_NAME, map)

fun TagArray.firstIsTaggedAddressableNote(addressableNotes: Set<String>) = this.fastFirstNotNullOfOrNull(ATag::parseIfIsIn, addressableNotes)

fun TagArray.isTaggedAddressableNote(addressId: String) = this.any(ATag::isTagged, addressId)

fun TagArray.isTaggedAddressableNotes(addressIds: Set<String>) = this.any(ATag::isIn, addressIds)

fun TagArray.isTaggedAddressableKind(kind: Int) = this.any(ATag::isTaggedWithKind, kind.toString())

fun TagArray.isTaggedAddressableKind(kindStr: String) = this.any(ATag::isTaggedWithKind, kindStr)

fun TagArray.getTagOfAddressableKind(kind: Int) = this.fastFirstNotNullOfOrNull(ATag::parseIfOfKind, kind.toString())

fun TagArray.getTagOfAddressableKind(kindStr: String) = this.fastFirstNotNullOfOrNull(ATag::parseIfOfKind, kindStr.toString())

fun TagArray.taggedATags() = this.mapNotNull(ATag::parse)

fun TagArray.firstTaggedATag() = this.fastFirstNotNullOfOrNull(ATag::parse)

fun TagArray.taggedAddresses() = this.mapNotNull(ATag::parseAddress)

fun TagArray.firstTaggedAddress() = this.fastFirstNotNullOfOrNull(ATag::parseAddress)
