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
package com.vitorpamplona.quartz.nip17Dm.settings.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.utils.ensure

class RelayTag {
    companion object {
        const val TAG_NAME = "relay"

        fun match(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun notMatch(tag: Array<String>) = !(tag.has(0) && tag[0] == TAG_NAME)

        /**
         * Parse, dropping local-network entries.
         *
         * The drop is for lists that came from SOMEONE ELSE: a relay list is
         * attacker-supplied input, and an entry naming `127.0.0.1` or an
         * RFC 1918 address would aim our connection at our own machine or LAN.
         * Use [parseUnfiltered] to read back a list this account published
         * itself.
         */
        fun parse(tag: Array<String>): NormalizedRelayUrl? {
            val relay = parseUnfiltered(tag)
            ensure(relay != null && !relay.isLocalHost()) { return null }
            return relay
        }

        /**
         * Parse without the local-network drop, for reading back OUR OWN list.
         *
         * A user who configured a local relay meant it, and [parse] would
         * report their list as empty — which a publisher then treats as
         * "unconfigured" and answers with a default relay set the user never
         * chose.
         */
        fun parseUnfiltered(tag: Array<String>): NormalizedRelayUrl? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return RelayUrlNormalizer.normalizeOrNull(tag[1])
        }

        fun assemble(relay: NormalizedRelayUrl) = arrayOf(TAG_NAME, relay.url)
    }
}
