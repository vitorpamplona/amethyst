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
package com.vitorpamplona.quartz.nip54Wiki

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A record that a wiki merge request was accepted (kind 819).
 *
 * **Not in NIP-54.** The spec stops at the merge request and says the destination author answers
 * it with a NIP-25 `+`/`-` reaction; kind 819 is an extension by the client publishing these
 * (`silberengel/jumble`), which records the acceptance *and* the version it produced — something
 * a bare reaction cannot carry.
 *
 * - `e` marked `result` — the merged article version.
 * - `e` marked `request` — the [WikiMergeRequestEvent] being answered.
 * - `p` — the requester.
 */
@Immutable
class WikiMergeAcceptanceEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider {
    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    /** The merged article version this acceptance produced. */
    fun result(): HexKey? = markedEvent(RESULT_MARKER)

    /** The kind-818 request being accepted. */
    fun request(): HexKey? = markedEvent(REQUEST_MARKER)

    /** The author of the request being accepted. */
    fun requester(): HexKey? = tags.firstNotNullOfOrNull(PTag::parseKey)

    /** An acceptance that names no request is not attributable to anything. */
    fun hasRequest() = request() != null

    private fun markedEvent(marker: String): HexKey? =
        tags.firstNotNullOfOrNull { tag ->
            if (tag.size > WikiMergeRequestEvent.MARKER_SLOT &&
                tag[0] == ETag.TAG_NAME &&
                tag[1].isNotEmpty() &&
                tag[WikiMergeRequestEvent.MARKER_SLOT] == marker
            ) {
                tag[1]
            } else {
                null
            }
        }

    companion object {
        const val KIND = 819
        const val RESULT_MARKER = "result"
        const val REQUEST_MARKER = "request"

        fun build(
            requestId: HexKey,
            requester: HexKey,
            resultVersionId: HexKey,
            relay: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WikiMergeAcceptanceEvent>.() -> Unit = {},
        ): EventTemplate<WikiMergeAcceptanceEvent> =
            eventTemplate(KIND, "", createdAt) {
                add(arrayOf(ETag.TAG_NAME, resultVersionId, relay?.url ?: "", RESULT_MARKER))
                add(arrayOf(ETag.TAG_NAME, requestId, relay?.url ?: "", REQUEST_MARKER))
                add(PTag.assemble(requester, relay))

                initializer()
            }
    }
}
