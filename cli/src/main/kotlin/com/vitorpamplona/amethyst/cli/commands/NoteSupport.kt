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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.commons.model.BroadcastRelayPlanner
import com.vitorpamplona.amethyst.commons.model.BroadcastRelaySource
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.LocatedEvent
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.OneShotNoteCache
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.OneShotRelayAccess
import com.vitorpamplona.amethyst.commons.rendering.EventRendererRegistry
import com.vitorpamplona.amethyst.commons.rendering.RenderContext
import com.vitorpamplona.amethyst.commons.rendering.json.JsonEventFormatter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import kotlin.coroutines.cancellation.CancellationException

/**
 * amy's adapters for the verbs that act on notes (`notes show / reply / quote / react /
 * repost / thread`, `notifications`, `delete`). The logic — lookup, profile loading,
 * the store-backed note cache, thread and notification loading — is commons'
 * `relayClient/oneshot` package; routing, tagging and the event builders are the app's
 * own code. What stays here is the [Context] port, the account's routing inputs and the
 * JSON shape.
 */
object NoteSupport {
    /** [Context]'s store and relay pool as the port the commons one-shot loaders run over. */
    fun access(ctx: Context): OneShotRelayAccess =
        object : OneShotRelayAccess {
            override suspend fun query(filter: Filter): List<Event> = ctx.store.query(filter)

            override suspend fun fetch(
                filters: Map<NormalizedRelayUrl, List<Filter>>,
                timeoutMs: Long,
            ) = ctx.drain(filters, timeoutMs)

            override suspend fun bootstrapRelays() = ctx.bootstrapRelays()

            override suspend fun indexRelays() = ctx.indexRelays()
        }

    /**
     * The app's routing over [notes] and the account's relay lists. Reads (and decrypts)
     * our own lists once up front: the planner's inputs are plain sets.
     */
    suspend fun planner(
        ctx: Context,
        notes: OneShotNoteCache,
    ): BroadcastRelayPlanner {
        val me = ctx.identity.pubKeyHex
        notes.addUsers(listOf(me))
        // The planner recognises our own events by finding our User in the cache (as the app
        // always does); create it even when the store holds nothing of ours yet.
        notes.user(me)
        val nip65Outbox = ctx.outboxRelays()
        val nip65Inbox = ctx.nip65ReadRelays()
        // Both lists are NIP-44 encrypted to ourselves; a list we cannot decrypt routes nowhere.
        val privateOutbox =
            orNull { (ctx.latestReplaceable(me, PrivateOutboxRelayListEvent.KIND) as? PrivateOutboxRelayListEvent)?.relays(ctx.signer) }.orEmpty().toSet()
        val broadcast =
            orNull { (ctx.latestReplaceable(me, BroadcastRelayListEvent.KIND) as? BroadcastRelayListEvent)?.decryptRelays(ctx.signer) }.orEmpty().toSet()
        val everywhere = ctx.bootstrapRelays() + ctx.indexRelays()

        return BroadcastRelayPlanner(
            notes.cache,
            object : BroadcastRelaySource {
                override fun userProfile() = notes.user(me)

                override fun notificationRelays() = nip65Inbox

                override fun broadcastRelays() = broadcast

                override fun outboxRelays() = nip65Outbox + privateOutbox + broadcast

                override fun personalOutboxRelays() = nip65Outbox + privateOutbox

                override fun everywhereRelays() = everywhere
            },
        )
    }

    private suspend fun <T> orNull(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    fun render(
        event: Event,
        renderCtx: RenderContext,
        includeBody: Boolean = true,
    ): Map<String, Any?> = JsonEventFormatter.toMap(EventRendererRegistry.render(event, renderCtx), includeBody)

    /** The `target` block every interaction verb echoes back. */
    fun targetFields(target: LocatedEvent): Map<String, Any?> =
        mapOf(
            "event_id" to target.event.id,
            "kind" to target.event.kind,
            "author" to target.event.pubKey,
            "source" to target.source,
        )
}
