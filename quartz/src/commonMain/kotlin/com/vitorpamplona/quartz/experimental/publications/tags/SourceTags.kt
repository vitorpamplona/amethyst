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
package com.vitorpamplona.quartz.experimental.publications.tags

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.ensure

/**
 * NKBIP-01 derivative works name their ORIGINAL with uppercase tags:
 * `["A", "<kind:pubkey:d>", "<relay>"]` and `["E", "<event id>", "<relay>", "<pubkey>"]`.
 *
 * They are not sections (see [com.vitorpamplona.quartz.experimental.publications.PublicationSectionRef]),
 * but they are references a reader has to fetch, so the hint providers link them.
 */
object SourceAddressTag {
    const val TAG_NAME = "A"

    /** The original's coordinate, validated. */
    fun parseAddressId(tag: Array<String>): String? {
        ensure(tag.has(1)) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].isNotEmpty()) { return null }
        return Address.parse(tag[1])?.toValue()
    }

    fun parseAsHint(tag: Array<String>): AddressHint? {
        ensure(tag.has(2)) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].isNotEmpty()) { return null }
        ensure(tag[2].isNotEmpty()) { return null }
        val relay = RelayUrlNormalizer.normalizeHintOrNull(tag[2]) ?: return null
        val address = Address.parse(tag[1]) ?: return null
        return AddressHint(address.toValue(), relay)
    }
}

/** See [SourceAddressTag]. */
object SourceEventTag {
    const val TAG_NAME = "E"

    fun parseId(tag: Array<String>): HexKey? {
        ensure(tag.has(1)) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].isValid()) { return null }
        return tag[1]
    }

    fun parseAsHint(tag: Array<String>): EventIdHint? {
        ensure(tag.has(2)) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].isValid()) { return null }
        ensure(tag[2].isNotEmpty()) { return null }
        val relay = RelayUrlNormalizer.normalizeHintOrNull(tag[2]) ?: return null
        return EventIdHint(tag[1], relay)
    }
}
