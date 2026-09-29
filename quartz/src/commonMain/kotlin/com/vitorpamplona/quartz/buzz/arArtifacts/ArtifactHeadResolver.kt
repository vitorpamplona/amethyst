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
package com.vitorpamplona.quartz.buzz.arArtifacts

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.ArtifactOp
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** What a set of revisions says about one artifact's current state. See [ArtifactHeadResolver]. */
@Immutable
sealed interface ArtifactHead {
    /** No well-formed revision of the artifact was supplied. */
    data object Unknown : ArtifactHead

    /**
     * [revision] is the current head. [chainComplete] is true when its `prev` chain reaches the
     * `create` with no missing revision; false when the head was found across a gap (a redacted,
     * expired or unreadable revision), which is still unambiguous for a linear history.
     */
    data class Current(
        val revision: ArtifactEvent,
        val chainComplete: Boolean,
    ) : ArtifactHead {
        /** A soft-deleted artifact: hidden from active views until a `restore`. */
        val isDeleted get() = revision.op() == ArtifactOp.DELETE
    }

    /**
     * The newest visible revision, [lastRevision], was replaced by a move into a channel this
     * reader cannot see — the relay-signed [removal] names it as `prev`.
     */
    data class MovedAway(
        val lastRevision: ArtifactEvent,
        val removal: ArtifactRemovalEvent,
    ) : ArtifactHead

    /**
     * The revisions do not determine a single head: a fork (impossible on a Buzz relay, which
     * serializes every identity, so it signals revisions from elsewhere), several gaps, or a
     * `prev` cycle ([candidates] is then empty). Ask the relay's current-state query
     * (`{"artifact":"current","#d":[id]}`) instead of guessing.
     */
    data class Ambiguous(
        val candidates: List<ArtifactEvent>,
    ) : ArtifactHead
}

/**
 * Resolves an artifact's current head from its revisions the way a Buzz relay defines it: the
 * latest *accepted* revision, where acceptance is a compare-and-swap on `prev` — every
 * revision after the `create` names the head it replaced, so the accepted revisions form one
 * linear chain. **Timestamps never choose the winner** (NIP-AR, "Identity and edits"), so
 * neither does this resolver.
 *
 * The head is the one revision no other revision names as `prev` (a *tip*). Readers can see
 * gaps — a NIP-29 `9005` redaction withholds a revision but keeps it accepted, retention
 * expires old ones, and a moved artifact's earlier revisions stay under the source channel's
 * access rules — which leaves more than one tip. Because the accepted history is linear, the
 * segment that starts at the `create` is always the oldest, so when some other segment starts
 * after a gap the create-rooted tip cannot be the head and is discarded. Anything still
 * unresolved is reported as [ArtifactHead.Ambiguous] rather than picked by `created_at`.
 *
 * Ground truth: `accept_artifact` in Buzz's `buzz-db/src/store/artifact.rs` (the CAS),
 * `removal_marker` in the same file, and `docs/nips/NIP-AR.md`.
 */
object ArtifactHeadResolver {
    fun resolve(
        artifactId: String,
        revisions: Collection<ArtifactEvent>,
        removals: Collection<ArtifactRemovalEvent> = emptyList(),
    ): ArtifactHead {
        val byId = LinkedHashMap<HexKey, ArtifactEvent>()
        val prevOf = HashMap<HexKey, HexKey?>()
        val opOf = HashMap<HexKey, ArtifactOp>()
        for (revision in revisions) {
            if (revision.id in byId) continue
            val envelope = revision.envelopeOrNull() ?: continue
            if (envelope.id != artifactId) continue
            byId[revision.id] = revision
            prevOf[revision.id] = envelope.prev
            opOf[revision.id] = envelope.op
        }
        if (byId.isEmpty()) return ArtifactHead.Unknown

        val replaced = HashSet<HexKey>()
        prevOf.values.forEach { if (it != null) replaced.add(it) }

        val tips = byId.values.filter { it.id !in replaced }
        if (tips.isEmpty()) return ArtifactHead.Ambiguous(emptyList())

        // Walk each tip back to the oldest revision reachable from it.
        val reachesCreate = tips.associateWith { tip -> opOf[segmentStart(tip.id, prevOf)] == ArtifactOp.CREATE }

        val head: ArtifactEvent
        val complete: Boolean
        if (tips.size == 1) {
            head = tips[0]
            complete = reachesCreate.getValue(head)
        } else {
            val afterGap = tips.filter { !reachesCreate.getValue(it) }
            if (afterGap.size != 1) return ArtifactHead.Ambiguous(tips)
            head = afterGap[0]
            complete = false
        }

        val removal = removals.firstOrNull { it.artifactId() == artifactId && it.replacedRevision() == head.id }
        if (removal != null) return ArtifactHead.MovedAway(head, removal)

        return ArtifactHead.Current(head, complete)
    }

    /**
     * The known revisions from [head] back along `prev`, newest first. Stops at the `create`,
     * at the first revision not in [revisions], or on a cycle.
     */
    fun history(
        head: ArtifactEvent,
        revisions: Collection<ArtifactEvent>,
    ): List<ArtifactEvent> {
        val byId = revisions.associateBy { it.id }
        val result = mutableListOf<ArtifactEvent>()
        val seen = HashSet<HexKey>()
        var current: ArtifactEvent? = head
        while (current != null && seen.add(current.id)) {
            result.add(current)
            current = current.prev()?.let { byId[it] }
        }
        return result
    }

    private fun segmentStart(
        tip: HexKey,
        prevOf: Map<HexKey, HexKey?>,
    ): HexKey {
        val seen = HashSet<HexKey>()
        var current = tip
        while (seen.add(current)) {
            val prev = prevOf[current] ?: return current
            if (prev !in prevOf) return current
            current = prev
        }
        return current
    }
}
