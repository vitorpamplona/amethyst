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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzJobAggregator
import com.vitorpamplona.amethyst.commons.model.buzz.JobState
import com.vitorpamplona.amethyst.commons.model.buzz.JobView
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent

/**
 * `amy buzz job …` — the requester side of the Buzz agent-job protocol (kinds
 * 43001-43006): ask an agent to do a task, list jobs, inspect one job's lifecycle, and
 * cancel. The agent side (accept/progress/result/error, plus a driving loop) lives in
 * [BuzzAgentCommands]. Job correlation + state folding is the shared, tested
 * [BuzzJobAggregator] in `commons`, so this file stays a thin assembly layer.
 *
 * SCHEMA CAVEAT: 43001-43006 are *reserved* in Buzz with no upstream builder; the tag
 * layout is Quartz's best-effort model (see [JobRequestEvent]).
 */
object BuzzJobCommands {
    private val USAGE =
        """
        |amy buzz job request RELAY <text>            file a job (kind-43001)
        |    [--agent PUBKEY] [--channel GID]           target agent (p) / channel scope (h)
        |amy buzz job list RELAY [--channel GID]       list jobs and their state
        |    [--mine|--assigned] [--limit N] [--timeout SECS]
        |amy buzz job show RELAY JOBID [--timeout SECS] show one job's full lifecycle
        |amy buzz job cancel RELAY JOBID [--reason R]  cancel a job (kind-43005)
        |    [--channel GID]
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "buzz job",
            tail,
            USAGE,
            mapOf(
                "request" to { rest -> request(dataDir, rest) },
                "list" to { rest -> list(dataDir, rest) },
                "show" to { rest -> show(dataDir, rest) },
                "cancel" to { rest -> cancel(dataDir, rest) },
            ),
        )

    /** `buzz job request RELAY <text> [--agent PUBKEY] [--channel GID]` → publishes a kind-43001. */
    private suspend fun request(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz job request RELAY <text> [--agent PUBKEY] [--channel GID]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val text = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        if (text.isBlank()) return Output.error("bad_args", "job text must not be blank")
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val channel = args.flag("channel")
        val agent =
            args.flag("agent")?.let {
                decodePublicKeyAsHexOrNull(it.trim())?.takeIf { hex -> hex.isValid() }
                    ?: return Output.error("bad_args", "invalid agent public key (npub or 64-char hex): $it")
            }
        args.rejectUnknown("agent", "channel")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val signed = ctx.signer.sign(JobRequestEvent.build(text, channel, agent))
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "job_id" to signed.id,
                    "kind" to signed.kind,
                    "relay" to relay.url,
                    "agent" to agent,
                    "channel" to channel,
                    "published" to ack.values.any { it.accepted },
                ),
            )
            return 0
        }
    }

    /**
     * `buzz job list RELAY [--channel GID] [--mine|--assigned] [--limit N] [--timeout SECS]` →
     * drains the job kinds and folds them into per-job state. `--mine` keeps jobs I
     * requested; `--assigned` keeps jobs targeting me; default shows both.
     */
    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz job list RELAY [--channel GID] [--mine|--assigned] [--limit N] [--timeout SECS]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val channel = args.flag("channel")
        val mine = args.bool("mine")
        val assigned = args.bool("assigned")
        val limit = args.flag("limit")?.toIntOrNull() ?: 50
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 8
        args.rejectUnknown("channel", "mine", "assigned", "limit", "timeout")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val me = ctx.identity.pubKeyHex
            val jobs =
                fetchJobs(ctx, relay, channel, timeoutSecs)
                    .filter { job ->
                        when {
                            mine && !assigned -> job.requester == me
                            assigned && !mine -> job.agent == me
                            else -> true
                        }
                    }.take(limit)
            Output.emit(mapOf("relay" to relay.url, "count" to jobs.size, "jobs" to jobs.map { it.toRow() }))
            return 0
        }
    }

    /** `buzz job show RELAY JOBID [--timeout SECS]` → the full folded lifecycle of one job. */
    private suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz job show RELAY JOBID [--timeout SECS]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val jobId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 8
        args.rejectUnknown("timeout")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            // Fetch the request by id, every reply that references it via `e`, and every
            // upvote (kind-7 reaction) targeting it.
            val filters =
                listOf(
                    Filter(kinds = JOB_KINDS, ids = listOf(jobId)),
                    Filter(kinds = JOB_REPLY_KINDS + ReactionEvent.KIND, tags = mapOf("e" to listOf(jobId))),
                )
            val events =
                ctx
                    .drain(mapOf(relay to filters), timeoutSecs * 1000, pendingOnAuthRequired = true)
                    .map { it.second }
            val job =
                BuzzJobAggregator.aggregate(events).firstOrNull { it.jobId == jobId }
                    ?: return Output.error("not_found", "no job $jobId on ${relay.url}")
            Output.emit(job.toRow() + mapOf("relay" to relay.url))
            return 0
        }
    }

    /** `buzz job cancel RELAY JOBID [--reason R] [--channel GID]` → publishes a kind-43005. */
    private suspend fun cancel(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz job cancel RELAY JOBID [--reason R] [--channel GID]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val jobId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val reason = args.flag("reason") ?: ""
        val channel = args.flag("channel")
        args.rejectUnknown("reason", "channel")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val signed = ctx.signer.sign(JobCancelEvent.build(jobId, reason, channel))
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "job_id" to jobId,
                    "relay" to relay.url,
                    "published" to ack.values.any { it.accepted },
                ),
            )
            return 0
        }
    }

    /**
     * Drain every job kind (optionally channel-scoped) plus, when a channel is given, its
     * kind-7 upvotes, and fold via the shared aggregator. Upvotes are only fetched with a
     * channel scope — a bare kind-7 query would pull the relay's entire reaction firehose.
     */
    internal suspend fun fetchJobs(
        ctx: Context,
        relay: NormalizedRelayUrl,
        channel: String?,
        timeoutSecs: Long,
    ): List<JobView> {
        val tags = channel?.let { mapOf("h" to listOf(it)) }
        val filters =
            buildList {
                add(Filter(kinds = JOB_KINDS, tags = tags))
                if (channel != null) add(Filter(kinds = listOf(ReactionEvent.KIND), tags = tags))
            }
        val events =
            ctx
                .drain(mapOf(relay to filters), timeoutSecs * 1000, pendingOnAuthRequired = true)
                .map { it.second }
        return BuzzJobAggregator.aggregate(events)
    }

    private fun JobView.toRow(): Map<String, Any?> =
        mapOf(
            "job_id" to jobId,
            "state" to state.name.lowercase(),
            "requester" to requester,
            "agent" to agent,
            "channel" to channel,
            "request" to request,
            "upvotes" to upvotes,
            "progress_updates" to progressUpdates,
            "last_progress" to lastProgress,
            "result" to result,
            "error" to error,
            "cancel_reason" to cancelReason,
            "created_at" to createdAt,
            "updated_at" to updatedAt,
        )

    internal val JOB_KINDS = (43001..43006).toList()
    internal val JOB_REPLY_KINDS = (43002..43006).toList()

    /** The terminal states — jobs a responder should never re-handle. */
    internal val TERMINAL = setOf(JobState.COMPLETED, JobState.FAILED, JobState.CANCELLED)
}
