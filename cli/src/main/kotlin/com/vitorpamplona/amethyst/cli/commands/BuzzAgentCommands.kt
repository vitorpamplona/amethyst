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
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * `amy buzz agent …` — the AGENT side of the Buzz job protocol: a headless SCHEDULER that
 * manages a shared backlog by itself. It watches a channel's job requests (kind-43001),
 * orders them by the group's upvotes, and runs up to `--parallel N` at a time — each in its
 * own git worktree/branch so concurrent runs never clobber each other — reporting every step
 * back as accept/progress/result/error events (43002/43003/43004/43006) the whole room sees.
 *
 * This is the "shared channel where the team drives an AI to build Amethyst" model:
 * **anyone in the channel files a job, the bot works them autonomously in parallel, and the
 * only human gate left is the merge — which happens on GitHub, never here.** Point `--exec`
 * at a coding agent (Claude Code via buzz-acp, a Goose/Codex wrapper, or a script): the job's
 * task text is piped to its stdin, and it runs inside a fresh worktree whose branch is
 * exported as `BUZZ_BRANCH`. The agent commits + pushes that branch and opens the PR; its
 * stdout (e.g. the PR URL) becomes the job result.
 *
 * PERMISSIONS — Buzz authorizes by identity, not capability flags, so this is only as safe as:
 *   1. INTAKE — `--accept-from` / `--accept-from-channel` gate WHO the bot obeys (the channel
 *      roster). Without either, it answers anyone who can post to the relay.
 *   2. BLAST RADIUS — what `--exec` can DO to a repo is bounded by the credentials you give it,
 *      NOT by Buzz. Each job gets its own branch off `--base-ref`; the exec's git token should
 *      only open PRs on feature branches (never merge, never force-push), and `main` must be
 *      branch-protected. See `cli/plans/2026-07-25-buzz-agent-support-channel.md`.
 *
 * SCHEMA CAVEAT: kinds 43001-43006 are *reserved* in Buzz with no upstream builder; see
 * [com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent].
 */
object BuzzAgentCommands {
    private val USAGE =
        """
        |amy buzz agent up RELAY --repo DIR --approver NPUB   one-command gated runner (recommended)
        |    [--channel GID]                             defaults to the relay's only channel
        |    [--base-ref REF] [--poll SECS] [--once]     bundled wrapper; intake = channel members
        |amy buzz agent doctor [--repo DIR] [--json]     preflight: gh token scope + branch protection
        |amy buzz agent serve RELAY --exec CMD         run a backlog scheduler
        |    [--channel GID]                             only handle jobs scoped to this channel
        |    [--accept-from npub,npub]                   allowlist of requester keys
        |    [--accept-from-channel]                     obey any member of --channel (kind-39002 roster)
        |    [--claim-untargeted]                        also handle jobs with no `p` target
        |    [--parallel N]                              run up to N jobs at once (default 1)
        |    [--worktree REPODIR]                        base git repo; each job gets its own worktree+branch
        |    [--base-ref REF]                            branch base for worktrees (default HEAD)
        |    [--branch-prefix P]                         job branch prefix (default claude/job-)
        |    [--poll SECS]                               poll interval (default 5)
        |    [--exec-timeout SECS]                       kill --exec after N seconds (default 1800; 0 = none)
        |    [--timeout SECS]                            per-fetch relay timeout (default 8)
        |    [--no-progress] [--dry-run] [--once]
        """.trimMargin()

    private val worktreeMutex = Mutex() // git worktree add/remove touch shared repo metadata

    // (worktreePath, branch) for jobs currently executing — a JVM shutdown hook force-removes
    // these on Ctrl-C/kill, when coroutine `finally` blocks don't run.
    private val activeWorktrees = Collections.synchronizedSet(mutableSetOf<Pair<String, String>>())

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "buzz agent",
            tail,
            USAGE,
            mapOf(
                "up" to { rest -> up(dataDir, rest) },
                "doctor" to { rest -> doctor(dataDir, rest) },
                "serve" to { rest -> serve(dataDir, rest) },
            ),
        )

    // ---- one-command bundles -------------------------------------------------

    /**
     * `amy buzz agent up RELAY --repo DIR --approver NPUB [--channel GID] …` — the low-ceremony way to
     * put a **gated** agent runner on a channel. It resolves the channel (the relay's only one unless
     * `--channel` is given), defaults the worktree to `--repo` and intake to the channel roster,
     * extracts the bundled agent/ship wrappers, and hands off to `buzz workflow run`. Everything it
     * defaults stays overridable there; this just removes the eight flags and the two scripts for the
     * common case. The one thing it can't default is `--approver` — a human must own the gate.
     */
    private suspend fun up(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz agent up RELAY --repo DIR --approver NPUB [--channel GID] [--base-ref REF] [--poll SECS] [--once]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val repo = args.flag("repo") ?: return Output.error("bad_args", "pass --repo DIR (your git checkout — the agent works and opens PRs here)")
        if (!File(repo).resolve(".git").exists()) return Output.error("bad_args", "--repo is not a git repository: $repo")
        val approver = args.flag("approver") ?: return Output.error("bad_args", "pass --approver NPUB (the human who signs off each run)")
        val baseRef = args.flag("base-ref")
        val poll = args.flag("poll")
        val timeout = args.flag("timeout")
        val once = args.bool("once")
        val explicitChannel = args.flag("channel")
        args.rejectUnknown("repo", "approver", "channel", "base-ref", "poll", "timeout", "once")

        val channel =
            explicitChannel
                ?: Context.open(dataDir).use { ctx ->
                    ctx.prepare()
                    resolveSingleChannel(ctx, relay, timeout?.toLongOrNull() ?: 8)
                        ?: return Output.error("bad_args", "couldn't pick a channel automatically — pass --channel GID (this relay hosts none or several)")
                }

        val (agentStep, shipStep) = extractWrappers()

        val runArgs =
            buildList {
                add(relayUrl)
                add("--channel")
                add(channel)
                add("--approver")
                add(approver)
                add("--exec")
                add(agentStep)
                add("--on-approve")
                add(shipStep)
                add("--worktree")
                add(repo)
                add("--accept-from-channel")
                baseRef?.let {
                    add("--base-ref")
                    add(it)
                }
                poll?.let {
                    add("--poll")
                    add(it)
                }
                timeout?.let {
                    add("--timeout")
                    add(it)
                }
                if (once) add("--once")
            }.toTypedArray()

        System.err.println("[agent up] gated runner on ${relay.url} #$channel — repo $repo — approver $approver")
        System.err.println("[agent up] wrappers: $agentStep + $shipStep (edit to customize, or re-run with your own --exec/--on-approve)")
        return BuzzWorkflowCommands.runFromArgs(dataDir, runArgs)
    }

    /** The relay's single hosted channel (its 39000 group id), or null when there are zero or many. */
    private suspend fun resolveSingleChannel(
        ctx: Context,
        relay: NormalizedRelayUrl,
        timeoutSecs: Long,
    ): String? =
        ctx
            .drain(mapOf(relay to listOf(Filter(kinds = listOf(GroupMetadataEvent.KIND)))), timeoutSecs * 1000, pendingOnAuthRequired = true)
            .map { it.second }
            .filterIsInstance<GroupMetadataEvent>()
            .mapNotNull { it.groupId() }
            .distinct()
            .singleOrNull()

    /** Extract the bundled gated wrappers to ~/.amy/buzz-agent and return (agentStepPath, shipStepPath). */
    private fun extractWrappers(): Pair<String, String> {
        val dir = File(System.getProperty("user.home"), ".amy/buzz-agent").apply { mkdirs() }

        fun extract(name: String): String {
            val out = File(dir, name)
            (
                BuzzAgentCommands::class.java.getResourceAsStream("/buzz-agent/$name")
                    ?: error("bundled wrapper /buzz-agent/$name missing from the amy jar")
            ).use { input -> out.outputStream().use { input.copyTo(it) } }
            out.setExecutable(true)
            return out.absolutePath
        }
        return extract("workflow-agent.sh") to extract("workflow-ship.sh")
    }

    /**
     * `amy buzz agent doctor [--repo DIR]` — preflight the host safety the gate relies on: `gh` is
     * authenticated and its token can write to the repo, the default branch is protected against
     * force-push, and the worktree is a clean git checkout. Turns the tutorial's security checklist
     * into a green/red report; exits non-zero if anything is off. Honours `--json` like every verb.
     */
    private suspend fun doctor(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val repo = args.flag("repo") ?: System.getProperty("user.dir")
        args.rejectUnknown("repo")

        val checks = mutableListOf<Map<String, Any?>>()

        fun check(
            name: String,
            ok: Boolean,
            detail: String,
        ) = checks.add(mapOf("name" to name, "ok" to ok, "detail" to detail))

        val isRepo = File(repo).resolve(".git").exists()
        check("git repo", isRepo, if (isRepo) repo else "$repo is not a git checkout")
        if (isRepo) {
            val status = git(repo, "status", "--porcelain")
            check("worktree clean", status.stdout.isBlank(), if (status.stdout.isBlank()) "no uncommitted changes" else "uncommitted changes present")
        }

        val ghAuth = runExec("gh auth status", "", emptyMap(), 20, repo)
        val ghOk = ghAuth.exit == 0
        check("gh authenticated", ghOk, if (ghOk) "ok" else "run: gh auth login")

        if (ghOk) {
            val perm = runExec("gh repo view --json viewerPermission -q .viewerPermission", "", emptyMap(), 20, repo).stdout.trim()
            val canWrite = perm == "WRITE" || perm == "MAINTAIN" || perm == "ADMIN"
            check("token can write to repo", canWrite, if (canWrite) "permission: $perm" else "permission: ${perm.ifBlank { "unknown" }} — needs Contents:RW + Pull requests:RW on this repo")

            val def = runExec("gh repo view --json defaultBranchRef -q .defaultBranchRef.name", "", emptyMap(), 20, repo).stdout.trim().ifBlank { "main" }
            val prot = runExec("gh api repos/{owner}/{repo}/branches/$def/protection --jq .allow_force_pushes.enabled", "", emptyMap(), 20, repo)
            val isProtected = prot.exit == 0
            val forcePushOff = prot.stdout.trim() == "false"
            check(
                "default branch protected ($def)",
                isProtected && forcePushOff,
                when {
                    !isProtected -> "'$def' has no branch protection — require a PR + reviews and block force-push"
                    !forcePushOff -> "'$def' allows force-push — disable it in branch protection"
                    else -> "protected; force-push blocked"
                },
            )
        }

        val allOk = checks.all { it["ok"] == true }
        Output.emit(mapOf("ok" to allOk, "repo" to repo, "checks" to checks))
        return if (allOk) 0 else 1
    }

    private class Opts(
        val relay: NormalizedRelayUrl,
        val exec: String?,
        val channel: String?,
        val claimUntargeted: Boolean,
        val postProgress: Boolean,
        val dryRun: Boolean,
        val parallel: Int,
        val pollSecs: Long,
        val timeoutSecs: Long,
        val execTimeoutSecs: Long,
        val worktreeBase: String?,
        val baseRef: String,
        val branchPrefix: String,
    )

    private suspend fun serve(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", USAGE)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val dryRun = args.bool("dry-run")
        val exec = args.flag("exec")
        if (exec == null && !dryRun) return Output.error("bad_args", "pass --exec CMD (or --dry-run to test without running anything)")
        val channel = args.flag("channel")
        val claimUntargeted = args.bool("claim-untargeted")
        val postProgress = !args.bool("no-progress")
        val once = args.bool("once")
        val parallel = args.flag("parallel")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val pollSecs = args.flag("poll")?.toLongOrNull() ?: 5
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 8
        val execTimeoutSecs = args.flag("exec-timeout")?.toLongOrNull() ?: 1800
        val worktreeBase = args.flag("worktree")
        val baseRef = args.flag("base-ref") ?: "HEAD"
        val branchPrefix = args.flag("branch-prefix") ?: "claude/job-"
        val fromChannel = args.bool("accept-from-channel")
        val acceptFrom =
            args
                .flag("accept-from")
                ?.split(",")
                ?.mapNotNull { it.trim().ifBlank { null } }
                ?.map {
                    decodePublicKeyAsHexOrNull(it)?.takeIf { hex -> hex.isValid() }
                        ?: return Output.error("bad_args", "invalid --accept-from key (npub or 64-char hex): $it")
                }?.toMutableSet()
        args.rejectUnknown(
            "exec",
            "channel",
            "accept-from",
            "accept-from-channel",
            "claim-untargeted",
            "parallel",
            "worktree",
            "base-ref",
            "branch-prefix",
            "poll",
            "exec-timeout",
            "timeout",
            "no-progress",
            "dry-run",
            "once",
        )

        // Parallel runs share one working tree unless each gets its own worktree — that's a
        // guaranteed clobber. Require --worktree once concurrency is on.
        if (parallel > 1 && worktreeBase == null) {
            return Output.error("bad_args", "--parallel > 1 needs --worktree REPODIR so concurrent jobs don't clobber one working tree")
        }
        if (fromChannel && channel == null) {
            return Output.error("bad_args", "--accept-from-channel needs --channel GID")
        }
        if (worktreeBase != null && !File(worktreeBase).isDirectory) {
            return Output.error("bad_args", "--worktree is not a directory: $worktreeBase")
        }

        val opts =
            Opts(
                relay,
                exec,
                channel,
                claimUntargeted,
                postProgress,
                dryRun,
                parallel,
                pollSecs,
                timeoutSecs,
                execTimeoutSecs,
                worktreeBase,
                baseRef,
                branchPrefix,
            )

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val me = ctx.identity.pubKeyHex

            if (worktreeBase != null && git(worktreeBase, "rev-parse", "--git-dir").exit != 0) {
                return Output.error("bad_args", "--worktree is not a git repo: $worktreeBase")
            }

            // Coroutine `finally` blocks don't run on a hard kill, so a Ctrl-C mid-job would leak
            // its worktree + branch. Force-remove any still-active ones on JVM shutdown.
            if (worktreeBase != null) {
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        activeWorktrees.toList().forEach { (path, branch) ->
                            runCatching { ProcessBuilder("git", "-C", worktreeBase, "worktree", "remove", "--force", path).start().waitFor() }
                            runCatching { ProcessBuilder("git", "-C", worktreeBase, "branch", "-D", branch).start().waitFor() }
                        }
                    },
                )
            }

            // Resolve the intake allowlist: explicit --accept-from ∪ the channel's kind-39002
            // member roster (when --accept-from-channel). Null = obey anyone (no gate).
            val allow: Set<HexKey>? =
                if (fromChannel) {
                    val members = channelMembers(ctx, relay, channel!!, timeoutSecs)
                    (acceptFrom ?: mutableSetOf()).apply { addAll(members) }
                } else {
                    acceptFrom
                }

            // Seed the handled set so a restart doesn't re-run work already picked up.
            val handled = mutableSetOf<HexKey>()
            BuzzJobCommands.fetchJobs(ctx, relay, channel, timeoutSecs).forEach { job ->
                if (job.state != JobState.REQUESTED) handled.add(job.jobId)
            }

            if (once) return runOnce(ctx, me, opts, allow, handled)

            Output.emit(
                mapOf(
                    "serving" to me,
                    "relay" to relay.url,
                    "channel" to channel,
                    "exec" to (exec ?: "(dry-run)"),
                    "parallel" to parallel,
                    "worktree" to worktreeBase,
                    "accept_from" to allow?.toList(),
                    "already_handled" to handled.size,
                ),
            )
            System.err.println("[agent] serving as $me on ${relay.url} — parallel=$parallel — Ctrl-C to stop")
            runForever(ctx, me, opts, allow, handled)
        }
        @Suppress("UNREACHABLE_CODE")
        return 0
    }

    /** One pass: launch every pending job (throttled to --parallel), wait for all, emit a summary. */
    private suspend fun runOnce(
        ctx: Context,
        me: HexKey,
        opts: Opts,
        allow: Set<HexKey>?,
        handled: MutableSet<HexKey>,
    ): Int {
        val pending = selectPending(ctx, me, opts, allow, handled)
        val done = mutableListOf<Map<String, Any?>>()
        val doneMutex = Mutex()
        coroutineScope {
            val sem = Semaphore(opts.parallel)
            pending.forEach { job ->
                handled.add(job.jobId)
                launch {
                    sem.withPermit {
                        // Isolate a throwing job so it can't cancel its siblings in this batch.
                        val r =
                            try {
                                handle(ctx, me, opts, job)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                mapOf("job_id" to job.jobId, "state" to "failed", "error" to (e.message ?: "exception"))
                            }
                        doneMutex.withLock { done.add(r) }
                    }
                }
            }
        }
        Output.emit(mapOf("relay" to opts.relay.url, "handled" to done.size, "jobs" to done))
        return 0
    }

    /** The long-running loop: keep the in-flight count at ≤ --parallel, launching by priority. */
    private suspend fun runForever(
        ctx: Context,
        me: HexKey,
        opts: Opts,
        allow: Set<HexKey>?,
        handled: MutableSet<HexKey>,
    ) {
        supervisorScope {
            val inflight = mutableSetOf<HexKey>()
            val mutex = Mutex()
            while (true) {
                // A poll runs selectPending()/drain() directly in this scope; a transient relay
                // error must not kill the unattended daemon, so isolate each poll and retry.
                try {
                    val busy = mutex.withLock { inflight.toSet() }
                    val free = opts.parallel - busy.size
                    if (free > 0) {
                        val pending = selectPending(ctx, me, opts, allow, handled + busy).take(free)
                        pending.forEach { job ->
                            handled.add(job.jobId)
                            mutex.withLock { inflight.add(job.jobId) }
                            launch {
                                try {
                                    val r = handle(ctx, me, opts, job)
                                    System.err.println("[agent] ${r["state"]} job ${job.jobId.take(12)}… (${job.upvotes} upvotes)")
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    System.err.println("[agent] job ${job.jobId.take(12)}… errored: ${e.message}")
                                } finally {
                                    mutex.withLock { inflight.remove(job.jobId) }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    System.err.println("[agent] poll error: ${e.message} — retrying in ${opts.pollSecs}s")
                }
                delay(opts.pollSecs * 1000)
            }
        }
    }

    /** REQUESTED jobs targeting me, from an allowed requester, not already taken — priority-ordered. */
    private suspend fun selectPending(
        ctx: Context,
        me: HexKey,
        opts: Opts,
        allow: Set<HexKey>?,
        exclude: Set<HexKey>,
    ): List<JobView> =
        BuzzJobAggregator.byPriority(
            BuzzJobCommands
                .fetchJobs(ctx, opts.relay, opts.channel, opts.timeoutSecs)
                .filter { it.state == JobState.REQUESTED && it.jobId !in exclude }
                .filter { targetedAtMe(it, me, opts.claimUntargeted) }
                .filter { allow == null || it.requester in allow },
        )

    /** A REQUESTED job is mine to handle if it `p`-targets me, or has no target and I opted in. */
    private fun targetedAtMe(
        job: JobView,
        me: HexKey,
        claimUntargeted: Boolean,
    ): Boolean =
        when (job.agent) {
            me -> true
            null -> claimUntargeted
            else -> false
        }

    /** Accept → (worktree) → (progress) → run --exec → result/error. Returns a summary row. */
    private suspend fun handle(
        ctx: Context,
        me: HexKey,
        opts: Opts,
        job: JobView,
    ): Map<String, Any?> {
        val channel = job.channel
        publish(ctx, opts.relay, JobAcceptedEvent.build(job.jobId, channel, job.requester, "picked up by amy"))

        if (opts.dryRun) {
            publish(ctx, opts.relay, JobResultEvent.build(job.jobId, "[dry-run] would run: ${opts.exec ?: "(none)"}", channel, job.requester, "completed"))
            return mapOf("job_id" to job.jobId, "state" to "completed", "dry_run" to true)
        }

        // Each job gets its own worktree+branch off base-ref so N run without collision.
        val short = job.jobId.take(12)
        val branch = opts.branchPrefix + short
        var workdir: String? = null
        var worktreePath: String? = null
        try {
            if (opts.worktreeBase != null) {
                val wt = File(System.getProperty("java.io.tmpdir"), "buzz-worktrees/$short")
                worktreePath = wt.absolutePath
                val add =
                    worktreeMutex.withLock {
                        wt.parentFile?.mkdirs()
                        // Clear anything a crashed prior run left behind for this exact job id. `-B`
                        // (reset-or-create) makes the branch idempotent so a leftover branch from a
                        // hard-killed run doesn't make the job permanently un-runnable.
                        git(opts.worktreeBase, "worktree", "prune")
                        wt.deleteRecursively()
                        git(opts.worktreeBase, "worktree", "add", "-B", branch, worktreePath, opts.baseRef)
                    }
                if (add.exit != 0) {
                    publish(ctx, opts.relay, JobErrorEvent.build(job.jobId, "worktree setup failed: ${add.stderr.take(MAX_BODY)}", channel, job.requester, "error"))
                    return mapOf("job_id" to job.jobId, "state" to "failed", "error" to "worktree")
                }
                workdir = worktreePath
                activeWorktrees.add(worktreePath to branch)
            }

            if (opts.postProgress) {
                publish(ctx, opts.relay, JobProgressEvent.build(job.jobId, "working on $branch…", channel, "running"))
            }
            val env =
                buildMap {
                    put("BUZZ_JOB_ID", job.jobId)
                    job.requester?.let { put("BUZZ_REQUESTER", it) }
                    channel?.let { put("BUZZ_CHANNEL", it) }
                    put("BUZZ_RELAY", opts.relay.url)
                    put("BUZZ_AGENT", me)
                    put("BUZZ_UPVOTES", job.upvotes.toString())
                    if (opts.worktreeBase != null) {
                        put("BUZZ_BRANCH", branch)
                        put("BUZZ_WORKTREE", worktreePath!!)
                        put("BUZZ_BASE_REF", opts.baseRef)
                    }
                }
            val run = runExec(opts.exec!!, job.request ?: "", env, opts.execTimeoutSecs, workdir)

            return if (run.exit == 0) {
                val body = run.stdout.ifBlank { "(no output)" }
                publish(ctx, opts.relay, JobResultEvent.build(job.jobId, body.take(MAX_BODY), channel, job.requester, "completed"))
                mapOf("job_id" to job.jobId, "state" to "completed", "exit" to 0, "branch" to if (opts.worktreeBase != null) branch else null)
            } else {
                val body = (run.stderr.ifBlank { run.stdout }).ifBlank { "exited ${run.exit}" }
                publish(ctx, opts.relay, JobErrorEvent.build(job.jobId, body.take(MAX_BODY), channel, job.requester, "error"))
                mapOf("job_id" to job.jobId, "state" to "failed", "exit" to run.exit)
            }
        } finally {
            // Drop the worktree; the branch stays in the base repo (the exec pushed it). Runs on
            // normal completion AND on a failed/early-return worktree setup (removal no-ops if the
            // worktree was never created).
            if (worktreePath != null) {
                worktreeMutex.withLock { git(opts.worktreeBase!!, "worktree", "remove", "--force", worktreePath) }
                activeWorktrees.remove(worktreePath to branch)
            }
        }
    }

    /** Latest kind-39002 roster for the channel → its member pubkeys. Empty if none served. */
    private suspend fun channelMembers(
        ctx: Context,
        relay: NormalizedRelayUrl,
        channel: String,
        timeoutSecs: Long,
    ): Set<HexKey> {
        val filter = Filter(kinds = listOf(GroupMembersEvent.KIND), tags = mapOf("d" to listOf(channel)))
        return ctx
            .drain(mapOf(relay to listOf(filter)), timeoutSecs * 1000, pendingOnAuthRequired = true)
            .map { it.second }
            .filterIsInstance<GroupMembersEvent>()
            .maxByOrNull { it.createdAt }
            ?.members()
            ?.toSet()
            .orEmpty()
    }

    private class ExecResult(
        val exit: Int,
        val stdout: String,
        val stderr: String,
    )

    /** Run `sh -c CMD` in [workdir], piping [input] to stdin and exporting [env]; capture both streams. */
    private suspend fun runExec(
        cmd: String,
        input: String,
        env: Map<String, String>,
        timeoutSecs: Long,
        workdir: String?,
    ): ExecResult =
        withContext(Dispatchers.IO) {
            val pb = ProcessBuilder("sh", "-c", cmd)
            workdir?.let { pb.directory(File(it)) }
            pb.environment().putAll(env)
            val proc = pb.start()
            coroutineScope {
                // Drain stdout and stderr on their own coroutines and feed stdin on another, so a
                // child that fills one pipe while we block on the other can't deadlock. The timeout
                // is enforced by waitFor (NOT by the reads, which have none): on expiry we
                // destroyForcibly, which closes the child's pipes and lets the readers finish.
                val outDeferred = async { proc.inputStream.readBytes().decodeToString() }
                val errDeferred = async { proc.errorStream.readBytes().decodeToString() }
                launch { runCatching { proc.outputStream.use { it.write(input.encodeToByteArray()) } } }

                val finished =
                    if (timeoutSecs > 0) {
                        proc.waitFor(timeoutSecs, TimeUnit.SECONDS)
                    } else {
                        proc.waitFor()
                        true
                    }
                if (!finished) proc.destroyForcibly()

                val out = outDeferred.await()
                val err = errDeferred.await()
                if (finished) ExecResult(proc.exitValue(), out, err) else ExecResult(124, out, "exec timed out after ${timeoutSecs}s")
            }
        }

    /** Run `git -C dir args…`, capturing exit + both streams. */
    private suspend fun git(
        dir: String,
        vararg gitArgs: String,
    ): ExecResult =
        withContext(Dispatchers.IO) {
            val proc = ProcessBuilder(listOf("git", "-C", dir) + gitArgs).start()
            coroutineScope {
                // Drain both pipes concurrently (same rationale as runExec) before waiting.
                val outDeferred = async { proc.inputStream.readBytes().decodeToString() }
                val errDeferred = async { proc.errorStream.readBytes().decodeToString() }
                proc.waitFor()
                ExecResult(proc.exitValue(), outDeferred.await(), errDeferred.await())
            }
        }

    private suspend fun publish(
        ctx: Context,
        relay: NormalizedRelayUrl,
        template: EventTemplate<out Event>,
    ) {
        val signed = ctx.signer.sign(template)
        ctx.publish(signed, setOf(relay))
    }

    private const val MAX_BODY = 60_000
}
