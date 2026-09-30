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
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.TitleTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.TypeTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz NIP-AR artifact revision (`kind:45010`): one complete snapshot of an editable,
 * channel-homed record (a task, a project card, …). User-signed and **regular**, not
 * addressable — identity is `(community, d)` and the relay advances a per-identity head
 * atomically, so every non-create revision must name the current head in `prev`
 * (compare-and-swap; timestamps never pick a winner, see [ArtifactHeadResolver]).
 *
 * Envelope tags, each exactly once with two elements: `ar`=`1`, `d` (lowercase UUID), `h`
 * (home channel UUID), `type` (namespaced, immutable), `title` (absent on delete), `op`,
 * optional `root` (conversation anchor), and `prev` (required iff `op != create`). Any other
 * tag is a client-defined, relay-matchable annotation ([clientTags]); `content` is opaque
 * and empty on delete. At most 256 tags.
 *
 * Ground truth: `validate` in Buzz's `buzz-core/src/artifact.rs` (mirrored by
 * [ArtifactValidator]), `accept_artifact` in `buzz-db/src/store/artifact.rs`, and
 * `docs/nips/NIP-AR.md`. Buzz ships no SDK builder for this kind yet; [build] enforces the
 * same envelope the relay validates.
 *
 * Reads: a plain REQ `{"kinds":[45010,45011],"#h":[channel]}` replays a channel's revisions
 * and removals. Current-state and history views (and any multi-letter tag predicate such as
 * `#project`) go through the relay's explicit artifact query (`{"artifact":"current"|"history",
 * …}` on the NIP-98 HTTP `/query`); the relay rejects a REQ that mixes artifact kinds with
 * multi-letter tag filters rather than silently dropping the predicate.
 */
@Immutable
class ArtifactEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The artifact's stable UUID — the `d` tag. */
    fun artifactId() = tags.artifactId()

    /** The home channel UUID — the `h` tag. */
    fun home() = tags.artifactHome()

    /** The namespaced content type — the `type` tag. */
    fun type() = tags.artifactType()

    /** The display title — the `title` tag; null on a delete. */
    fun title() = tags.artifactTitle()

    /** The lifecycle operation — the `op` tag. */
    fun op() = tags.artifactOp()

    /** The conversation anchor — the `root` tag. */
    fun root() = tags.artifactRoot()

    /** The replaced revision — the `prev` tag; null on a create. */
    fun prev() = tags.artifactPrev()

    /** True for a soft-delete revision. */
    fun isDelete() = op() == ArtifactOp.DELETE

    /** The client-defined annotation tags an editor must carry forward. */
    fun clientTags() = tags.artifactClientTags()

    /** Runs the relay's envelope validation over this revision. */
    fun validate(): ArtifactValidation = ArtifactValidator.validate(tags, content)

    /** The validated envelope, or null when the relay would reject this revision. */
    fun envelopeOrNull(): ArtifactEnvelope? = (validate() as? ArtifactValidation.Valid)?.envelope

    fun isWellFormed() = validate() is ArtifactValidation.Valid

    companion object {
        const val KIND = 45010

        /**
         * Builds a revision and checks it against [ArtifactValidator] — including any client
         * tags the [initializer] adds — so a template that the relay would reject never
         * leaves this function.
         *
         * [title] must be null exactly when [op] is [ArtifactOp.DELETE] (and [content] then
         * empty); [prev] must be null exactly when [op] is [ArtifactOp.CREATE].
         */
        fun build(
            artifactId: String,
            channelId: String,
            type: String,
            op: ArtifactOp,
            title: String?,
            content: String,
            prev: HexKey? = null,
            root: HexKey? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ArtifactEvent>.() -> Unit = {},
        ): EventTemplate<ArtifactEvent> {
            require(ArtifactIds.isCanonicalUuid(artifactId)) { "artifact id must be a lowercase, non-nil UUID" }
            require(ArtifactIds.isCanonicalUuid(channelId)) { "channel id must be a lowercase, non-nil UUID" }
            require(TypeTag.isValid(type)) { "invalid namespaced artifact type: $type" }
            if (op == ArtifactOp.DELETE) {
                require(title == null) { "a delete must omit the title" }
                require(content.isEmpty()) { "a delete must have empty content" }
            } else {
                require(title != null && TitleTag.isValid(title)) { "title must be nonblank and at most ${TitleTag.MAX_BYTES} UTF-8 bytes" }
            }
            require((op == ArtifactOp.CREATE) == (prev == null)) { "prev is required exactly on non-create revisions" }
            require(prev == null || ArtifactIds.isEventId(prev)) { "prev must be a 64-char lowercase hex event id" }
            require(root == null || ArtifactIds.isEventId(root)) { "root must be a 64-char lowercase hex event id" }

            val template =
                eventTemplate<ArtifactEvent>(KIND, content, createdAt) {
                    artifactVersion()
                    artifactId(artifactId)
                    artifactHome(channelId)
                    artifactType(type)
                    title?.let { artifactTitle(it) }
                    artifactOp(op)
                    root?.let { artifactRoot(it) }
                    prev?.let { artifactPrev(it) }
                    initializer()
                }

            val result = ArtifactValidator.validate(template.tags, template.content)
            require(result is ArtifactValidation.Valid) { "invalid artifact envelope: ${(result as ArtifactValidation.Invalid).reason}" }
            return template
        }

        /** Creates a new artifact identity. Pass client tags (e.g. `assignee`) via [initializer]. */
        fun create(
            artifactId: String,
            channelId: String,
            type: String,
            title: String,
            content: String,
            root: HexKey? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ArtifactEvent>.() -> Unit = {},
        ) = build(artifactId, channelId, type, ArtifactOp.CREATE, title, content, null, root, createdAt, initializer)

        /**
         * An `update` on top of [current] (which must be the head the relay holds): keeps its
         * `d`, `h`, `type` and `root`, names it in `prev`, and carries its client tags forward
         * unless [keepClientTags] is false — NIP-AR editors must preserve annotations they do
         * not understand. The [initializer] runs after the carry-over, so it can replace them.
         */
        fun update(
            current: ArtifactEvent,
            title: String,
            content: String,
            keepClientTags: Boolean = true,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ArtifactEvent>.() -> Unit = {},
        ): EventTemplate<ArtifactEvent> {
            val envelope = requireNotNull(current.envelopeOrNull()) { "current revision has an invalid envelope" }
            return build(envelope.id, envelope.home, envelope.type, ArtifactOp.UPDATE, title, content, current.id, envelope.root, createdAt) {
                if (keepClientTags) current.clientTags().forEach { add(it) }
                initializer()
            }
        }

        /**
         * The soft delete of [current]: same `d`, `h`, `type` and `root` (the relay rejects a
         * delete that changes the anchor), empty content, no title and no client tags.
         */
        fun delete(
            current: ArtifactEvent,
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<ArtifactEvent> {
            val envelope = requireNotNull(current.envelopeOrNull()) { "current revision has an invalid envelope" }
            return build(envelope.id, envelope.home, envelope.type, ArtifactOp.DELETE, null, "", current.id, envelope.root, createdAt)
        }
    }
}
