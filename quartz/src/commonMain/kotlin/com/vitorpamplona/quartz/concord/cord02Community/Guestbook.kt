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

import com.vitorpamplona.quartz.concord.cord03Channels.tags.MsTag
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VacTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler

/**
 * A membership motion on the Guestbook Plane: the self-signed Join and Leave verbs of kind 3306,
 * and an authorized [KICK] (kind 3309), which is never a 3306 verb.
 */
enum class GuestbookAction(
    val wire: String,
) {
    JOIN("join"),
    LEAVE("leave"),

    /** An authorized Kick (kind 3309): the target is departed, and asked to leave (CORD-04 §6). */
    KICK("kick"),
    ;

    companion object {
        /** The kind-3306 verb [wire] names — only `join` and `leave` ride that kind. */
        fun of(wire: String) =
            when (wire) {
                JOIN.wire -> JOIN
                LEAVE.wire -> LEAVE
                else -> null
            }
    }
}

/**
 * A parsed Guestbook motion: whose state it sets ([member] — the target, for a Kick), what
 * ([action]), who signed it ([author] — the member themself for a Join/Leave, the kicker for a
 * Kick), and invite attribution (Joins only). [ms] is the CORD-02 §4 ordering basis
 * (`created_at * 1000 + ms`) and [rumorId] the equal-time tiebreak; [citation] is a Kick's `vac`.
 */
class GuestbookEntry(
    val member: HexKey,
    val action: GuestbookAction,
    val createdAt: Long,
    val inviteCreator: HexKey?,
    val inviteLabel: String?,
    val author: HexKey = member,
    val ms: Long = createdAt * 1000,
    val rumorId: HexKey = "",
    val citation: AuthorityCitation? = null,
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

    /** An entry dated further than this ahead of the receiver's clock is dropped outright (CORD-02 §5). */
    const val MAX_FUTURE_MS = 60 * 60 * 1000L

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
     * An authorized Kick (kind 3309) directing [target] to leave, citing the Grant the actor acts
     * under ([citation], the `vac` — null only for the owner, who cites nothing). Tags in the
     * examples' order: `ms`, `p`, `vac`. A Kick is only honored by clients when its signer holds
     * [ConcordPermissions.KICK] and strictly outranks the target ([canKick]) — a Kick from a
     * non-KICK holder is dropped (CORD-04 §6).
     */
    fun kick(
        actorPubKey: HexKey,
        target: HexKey,
        createdAt: Long,
        subMs: Int = MsTag.remainderFor(createdAt),
        citation: AuthorityCitation? = null,
    ): Event {
        val tags = ArrayList<Array<String>>(3)
        tags.add(MsTag.assemble(subMs))
        tags.add(arrayOf(TAG_P, target))
        if (citation != null) tags.add(VacTag.assemble(citation))
        return RumorAssembler.assembleRumor(actorPubKey, createdAt, KIND_KICK, tags.toTypedArray(), "")
    }

    /**
     * Parses a Guestbook Join/Leave (kind 3306) or Kick (kind 3309), or null if it is neither or is
     * malformed: an unknown verb, a Kick naming no valid 64-hex target, or a malformed/duplicated
     * `ms` tag (dropped, never interpreted — CORD-02 §5).
     */
    fun parse(rumor: Event): GuestbookEntry? {
        val ms = MsTag.orderingMs(rumor.createdAt, rumor.tags)
        when (rumor.kind) {
            KIND_JOIN_LEAVE -> {
                val action = GuestbookAction.of(rumor.content) ?: return null
                if (ms == null) return null
                val invite = if (action == GuestbookAction.JOIN) rumor.tags.firstOrNull { it.size >= 2 && it[0] == TAG_INVITE } else null
                return GuestbookEntry(
                    member = rumor.pubKey,
                    action = action,
                    createdAt = rumor.createdAt,
                    inviteCreator = invite?.getOrNull(1),
                    inviteLabel = invite?.getOrNull(2),
                    author = rumor.pubKey,
                    ms = ms,
                    rumorId = rumor.id,
                )
            }
            KIND_KICK -> {
                val target = kickTarget(rumor)?.lowercase()?.takeIf { it.isValid() } ?: return null
                if (ms == null) return null
                return GuestbookEntry(
                    member = target,
                    action = GuestbookAction.KICK,
                    createdAt = rumor.createdAt,
                    inviteCreator = null,
                    inviteLabel = null,
                    author = rumor.pubKey,
                    ms = ms,
                    rumorId = rumor.id,
                    citation = rumor.tags.firstOrNull { VacTag.isTag(it) }?.let { VacTag.parse(it) },
                )
            }
            else -> return null
        }
    }

    /** The kick target's pubkey from a kind-3309 rumor, or null. */
    fun kickTarget(rumor: Event): HexKey? = if (rumor.kind == KIND_KICK) rumor.tags.firstTagValue(TAG_P) else null

    /**
     * Whether the Kick [entry] is honored against the folded [authority] (CORD-04 §5/§6): its
     * author holds [ConcordPermissions.KICK], is not banned, and strictly outranks the target (the
     * owner is never a valid target), judged against the **current** roster — and its `vac` is
     * synced (block-until-synced: a citation we cannot yet match parks the Kick, here drops it
     * until a later fold).
     */
    fun canKick(
        authority: AuthorityResolver,
        entry: GuestbookEntry,
    ): Boolean =
        entry.action == GuestbookAction.KICK &&
            authority.canActOn(entry.author, entry.member, ConcordPermissions.KICK) &&
            authority.citationSatisfied(entry.author, entry.citation)

    /** True when [next] supersedes [prev]: later on the ms basis, or the lower rumor id on a tie. */
    private fun supersedes(
        prev: GuestbookEntry?,
        next: GuestbookEntry,
    ): Boolean = prev == null || next.ms > prev.ms || (next.ms == prev.ms && next.rumorId < prev.rumorId)

    /**
     * Coalesces [entries] flat (CORD-02 §5): one final state per npub (lowercase), where their
     * latest Join, Leave or honored Kick wins — later ms, ties to the lower rumor id.
     *  - an entry more than [MAX_FUTURE_MS] ahead of [nowMs] is dropped;
     *  - a [banned] author's entries, Kicks included, are dropped (CORD-04 §4);
     *  - a Kick counts only when [canKick] honors it (a Kick from a non-KICK holder is dropped).
     */
    fun coalesce(
        entries: Collection<GuestbookEntry>,
        nowMs: Long,
        canKick: (GuestbookEntry) -> Boolean,
        banned: Set<HexKey> = emptySet(),
    ): Map<HexKey, GuestbookEntry> {
        val out = HashMap<HexKey, GuestbookEntry>()
        for (entry in entries) {
            if (entry.ms > nowMs + MAX_FUTURE_MS) continue
            if (entry.author.lowercase() in banned) continue
            if (entry.action == GuestbookAction.KICK && !canKick(entry)) continue
            val key = entry.member.lowercase()
            if (supersedes(out[key], entry)) out[key] = entry
        }
        return out
    }

    /** [coalesce] with Kicks judged by [canKick] against [authority], and its banlist applied. */
    fun coalesce(
        entries: Collection<GuestbookEntry>,
        nowMs: Long,
        authority: AuthorityResolver?,
    ): Map<HexKey, GuestbookEntry> =
        coalesce(
            entries,
            nowMs,
            canKick = { authority != null && canKick(authority, it) },
            banned = authority?.bannedMembers().orEmpty(),
        )
}
