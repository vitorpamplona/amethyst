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
package com.vitorpamplona.quartz.concord.cord07Voice

import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.tags.ChannelTag
import com.vitorpamplona.quartz.concord.cord03Channels.tags.EpochTag
import com.vitorpamplona.quartz.concord.cord03Channels.tags.MsTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler

/**
 * A parsed voice presence: who is in the call, under which SFU [identity], and on which [broker].
 * [ms] is the CORD-02 §4 ordering basis (`created_at * 1000 + ms`) and [rumorId] the equal-time
 * tiebreak — together they decide which of an author's presences is the latest.
 */
class VoicePresenceInfo(
    val author: HexKey,
    val channelId: HexKey?,
    val epoch: Long?,
    val joined: Boolean,
    val identity: String?,
    val broker: String?,
    val createdAt: Long,
    val ms: Long,
    val rumorId: HexKey,
)

/**
 * The folded view of one Channel's call (CORD-07 §4): [present] holds each author's latest
 * presence when it is a fresh `joined`; [verified] maps an SFU identity to the single author
 * whose fresh presence claims it (contested and unclaimed identities are absent).
 */
class VoicePresenceFold(
    val present: List<VoicePresenceInfo>,
    val verified: Map<String, HexKey>,
)

/**
 * Voice/video presence (CORD-07 §4): ephemeral kind-23313 rumors sealed on the
 * Channel plane (like Chat Plane messages), announcing that a member is in the
 * call under a broker-assigned SFU [VoicePresenceInfo.identity].
 *
 * Per author the **latest** presence wins ([latestPerAuthor]: millisecond basis, lower rumor id
 * on a tie — the reference client's `foldVoicePresence`), so a `left` supersedes an earlier
 * `joined` and a heartbeat under a new identity drops the old claim. A participant renders as a
 * verified member only when **exactly one author's fresh signed presence** claims an identity
 * ([fold]); contested identities render unverified. Presence is heartbeated every
 * [HEARTBEAT_MS] and considered absent after [STALE_MS].
 */
object VoicePresence {
    const val KIND = 23313
    const val CONTENT_JOINED = "joined"
    const val CONTENT_LEFT = "left"
    const val TAG_IDENTITY = "identity"
    const val TAG_BROKER = "broker"

    const val HEARTBEAT_MS = 30_000L
    const val STALE_MS = 90_000L

    /** A "joined" presence bound to the channel/epoch, carrying the SFU [identity] and optional [broker]. */
    fun joined(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        identity: String,
        createdAt: Long,
        broker: String? = null,
        subMs: Int? = MsTag.remainderFor(createdAt),
    ): Event {
        val tags = ArrayList<Array<String>>()
        tags.add(ChannelTag.assemble(channelId))
        tags.add(EpochTag.assemble(epoch))
        tags.add(arrayOf(TAG_IDENTITY, identity))
        if (broker != null) tags.add(arrayOf(TAG_BROKER, broker))
        if (subMs != null) tags.add(MsTag.assemble(subMs))
        return RumorAssembler.assembleRumor(authorPubKey, createdAt, KIND, tags.toTypedArray(), CONTENT_JOINED)
    }

    /** A "left" presence bound to the channel/epoch (identity and broker omitted). */
    fun left(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        createdAt: Long,
        subMs: Int? = MsTag.remainderFor(createdAt),
    ): Event {
        val tags = ArrayList<Array<String>>(3)
        tags.add(ChannelTag.assemble(channelId))
        tags.add(EpochTag.assemble(epoch))
        if (subMs != null) tags.add(MsTag.assemble(subMs))
        return RumorAssembler.assembleRumor(authorPubKey, createdAt, KIND, tags.toTypedArray(), CONTENT_LEFT)
    }

    /**
     * Parses a kind-23313 rumor, or null when malformed: the content is not `joined`/`left`, a
     * `joined` names no identity, or the `ms` tag is malformed/duplicated (dropped, never
     * interpreted — CORD-02 §5). The caller checks the channel/epoch binding.
     */
    fun parse(rumor: Event): VoicePresenceInfo? {
        if (rumor.kind != KIND) return null
        val joined =
            when (rumor.content) {
                CONTENT_JOINED -> true
                CONTENT_LEFT -> false
                else -> return null
            }
        val ms = MsTag.orderingMs(rumor.createdAt, rumor.tags) ?: return null
        val identity = rumor.tags.firstTagValue(TAG_IDENTITY)?.takeIf { it.isNotEmpty() }
        if (joined && identity == null) return null
        return VoicePresenceInfo(
            author = rumor.pubKey,
            channelId = ChannelChat.channelOf(rumor),
            epoch = ChannelChat.epochOf(rumor),
            joined = joined,
            identity = if (joined) identity else null,
            broker = if (joined) rumor.tags.firstTagValue(TAG_BROKER) else null,
            createdAt = rumor.createdAt,
            ms = ms,
            rumorId = rumor.id,
        )
    }

    /** True if [presence] is within [STALE_MS] of [nowMs], on the millisecond basis. */
    fun isFresh(
        presence: VoicePresenceInfo,
        nowMs: Long,
    ): Boolean = nowMs - presence.ms <= STALE_MS

    /** True when [candidate] supersedes [current]: later on the ms basis, or the lower rumor id on a tie. */
    private fun isLater(
        candidate: VoicePresenceInfo,
        current: VoicePresenceInfo,
    ): Boolean = candidate.ms > current.ms || (candidate.ms == current.ms && candidate.rumorId < current.rumorId)

    /** Each author's latest presence (CORD-07 §4: ms basis, lower rumor id on equal ms). */
    fun latestPerAuthor(presences: Collection<VoicePresenceInfo>): Map<HexKey, VoicePresenceInfo> {
        val latest = HashMap<HexKey, VoicePresenceInfo>()
        for (p in presences) {
            val prev = latest[p.author]
            if (prev == null || isLater(p, prev)) latest[p.author] = p
        }
        return latest
    }

    /**
     * Folds every received presence of one call: per author the latest wins, then a `joined`
     * older than [STALE_MS] counts as absent, then identities claimed by exactly one of the
     * remaining authors verify. [VoicePresenceFold.present] is ordered by time, then author.
     */
    fun fold(
        presences: Collection<VoicePresenceInfo>,
        nowMs: Long,
    ): VoicePresenceFold {
        val present =
            latestPerAuthor(presences)
                .values
                .filter { it.joined && it.identity != null && isFresh(it, nowMs) }
                .sortedWith(compareBy<VoicePresenceInfo> { it.ms }.thenBy { it.author })
        return VoicePresenceFold(present, verifiedParticipants(present))
    }

    /**
     * Maps each SFU identity to its single verified author across [presences], which must
     * already be the per-author latest, fresh presences ([fold] does both). An identity claimed
     * by more than one author is omitted (contested identities render unverified).
     */
    fun verifiedParticipants(presences: List<VoicePresenceInfo>): Map<String, HexKey> {
        val claimants = HashMap<String, MutableSet<HexKey>>()
        for (p in presences) {
            if (!p.joined) continue
            val id = p.identity ?: continue
            claimants.getOrPut(id) { HashSet() }.add(p.author)
        }
        val out = HashMap<String, HexKey>()
        for ((id, authors) in claimants) {
            if (authors.size == 1) out[id] = authors.first()
        }
        return out
    }
}
