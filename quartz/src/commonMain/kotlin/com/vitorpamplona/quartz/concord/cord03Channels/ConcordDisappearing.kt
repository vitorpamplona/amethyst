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
package com.vitorpamplona.quartz.concord.cord03Channels

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Disappearing Messages (CORD-08): one Community-wide timer, `message_expiration` in the metadata
 * entity, after which every Chat-plane message expires.
 *
 * - **Sender (§2):** while the timer is set, every durable Chat rumor carries
 *   `["expiration", created_at + timer]` inside the signed rumor, and its kind-1059 wrap carries the
 *   same tag so NIP-40 relays delete the ciphertext. Deletes (5) and timer notices (1740) never
 *   carry it — an expiring delete would resurrect what it erased, an expiring notice would erase
 *   why history is missing — and ephemeral kinds (typing 23311, voice presence 23313) carry nothing.
 * - **Reader (§3):** only the rumor's own tag counts (the wrap's is relay hygiene). An expired rumor
 *   is refused at ingest, never displayed, and purged from local storage.
 * - **Notice (§4):** kind 1740 with `["timer", "<secs>"]` and the channel binding, shown only when
 *   its author holds MANAGE_METADATA.
 *
 * A timer change is never retroactive: a rumor keeps the expiry it was sent under.
 */
object ConcordDisappearing {
    /** The timer-notice kind, shared with NIP-17 disappearing DMs (§4). */
    const val KIND_TIMER_NOTICE = ChannelChat.KIND_TIMER_NOTICE

    const val TIMER_TAG = "timer"

    private const val KIND_DELETE = 5
    private const val KIND_TYPING = 23311
    private const val KIND_VOICE_PRESENCE = 23313

    private val DECIMAL = Regex("^(0|[1-9][0-9]*)$")

    /** True when a rumor of [kind] must carry the expiration tag while the timer is set (§2). */
    fun expires(kind: Int): Boolean = kind != KIND_DELETE && kind != KIND_TIMER_NOTICE && kind != KIND_TYPING && kind != KIND_VOICE_PRESENCE

    /**
     * The expiration a rumor created at [createdAt] of [kind] must carry under [timerSecs], or null
     * when the timer is off or the kind is exempt.
     */
    fun expirationFor(
        kind: Int,
        createdAt: Long,
        timerSecs: Long?,
    ): Long? {
        if (timerSecs == null || timerSecs < 1 || !expires(kind)) return null
        return createdAt + timerSecs
    }

    /**
     * [tags] with the expiration tag applied: added (or replaced) when [expiration] is set, left as is
     * otherwise. The rumor's copy is what readers enforce, so it must be in the signed tags.
     */
    fun withExpiration(
        tags: TagArray,
        expiration: Long?,
    ): TagArray {
        if (expiration == null) return tags
        return tags.filterNot { it.isNotEmpty() && it[0] == ExpirationTag.TAG_NAME }.toTypedArray() + ExpirationTag.assemble(expiration)
    }

    /**
     * The outer tags [rumor]'s kind-1059 wrap must carry (§2): the rumor's own expiration, repeated
     * with the same value so NIP-40 relays delete the ciphertext, or nothing when the rumor carries
     * none. Hand it to [com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope.wrap]'s
     * `outerTags`.
     */
    fun wrapTagsFor(rumor: Event): TagArray = expirationOf(rumor)?.let { arrayOf(ExpirationTag.assemble(it)) } ?: emptyArray()

    /** One day, the shortest timer a client should offer (§3: it dwarfs any honest clock skew). */
    const val MIN_OFFERED_SECS = 86_400L

    /**
     * The timers a client offers staff, in seconds (`0` = off): off, 1 day, 1 week, 30 days, 90 days
     * and 1 year — the reference client's presets. Nothing shorter than [MIN_OFFERED_SECS].
     */
    val PRESET_SECS: List<Long> =
        listOf(0L, MIN_OFFERED_SECS, 7 * MIN_OFFERED_SECS, 30 * MIN_OFFERED_SECS, 90 * MIN_OFFERED_SECS, 365 * MIN_OFFERED_SECS)

    /** The rumor's own expiration, the only one a reader judges by (§3). */
    fun expirationOf(rumor: Event): Long? = rumor.tags.firstNotNullOfOrNull(ExpirationTag::parse)

    /** True when [rumor] carries an expiration at or before [now] (NIP-40: `exp <= now`). */
    fun isExpired(
        rumor: Event,
        now: Long = TimeUtils.now(),
    ): Boolean {
        val exp = expirationOf(rumor) ?: return false
        return exp <= now
    }

    /** The timer-notice rumor: the new value in seconds (`0` = off) bound to [channelId] at [epoch]. */
    fun timerNotice(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        timerSecs: Long,
        createdAt: Long = TimeUtils.now(),
    ): Event =
        RumorAssembler.assembleRumor(
            authorPubKey,
            eventTemplate<Event>(KIND_TIMER_NOTICE, "", createdAt) {
                channelBinding(channelId, epoch)
                add(arrayOf(TIMER_TAG, timerSecs.coerceAtLeast(0).toString()))
            },
        )

    /**
     * True when a reader may display timer notice [rumor] (§4): a well-formed 1740 whose author holds
     * MANAGE_METADATA in the folded [authority] (the owner always does; a banned member never). Anyone
     * can spell the tag; only staff are believed about policy, so everything else is dropped.
     */
    fun isBelievedNotice(
        rumor: Event,
        authority: AuthorityResolver,
    ): Boolean = noticeTimerSecs(rumor) != null && authority.hasPermission(rumor.pubKey, ConcordPermissions.MANAGE_METADATA)

    /**
     * The timer a notice announces, in seconds (`0` = turned off), or null when [rumor] isn't a
     * well-formed notice — a malformed value is dropped, never guessed at.
     */
    fun noticeTimerSecs(rumor: Event): Long? {
        if (rumor.kind != KIND_TIMER_NOTICE) return null
        val values = rumor.tags.filter { it.size >= 2 && it[0] == TIMER_TAG }
        if (values.size != 1) return null
        val raw = values[0][1]
        if (!DECIMAL.matches(raw)) return null
        return raw.toLongOrNull()
    }
}
