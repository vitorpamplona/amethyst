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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.viewmodels.NestRoomEventSource
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter

/**
 * Production [NestRoomEventSource] backed by [LocalCache]. Wraps the
 * four `LocalCache.observe*` flows the room screen used to drive from
 * `LaunchedEffect`s in `NestRoomEventCollectors` so the read side
 * lives at viewModelScope lifetime instead of recomposing-with-the-screen.
 *
 * Filter shapes match the original Composable collectors verbatim
 * (see `NestRoomEventCollectors` in git history for the prior wiring).
 *
 * The kind-4312 admin-command auth gate (host/moderator-only),
 * 60-s freshness window, and lifetime-of-source dedup all live here
 * — the VM consumes [adminCommands] as a clean stream of trusted
 * verbs.
 */
internal class LocalCacheNestRoomEventSource(
    private val roomATag: String,
    private val localPubkey: String,
    initialEvent: MeetingSpaceEvent,
) : NestRoomEventSource {
    private val roomEventState = MutableStateFlow(initialEvent)

    override fun updateRoomEvent(event: MeetingSpaceEvent) {
        roomEventState.value = event
    }

    override val presenceEvents: Flow<List<MeetingRoomPresenceEvent>> by lazy {
        LocalCache.observeEvents(
            Filter(
                kinds = listOf(MeetingRoomPresenceEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            ),
        )
    }

    override val chatNotes: Flow<List<Note>> by lazy {
        LocalCache.observeNotes(
            Filter(
                kinds = listOf(LiveActivitiesChatMessageEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            ),
        )
    }

    override val reactionEvents: Flow<List<ReactionEvent>> by lazy {
        LocalCache.observeEvents(
            Filter(
                kinds = listOf(ReactionEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            ),
        )
    }

    /**
     * EGG-07 #7 / nostrnests `useAdminCommands.ts`: ignore admin
     * commands older than 60 s, gate by host/moderator authority on
     * the active kind-30312, dedupe by event id over the source's
     * lifetime so a relay re-emit on reconnect can't re-fire a kick.
     *
     * `processed` is mutated from the collector coroutine which runs
     * sequentially — no concurrent insertion.
     */
    override val adminCommands: Flow<AdminCommandEvent> by lazy {
        val processed = mutableSetOf<String>()
        LocalCache
            .observeNewEvents<AdminCommandEvent>(
                Filter(
                    kinds = listOf(AdminCommandEvent.KIND),
                    tags = mapOf("a" to listOf(roomATag), "p" to listOf(localPubkey)),
                    since = TimeUtils.now() - ADMIN_COMMAND_FRESHNESS_SEC,
                ),
            ).filter { cmd ->
                if (cmd.targetPubkey() != localPubkey) return@filter false
                // Defensive freshness re-check: relay might have served
                // a cached older event despite the `since` hint, or the
                // user's clock might have jumped forward.
                if (TimeUtils.now() - cmd.createdAt > ADMIN_COMMAND_FRESHNESS_SEC) return@filter false
                if (!processed.add(cmd.id)) return@filter false
                val event = roomEventState.value
                cmd.pubKey == event.pubKey ||
                    event.participants().any {
                        it.pubKey == cmd.pubKey && (it.isHost() || it.isModerator())
                    }
            }
    }

    companion object {
        /** Spec window from EGG-07 #7 — 60-second freshness gate on kind-4312. */
        private const val ADMIN_COMMAND_FRESHNESS_SEC: Long = 60L
    }
}
