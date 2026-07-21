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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip34Git.state.tags.RefTag
import java.io.File

/**
 * `amy git init` — bootstrap a NIP-34 repository from the local git checkout,
 * the way `ngit init` does: derive the name, clone URL, earliest-unique-commit,
 * and branch/tag state from `git`, then publish the kind:30617 announcement and
 * (unless `--no-state`) the kind:30618 state in one shot. Every field can be
 * overridden with a flag; when the directory isn't a git repo, the derivation
 * is skipped and you supply `--name` / `--clone` yourself.
 *
 * This is the one `amy git` verb that shells out to `git` — it's inherently
 * about the local working tree, exactly like the `ngit`/`nak git` `init`.
 */
object GitInitCommand {
    suspend fun init(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val repoDir = File(args.flag("repo") ?: ".").absoluteFile
        val noState = args.bool("no-state")

        val toplevel = git(repoDir, "rev-parse", "--show-toplevel")?.let { File(it) }
        val derivedName = toplevel?.name
        val originUrl = git(repoDir, "remote", "get-url", "origin")?.let(::normalizeCloneUrl)
        // The earliest-unique-commit is the repo's mainline (first-parent) root
        // commit — the cross-fork identity every NIP-34 client must agree on. A
        // SHALLOW clone cannot know its true root (`rev-list --max-parents=0`
        // returns the shallow-boundary commits, not the real first commit), so we
        // must NOT derive a bogus euc there — that would announce the repo under a
        // wrong identity and fork it away from ngit's view. `--first-parent` keeps
        // the mainline root deterministic when a history has merged-in subtree roots.
        val shallow = git(repoDir, "rev-parse", "--is-shallow-repository") == "true"
        val euc =
            if (toplevel == null || shallow) {
                null
            } else {
                git(repoDir, "rev-list", "--max-parents=0", "--first-parent", "HEAD")?.lineSequence()?.lastOrNull { it.isNotBlank() }
            }

        val name =
            args.flag("name") ?: derivedName
                ?: return Output.error("bad_args", "not a git repo and no --name given (run inside a repo, or pass --name)")
        val identifier = args.flag("d") ?: args.flag("identifier") ?: kebab(name)
        val cloneUrls = GitSupport.csv(args, "clone").ifEmpty { listOfNotNull(originUrl) }
        val earliestCommit = args.flag("earliest-commit") ?: euc
        if (earliestCommit == null && toplevel != null) {
            System.err.println(
                "[git init] warning: could not derive the earliest-unique-commit" +
                    (if (shallow) " (shallow clone)" else "") +
                    " — the announcement will omit it. Pass --earliest-commit <root-commit-id> so the repo keeps a stable cross-fork identity.",
            )
        }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val announce =
                GitRepositoryEvent.build(
                    name = name,
                    description = args.flag("description"),
                    webUrls = GitSupport.csv(args, "web"),
                    cloneUrls = cloneUrls,
                    relays = GitSupport.csv(args, "relay"),
                    maintainers = GitSupport.csv(args, "maintainer"),
                    hashtags = GitSupport.csv(args, "hashtag"),
                    earliestUniqueCommit = earliestCommit,
                    personalFork = args.bool("personal-fork"),
                    dTag = identifier,
                )
            val signedAnnounce = ctx.signer.sign(announce)
            val targets = RawEventSupport.publishTargets(ctx, args)
            args.rejectUnknown()
            val ackAnnounce = ctx.publish(signedAnnounce, targets)
            RawEventSupport.publishGuard(ackAnnounce, signedAnnounce.id)?.let { return it }

            val result =
                mutableMapOf<String, Any?>(
                    "event_id" to signedAnnounce.id,
                    "address" to Address.assemble(signedAnnounce.kind, signedAnnounce.pubKey, identifier),
                    "name" to name,
                    "clone" to cloneUrls,
                    "earliest_commit" to earliestCommit,
                    "from_git_repo" to (toplevel != null),
                )

            if (!noState && toplevel != null) {
                val refs = readRefs(repoDir)
                val head = git(repoDir, "symbolic-ref", "--short", "HEAD")
                if (refs.isNotEmpty() || head != null) {
                    val stateTemplate = GitRepositoryStateEvent.build(dTag = identifier, refs = refs, head = head)
                    val signedState = ctx.signer.sign(stateTemplate)
                    // Surface the state publish result separately (state_*) rather than
                    // dropping it — otherwise a fully-rejected 30618 reads as success.
                    val stateAck = ctx.publish(signedState, targets)
                    result["state_event_id"] = signedState.id
                    result["branches"] = refs.count { it.kind == RefTag.Kind.BRANCH }
                    result["tags"] = refs.count { it.kind == RefTag.Kind.TAG }
                    result["head"] = head
                    result.putAll(RawEventSupport.ackFields(stateAck).mapKeys { "state_${it.key}" })
                    if (stateAck.isNotEmpty() && stateAck.none { it.value.accepted }) {
                        System.err.println("[git init] warning: no relay accepted the repository-state (30618) event — branches/tags/HEAD were not delivered.")
                    }
                }
            }
            Output.emit(result + RawEventSupport.ackFields(ackAnnounce))
            return 0
        }
    }

    /** Read local branch + tag refs as NIP-34 [RefTag]s via `git for-each-ref`. */
    private fun readRefs(repoDir: File): List<RefTag> {
        fun parse(
            output: String?,
            builder: (String, String) -> RefTag,
        ): List<RefTag> =
            output
                ?.lineSequence()
                ?.mapNotNull { line ->
                    val parts = line.trim().split(' ')
                    if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) builder(parts[0], parts[1]) else null
                }?.toList()
                .orEmpty()

        val branches = parse(git(repoDir, "for-each-ref", "--format=%(refname:short) %(objectname)", "refs/heads")) { n, c -> RefTag.branch(n, c) }
        val tags = parse(git(repoDir, "for-each-ref", "--format=%(refname:short) %(objectname)", "refs/tags")) { n, c -> RefTag.tag(n, c) }
        return branches + tags
    }

    /**
     * Run `git <args>` in [repoDir]; returns trimmed stdout on exit 0, else null
     * (git missing / not a repo). stderr is discarded straight to the OS so a
     * chatty command can never fill its stderr pipe and deadlock the stdout read.
     */
    private fun git(
        repoDir: File,
        vararg gitArgs: String,
    ): String? =
        try {
            val proc =
                ProcessBuilder(listOf("git", *gitArgs))
                    .directory(repoDir)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            val out = proc.inputStream.readBytes().decodeToString()
            if (proc.waitFor() == 0) out.trim().ifEmpty { null } else null
        } catch (_: Exception) {
            null
        }

    /** Convert an ssh remote (`git@host:owner/repo.git`, `ssh://…`) to a browsable https URL; pass others through. */
    private fun normalizeCloneUrl(url: String): String =
        when {
            url.startsWith("git@") -> {
                val rest = url.removePrefix("git@")
                val host = rest.substringBefore(':')
                val path = rest.substringAfter(':')
                "https://$host/$path"
            }
            url.startsWith("ssh://git@") -> {
                // ssh://git@host[:port]/owner/repo.git → https://host/owner/repo.git
                // (drop the SSH port; carrying it into the https URL makes it unreachable).
                val rest = url.removePrefix("ssh://git@")
                val slash = rest.indexOf('/')
                val hostPort = if (slash >= 0) rest.take(slash) else rest
                val path = if (slash >= 0) rest.substring(slash) else ""
                "https://${hostPort.substringBefore(':')}$path"
            }
            else -> url
        }

    /** kebab-case a repo name for the `d` identifier: lowercase, non-alphanumerics collapse to single hyphens. */
    private fun kebab(name: String): String =
        name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { name }
}
