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
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * Nostr CI `q` tag naming a Workflow Progress address, `["q", "39842:<coordinator>:<run-id>", "<relay-url>"]`.
 *
 * A Job Result uses it to say which run it belongs to: the address carries both the coordinator
 * that requested the job and the workflow run id. Only a kind 39842 address with a non-empty run
 * id parses.
 */
class WorkflowRunQuoteTag {
    companion object {
        const val TAG_NAME = "q"
        const val WORKFLOW_PROGRESS_KIND = 39842

        fun isTag(tag: Array<String>) = parseAddress(tag) != null

        fun parseAddress(tag: Array<String>): Address? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length != 64) { return null }
            val address = AddressSerializer.parse(tag[1]) ?: return null
            ensure(address.kind == WORKFLOW_PROGRESS_KIND && address.dTag.isNotEmpty()) { return null }
            return address
        }

        fun parse(tag: Array<String>): ATag? {
            val address = parseAddress(tag) ?: return null
            return ATag(address, tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeHintOrNull(it) })
        }

        fun parseAddressId(tag: Array<String>): String? = parseAddress(tag)?.toValue()

        fun parseAsHint(tag: Array<String>): AddressHint? {
            val run = parse(tag) ?: return null
            val relay = run.relay ?: return null
            return AddressHint(run.toTag(), relay)
        }

        fun assemble(
            address: Address,
            relay: NormalizedRelayUrl?,
        ) = arrayOfNotNull(TAG_NAME, address.toValue(), relay?.url)
    }
}
