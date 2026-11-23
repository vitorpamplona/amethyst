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
package com.vitorpamplona.amethyst.commons.claudecode

import java.io.File

/**
 * What an agent session may push, fixed by the app when the session starts and never read
 * from the repository at push time, where the session could change it.
 */
data class PushScope(
    /** The URLs of the remotes the repositories had when the session started, push URLs as git rewrites them. */
    val urls: Set<String>,
    /** The session's own branches: they may be rewritten. */
    val ownBranches: Set<String>,
    /** Branches no push may touch: each remote's default branch, `main` and `master`. */
    val protectedBranches: Set<String>,
)

/**
 * The guard on an agent session's pushes: a pre-push hook that git runs from config passed in
 * the session's environment (config hooks, git 2.54+), beside the repository's own hooks.
 *
 * A push goes only to a URL in [PushScope.urls], never to a `nostr://` remote (git-remote-nostr
 * signs and publishes the repository's state, or a proposal, with the user's key). Of what it
 * pushes, only branches pass: the session's own ([PushScope.ownBranches]) in any way but
 * deletion, and new branches named [AGENT_BRANCH_PREFIXES] only forward. Tags, deletions, other
 * refs, other branches and the protected ones are refused.
 *
 * The script is the hook's command itself, with the scope written into it: there is no file
 * the session could rewrite to turn the guard off, and nothing it reads from the repository.
 *
 * It guards against a push by mistake, not against an agent set on it: one that clears the
 * environment (`env -u GIT_CONFIG_COUNT`), passes `--no-verify` or its own `-c` config, or
 * pushes through a script or a token skips it. The command rules ([SessionCommandGuard]) catch
 * the common spellings of those; nothing in an agent's own shell can stop all of them.
 */
object PushGuard {
    /** Branch names the app gives agents' branches (workstreams, worktrees): new ones may be pushed. */
    val AGENT_BRANCH_PREFIXES = listOf("agent/", "claude/")

    /** How a session's pushes are guarded: the environment to start it with, and what the agent is told. */
    class PushRules(
        val environment: Map<String, String>,
        /** False when git couldn't run the guard and every push is blocked ([ClaudeChat.noPushEnvironment]). */
        val guarded: Boolean,
        val scope: PushScope? = null,
    ) {
        /** Appended to the session's instructions, so the agent knows what it may push before it tries. */
        val note: String
            get() =
                if (!guarded) {
                    "Pushing is blocked in this session: this git can't run Amethyst's push guard (it needs git 2.54 or newer). " +
                        "Leave the push to the user; don't work around it."
                } else {
                    val own = scope?.ownBranches.orEmpty()
                    buildString {
                        append("Pushes in this session go through Amethyst's push guard. ")
                        if (own.isNotEmpty()) append("You may push your branch (${own.joinToString(", ")}), force included, ")
                        append("or a new branch named ${AGENT_BRANCH_PREFIXES.joinToString(" or ") { "$it…" }} (forward only), ")
                        append("to the remotes this session started with. ")
                        append("Tags, deleting branches, the default branch, other branches and nostr:// remotes are the user's; don't work around the guard.")
                    }
                }
    }

    /**
     * What [repos] may push to now: their remotes' URLs, and their branches. In a workstream
     * ([workspace]) the branches are the ones the app made for it; elsewhere, each repository's
     * branch as the session starts on it, unless it is a default branch. A branch not named like
     * an agent's is checked with the remotes themselves (`ls-remote --symref`), not their copy
     * in the clone, which a past session could have repointed; when no remote answers, it is not
     * the session's. Blocks on git, and on the network for that check: call it off the main thread.
     */
    fun capture(
        repos: List<File>,
        workspace: AgentWorkspace? = null,
    ): PushScope {
        val urls = LinkedHashSet<String>()
        val protected = linkedSetOf("main", "master")
        workspace?.repos?.forEach { protected += it.baseBranch }
        val remoteUrls = HashMap<File, List<String>>()
        for (repo in repos) {
            val remotes = lines(GitWorkspace.git(repo, "remote"))
            val repoUrls =
                remotes.flatMap { remote ->
                    lines(GitWorkspace.git(repo, "remote", "get-url", "--all", "--", remote)) +
                        lines(GitWorkspace.git(repo, "remote", "get-url", "--push", "--all", "--", remote))
                }
            remoteUrls[repo] = repoUrls.distinct()
            urls += repoUrls
            remotes.forEach { remote ->
                lines(GitWorkspace.git(repo, "symbolic-ref", "-q", "--short", "refs/remotes/$remote/HEAD")).forEach { protected += it.removePrefix("$remote/") }
            }
        }
        val own =
            if (workspace != null) {
                workspace.repos.map { it.branch }.filter { it !in protected }
            } else {
                repos.mapNotNull { repo ->
                    val branch = lines(GitWorkspace.git(repo, "branch", "--show-current")).firstOrNull() ?: return@mapNotNull null
                    if (branch in protected) return@mapNotNull null
                    if (AGENT_BRANCH_PREFIXES.any { branch.startsWith(it) }) return@mapNotNull branch
                    val defaults = remoteDefaults(repo, remoteUrls[repo].orEmpty()) ?: return@mapNotNull null
                    protected += defaults
                    branch.takeIf { it !in defaults }
                }
            }
        return PushScope(urls, own.toSet(), protected)
    }

    /** The default branch each git server among [urls] names, or null when none answered. */
    private fun remoteDefaults(
        repo: File,
        urls: List<String>,
    ): Set<String>? {
        // A nostr remote's helper would go to relays; its pushes are refused anyway.
        val servers = urls.filter { !it.startsWith("nostr:") }
        var answered = false
        val defaults = HashSet<String>()
        for (url in servers) {
            val result = GitWorkspace.run(repo, listOf("git", "ls-remote", "--symref", "--", url, "HEAD"), timeoutSeconds = 20, noLoginWindow = true)
            if (result.code != 0) continue
            answered = true
            result.output.lines().forEach { line ->
                SYMREF_HEAD.matchEntire(line.trim())?.let { defaults += it.groupValues[1] }
            }
        }
        return if (answered) defaults else null
    }

    private val SYMREF_HEAD = Regex("ref: refs/heads/(\\S+)\\s+HEAD")

    private fun lines(output: String?): List<String> =
        output
            .orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * Git config, through the environment, that runs [script] for [scope] before each of the
     * session's pushes. Without config hooks (an older git, or no repository in [repos] to ask)
     * every push is blocked instead ([ClaudeChat.noPushEnvironment]). Turning the event off in the
     * repository's config doesn't help the session: this config is read after it.
     */
    fun environment(
        parent: Map<String, String> = System.getenv(),
        repos: List<File>,
        scope: PushScope,
    ): PushRules {
        if (!configHooksWork(repos)) {
            return PushRules(ClaudeChat.noPushEnvironment(parent, repos.flatMap { GitWorkspace.remoteUrls(it) }.distinct()), guarded = false)
        }
        val n = parent["GIT_CONFIG_COUNT"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val entries =
            listOf(
                "hook.$GUARD_NAME.command" to script(scope),
                "hook.$GUARD_NAME.event" to "pre-push",
                "hook.$GUARD_NAME.enabled" to "true",
                "hook.pre-push.enabled" to "true",
            )
        val environment =
            buildMap {
                put("GIT_CONFIG_COUNT", (n + entries.size).toString())
                entries.forEachIndexed { i, (key, value) ->
                    put("GIT_CONFIG_KEY_${n + i}", key)
                    put("GIT_CONFIG_VALUE_${n + i}", value)
                }
            }
        return PushRules(environment, guarded = true, scope = scope)
    }

    const val GUARD_NAME = "amethyst-push-guard"

    /** Whether the app's git runs hooks named in config: the binary doesn't change while the app runs. */
    @Volatile
    private var configHooks: Boolean? = null

    /** Asked once, inside the first of [repos] (`git hook list` reads config only in a repository). */
    private fun configHooksWork(repos: List<File>): Boolean {
        val repo = repos.firstOrNull() ?: return false
        configHooks?.let { return it }
        val result = GitWorkspace.run(repo, listOf("git", "-c", "hook.amethyst-probe.command=true", "-c", "hook.amethyst-probe.event=pre-push", "hook", "list", "pre-push"))
        // A git without config hooks says `hook list` is no command, or lists nothing; a timeout says nothing.
        if (result.code < 0) return false
        val works = result.code == 0 && result.output.lines().any { it.trim() == "amethyst-probe" }
        configHooks = works
        return works
    }

    /** [values], one per line, as a single-quoted shell word; a value with a line break can't be matched by line and is left out. */
    private fun shellList(values: Collection<String>): String = "'" + values.filter { '\n' !in it && '\r' !in it }.joinToString("\n") { it.replace("'", "'\\''") } + "'"

    /**
     * The pre-push hook for [scope], run by git as `sh -c '<script> "$@"' <script> <remote> <url>`
     * with one line per ref on stdin: local ref, local id, remote ref, remote id, a zero id for a
     * ref that doesn't exist on that side. It ends in a comment, so the `"$@"` git appends is one.
     */
    internal fun script(scope: PushScope): String =
        """
        |# Amethyst's guard on an agent session's pushes (PushGuard): what it may push was fixed when it started.
        |urls=${shellList(scope.urls)}
        |own=${shellList(scope.ownBranches)}
        |protected=${shellList(scope.protectedBranches)}
        |url="${'$'}2"
        |listed() { printf '%s\n' "${'$'}2" | grep -Fxq -- "${'$'}1"; }
        |case "${'$'}url" in
        |nostr:*)
        |    echo "amethyst: not pushed: a push to a nostr remote signs and publishes with the user's key; that is the user's to do from the app" >&2
        |    exit 1
        |    ;;
        |esac
        |if ! listed "${'$'}url" "${'$'}urls"; then
        |    echo "amethyst: not pushed: ${'$'}url is not a remote this session started with" >&2
        |    exit 1
        |fi
        |zero=${'$'}(git hash-object --stdin </dev/null | tr '0-9a-f' '0')
        |status=0
        |refuse() {
        |    echo "amethyst: not pushed: ${'$'}1" >&2
        |    status=1
        |}
        |while read -r local_ref local_oid remote_ref remote_oid; do
        |    case "${'$'}remote_ref" in
        |    refs/heads/*) branch=${'$'}{remote_ref#refs/heads/} ;;
        |    refs/tags/*) refuse "${'$'}remote_ref: publishing a tag is the user's to do"; continue ;;
        |    *) refuse "${'$'}remote_ref: only branches are pushed"; continue ;;
        |    esac
        |    if [ "${'$'}local_oid" = "${'$'}zero" ]; then
        |        refuse "${'$'}branch: deleting a branch is the user's to do"
        |    elif listed "${'$'}branch" "${'$'}protected"; then
        |        refuse "${'$'}branch: the default branch only moves by the user's hand"
        |    elif listed "${'$'}branch" "${'$'}own"; then
        |        :
        |    else
        |        case "${'$'}branch" in
        |        ${AGENT_BRANCH_PREFIXES.joinToString(" | ") { "$it*" }})
        |            if [ "${'$'}remote_oid" != "${'$'}zero" ] && ! git merge-base --is-ancestor "${'$'}remote_oid" "${'$'}local_oid" 2>/dev/null; then
        |                refuse "${'$'}branch: not this session's branch, so it only moves forward"
        |            fi
        |            ;;
        |        *) refuse "${'$'}branch: not this session's branch; push yours or a new ${AGENT_BRANCH_PREFIXES.joinToString(" or ") { "$it…" }} branch" ;;
        |        esac
        |    fi
        |done
        |exit "${'$'}status"
        |#
        """.trimMargin()
}
