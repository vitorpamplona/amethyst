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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Read side of a Nests room: the four LocalCache-backed event streams
 * that [NestViewModel] folds into per-room state. Production implementation
 * (`LocalCacheNestRoomEventSource` in `amethyst/`) wraps `LocalCache.observe*`
 * with the appropriate filters; tests pass [EmptyNestRoomEventSource]
 * and drive the VM's public `on*Event` hooks directly.
 *
 * Splitting the read side off the VM lets the LocalCache → VM fan-out
 * live at viewModelScope lifetime (cancelled by `onCleared`) instead of
 * recomposing-with-the-screen `LaunchedEffect`s, and keeps `NestViewModel`
 * platform-agnostic (LocalCache is Android-only today).
 */
interface NestRoomEventSource {
    val presenceEvents: Flow<List<MeetingRoomPresenceEvent>>
    val chatNotes: Flow<List<Note>>
    val reactionEvents: Flow<List<ReactionEvent>>

    /**
     * Inbound kind-4312 admin commands targeting the local user that
     * have already passed the host/moderator authority check, the
     * 60-s freshness gate, and lifetime-of-source dedup. The VM
     * collects this and routes by [AdminCommandEvent.action].
     */
    val adminCommands: Flow<AdminCommandEvent>

    /**
     * Update the live [MeetingSpaceEvent] reference. The host/moderator
     * authority check inside [adminCommands] reads the latest value
     * at command-arrival time so a mid-session moderator promotion
     * takes effect before any command they issue lands.
     */
    fun updateRoomEvent(event: MeetingSpaceEvent)
}

/**
 * No-op source for unit tests that drive the VM via
 * [NestViewModel.onPresenceEvent] / `onChatEvent` / `onReactionEvent`
 * / `onKick` / `onForceMuted` directly.
 */
object EmptyNestRoomEventSource : NestRoomEventSource {
    override val presenceEvents: Flow<List<MeetingRoomPresenceEvent>> = emptyFlow()
    override val chatNotes: Flow<List<Note>> = emptyFlow()
    override val reactionEvents: Flow<List<ReactionEvent>> = emptyFlow()
    override val adminCommands: Flow<AdminCommandEvent> = emptyFlow()

    override fun updateRoomEvent(event: MeetingSpaceEvent) = Unit
}
