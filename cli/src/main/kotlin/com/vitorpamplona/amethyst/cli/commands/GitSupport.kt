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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent

/**
 * Shared, protocol-free glue for the `amy git` (NIP-34) sub-verbs — address /
 * event-id parsing, cache-first fetch helpers, relay routing, and the read-side
 * summaries. Every real Nostr piece lives in quartz's `nip34Git` package; this
 * object only parses CLI input and shapes the `--json` result maps.
 */
object GitSupport {
    /** Accept `naddr1…`, `kind:pubkey:dtag`, or `pubkey:dtag` (kind defaults to 30617). */
    fun resolveAddress(input: String): Address? {
        val trimmed = input.trim().removePrefix("nostr:")
        if (trimmed.startsWith("naddr")) {
            val n = NAddress.parse(trimmed) ?: return null
            return Address(n.kind, n.author, n.dTag)
        }
        Address.parse(trimmed)?.let { return it }
        val parts = trimmed.split(":")
        return if (parts.size == 2 && parts[0].length == 64) Address(GitRepositoryEvent.KIND, parts[0], parts[1]) else null
    }

    /** Decode a `note1…` / `nevent1…` / 64-hex event reference to its raw event id. */
    fun resolveEventId(input: String): String? = decodeEventIdAsHexOrNull(input.trim().removePrefix("nostr:"))

    /** The `["a", …]` coordinate value of a repository announcement (`30617:pubkey:identifier`). */
    fun repoCoordinate(addr: Address): String = Address.assemble(GitRepositoryEvent.KIND, addr.pubKeyHex, addr.dTag)

    /**
     * Cache-first fetch of a repository announcement (kind 30617) for [addr],
     * draining the query relays only on a store miss.
     */
    suspend fun fetchRepo(
        ctx: Context,
        addr: Address,
        args: Args,
    ): GitRepositoryEvent? {
        val filter =
            Filter(
                kinds = listOf(GitRepositoryEvent.KIND),
                authors = listOf(addr.pubKeyHex),
                tags = mapOf("d" to listOf(addr.dTag)),
                limit = 1,
            )
        ctx.store
            .query<Event>(filter)
            .firstOrNull()
            ?.let { return it as? GitRepositoryEvent }
        val relays = RawEventSupport.queryTargets(ctx, args)
        ctx.drain(relays.associateWith { listOf(filter) })
        return ctx.store.query<Event>(filter).firstOrNull() as? GitRepositoryEvent
    }

    /**
     * Cache-first fetch of any event by its raw [idHex], draining the query
     * relays only on a store miss. Used to resolve the patch / PR / issue a
     * status, comment, or thread view targets.
     */
    suspend fun fetchEvent(
        ctx: Context,
        idHex: String,
        args: Args,
    ): Event? {
        val filter = Filter(ids = listOf(idHex), limit = 1)
        ctx.store
            .query<Event>(filter)
            .firstOrNull()
            ?.let { return it }
        val relays = RawEventSupport.queryTargets(ctx, args)
        ctx.drain(relays.associateWith { listOf(filter) })
        return ctx.store.query<Event>(filter).firstOrNull()
    }

    /** The repository this issue / patch / PR / status refers to, as an [ATag] (or null). */
    fun repositoryOf(event: Event): ATag? =
        when (event) {
            is GitIssueEvent -> event.repository()
            is GitPatchEvent -> event.repository()
            is GitPullRequestEvent -> event.repository()
            is GitPullRequestUpdateEvent -> event.repository()
            is GitStatusEvent -> event.repository()
            else -> null
        }

    /**
     * Where to deliver a collaboration event: the repo announcement's advertised
     * `relays` (the NIP-34 monitored set), else the explicit `--relay` flag, else
     * the account outbox. [repo] is the fetched announcement, or null when it
     * couldn't be resolved.
     */
    suspend fun deliveryTargets(
        ctx: Context,
        repo: GitRepositoryEvent?,
        args: Args,
    ): Set<NormalizedRelayUrl> {
        val flag = RawEventSupport.relayFlag(args)
        if (flag.isNotEmpty()) return flag
        val advertised =
            repo
                ?.relays()
                ?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                ?.toSet()
                .orEmpty()
        if (advertised.isNotEmpty()) return advertised
        // Falling back to the account outbox: the repo couldn't be resolved or
        // advertises no relays, so a maintainer watching only the repo's NIP-34
        // relays may never see this event. Say so instead of reporting silent success.
        System.err.println(
            "[git] warning: no repository relays known — delivering to your outbox. " +
                "A maintainer watching only the repo's relays may not see this; pass --relay to target them.",
        )
        return ctx.outboxRelays()
    }

    /**
     * Where to READ a repo's collaboration events from: the account's query
     * relays (explicit `--relay`, else outbox, else bootstrap) UNION the repo
     * announcement's own advertised `relays` — the NIP-34 monitored set where
     * patches/issues/statuses actually live. Without the union, a repo hosted on
     * GRASP/git-specific relays (which general relays don't mirror) reads empty.
     */
    suspend fun readTargets(
        ctx: Context,
        repo: GitRepositoryEvent?,
        args: Args,
    ): Set<NormalizedRelayUrl> {
        val advertised =
            repo
                ?.relays()
                ?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                ?.toSet()
                .orEmpty()
        return RawEventSupport.queryTargets(ctx, args) + advertised
    }

    /** Parse a `--flag a,b,c` CSV flag into a trimmed, non-empty list. */
    fun csv(
        args: Args,
        key: String,
    ): List<String> =
        args
            .flag(key)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /**
     * Parse a `--flag name=commit,other=commit` CSV of `key=value` pairs (used
     * for `git state --branch`/`--tag`). An entry without `=` is a bad-args
     * error so a typo can't silently drop a ref.
     */
    fun keyValueCsv(
        args: Args,
        key: String,
    ): List<Pair<String, String>> =
        csv(args, key).map { entry ->
            val idx = entry.indexOf('=')
            require(idx > 0 && idx < entry.length - 1) { "--$key expects name=commit pairs, got '$entry'" }
            entry.take(idx).trim() to entry.substring(idx + 1).trim()
        }

    /** The single-word status of a patch/PR/issue, derived from its status events. */
    fun statusLabel(kind: Int?): String =
        when (kind) {
            GitStatusEvent.KIND_OPEN -> "open"
            GitStatusEvent.KIND_APPLIED -> "applied"
            GitStatusEvent.KIND_CLOSED -> "closed"
            GitStatusEvent.KIND_DRAFT -> "draft"
            null -> "open"
            else -> "open"
        }

    /** A compact `--json`-friendly summary of an issue / patch / PR event. */
    fun targetSummary(event: Event): Map<String, Any?> {
        val base =
            mutableMapOf<String, Any?>(
                "event_id" to event.id,
                "kind" to event.kind,
                "author" to event.pubKey,
                "created_at" to event.createdAt,
            )
        when (event) {
            is GitIssueEvent -> {
                base["type"] = "issue"
                base["subject"] = event.subject()
                base["labels"] = event.topics()
            }
            is GitPatchEvent -> {
                base["type"] = "patch"
                base["subject"] = event.subject()
                base["commit"] = event.commit()
                base["parent_commit"] = event.parentCommit()
                base["root"] = event.isRoot()
            }
            is GitPullRequestEvent -> {
                base["type"] = "pull-request"
                base["subject"] = event.subject()
                base["current_commit"] = event.currentCommit()
                base["clone"] = event.cloneUrls()
                base["branch_name"] = event.branchName()
                base["labels"] = event.labels()
            }
            is GitPullRequestUpdateEvent -> {
                base["type"] = "pull-request-update"
                base["current_commit"] = event.currentCommit()
            }
        }
        return base
    }
}
