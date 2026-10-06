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

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.actions.DeletionActions
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds

/**
 * `amy delete EVENT… [--relay URL,…] [--refresh] [--timeout SECS]` — NIP-09
 * deletion requests for your own events, any kind (`notes delete` is the same
 * verb). Each EVENT is located first (a kind:5 needs the target's kind, and its
 * coordinate when addressable), then [DeletionActions] builds the kind:5s and the
 * app's [com.vitorpamplona.amethyst.commons.model.BroadcastRelayPlanner] routes them
 * (your outbox plus every relay a target was seen on — a relay can only honour a
 * deletion it receives).
 */
object DeleteCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val refs = args.positional
        if (refs.isEmpty()) return Output.error("bad_args", "delete EVENT… [--relay URL]")
        val extraRelays = RawEventSupport.relayFlag(args)
        val refresh = args.bool("refresh")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        val parsed = refs.map(NoteSupport::parseRef)

        Context.open(dataDir).use { ctx ->
            if (!ctx.signer.isWriteable()) return Output.error("read_only", "this account can't sign (npub-only login)")
            ctx.prepare()
            val located =
                parsed.map { ref ->
                    NoteSupport.locate(ctx, ref, refresh, timeoutMs)
                        ?: return Output.error("not_found", "event not found: ${ref.input}")
                }
            located.firstOrNull { it.event.pubKey != ctx.identity.pubKeyHex }?.let {
                return Output.error("forbidden", "event ${it.event.id} was signed by ${it.event.pubKey}, not this account; only its author can delete it")
            }

            val deletions = DeletionActions.delete(located.map { it.event }, ctx.signer)

            // Account.delete's routing (our outbox + where each target was seen) plus where each
            // target was routed when it was published: our own events are cache hits, so "where it
            // was seen" is usually unknown, and a reply also went to the inboxes it notified.
            val notes = NoteCache(ctx, timeoutMs)
            notes.addUsers(located.flatMap { it.event.taggedUserIds() }, fetchMissing = true, alsoAsk = located.flatMap { it.seenOn })
            val targetNotes = located.mapNotNull { notes.add(it.event, it.seenOn) }
            val planner = notes.planner()
            val relays = planner.computeDeletionRelays(targetNotes) + targetNotes.flatMap { planner.computeRelayListToBroadcast(it) } + extraRelays

            // Every chunk is published before deciding the outcome: an early return after chunk 1
            // would hide the deletions that did go out.
            val published = deletions.map { it to ctx.publish(it, relays) }
            val results =
                published.map { (deletion, ack) ->
                    mapOf(
                        "event_id" to deletion.id,
                        "kind" to deletion.kind,
                        "created_at" to deletion.createdAt,
                        "targets" to deletion.deleteEventIds(),
                        "addresses" to deletion.deleteAddressIds(),
                        "accepted" to (ack.isEmpty() || ack.values.any { it.accepted }),
                    ) + RawEventSupport.ackFields(ack)
                }
            val failed = published.filter { (_, ack) -> ack.isNotEmpty() && ack.values.none { it.accepted } }
            if (failed.size == published.size) {
                val (deletion, ack) = published.first()
                return RawEventSupport.publishGuard(ack, deletion.id)!!
            }
            if (failed.isNotEmpty()) {
                return Output.error(
                    "partial",
                    "${failed.size} of ${published.size} deletion events were refused by every relay",
                    extra = mapOf("deletions" to results),
                )
            }

            Output.emit(
                mapOf(
                    "deleted" to located.map { it.event.id },
                    "count" to located.size,
                    "deletions" to results,
                ),
            )
            return 0
        }
    }
}
