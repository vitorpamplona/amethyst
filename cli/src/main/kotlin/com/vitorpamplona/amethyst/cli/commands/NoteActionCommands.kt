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
import com.vitorpamplona.amethyst.commons.actions.ReplyActions
import com.vitorpamplona.amethyst.commons.model.BroadcastRelayPlanner
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.composer.quoteMessage
import com.vitorpamplona.amethyst.commons.model.nip18Reposts.RepostAction
import com.vitorpamplona.amethyst.commons.model.nip25Reactions.ReactionAction
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds

/**
 * `amy notes reply|quote|react|repost EVENT …` — the interactions on an existing
 * event. Each one locates the target (cache-first, `--refresh` to re-drain), loads it
 * into a [NoteCache] and then runs the app's own code end to end: the text goes
 * through the composer's `NewMessageTagger`, the event is built by the shared
 * builder ([ReplyActions], [ReactionAction], [RepostAction]; a quote is the composer's
 * pre-filled quote text posted as a note), and it
 * is routed by [BroadcastRelayPlanner] with the same call the app makes for that action.
 */
object NoteActionCommands {
    /** NIP-10 kind:1 reply to a kind:1, NIP-22 kind:1111 comment on anything else. */
    suspend fun reply(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val ref = args.positionalOrNull(0) ?: return Output.error("bad_args", "notes reply EVENT TEXT")
        val text = args.positionalOrNull(1) ?: return Output.error("bad_args", "notes reply EVENT TEXT")
        if (text.isBlank()) return Output.error("bad_args", "reply text must not be blank")
        // The composer publishes with signAndComputeBroadcast: routed as the new note.
        return interact(
            dataDir,
            args,
            ref,
            build = { ctx, notes, _, hint ->
                val tagged = notes.tag(text)
                ReplyActions.reply(hint, tagged.message, ctx.signer, tagged.mentions)
            },
            route = { planner, _, _, mine -> planner.computeRelayListToBroadcast(mine) },
        )
    }

    /** NIP-18 quote: a new kind:1 embedding `nostr:nevent…` with a `q` tag. */
    suspend fun quote(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val ref = args.positionalOrNull(0) ?: return Output.error("bad_args", "notes quote EVENT [TEXT]")
        val text = args.positionalOrNull(1).orEmpty()
        return interact(
            dataDir,
            args,
            ref,
            // Exactly the app's quote: the composer's pre-filled text, posted as a note.
            build = { ctx, notes, target, _ -> ctx.signer.sign(notes.textNote(notes.tag(quoteMessage(text, target).trim()))) },
            route = { planner, _, _, mine -> planner.computeRelayListToBroadcast(mine) },
        )
    }

    /** NIP-25 reaction. `--content` defaults to `+` (a like); `-` is a dislike; any emoji works. */
    suspend fun react(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val ref = args.positionalOrNull(0) ?: return Output.error("bad_args", "notes react EVENT [--content +|-|EMOJI]")
        val content = args.flag("content") ?: args.positionalOrNull(1) ?: "+"
        if (content.isEmpty()) return Output.error("bad_args", "--content must not be empty")
        // Account.reactTo publishes through sendAutomatic: routed as the event itself.
        return interact(
            dataDir,
            args,
            ref,
            build = { ctx, _, _, hint -> ReactionAction.reactTo(hint, content, ctx.signer) },
            route = { planner, _, signed, _ -> planner.computeRelayListToBroadcast(signed) },
        )
    }

    /** NIP-18 repost: kind:6 for a kind:1, kind:16 (generic repost) for any other kind. */
    suspend fun repost(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val ref = args.positionalOrNull(0) ?: return Output.error("bad_args", "notes repost EVENT")
        // Account.boost publishes to computeMyReactionToNote.
        return interact(
            dataDir,
            args,
            ref,
            build = { ctx, _, _, hint -> RepostAction.repost(hint, ctx.signer) },
            route = { planner, target, signed, _ -> planner.computeMyReactionToNote(target, signed) },
        )
    }

    private suspend fun interact(
        dataDir: DataDir,
        args: Args,
        refInput: String,
        build: suspend (Context, NoteCache, target: Note, hint: EventHintBundle<Event>) -> Event,
        route: (BroadcastRelayPlanner, target: Note, signed: Event, mine: Note) -> Set<NormalizedRelayUrl>,
    ): Int {
        val extraRelays = RawEventSupport.relayFlag(args)
        val refresh = args.bool("refresh")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        val ref = NoteSupport.parseRef(refInput)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val target =
                NoteSupport.locate(ctx, ref, refresh, timeoutMs)
                    ?: return Output.error("not_found", "event not found: $refInput")

            val notes = NoteCache(ctx)
            // The target author's inbox is where the app routes a notification; fetch it if unknown.
            notes.addUsers(listOf(target.event.pubKey), fetchMissing = true)
            notes.addUsers(target.event.taggedUserIds())
            val targetNote =
                notes.add(target.event, target.seenOn)
                    ?: return Output.error("not_found", "event not found: $refInput")

            // The same hint the app writes: the relay it saw the note on, and its author's home relay.
            val hint = targetNote.toEventHint<Event>() ?: EventHintBundle(target.event, target.seenOn.firstOrNull())
            val signed = build(ctx, notes, targetNote, hint)
            notes.addUsers(signed.taggedUserIds())
            val planner = notes.planner()
            val mine = notes.addMine(signed) ?: return Output.error("runtime", "could not cache ${signed.id}")
            val ack = ctx.publish(signed, route(planner, targetNote, signed, mine) + extraRelays)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }

            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "created_at" to signed.createdAt,
                    "content" to signed.content,
                    "tags" to signed.tags.map { it.toList() },
                    "target" to NoteSupport.targetFields(target),
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }
}
