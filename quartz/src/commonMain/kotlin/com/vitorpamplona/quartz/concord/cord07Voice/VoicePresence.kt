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
import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler

/** A parsed voice presence: who is in the call, under which SFU [identity], and on which [broker]. */
class VoicePresenceInfo(
    val author: HexKey,
    val channelId: HexKey?,
    val epoch: Long?,
    val joined: Boolean,
    val identity: String?,
    val broker: String?,
    val createdAt: Long,
)

/**
 * Voice/video presence (CORD-07 §4): ephemeral kind-23313 rumors sealed on the
 * Channel plane (like Chat Plane messages), announcing that a member is in the
 * call under a broker-assigned SFU [VoicePresenceInfo.identity].
 *
 * A participant renders as a verified member only when **exactly one author's
 * fresh signed presence** claims an identity ([verifiedParticipants]); contested
 * identities render unverified. Presence is heartbeated every
 * [HEARTBEAT_MS] and considered absent after [STALE_MS].
 */
object VoicePresence {
    const val KIND = ConcordKinds.VOICE_PRESENCE
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
        subMs: Int? = null,
    ): Event {
        val tags = ArrayList<Array<String>>()
        tags.add(ChannelTag.assemble(channelId))
        tags.add(EpochTag.assemble(epoch))
        tags.add(arrayOf(TAG_IDENTITY, identity))
        if (broker != null) tags.add(arrayOf(TAG_BROKER, broker))
        if (subMs != null) tags.add(arrayOf("ms", subMs.toString()))
        return RumorAssembler.assembleRumor(authorPubKey, createdAt, KIND, tags.toTypedArray(), CONTENT_JOINED)
    }

    /** A "left" presence bound to the channel/epoch. */
    fun left(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        createdAt: Long,
    ): Event = RumorAssembler.assembleRumor(authorPubKey, createdAt, KIND, arrayOf(ChannelTag.assemble(channelId), EpochTag.assemble(epoch)), CONTENT_LEFT)

    fun parse(rumor: Event): VoicePresenceInfo? {
        if (rumor.kind != KIND) return null
        return VoicePresenceInfo(
            author = rumor.pubKey,
            channelId = ChannelChat.channelOf(rumor),
            epoch = ChannelChat.epochOf(rumor),
            joined = rumor.content == CONTENT_JOINED,
            identity = rumor.tags.firstTagValue(TAG_IDENTITY),
            broker = rumor.tags.firstTagValue(TAG_BROKER),
            createdAt = rumor.createdAt,
        )
    }

    /** True if [presence] is within [STALE_MS] of [nowMs] (createdAt is unix seconds). */
    fun isFresh(
        presence: VoicePresenceInfo,
        nowMs: Long,
    ): Boolean = nowMs - presence.createdAt * 1000 <= STALE_MS

    /**
     * Maps each SFU identity to its single verified author across the given fresh
     * [presences]. An identity claimed by zero or more-than-one author is omitted
     * (contested identities render unverified).
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
