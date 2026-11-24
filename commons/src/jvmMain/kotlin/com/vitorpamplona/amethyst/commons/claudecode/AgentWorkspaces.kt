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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import com.vitorpamplona.agents.deleteTree as removeTree

/** One repository of a workspace: where it comes from, its folder, and the branch the session works on. */
@Immutable
@Serializable
data class WorkspaceRepo(
    val url: String,
    /** Its folder inside the workspace. */
    val name: String,
    /** What a pull request merges into (the repository's default branch). */
    val baseBranch: String,
    val branch: String,
)

/**
 * A session's own copy of a group of repositories: a folder with a fresh clone of each, made
 * from the local mirror, on a branch of its own. Agents work here and never touch another
 * session's files, branches or git settings.
 */
@Immutable
@Serializable
data class AgentWorkspace(
    val id: String,
    val title: String,
    val dir: String,
    val repos: List<WorkspaceRepo>,
    val createdAt: Long,
    /** The pull request each repository's branch opened, by its folder name, as last seen. */
    val pullRequests: Map<String, SavedPullRequest> = emptyMap(),
    /** The agent it was made for ([com.vitorpamplona.agents.AgentBackend.id]): a chat reopened in it uses the same. */
    val agentId: String? = null,
    /**
     * When its clones were removed to give the disk back ([AgentWorkspaces.free]); null while they
     * are there. [AgentWorkspaces.restore] makes them again.
     */
    val freedAt: Long? = null,
    /** When its clones were last made again ([AgentWorkspaces.restore]): the user came back to it. */
    val restoredAt: Long? = null,
    /** What the agent asked the user to publish, by repository folder: a pull request waiting on their click. */
    val publishRequests: Map<String, PublishRequest> = emptyMap(),
) {
    /** Its clones are gone for now, kept as what brings them back. */
    val isFreed: Boolean get() = freedAt != null

    /**
     * Since when the record says nobody needed its clones: its work done ([finishedAt]), or, when
     * the user brought it back after that, since then. Null while its work isn't done. The
     * automatic cleanup also counts its sessions' last activity, which the record doesn't hold.
     */
    val idleSince: Long?
        get() = finishedAt?.let { maxOf(it, restoredAt ?: 0L) }

    /**
     * What the agent is told about the workstream: its folder holds one clone per repository and is not
     * a repository itself, so git runs inside them; and what it may publish.
     */
    val brief: String
        get() =
            buildString {
                appendLine("This folder is a workstream with one clone per repository; it is not a git repository itself. Run git inside the clones:")
                repos.forEach { appendLine("- ${it.name}/: ${it.url}, on branch ${it.branch} (from ${it.baseBranch})") }
                append(
                    "Commit your work on those branches. You may push them and open a pull request when the user asks. " +
                        "Merging, tags, deleting branches and anything on the default branch are the user's.",
                )
            }

    /** The repositories, as the user knows them: `owner/name` from each URL. */
    val repoLabels: List<String> get() = repos.map { AgentWorkspaces.ownerAndName(it.url) }

    /**
     * When the workspace's work was done: every repository's pull request merged or closed, as
     * of the last of them. Null while one is open, or a repository has none.
     */
    val finishedAt: Long?
        get() {
            if (repos.isEmpty()) return null
            val prs = repos.map { pullRequests[it.name] ?: return null }
            if (prs.any { it.isOpen }) return null
            return prs.maxOf { it.closedAt ?: it.checkedAt }
        }
}

/**
 * A pull request the agent asked for ([PublishToolServer]): its title and description, ready for the
 * user to review, change and open, or dismiss. The agent never publishes it itself.
 */
@Immutable
@Serializable
data class PublishRequest(
    val title: String,
    val body: String = "",
    val draft: Boolean = false,
    val requestedAt: Long = 0,
)

/** A pull request as last seen, kept so the session list can show it without asking GitHub. */
@Immutable
@Serializable
data class SavedPullRequest(
    val url: String,
    val state: String,
    val isDraft: Boolean = false,
    val checkedAt: Long = 0,
    /** When it was first seen merged or closed. */
    val closedAt: Long? = null,
) {
    val isOpen get() = state == "OPEN"
    val isMerged get() = state == "MERGED"
}

/** The user's choices about workspaces, kept with them. */
@Immutable
@Serializable
data class AgentPreferences(
    /**
     * Gives disk back on its own: frees a workspace's clones a day after its pull requests are all
     * merged or closed, and when its last session is archived.
     */
    val autoCleanup: Boolean = true,
    /** Sessions archived by the user, for agents that keep no archive of their own. */
    val archivedSessions: Set<String> = emptySet(),
    /**
     * The permission mode each session was last left in ([PermissionMode.wire] by session id), so
     * it reopens in it after a restart. Sessions in the default mode have no entry.
     */
    val sessionModes: Map<String, String> = emptyMap(),
    /** What each guarded session may push ([PushScope]), by session id, as it first started: later starts reuse it. */
    val pushScopes: Map<String, PushScope> = emptyMap(),
)

/** How often workspaces cloned a repository, and when last: what the repository picker leads with. */
@Immutable
@Serializable
data class RepoUsage(
    val count: Int,
    val lastUsed: Long,
)

/** A named list of repositories that sessions often need together. */
@Immutable
@Serializable
data class RepoGroup(
    val name: String,
    val urls: List<String>,
)

/**
 * What [AgentWorkspaces.free] kept of one repository besides its commits and settings: enough to
 * show it, and to put it back on its branch, without its clone.
 */
@Immutable
@Serializable
data class FreedRepo(
    /** The branch it was on. */
    val head: String,
    /** Commits on [head] beyond its base, and their subjects, newest first. */
    val ahead: Int = 0,
    val subjects: List<String> = emptyList(),
    /** The NIP-34 repository [head] is linked to, as its git setting holds it ([NostrRepoRef.fromConfig]). */
    val nostrRepo: String? = null,
)

/** Why [AgentWorkspaces.free] kept a workspace's clones: [repo] holds work only its folder has. */
class UnsavedWorkException(
    val repo: String,
) : IOException("$repo has work that is not committed")

/** A repository of a workspace, now: commits not yet in its base, files not yet committed. */
@Immutable
data class WorkspaceRepoStatus(
    val repo: WorkspaceRepo,
    val ahead: Int,
    val uncommitted: Int,
    /** Lines the branch changed since its base, uncommitted work included. */
    val insertions: Int = 0,
    val deletions: Int = 0,
)

/**
 * Workspaces for agent sessions, under [root]:
 *
 * ```
 * mirrors/<host>-<owner>-<name>.git   a bare mirror of each repository, fetched before each use
 * workstreams/<workstream>/<name>/                  a workstream's clones, made from the mirrors
 * meta/<workstream>.json                     what each workstream holds (outside the agent's folder)
 * meta/<workstream>.gone                     a workstream that was removed: its name is not given out again
 * meta/<workstream>.freed/                   a freed workstream's commits (a bundle), settings and summary, by repository
 * groups.json                         the repository groups the user saved
 * ```
 *
 * A clone made from a local mirror hardlinks its objects, so it takes seconds and little disk,
 * yet it is a whole repository: its config, hooks, refs and stashes are its own, unlike a git
 * worktree's. Its `origin` points at the real remote, where the user's push and pull request go.
 */
class AgentWorkspaces(
    val root: File = File(System.getProperty("user.home"), ".amethyst/agents"),
) {
    val mirrorsDir = File(root, "mirrors")
    val workstreamsDir = File(root, "workstreams")
    private val metaDir = File(root, "meta")
    private val groupsFile = File(root, "groups.json")
    private val lastAgentFile = File(metaDir, "last-agent.txt")
    private val usageFile = File(root, "usage.json")
    private val preferencesFile = File(root, "preferences.json")

    /** One fetch at a time per mirror: fifteen sessions starting together share one. */
    private val mirrorLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Calls waiting on or inside [withMirror], by mirror path: a mirror a new workspace is about
     * to clone from is in use before its workspace's meta is saved, and cleanup must not take it.
     */
    private val mirrorUsers = ConcurrentHashMap<String, Int>()

    /** The mirror of [url], made or brought up to date. Blocks on the network: minutes for a big first clone. */
    suspend fun refreshMirror(url: String): File = withMirror(url) { it }

    /**
     * Brings [url]'s mirror up to date, then runs [use] on it while no other session can fetch
     * into it: a clone copying its object files must not race a fetch or its maintenance.
     */
    private suspend fun <T> withMirror(
        url: String,
        use: (File) -> T,
    ): T {
        val mirror = mirrorDir(url)
        mirrorUsers.merge(mirror.absolutePath, 1, Int::plus)
        try {
            return lockedMirror(url, mirror, use)
        } finally {
            mirrorUsers.compute(mirror.absolutePath) { _, n -> if (n == null || n <= 1) null else n - 1 }
        }
    }

    private suspend fun <T> lockedMirror(
        url: String,
        mirror: File,
        use: (File) -> T,
    ): T =
        mirrorLocks.getOrPut(mirror.path) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                if (File(mirror, "HEAD").isFile) {
                    narrowToBranchesAndTags(mirror)
                    // Git's automatic maintenance would run detached, past the lock, and repack
                    // the objects a clone is copying: off here, and run in step below instead.
                    val fetched = GitWorkspace.run(mirror, listOf("git", "-c", "maintenance.auto=false", "-c", "gc.auto=0", "fetch", "--prune", "origin"), timeoutSeconds = NETWORK_TIMEOUT)
                    if (fetched.code != 0) throw IOException(fetched.output.trim().ifEmpty { "git fetch failed" })
                    GitWorkspace.run(mirror, listOf("git", "-c", "gc.autoDetach=false", "gc", "--auto", "--quiet"), timeoutSeconds = NETWORK_TIMEOUT)
                } else {
                    if (!mirrorsDir.mkdirs() && !mirrorsDir.isDirectory) throw IOException("Could not create $mirrorsDir")
                    // Bare, not --mirror: a mirror fetches every ref, GitHub's refs/pull/* for
                    // each pull request ever opened included, which no workspace clone uses.
                    val cloned = GitWorkspace.run(mirrorsDir, listOf("git", "clone", "--bare", "--quiet", url, mirror.absolutePath), timeoutSeconds = NETWORK_TIMEOUT)
                    if (cloned.code != 0) throw IOException(cloned.output.trim().ifEmpty { "git clone --bare failed" })
                    // A bare clone records no fetch refspec: later fetches would update nothing.
                    git(mirror, "config", "--replace-all", "remote.origin.fetch", BRANCHES)
                    git(mirror, "config", "--add", "remote.origin.fetch", TAGS)
                }
                use(mirror)
            }
        }

    /**
     * A new workspace with a clone of each of [urls], every one on [branch] from its default
     * branch. A repository that fails leaves no half-made workspace behind.
     */
    suspend fun create(
        urls: List<String>,
        title: String?,
        /** Every repository's branch; by default `agent/<workstream>`, made unique in each repository. */
        branch: String? = null,
        agentId: String? = null,
        /** The NIP-34 repository each URL belongs to, when it is one: its branch is linked to it, to propose back. */
        nostrRepos: Map<String, NostrRepoRef> = emptyMap(),
        /** The workstream's folder, from [claim]; claimed here when null. */
        claimed: File? = null,
    ): AgentWorkspace {
        val clean = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) throw IOException("No repositories")
        clean.firstOrNull { !GitWorkspace.isSafeRemoteUrl(it) }?.let { throw IOException("Not a repository URL: $it") }
        if (branch != null && !GitWorkspace.isSafeBranchName(branch)) throw IOException("Not a branch name: $branch")
        val dir = claimed ?: claim(title, clean)
        val id = dir.name
        return withContext(Dispatchers.IO) {
            try {
                // Checked here, in the cleanup's reach: a refused name leaves no folder behind.
                if (branch == null && !GitWorkspace.isSafeBranchName("agent/$id")) throw IOException("Not a branch name: agent/$id")
                val names = HashSet<String>()
                val repos =
                    clean.map { url ->
                        // Two repositories with the same name get distinct folders.
                        var name = repoName(url)
                        while (!names.add(name)) name += "-2"
                        val target = File(dir, name)
                        // Hardlinks the mirror's objects: seconds, so other sessions barely wait.
                        val repoBranch =
                            withMirror(url) { mirror ->
                                git(dir, "clone", "--quiet", mirror.absolutePath, target.absolutePath, timeout = NETWORK_TIMEOUT)
                                branch ?: unusedBranch(mirror, "agent/" + id)
                            }
                        git(target, "remote", "set-url", "origin", url)
                        val base =
                            GitWorkspace
                                .git(target, "symbolic-ref", "--short", "HEAD")
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() } ?: "main"
                        git(target, "checkout", "--quiet", "-b", repoBranch)
                        // A NIP-34 repository: the branch remembers it and its base, as a checkout
                        // made from the repository's own screen does, so the Propose screen works here.
                        nostrRepos[url]?.let { GitWorkspace.link(target, repoBranch, repo = it, base = "origin/$base") }
                        WorkspaceRepo(url, name, base, repoBranch)
                    }
                val workspace =
                    AgentWorkspace(
                        id = id,
                        title = title?.takeIf { it.isNotBlank() } ?: repos.joinToString(" + ") { it.name },
                        dir = dir.absolutePath,
                        repos = repos,
                        createdAt = System.currentTimeMillis(),
                        agentId = agentId,
                    )
                save(workspace)
                countUses(workspace.repos.map { it.url })
                workspace
            } catch (e: Throwable) {
                // Any failure, cancellation included (the user left the start screen mid-clone):
                // a folder with no meta is one no list, cleanup or storage screen ever sees.
                if (!deleteTree(dir)) Log.w(TAG) { "Could not remove the half-made $dir" }
                throw e
            }
        }
    }

    /**
     * Makes the folder of a new workstream called [name] (or after its first repository), and returns
     * it: `workstreams/<name>`, or `<name>-2` and on when that one is taken or was used by a removed workstream,
     * whose archived conversations would otherwise show up in the new one.
     */
    suspend fun claim(
        name: String?,
        urls: List<String>,
    ): File {
        val stem = slug(name?.takeIf { it.isNotBlank() } ?: urls.firstOrNull()?.let(::repoName) ?: "workstream")
        return withContext(Dispatchers.IO) {
            if (!workstreamsDir.mkdirs() && !workstreamsDir.isDirectory) throw IOException("Could not create $workstreamsDir")
            // Workstreams started together with the same name each get a folder: mkdir is the claim.
            var n = 1
            var id = stem
            while (File(metaDir, "$id$GONE").exists() || !File(workstreamsDir, id).mkdir()) {
                if (++n > 1000) throw IOException("Could not create a folder for $stem")
                id = "$stem-$n"
            }
            File(workstreamsDir, id)
        }
    }

    /** [wanted], or `wanted-2` and on: a branch [mirror] doesn't have, so the push never meets an old one. */
    private fun unusedBranch(
        mirror: File,
        wanted: String,
    ): String {
        var n = 1
        var name = wanted
        while (GitWorkspace.run(mirror, listOf("git", "show-ref", "--verify", "--quiet", "refs/heads/$name")).code == 0) {
            name = "$wanted-${++n}"
        }
        return name
    }

    /** Every workspace, newest first. */
    fun list(): List<AgentWorkspace> =
        metaDir
            .listFiles { f -> f.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { read(it) }
            .sortedByDescending { it.createdAt }

    /** Whether [dir] is inside a workstream's folder, made or still being made: no record is read. */
    fun isInWorkstreams(dir: File): Boolean {
        val root = workstreamsDir.absoluteFile.normalize().toPath()
        val path = dir.absoluteFile.normalize().toPath()
        return path.startsWith(root) && path != root
    }

    /** The workspace [dir] is in (its folder or one inside it), or null. */
    fun of(dir: File): AgentWorkspace? {
        val root = workstreamsDir.absoluteFile.normalize().toPath()
        val path = dir.absoluteFile.normalize().toPath()
        if (!path.startsWith(root) || path == root) return null
        // workstreams/<id>/…: its record is meta/<id>.json, read alone.
        val id = root.relativize(path).getName(0).toString()
        return read(File(metaDir, "$id.json"))?.takeIf { File(it.dir).absoluteFile.normalize().toPath() == root.resolve(id) }
    }

    /**
     * Records [pr] as the pull request of [repo] in the workspace [id] (null: it has none), and
     * returns the workspace as saved. Nothing happens to a workspace that is gone.
     */
    fun recordPullRequest(
        id: String,
        repo: String,
        pr: PullRequestInfo?,
    ): AgentWorkspace? =
        update(id) { workspace ->
            val next =
                if (pr == null) {
                    workspace.pullRequests - repo
                } else {
                    // When it first closed: a later check of a merged one must not move the date.
                    val before = workspace.pullRequests[repo]
                    val now = System.currentTimeMillis()
                    val closedAt = if (pr.isOpen) null else before?.closedAt?.takeIf { before.url == pr.url } ?: now
                    workspace.pullRequests + (repo to SavedPullRequest(pr.url, pr.state, pr.isDraft, now, closedAt))
                }
            // A new check time alone is no change: the record is rewritten only when something moved.
            if (next.mapValues { it.value.copy(checkedAt = 0) } == workspace.pullRequests.mapValues { it.value.copy(checkedAt = 0) }) workspace else workspace.copy(pullRequests = next)
        }

    /**
     * Records what the agent asked to publish from [repo] in the workspace [id] (null: nothing,
     * once the user opened or dismissed it), and returns the workspace as saved. Nothing happens
     * to a workspace that is gone.
     */
    fun recordPublishRequest(
        id: String,
        repo: String,
        request: PublishRequest?,
    ): AgentWorkspace? =
        update(id) { workspace ->
            val next = if (request == null) workspace.publishRequests - repo else workspace.publishRequests + (repo to request)
            if (next == workspace.publishRequests) workspace else workspace.copy(publishRequests = next)
        }

    /** The workspace [id] as saved now, or null when it is gone. */
    fun get(id: String): AgentWorkspace? = read(File(metaDir, "$id.json"))

    /**
     * Re-reads the workspace [id] and saves what [change] makes of it, unless that is the same; the
     * one way a record changes once made. Each change reads the record as the last one left it, so
     * a pull request seen during a restore's minutes of git work isn't lost when the restore saves.
     * Null, and nothing saved, when the workspace is gone.
     */
    @Synchronized
    private fun update(
        id: String,
        change: (AgentWorkspace) -> AgentWorkspace,
    ): AgentWorkspace? {
        val workspace = get(id) ?: return null
        val updated = change(workspace)
        if (updated != workspace) save(updated)
        return updated
    }

    /** The agent the last session started with ([com.vitorpamplona.agents.AgentBackend.id]), offered first next time. */
    var lastAgent: String?
        get() = if (lastAgentFile.isFile) lastAgentFile.readText().trim().ifEmpty { null } else null
        set(value) {
            try {
                if (!metaDir.mkdirs() && !metaDir.isDirectory) throw IOException("Could not create $metaDir")
                lastAgentFile.writeText(value.orEmpty())
            } catch (e: IOException) {
                Log.w(TAG, "Could not save the last agent", e)
            }
        }

    /** Commits each repository has beyond its base, and files not yet committed. */
    suspend fun status(workspace: AgentWorkspace): List<WorkspaceRepoStatus> =
        withContext(Dispatchers.IO) {
            workspace.repos.map { repo ->
                val dir = File(workspace.dir, repo.name)
                val ahead = GitWorkspace.git(dir, "rev-list", "--count", "origin/${repo.baseBranch}..HEAD")?.trim()?.toIntOrNull() ?: 0
                val uncommitted = GitWorkspace.git(dir, "status", "--porcelain")?.lines()?.count { it.isNotBlank() } ?: 0
                val (insertions, deletions) = GitWorkspace.lineStat(dir, "origin/${repo.baseBranch}")
                WorkspaceRepoStatus(repo, ahead, uncommitted, insertions, deletions)
            }
        }

    /** Removes the workspace's clones and its record. The mirrors stay for the next session. */
    suspend fun delete(workspace: AgentWorkspace) =
        withContext(Dispatchers.IO) {
            val dir = File(workspace.dir)
            if (dir.exists() && !deleteTree(dir)) throw IOException("Could not remove $dir")
            val meta = File(metaDir, "${workspace.id}.json")
            if (meta.exists() && !meta.delete()) Log.w(TAG) { "Could not remove $meta" }
            val freed = freedDir(workspace.id)
            if (freed.exists() && !deleteTree(freed)) Log.w(TAG) { "Could not remove $freed" }
            trashDirs(workspace.id).forEach { if (!deleteTree(it)) Log.w(TAG) { "Could not remove $it" } }
            // Its conversations stay, archived, and Claude files them by folder: a new workstream in this
            // folder would show them as its own.
            try {
                File(metaDir, "${workspace.id}$GONE").writeText(workspace.title)
            } catch (e: IOException) {
                Log.w(TAG, "Could not mark ${workspace.id} as removed", e)
            }
        }

    /**
     * Removes [workspace]'s clones to give their disk back, keeping what brings them back ([FreedFiles]):
     * each repository's local branches beyond its base, and where they were pushed, as a git bundle
     * (a few KB: the base is in the mirror); its branch and remote settings (the NIP-34 link, the
     * comments already reviewed, the user's fork); and a [FreedRepo] summary, the branch it was on
     * among it. The folder stays: it keeps the workstream's name, and its sessions' folder.
     *
     * Refuses with [UnsavedWorkException], and changes nothing, while a repository holds what a
     * bundle can't carry ([canBundle]). The caller ends the agents working there first.
     */
    suspend fun free(workspace: AgentWorkspace): AgentWorkspace =
        withContext(Dispatchers.IO) {
            if (workspace.isFreed) return@withContext workspace
            val clones = workspace.repos.map { it to File(workspace.dir, it.name) }.filter { it.second.isDirectory }
            clones.firstOrNull { !canBundle(it.second) }?.let { throw UnsavedWorkException(it.first.name) }
            val staging = File(metaDir, "${workspace.id}$FREED.tmp")
            if (staging.exists() && !deleteTree(staging)) throw IOException("Could not remove $staging")
            if (!staging.mkdirs()) throw IOException("Could not create $staging")
            val saved = freedDir(workspace.id)
            try {
                clones.forEach { (repo, dir) -> keep(repo, dir, FreedFiles(staging, repo.name)) }
                if (saved.exists() && !deleteTree(saved)) throw IOException("Could not remove $saved")
                if (!staging.renameTo(saved)) throw IOException("Could not move $staging to $saved")
            } catch (e: Throwable) {
                if (!deleteTree(staging)) Log.w(TAG) { "Could not remove $staging" }
                throw e
            }
            // Only now: what brings the clones back is on disk. Each clone leaves the folder whole,
            // by a rename into the trash (meta/ is on the same disk), so none is ever left half
            // removed for a restore to take as whole. Anything else in the folder (notes, the
            // agent's settings) stays.
            val trash = emptyTrash(workspace.id)
            val moved = mutableListOf<Pair<File, File>>()

            /** Puts back what moved: the workspace is as it was, and nothing says it was freed. */
            fun undo(e: Exception): Nothing {
                if (moved.all { (dir, target) -> target.renameTo(dir) }) {
                    if (!deleteTree(saved)) Log.w(TAG) { "Could not remove $saved" }
                    if (!deleteTree(trash)) Log.w(TAG) { "Could not remove $trash" }
                    throw e
                }
                // A clone is stuck in the trash: the bundle is what brings it back now.
                Log.w(TAG, "Could not put the clones of ${workspace.dir} back", e)
                update(workspace.id) { it.copy(freedAt = System.currentTimeMillis()) }
                throw e
            }
            try {
                clones.forEach { (repo, dir) ->
                    val target = File(trash, repo.name)
                    if (!dir.renameTo(target)) throw IOException("Could not move $dir aside")
                    moved.add(dir to target)
                }
            } catch (e: IOException) {
                undo(e)
            }
            val freed =
                try {
                    update(workspace.id) { it.copy(freedAt = System.currentTimeMillis()) } ?: workspace.copy(freedAt = System.currentTimeMillis())
                } catch (e: IOException) {
                    undo(e)
                }
            if (!deleteTree(trash)) Log.w(TAG) { "Could not empty $trash; it goes with the next free or delete" }
            freed
        }

    /** Writes what [free] keeps of the clone [dir] of [repo] into [files]. */
    private fun keep(
        repo: WorkspaceRepo,
        dir: File,
        files: FreedFiles,
    ) {
        val head = GitWorkspace.git(dir, "symbolic-ref", "--quiet", "--short", "HEAD")?.trim()?.ifEmpty { null } ?: throw UnsavedWorkException(repo.name)
        // Relative to the base the clone came from: whatever the mirror holds later, it holds that.
        // The branches, and where they were pushed other than origin (the user's fork), so a
        // restored branch knows it was published; origin's come fresh from the mirror. (After
        // --remotes, git matches --exclude without the refs/remotes/ prefix.)
        val base = "refs/remotes/origin/${repo.baseBranch}"
        val refs = arrayOf("--branches", "--exclude=origin/*", "--remotes", "--not", base)
        val toKeep =
            GitWorkspace.git(dir, "rev-list", "--count", *refs)?.trim()?.toIntOrNull()
                ?: throw IOException("Could not count the commits of ${repo.name}")
        if (toKeep > 0) git(dir, "bundle", "create", "--quiet", files.bundle.absolutePath, *refs)
        // The branches' settings (the NIP-34 link, comments reviewed) and the remotes (the fork a pull request opened from).
        GitWorkspace.git(dir, "config", "--local", "--null", "--get-regexp", "^(branch|remote)\\.")?.takeIf { it.isNotEmpty() }?.let { files.config.writeText(it) }
        val subjects =
            GitWorkspace
                .git(dir, "log", "--format=%s", "$base..HEAD")
                ?.lines()
                ?.filter { it.isNotBlank() }
                .orEmpty()
        val nostr = GitWorkspace.git(dir, "config", "--get", "branch.$head.${GitWorkspace.KEY_REPO}")?.trim()?.ifEmpty { null }
        files.summary.writeText(json.encodeToString(FreedRepo(head, subjects.size, subjects, nostr)))
    }

    /**
     * Makes a freed [workspace]'s clones again ([free]): from the mirrors, fetched first, then its
     * saved branches on top, the branch it was on checked out and its settings back. A repository
     * whose clone is still there is left as it is. A failure leaves it freed, with nothing half made.
     */
    suspend fun restore(workspace: AgentWorkspace): AgentWorkspace {
        if (!workspace.isFreed) return workspace
        val saved = freedDir(workspace.id)
        val dir = File(workspace.dir)
        val made = mutableListOf<File>()
        return withContext(Dispatchers.IO) {
            try {
                if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create $dir")
                workspace.repos.forEach { repo ->
                    val target = File(dir, repo.name)
                    val files = FreedFiles(saved, repo.name)
                    // One still there is kept only when whole: a delete that stopped partway leaves
                    // a .git whose refs read while its objects are gone.
                    if (target.exists() && isWholeClone(target, files, thorough = true)) return@forEach
                    if (target.exists() && !deleteTree(target)) throw IOException("Could not remove $target")
                    made.add(target)
                    restoreClone(repo, target, files)
                    if (!isWholeClone(target, files, thorough = false)) throw IOException("${repo.name} did not come back whole")
                }
            } catch (e: Throwable) {
                made.forEach { if (it.exists() && !deleteTree(it)) Log.w(TAG) { "Could not remove the half-made $it" } }
                throw e
            }
            val now = System.currentTimeMillis()
            val restored = update(workspace.id) { it.copy(freedAt = null, restoredAt = now) } ?: workspace.copy(freedAt = null, restoredAt = now)
            // Every clone is back and whole: only now may the bundles go.
            if (saved.exists() && !deleteTree(saved)) Log.w(TAG) { "Could not remove $saved" }
            restored
        }
    }

    /**
     * Whether [target] is a whole clone of what [files] kept: a repository whose saved branch and
     * every bundled branch resolve to commits it has, at the commits bundled. [thorough] also
     * walks every object (`fsck --connectivity-only`) and wants no file changed: for a clone found
     * in a freed workspace, which nothing vouches for.
     */
    private fun isWholeClone(
        target: File,
        files: FreedFiles,
        thorough: Boolean,
    ): Boolean {
        if (!File(target, ".git").isDirectory || GitWorkspace.git(target, "rev-parse", "--git-dir") == null) return false
        val head = readFreed(files.summary)?.head
        if (head != null && GitWorkspace.git(target, "rev-parse", "--verify", "--quiet", "refs/heads/$head^{commit}") == null) return false
        if (files.bundle.isFile) {
            val heads =
                GitWorkspace
                    .git(target, "bundle", "list-heads", files.bundle.absolutePath)
                    ?.lines()
                    ?.filter { it.isNotBlank() }
                    ?: return false
            val allThere =
                heads.all { line ->
                    val sha = line.substringBefore(' ')
                    val ref = line.substringAfter(' ')
                    GitWorkspace.git(target, "rev-parse", "--verify", "--quiet", "$ref^{commit}")?.trim() == sha &&
                        GitWorkspace.run(target, listOf("git", "rev-list", "--objects", "--quiet", sha, "--not", "--remotes=origin")).code == 0
                }
            if (!allThere) return false
        }
        if (!thorough) return true
        return GitWorkspace.run(target, listOf("git", "fsck", "--connectivity-only", "--no-dangling", "--no-progress"), timeoutSeconds = NETWORK_TIMEOUT).code == 0 &&
            GitWorkspace.git(target, "status", "--porcelain")?.isBlank() == true
    }

    /** Clones [repo] into [target] from its mirror, and puts back what [files] kept of it. */
    private suspend fun restoreClone(
        repo: WorkspaceRepo,
        target: File,
        files: FreedFiles,
    ) {
        // No checkout yet: the saved branches may replace the one the clone starts on.
        withMirror(repo.url) { mirror -> git(target.parentFile, "clone", "--quiet", "--no-checkout", mirror.absolutePath, target.absolutePath, timeout = NETWORK_TIMEOUT) }
        git(target, "remote", "set-url", "origin", repo.url)
        // Forced: a branch the agent moved past the base (its local main among them) comes back
        // where it left it, though the clone starts it at the base as the mirror has it now.
        if (files.bundle.isFile) git(target, "fetch", "--quiet", "--update-head-ok", files.bundle.absolutePath, "+refs/heads/*:refs/heads/*", "+refs/remotes/*:refs/remotes/*")
        val head = readFreed(files.summary)?.head ?: repo.branch
        // A branch with nothing of its own was not bundled: it starts again from the base, as now,
        // and like the one [create] made, tracks nothing until the user pushes it.
        if (GitWorkspace.git(target, "rev-parse", "--verify", "--quiet", "refs/heads/$head") == null) {
            git(target, "branch", "--no-track", head, "refs/remotes/origin/${repo.baseBranch}")
        }
        // Forced: the clone has no files yet, and nothing of its own to keep.
        git(target, "checkout", "--quiet", "--force", head)
        if (files.config.isFile) restoreConfig(target, files.config.readText())
        // The saved settings name origin as it was; the repository's URL is what it is now.
        git(target, "remote", "set-url", "origin", repo.url)
    }

    /** Sets the `branch.*` and `remote.*` settings [saved] holds (`git config --null --get-regexp`), replacing the clone's own. */
    private fun restoreConfig(
        dir: File,
        saved: String,
    ) {
        val entries =
            saved.split('\u0000').filter { it.isNotEmpty() }.map { entry ->
                val key = entry.substringBefore('\n')
                key to (if ('\n' in entry) entry.substringAfter('\n') else "true")
            }
        entries.map { it.first }.distinct().forEach { GitWorkspace.run(dir, listOf("git", "config", "--local", "--unset-all", it)) }
        entries.forEach { (key, value) -> git(dir, "config", "--local", "--add", key, value) }
    }

    /** What a freed [workspace] kept of each repository, by its folder name: to show it without its clones. */
    fun freedRepos(workspace: AgentWorkspace): Map<String, FreedRepo> {
        if (!workspace.isFreed) return emptyMap()
        val saved = freedDir(workspace.id)
        return workspace.repos.mapNotNull { repo -> readFreed(FreedFiles(saved, repo.name).summary)?.let { repo.name to it } }.toMap()
    }

    private fun readFreed(file: File): FreedRepo? =
        try {
            if (file.isFile) json.decodeFromString<FreedRepo>(file.readText()) else null
        } catch (e: SerializationException) {
            Log.w(TAG, "Unreadable $file", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "Unreadable $file", e)
            null
        }

    private fun freedDir(id: String) = File(metaDir, "$id$FREED")

    /**
     * An empty folder for [free] to move workspace [id]'s clones into before removing them, on the
     * same disk: `meta/<id>.trash/`, or, when an earlier one could not be emptied, one beside it.
     */
    private fun emptyTrash(id: String): File {
        val trash = File(metaDir, "$id$TRASH")
        val fresh = if (!trash.exists() || deleteTree(trash)) trash else File(metaDir, "$id$TRASH-${System.nanoTime()}")
        if (!fresh.mkdirs()) throw IOException("Could not create $fresh")
        return fresh
    }

    /** Every trash folder [free] left for workspace [id]. */
    private fun trashDirs(id: String): List<File> = metaDir.listFiles { f -> f.name == "$id$TRASH" || f.name.startsWith("$id$TRASH-") }.orEmpty().toList()

    /** Where a freed workspace keeps one repository, under `meta/<id>.freed/`. */
    private class FreedFiles(
        dir: File,
        repo: String,
    ) {
        val summary = File(dir, "$repo.json")
        val bundle = File(dir, "$repo.bundle")
        val config = File(dir, "$repo.config")
    }

    /**
     * Whether [free] would keep [workspace]'s clones, and a cleanup would leave it: a repository
     * holds work a bundle can't carry ([canBundle]).
     */
    suspend fun cannotFree(workspace: AgentWorkspace): Boolean =
        withContext(Dispatchers.IO) {
            !workspace.isFreed && workspace.repos.any { repo -> File(workspace.dir, repo.name).let { it.isDirectory && !canBundle(it) } }
        }

    /**
     * Whether all of the clone [dir]'s work is in its commits: no files left uncommitted (ignored
     * ones included, but not what a build makes again: [isRebuildable]), no stash, on a branch,
     * and no other worktree of it, whose files and `.git` link would go with the clone.
     */
    private fun canBundle(dir: File): Boolean =
        !hasUncommittedWork(dir) &&
            GitWorkspace.git(dir, "stash", "list")?.isBlank() == true &&
            GitWorkspace.git(dir, "symbolic-ref", "--quiet", "HEAD") != null &&
            GitWorkspace
                .git(dir, "worktree", "list", "--porcelain")
                ?.lines()
                ?.count { it.startsWith("worktree ") } == 1

    fun groups(): List<RepoGroup> =
        try {
            if (groupsFile.isFile) json.decodeFromString<List<RepoGroup>>(groupsFile.readText()) else emptyList()
        } catch (e: SerializationException) {
            Log.w(TAG, "Unreadable $groupsFile", e)
            emptyList()
        } catch (e: IOException) {
            Log.w(TAG, "Unreadable $groupsFile", e)
            emptyList()
        }

    /** Saves [group], replacing one of the same name; the most recent first. */
    @Synchronized
    fun saveGroup(group: RepoGroup) {
        val groups = listOf(group) + groups().filter { it.name != group.name }
        if (!root.mkdirs() && !root.isDirectory) throw IOException("Could not create $root")
        groupsFile.writeText(json.encodeToString(groups))
    }

    /** How often each repository was cloned into a workspace, by URL. */
    fun usage(): Map<String, RepoUsage> =
        try {
            if (usageFile.isFile) json.decodeFromString<Map<String, RepoUsage>>(usageFile.readText()) else emptyMap()
        } catch (e: SerializationException) {
            Log.w(TAG, "Unreadable $usageFile", e)
            emptyMap()
        } catch (e: IOException) {
            Log.w(TAG, "Unreadable $usageFile", e)
            emptyMap()
        }

    /** Counts one more use of each of [urls]. Kept apart from the workspaces: it outlives them. */
    @Synchronized
    private fun countUses(urls: List<String>) {
        val now = System.currentTimeMillis()
        val usage = usage().toMutableMap()
        urls.forEach { url -> usage[url] = RepoUsage((usage[url]?.count ?: 0) + 1, now) }
        try {
            usageFile.writeText(json.encodeToString(usage))
        } catch (e: IOException) {
            Log.w(TAG, "Could not save $usageFile", e)
        }
    }

    /** The user's choices about workspaces. */
    fun preferences(): AgentPreferences =
        try {
            if (preferencesFile.isFile) json.decodeFromString<AgentPreferences>(preferencesFile.readText()) else AgentPreferences()
        } catch (e: SerializationException) {
            Log.w(TAG, "Unreadable $preferencesFile", e)
            AgentPreferences()
        } catch (e: IOException) {
            Log.w(TAG, "Unreadable $preferencesFile", e)
            AgentPreferences()
        }

    /** Changes the preferences with [change], and saves them. */
    @Synchronized
    fun updatePreferences(change: (AgentPreferences) -> AgentPreferences): AgentPreferences {
        val next = change(preferences())
        try {
            if (!root.mkdirs() && !root.isDirectory) throw IOException("Could not create $root")
            preferencesFile.writeText(json.encodeToString(next))
        } catch (e: IOException) {
            Log.w(TAG, "Could not save $preferencesFile", e)
        }
        return next
    }

    /**
     * Turns a mirror made with `git clone --mirror` (fetches every ref) into one that fetches
     * branches and tags, and drops the other refs it holds (the pull requests under `refs/pull`): their objects go at
     * the next gc. Nothing to do for a mirror made since.
     */
    private fun narrowToBranchesAndTags(mirror: File) {
        if (GitWorkspace.git(mirror, "config", "--get", "remote.origin.mirror")?.trim() != "true") return
        git(mirror, "config", "--replace-all", "remote.origin.fetch", BRANCHES)
        git(mirror, "config", "--add", "remote.origin.fetch", TAGS)
        git(mirror, "config", "--unset", "remote.origin.mirror")
        val others =
            GitWorkspace
                .git(mirror, "for-each-ref", "--format=%(refname)")
                ?.lines()
                .orEmpty()
                .filter { it.isNotBlank() && !it.startsWith("refs/heads/") && !it.startsWith("refs/tags/") }
        if (others.isEmpty()) return
        val dropped = GitWorkspace.run(mirror, listOf("git", "update-ref", "--stdin"), input = others.joinToString("") { "delete $it\n" })
        if (dropped.code != 0) Log.w(TAG) { "Could not drop the extra refs of $mirror: ${dropped.output.trim()}" }
    }

    /** Files not committed in the clone [dir], ignored ones included; never what a build makes again. */
    private fun hasUncommittedWork(dir: File): Boolean =
        GitWorkspace
            .git(dir, "status", "--porcelain", "--ignored")
            ?.lines()
            ?.any { line -> line.isNotBlank() && !isRebuildable(line) } ?: true

    /** The disk a workspace's clones use, without the objects they share with the mirrors. */
    suspend fun sizeOf(workspace: AgentWorkspace): Long = withContext(Dispatchers.IO) { diskUse(File(workspace.dir)) }

    /** The disk the mirrors use. */
    suspend fun mirrorsSize(): Long = withContext(Dispatchers.IO) { diskUse(mirrorsDir, countShared = true) }

    /**
     * Mirrors no workspace uses and no session cloned in [days] days: they only take space. A
     * mirror with no record of use counts from when it was last fetched.
     */
    fun unusedMirrors(days: Int = UNUSED_MIRROR_DAYS): List<File> {
        val cutoff = System.currentTimeMillis() - days * DAY_MS
        val usage = usage().mapKeys { mirrorDir(it.key).absolutePath }
        val inUse = list().flatMap { ws -> ws.repos.map { mirrorDir(it.url).absolutePath } }.toSet()
        return mirrorsDir
            .listFiles { f -> f.isDirectory && f.name.endsWith(".git") }
            .orEmpty()
            .filter { mirror ->
                val path = mirror.absolutePath
                val lastUsed = usage[path]?.lastUsed ?: File(mirror, "FETCH_HEAD").takeIf { it.isFile }?.lastModified() ?: mirror.lastModified()
                path !in inUse && (mirrorUsers[path] ?: 0) == 0 && lastUsed < cutoff
            }
    }

    /**
     * Removes the mirrors [unusedMirrors] names, each under its lock and checked again there: a
     * session that started cloning from one meanwhile keeps it. Returns how many were removed.
     */
    suspend fun removeUnusedMirrors(days: Int = UNUSED_MIRROR_DAYS): Int {
        var removed = 0
        unusedMirrors(days).forEach { candidate ->
            mirrorLocks.getOrPut(candidate.path) { Mutex() }.withLock {
                withContext(Dispatchers.IO) {
                    if (unusedMirrors(days).none { it.absolutePath == candidate.absolutePath }) return@withContext
                    if (deleteTree(candidate)) removed++ else Log.w(TAG) { "Could not remove the unused mirror $candidate" }
                }
            }
        }
        return removed
    }

    /**
     * Compacts every mirror (`git gc`) and removes the unused ones; returns how many were
     * removed. Under each mirror's lock: no session clones from one while it is repacked.
     */
    suspend fun tidyMirrors(): Int {
        var removed = 0
        mirrorsDir.listFiles { f -> f.isDirectory && f.name.endsWith(".git") }.orEmpty().forEach { mirror ->
            mirrorLocks.getOrPut(mirror.path) { Mutex() }.withLock {
                withContext(Dispatchers.IO) {
                    // Checked under the lock: a session may have started using it since.
                    if (unusedMirrors().any { it.absolutePath == mirror.absolutePath }) {
                        if (deleteTree(mirror)) removed++ else Log.w(TAG) { "Could not remove $mirror" }
                    } else {
                        GitWorkspace.run(mirror, listOf("git", "-c", "gc.autoDetach=false", "gc", "--quiet", "--prune=now"), timeoutSeconds = NETWORK_TIMEOUT)
                    }
                }
            }
        }
        return removed
    }

    /** The URL each mirror on this computer was made from. */
    fun mirrorUrls(): List<String> =
        mirrorsDir
            .listFiles { f -> f.isDirectory && f.name.endsWith(".git") }
            .orEmpty()
            .mapNotNull { mirror -> GitWorkspace.git(mirror, "config", "--get", "remote.origin.url")?.trim()?.takeIf { it.isNotEmpty() } }

    /** Forgets the group named [name]. */
    @Synchronized
    fun deleteGroup(name: String) {
        val groups = groups()
        if (groups.none { it.name == name }) return
        groupsFile.writeText(json.encodeToString(groups.filter { it.name != name }))
    }

    private fun save(workspace: AgentWorkspace) {
        if (!metaDir.mkdirs() && !metaDir.isDirectory) throw IOException("Could not create $metaDir")
        File(metaDir, "${workspace.id}.json").writeText(json.encodeToString(workspace))
    }

    private fun read(file: File): AgentWorkspace? =
        try {
            json.decodeFromString<AgentWorkspace>(file.readText())
        } catch (e: SerializationException) {
            Log.w(TAG, "Unreadable $file", e)
            null
        } catch (e: IOException) {
            null
        }

    private fun git(
        dir: File,
        vararg args: String,
        timeout: Long = 60,
    ) {
        val result = GitWorkspace.run(dir, listOf("git") + args, timeoutSeconds = timeout)
        if (result.code != 0) throw IOException(result.output.trim().ifEmpty { "git ${args.first()} failed" })
    }

    /** The mirror's folder: host, owner and name from the URL, so two forks don't share one. */
    fun mirrorDir(url: String): File {
        val path =
            url
                .removeSuffix("/")
                .removeSuffix(".git")
                .substringAfter("://")
                .replace(Regex("^[^@/]*@"), "")
                .replace(':', '/')
        return File(mirrorsDir, path.split('/').filter { it.isNotEmpty() }.joinToString("-") { slug(it) } + ".git")
    }

    companion object {
        private const val TAG = "AgentWorkspaces"
        private const val NETWORK_TIMEOUT = 900L

        /** The mark a removed workstream leaves in `meta/`, so its name is not given to a new one. */
        private const val GONE = ".gone"

        /** A freed workstream's folder in `meta/`. */
        private const val FREED = ".freed"

        /** Where a workstream's clones wait to be removed, in `meta/`. */
        private const val TRASH = ".trash"

        /**
         * Folders a build or a package manager makes again from what is committed: an ignored one
         * of these names goes with its clone. Any other ignored folder (a virtualenv, IDE settings,
         * data, secrets) is work only that folder holds.
         */
        private val REBUILDABLE_DIRS =
            setOf(
                "build",
                "out",
                "target",
                "dist",
                "node_modules",
                ".gradle",
                ".kotlin",
                ".cxx",
                ".externalNativeBuild",
                "__pycache__",
                ".pytest_cache",
                ".mypy_cache",
                ".ruff_cache",
                ".next",
                ".nuxt",
                ".turbo",
                ".parcel-cache",
                ".dart_tool",
                "DerivedData",
            )

        /** Files operating systems drop in folders: never anyone's work. */
        private val OS_CLUTTER = setOf(".DS_Store", "Thumbs.db", "desktop.ini")

        /**
         * Whether a `git status --porcelain --ignored` [line] is something a build makes again or
         * clutter: an ignored build-output directory ([REBUILDABLE_DIRS]) as a whole, or an OS
         * file, ignored or not.
         */
        internal fun isRebuildable(line: String): Boolean {
            val ignored = line.startsWith("!! ")
            if (!ignored && !line.startsWith("?? ")) return false
            val path = line.drop(3).trim('"')
            val name = path.removeSuffix("/").substringAfterLast('/')
            return (ignored && path.endsWith("/") && name in REBUILDABLE_DIRS) || name in OS_CLUTTER
        }

        private const val BRANCHES = "+refs/heads/*:refs/heads/*"
        private const val TAGS = "+refs/tags/*:refs/tags/*"
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * The repository's name from its URL: `amethyst` for `https://github.com/vitorpamplona/amethyst.git`,
         * and for a local path on Windows (`C:\repos\amethyst`) too.
         */
        fun repoName(url: String): String =
            url
                .trim()
                .replace('\\', '/')
                .removeSuffix("/")
                .removeSuffix(".git")
                .substringAfterLast('/')
                .substringAfterLast(':')
                .ifEmpty { "repo" }

        /** `owner/name` for a repository URL (`vitorpamplona/amethyst`); the name alone when it has no owner. */
        fun ownerAndName(url: String): String {
            val path =
                url
                    .trim()
                    .replace('\\', '/')
                    .removeSuffix("/")
                    .removeSuffix(".git")
                    .substringAfter("://")
                    .replace(Regex("^[^@/]*@"), "")
                    .replace(':', '/')
                    .split('/')
                    .filter { it.isNotEmpty() }
            // The first part is the host, except for a local path.
            val parts = if (url.contains("://") || url.contains('@')) path.drop(1) else path
            // A GRASP server names the owner by their npub: unreadable, and the name says enough.
            if (parts.size >= 2 && parts[parts.size - 2].startsWith("npub1")) return parts.last()
            return parts.takeLast(2).joinToString("/").ifEmpty { repoName(url) }
        }

        /** The web page of a repository URL, for a link: `https://github.com/owner/name`; null when it has none. */
        fun webUrl(url: String): String? {
            val clean = url.trim().removeSuffix("/").removeSuffix(".git")
            return when {
                clean.startsWith("https://") -> clean
                clean.startsWith("http://") -> clean
                // git@github.com:owner/name
                Regex("^[^@/]+@[^:/]+:.+").matches(clean) -> "https://" + clean.substringAfter('@').replaceFirst(':', '/')
                clean.startsWith("ssh://") -> "https://" + clean.removePrefix("ssh://").replace(Regex("^[^@/]*@"), "").replace(Regex(":\\d+/"), "/")
                else -> null
            }
        }

        /** Removes [dir] and everything in it, links but never what they point to: see [com.vitorpamplona.agents.deleteTree]. */
        fun deleteTree(dir: File): Boolean = removeTree(dir)

        private const val DAY_MS = 24 * 60 * 60 * 1000L
        const val UNUSED_MIRROR_DAYS = 60

        /**
         * The bytes the files under [dir] take. A file hardlinked elsewhere (a clone's objects,
         * shared with its mirror) is left out unless [countShared]: removing [dir] frees none of
         * it. Where the file system can't say (Windows), every file counts.
         */
        fun diskUse(
            dir: File,
            countShared: Boolean = false,
        ): Long {
            if (!dir.exists()) return 0
            val unix = !countShared && "unix" in FileSystems.getDefault().supportedFileAttributeViews()
            var total = 0L
            try {
                Files.walkFileTree(
                    dir.toPath(),
                    object : SimpleFileVisitor<Path>() {
                        override fun visitFile(
                            file: Path,
                            attrs: BasicFileAttributes,
                        ): FileVisitResult {
                            val shared = unix && attrs.isRegularFile && ((Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS) as? Int) ?: 1) > 1
                            if (attrs.isRegularFile && !shared) total += attrs.size()
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFileFailed(
                            file: Path,
                            exc: IOException,
                        ): FileVisitResult = FileVisitResult.CONTINUE
                    },
                )
            } catch (e: IOException) {
                Log.w(TAG, "Could not measure $dir", e)
            }
            return total
        }

        fun slug(text: String): String =
            text
                .lowercase()
                .replace(Regex("[^a-z0-9._-]+"), "-")
                // Git refuses `..` in a branch name, and one ending in `.` or `.lock`.
                .replace(Regex("\\.{2,}"), ".")
                .trim('-', '.')
                .take(40)
                .trimEnd('-', '.')
                .removeSuffix(".lock")
                .trimEnd('-', '.')
                .ifEmpty { "workstream" }
    }
}
