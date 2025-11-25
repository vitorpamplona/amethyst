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
package com.vitorpamplona.quartz.experimental.trustedAssertions.list.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.ensure

data class ServiceProviderTag(
    val service: ServiceType,
    val pubkey: HexKey,
    val relayUrl: NormalizedRelayUrl,
) {
    fun toTagArray() = assemble(service, pubkey, relayUrl)

    companion object {
        fun parse(tag: Array<String>): ServiceProviderTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0].isNotEmpty()) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val service = ServiceType.parse(tag[0]) ?: return null
            val relay = RelayUrlNormalizer.normalizeOrNull(tag[2]) ?: return null

            return ServiceProviderTag(service, tag[1], relay)
        }

        fun assemble(
            serviceType: String,
            pubkey: HexKey,
            relayUrl: String,
        ) = arrayOf(serviceType, pubkey, relayUrl)

        fun assemble(
            service: ServiceType,
            pubkey: HexKey,
            relayUrl: NormalizedRelayUrl,
        ) = assemble(service.toValue(), pubkey, relayUrl.url)

        fun assemble(id: ServiceProviderTag) = arrayOf(id.service, id.pubkey, id.relayUrl)
    }
}
