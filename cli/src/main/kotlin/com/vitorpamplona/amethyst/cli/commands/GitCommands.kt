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
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip34Git.state.tags.RefTag

/**
 * `amy git <verb>` — NIP-34 Nostr-native git collaboration, adapted to amy's
 * event-publish model. Mirrors the pure-Nostr surface of `nak git` and `ngit`:
 * repository announcements + state, patches, pull requests, issues, threaded
 * comments (NIP-22), and status updates. Thin assembly only — every event lives
 * in quartz's `nip34Git` package; the shared glue is in [GitSupport].
 *
 * Out of scope: the git *packfile* transport (`clone` / `fetch` / `push` of real
 * git objects to clone/GRASP servers). That needs a git plumbing layer, not an
 * event builder — see `cli/ROADMAP.md`.
 */
object GitCommands {
    val USAGE: String =
        """
        |amy git — NIP-34 Nostr-native git collaboration
        |
        |Repository:
        |  git init [--name N] [--description D]          bootstrap a repo from the local git checkout
        |      [--clone URL[,URL]] [--relay URL[,URL]]     (derives name/clone/earliest-commit/state via
        |      [--no-state] [--repo PATH] [--d ID]          `git`; flags override; publishes 30617 + 30618)
        |  git announce --name N [--description D]        publish a kind:30617 repo announcement
        |      [--clone URL[,URL]] [--web URL[,URL]]       (--d / --identifier sets the identifier;
        |      [--relay URL[,URL]] [--maintainer HEX[,]]    defaults to name)
        |      [--hashtag T[,T]] [--earliest-commit C]
        |      [--personal-fork] [--d ID | --identifier ID]
        |  git state REPO|IDENTIFIER                       publish a kind:30618 repository state
        |      [--head BRANCH] [--branch name=commit[,…]]   (branches/tags as name=commit CSV)
        |      [--tag name=commit[,…]]
        |  git list [USER]                                list a user's repo announcements (default self)
        |  git show NADDR|kind:pubkey:id                  print one repo announcement
        |  git grasp list [USER] | set URL[,URL]          a user's GRASP hosting-server list (kind 10317)
        |
        |Read repo content (git smart-HTTP, read-only — needs a reachable git host):
        |  git browse REPO [PATH] [--ref R] [--clone URL] list a repo's tree at PATH (default root)
        |  git cat REPO PATH [--ref R] [--out FILE]       print (or write) a file's contents at a ref
        |  git log REPO [--ref R] [--depth N]             recent commit history (most recent first)
        |
        |Issues / patches / pull requests:
        |  git issue REPO --subject S [BODY]              publish a kind:1621 issue (BODY arg or stdin)
        |      [--hashtag T[,T]]
        |  git patch REPO [--file PATH]                   publish a kind:1617 patch (git format-patch
        |      [--root|--root-revision] [--commit C]        from --file or stdin)
        |      [--parent-commit P] [--in-reply-to ID]
        |  git apply PATCH_ID [--check|--print]           apply a fetched kind:1617 patch to the local tree
        |      [--repo PATH]                                (default: `git am`; --check dry-runs; --print emits it)
        |  git pr REPO --commit TIP --clone URL[,URL]     publish a kind:1618 pull request [DESC arg]
        |      [--subject S] [--branch-name N] [--merge-base C] [--label L[,L]]
        |  git pr-update PR --commit TIP --clone URL[,URL] publish a kind:1619 pull-request update
        |  git issues|patches|prs REPO                    list a repo's issues/patches/PRs + status
        |      [--open|--applied|--closed|--draft|--status a,b] [--limit N]
        |  git thread EVENT_ID                            print one item + its status timeline + comments
        |
        |Comments, labels & status:
        |  git comment TARGET [BODY]                      NIP-22 kind:1111 comment (BODY arg or stdin)
        |  git label TARGET LABEL[,LABEL] [--namespace N] NIP-32 kind:1985 labels on an issue/patch/PR
        |  git open|applied|close|draft TARGET [MESSAGE]  publish a kind:1630/1631/1632/1633 status
        |      applied: [--merge-commit C] [--commit C[,C]] [--patch ID[,ID]]
        |
        |Every write verb takes [--relay URL[,URL]] to override the target relays
        |(default: the repo's advertised relays, else your outbox).
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "git",
            tail,
            "git <init|announce|state|list|show|grasp|browse|cat|log|issue|patch|apply|pr|comment|label|open|applied|close|draft|issues|patches|prs|thread>",
            mapOf(
                "init" to { rest -> GitInitCommand.init(dataDir, rest) },
                "announce" to { rest -> announce(dataDir, rest) },
                "state" to { rest -> state(dataDir, rest) },
                "list" to { rest -> list(dataDir, rest) },
                "show" to { rest -> show(dataDir, rest) },
                "grasp" to { rest -> GitGraspCommands.dispatch(dataDir, rest) },
                "browse" to { rest -> GitBrowseCommands.browse(dataDir, rest) },
                "cat" to { rest -> GitBrowseCommands.cat(dataDir, rest) },
                "log" to { rest -> GitBrowseCommands.log(dataDir, rest) },
                "issue" to { rest -> issue(dataDir, rest) },
                "issues" to { rest -> GitReadCommands.issues(dataDir, rest) },
                "patch" to { rest -> GitPatchCommands.patch(dataDir, rest) },
                "patches" to { rest -> GitReadCommands.patches(dataDir, rest) },
                "pr" to { rest -> GitPrCommands.pr(dataDir, rest) },
                "pr-update" to { rest -> GitPrCommands.prUpdate(dataDir, rest) },
                "prs" to { rest -> GitReadCommands.prs(dataDir, rest) },
                "thread" to { rest -> GitReadCommands.thread(dataDir, rest) },
                "comment" to { rest -> GitCommentCommand.comment(dataDir, rest) },
                "label" to { rest -> GitLabelCommand.label(dataDir, rest) },
                "apply" to { rest -> GitApplyCommand.apply(dataDir, rest) },
                "open" to { rest -> GitStatusCommands.open(dataDir, rest) },
                "applied" to { rest -> GitStatusCommands.applied(dataDir, rest) },
                "merged" to { rest -> GitStatusCommands.applied(dataDir, rest) },
                "resolved" to { rest -> GitStatusCommands.applied(dataDir, rest) },
                "close" to { rest -> GitStatusCommands.close(dataDir, rest) },
                "draft" to { rest -> GitStatusCommands.draft(dataDir, rest) },
            ),
            help = USAGE,
        )

    private suspend fun announce(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val name = args.flag("name") ?: return Output.error("bad_args", "git announce requires --name")
        // `--identifier` is the spelled-out alias of `--d` (the d-tag) — read
        // eagerly so passing both spellings doesn't trip rejectUnknown().
        val identifierAlias = args.flag("identifier")
        val identifier = args.flag("d") ?: identifierAlias ?: name

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val template =
                GitRepositoryEvent.build(
                    name = name,
                    description = args.flag("description"),
                    webUrls = GitSupport.csv(args, "web"),
                    cloneUrls = GitSupport.csv(args, "clone"),
                    relays = GitSupport.csv(args, "relay"),
                    maintainers = GitSupport.csv(args, "maintainer"),
                    hashtags = GitSupport.csv(args, "hashtag"),
                    earliestUniqueCommit = args.flag("earliest-commit"),
                    personalFork = args.bool("personal-fork"),
                    dTag = identifier,
                )
            val signed = ctx.signer.sign(template)
            val targets = RawEventSupport.publishTargets(ctx, args)
            args.rejectUnknown()
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "address" to Address.assemble(signed.kind, signed.pubKey, identifier),
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private suspend fun state(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val ref = args.positional(0, "repo-identifier-or-naddr")
        val addr = GitSupport.resolveAddress(ref)
        val dTag = addr?.dTag ?: ref
        val head = args.flag("head")
        val branches = GitSupport.keyValueCsv(args, "branch")
        val tagRefs = GitSupport.keyValueCsv(args, "tag")
        args.rejectUnknown("relay")
        if (branches.isEmpty() && tagRefs.isEmpty() && head == null) {
            return Output.error("bad_args", "git state needs at least one --branch, --tag, or --head")
        }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val refs =
                branches.map { RefTag.branch(it.first, it.second) } +
                    tagRefs.map { RefTag.tag(it.first, it.second) }
            val template = GitRepositoryStateEvent.build(dTag = dTag, refs = refs, head = head)
            val signed = ctx.signer.sign(template)
            // Deliver to the announcement's advertised relays (the announcement may
            // be someone else's when we're a maintainer publishing state for it).
            val announceOwner = addr?.pubKeyHex ?: ctx.identity.pubKeyHex
            val repo = GitSupport.fetchRepo(ctx, Address(GitRepositoryEvent.KIND, announceOwner, dTag), args)
            val targets = GitSupport.deliveryTargets(ctx, repo, args)
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "address" to Address.assemble(GitRepositoryStateEvent.KIND, signed.pubKey, dTag),
                    "branches" to branches.size,
                    "tags" to tagRefs.size,
                    "head" to head,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        // Read-only: runs anonymously when there is no account (defaults to
        // the anonymous key, so pass a USER to list someone's repos).
        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val author = args.positionalOrNull(0)?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val relays = RawEventSupport.queryTargets(ctx, args)
            args.rejectUnknown()
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
        // `--relay` is read later inside fetchRepo's queryTargets.
        args.rejectUnknown("relay")
        val addr = GitSupport.resolveAddress(coord) ?: return Output.error("bad_args", "expected an naddr or kind:pubkey:identifier (or pubkey:identifier)")
        if (addr.kind != GitRepositoryEvent.KIND) {
            return Output.error("bad_args", "not a git repository address (expected kind ${GitRepositoryEvent.KIND}, got ${addr.kind})")
        }

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val repo = GitSupport.fetchRepo(ctx, addr, args) ?: return Output.error("not_found", "no repository announcement found for $coord")
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
        val addr = GitSupport.resolveAddress(coord) ?: return Output.error("bad_args", "expected an naddr or kind:pubkey:identifier")
        val body = args.positionalOrNull(1) ?: ""
        val topics = GitSupport.csv(args, "hashtag")
        // `--relay` is read later (deliveryTargets + fetchRepo's queryTargets).
        args.rejectUnknown("relay")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val repo = GitSupport.fetchRepo(ctx, addr, args) ?: return Output.error("not_found", "no repository announcement found for $coord")
            val template =
                GitIssueEvent.build(
                    subject = subject,
                    content = body,
                    repository = EventHintBundle(repo),
                    notify = emptyList(),
                    topics = topics,
                )
            val signed = ctx.signer.sign(template)
            val targets = GitSupport.deliveryTargets(ctx, repo, args)
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "repository" to Address.assemble(addr.kind, addr.pubKeyHex, addr.dTag),
                    "subject" to subject,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
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
