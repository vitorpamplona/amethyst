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
package com.vitorpamplona.quartz.cordn.spec02Envelopes

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstOrNull

/**
 * How one cordn message points at another, in both directions.
 *
 * `spec/02.md` §7 fixes the only part of this the protocol cares about: a
 * reference names the target's **envelope `id`**, never a coordinator cursor,
 * which is a delivery-order primitive that two coordinators will not agree on.
 * Everything below that — which tags, in what shape — follows the NIPs §6 names
 * and, for the kinds no spec covers, the reference client.
 *
 * Parse and build live in one file deliberately. They are two halves of one
 * format, and the failure mode of splitting them is a client that emits tags
 * its own parser rejects.
 */
object CordnMessageReferences {
    // ---- inbound -------------------------------------------------------

    /** A NIP-22 thread position: the root of the thread and the direct parent. */
    data class ThreadReference(
        val rootId: HexKey,
        val rootPubKey: HexKey,
        val rootKind: Int,
        val parentId: HexKey,
        val parentPubKey: HexKey,
        val parentKind: Int,
    )

    /** A NIP-25 reaction, with the emoji the sender chose. */
    data class ReactionReference(
        val targetId: HexKey,
        val targetPubKey: HexKey,
        val targetKind: Int,
        val reaction: String,
    )

    /** An edit. Carries only the target — the new text is the envelope's content. */
    data class EditReference(
        val targetId: HexKey,
    )

    /** A deletion. [targetKind] is checked against the target before it counts. */
    data class DeleteReference(
        val targetId: HexKey,
        val targetKind: Int,
    )

    /** A pin or unpin. */
    data class PinReference(
        val targetId: HexKey,
        val op: PinOp,
    )

    enum class PinOp(
        val wire: String,
    ) {
        ADD("add"),
        REMOVE("remove"),
        ;

        companion object {
            fun of(wire: String?): PinOp? = entries.firstOrNull { it.wire == wire }
        }
    }

    /**
     * Where [tags] places a message in a thread, or null if it places it nowhere.
     *
     * NIP-22 splits root from parent by case: uppercase `E`/`K`/`P` for the
     * thread root, lowercase `e`/`k`/`p` for the message being replied to. All
     * four of `E`/`K`/`e`/`k` must be present and the kinds must be numbers —
     * a half-formed thread tag is treated as no thread rather than guessed at,
     * because guessing reparents a reply under the wrong message.
     *
     * The pubkeys fall back to the `e`-tag's 4th element, which is where NIP-10
     * style tags carry them.
     */
    fun thread(tags: TagArray): ThreadReference? {
        val rootEvent = tags.tag("E") ?: return null
        val rootKind = tags.tagValue("K")?.toIntOrNull() ?: return null
        val parentEvent = tags.tag("e") ?: return null
        val parentKind = tags.tagValue("k")?.toIntOrNull() ?: return null

        val rootId = rootEvent.getOrNull(1)?.ifEmpty { null } ?: return null
        val parentId = parentEvent.getOrNull(1)?.ifEmpty { null } ?: return null
        val rootPubKey = (tags.tagValue("P") ?: rootEvent.getOrNull(3))?.ifEmpty { null } ?: return null
        val parentPubKey = (tags.tagValue("p") ?: parentEvent.getOrNull(3))?.ifEmpty { null } ?: return null

        return ThreadReference(rootId, rootPubKey, rootKind, parentId, parentPubKey, parentKind)
    }

    /** The reaction [kind]/[content]/[tags] express, or null if they do not. */
    fun reaction(
        kind: Int,
        content: String,
        tags: TagArray,
    ): ReactionReference? {
        if (kind != CordnMessageKinds.REACTION) return null

        val targetId = tags.tagValue("e")?.ifEmpty { null } ?: return null
        val targetPubKey = tags.tagValue("p")?.ifEmpty { null } ?: return null
        val targetKind = tags.tagValue("k")?.toIntOrNull() ?: return null
        // An empty reaction is not a reaction. NIP-25 gives `content` meaning
        // here, and a blank one would render as an invisible chip.
        val reaction = content.trim().ifEmpty { null } ?: return null

        return ReactionReference(targetId, targetPubKey, targetKind, reaction)
    }

    /**
     * The edit [kind]/[content]/[tags] express.
     *
     * Blank content is refused: an edit to nothing is a deletion, and those are
     * a different kind with a different authorization rule.
     */
    fun edit(
        kind: Int,
        content: String,
        tags: TagArray,
    ): EditReference? {
        if (kind != CordnMessageKinds.EDIT || content.isBlank()) return null
        val targetId = tags.tagValue("e")?.ifEmpty { null } ?: return null
        return EditReference(targetId)
    }

    /** The deletion [kind]/[tags] express. */
    fun delete(
        kind: Int,
        tags: TagArray,
    ): DeleteReference? {
        if (kind != CordnMessageKinds.DELETION) return null
        val targetId = tags.tagValue("e")?.ifEmpty { null } ?: return null
        val targetKind = tags.tagValue("k")?.toIntOrNull() ?: return null
        return DeleteReference(targetId, targetKind)
    }

    /** The pin or unpin [kind]/[tags] express. An unknown `op` is neither. */
    fun pin(
        kind: Int,
        tags: TagArray,
    ): PinReference? {
        if (kind != CordnMessageKinds.PIN) return null
        val targetId = tags.tagValue("e")?.ifEmpty { null } ?: return null
        val op = PinOp.of(tags.tagValue("op")) ?: return null
        return PinReference(targetId, op)
    }

    // ---- outbound ------------------------------------------------------

    /** The fields of a message being replied to, reacted to, edited or deleted. */
    data class Target(
        val id: HexKey,
        val pubKey: HexKey,
        val kind: Int,
        /** The target's own tags, read to find the thread root. Empty is fine. */
        val tags: TagArray = emptyArray(),
    )

    /** What one outbound message will be, once its kind and tags are decided. */
    data class Outbound(
        val kind: Int,
        val content: String,
        val tags: TagArray,
    )

    /**
     * Resolves a send into its kind, content and tags.
     *
     * One function rather than a kind switch beside a tag switch, because the
     * two have to agree and keeping them apart is how they stop agreeing. The
     * ordering below is the precedence: a pin is a pin even if content was
     * typed, and a delete ignores content entirely.
     */
    fun outbound(
        content: String,
        extraTags: TagArray = emptyArray(),
        replyTo: Target? = null,
        reactionTo: Target? = null,
        editTo: Target? = null,
        deleteTo: Target? = null,
        pinTo: Target? = null,
        pinOp: PinOp = PinOp.ADD,
    ): Outbound {
        pinTo?.let {
            return Outbound(CordnMessageKinds.PIN, "", pinTags(it, pinOp))
        }
        reactionTo?.let {
            // Not trimmed: an emoji is content, and trimming a reaction that is
            // deliberately whitespace-adjacent would change what was sent.
            return Outbound(CordnMessageKinds.REACTION, content, reactionTags(it))
        }
        deleteTo?.let {
            return Outbound(CordnMessageKinds.DELETION, "", deleteTags(it))
        }
        editTo?.let {
            return Outbound(CordnMessageKinds.EDIT, content.trim(), editTags(it) + extraTags)
        }
        return Outbound(
            kind = if (replyTo != null) CordnMessageKinds.THREAD_REPLY else CordnMessageKinds.TEXT,
            content = content.trim(),
            tags = (replyTo?.let(::replyTags) ?: emptyArray()) + extraTags,
        )
    }

    /**
     * NIP-22 reply tags.
     *
     * The root is read out of the target's own `E`/`K`/`P` when it has them —
     * replying to a reply keeps the original root — and is the target itself
     * otherwise, which is the first reply in a thread.
     */
    fun replyTags(target: Target): TagArray {
        val rootEvent = target.tags.tag("E")
        val rootId = rootEvent?.getOrNull(1)?.ifEmpty { null } ?: target.id
        val rootPubKey =
            (target.tags.tagValue("P") ?: rootEvent?.getOrNull(3))?.ifEmpty { null } ?: target.pubKey
        val rootKind = target.tags.tagValue("K") ?: target.kind.toString()

        return arrayOf(
            arrayOf("E", rootId, "", rootPubKey),
            arrayOf("K", rootKind),
            arrayOf("P", rootPubKey),
            arrayOf("e", target.id, "", target.pubKey),
            arrayOf("k", target.kind.toString()),
            arrayOf("p", target.pubKey),
        )
    }

    fun reactionTags(target: Target): TagArray =
        arrayOf(
            arrayOf("e", target.id, "", target.pubKey),
            arrayOf("p", target.pubKey),
            arrayOf("k", target.kind.toString()),
        )

    fun editTags(target: Target): TagArray =
        arrayOf(
            arrayOf("e", target.id, "", target.pubKey),
            arrayOf("p", target.pubKey),
            arrayOf("k", target.kind.toString()),
        )

    /** No `p`: a deletion names what is being removed, not who to notify. */
    fun deleteTags(target: Target): TagArray =
        arrayOf(
            arrayOf("e", target.id, "", target.pubKey),
            arrayOf("k", target.kind.toString()),
        )

    fun pinTags(
        target: Target,
        op: PinOp,
    ): TagArray =
        arrayOf(
            arrayOf("e", target.id, "", target.pubKey),
            arrayOf("p", target.pubKey),
            arrayOf("k", target.kind.toString()),
            arrayOf("op", op.wire),
        )

    // ---- plumbing ------------------------------------------------------

    private fun TagArray.tag(name: String): Array<String>? = fastFirstOrNull { it.isNotEmpty() && it[0] == name }

    private fun TagArray.tagValue(name: String): String? = tag(name)?.getOrNull(1)
}
