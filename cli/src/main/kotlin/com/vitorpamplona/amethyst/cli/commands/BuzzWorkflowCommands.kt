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
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRun
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunAggregator
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunPayload
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunState
import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCancelledEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggeredEvent
import com.vitorpamplona.quartz.buzz.workflow.workflowChannel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * `amy buzz workflow …` — Buzz's **source-confirmed** structured-work + human-approval primitive
 * (kinds 30620 def, 46020 trigger, 46010 approval-requested, 46030/46031 grant/deny, 46001-46007
 * lifecycle), replacing the speculative agent-job protocol (43001-43006).
 *
 * On a real Buzz relay the RELAY parses the workflow YAML, runs the steps, and signs the
 * lifecycle + approval events. Self-hosted on geode there is no workflow engine, so **`amy` is
 * the runner** (`workflow run`) and emits the lifecycle events itself — a documented divergence.
 * The approval gate is faithful: the runner does the agent's work, pauses on a 46010 (addressed to
 * an approver), and only pushes/opens the PR after a human publishes a 46030 grant (46031 = deny).
 *
 * Correlation is simple: the **run id is the trigger's event id, and it doubles as the approval
 * token**, so an `ApprovalGrant`'s `d` tag equals the run id. Run/step folding is the shared
 * [WorkflowRunAggregator] in `commons`.
 */
object BuzzWorkflowCommands {
    private val json = Json { ignoreUnknownKeys = true }

    private val USAGE =
        """
        |amy buzz workflow trigger RELAY WFID --task TEXT --channel GID   start a run (kind-46020)
        |amy buzz workflow list RELAY --channel GID [--timeout SECS]      list runs + their state
        |amy buzz workflow show RELAY RUNID [--timeout SECS]             one run's lifecycle
        |amy buzz workflow approve RELAY RUNID [--note N]                grant the approval gate (46030)
        |amy buzz workflow deny RELAY RUNID [--note N]                   deny the approval gate (46031)
        |amy buzz workflow run RELAY --exec CMD --channel GID            run the workflow runner
        |    --approver NPUB [--on-approve CMD] [--worktree REPODIR]        agent work → 46010 gate →
        |    [--base-ref REF] [--accept-from npub,…] [--poll SECS] [--once] on grant: --on-approve → 46005
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "buzz workflow",
            tail,
            USAGE,
            mapOf(
                "trigger" to { rest -> trigger(dataDir, rest) },
                "list" to { rest -> list(dataDir, rest) },
                "show" to { rest -> show(dataDir, rest) },
                "approve" to { rest -> decide(dataDir, rest, grant = true) },
                "deny" to { rest -> decide(dataDir, rest, grant = false) },
                "run" to { rest -> run(dataDir, rest) },
            ),
        )

    // ---- requester side ------------------------------------------------------

    /** `workflow trigger RELAY WFID --task TEXT --channel GID` → publishes a kind-46020. */
    private suspend fun trigger(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz workflow trigger RELAY WFID --task TEXT --channel GID"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val wfId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val task = args.flag("task")?.takeIf { it.isNotBlank() } ?: return Output.error("bad_args", "pass --task TEXT")
        val channel = args.flag("channel") ?: return Output.error("bad_args", "pass --channel GID")
        args.rejectUnknown("task", "channel")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val content = json.encodeToString(WorkflowRunPayload(task = task, workflow = wfId))
            val signed = ctx.signer.sign(WorkflowTriggerEvent.build(wfId, content) { workflowChannel(channel) })
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "run_id" to signed.id, // the trigger id IS the run id and the approval token
                    "workflow" to wfId,
                    "channel" to channel,
                    "relay" to relay.url,
                    "published" to ack.values.any { it.accepted },
                ),
            )
            return 0
        }
    }

    /** `workflow approve|deny RELAY RUNID [--note N]` → publishes a kind-46030/46031 with `d` = run id. */
    private suspend fun decide(
        dataDir: DataDir,
        rest: Array<String>,
        grant: Boolean,
    ): Int {
        val args = Args(rest)
        val verb = if (grant) "approve" else "deny"
        val usage = "buzz workflow $verb RELAY RUNID [--note N]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val runId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val note = args.flag("note") ?: ""
        args.rejectUnknown("note")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val template = if (grant) ApprovalGrantEvent.build(runId, note) else ApprovalDenyEvent.build(runId, note)
            val signed = ctx.signer.sign(template)
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(mapOf("event_id" to signed.id, "kind" to signed.kind, "run_id" to runId, "decision" to verb, "published" to ack.values.any { it.accepted }))
            return 0
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz workflow list RELAY --channel GID [--timeout SECS]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val channel = args.flag("channel") ?: return Output.error("bad_args", "pass --channel GID")
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 8
        args.rejectUnknown("channel", "timeout")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val runs = WorkflowRunAggregator.byPriority(fetchRuns(ctx, relay, channel, timeoutSecs))
            Output.emit(mapOf("relay" to relay.url, "count" to runs.size, "runs" to runs.map { it.toRow() }))
            return 0
        }
    }

    private suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz workflow show RELAY RUNID [--timeout SECS]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val runId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 8
        args.rejectUnknown("timeout")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            // The run id is the trigger's id; the lifecycle events carry the run id only in their JSON
            // `content` (+ an `h` channel tag), so we can't query them by `#e`/`#d`. Resolve the channel
            // from the trigger, then fold the whole channel — the same path `list` takes.
            val trigger =
                ctx
                    .drain(mapOf(relay to listOf(Filter(kinds = listOf(WorkflowTriggerEvent.KIND), ids = listOf(runId)))), timeoutSecs * 1000, pendingOnAuthRequired = true)
                    .map { it.second }
                    .filterIsInstance<WorkflowTriggerEvent>()
                    .firstOrNull { it.id == runId }
                    ?: return Output.error("not_found", "no workflow trigger $runId on ${relay.url}")
            val channel = trigger.tags.workflowChannel() ?: return Output.error("not_found", "trigger $runId has no channel")
            val run =
                fetchRuns(ctx, relay, channel, timeoutSecs).firstOrNull { it.runId == runId }
                    ?: return Output.error("not_found", "no run $runId on ${relay.url}")
            Output.emit(run.toRow() + mapOf("relay" to relay.url))
            return 0
        }
    }

    /**
     * All workflow events on a channel, folded into runs. Two-phase: the trigger + lifecycle events
     * are scoped by the channel `h` tag, but the approval grant/deny events (46030/46031) reference
     * their run only by a `d` tag (= the token = the run id) — and quartz's shared event store routes
     * every `#d` filter to the addressable `d_tag` column, which is NULL for a regular kind like
     * 46030, so they can't be fetched by `#d`. Instead we fetch them by **author** — every 46010 gate
     * names its approver in a `p` tag, so those are exactly the keys that can sign a decision — and
     * the aggregator matches each decision to its run by the `d`-tag token it reads off the event.
     */
    private suspend fun fetchRuns(
        ctx: Context,
        relay: NormalizedRelayUrl,
        channel: String,
        timeoutSecs: Long,
    ): List<WorkflowRun> {
        val base =
            ctx
                .drain(mapOf(relay to listOf(Filter(kinds = listOf(WorkflowTriggerEvent.KIND) + LIFECYCLE_KINDS, tags = mapOf("h" to listOf(channel))))), timeoutSecs * 1000, pendingOnAuthRequired = true)
                .map { it.second }
        val approvers =
            base
                .filterIsInstance<WorkflowApprovalRequestedEvent>()
                .mapNotNull { it.approver() }
                .distinct()
        val decisions =
            if (approvers.isEmpty()) {
                emptyList()
            } else {
                ctx
                    .drain(mapOf(relay to listOf(Filter(kinds = DECISION_KINDS, authors = approvers))), timeoutSecs * 1000, pendingOnAuthRequired = true)
                    .map { it.second }
            }
        return WorkflowRunAggregator.aggregate(base + decisions)
    }

    private fun WorkflowRun.toRow(): Map<String, Any?> =
        mapOf(
            "run_id" to runId,
            "state" to state.name.lowercase(),
            "workflow" to workflowId,
            "channel" to channel,
            "task" to task,
            "requester" to requester,
            "pending_approver" to pendingApprover,
            "approval_token" to if (state == WorkflowRunState.AWAITING_APPROVAL) approvalToken else null,
            "result" to result,
            "error" to error,
            "last_step" to lastStep,
            "created_at" to createdAt,
            "updated_at" to updatedAt,
        )

    // ---- runner --------------------------------------------------------------

    private class AwaitingRun(
        val channel: String,
        val requester: HexKey?,
        val worktree: String?,
        val branch: String?,
    )

    /** The worktree path/branch for a run are deterministic from its id, so a restarted runner
     *  (or a `--once` resolve pass in a fresh process) can rebuild the [AwaitingRun] it lost. */
    private fun worktreeDirFor(runId: HexKey): File = File(System.getProperty("java.io.tmpdir"), "buzz-runs/${runId.take(12)}")

    private fun branchFor(runId: HexKey): String = "claude/run-${runId.take(12)}"

    private suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", USAGE)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val exec = args.flag("exec") ?: return Output.error("bad_args", "pass --exec CMD (the agent's work step)")
        val channel = args.flag("channel") ?: return Output.error("bad_args", "pass --channel GID")
        val approverInput = args.flag("approver") ?: return Output.error("bad_args", "pass --approver NPUB (who signs off the gate)")
        val approver =
            decodePublicKeyAsHexOrNull(approverInput.trim())?.takeIf { it.isValid() }
                ?: return Output.error("bad_args", "invalid --approver key: $approverInput")
        val onApprove = args.flag("on-approve") // push + open PR; runs in the worktree after grant
        val worktreeBase = args.flag("worktree")
        val baseRef = args.flag("base-ref") ?: "HEAD"
        val once = args.bool("once")
        val pollSecs = args.flag("poll")?.toLongOrNull() ?: 5
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 8
        val acceptFrom =
            args
                .flag("accept-from")
                ?.split(",")
                ?.mapNotNull { it.trim().ifBlank { null } }
                ?.map {
                    decodePublicKeyAsHexOrNull(it)?.takeIf { hex -> hex.isValid() } ?: return Output.error("bad_args", "invalid --accept-from key: $it")
                }?.toSet()
        args.rejectUnknown("exec", "channel", "approver", "on-approve", "worktree", "base-ref", "once", "poll", "timeout", "accept-from")

        if (worktreeBase != null && !File(worktreeBase).isDirectory) return Output.error("bad_args", "--worktree is not a directory: $worktreeBase")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val me = ctx.identity.pubKeyHex
            val started = mutableSetOf<HexKey>() // triggers we've begun
            val awaiting = mutableMapOf<HexKey, AwaitingRun>() // runId -> worktree while at the gate
            val decided = mutableSetOf<HexKey>()

            // Seed from existing runs so a restart doesn't re-run finished work. A run the runner has
            // already carried to a terminal outcome (COMPLETED/FAILED/CANCELLED) is done. A run still
            // needing the runner to act — parked at the gate (AWAITING_APPROVAL), granted-but-not-yet
            // -shipped (APPROVED), or denied-but-its-worktree-still-around (DENIED) — is rebuilt into
            // `awaiting` from its run id (the worktree/branch are deterministic) so a decision arriving
            // in a later poll (or a fresh `--once` process) still resolves. The in-memory `awaiting`
            // map is a cache, not the source of truth — the relay is.
            fetchRuns(ctx, relay, channel, timeoutSecs).forEach { runv ->
                if (runv.state != WorkflowRunState.TRIGGERED) started.add(runv.runId)
                val ch = runv.channel
                when (runv.state) {
                    WorkflowRunState.COMPLETED, WorkflowRunState.FAILED, WorkflowRunState.CANCELLED ->
                        decided.add(runv.runId)
                    WorkflowRunState.AWAITING_APPROVAL, WorkflowRunState.APPROVED, WorkflowRunState.DENIED ->
                        if (ch != null) {
                            awaiting[runv.runId] =
                                AwaitingRun(
                                    channel = ch,
                                    requester = runv.requester,
                                    worktree = worktreeBase?.let { worktreeDirFor(runv.runId).absolutePath },
                                    branch = worktreeBase?.let { branchFor(runv.runId) },
                                )
                        }
                    WorkflowRunState.TRIGGERED, WorkflowRunState.RUNNING -> Unit
                }
            }

            if (!once) {
                Output.emit(mapOf("running" to me, "relay" to relay.url, "channel" to channel, "approver" to approver, "seeded" to started.size))
                System.err.println("[workflow] runner up on ${relay.url} #$channel — gate → $approver — Ctrl-C to stop")
            }

            val summary = mutableListOf<Map<String, Any?>>()
            while (true) {
                // 1. Start new triggers (agent work → 46010 gate).
                fetchRuns(ctx, relay, channel, timeoutSecs)
                    .filter { it.state == WorkflowRunState.TRIGGERED && it.runId !in started }
                    .filter { acceptFrom == null || it.requester in acceptFrom }
                    .forEach { runv ->
                        started.add(runv.runId)
                        val a = startRun(ctx, relay, me, runv, exec, approver, worktreeBase, baseRef, timeoutSecs)
                        if (a != null) awaiting[runv.runId] = a else decided.add(runv.runId)
                        summary.add(mapOf("run_id" to runv.runId, "stage" to if (a != null) "awaiting_approval" else "failed"))
                    }

                // 2. Resolve gates whose decision has arrived. Grants/denies reference the run only by
                //    a `d`-tag token, which quartz's store can't serve via `#d` on a regular kind, so we
                //    fetch the approver's decisions by author and match the token to a run at the gate.
                val decisions =
                    if (awaiting.isEmpty()) {
                        emptyList()
                    } else {
                        ctx.drain(mapOf(relay to listOf(Filter(kinds = DECISION_KINDS, authors = listOf(approver)))), timeoutSecs * 1000, pendingOnAuthRequired = true).map { it.second }
                    }
                decisions.forEach { d ->
                    val (runId, granted) =
                        when (d) {
                            is ApprovalGrantEvent -> (d.tokenHash() ?: return@forEach) to true
                            is ApprovalDenyEvent -> (d.tokenHash() ?: return@forEach) to false
                            else -> return@forEach
                        }
                    val a = awaiting[runId] ?: return@forEach
                    if (runId in decided) return@forEach
                    decided.add(runId)
                    awaiting.remove(runId)
                    resolve(ctx, relay, runId, a, granted, onApprove, worktreeBase, timeoutSecs)
                    summary.add(mapOf("run_id" to runId, "stage" to if (granted) "completed" else "denied"))
                    if (!once) System.err.println("[workflow] ${if (granted) "granted → shipped" else "denied"} run ${runId.take(12)}…")
                }

                if (once) {
                    Output.emit(mapOf("relay" to relay.url, "handled" to summary.size, "runs" to summary))
                    return 0
                }
                delay(pollSecs * 1000)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return 0
    }

    /** Emit 46001/46002, run --exec in a worktree, emit 46003 + the 46010 gate. Null if the work failed. */
    private suspend fun startRun(
        ctx: Context,
        relay: NormalizedRelayUrl,
        me: HexKey,
        runv: WorkflowRun,
        exec: String,
        approver: HexKey,
        worktreeBase: String?,
        baseRef: String,
        timeoutSecs: Long,
    ): AwaitingRun? {
        val channel = runv.channel ?: return null
        val runId = runv.runId
        emit(ctx, relay, WorkflowTriggeredEvent.build(channel, payload(WorkflowRunPayload(run = runId, workflow = runv.workflowId, task = runv.task))))
        emit(ctx, relay, WorkflowStepStartedEvent.build(channel, payload(WorkflowRunPayload(run = runId, step = "build"))))

        var workdir: String? = null
        var branch: String? = null
        if (worktreeBase != null) {
            branch = branchFor(runId)
            val wt = worktreeDirFor(runId)
            wt.parentFile?.mkdirs()
            git(worktreeBase, "worktree", "prune")
            wt.deleteRecursively()
            val add = git(worktreeBase, "worktree", "add", "-B", branch, wt.absolutePath, baseRef)
            if (add.exit != 0) {
                emit(ctx, relay, WorkflowFailedEvent.build(channel, payload(WorkflowRunPayload(run = runId, error = "worktree: ${add.err.take(500)}"))))
                return null
            }
            workdir = wt.absolutePath
        }

        val env =
            buildMap {
                put("BUZZ_RUN", runId)
                put("BUZZ_CHANNEL", channel)
                put("BUZZ_RELAY", relay.url)
                put("BUZZ_AGENT", me)
                runv.requester?.let { put("BUZZ_REQUESTER", it) }
                branch?.let { put("BUZZ_BRANCH", it) }
                workdir?.let { put("BUZZ_WORKTREE", it) }
                put("BUZZ_BASE_REF", baseRef)
            }
        val work = exec(exec, runv.task ?: "", env, workdir)
        if (work.exit != 0) {
            emit(ctx, relay, WorkflowFailedEvent.build(channel, payload(WorkflowRunPayload(run = runId, error = (work.err.ifBlank { work.out }).take(MAX)))))
            return null
        }
        emit(ctx, relay, WorkflowStepCompletedEvent.build(channel, payload(WorkflowRunPayload(run = runId, step = "build"))))
        // Pause on the approval gate — a human reviews the work before it ships.
        emit(ctx, relay, WorkflowApprovalRequestedEvent.build(channel, approver, payload(WorkflowRunPayload(run = runId, note = work.out.take(1000)))))
        return AwaitingRun(channel, runv.requester, workdir, branch)
    }

    /** On grant: run --on-approve (push + PR) and emit 46005 with its output. On deny: just discard. */
    private suspend fun resolve(
        ctx: Context,
        relay: NormalizedRelayUrl,
        runId: HexKey,
        a: AwaitingRun,
        granted: Boolean,
        onApprove: String?,
        worktreeBase: String?,
        timeoutSecs: Long,
    ) {
        try {
            // A deny (46031) is itself the terminal signal — the aggregator folds it to DENIED — so the
            // runner only discards the unshipped work (the `finally` removes the worktree); emitting a
            // competing 46007 cancelled would just race the deny for "newest terminal".
            if (!granted) return
            val prUrl =
                if (onApprove != null) {
                    val env =
                        buildMap {
                            put("BUZZ_RUN", runId)
                            a.branch?.let { put("BUZZ_BRANCH", it) }
                            a.worktree?.let { put("BUZZ_WORKTREE", it) }
                        }
                    val r = exec(onApprove, "", env, a.worktree)
                    if (r.exit != 0) {
                        emit(ctx, relay, WorkflowFailedEvent.build(a.channel, payload(WorkflowRunPayload(run = runId, error = "on-approve: ${(r.err.ifBlank { r.out }).take(MAX)}"))))
                        return
                    }
                    r.out.trim().takeIf { it.isNotBlank() }
                } else {
                    null
                }
            emit(ctx, relay, WorkflowCompletedEvent.build(a.channel, payload(WorkflowRunPayload(run = runId, pr = prUrl))))
        } finally {
            // The worktree lives under the runner's tmpdir but *belongs* to the repo at worktreeBase,
            // so `git worktree remove` must run against that repo, not the worktree's own parent path.
            if (a.worktree != null && worktreeBase != null) {
                git(worktreeBase, "worktree", "remove", "--force", a.worktree)
                git(worktreeBase, "worktree", "prune")
            }
        }
    }

    // ---- helpers -------------------------------------------------------------

    private fun payload(p: WorkflowRunPayload): String = json.encodeToString(p)

    private suspend fun emit(
        ctx: Context,
        relay: NormalizedRelayUrl,
        template: EventTemplate<out Event>,
    ) {
        val signed = ctx.signer.sign(template)
        ctx.publish(signed, setOf(relay))
    }

    private class ExecResult(
        val exit: Int,
        val out: String,
        val err: String,
    )

    private suspend fun exec(
        cmd: String,
        input: String,
        env: Map<String, String>,
        workdir: String?,
    ): ExecResult =
        withContext(Dispatchers.IO) {
            val pb = ProcessBuilder("sh", "-c", cmd)
            workdir?.let { pb.directory(File(it)) }
            pb.environment().putAll(env)
            val proc = pb.start()
            coroutineScope {
                val out = async { proc.inputStream.readBytes().decodeToString() }
                val err = async { proc.errorStream.readBytes().decodeToString() }
                // Feed the task on stdin, but a command that never reads it (e.g. `printf …`) exits
                // and closes the pipe first — tolerate the resulting broken pipe rather than abort.
                runCatching { proc.outputStream.use { it.write(input.encodeToByteArray()) } }
                proc.waitFor()
                ExecResult(proc.exitValue(), out.await(), err.await())
            }
        }

    private suspend fun git(
        dir: String,
        vararg gitArgs: String,
    ): ExecResult =
        withContext(Dispatchers.IO) {
            val proc = ProcessBuilder(listOf("git", "-C", dir) + gitArgs).start()
            coroutineScope {
                val out = async { proc.inputStream.readBytes().decodeToString() }
                val err = async { proc.errorStream.readBytes().decodeToString() }
                proc.waitFor()
                ExecResult(proc.exitValue(), out.await(), err.await())
            }
        }

    private const val MAX = 60_000
    private val LIFECYCLE_KINDS =
        listOf(
            WorkflowTriggeredEvent.KIND,
            WorkflowStepStartedEvent.KIND,
            WorkflowStepCompletedEvent.KIND,
            WorkflowApprovalRequestedEvent.KIND,
            WorkflowCompletedEvent.KIND,
            WorkflowFailedEvent.KIND,
            WorkflowCancelledEvent.KIND,
        )
    private val DECISION_KINDS = listOf(ApprovalGrantEvent.KIND, ApprovalDenyEvent.KIND)
}
