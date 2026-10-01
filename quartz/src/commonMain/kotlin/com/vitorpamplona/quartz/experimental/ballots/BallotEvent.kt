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
package com.vitorpamplona.quartz.experimental.ballots

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent

/**
 * A ballot cast in an auditable-voting app (kind 38000).
 *
 * App-specific and **not** defined by any NIP: kind 38000 is NIP-87's mint recommendation, and
 * the voting app reuses the number. [com.vitorpamplona.quartz.utils.EventFactory] tells them
 * apart by the `election` tag ([isBallot]). Amethyst only reads these — it never publishes them.
 *
 * The schema is derived from events seen in the wild: an `election` tag naming the election, an
 * optional proof hash (`proof-hash` or `proof_hash` tag, or `proof_hash` in the content), and the
 * choices as JSON `content` in one of three shapes (see [BallotContent]). The content is parsed at
 * most once per event, lazily, and a malformed blob just reads as a ballot with no answers.
 */
@Immutable
class BallotEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    private val parsedContent by lazy { BallotContent.parse(content) }

    override fun indexableContent() =
        buildList {
            election()?.let { add(it) }
            answers().forEach { add(it.answer) }
        }.joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(election())) return
        for (answer in answers()) {
            if (!visitor.visit(answer.answer)) return
        }
    }

    fun election() = tags.election()

    /** The ballot's answers in the order they were cast; empty when the content says nothing readable. */
    fun answers(): List<BallotAnswer> = parsedContent?.answers ?: emptyList()

    fun proofHash() = tags.proofHash() ?: parsedContent?.proofHash

    companion object {
        const val KIND = 38000

        /** The question a lone `vote_choice` answers; the UI shows its own label for it. */
        const val VOTE_QUESTION = "Vote"

        /** True when a kind-38000 event is a ballot: it names the `election` it is cast in. */
        fun isBallot(tags: TagArray): Boolean = tags.fastAny { it.size > 1 && it[0] == "election" && it[1].isNotBlank() }
    }
}
