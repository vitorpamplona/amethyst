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

import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The push guard against a real `git`, temporary repositories and remotes, as an agent session runs it. */
class PushGuardTest {
    private lateinit var root: File
    private lateinit var dir: File
    private lateinit var origin: File

    private fun run(
        where: File,
        vararg args: String,
    ): String {
        val p =
            ProcessBuilder(listOf("git", "-c", "user.name=T", "-c", "user.email=t@example.com", "-c", "commit.gpgsign=false") + args)
                .directory(where)
                .apply { environment().forgetInheritedRepository() }
                .apply { environment().keys.removeAll { it.startsWith("GIT_CONFIG_") } }
                .redirectErrorStream(true)
                .start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "git ${args.joinToString(" ")}: $out" }
        return out
    }

    private fun git(vararg args: String) = run(dir, *args)

    private fun commit(message: String) {
        File(dir, "f.txt").appendText("$message\n")
        git("add", ".")
        git("commit", "-q", "-m", message)
    }

    private fun originHas(ref: String) = GitWorkspace.git(origin, "rev-parse", "-q", "--verify", ref)?.trim()

    private fun head() = git("rev-parse", "HEAD").trim()

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("push-guard").toFile()
        dir = File(root, "work").apply { mkdirs() }
        origin = File(root, "origin.git")
        git("init", "-q", "-b", "main")
        commit("base")
        run(root, "init", "-q", "--bare", "-b", "main", origin.absolutePath)
        git("remote", "add", "origin", origin.absolutePath)
        git("push", "-q", "origin", "main")
        git("fetch", "-q", "origin")
        git("checkout", "-q", "-b", "agent/w")
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun rules(workspace: AgentWorkspace? = null): PushGuard.PushRules {
        val rules = PushGuard.environment(emptyMap(), listOf(dir), PushGuard.capture(listOf(dir), workspace))
        // An older git (before 2.54) has no config hooks: the session gets the full block instead.
        assumeTrue("git has no config hooks", rules.guarded)
        return rules
    }

    private fun guard(): Map<String, String> = rules().environment

    private fun asTheSession(
        env: Map<String, String>,
        command: String,
        extra: Map<String, String> = emptyMap(),
    ): Int {
        val p =
            ProcessBuilder("sh", "-c", command)
                .directory(dir)
                .apply { environment().forgetInheritedRepository() }
                .apply { environment().keys.removeAll { it.startsWith("GIT_CONFIG_") } }
                .apply { environment().putAll(env + extra) }
                .redirectErrorStream(true)
                .start()
        p.inputStream.readBytes()
        return p.waitFor()
    }

    @Test
    fun aRemoteTheSessionAddsIsNoMirrorForMain() {
        val env = guard()
        commit("new work")
        // The session makes its own "mirror" holding the new work as main.
        val evil = File(root, "evil.git")
        run(root, "init", "-q", "--bare", "-b", "main", evil.absolutePath)
        git("remote", "add", "evil", evil.absolutePath)
        git("push", "-q", "evil", "HEAD:main")
        git("fetch", "-q", "evil")
        assertTrue(asTheSession(env, "git push -q origin HEAD:main") != 0, "new work reached main through a mirror the session made")
        assertEquals(run(root, "-C", origin.absolutePath, "rev-parse", "main").trim(), originHas("refs/heads/main"))
    }

    @Test
    fun onlyTheSessionsOwnBranchesMove() {
        commit("base of someone else's branch")
        git("push", "-q", "origin", "HEAD:refs/heads/someone/else")
        val theirs = originHas("refs/heads/someone/else")
        val env = guard()
        git("reset", "-q", "--hard", "HEAD~1")
        commit("rewritten")
        assertTrue(asTheSession(env, "git push -q -f origin HEAD:refs/heads/someone/else") != 0, "rewrote a branch that isn't the session's")
        assertTrue(asTheSession(env, "git push -q origin HEAD:refs/heads/feature") != 0, "pushed a branch outside the agent's names")
        assertEquals(theirs, originHas("refs/heads/someone/else"))
        assertEquals(null, originHas("refs/heads/feature"))
        // Its own branch goes, rewritten too.
        assertEquals(0, asTheSession(env, "git push -q origin agent/w"))
        git("commit", "-q", "--amend", "-m", "amended")
        assertEquals(0, asTheSession(env, "git push -q -f origin agent/w"), "the session's own branch may be rewritten")
        assertEquals(head(), originHas("refs/heads/agent/w"))
    }

    @Test
    fun onlyRemotesTheSessionStartedWithTakePushes() {
        val env = guard()
        val other = File(root, "other.git")
        run(root, "init", "-q", "--bare", other.absolutePath)
        git("remote", "add", "other", other.absolutePath)
        assertTrue(asTheSession(env, "git push -q other agent/w") != 0, "pushed to a remote added after the session started")
        assertTrue(asTheSession(env, "git push -q ${other.absolutePath} agent/w") != 0, "pushed to a URL the session didn't start with")
        git("remote", "set-url", "origin", other.absolutePath)
        assertTrue(asTheSession(env, "git push -q origin agent/w") != 0, "pushed to origin pointed elsewhere")
        assertEquals(null, GitWorkspace.git(other, "rev-parse", "-q", "--verify", "refs/heads/agent/w")?.trim())
    }

    @Test
    fun rewritingTheGuardOnDiskDoesNotTurnItOff() {
        guard()
        // The session overwrites whatever file the guard runs from; the next session starts.
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".sh") }.forEach { it.writeText("#!/bin/sh\nexit 0\n") }
        val env = guard()
        // The hook is its command: no file the session could rewrite.
        val command =
            env.entries
                .single { it.value == "hook.${PushGuard.GUARD_NAME}.command" }
                .key
                .replace("KEY", "VALUE")
        assertTrue(env.getValue(command).contains("refs/heads/*"), "the hook runs a script from somewhere else")
        commit("new work")
        assertTrue(asTheSession(env, "git push -q origin HEAD:main") != 0, "a rewritten guard let new work onto main")
    }

    @Test
    fun branchesGoButTagsDeletionsAndMainDoNot() {
        val env = guard()
        assertEquals(0, asTheSession(env, "git push -q origin agent/w"), "the session's branch must go")
        assertEquals(0, asTheSession(env, "git push -q origin HEAD:refs/heads/agent/second"), "a new agent branch must go")
        commit("more")
        assertEquals(0, asTheSession(env, "git push -q origin HEAD:refs/heads/agent/second"), "a new agent branch moves forward")
        assertTrue(asTheSession(env, "git push -q -f origin main:refs/heads/agent/second") != 0, "rewrote an agent branch that isn't the session's")
        git("tag", "v1")
        listOf(
            "git push -q origin v1",
            "git push -q origin --tags",
            "git push -q origin :agent/w",
            "git push -q origin --delete agent/w",
            "git push -q origin HEAD:main",
            "git -C . push -q origin HEAD:refs/heads/main",
            "git push -q origin HEAD:refs/notes/x",
            "git push -q --mirror origin",
        ).forEach { assertTrue(asTheSession(env, it) != 0, "pushed with: $it") }
        git("config", "hook.pre-push.enabled", "false")
        assertTrue(asTheSession(env, "git push -q origin HEAD:main") != 0, "the repository's config turned the guard off")
        assertEquals(null, originHas("refs/tags/v1"))
        assertEquals(run(dir, "rev-parse", "main").trim(), originHas("refs/heads/main"))
        assertNotNull(originHas("refs/heads/agent/w"))
        assertEquals(head(), originHas("refs/heads/agent/second"))
    }

    @Test
    fun aWorkstreamPushesTheBranchesMadeForIt() {
        git("checkout", "-q", "-b", "elsewhere")
        val workspace = AgentWorkspace("w", "w", root.absolutePath, listOf(WorkspaceRepo(origin.absolutePath, "work", "main", "agent/w")), 0)
        val rules = rules(workspace)
        assertEquals(setOf("agent/w"), rules.scope?.ownBranches)
        assertTrue(rules.note.contains("agent/w"))
        git("checkout", "-q", "agent/w")
        commit("work")
        assertEquals(0, asTheSession(rules.environment, "git push -q origin agent/w"))
        assertTrue(asTheSession(rules.environment, "git push -q origin elsewhere") != 0, "pushed the branch the session happened to be on")
    }

    @Test
    fun aStartingBranchTheRemoteHasOnlyMovesForward() {
        git("checkout", "-q", "-b", "release/1.x")
        git("push", "-q", "origin", "release/1.x")
        val rules = rules()
        assertEquals(setOf("release/1.x"), rules.scope?.forwardBranches)
        assertTrue("release/1.x" !in rules.scope!!.ownBranches)
        commit("ahead")
        assertEquals(0, asTheSession(rules.environment, "git push -q origin release/1.x"), "a forward push of the starting branch must go")
        val pushed = originHas("refs/heads/release/1.x")
        git("reset", "-q", "--hard", "HEAD~1")
        commit("rewritten")
        assertTrue(asTheSession(rules.environment, "git push -q -f origin release/1.x") != 0, "rewrote a branch the remote already had")
        assertEquals(pushed, originHas("refs/heads/release/1.x"))
    }

    @Test
    fun aStartingBranchNewToTheRemotesIsTheSessions() {
        git("checkout", "-q", "-b", "fix/x")
        val rules = rules()
        assertEquals(setOf("fix/x"), rules.scope?.ownBranches)
        commit("work")
        assertEquals(0, asTheSession(rules.environment, "git push -q origin fix/x"))
        git("commit", "-q", "--amend", "-m", "amended")
        assertEquals(0, asTheSession(rules.environment, "git push -q -f origin fix/x"), "the session's new branch may be rewritten")
    }

    @Test
    fun aRespawnKeepsTheScopeTheSessionStartedWith() {
        val saved = HashMap<String, PushScope>()
        val store =
            object : PushScopeStore {
                override fun load(sessionId: String) = saved[sessionId]

                override fun save(
                    sessionId: String,
                    scope: PushScope,
                ) {
                    saved[sessionId] = scope
                }
            }
        val first = PushGuard.scopeFor(null, "s-1", store) { PushGuard.capture(listOf(dir)) }
        assertEquals(setOf("agent/w"), first.ownBranches)
        // The session moves to a release branch the remote has; then the app restarts and resumes it.
        commit("release work")
        git("push", "-q", "origin", "HEAD:refs/heads/release/1.x")
        git("checkout", "-q", "-b", "release/1.x")
        assertEquals(first, PushGuard.scopeFor(first, "s-1", store) { error("captured again on a respawn") })
        val again = PushGuard.scopeFor(null, "s-1", store) { error("captured again after a restart") }
        assertEquals(first, again)
        val rules = PushGuard.environment(emptyMap(), listOf(dir), again)
        assumeTrue("git has no config hooks", rules.guarded)
        commit("more")
        assertTrue(asTheSession(rules.environment, "git push -q origin release/1.x") != 0, "pushed a branch outside the scope the session started with")
        git("commit", "-q", "--amend", "-m", "amended")
        assertTrue(asTheSession(rules.environment, "git push -q -f origin release/1.x") != 0, "rewrote a branch outside the scope")
    }

    @Test
    fun theGitTheGuardWasCheckedWithComesFirstOnThePath() {
        val rules = rules()
        val git = assertNotNull(PushGuard.gitOnPath(System.getenv("PATH")))
        assertEquals(listOf(git.parentFile), rules.pathFirst)
        val a = File(root, "a").apply { mkdirs() }
        val b = File(root, "b").apply { mkdirs() }
        File(b, "git").apply {
            writeText("#!/bin/sh\n")
            check(setExecutable(true))
        }
        assertEquals(File(b, "git"), PushGuard.gitOnPath(listOf(a, b).joinToString(File.pathSeparator)))
        assertEquals(null, PushGuard.gitOnPath(a.path))
    }

    @Test
    fun theGuardFallsBackToNoPushWithoutARepositoryToAsk() {
        val rules = PushGuard.environment(emptyMap(), emptyList(), PushScope(emptySet(), emptySet(), emptySet()))
        assertEquals(false, rules.guarded)
        assertTrue(rules.note.contains("blocked"))
        assertEquals("url.publishing-is-the-users-step-in-amethyst://.pushInsteadOf", rules.environment["GIT_CONFIG_KEY_0"])
    }

    @Test
    fun aRepointedRemoteHeadLeavesTheDefaultBranchProtected() {
        // The remote's default branch is `develop`, and the session started on it.
        git("push", "-q", "origin", "main:refs/heads/develop")
        run(root, "-C", origin.absolutePath, "symbolic-ref", "HEAD", "refs/heads/develop")
        git("checkout", "-q", "-b", "develop")
        // The session repoints its copy of the remote's HEAD before pushing.
        git("symbolic-ref", "refs/remotes/origin/HEAD", "refs/remotes/origin/main")
        val env = guard()
        commit("new work")
        assertTrue(asTheSession(env, "git push -q origin HEAD:develop") != 0, "new work reached the default branch")
        assertEquals(run(root, "-C", origin.absolutePath, "rev-parse", "main").trim(), originHas("refs/heads/develop"))
    }

    @Test
    fun nothingIsPushedToANostrRemote() {
        // A stand-in for git-remote-nostr on the PATH: the real one would sign and publish.
        val bin = File(root, "bin").apply { mkdirs() }
        val log = File(root, "nostr.log")
        File(bin, "git-remote-nostr").apply {
            writeText(FAKE_NOSTR_HELPER)
            check(setExecutable(true)) { "could not make the fake helper executable" }
        }
        git("remote", "add", "nostr", "nostr://npub1testonlynotarealkey/repo")
        val env = guard()
        val extra = mapOf("PATH" to bin.absolutePath + File.pathSeparator + System.getenv("PATH"), "FAKE_NOSTR_LOG" to log.absolutePath)
        // The stand-in takes a push when nothing stops it.
        assertEquals(0, asTheSession(emptyMap(), "git push -q nostr HEAD:refs/heads/pr/probe", extra), "the stand-in helper doesn't work")
        assertTrue(log.readText().contains("pr/probe"))
        check(log.delete())
        assertTrue(asTheSession(env, "git push -q nostr agent/w", extra) != 0, "pushed a branch to a nostr remote")
        assertTrue(asTheSession(env, "git push -q nostr HEAD:refs/heads/pr/x", extra) != 0, "opened a proposal through a nostr remote")
        assertTrue(!log.exists(), "the nostr remote helper was handed a push")
    }

    companion object {
        /** Takes pushes and logs them: capabilities, an empty ref list, `ok` for each pushed ref. */
        private val FAKE_NOSTR_HELPER =
            """
            |#!/bin/sh
            |pushes=
            |while read -r line; do
            |    case "${'$'}line" in
            |    capabilities) printf 'push\n\n' ;;
            |    list*) printf '\n' ;;
            |    push\ *) echo "${'$'}line" >>"${'$'}FAKE_NOSTR_LOG"; pushes="${'$'}pushes ${'$'}{line#*:}" ;;
            |    "")
            |        [ -n "${'$'}pushes" ] || exit 0
            |        for r in ${'$'}pushes; do printf 'ok %s\n' "${'$'}r"; done
            |        printf '\n'
            |        pushes=
            |        ;;
            |    esac
            |done
            |
            """.trimMargin()
    }
}
