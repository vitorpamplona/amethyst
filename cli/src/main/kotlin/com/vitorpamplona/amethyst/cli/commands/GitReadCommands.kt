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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent

/**
 * Read-side `amy git` verbs — list a repository's issues / patches / pull
 * requests with their derived status, and print one collaboration thread with
 * its status timeline and comments. All read-only (anonymous-capable).
 */
object GitReadCommands {
    private val STATUS_KINDS =
        listOf(
            GitStatusEvent.KIND_OPEN,
            GitStatusEvent.KIND_APPLIED,
            GitStatusEvent.KIND_CLOSED,
            GitStatusEvent.KIND_DRAFT,
        )

    /** Idle timeout for the list reads — tighter than `drainAllPages`' 30s default so a dead relay can't stall a list. */
    private const val READ_TIMEOUT_MS = 12_000L

    /** Max event ids per `#e` status filter — many relays cap tag-filter values around 100, so stay well under. */
    private const val STATUS_ID_CHUNK = 50

    suspend fun issues(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = listItems(dataDir, rest, GitIssueEvent.KIND)

    suspend fun patches(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = listItems(dataDir, rest, GitPatchEvent.KIND)

    suspend fun prs(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = listItems(dataDir, rest, GitPullRequestEvent.KIND)

    /** Shared list path for issues (1621) / patches (1617) / pull requests (1618). */
    private suspend fun listItems(
        dataDir: DataDir,
        rest: Array<String>,
        itemKind: Int,
    ): Int {
        val args = Args(rest)
        val coord = args.positional(0, "repo-naddr-or-coordinates")
        val addr =
            GitSupport.resolveAddress(coord)
                ?: return Output.error("bad_args", "expected an naddr or kind:pubkey:identifier")
        val limit = args.intFlag("limit", 100)
        val wanted = statusFilter(args)
        args.rejectUnknown("relay")

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val repoAddress = GitSupport.repoCoordinate(addr)
            // Fetch the announcement once: it supplies both the maintainer set
            // (status authority) AND the advertised relays the events actually live
            // on. Reading only from general relays misses GRASP-hosted repos.
            val repo = GitSupport.fetchRepo(ctx, addr, args)
            val relays = GitSupport.readTargets(ctx, repo, args)

            // Two queries so item volume and status volume never starve each other.
            // Items are paged to the limit; statuses are fetched separately by the
            // items' ids so derived status is never truncated by item volume.
            val itemEvents =
                ctx
                    .drainAllPages(
                        relays.associateWith { listOf(Filter(kinds = listOf(itemKind), tags = mapOf("a" to listOf(repoAddress)), limit = limit)) },
                        timeoutMs = READ_TIMEOUT_MS,
                    ).asSequence()
                    .map { it.second }
                    .filter { it.kind == itemKind }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
                    .toList()

            val statusesByRoot = fetchStatusesFor(ctx, relays, itemEvents.map { it.id }).groupBy { it.rootEventId() }
            val authorities = repoAuthorities(repo, addr)

            val items =
                itemEvents
                    .map { item -> GitSupport.targetSummary(item) + mapOf("status" to latestStatus(item, statusesByRoot, authorities)) }
                    .filter { wanted == null || it["status"] in wanted }

            Output.emit(mapOf("repository" to repoAddress, "count" to items.size, "items" to items))
            return 0
        }
    }

    /**
     * `amy git thread TARGET` — the target event plus its status timeline and
     * NIP-22 comments (and legacy kind:1622 replies).
     *
     * Scope: first-level replies/statuses that `e`-reference the target. Nested
     * comment trees and PR-update (1619) events (which use NIP-22 uppercase `E`)
     * are out of scope here.
     */
    @Suppress("DEPRECATION") // legacy kind:1622 GitReplyEvent is read for backward compatibility.
    suspend fun thread(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val ref = args.positional(0, "target-event-id")
        val id =
            GitSupport.resolveEventId(ref)
                ?: return Output.error("bad_args", "expected a note/nevent/64-hex event id")
        args.rejectUnknown("relay")

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val target =
                GitSupport.fetchEvent(ctx, id, args)
                    ?: return Output.error("not_found", "no event found for $ref")
            val repoATag = GitSupport.repositoryOf(target)
            val repo = repoATag?.let { GitSupport.fetchRepo(ctx, Address(it.kind, it.pubKeyHex, it.dTag), args) }
            val relays = GitSupport.readTargets(ctx, repo, args)
            // Everything that `e`-references the target: statuses, comments, replies.
            val related =
                ctx
                    .drain(
                        relays.associateWith {
                            listOf(Filter(kinds = STATUS_KINDS + listOf(CommentEvent.KIND, GitReplyEvent.KIND), tags = mapOf("e" to listOf(id))))
                        },
                        timeoutMs = READ_TIMEOUT_MS,
                    ).map { it.second }
                    .distinctBy { it.id }

            val authorities = repoATag?.let { repoAuthorities(repo, Address(it.kind, it.pubKeyHex, it.dTag)) } ?: setOf(target.pubKey)
            val statuses = related.filterIsInstance<GitStatusEvent>().filter { it.rootEventId() == id }
            val statusesByRoot = statuses.groupBy { it.rootEventId() }
            val comments =
                related
                    .filter { it.kind == CommentEvent.KIND || it.kind == GitReplyEvent.KIND }
                    .sortedBy { it.createdAt }
                    .map { mapOf("event_id" to it.id, "kind" to it.kind, "author" to it.pubKey, "created_at" to it.createdAt, "content" to it.content) }

            Output.emit(
                GitSupport.targetSummary(target) +
                    mapOf(
                        "content" to target.content,
                        "status" to latestStatus(target, statusesByRoot, authorities),
                        "status_events" to
                            statuses.sortedBy { it.createdAt }.map {
                                mapOf("event_id" to it.id, "status" to GitSupport.statusLabel(it.kind), "author" to it.pubKey, "created_at" to it.createdAt)
                            },
                        "comments" to comments,
                    ),
            )
            return 0
        }
    }

    // ------------------------------------------------------------------

    /**
     * Fetch the status events that `e`-reference [itemIds]. Chunked to stay under
     * relay tag-filter value caps, and paged (`drainAllPages`) so a heavily
     * reopened/closed item's older status can't fall off a single page.
     */
    private suspend fun fetchStatusesFor(
        ctx: Context,
        relays: Set<NormalizedRelayUrl>,
        itemIds: List<HexKey>,
    ): List<GitStatusEvent> {
        if (itemIds.isEmpty()) return emptyList()
        return itemIds
            .chunked(STATUS_ID_CHUNK)
            .flatMap { chunk ->
                ctx
                    .drainAllPages(
                        relays.associateWith { listOf(Filter(kinds = STATUS_KINDS, tags = mapOf("e" to chunk))) },
                        timeoutMs = READ_TIMEOUT_MS,
                    ).map { it.second }
            }.filterIsInstance<GitStatusEvent>()
            .distinctBy { it.id }
    }

    /** The set of pubkeys whose status is authoritative for a repo: the owner + declared maintainers. */
    private fun repoAuthorities(
        repo: GitRepositoryEvent?,
        addr: Address,
    ): Set<HexKey> =
        buildSet {
            add(addr.pubKeyHex)
            repo?.maintainers()?.let { addAll(it) }
        }

    /**
     * The authoritative status label for [item]: the newest status event (by
     * `created_at`, id as a deterministic tie-break) that `e`-roots this item and
     * is signed by the item author, the repo owner, or a maintainer. Defaults to
     * `open` when none exists. [statusesByRoot] is pre-grouped by root event id so
     * this is an O(1) lookup instead of an O(items × statuses) rescan.
     */
    private fun latestStatus(
        item: Event,
        statusesByRoot: Map<HexKey?, List<GitStatusEvent>>,
        authorities: Set<HexKey>,
    ): String {
        val allowed = authorities + item.pubKey
        val newest =
            statusesByRoot[item.id]
                ?.filter { it.pubKey in allowed }
                ?.maxWithOrNull(compareBy({ it.createdAt }, { it.id }))
        return GitSupport.statusLabel(newest?.kind)
    }

    /** Translate `--status a,b` plus the `--open/--applied/--closed/--draft/--all` bools into a wanted set (null = all). */
    private fun statusFilter(args: Args): Set<String>? {
        val explicit = GitSupport.csv(args, "status").toMutableSet()
        if (args.bool("open")) explicit.add("open")
        if (args.bool("applied")) explicit.add("applied")
        if (args.bool("closed")) explicit.add("closed")
        if (args.bool("draft")) explicit.add("draft")
        args.bool("all") // consumed; means "no filter"
        return explicit.takeIf { it.isNotEmpty() }
    }
}
