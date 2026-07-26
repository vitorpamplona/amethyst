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
package com.vitorpamplona.amethyst.commons.model.buzz

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Somebody added [viewer] to a channel: who did it, where, and when. */
@Immutable
class BuzzChannelInvite(
    val channelId: String,
    val relay: NormalizedRelayUrl,
    val actor: HexKey?,
    val createdAt: Long,
)

/**
 * App-wide, per-viewer set of channels somebody **else** added the viewer to, awaiting the viewer's
 * decision about whether they appear on Messages.
 *
 * On a Buzz relay, membership is server-side: another member issues the add, the relay writes you into
 * the channel's kind-39002 roster, and you can immediately read and post. The relay then addresses you a
 * kind-44100 with `{"actor": …}` naming who did it — and it emits the *same* kind for a self-join, with
 * `actor == you`, which is the only thing separating the two cases.
 *
 * Amethyst used to funnel every 44100 into [BuzzDmChannels], which silently subscribed the viewer to the
 * channel's messages while the Messages list — which reads the self-published kind-10009 — showed no row
 * for it. So a channel could be simultaneously joined (relay roster, no Join button, composer enabled),
 * streaming messages, and invisible. Only `t = dm` channels belong in [BuzzDmChannels]; everything else
 * lands here until the viewer accepts.
 *
 * Accepting adds the group to kind-10009 (`Account.follow`), after which the normal joined-group path
 * owns it and the entry is dropped. Dismissing is a *display* choice recorded in
 * `AccountSettings.dismissedChannelInvites`; genuinely leaving is a kind-9022 `LeaveRequestEvent`, which
 * is a different action because the viewer really is a member until the relay says otherwise.
 */
object BuzzChannelInvites {
    private val lock = KmpLock()
    private val byViewer = HashMap<HexKey, MutableMap<String, BuzzChannelInvite>>()
    private val mutableFlow = MutableStateFlow<Map<HexKey, Map<String, BuzzChannelInvite>>>(emptyMap())

    /** Per-viewer pending invites (`channelId` -> who/where/when). */
    val flow: StateFlow<Map<HexKey, Map<String, BuzzChannelInvite>>> = mutableFlow

    /**
     * Records that somebody added [viewer] to [channelId]. Returns true when this is newly seen, so
     * callers can invalidate a feed; a repeat of the same (viewer, channel) returns false rather than
     * churning the flow — the relay re-sends the notification on every reconnect.
     */
    fun record(
        viewer: HexKey,
        invite: BuzzChannelInvite,
    ): Boolean =
        lock.withLock {
            val invites = byViewer.getOrPut(viewer) { mutableMapOf() }
            if (invites.containsKey(invite.channelId)) return@withLock false
            invites[invite.channelId] = invite
            mutableFlow.value = snapshot()
            true
        }

    /**
     * Drops an invite once it is no longer pending — the viewer accepted it (now in kind-10009), left the
     * channel, or the relay reported a kind-44101 removal.
     */
    fun remove(
        viewer: HexKey,
        channelId: String,
    ): Boolean =
        lock.withLock {
            val invites = byViewer[viewer] ?: return@withLock false
            if (invites.remove(channelId) == null) return@withLock false
            mutableFlow.value = snapshot()
            true
        }

    /** Invites pending for [viewer], possibly empty. */
    fun invitesFor(viewer: HexKey): Map<String, BuzzChannelInvite> = mutableFlow.value[viewer] ?: emptyMap()

    private fun snapshot(): Map<HexKey, Map<String, BuzzChannelInvite>> = byViewer.mapValues { it.value.toMap() }

    /** Test-only: clears all state so unit tests don't leak into each other. */
    fun clearForTesting() =
        lock.withLock {
            byViewer.clear()
            mutableFlow.value = emptyMap()
        }
}
