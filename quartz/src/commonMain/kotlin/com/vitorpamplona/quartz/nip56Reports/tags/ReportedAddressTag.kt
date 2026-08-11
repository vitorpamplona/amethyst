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
package com.vitorpamplona.quartz.nip56Reports.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
class ReportedAddressTag(
    val address: Address,
    val relay: NormalizedRelayUrl? = null,
    override val type: ReportType? = null,
) : BaseReportTag {
    fun toTagArray() = assemble(address, relay, type)

    companion object {
        const val TAG_NAME = "a"

        fun parse(
            tag: Array<String>,
            defaultReportType: ReportType? = null,
        ): ReportedAddressTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

            val address = Address.parse(tag[1])

            ensure(address != null) { return null }

            return ReportedAddressTag(
                address,
                ReportTagLayout.relayHint(tag),
                ReportTagLayout.reportType(tag, defaultReportType),
            )
        }

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

            val address = Address.parse(tag[1])
            ensure(address != null) { return null }

            val hint = ReportTagLayout.relayHint(tag)
            ensure(hint != null) { return null }

            return AddressHint(address.toValue(), hint)
        }

        /** See [ReportedAuthorTag.assemble] for why the layout is conditional. */
        fun assemble(
            address: Address,
            relay: NormalizedRelayUrl?,
            type: ReportType?,
        ) = if (relay != null) {
            arrayOfNotNull(TAG_NAME, address.toValue(), relay.url, type?.code)
        } else {
            arrayOfNotNull(TAG_NAME, address.toValue(), type?.code)
        }

        fun assemble(
            address: Address,
            type: ReportType? = null,
        ) = assemble(address, null, type)
    }
}
