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
import com.vitorpamplona.amethyst.cli.CliJson
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotPollRumors
import com.vitorpamplona.marmot.foundation.appEvents.MarmotPolls
import com.vitorpamplona.quartz.nip01Core.core.Event

object MessageCommands {
    val USAGE: String =
        """
        |amy marmot message — MLS group messaging
        |
        |  marmot message send GID TEXT               publish kind:9 inner event into the group
        |  marmot message reply GID EVENT_ID TEXT     publish kind:9 replying to an inner event (q tag)
        |  marmot message list GID [--limit N]        dump decrypted inner events (default --limit 50;
        |                                              --limit 0 = unlimited)
        |  marmot message edit GID EVENT_ID TEXT      publish kind:1009 replacing a message's text
        |  marmot message react GID EVENT_ID EMOJI    publish kind:7 reaction targeting an inner event
        |  marmot message delete GID EVENT_ID…        publish kind:5 deletion targeting inner events
        |  marmot message poll GID QUESTION OPTION OPTION… [--multiple] [--ends-at UNIX]
        |                                             publish a kind:1068 poll (2–10 options, ids "0".."9")
        |  marmot message vote GID POLL_ID OPTION_ID… publish a kind:1018 vote; replaces your earlier vote
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "message",
            tail,
            "message <send|reply|list|edit|react|delete|poll|vote> …",
            mapOf(
                "send" to { rest -> send(dataDir, rest) },
                "reply" to { rest -> reply(dataDir, rest) },
                "list" to { rest -> list(dataDir, rest) },
                "edit" to { rest -> edit(dataDir, rest) },
                "react" to { rest -> react(dataDir, rest) },
                "delete" to { rest -> delete(dataDir, rest) },
                "poll" to { rest -> poll(dataDir, rest) },
                "vote" to { rest -> vote(dataDir, rest) },
            ),
            help = USAGE,
        )

    private suspend fun send(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "message send <gid> <text>")
        val text = rest[1]
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val bundle = ctx.marmot.buildTextMessage(gid, text)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, targets)
            RawEventSupport.publishGuard(ack, bundle.outbound.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "inner_event_id" to bundle.innerEvent.id,
                    "outer_event_id" to bundle.outbound.signedEvent.id,
                    "kind" to bundle.innerEvent.kind,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "message list <gid> [--limit N]")
        val args = Args(rest.drop(1).toTypedArray())
        // Default 50 newest messages; `--limit 0` restores the old dump-everything.
        val limitFlag = args.intFlag("limit", 50)
        args.rejectUnknown()
        val limit = if (limitFlag <= 0) Int.MAX_VALUE else limitFlag
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val raw = ctx.marmot.loadStoredMessages(gid)
            val parsed = raw.mapNotNull { Event.fromJsonOrNull(it) }
            val parsedById = parsed.associateBy { it.id }
            // An edit is not its own row: it replaces the target's text in
            // place. Resolving the overlay here rather than in the renderer is
            // what keeps every front end from re-deriving the authorship and
            // tie-break rules, and getting one of them subtly different.
            val overlays = ctx.marmot.editOverlays(parsed)
            // A deletion is not its own row either. The retracted body is
            // blanked rather than the row dropped, so a harness (or a reader
            // paging back) can tell "retracted" from "never arrived".
            // Admin deletions count when their author was an admin in the
            // epoch they were sent in, not by today's admin list.
            val deleted = ctx.marmot.deletedIds(gid, parsed)
            // Pinned at persist time from the retention of the epoch that
            // DELIVERED each message, so it is the message's own expiry and not
            // a recomputation against whatever the group's setting is now.
            val expiries = ctx.marmot.messageExpiries(gid)
            val items =
                raw
                    .map { line ->
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val obj = CliJson.toPlain(CliJson.parse(line)) as Map<*, *>
                            val id = obj["id"] as? String
                            val edited = overlays[id]
                            val retracted = id != null && id in deleted
                            val event = parsedById[id]
                            val poll = event?.let { MarmotPolls.parsePoll(it) }
                            mapOf(
                                "event_id" to obj["id"],
                                "author" to obj["pubkey"],
                                "kind" to obj["kind"],
                                "content" to if (retracted) "" else (edited ?: obj["content"]),
                                "edited" to (edited != null && !retracted),
                                "deleted" to retracted,
                                "expires_at" to expiries[id],
                                "created_at" to obj["created_at"],
                            ) +
                                listOfNotNull(
                                    event?.let(::replyTarget)?.let { "reply_to" to it },
                                    poll?.let { "poll" to pollFields(it) },
                                )
                        } catch (_: Exception) {
                            mapOf("raw" to line)
                        }
                    }.takeLast(limit)

            Output.emit(mapOf("group_id" to gid, "messages" to items))
            return 0
        }
    }

    /**
     * Replace a prior message's text. `message edit <gid> <event_id> <text>`
     *
     * The edit only lands for readers if this account wrote the target — every
     * receiver re-checks that against the message it holds — so the same check
     * runs here rather than publishing something that will be ignored.
     */
    private suspend fun edit(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 3) return Output.error("bad_args", "message edit <gid> <target_event_id> <text>")
        val targetId = rest[1]
        val replacement = rest[2]
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val target =
                findStoredInnerEvent(ctx, gid, targetId)
                    ?: return Output.error("not_found", "no stored message $targetId in group $gid")
            if (target.pubKey != ctx.identity.pubKeyHex) {
                return Output.error("not_author", "only the author of $targetId may replace its text")
            }

            val bundle = ctx.marmot.buildMessageEdit(gid, target.id, replacement)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, targets)
            RawEventSupport.publishGuard(ack, bundle.outbound.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "inner_event_id" to bundle.innerEvent.id,
                    "outer_event_id" to bundle.outbound.signedEvent.id,
                    "kind" to bundle.innerEvent.kind,
                    "target_event_id" to target.id,
                    "content" to replacement,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private suspend fun react(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 3) return Output.error("bad_args", "message react <gid> <target_event_id> <emoji>")
        val targetId = rest[1]
        val emoji = rest[2]
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val target =
                findStoredInnerEvent(ctx, gid, targetId)
                    ?: return Output.error("not_found", "no stored message $targetId in group $gid")
            val bundle = ctx.marmot.buildReactionMessage(gid, target, emoji)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, targets)
            RawEventSupport.publishGuard(ack, bundle.outbound.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "inner_event_id" to bundle.innerEvent.id,
                    "outer_event_id" to bundle.outbound.signedEvent.id,
                    "kind" to bundle.innerEvent.kind,
                    "target_event_id" to target.id,
                    "reaction" to emoji,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private suspend fun delete(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "message delete <gid> <target_event_id> [target_event_id ...]")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val targetIds = rest.drop(1)
            val targets =
                targetIds.map { id ->
                    findStoredInnerEvent(ctx, gid, id)
                        ?: return Output.error("not_found", "no stored message $id in group $gid")
                }

            // Someone else's chat message can only be removed by an admin, with
            // a kind 4891 (`features/content-moderation.md`), as White Noise does.
            // A kind 5 is the author's own retraction.
            val others = targets.filter { it.pubKey != ctx.identity.pubKeyHex }
            if (others.isNotEmpty() && (targets.size != 1 || !ctx.marmot.canAdminDelete(gid, others[0]))) {
                return Output.error("forbidden", "only a group admin can delete another member's chat message, one at a time")
            }
            val bundle =
                if (others.isEmpty()) {
                    ctx.marmot.buildDeletionMessage(gid, targets)
                } else {
                    ctx.marmot.buildAdminDeletionMessage(gid, others[0].id)
                }
            val relays = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, relays)
            RawEventSupport.publishGuard(ack, bundle.outbound.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "inner_event_id" to bundle.innerEvent.id,
                    "outer_event_id" to bundle.outbound.signedEvent.id,
                    "kind" to bundle.innerEvent.kind,
                    "target_event_ids" to targets.map { it.id },
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    /**
     * Reply to a message in the group. `message reply <gid> <event_id> <text>`
     *
     * The same kind:9 the app's composer sends for a reply: a `q` tag naming the parent and its
     * author, built by MarmotManager.buildTextMessage.
     */
    private suspend fun reply(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 3) return Output.error("bad_args", "message reply <gid> <target_event_id> <text>")
        val targetId = rest[1]
        val text = rest[2]
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val target =
                findStoredInnerEvent(ctx, gid, targetId)
                    ?: return Output.error("not_found", "no stored message $targetId in group $gid")
            val bundle = ctx.marmot.buildTextMessage(gid, text, replyToEventId = target.id, replyToAuthorPubKey = target.pubKey)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, targets)
            RawEventSupport.publishGuard(ack, bundle.outbound.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "inner_event_id" to bundle.innerEvent.id,
                    "outer_event_id" to bundle.outbound.signedEvent.id,
                    "kind" to bundle.innerEvent.kind,
                    "reply_to" to target.id,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    /**
     * Ask the group a question. `message poll <gid> <question> <option> <option>… [--multiple] [--ends-at UNIX]`
     *
     * Options get the ids "0".."9" in the order given; `message vote` takes those ids.
     */
    private suspend fun poll(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest, booleanFlags = setOf("multiple"), valueFlags = setOf("ends-at"))
        val multiple = args.bool("multiple")
        val endsAt = args.flag("ends-at")?.let { it.toLongOrNull() ?: return Output.error("bad_args", "--ends-at takes unix seconds, got '$it'") }
        args.rejectUnknown()
        val positional = args.positional
        if (positional.size < 4) return Output.error("bad_args", "message poll <gid> <question> <option> <option>… [--multiple] [--ends-at UNIX]")
        val question = positional[1]
        val options = positional.drop(2)
        val type = if (multiple) MarmotPolls.PollType.MULTIPLE_CHOICE else MarmotPolls.PollType.SINGLE_CHOICE

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(positional[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            var reason: MarmotPolls.BuildError? = null
            val rumor =
                MarmotPollRumors.poll(ctx.identity.pubKeyHex, question, options, type, endsAt, onError = { reason = it })
                    ?: return Output.error("bad_args", pollErrorText(reason))
            val bundle = ctx.marmot.buildGroupMessage(gid, rumor)
            ctx.marmot.persistDecryptedMessage(gid, rumor.toJson(), bundle.epoch)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.signedEvent, targets)
            RawEventSupport.publishGuard(ack, bundle.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "inner_event_id" to rumor.id,
                    "outer_event_id" to bundle.signedEvent.id,
                    "kind" to rumor.kind,
                    "poll" to pollFields(MarmotPolls.parsePoll(rumor)!!),
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    /**
     * Vote on a poll in the group. `message vote <gid> <poll_event_id> <option_id>…`
     *
     * A vote is the voter's whole selection and replaces any earlier one; the poll must still be
     * open and the selection must fit its type (one id for a single-choice poll).
     */
    private suspend fun vote(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 3) return Output.error("bad_args", "message vote <gid> <poll_event_id> <option_id>…")
        val pollId = rest[1]
        val optionIds = rest.drop(2)
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val poll =
                findStoredInnerEvent(ctx, gid, pollId)
                    ?: return Output.error("not_found", "no stored message $pollId in group $gid")
            val definition =
                MarmotPolls.parsePoll(poll)
                    ?: return Output.error("not_a_poll", "$pollId is not a poll this client can read")
            val rumor =
                MarmotPollRumors.vote(ctx.identity.pubKeyHex, poll, optionIds)
                    ?: return Output.error(
                        "bad_selection",
                        "can't vote ${optionIds.joinToString(",")} on $pollId: the poll is closed or the selection does not fit it " +
                            "(options ${definition.options.joinToString(",") { it.id }}, ${definition.type.nip88})",
                    )
            val bundle = ctx.marmot.buildGroupMessage(gid, rumor)
            ctx.marmot.persistDecryptedMessage(gid, rumor.toJson(), bundle.epoch)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.signedEvent, targets)
            RawEventSupport.publishGuard(ack, bundle.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "inner_event_id" to rumor.id,
                    "outer_event_id" to bundle.signedEvent.id,
                    "kind" to rumor.kind,
                    "poll_id" to poll.id,
                    "options" to MarmotPolls.parseResponse(rumor)?.second,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private fun pollErrorText(reason: MarmotPolls.BuildError?): String =
        when (reason) {
            MarmotPolls.BuildError.InvalidQuestion -> "the question is empty, too long or has control characters"
            MarmotPolls.BuildError.InvalidOptionCount -> "a poll takes ${MarmotPolls.MIN_OPTIONS} to ${MarmotPolls.MAX_OPTIONS} options"
            MarmotPolls.BuildError.InvalidOptionLabel -> "an option is empty, too long or has control characters"
            MarmotPolls.BuildError.InvalidDeadline -> "--ends-at must be in the future and within 30 days"
            else -> "the poll breaks the Marmot poll profile"
        }

    private fun pollFields(poll: MarmotPolls.Definition): Map<String, Any?> =
        mapOf(
            "question" to poll.question,
            "type" to poll.type.nip88,
            "ends_at" to poll.endsAt,
            "options" to poll.options.map { mapOf("id" to it.id, "label" to it.label) },
        )

    /** The message [event] replies to: its first `q` tag, or White Noise's `e` on a chat message. */
    private fun replyTarget(event: Event): String? {
        if (event.kind != 9) return null
        return event.tags.firstOrNull { it.size > 1 && it[0] == "q" }?.get(1)
            ?: event.tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)
    }

    /**
     * Scan the group's persisted inner-message log for an event matching
     * [targetId] and reconstruct the full `Event`. Required by `react` and
     * `delete` because the NIP-25 / NIP-09 templates need the target's
     * `pubKey` + `kind` for p-tag / k-tag, not just the id.
     */
    private suspend fun findStoredInnerEvent(
        ctx: Context,
        nostrGroupId: String,
        targetId: String,
    ): Event? {
        for (line in ctx.marmot.loadStoredMessages(nostrGroupId)) {
            val parsed = Event.fromJsonOrNull(line) ?: continue
            if (parsed.id == targetId) return parsed
        }
        return null
    }
}
