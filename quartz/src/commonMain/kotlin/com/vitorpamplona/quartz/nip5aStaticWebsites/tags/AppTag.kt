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
package com.vitorpamplona.quartz.nip5aStaticWebsites.tags

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * The NIP-5A upstream app descriptor tag: `["app", "<kind>:<pubkey>:<d-tag>", "<relay>"]`.
 *
 * NIP-5A: "the second element is an addressable event reference to an app descriptor and the third
 * element is a relay hint". The descriptor kind is intentionally generic (NIP-89 31990 and others),
 * so no kind is enforced here, only a well-formed address.
 */
class AppTag {
    companion object {
        const val TAG_NAME = "app"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        /** The app descriptor's address, or null when the value is not a well-formed `kind:pubkey:d`. */
        fun parseAddress(tag: Array<String>): Address? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return AddressSerializer.parse(tag[1])
        }

        fun parseAsHint(tag: Array<String>): AddressHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[1].contains(':')) { return null }
            ensure(tag[2].isNotEmpty()) { return null }
            val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2]) ?: return null
            return AddressHint(tag[1], relayHint)
        }

        fun assemble(
            address: Address,
            relay: NormalizedRelayUrl? = null,
        ) = arrayOfNotNull(TAG_NAME, address.toValue(), relay?.url)
    }
}
