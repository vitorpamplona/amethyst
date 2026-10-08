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
package com.vitorpamplona.quartz.nip34Git.ci.tags

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.utils.ensure

/**
 * Nostr CI `a` tag: a NIP-34 repository announcement, `["a", "30617:<pubkey>:<repo-id>", <relay>?]`.
 *
 * A thin layer over [ATag] that adds the CI rules ngit enforces: the address must be a kind
 * 30617 announcement with a non-empty repository id. Multi-maintainer repositories are announced
 * once per maintainer, so CI events MAY repeat this tag, one per maintainer coordinate.
 *
 * [parseAddressId] returns the re-serialized, validated `kind:pubkey:d` (never the raw tag value),
 * which is what `linkedAddressIds()` must contain.
 */
class RepositoryTag {
    companion object {
        const val TAG_NAME = ATag.TAG_NAME

        fun isRepository(address: Address) = address.kind == GitRepositoryEvent.KIND && address.dTag.isNotEmpty()

        fun isTag(tag: Array<String>) = parseAddress(tag) != null

        fun parse(tag: Array<String>): ATag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val aTag = ATag.parse(tag) ?: return null
            ensure(aTag.kind == GitRepositoryEvent.KIND && aTag.dTag.isNotEmpty()) { return null }
            return aTag
        }

        fun parseAddress(tag: Array<String>): Address? {
            val address = ATag.parseAddress(tag) ?: return null
            ensure(isRepository(address)) { return null }
            return address
        }

        fun parseAddressId(tag: Array<String>): String? = parseAddress(tag)?.toValue()

        fun parseAsHint(tag: Array<String>): AddressHint? {
            ensure(tag.has(2)) { return null }
            val aTag = parse(tag) ?: return null
            val relay = aTag.relay ?: return null
            return AddressHint(aTag.toTag(), relay)
        }

        fun assemble(
            address: Address,
            relay: NormalizedRelayUrl?,
        ) = ATag.assemble(address, relay)

        fun assemble(aTag: ATag) = aTag.toATagArray()
    }
}
