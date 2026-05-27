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
import com.vitorpamplona.amethyst.commons.actions.FollowActions
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

/**
 * `amy follow <user>` and `amy unfollow <user>` — update the active
 * account's NIP-02 kind:3 contact list.
 *
 * Both commands fetch the user's latest kind:3 from their outbox relays
 * before mutating, so concurrent follows from another client are preserved
 * (the new event is built on top of the freshest known list).
 *
 * Identifier formats accepted by `<user>`: npub / nprofile / 64-hex /
 * `name@domain.tld` — same set [Context.requireUserHex] handles.
 */
object FollowCommand {
    suspend fun follow(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = run(dataDir, rest, FollowOp.FOLLOW)

    suspend fun unfollow(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = run(dataDir, rest, FollowOp.UNFOLLOW)

    private enum class FollowOp { FOLLOW, UNFOLLOW }

    private suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
        op: FollowOp,
    ): Int {
        if (rest.isEmpty()) {
            val verb = if (op == FollowOp.FOLLOW) "follow" else "unfollow"
            return Output.error("bad_args", "$verb <user> [--timeout SECS]")
        }
        val userArg = rest[0]
        val args = Args(rest.drop(1).toTypedArray())
        val timeoutSecs = args.longFlag("timeout", 8L)

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val target = ctx.requireUserHex(userArg)
            val self = ctx.identity.pubKeyHex
            if (target == self) {
                return Output.error("bad_args", "cannot follow/unfollow yourself")
            }

            val outbox = ctx.outboxRelays()
            if (outbox.isEmpty()) {
                return Output.error("no_relays", "no outbox relays configured; run `amy relay add` or `amy create`")
            }

            val latest = fetchLatestContactList(ctx, self, outbox, timeoutSecs * 1000)
            val previouslyFollowed = latest?.isTaggedUser(target) ?: false

            // Relay hint embedded in the `p` tag for new follows — points
            // readers at a relay where they'll find the target's events.
            // Best-effort: first write relay from the target's cached
            // kind:10002 advertised relay list, null if we've never seen
            // one. Mirrors User.bestRelayHint() in the Android UI.
            val targetRelayHint =
                if (op == FollowOp.FOLLOW) {
                    ctx
                        .relaysOf(target)
                        ?.writeRelaysNorm()
                        ?.firstOrNull()
                } else {
                    null
                }

            val newEvent: ContactListEvent? =
                when (op) {
                    FollowOp.FOLLOW ->
                        FollowActions.buildFollow(
                            signer = ctx.signer,
                            pubkeyToFollow = target,
                            currentContactList = latest,
                            relayHint = targetRelayHint,
                        )
                    FollowOp.UNFOLLOW ->
                        FollowActions.buildUnfollow(
                            signer = ctx.signer,
                            pubkeyToUnfollow = target,
                            currentContactList = latest,
                        )
                }

            // No-op cases: already following / not following.
            if (newEvent == null || newEvent.id == latest?.id) {
                Output.emit(
                    mapOf(
                        "target" to target,
                        "op" to op.name.lowercase(),
                        "changed" to false,
                        "previously_followed" to previouslyFollowed,
                        "based_on" to latest?.id,
                        "follow_count" to (latest?.verifiedFollowKeySet()?.size ?: 0),
                    ),
                )
                return 0
            }

            val ack = ctx.publish(newEvent, outbox)
            Output.emit(
                mapOf(
                    "target" to target,
                    "op" to op.name.lowercase(),
                    "changed" to true,
                    "previously_followed" to previouslyFollowed,
                    "event_id" to newEvent.id,
                    "created_at" to newEvent.createdAt,
                    "based_on" to latest?.id,
                    "follow_count" to newEvent.verifiedFollowKeySet().size,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                    "rejected_by" to ack.filterValues { !it }.keys.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    /**
     * Fetch the freshest kind:3 for [pubKey] from [relays]. Returns null when
     * no relay surfaces one within the timeout. We never trust the local
     * store alone for the base event — a stale read here would silently drop
     * follows the user made from another client.
     */
    private suspend fun fetchLatestContactList(
        ctx: Context,
        pubKey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): ContactListEvent? {
        if (relays.isEmpty()) return null
        val filter = Filter(kinds = listOf(ContactListEvent.KIND), authors = listOf(pubKey), limit = 1)
        val received = ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs)
        return received
            .mapNotNull { (_, ev) -> ev as? ContactListEvent }
            .filter { it.pubKey == pubKey }
            .maxByOrNull { it.createdAt }
    }
}
