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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler

/** A self-signed membership motion on the Guestbook Plane. */
enum class GuestbookAction(
    val wire: String,
) {
    JOIN("join"),
    LEAVE("leave"),
    ;

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.wire == wire }
    }
}

/** A parsed Guestbook join/leave: who ([member]), what ([action]), and invite attribution. */
class GuestbookEntry(
    val member: HexKey,
    val action: GuestbookAction,
    val createdAt: Long,
    val inviteCreator: HexKey?,
    val inviteLabel: String?,
)

/**
 * The Guestbook Plane (CORD-02): self-signed Joins and Leaves plus authorized
 * Kicks that track membership motion. It is **off-consensus** — nothing in the
 * Control or Chat planes depends on it — so it is best-effort presence, not
 * authority.
 *
 * Rumor builders here are unsigned; seal + wrap them onto the community's
 * Guestbook plane with
 * [com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope].
 */
object Guestbook {
    /** Guestbook rumor kinds (CORD-02): self-signed join/leave and authorized kick. */
    const val KIND_JOIN_LEAVE = 3306
    const val KIND_KICK = 3309

    const val TAG_MS = "ms"
    const val TAG_INVITE = "invite"
    const val TAG_P = "p"

    /** A self-signed join (kind 3306), optionally attributing the invite used. */
    fun join(
        memberPubKey: HexKey,
        createdAt: Long,
        subMs: Int? = null,
        inviteCreator: HexKey? = null,
        inviteLabel: String? = null,
    ): Event = motion(memberPubKey, GuestbookAction.JOIN, createdAt, subMs, inviteCreator, inviteLabel)

    /** A self-signed leave (kind 3306). */
    fun leave(
        memberPubKey: HexKey,
        createdAt: Long,
        subMs: Int? = null,
    ): Event = motion(memberPubKey, GuestbookAction.LEAVE, createdAt, subMs, null, null)

    private fun motion(
        memberPubKey: HexKey,
        action: GuestbookAction,
        createdAt: Long,
        subMs: Int?,
        inviteCreator: HexKey?,
        inviteLabel: String?,
    ): Event {
        val tags = ArrayList<Array<String>>(2)
        if (subMs != null) tags.add(arrayOf(TAG_MS, subMs.toString()))
        if (inviteCreator != null) {
            tags.add(if (inviteLabel != null) arrayOf(TAG_INVITE, inviteCreator, inviteLabel) else arrayOf(TAG_INVITE, inviteCreator))
        }
        return RumorAssembler.assembleRumor(memberPubKey, createdAt, KIND_JOIN_LEAVE, tags.toTypedArray(), action.wire)
    }

    /**
     * An authorized Kick (kind 3309) directing [target] to leave. A Kick is only
     * honored by clients when its signer holds [com.vitorpamplona.quartz.concord
     * .cord04Roles.ConcordPermissions.KICK] and outranks the target — a Kick from
     * a non-KICK holder is dropped (CORD-04 §The Three Removals).
     */
    fun kick(
        actorPubKey: HexKey,
        target: HexKey,
        createdAt: Long,
    ): Event = RumorAssembler.assembleRumor(actorPubKey, createdAt, KIND_KICK, arrayOf(arrayOf(TAG_P, target)), "")

    /** Parses a Guestbook join/leave rumor, or null if it is not one. */
    fun parse(rumor: Event): GuestbookEntry? {
        if (rumor.kind != KIND_JOIN_LEAVE) return null
        val action = GuestbookAction.of(rumor.content) ?: return null
        val invite = rumor.tags.firstOrNull { it.size >= 2 && it[0] == TAG_INVITE }
        return GuestbookEntry(
            member = rumor.pubKey,
            action = action,
            createdAt = rumor.createdAt,
            inviteCreator = invite?.getOrNull(1),
            inviteLabel = invite?.getOrNull(2),
        )
    }

    /** The kick target's pubkey from a kind-3309 rumor, or null. */
    fun kickTarget(rumor: Event): HexKey? = if (rumor.kind == KIND_KICK) rumor.tags.firstTagValue(TAG_P) else null
}
