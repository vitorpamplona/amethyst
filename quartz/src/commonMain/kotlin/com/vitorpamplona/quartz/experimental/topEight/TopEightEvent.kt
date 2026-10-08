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
package com.vitorpamplona.quartz.experimental.topEight

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventUpdate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 18678, Top 8 (Ditto's `NIP.md`; the number keypad-spells 1-TOP8): the author's eight
 * favorite people **in rank order**, MySpace style. Shaped like a NIP-51 people list (`p` tags),
 * replaceable, one per user — but unlike a follow list, order is the point: the first `p` is
 * number one, and reordering is a social act that clients surface in feeds as a card, which is
 * why NIP-22 comments on it are allowed ([RootScope]).
 *
 * Read through [ranked]: duplicates dropped, cut at eight. Mutations MUST read-modify-write the
 * freshest event and keep unknown tags; use [rerank] for that. The spec allows NIP-51 private
 * items in `content`, but Ditto publishes public entries only and ignores what it cannot decrypt;
 * so does this class (`content` is never read). Clients SHOULD hide an event with no ranked
 * people ([isEmpty]).
 *
 * Not searchable: a list of keys.
 */
@Immutable
class TopEightEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    PubKeyHintProvider {
    override fun pubKeyHints(): List<PubKeyHint> = ranked().mapNotNull { person -> person.relayHint?.let { PubKeyHint(person.pubKey, it) } }

    /** `FAVORITE`: each ranked person, rank 1 first (props: `rank`, 1-based position in this list). */
    override fun linkedPubKeys(): List<HexKey> = rankedKeys()

    /** The ranked people, rank 1 first, at most eight. */
    fun ranked() = tags.topEight()

    fun rankedKeys() = ranked().map { it.pubKey }

    /** The 1-based rank of [pubKey], or null when they are not in this Top 8. */
    fun rankOf(pubKey: HexKey): Int? = rankedKeys().indexOf(pubKey).takeIf { it >= 0 }?.plus(1)

    fun isEmpty() = ranked().isEmpty()

    companion object {
        const val KIND = 18678
        const val MAX_ENTRIES = 8

        fun build(
            people: List<PTag>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TopEightEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            topEight(people)
            initializer()
        }

        /**
         * Re-ranks [current] to [people], keeping its other tags and its `content` as the spec's
         * read-modify-write rule requires.
         */
        fun rerank(
            current: TopEightEvent,
            people: List<PTag>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TopEightEvent>.() -> Unit = {},
        ) = eventUpdate<TopEightEvent>(current, createdAt) {
            topEight(people)
            initializer()
        }
    }
}
