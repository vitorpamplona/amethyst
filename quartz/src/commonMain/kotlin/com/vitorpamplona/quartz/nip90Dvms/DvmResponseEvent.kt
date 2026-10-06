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
package com.vitorpamplona.quartz.nip90Dvms

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmInputEventHints
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmInputEventIds

/**
 * Base of every NIP-90 job result (kinds 6000-6999) and of job feedback (kind 7000), which
 * share one reference shape the spec makes mandatory:
 * - `e`: the job request, with the relay it was seen on;
 * - `p`: the customer who asked for it;
 * - `i`: the request's inputs echoed back (event/job inputs are event pointers).
 *
 * Results whose `content` is itself a list of references (discovery, search) add those.
 */
@Immutable
abstract class DvmResponseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, kind, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider {
    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(ETag::parseAsHint) + tags.dvmInputEventHints()

    // NIP-90 gives a result / feedback exactly one `e` (the job request) and one `p` (the customer).
    override fun linkedEventIds(): List<HexKey> = listOfNotNull(jobRequestId()) + tags.dvmInputEventIds()

    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys(): List<HexKey> = listOfNotNull(customer())

    /** NIP-90: the id of the job request this result or feedback answers (`e`). */
    fun jobRequestId(): HexKey? = tags.firstNotNullOfOrNull(ETag::parseId)

    /** NIP-90: the customer who requested the job (`p`). */
    fun customer(): HexKey? = tags.firstNotNullOfOrNull(PTag::parseKey)
}
