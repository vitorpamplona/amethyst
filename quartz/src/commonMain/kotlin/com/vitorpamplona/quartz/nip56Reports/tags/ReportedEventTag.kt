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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
class ReportedEventTag(
    override val eventId: HexKey,
    override val relay: NormalizedRelayUrl? = null,
    override val type: ReportType? = null,
) : BaseReportTag,
    GenericETag {
    /** NIP-56 `e` tags carry no author slot — slot 3 is the report type. */
    override val author: HexKey? = null

    override fun toTagArray() = assemble(eventId, relay, type)

    companion object {
        const val TAG_NAME = "e"

        fun parse(
            tag: Array<String>,
            defaultReportType: ReportType? = null,
        ): ReportedEventTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            return ReportedEventTag(
                tag[1],
                ReportTagLayout.relayHint(tag),
                ReportTagLayout.reportType(tag, defaultReportType),
            )
        }

        fun parseId(tag: Array<String>): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return tag[1]
        }

        fun parseAsHint(tag: Array<String>): EventIdHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val hint = ReportTagLayout.relayHint(tag)

            ensure(hint != null) { return null }

            return EventIdHint(tag[1], hint)
        }

        /** See [ReportedAuthorTag.assemble] for why the layout is conditional. */
        fun assemble(
            eventId: HexKey,
            relay: NormalizedRelayUrl?,
            type: ReportType?,
        ) = if (relay != null) {
            arrayOfNotNull(TAG_NAME, eventId, relay.url, type?.code)
        } else {
            arrayOfNotNull(TAG_NAME, eventId, type?.code)
        }

        fun assemble(
            eventId: HexKey,
            type: ReportType? = null,
        ) = assemble(eventId, null, type)
    }
}
