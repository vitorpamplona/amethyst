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

object GroupMembershipCommands {
    suspend fun remove(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "group remove <gid> <npub>")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            val target = ctx.requireUserHex(rest[1])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val leafIndex =
                ctx.marmot.leafIndexOf(gid, target)
                    ?: return Output.error("target_not_member", "$target is not a member of group $gid")

            val outbound = ctx.marmot.removeMember(nostrGroupId = gid, targetLeafIndex = leafIndex)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(outbound.signedEvent, targets)
            RawEventSupport.publishGuard(ack, outbound.signedEvent.id)?.let { return it }
            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "removed" to target,
                    "leaf_index" to leafIndex,
                    "epoch" to ctx.marmot.groupEpoch(gid),
                    "commit_event_id" to outbound.signedEvent.id,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    suspend fun leave(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "group leave <gid>")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }

            // An admin steps down first, handing the role on when no other admin
            // is a member; the manager publishes that commit itself.
            val departure = ctx.marmot.leave(gid, targets.toList())
            val ack = departure.selfRemove?.let { ctx.publish(it.signedEvent, targets) }
            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "self_demote_event_id" to departure.stepDown?.signedEvent?.id,
                    "proposal_event_id" to departure.selfRemove?.signedEvent?.id,
                ) + (ack?.let { RawEventSupport.ackFields(it) } ?: emptyMap()),
            )
            return 0
        }
    }

    /**
     * End the group for everyone. `group disband <gid> [--yes]`
     *
     * Terminal and irreversible: there is no un-disband commit, no later branch
     * supersedes it, and a replacement conversation is a NEW group with a new
     * id. `--yes` is required for exactly that reason — every other verb here
     * is recoverable by issuing its opposite, and this one is not.
     *
     * The commit shape, the admin check and the enablement step all live in
     * [com.vitorpamplona.marmot.client.MarmotManager.disbandGroup];
     * this only confirms the intent and reports what happened.
     */
    suspend fun disband(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "group disband <gid> --yes")
        val args = Args(rest.drop(1).toTypedArray())
        val confirmed = args.bool("yes")
        args.rejectUnknown()
        if (!confirmed) {
            return Output.error(
                "needs_confirmation",
                "disbanding ends the group for every member and cannot be undone; pass --yes",
            )
        }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            // Publish-before-apply happens INSIDE disbandGroup, which refuses
            // to terminalize on anything less than a relay OK — so unlike every
            // other group verb here there is no second publish afterwards. A
            // re-publish of a commit that already landed can still fail on a
            // dropped connection, and reporting a completed, irreversible
            // disband as a failure is the one wrong answer this command can
            // give.
            val outbound =
                try {
                    ctx.marmot.disbandGroup(gid, targets.toList())
                } catch (e: IllegalStateException) {
                    return Output.error("refused", e.message ?: "cannot disband group $gid")
                }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "disbanded" to (ctx.marmot.groupState(gid)?.isDisbanded == true),
                    "epoch" to ctx.marmot.groupEpoch(gid),
                    "commit_event_id" to outbound.signedEvent.id,
                ),
            )
            return 0
        }
    }
}
