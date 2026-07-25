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
import com.vitorpamplona.amethyst.commons.model.buzz.JobState
import com.vitorpamplona.amethyst.commons.model.buzz.JobView
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * `amy buzz agent …` — the AGENT side of the Buzz job protocol: a headless responder loop
 * that turns an incoming job request (kind-43001 targeting my key) into a spawned command,
 * and reports the outcome back as accept/progress/result/error events (43002/43003/43004/43006).
 *
 * This is the "drive this agent to build Amethyst" prototype: point `--exec` at a coding
 * agent (e.g. `claude -p`, a Goose/Codex wrapper, or a build script). The job's task text is
 * piped to the command's stdin; `BUZZ_JOB_ID`, `BUZZ_REQUESTER`, `BUZZ_CHANNEL`, and
 * `BUZZ_RELAY` are exported into its environment; its stdout becomes the job result.
 *
 * PERMISSIONS — read this. Buzz authorizes by identity, not by capability flags, so this
 * responder is only as safe as the two guardrails around it:
 *   1. `--accept-from` is the intake gate: only listed requester keys are obeyed (your team's
 *      npubs). Without it the agent answers anyone who can post to the relay.
 *   2. What `--exec` can DO to a repo is bounded entirely by the credentials/tooling you give
 *      that command — NOT by Buzz. Keep its git token scoped to open PRs on feature branches
 *      (never merge, never force-push), and branch-protect `main`. See
 *      `cli/plans/2026-07-25-buzz-agent-support-channel.md`.
 *
 * SCHEMA CAVEAT: kinds 43001-43006 are *reserved* in Buzz with no upstream builder; see
 * [com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent].
 */
object BuzzAgentCommands {
    private val USAGE =
        """
        |amy buzz agent serve RELAY --exec CMD         run a job-responder loop
        |    [--channel GID]                             only handle jobs scoped to this channel
        |    [--accept-from npub,npub]                   allowlist of requester keys (STRONGLY advised)
        |    [--claim-untargeted]                        also handle jobs with no `p` target
        |    [--poll SECS]                               poll interval (default 5)
        |    [--exec-timeout SECS]                       kill --exec after N seconds (default 1800; 0 = none)
        |    [--timeout SECS]                            per-fetch relay timeout (default 8)
        |    [--no-progress]                             don't post a 43003 "working" ping
        |    [--dry-run]                                 accept + canned result, never run --exec
        |    [--once]                                    drain currently-pending jobs, then exit
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "buzz agent",
            tail,
            USAGE,
            mapOf(
                "serve" to { rest -> serve(dataDir, rest) },
            ),
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
        val pollSecs = args.flag("poll")?.toLongOrNull() ?: 5
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 8
        val execTimeoutSecs = args.flag("exec-timeout")?.toLongOrNull() ?: 1800
        val acceptFrom =
            args
                .flag("accept-from")
                ?.split(",")
                ?.mapNotNull { it.trim().ifBlank { null } }
                ?.map {
                    decodePublicKeyAsHexOrNull(it)?.takeIf { hex -> hex.isValid() }
                        ?: return Output.error("bad_args", "invalid --accept-from key (npub or 64-char hex): $it")
                }?.toSet()
        args.rejectUnknown(
            "exec",
            "channel",
            "accept-from",
            "claim-untargeted",
            "poll",
            "exec-timeout",
            "timeout",
            "no-progress",
            "dry-run",
            "once",
        )

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val me = ctx.identity.pubKeyHex

            // Seed the handled set from jobs already accepted/terminated so a restart doesn't
            // re-run work. Any job past REQUESTED — someone (maybe a previous run of me)
            // already picked it up — is treated as done for intake purposes.
            val handled = mutableSetOf<HexKey>()
            BuzzJobCommands.fetchJobs(ctx, relay, channel, timeoutSecs).forEach { job ->
                if (job.state != JobState.REQUESTED) handled.add(job.jobId)
            }

            if (!once) {
                Output.emit(
                    mapOf(
                        "serving" to me,
                        "relay" to relay.url,
                        "channel" to channel,
                        "exec" to (exec ?: "(dry-run)"),
                        "accept_from" to acceptFrom?.toList(),
                        "poll_secs" to pollSecs,
                        "already_handled" to handled.size,
                    ),
                )
                System.err.println("[agent] serving as $me on ${relay.url} — Ctrl-C to stop")
            }

            val donePass = mutableListOf<Map<String, Any?>>()
            while (true) {
                val pending =
                    BuzzJobCommands
                        .fetchJobs(ctx, relay, channel, timeoutSecs)
                        .filter { it.state == JobState.REQUESTED && it.jobId !in handled }
                        .filter { targetedAtMe(it, me, claimUntargeted) }
                        .filter { acceptFrom == null || it.requester in acceptFrom }
                        .sortedBy { it.createdAt }

                for (job in pending) {
                    handled.add(job.jobId) // mark before working so a slow --exec isn't double-run
                    val outcome = handle(ctx, relay, me, job, exec, dryRun, postProgress, execTimeoutSecs)
                    if (once) donePass.add(outcome) else System.err.println("[agent] ${outcome["state"]} job ${job.jobId.take(12)}…")
                }

                if (once) {
                    Output.emit(mapOf("relay" to relay.url, "handled" to donePass.size, "jobs" to donePass))
                    return 0
                }
                delay(pollSecs * 1000)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return 0
    }

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

    /** Accept → (progress) → run --exec → result/error. Returns a summary row. */
    private suspend fun handle(
        ctx: Context,
        relay: NormalizedRelayUrl,
        me: HexKey,
        job: JobView,
        exec: String?,
        dryRun: Boolean,
        postProgress: Boolean,
        execTimeoutSecs: Long,
    ): Map<String, Any?> {
        val channel = job.channel
        publish(ctx, relay, JobAcceptedEvent.build(job.jobId, channel, job.requester, "picked up by amy"))

        if (dryRun) {
            publish(ctx, relay, JobResultEvent.build(job.jobId, "[dry-run] would run: ${exec ?: "(none)"}", channel, job.requester, "completed"))
            return mapOf("job_id" to job.jobId, "state" to "completed", "dry_run" to true)
        }
        if (postProgress) {
            publish(ctx, relay, JobProgressEvent.build(job.jobId, "working…", channel, "running"))
        }

        val env =
            buildMap {
                put("BUZZ_JOB_ID", job.jobId)
                job.requester?.let { put("BUZZ_REQUESTER", it) }
                channel?.let { put("BUZZ_CHANNEL", it) }
                put("BUZZ_RELAY", relay.url)
                put("BUZZ_AGENT", me)
            }
        val run = runExec(exec!!, job.request ?: "", env, execTimeoutSecs)

        return if (run.exit == 0) {
            val body = run.stdout.ifBlank { "(no output)" }
            publish(ctx, relay, JobResultEvent.build(job.jobId, body.take(MAX_BODY), channel, job.requester, "completed"))
            mapOf("job_id" to job.jobId, "state" to "completed", "exit" to 0)
        } else {
            val body = (run.stderr.ifBlank { run.stdout }).ifBlank { "exited ${run.exit}" }
            publish(ctx, relay, JobErrorEvent.build(job.jobId, body.take(MAX_BODY), channel, "error"))
            mapOf("job_id" to job.jobId, "state" to "failed", "exit" to run.exit)
        }
    }

    private class ExecResult(
        val exit: Int,
        val stdout: String,
        val stderr: String,
    )

    /** Run `sh -c CMD`, piping [input] to its stdin and exporting [env]; capture both streams. */
    private suspend fun runExec(
        cmd: String,
        input: String,
        env: Map<String, String>,
        timeoutSecs: Long,
    ): ExecResult =
        withContext(Dispatchers.IO) {
            val pb = ProcessBuilder("sh", "-c", cmd)
            pb.environment().putAll(env)
            val proc = pb.start()
            proc.outputStream.use { it.write(input.encodeToByteArray()) }
            // Read both pipes before waiting so a chatty child can't deadlock on a full buffer.
            val out = proc.inputStream.readBytes().decodeToString()
            val err = proc.errorStream.readBytes().decodeToString()
            if (timeoutSecs > 0) {
                if (!proc.waitFor(timeoutSecs, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                    return@withContext ExecResult(124, out, "exec timed out after ${timeoutSecs}s")
                }
            } else {
                proc.waitFor()
            }
            ExecResult(proc.exitValue(), out, err)
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
