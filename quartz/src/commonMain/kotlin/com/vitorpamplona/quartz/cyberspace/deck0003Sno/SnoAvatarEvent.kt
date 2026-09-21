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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag

/**
 * `CYBERSPACE_V2.md` §8.10 — an avatar, kind 11333: the shape an identity is
 * drawn as, carrying an SNO payload in its content or empty content for the
 * default avatar.
 *
 * Replaceable, so relays keep the newest per `(pubkey, kind)` and an identity
 * has exactly one avatar. This kind **was** 33331 with a `d` fixed at
 * `"avatar"` before the spec moved it here, and 33331 was then handed to the
 * standalone objects of DECK-0003 §3.1 — so an old 33331 whose `d` is literally
 * `avatar` is not a surprise, merely stale.
 *
 * An avatar is the one thing in cyberspace that lands on other people's screens
 * whether they asked for it or not, so its size and detail are paid for in
 * proof of work on the event that publishes it, and **a client MUST NOT draw an
 * avatar that is not paid, or that carries content it cannot read** — it draws
 * its default for that identity instead. [isPaid] is that gate.
 */
@Immutable
class SnoAvatarEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** True when this identity asked for the default avatar, which owes no work. */
    fun isDefaultAvatar(): Boolean = content.isBlank()

    fun sno(): SnoResult = SnoParser.parse(content)

    /** The `name` tag: the shape's name for humans. */
    fun nameTag(): String? = tags.firstOrNull { it.size > 1 && it[0] == "name" }?.get(1)

    /**
     * Whether this avatar has paid for the room it takes up (§8.10).
     *
     * Both conditions are required: the committed target must cover the work the
     * payload owes, **and** the id must actually carry that many leading zero
     * bits. Committing the target before mining is what stops a lucky id being
     * claimed against a lower bar than it was mined for (NIP-13).
     *
     * Note that `Event.pow()` cannot stand in for this. It is
     * `PoWRankEvaluator.compute(id, commitedPoW)`, which returns
     * `min(actualRank, committed)` — so comparing it against the required work
     * passes an event whose id falls short of its own commitment: work 16,
     * committed 30 and an id carrying 20 gives 20, which clears 16 while
     * failing the second condition outright.
     */
    fun isPaid(): Boolean {
        if (isDefaultAvatar()) return true
        val payload = sno().payloadOrNull() ?: return false
        val committed = tags.firstNotNullOfOrNull { PoWTag.parseCommitment(it) } ?: return false
        if (committed < SnoAvatarWork.required(payload)) return false
        return PoWRankEvaluator.calculatePowRankOf(id) >= committed
    }

    companion object {
        const val KIND = 11333
    }
}
