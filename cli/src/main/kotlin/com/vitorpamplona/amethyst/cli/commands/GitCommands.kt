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
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * `amy git <announce|list|show|issue>` — NIP-34 Nostr-native git
 * repositories (nak's `git`, adapted to amy's event-publish model).
 *
 *   announce  publish a kind:30617 repository announcement
 *   list      list a user's repository announcements
 *   show      print one repository announcement (naddr or coordinates)
 *   issue     publish a kind:1621 issue against a repository
 *
 * nak's clone/push/pull (git-packfile transport over relays/GRASP) are out
 * of scope — they need a real git plumbing layer. These are the metadata /
 * collaboration events. Thin assembly only: every event lives in quartz
 * (`GitRepositoryEvent`, `GitIssueEvent`).
 */
object GitCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "git",
            tail,
            "git <announce|list|show|issue>",
            mapOf(
                "announce" to { rest -> announce(dataDir, rest) },
                "list" to { rest -> list(dataDir, rest) },
                "show" to { rest -> show(dataDir, rest) },
                "issue" to { rest -> issue(dataDir, rest) },
            ),
        )

    private suspend fun announce(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val name = args.flag("name") ?: return Output.error("bad_args", "git announce requires --name")
        val csv = { key: String ->
            args
                .flag(key)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val template =
                GitRepositoryEvent.build(
                    name = name,
                    description = args.flag("description"),
                    webUrls = csv("web"),
                    cloneUrls = csv("clone"),
                    relays = csv("relay"),
                    maintainers = csv("maintainer"),
                    hashtags = csv("hashtag"),
                    earliestUniqueCommit = args.flag("earliest-commit"),
                    personalFork = args.bool("personal-fork"),
                    dTag = args.flag("d") ?: name,
                )
            val signed = ctx.signer.sign(template)
            val targets = RawEventSupport.publishTargets(ctx, args)
            val ack = ctx.publish(signed, targets)
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "address" to Address.assemble(signed.kind, signed.pubKey, args.flag("d") ?: name),
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val author = args.positionalOrNull(0)?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val relays = RawEventSupport.queryTargets(ctx, args)
            val received = ctx.drain(relays.associateWith { listOf(Filter(kinds = listOf(GitRepositoryEvent.KIND), authors = listOf(author))) })
            val repos =
                received
                    .asSequence()
                    .map { it.second }
                    .filterIsInstance<GitRepositoryEvent>()
                    .distinctBy { it.dTag() }
                    .sortedByDescending { it.createdAt }
                    .map { repoSummary(it) }
                    .toList()
            Output.emit(mapOf("pubkey" to author, "count" to repos.size, "repositories" to repos))
            return 0
        }
    }

    private suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val coord = args.positional(0, "naddr-or-coordinates")
        val addr = resolveAddress(coord) ?: return Output.error("bad_args", "expected an naddr or kind:pubkey:identifier (or pubkey:identifier)")
        if (addr.kind != GitRepositoryEvent.KIND) {
            return Output.error("bad_args", "not a git repository address (expected kind ${GitRepositoryEvent.KIND}, got ${addr.kind})")
        }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val repo = fetchRepo(ctx, addr, args) ?: return Output.error("not_found", "no repository announcement found for $coord")
            Output.emit(repoSummary(repo) + mapOf("event_id" to repo.id, "content" to repo.content))
            return 0
        }
    }

    private suspend fun issue(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val coord = args.positional(0, "repo-naddr-or-coordinates")
        val subject = args.flag("subject") ?: return Output.error("bad_args", "git issue requires --subject")
        val addr = resolveAddress(coord) ?: return Output.error("bad_args", "expected an naddr or kind:pubkey:identifier")
        val body = args.positionalOrNull(1) ?: ""
        val topics =
            args
                .flag("hashtag")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val repo = fetchRepo(ctx, addr, args) ?: return Output.error("not_found", "no repository announcement found for $coord")
            val template =
                GitIssueEvent.build(
                    subject = subject,
                    content = body,
                    repository = EventHintBundle(repo),
                    notify = emptyList(),
                    topics = topics,
                )
            val signed = ctx.signer.sign(template)
            // Deliver to the repo's advertised relays when present, else our targets.
            val repoRelays = repo.relays().mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
            val targets = RawEventSupport.relayFlag(args).ifEmpty { repoRelays }.ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(signed, targets)
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "repository" to Address.assemble(addr.kind, addr.pubKeyHex, addr.dTag),
                    "subject" to subject,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }

    // ------------------------------------------------------------------

    private suspend fun fetchRepo(
        ctx: Context,
        addr: Address,
        args: Args,
    ): GitRepositoryEvent? {
        // Cache-first, then drain the query relays.
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

    /** Accept `naddr1…`, `kind:pubkey:dtag`, or `pubkey:dtag` (kind defaults to 30617). */
    private fun resolveAddress(input: String): Address? {
        val trimmed = input.trim().removePrefix("nostr:")
        if (trimmed.startsWith("naddr")) {
            val n = NAddress.parse(trimmed) ?: return null
            return Address(n.kind, n.author, n.dTag)
        }
        Address.parse(trimmed)?.let { return it }
        val parts = trimmed.split(":")
        return if (parts.size == 2 && parts[0].length == 64) Address(GitRepositoryEvent.KIND, parts[0], parts[1]) else null
    }

    private fun repoSummary(repo: GitRepositoryEvent): Map<String, Any?> =
        mapOf(
            "identifier" to repo.dTag(),
            "name" to repo.name(),
            "description" to repo.description(),
            "clone" to repo.clones(),
            "web" to repo.webs(),
            "relays" to repo.relays(),
            "maintainers" to repo.maintainers(),
            "hashtags" to repo.hashtags(),
            "address" to Address.assemble(repo.kind, repo.pubKey, repo.dTag()),
        )
}
