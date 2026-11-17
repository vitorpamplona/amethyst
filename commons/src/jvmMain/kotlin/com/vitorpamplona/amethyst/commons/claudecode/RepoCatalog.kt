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
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/** A repository the New session dialog offers to clone, and where the app knows it from. */
@Immutable
data class RepoCandidate(
    val url: String,
    /** `owner/name`, or the name a NIP-34 announcement gives it. */
    val label: String,
    val description: String?,
    val source: Source,
    /** Workspaces that cloned it, as counted on this computer. */
    val uses: Int = 0,
    /** Set for a NIP-34 repository: its address, so changes can go back as a proposal. */
    val nostr: NostrRepoRef? = null,
) {
    enum class Source {
        /** Cloned before: its mirror is on this computer. */
        LOCAL,
        GITHUB,
        NOSTR,
    }
}

/**
 * Where the repositories a session can clone come from: the mirrors already on this computer,
 * most used first; the user's GitHub repositories, through their `gh`; and the NIP-34
 * repositories Amethyst has seen, through their `https` clone URLs.
 */
object RepoCatalog {
    /** The repositories cloned before, most used first, then most recent. */
    fun local(workspaces: AgentWorkspaces): List<RepoCandidate> {
        val usage = workspaces.usage()
        val urls = (usage.keys + workspaces.mirrorUrls()).distinctBy { NostrWorkspaces.normalize(it) }
        return urls
            .map { url ->
                val used = usage[url]
                RepoCandidate(url, AgentWorkspaces.ownerAndName(url), null, RepoCandidate.Source.LOCAL, used?.count ?: 0) to (used?.lastUsed ?: 0L)
            }.sortedWith(compareByDescending<Pair<RepoCandidate, Long>> { it.first.uses }.thenByDescending { it.second })
            .map { it.first }
    }

    /** Every NIP-34 repository in the cache that can be cloned over `https` (or plain git). */
    fun nostr(): List<RepoCandidate> =
        LocalCache.addressables
            .filterIntoSet(GitRepositoryEvent.KIND) { _, note -> note.event is GitRepositoryEvent }
            .mapNotNull { note ->
                val event = note.event as? GitRepositoryEvent ?: return@mapNotNull null
                val url = cloneUrlOf(event.clones()) ?: return@mapNotNull null
                RepoCandidate(
                    url = url,
                    label = event.name()?.takeIf { it.isNotBlank() } ?: event.dTag(),
                    description = event.description()?.takeIf { it.isNotBlank() },
                    source = RepoCandidate.Source.NOSTR,
                    nostr = NostrRepoRef(event.pubKey, event.dTag(), GitRepositoryEvent.KIND),
                )
            }.distinctBy { NostrWorkspaces.normalize(it.url) }

    /** The NIP-34 repository a clone URL belongs to, when Amethyst has seen its announcement. */
    fun nostrRepoOf(url: String): NostrRepoRef? {
        val wanted = NostrWorkspaces.normalize(url)
        return LocalCache.addressables
            .filterIntoSet(GitRepositoryEvent.KIND) { _, note ->
                (note.event as? GitRepositoryEvent)?.clones()?.any { NostrWorkspaces.normalize(it) == wanted } == true
            }.firstNotNullOfOrNull { note -> (note.event as? GitRepositoryEvent)?.let { NostrRepoRef(it.pubKey, it.dTag(), GitRepositoryEvent.KIND) } }
    }

    /**
     * The clone URL git can fetch on its own: `https` first (GRASP servers serve it), then git or
     * ssh. Not `nostr://`, `htree://` and other schemes that need a helper program.
     */
    internal fun cloneUrlOf(clones: List<String>): String? = clones.firstOrNull { it.startsWith("https://") && gitCanClone(it) } ?: clones.firstOrNull(::gitCanClone)

    private val SCP_LIKE = Regex("^[^@/\\s]+@[^:/\\s]+:.+")

    /** Whether git clones [url] with no helper: one of its own transports, or `user@host:path`. */
    internal fun gitCanClone(url: String): Boolean {
        val lower = url.lowercase()
        val transport =
            lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("git://") || lower.startsWith("ssh://") ||
                (!url.contains("://") && SCP_LIKE.matches(url))
        return transport && GitWorkspace.isSafeRemoteUrl(url)
    }

    /** The user's own GitHub repositories, through their `gh`; empty when it isn't installed or signed in. */
    suspend fun github(gh: File? = GitHubPullRequests.find()): List<RepoCandidate> =
        withContext(Dispatchers.IO) {
            gh ?: return@withContext emptyList()
            val result = GitWorkspace.run(File(System.getProperty("user.home")), listOf(gh.path, "repo", "list", "--limit", "200", "--json", "nameWithOwner,url,description"), timeoutSeconds = 60)
            if (result.code != 0) emptyList() else parseGitHub(result.output, "nameWithOwner")
        }

    /** Public GitHub repositories matching [query], through the user's `gh`. */
    suspend fun searchGitHub(
        query: String,
        gh: File? = GitHubPullRequests.find(),
    ): List<RepoCandidate> =
        // Interruptible: newer typing cancels this search, and its `gh` goes with it.
        runInterruptible(Dispatchers.IO) {
            gh ?: return@runInterruptible emptyList()
            if (query.isBlank() || query.startsWith("-")) return@runInterruptible emptyList()
            val result = GitWorkspace.run(File(System.getProperty("user.home")), listOf(gh.path, "search", "repos", "--limit", "20", "--json", "fullName,url,description", "--", query), timeoutSeconds = 60)
            if (result.code != 0) emptyList() else parseGitHub(result.output, "fullName")
        }

    /** `gh`'s JSON list of repositories, as candidates. */
    internal fun parseGitHub(
        output: String,
        nameField: String,
    ): List<RepoCandidate> {
        val start = output.indexOf('[')
        if (start < 0) return emptyList()
        val array =
            try {
                Json.parseToJsonElement(output.substring(start)) as? JsonArray
            } catch (e: IllegalArgumentException) {
                null
            } ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val url = (obj["url"] as? JsonPrimitive)?.content?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            RepoCandidate(
                url = "$url.git",
                label = (obj[nameField] as? JsonPrimitive)?.content ?: AgentWorkspaces.ownerAndName(url),
                description = (obj["description"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() },
                source = RepoCandidate.Source.GITHUB,
            )
        }
    }

    /**
     * The candidates that fit [query], from every source, each repository once: the ones used
     * here first, then the user's own GitHub ones, then the NIP-34 ones Amethyst knows, then
     * GitHub's search, which is the widest and the least likely to be meant. With no query,
     * only the ones used here.
     */
    fun merge(
        query: String,
        local: List<RepoCandidate>,
        github: List<RepoCandidate>,
        nostr: List<RepoCandidate>,
        searched: List<RepoCandidate> = emptyList(),
        limit: Int = 30,
    ): List<RepoCandidate> {
        val q = query.trim()

        fun fits(candidate: RepoCandidate) =
            q.isEmpty() ||
                candidate.label.contains(q, ignoreCase = true) ||
                candidate.url.contains(q, ignoreCase = true) ||
                candidate.description?.contains(q, ignoreCase = true) == true
        val sources = if (q.isEmpty()) listOf(local) else listOf(local, github, nostr, searched)
        val seen = HashMap<String, Int>()
        val out = ArrayList<RepoCandidate>()
        for (candidate in sources.flatten()) {
            if (!fits(candidate)) continue
            val key = NostrWorkspaces.normalize(candidate.url)
            val at = seen[key]
            if (at == null) {
                seen[key] = out.size
                out.add(candidate)
            } else if (out[at].nostr == null && candidate.nostr != null) {
                // The same repository, also announced on Nostr: keep where it was found, and that it is.
                out[at] = out[at].copy(nostr = candidate.nostr)
            }
        }
        return out.take(limit)
    }
}
