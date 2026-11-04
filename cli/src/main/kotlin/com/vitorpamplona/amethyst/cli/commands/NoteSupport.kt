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
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.model.BroadcastRelayPlanner
import com.vitorpamplona.amethyst.commons.model.BroadcastRelaySource
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.LocatedEvent
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.OneShotNoteCache
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.OneShotRelayAccess
import com.vitorpamplona.amethyst.commons.rendering.EventRendererRegistry
import com.vitorpamplona.amethyst.commons.rendering.RenderContext
import com.vitorpamplona.amethyst.commons.rendering.json.JsonEventFormatter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupScoped
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
    /**
     * [Context]'s store and relay pool as the port the commons one-shot loaders run over.
     * The relay sets are read once: each costs several store reads and one command asks
     * for them many times.
     */
    fun access(ctx: Context): OneShotRelayAccess =
        object : OneShotRelayAccess {
            private var bootstrap: Set<NormalizedRelayUrl>? = null
            private var index: Set<NormalizedRelayUrl>? = null

            override suspend fun query(filter: Filter): List<Event> = ctx.store.query(filter)

            override suspend fun fetch(
                filters: Map<NormalizedRelayUrl, List<Filter>>,
                timeoutMs: Long,
            ) = ctx.drain(filters, timeoutMs)

            override suspend fun bootstrapRelays() = bootstrap ?: ctx.bootstrapRelays().also { bootstrap = it }

            override suspend fun indexRelays() = index ?: ctx.indexRelays().also { index = it }
        }

    /** More tagged people than this and an event is a list (a kind:3 tags every follow), not addressed to them. */
    private const val MAX_ADDRESSED_PEOPLE = 50

    /** Whom [event] is addressed to: its author and the people it tags, unless it tags a whole list of them. */
    fun addressedPeople(event: Event): List<HexKey> {
        val tagged = event.taggedUserIds()
        return if (tagged.size <= MAX_ADDRESSED_PEOPLE) listOf(event.pubKey) + tagged else listOf(event.pubKey)
    }

    /**
     * NIP-29 group content exists on its host relay only; the planner routes anything about it
     * there once [notes] knows the host, which it learns from the relay that served the event.
     * A store hit has no relay, so the host is taken from [extraRelays] (`--relay`), and with
     * neither this returns the error exit code: routing on would publish group content to
     * public relays.
     */
    fun requireGroupHost(
        notes: OneShotNoteCache,
        target: Event,
        extraRelays: Set<NormalizedRelayUrl>,
    ): Int? {
        if (!target.isGroupScoped() || notes.cache.relayGroupHostsFor(target).isNotEmpty()) return null
        if (extraRelays.isNotEmpty()) notes.add(target, extraRelays)
        if (notes.cache.relayGroupHostsFor(target).isNotEmpty()) return null
        return Output.error(
            "bad_args",
            "event ${target.id} belongs to NIP-29 group '${target.groupId()}' and its host relay is unknown; pass --relay HOST",
        )
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
        // Both lists keep their private relays NIP-44 encrypted to ourselves.
        val privateOutbox =
            listRelays(
                "private outbox (kind:10013)",
                ctx.latestReplaceable(me, PrivateOutboxRelayListEvent.KIND) as? PrivateOutboxRelayListEvent,
                { it.publicRelays() },
                { it.privateRelays(ctx.signer) },
            )
        val broadcast =
            listRelays(
                "broadcast (kind:10088)",
                ctx.latestReplaceable(me, BroadcastRelayListEvent.KIND) as? BroadcastRelayListEvent,
                { it.publicRelays() },
                { it.decryptPrivateRelays(ctx.signer) },
            )
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

    /**
     * The relays in one of our lists: its public tags plus the encrypted part. [private] returns
     * null when that part does not decrypt (a bunker timing out, say); this is reported on
     * stderr, since the event still goes out, to fewer relays than the user configured.
     */
    private suspend fun <L : Event> listRelays(
        name: String,
        list: L?,
        public: (L) -> List<NormalizedRelayUrl>,
        private: suspend (L) -> List<NormalizedRelayUrl>?,
    ): Set<NormalizedRelayUrl> {
        if (list == null) return emptySet()
        if (list.content.isBlank()) return public(list).toSet()
        val decrypted = orNull { private(list) }
        if (decrypted == null) System.err.println("[amy] warning: could not decrypt your $name relay list; not routing to its private relays")
        return (public(list) + decrypted.orEmpty()).toSet()
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
