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
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.BroadcastRelayPlanner
import com.vitorpamplona.amethyst.commons.model.BroadcastRelaySource
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.composer.messageTags
import com.vitorpamplona.amethyst.commons.model.composer.pTagsWithHints
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlin.coroutines.cancellation.CancellationException

/**
 * A per-invocation [EventCache] — the class behind the app's `LocalCache` — filled from
 * amy's event store, so amy runs the app's own `Note`/`User`-based code instead of a
 * second implementation of it: [BroadcastRelayPlanner] for routing, `NewMessageTagger`
 * for message tagging, `ThreadAssembler` / `ThreadFeedFilter` for threads.
 *
 * It lives for one command (Rule 4: nothing survives the process); the store stays the
 * source of truth.
 */
class NoteCache(
    private val ctx: Context,
) {
    val cache = EventCache()

    /** Consumes [event] as seen on [relays] and returns its note (the addressable slot for an addressable event). */
    fun add(
        event: Event,
        relays: Collection<NormalizedRelayUrl> = emptyList(),
    ): Note? {
        cache.justConsume(event, null, true)
        val note = if (event is AddressableEvent) cache.getAddressableNoteIfExists(event.address()) else cache.getNoteIfExists(event.id)
        relays.forEach { note?.addRelay(it) }
        return note
    }

    /** Our own just-signed event, consumed the way the app consumes what it publishes. */
    fun addMine(event: Event): Note? {
        cache.justConsumeMyOwnEvent(event)
        return if (event is AddressableEvent) cache.getAddressableNoteIfExists(event.address()) else cache.getNoteIfExists(event.id)
    }

    /**
     * Loads what routing and mentions need to know about [pubKeys] — profile, NIP-65 and
     * DM relay lists — from the store. With [fetchMissing], first drains the kind:10002 of
     * anyone the store has none for (the routing input that matters most).
     */
    suspend fun addUsers(
        pubKeys: Collection<HexKey>,
        fetchMissing: Boolean = false,
    ) {
        val wanted = pubKeys.toSet()
        if (wanted.isEmpty()) return
        if (fetchMissing) {
            val missing = wanted.filter { ctx.relaysOf(it) == null }
            if (missing.isNotEmpty()) {
                val filter = Filter(authors = missing, kinds = listOf(AdvertisedRelayListEvent.KIND))
                ctx.drain(ctx.bootstrapRelays().associateWith { listOf(filter) })
            }
        }
        val kinds = listOf(MetadataEvent.KIND, AdvertisedRelayListEvent.KIND, DmRelayListEvent.KIND)
        ctx.store.query<Event>(Filter(authors = wanted.toList(), kinds = kinds)).forEach { add(it) }
    }

    fun user(pubKey: HexKey): User = cache.getOrCreateUser(pubKey)

    /** The composer's kind:1 for a tagged message: the users it cites as `p`, plus [messageTags]. */
    fun textNote(tagged: Tagged): EventTemplate<TextNoteEvent> =
        TextNoteEvent.build(tagged.message) {
            notify(tagged.mentions)
            messageTags(tagged.message)
        }

    /** A message after the composer's tagging: the rewritten text and the users it notifies. */
    class Tagged(
        val message: String,
        val mentions: List<PTag>,
    )

    /**
     * Runs the app's `NewMessageTagger` over [text]: bare `npub`/`note`/`nevent`/`naddr`
     * words (and `@npub`) become `nostr:` references with relay hints, and every cited
     * user — plus the author of every cited note — is collected for `p` tags. A first
     * pass finds who and what is cited so their relay lists and events can be loaded
     * from the store; the second pass then writes the same hints the app would.
     */
    suspend fun tag(text: String): Tagged {
        val probe = NewMessageTagger(message = text, dao = cache).also { it.run() }
        addUsers(probe.directMentionsUsers.map { it.pubkeyHex })
        probe.directMentionsNotes.forEach { note ->
            val filter =
                if (note is AddressableNote) {
                    Filter(kinds = listOf(note.address.kind), authors = listOf(note.address.pubKeyHex), tags = mapOf("d" to listOf(note.address.dTag)))
                } else {
                    Filter(ids = listOf(note.idHex))
                }
            ctx.store.query<Event>(filter).forEach { add(it) }
        }
        val tagger = NewMessageTagger(message = text, dao = cache).also { it.run() }
        return Tagged(tagger.message, tagger.pTagsWithHints(cache.relayHints).orEmpty())
    }

    /**
     * The app's routing over this cache and the account's relay lists. Reads (and decrypts)
     * our own lists once up front: the planner's inputs are plain sets.
     */
    suspend fun planner(): BroadcastRelayPlanner {
        val me = ctx.identity.pubKeyHex
        addUsers(listOf(me))
        val nip65Outbox = ctx.outboxRelays()
        val nip65Inbox = ctx.nip65ReadRelays()
        // Both lists are NIP-44 encrypted to ourselves; a list we cannot decrypt routes nowhere.
        val privateOutbox =
            orNull { (ctx.latestReplaceable(me, PrivateOutboxRelayListEvent.KIND) as? PrivateOutboxRelayListEvent)?.relays(ctx.signer) }.orEmpty().toSet()
        val broadcast =
            orNull { (ctx.latestReplaceable(me, BroadcastRelayListEvent.KIND) as? BroadcastRelayListEvent)?.decryptRelays(ctx.signer) }.orEmpty().toSet()
        val everywhere = ctx.bootstrapRelays() + ctx.indexRelays()

        return BroadcastRelayPlanner(
            cache,
            object : BroadcastRelaySource {
                override fun userProfile() = user(me)

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
}
