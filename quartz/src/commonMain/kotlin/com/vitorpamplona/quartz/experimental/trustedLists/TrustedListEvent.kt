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
package com.vitorpamplona.quartz.experimental.trustedLists

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.trustedLists.tags.ListStatus
import com.vitorpamplona.quartz.experimental.trustedLists.tags.TrustedListMemberTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * Base of the Tapestry Trusted List family: an addressable event that
 * publishes a curated set of members computed under a point of view.
 *
 * Where a NIP-85 Trusted Assertion states a computed result about **one**
 * subject, a Trusted List enumerates **many** members of one type. The family
 * binds to NIP-85's subject-type convention by the `+10` rule -- a list kind
 * is its assertion kind plus ten -- so the last digit denotes the member type
 * on both sides and a reader can tell what a list contains from the kind
 * alone:
 *
 * | List kind | Assertion kind | Member type | Member tag |
 * |---|---|---|---|
 * | 30392 | 30382 | pubkeys | `p` |
 * | 30393 | 30383 | events | `e` |
 * | 30394 | 30384 | addressable events | `a` |
 * | 30395 | 30385 | external identifiers (NIP-73) | `i` |
 *
 * The `d` tag identifies the *list*, not a single subject, so the list
 * replaces in place as it is recomputed.
 */
@Immutable
abstract class TrustedListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    /** The addressable identity of this list. Deterministic per list. */
    fun listId() = dTag()

    fun title() = tags.title()

    fun metric() = tags.metric()

    fun observer() = tags.observer()

    fun sourceTag() = tags.sourceTag()

    fun cutoff() = tags.cutoff()

    fun minRank() = tags.minRank()

    /**
     * The members of this list, in the tag this kind's last digit denotes.
     * Subclasses narrow the return type to their own member tag.
     */
    abstract fun members(): List<TrustedListMemberTag>

    fun memberValues() = members().map { it.memberValue }

    fun memberCount() = members().size

    /**
     * True when the publisher signalled that it could not carry the full
     * membership. A list without the `truncated` tag is authoritative-complete;
     * a truncated one is a cue to reconcile from the raw source events.
     */
    fun isTruncated() = tags.isTruncated()

    /** The true total member count, when the list is truncated. */
    fun truncatedTotal() = tags.truncatedTotal()

    fun status() = tags.status()

    /**
     * Retracted lists are empty-membership replacements published in place of
     * a list that no longer has members or was migrated to another kind.
     */
    fun isRetracted() = status() == ListStatus.RETRACTED

    /** The optional JSON echo of the membership carried in `content`. */
    fun contentEcho() = TrustedListContent.parse(content)
}
