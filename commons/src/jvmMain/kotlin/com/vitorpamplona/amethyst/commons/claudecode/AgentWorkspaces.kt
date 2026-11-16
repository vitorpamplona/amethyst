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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
) {
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
    /** Removes a workspace's clones a day after its pull requests are all merged or closed. */
    val autoCleanup: Boolean = true,
    /** Sessions archived by the user, for agents that keep no archive of their own. */
    val archivedSessions: Set<String> = emptySet(),
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

/** A repository of a workspace, now: commits not yet in its base, files not yet committed. */
@Immutable
data class WorkspaceRepoStatus(
    val repo: WorkspaceRepo,
    val ahead: Int,
    val uncommitted: Int,
)

/**
 * Workspaces for agent sessions, under [root]:
 *
 * ```
 * mirrors/<host>-<owner>-<name>.git   a bare mirror of each repository, fetched before each use
 * sessions/<id>/<name>/               a session's clones, made from the mirrors
 * meta/<id>.json                      what each workspace holds (outside the agent's folder)
 * groups.json                         the repository groups the user saved
 * ```
 *
 * A clone made from a local mirror hardlinks its objects, so it takes seconds and little disk,
 * yet it is a whole repository: its config, hooks, refs and stashes are its own, unlike a git
 * worktree's. Its `origin` points at the real remote, where the user's push and pull request go.
 */
class AgentWorkspaces(
    val root: File = File(System.getProperty("user.home"), "AmethystAgents"),
) {
    val mirrorsDir = File(root, "mirrors")
    val sessionsDir = File(root, "sessions")
    private val metaDir = File(root, "meta")
    private val groupsFile = File(root, "groups.json")
    private val lastAgentFile = File(metaDir, "last-agent.txt")
    private val usageFile = File(root, "usage.json")
    private val preferencesFile = File(root, "preferences.json")

    /** One fetch at a time per mirror: fifteen sessions starting together share one. */
    private val mirrorLocks = ConcurrentHashMap<String, Mutex>()

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
        return mirrorLocks.getOrPut(mirror.path) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                if (File(mirror, "HEAD").isFile) {
                    // Git's automatic maintenance would run detached, past the lock, and repack
                    // the objects a clone is copying: off here, and run in step below instead.
                    val fetched = GitWorkspace.run(mirror, listOf("git", "-c", "maintenance.auto=false", "-c", "gc.auto=0", "fetch", "--prune", "origin"), timeoutSeconds = NETWORK_TIMEOUT)
                    if (fetched.code != 0) throw IOException(fetched.output.trim().ifEmpty { "git fetch failed" })
                    GitWorkspace.run(mirror, listOf("git", "-c", "gc.autoDetach=false", "gc", "--auto", "--quiet"), timeoutSeconds = NETWORK_TIMEOUT)
                } else {
                    if (!mirrorsDir.mkdirs() && !mirrorsDir.isDirectory) throw IOException("Could not create $mirrorsDir")
                    val cloned = GitWorkspace.run(mirrorsDir, listOf("git", "clone", "--mirror", url, mirror.absolutePath), timeoutSeconds = NETWORK_TIMEOUT)
                    if (cloned.code != 0) throw IOException(cloned.output.trim().ifEmpty { "git clone --mirror failed" })
                }
                use(mirror)
            }
        }
    }

    /**
     * A new workspace with a clone of each of [urls], every one on [branch] from its default
     * branch. A repository that fails leaves no half-made workspace behind.
     */
    suspend fun create(
        urls: List<String>,
        title: String?,
        branch: String = defaultBranchName(),
        agentId: String? = null,
        /** The NIP-34 repository each URL belongs to, when it is one: its branch is linked to it, to propose back. */
        nostrRepos: Map<String, NostrRepoRef> = emptyMap(),
    ): AgentWorkspace {
        val clean = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) throw IOException("No repositories")
        clean.firstOrNull { !GitWorkspace.isSafeRemoteUrl(it) }?.let { throw IOException("Not a repository URL: $it") }
        if (!GitWorkspace.isSafeBranchName(branch)) throw IOException("Not a branch name: $branch")
        val stem = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + slug(title ?: repoName(clean.first()))
        return withContext(Dispatchers.IO) {
            if (!sessionsDir.mkdirs() && !sessionsDir.isDirectory) throw IOException("Could not create $sessionsDir")
            // Sessions started in the same second with the same name each get a folder: mkdir is the claim.
            var n = 1
            var id = stem
            while (!File(sessionsDir, id).mkdir()) {
                if (++n > 100) throw IOException("Could not create a folder for $stem")
                id = "$stem-$n"
            }
            val dir = File(sessionsDir, id)
            try {
                val names = HashSet<String>()
                val repos =
                    clean.map { url ->
                        // Two repositories with the same name get distinct folders.
                        var name = repoName(url)
                        while (!names.add(name)) name += "-2"
                        val target = File(dir, name)
                        // Hardlinks the mirror's objects: seconds, so other sessions barely wait.
                        withMirror(url) { mirror -> git(dir, "clone", "--quiet", mirror.absolutePath, target.absolutePath, timeout = NETWORK_TIMEOUT) }
                        git(target, "remote", "set-url", "origin", url)
                        val base =
                            GitWorkspace
                                .git(target, "symbolic-ref", "--short", "HEAD")
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() } ?: "main"
                        git(target, "checkout", "--quiet", "-b", branch)
                        // A NIP-34 repository: the branch remembers it and its base, as a checkout
                        // made from the repository's own screen does, so the Propose screen works here.
                        nostrRepos[url]?.let { GitWorkspace.link(target, branch, repo = it, base = "origin/$base") }
                        WorkspaceRepo(url, name, base, branch)
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
            } catch (e: IOException) {
                if (!deleteTree(dir)) Log.w(TAG) { "Could not remove the half-made $dir" }
                throw e
            }
        }
    }

    /** Every workspace, newest first. */
    fun list(): List<AgentWorkspace> =
        metaDir
            .listFiles { f -> f.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { read(it) }
            .sortedByDescending { it.createdAt }

    /** The workspace [dir] is in (its folder or one inside it), or null. */
    fun of(dir: File): AgentWorkspace? {
        val path = dir.absoluteFile.normalize().path
        if (!path.startsWith(sessionsDir.absoluteFile.normalize().path + File.separator)) return null
        return list().firstOrNull { path == it.dir || path.startsWith(it.dir + File.separator) }
    }

    /**
     * Records [pr] as the pull request of [repo] in the workspace [id] (null: it has none), and
     * returns the workspace as saved. Nothing happens to a workspace that is gone.
     */
    @Synchronized
    fun recordPullRequest(
        id: String,
        repo: String,
        pr: PullRequestInfo?,
    ): AgentWorkspace? {
        val file = File(metaDir, "$id.json")
        val workspace = read(file) ?: return null
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
        if (next.mapValues { it.value.copy(checkedAt = 0) } == workspace.pullRequests.mapValues { it.value.copy(checkedAt = 0) }) return workspace
        val updated = workspace.copy(pullRequests = next)
        save(updated)
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
                WorkspaceRepoStatus(repo, ahead, uncommitted)
            }
        }

    /** Removes the workspace's clones and its record. The mirrors stay for the next session. */
    suspend fun delete(workspace: AgentWorkspace) =
        withContext(Dispatchers.IO) {
            val dir = File(workspace.dir)
            if (dir.exists() && !deleteTree(dir)) throw IOException("Could not remove $dir")
            val meta = File(metaDir, "${workspace.id}.json")
            if (meta.exists() && !meta.delete()) Log.w(TAG) { "Could not remove $meta" }
        }

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
     * Whether removing [workspace] would lose work: a repository with files not committed, or
     * commits that are on no remote (the user's push, to the repository or a fork, puts them there).
     */
    suspend fun hasUnsavedWork(workspace: AgentWorkspace): Boolean =
        withContext(Dispatchers.IO) {
            workspace.repos.any { repo ->
                val dir = File(workspace.dir, repo.name)
                if (!dir.isDirectory) return@any false
                val uncommitted = GitWorkspace.git(dir, "status", "--porcelain")?.lines()?.any { it.isNotBlank() } ?: true
                val unpushed =
                    GitWorkspace
                        .git(dir, "rev-list", "--count", "HEAD", "--not", "--remotes")
                        ?.trim()
                        ?.toIntOrNull()
                        ?.let { it > 0 } ?: true
                uncommitted || unpushed
            }
        }

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
                path !in inUse && lastUsed < cutoff
            }
    }

    /**
     * Compacts every mirror (`git gc`) and removes the unused ones; returns how many were
     * removed. Under each mirror's lock: no session clones from one while it is repacked.
     */
    suspend fun tidyMirrors(): Int {
        val unused = unusedMirrors().map { it.absolutePath }.toSet()
        var removed = 0
        mirrorsDir.listFiles { f -> f.isDirectory && f.name.endsWith(".git") }.orEmpty().forEach { mirror ->
            mirrorLocks.getOrPut(mirror.path) { Mutex() }.withLock {
                withContext(Dispatchers.IO) {
                    if (mirror.absolutePath in unused) {
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

        fun defaultBranchName(): String = "agent/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

        fun slug(text: String): String =
            text
                .lowercase()
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .trim('-', '.')
                .take(40)
                .ifEmpty { "workspace" }
    }
}
