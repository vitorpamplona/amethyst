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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Single entry point for every read-side `LocalCache` → [NestViewModel]
 * fan-out the room screen needs. Wraps four parallel collectors
 * (presence, chat, reactions, admin commands), the two eviction
 * tickers (presence + reactions), and the kick-authority check
 * into one call so the screen body doesn't have to thread the
 * VM through six LaunchedEffects.
 *
 * The wire subscription that populates LocalCache is
 * [com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource.NestRoomFilterAssemblerSubscription];
 * this composable is purely the read side — observing what's
 * already in cache, not opening relay REQs of its own.
 */
@Composable
internal fun NestRoomEventCollectors(
    viewModel: NestViewModel,
    event: MeetingSpaceEvent,
    roomATag: String,
    localPubkey: String,
) {
    PresenceCollector(viewModel, roomATag)
    PresenceEvictionTicker(viewModel)
    ChatCollector(viewModel, roomATag)
    ReactionsCollector(viewModel, roomATag)
    ReactionsEvictionTicker(viewModel)
    AdminCommandsCollector(viewModel, event, roomATag, localPubkey)
}

/**
 * Pump kind-10312 presence events from LocalCache into the VM's
 * aggregator. Drives the listener counter, participant grid, and
 * hand-raise queue.
 */
@Composable
private fun PresenceCollector(
    viewModel: NestViewModel,
    roomATag: String,
) {
    LaunchedEffect(viewModel, roomATag) {
        val filter =
            Filter(
                kinds = listOf(MeetingRoomPresenceEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeEvents<MeetingRoomPresenceEvent>(filter).collect { events ->
            events.forEach { viewModel.onPresenceEvent(it) }
        }
    }
}

/**
 * Drop peers silent for >6 min (one missed 30-s heartbeat plus a
 * 5-min "still here" tolerance window so a transient relay hiccup
 * doesn't drop everyone). Self-cancels with the composable.
 */
@Composable
private fun PresenceEvictionTicker(viewModel: NestViewModel) {
    LaunchedEffect(viewModel) {
        while (isActive) {
            delay(PRESENCE_EVICT_INTERVAL_MS)
            viewModel.evictStalePresences(System.currentTimeMillis() / 1000 - PRESENCE_STALE_THRESHOLD_SEC)
        }
    }
}

/** Pump kind-1311 chat messages into the VM's chat ledger. */
@Composable
private fun ChatCollector(
    viewModel: NestViewModel,
    roomATag: String,
) {
    LaunchedEffect(viewModel, roomATag) {
        val filter =
            Filter(
                kinds = listOf(LiveActivitiesChatMessageEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeEvents<LiveActivitiesChatMessageEvent>(filter).collect { events ->
            events.forEach { viewModel.onChatEvent(it) }
        }
    }
}

/**
 * Pump kind-7 reactions into the VM's sliding-window aggregator.
 * The aggregator drops entries older than 30 s; the 1-s tick in
 * [ReactionsEvictionTicker] drives that eviction so the
 * floating-up overlay animation timing lives in one place rather
 * than per-component.
 */
@Composable
private fun ReactionsCollector(
    viewModel: NestViewModel,
    roomATag: String,
) {
    LaunchedEffect(viewModel, roomATag) {
        val filter =
            Filter(
                kinds = listOf(ReactionEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeEvents<ReactionEvent>(filter).collect { events ->
            val nowSec = System.currentTimeMillis() / 1000
            events.forEach { viewModel.onReactionEvent(it, nowSec) }
        }
    }
}

@Composable
private fun ReactionsEvictionTicker(viewModel: NestViewModel) {
    LaunchedEffect(viewModel) {
        while (isActive) {
            delay(REACTIONS_TICK_MS)
            viewModel.evictReactions(System.currentTimeMillis() / 1000 - REACTION_WINDOW_SEC_LOCAL)
        }
    }
}

/**
 * Pump kind-4312 admin commands targeting THIS user. The
 * signer-must-be-host-or-moderator authority check happens
 * here (not in the relay) — only honour kicks where the signer's
 * `ParticipantTag.canSpeak()` returns true on the active
 * kind-30312, OR the signer IS the room author. nostrnests' UI
 * does the same gating.
 */
@Composable
private fun AdminCommandsCollector(
    viewModel: NestViewModel,
    event: MeetingSpaceEvent,
    roomATag: String,
    localPubkey: String,
) {
    LaunchedEffect(viewModel, roomATag, localPubkey) {
        val filter =
            Filter(
                kinds = listOf(AdminCommandEvent.KIND),
                tags = mapOf("a" to listOf(roomATag), "p" to listOf(localPubkey)),
            )
        LocalCache.observeNewEvents<AdminCommandEvent>(filter).collect { cmd ->
            if (cmd.action() != AdminCommandEvent.Action.KICK) return@collect
            if (cmd.targetPubkey() != localPubkey) return@collect
            val signerIsAuthorised =
                cmd.pubKey == event.pubKey ||
                    event.participants().any { it.pubKey == cmd.pubKey && (it.isHost() || it.isModerator()) }
            if (!signerIsAuthorised) return@collect
            viewModel.onKick()
        }
    }
}

private const val PRESENCE_EVICT_INTERVAL_MS = 60_000L
private const val PRESENCE_STALE_THRESHOLD_SEC = 6L * 60L
private const val REACTIONS_TICK_MS = 1_000L

// Mirror of [com.vitorpamplona.amethyst.commons.viewmodels.REACTION_WINDOW_SEC]
// — the platform layer doesn't import from commons here, and a
// duplicate constant is cheaper than another import. If these
// drift, the 1-s tick still self-heals within one window length.
private const val REACTION_WINDOW_SEC_LOCAL = 30L
