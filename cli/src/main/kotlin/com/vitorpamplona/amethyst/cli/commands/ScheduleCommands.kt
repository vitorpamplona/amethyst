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
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageClock
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleDeletion
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduledTaskEdits
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduledTaskRelays
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronExpression
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `amy schedule …` — NIP-FB scheduled tasks (kind 34817). amy edits a task's settings and never
 * claims or runs one. The quorum'd read-modify-write and the plans are commons'
 * (`model/nipFBScheduledTasks/ScheduledTaskRelays.kt`); this file wires them to amy's store.
 */
object ScheduleCommands {
    val USAGE: String =
        """
        |amy schedule — NIP-FB scheduled tasks (kind 34817); amy edits them, never claims or runs one
        |
        |  schedule list                       every task, decrypted, with its next run
        |  schedule show [D]                   one task (D defaults to `observer`, the morning paper)
        |  schedule set [D] --cron EXPR [--tz ZONE] [--task NAME] [--paused]
        |                                      when D runs: five cron fields in ZONE, an IANA name
        |                                      (default: the task's own, or this machine's for a new
        |                                      task, which runs NAME, default D)
        |  schedule pause [D]                  nobody runs it until resumed
        |  schedule resume [D]                 runs again from now on
        |  schedule delete [D]                 NIP-09 deletion of the task's address
        |
        |  Reads and writes go to your NIP-65 write relays only; more than half must answer.
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "schedule",
            tail,
            "schedule <list|show|set|pause|resume|delete>",
            help = USAGE,
            routes =
                mapOf(
                    "list" to { rest -> list(dataDir, rest) },
                    "show" to { rest -> show(dataDir, rest) },
                    "set" to { rest -> set(dataDir, rest) },
                    "pause" to { rest -> enable(dataDir, rest, on = false) },
                    "resume" to { rest -> enable(dataDir, rest, on = true) },
                    "delete" to { rest -> delete(dataDir, rest) },
                ),
        )

    /** One task as amy shows it; only `d` and the event when [task] can't be read. */
    private fun row(
        dTag: String,
        event: Event?,
        task: ScheduledTask?,
        now: Long,
    ): Map<String, Any?> {
        val base = mapOf("d" to dTag, "event_id" to event?.id, "created_at" to event?.createdAt, "readable" to (task != null))
        if (task == null) return base
        val zone = FrontPageClock.cronZone(task.tz)
        val claim =
            task.claim?.let {
                mapOf("due" to it.due, "id" to it.id, "by" to it.by, "at" to it.at, "finished" to ScheduledTaskClaims.isFinished(task))
            }
        return base +
            mapOf(
                "task" to task.task,
                "cron" to task.cron,
                "tz" to task.tz,
                "cron_readable" to (task.isReadable() && zone != null),
                "set_at" to task.cronSetAt,
                "paused" to task.paused,
                "claim" to claim,
                "ran" to task.ran,
                "next_run" to ScheduledTaskEdits.nextRun(task, zone, now),
                "missed" to zone?.let { task.missedRun(it, now) },
            )
    }

    /** What the last send reported: where it went and who refused it, for the `--json` output. */
    private class Sent {
        var ack: Map<NormalizedRelayUrl, PublishResult> = emptyMap()
        var event: ScheduledTaskEvent? = null

        fun fields(): Map<String, Any?> = if (ack.isEmpty()) emptyMap() else RawEventSupport.ackFields(ack)
    }

    /** Runs [body] with this account's write relays and the editor for task [dTag] over them. */
    private suspend fun withTask(
        dataDir: DataDir,
        dTag: String,
        write: Boolean,
        body: suspend (ScheduledTaskRelays, Set<NormalizedRelayUrl>, Sent) -> Int,
    ): Int {
        Context.open(dataDir).use { ctx ->
            if (write && !ctx.signer.isWriteable()) return Output.error("read_only", "this account can't sign (npub-only login)")
            ctx.prepare()
            val relays = ctx.ownWriteRelays()
            if (relays.isEmpty()) return Output.error("no_relays", "no NIP-65 write relays (none found on this machine or the bootstrap relays); add one with `amy relay outbox add URL`")
            val known = ctx.store.query<Event>(ScheduledTaskEvent.filter(ctx.identity.pubKeyHex, dTag)).filterIsInstance<ScheduledTaskEvent>()
            val sent = Sent()
            val editor =
                ScheduledTaskRelays(
                    signer = ctx.signer,
                    client = ctx.client,
                    dTag = dTag,
                    writeRelays = { relays },
                    known = { ScheduledTaskClaims.winner(known) },
                    onRead = { event, _ -> ctx.verifyAndStore(event) },
                    onSigned = { event -> if (event is ScheduledTaskEvent) sent.event = event },
                    // amy's own publish: it stores the event, answers NIP-42 challenges and says where it went.
                    sender = { event, to ->
                        ctx
                            .publish(event, to)
                            .also { sent.ack = it }
                            .filterValues { it.accepted }
                            .keys
                    },
                )
            return body(editor, relays, sent)
        }
    }

    private fun noQuorum(
        relays: Set<NormalizedRelayUrl>,
        what: String,
    ) = Output.error("no_quorum", "not more than half of your ${relays.size} write relay(s) $what")

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        Args(rest).rejectUnknown()
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val relays = ctx.ownWriteRelays()
            if (relays.isEmpty()) return Output.error("no_relays", "no NIP-65 write relays (none found on this machine or the bootstrap relays); add one with `amy relay outbox add URL`")
            val listing = ScheduledTaskRelays.readAll(ctx.client, ctx.signer, relays, onRead = { event, _ -> ctx.verifyAndStore(event) })
            if (!listing.quorum) System.err.println("[amy] warning: not more than half of your write relays answered; the list may be stale")
            val now = TimeUtils.now()
            Output.emit(mapOf("tasks" to listing.entries.map { row(it.dTag, it.event, it.task, now) }, "count" to listing.entries.size, "quorum" to listing.quorum))
            return 0
        }
    }

    private suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val dTag = args.positionalOrNull(0) ?: ObserverSchedule.D_TAG
        args.rejectUnknown()
        return withTask(dataDir, dTag, write = false) { editor, relays, _ ->
            val read = editor.readQuorum()
            val event = read.event ?: return@withTask if (read.quorum) Output.error("not_found", "no task $dTag") else noQuorum(relays, "answered")
            Output.emit(row(dTag, event, read.task, TimeUtils.now()) + ("quorum" to read.quorum))
            0
        }
    }

    private suspend fun set(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        // `--paused` takes no value: `set --paused weekly --cron …` edits `weekly`.
        val args = Args(rest, booleanFlags = setOf("paused"))
        val dTag = args.positionalOrNull(0) ?: ObserverSchedule.D_TAG
        val cronText = args.requireFlag("cron")
        val zone = args.flag("tz")
        val taskName = args.flag("task")
        val paused = args.bool("paused")
        args.rejectUnknown()
        val cron = CronExpression.parse(cronText) ?: return Output.error("bad_args", "not a NIP-FB cron (minute hour day-of-month month day-of-week): $cronText")
        if (zone != null && FrontPageClock.cronZone(zone) == null) return Output.error("bad_args", "not an IANA time zone name: $zone")
        val localZone = FrontPageClock.localZone()
        return withTask(dataDir, dTag, write = true) { editor, relays, sent ->
            val now = TimeUtils.now()
            var noZone = false
            var seen: ScheduledTask? = null
            val outcome =
                editor.edit(now) { current ->
                    seen = current
                    val fallback = localZone ?: current?.tz
                    if (zone == null && fallback == null) {
                        noZone = true
                        null
                    } else {
                        ScheduledTaskEdits.withSchedule(current, dTag, cron, zone, fallback ?: zone!!, now, taskName, paused)
                    }
                }
            if (noZone) return@withTask Output.error("bad_args", "this machine's time zone has no IANA name; pass --tz ZONE")
            report(dTag, outcome, sent, relays, now, seen)
        }
    }

    private suspend fun enable(
        dataDir: DataDir,
        rest: Array<String>,
        on: Boolean,
    ): Int {
        val args = Args(rest)
        val dTag = args.positionalOrNull(0) ?: ObserverSchedule.D_TAG
        args.rejectUnknown()
        return withTask(dataDir, dTag, write = true) { editor, relays, sent ->
            val now = TimeUtils.now()
            var missing = false
            var current: ScheduledTask? = null
            val outcome =
                editor.edit(now) { task ->
                    current = task
                    missing = task == null
                    task?.withEnabled(on, now)
                }
            if (missing && outcome == ScheduleEdit.Unchanged) return@withTask Output.error("not_found", "no task $dTag")
            report(dTag, outcome, sent, relays, now, current)
        }
    }

    private fun report(
        dTag: String,
        outcome: ScheduleEdit,
        sent: Sent,
        relays: Set<NormalizedRelayUrl>,
        now: Long,
        current: ScheduledTask? = null,
    ): Int =
        when (outcome) {
            is ScheduleEdit.Saved -> {
                Output.emit(row(dTag, sent.event, outcome.task, now) + ("changed" to true) + sent.fields())
                0
            }
            ScheduleEdit.Unchanged -> {
                Output.emit(row(dTag, null, current, now) + ("changed" to false))
                0
            }
            ScheduleEdit.Unreachable -> noQuorum(relays, "answered; nothing was written")
            ScheduleEdit.NotAccepted -> Output.error("no_quorum", "not more than half of your ${relays.size} write relay(s) accepted the edit; other devices may not see it", sent.fields())
            ScheduleEdit.Unreadable -> Output.error("decrypt_failed", "task $dTag holds content this signer can't read; not overwriting it")
            ScheduleEdit.CannotSign -> Output.error("signer_error", "the signer refused or failed")
            ScheduleEdit.NoWriteRelays -> Output.error("no_relays", "no NIP-65 write relays")
            ScheduleEdit.NotExpressible, ScheduleEdit.NoZone -> Output.error("bad_args", "the schedule can't be said in its zone")
        }

    private suspend fun delete(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val dTag = args.positionalOrNull(0) ?: ObserverSchedule.D_TAG
        args.rejectUnknown()
        return withTask(dataDir, dTag, write = true) { editor, relays, sent ->
            when (val outcome = editor.delete()) {
                is ScheduleDeletion.Deleted -> {
                    Output.emit(mapOf("d" to dTag, "deleted" to true, "event_id" to outcome.deletion.id, "target" to outcome.target.id, "address" to outcome.target.address().toValue()) + sent.fields())
                    0
                }
                ScheduleDeletion.NotFound -> Output.error("not_found", "no task $dTag")
                ScheduleDeletion.Unreachable -> noQuorum(relays, "answered; nothing was sent")
                is ScheduleDeletion.NotAccepted -> noQuorum(relays, "accepted the deletion ${outcome.deletion.id}")
                ScheduleDeletion.CannotSign -> Output.error("signer_error", "the signer refused or failed")
                ScheduleDeletion.NoWriteRelays -> Output.error("no_relays", "no NIP-65 write relays")
            }
        }
    }
}
