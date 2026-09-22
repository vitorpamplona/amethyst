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
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.cordn.CordnGroupManager
import com.vitorpamplona.quartz.cordn.appGroupRef.CordnGroupRef
import com.vitorpamplona.quartz.cordn.groups.CordnCredential
import com.vitorpamplona.quartz.cordn.spec00Coordinator.JoinRequest
import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageReferences
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * `amy cordn group …`, `invite`, `request`, `welcomes`, `join`, `send`, `fetch`
 * — the group lifecycle over a live coordinator.
 *
 * ## What this is for
 *
 * A second implementation is the only thing that tests a protocol, and a
 * non-interactive client is the only way to drive one from a script. These
 * verbs exist so the cordn binding can be run end to end against the
 * reference coordinator without a phone, and so an interop scenario can be a
 * shell script rather than a description of one.
 *
 * ## A fetch is explicit here, and that is a real difference
 *
 * The Android app runs `CordnSyncLoop`, which catches up and then holds a
 * live subscription. A CLI invocation is a process: it cannot hold anything.
 * So `fetch` drains what the cursor has not seen and exits, and the cursor on
 * disk is the entire continuity mechanism between runs. `spec/02.md` §7 calls
 * a cursor a delivery primitive rather than message identity — here it is
 * also the only state that makes two separate invocations one conversation.
 *
 * A consequence worth stating: nothing arrives while amy is not running. A
 * message posted between two `fetch` calls is not lost — the coordinator holds
 * the ordered stream — but it is not seen until asked for. That is the correct
 * model for a script and the wrong one for a chat app, which is why both exist.
 */
internal object CordnGroupCommands {
    suspend fun group(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "cordn group",
            tail,
            "cordn group <create|list|info>",
            help = CordnCommands.USAGE,
            routes =
                mapOf(
                    "create" to { rest -> create(dataDir, rest) },
                    "list" to { rest -> list(dataDir, rest) },
                    "info" to { rest -> info(dataDir, rest) },
                ),
        )

    /**
     * `cordn group create --name NAME`
     *
     * The `gid` is ours to choose and the coordinator never interprets it
     * (`spec/00.md` §4). A random 128-bit hex value by default; `--gid` is
     * accepted because an interop script needs to name the group it is about
     * to assert things about. It must not be derived from the MLS `group_id`,
     * which is secret — here the relationship runs the other way, which is
     * what lets a joiner read the delivery id out of the Welcome alone.
     */
    private suspend fun create(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        val name = args.flag("name") ?: return Output.error("bad_args", "cordn group create needs --name")
        val about = args.flag("about") ?: ""
        val icon = args.flag("icon") ?: ""
        val imageUrl = args.flag("image") ?: ""
        val gid = args.flag("gid") ?: RandomInstance.bytes(16).joinToString("") { "%02x".format(it) }
        val admins =
            args
                .flag("admin")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        args.rejectUnknown("coordinator", "relay")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val metadata =
                try {
                    CordnGroupMetadata(name = name, description = about, adminPubkeys = admins, icon = icon, imageUrl = imageUrl)
                } catch (e: IllegalArgumentException) {
                    return@withSession Output.error("bad_args", e.message ?: "bad group metadata")
                }

            val group = scope.manager.createGroup(gid, metadata)
            val ref = scope.manager.shareRef(gid)

            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "gid" to gid,
                    "epoch" to group.epoch,
                    "ref" to ref.encode(),
                    "name" to name,
                    // Empty is not "no admins yet" — spec/01.md §5.3 makes it
                    // egalitarian, permanently, because there is no way to add
                    // an admin later to a field that has no enforcement behind
                    // it anyway.
                    "egalitarian" to admins.isEmpty(),
                    // Nothing has been posted: creating a group is local until
                    // the first message or the first invite.
                    "posted" to false,
                ),
            )
            0
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        args.rejectUnknown("coordinator", "relay")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "groups" to
                        scope.manager.gids.value.sorted().map { gid ->
                            val group = scope.manager.group(gid)
                            mapOf(
                                "gid" to gid,
                                "epoch" to group?.epoch,
                                "name" to group?.let { CordnGroupMetadata.fromExtensions(it.extensions)?.name },
                                "members" to group?.let { CordnCredential.memberIdentities(it).size },
                            )
                        },
                ),
            )
            0
        }
    }

    private suspend fun info(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        args.rejectUnknown("coordinator", "relay", "gid")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val gid = CordnRun.gid(args, scope)
            val group = scope.manager.group(gid) ?: return@withSession Output.error("not_found", "no group $gid here")
            val metadata = CordnGroupMetadata.fromExtensions(group.extensions)
            val exposure = scope.session.exposure(gid)

            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "gid" to gid,
                    "epoch" to group.epoch,
                    "name" to metadata?.name,
                    "about" to metadata?.description,
                    "members" to CordnCredential.memberIdentities(group).toList(),
                    "admins" to metadata?.adminPubkeys.orEmpty(),
                    "egalitarian" to metadata?.adminPubkeys.orEmpty().isEmpty(),
                    "ref" to scope.manager.shareRef(gid).encode(),
                    "exposure" to
                        mapOf(
                            "content" to exposure.content.name,
                            "membership" to exposure.membership.name,
                            "messaging" to exposure.messaging.name,
                            "notes" to exposure.notes().map { it.name },
                        ),
                ),
            )
            0
        }
    }

    /**
     * `cordn invite --gid GID --pubkey PK`
     *
     * Takes their KeyPackage from the coordinator, verifies the publication
     * payload binds it to that npub, commits, and leaves a Welcome. The
     * verification is not belt-and-braces: §9/§10 require the client to check
     * even though the coordinator does, because a coordinator that skipped it
     * could hand any account's name over any account's key material and we
     * would add the wrong person.
     */
    suspend fun invite(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        val target = args.flag("pubkey") ?: return Output.error("bad_args", "cordn invite needs --pubkey")
        val keyPackageRef = args.flag("kp-ref")
        args.rejectUnknown("coordinator", "relay", "gid")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val gid = CordnRun.gid(args, scope)
            val result = scope.manager.invite(gid, target, keyPackageRef)
            Output.emit(
                mapOf(
                    "gid" to result.gid,
                    "invited" to result.invited,
                    "commit_cursor" to result.commitCursor,
                    "welcome_at" to result.welcomeAt,
                    "epoch" to scope.manager.group(gid)?.epoch,
                ),
            )
            0
        }
    }

    /**
     * `cordn request --gid GID`
     *
     * Asks to join. Needs a published KeyPackage, because the request names
     * the exact one the inviter should use — a request with nothing to add is
     * a request nobody can accept. The asking itself is attributable (§8.1):
     * the coordinator learns this npub wants into this group whether or not
     * anyone ever answers, and that is unavoidable rather than an oversight.
     */
    suspend fun request(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        val gid = args.flag("gid")
        val ref = args.flag("ref")
        val keyPackageRef = args.flag("kp-ref")
        args.rejectUnknown("coordinator", "relay")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val target =
                gid
                    ?: ref?.let { CordnGroupRef.decode(it).gid }
                    ?: return@withSession Output.error("bad_args", "cordn request needs --gid or --ref cordn1…")

            val using =
                keyPackageRef
                    ?: scope.keyPackages.published.value
                        .firstOrNull()
                    ?: scope.keyPackages.publishNew().keyPackageRef

            val at = scope.manager.requestToJoin(target, using)
            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "gid" to target,
                    "kp_ref" to using,
                    "at" to at,
                    // Holding a ref lets you ask. It does not make you a member,
                    // and nothing obliges anyone to answer.
                    "member" to false,
                    "attributable" to true,
                ),
            )
            0
        }
    }

    /**
     * `cordn requests` — everyone asking to join a group we hold.
     *
     * Any member can answer these, not only an admin: `spec/01.md` §5.3 makes
     * `admin_pubkeys` presentation metadata and nothing enforces it, so a
     * check here would be inventing a boundary cordn does not have.
     */
    suspend fun requests(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "cordn requests",
            tail,
            "cordn requests <list|accept|decline>",
            help = CordnCommands.USAGE,
            routes =
                mapOf(
                    "list" to { rest -> requestList(dataDir, rest) },
                    "accept" to { rest -> answerRequest(dataDir, rest, accept = true) },
                    "decline" to { rest -> answerRequest(dataDir, rest, accept = false) },
                ),
        )

    private suspend fun requestList(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        args.rejectUnknown("coordinator", "relay")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "requests" to scope.manager.pendingJoinRequests().map { it.toMap() },
                ),
            )
            0
        }
    }

    private suspend fun answerRequest(
        dataDir: DataDir,
        tail: Array<String>,
        accept: Boolean,
    ): Int {
        val args = Args(tail)
        val who = args.flag("pubkey")
        args.rejectUnknown("coordinator", "relay", "gid", "all")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val pending = scope.manager.pendingJoinRequests()
            val gidFilter = args.flag("gid")
            val chosen =
                pending.filter { (who == null || it.pubKey == who) && (gidFilter == null || it.gid == gidFilter) }

            if (chosen.isEmpty()) {
                Output.emit(mapOf("answered" to emptyList<String>(), "pending" to pending.size))
                return@withSession 1
            }
            if (who == null && "all" !in args.booleans && chosen.size > 1) {
                return@withSession Output.error(
                    "bad_args",
                    "${chosen.size} requests pending; name one with --pubkey or pass --all",
                )
            }

            // Taken, so the loop cannot hand the same request to two calls.
            val answered =
                chosen.map { req ->
                    if (accept) {
                        val result = scope.manager.acceptJoinRequest(req)
                        mapOf("pubkey" to req.pubKey, "gid" to req.gid, "welcome_at" to result.welcomeAt)
                    } else {
                        scope.manager.declineJoinRequest(req)
                        mapOf("pubkey" to req.pubKey, "gid" to req.gid, "declined" to true)
                    }
                }

            Output.emit(mapOf("accepted" to accept, "answered" to answered))
            0
        }
    }

    /**
     * `cordn welcomes` — open every pending Welcome without joining anything.
     *
     * Opening and accepting are separate acts on purpose. A Welcome is opaque
     * until processed: the `gid`, the group's name and who is already in it all
     * live inside it, so a person cannot be asked about an invitation that has
     * not been opened. Printing them without joining is that split, in a shell.
     */
    suspend fun welcomes(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        args.rejectUnknown("coordinator", "relay")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val inbox = scope.manager.pendingWelcomes(scope.keyPackages::bundleFor)
            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "pending" to
                        inbox.pending.map {
                            mapOf(
                                "gid" to it.gid,
                                "kp_ref" to it.keyPackageRef,
                                "at" to it.at,
                                "epoch" to it.epoch,
                                "name" to it.metadata?.name,
                                "members" to it.members.toList(),
                            )
                        },
                    // Not errors. A Welcome for a KeyPackage this device never
                    // held belongs to another device of the same account, and
                    // draining it here would destroy it.
                    "skipped" to inbox.skipped.map { mapOf("kp_ref" to it.keyPackageRef, "reason" to it.reason) },
                ),
            )
            0
        }
    }

    /** `cordn join [--gid GID | --all]` — accept a pending Welcome. */
    suspend fun join(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = acceptOrDecline(dataDir, tail, accept = true)

    /** `cordn decline [--gid GID | --all]` — refuse one, and retire it. */
    suspend fun decline(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = acceptOrDecline(dataDir, tail, accept = false)

    private suspend fun acceptOrDecline(
        dataDir: DataDir,
        tail: Array<String>,
        accept: Boolean,
    ): Int {
        val args = Args(tail)
        val gid = args.flag("gid")
        val all = "all" in args.booleans
        args.rejectUnknown("coordinator", "relay", "all")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val inbox = scope.manager.pendingWelcomes(scope.keyPackages::bundleFor)
            val chosen = inbox.pending.filter { gid == null || it.gid == gid }

            if (chosen.isEmpty()) {
                Output.emit(mapOf("joined" to emptyList<String>(), "skipped" to inbox.skipped.size))
                return@withSession 1
            }
            if (gid == null && !all && chosen.size > 1) {
                return@withSession Output.error(
                    "bad_args",
                    "${chosen.size} invitations pending; name one with --gid or pass --all",
                )
            }

            val done =
                chosen.map {
                    if (accept) {
                        scope.manager.accept(it)
                    } else {
                        scope.manager.decline(it)
                        it.gid
                    }
                }

            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    (if (accept) "joined" else "declined") to done,
                    "epochs" to done.associateWith { scope.manager.group(it)?.epoch },
                    "skipped" to inbox.skipped.map { mapOf("kp_ref" to it.keyPackageRef, "reason" to it.reason) },
                ),
            )
            0
        }
    }

    /**
     * `cordn send --text "…"` — a chat message, or an annotation of one.
     *
     * `--reply-to` / `--react-to` / `--edit` / `--delete` / `--pin` / `--unpin`
     * take a message id and need `--to-author` beside it, and that is not an
     * awkward flag — it is the shape of the data. An annotation's tags name the
     * target's author and kind as well as its id, and the CLI keeps no message
     * store: cursors and MLS state persist between runs, decrypted messages
     * deliberately do not. So the fields a reference needs have to come from
     * the caller, who has them from the `fetch` that printed the message.
     *
     * One limitation worth naming rather than discovering. Threading reads the
     * *target's* tags to find the thread root, and those are not passed here,
     * so replying to a reply roots the new message at the message it answers
     * instead of at the original root. Correct for a one-level reply, which is
     * what a script tends to send; a client with a message store does better.
     */
    suspend fun send(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        val text = args.flag("text") ?: ""
        val author = args.flag("to-author")
        val targetKind = args.intFlag("to-kind", CordnGroupManager.CHAT_KIND)
        val replyTo = args.flag("reply-to")
        val reactionTo = args.flag("react-to")
        val editTo = args.flag("edit")
        val deleteTo = args.flag("delete")
        val pinTo = args.flag("pin")
        val unpinTo = args.flag("unpin")
        args.rejectUnknown("coordinator", "relay", "gid")

        val referenced = listOfNotNull(replyTo, reactionTo, editTo, deleteTo, pinTo, unpinTo)
        if (referenced.size > 1) {
            return Output.error("bad_args", "cordn send takes at most one of --reply-to/--react-to/--edit/--delete/--pin/--unpin")
        }
        val targetId = referenced.firstOrNull()
        if (targetId != null && author == null) {
            return Output.error("bad_args", "a reference needs --to-author PK: its tags name the target's author, not only its id")
        }
        if (targetId == null && text.isEmpty()) {
            return Output.error("bad_args", "cordn send needs --text, or a reference to annotate")
        }

        val target = targetId?.let { CordnMessageReferences.Target(id = it, pubKey = author!!, kind = targetKind) }

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val gid = CordnRun.gid(args, scope)
            val envelope =
                try {
                    scope.manager.post(
                        gid = gid,
                        content = text,
                        replyTo = target.takeIf { replyTo != null },
                        reactionTo = target.takeIf { reactionTo != null },
                        editTo = target.takeIf { editTo != null },
                        deleteTo = target.takeIf { deleteTo != null },
                        pinTo = target.takeIf { pinTo != null || unpinTo != null },
                        pinOp = if (unpinTo != null) CordnMessageReferences.PinOp.REMOVE else CordnMessageReferences.PinOp.ADD,
                    )
                } catch (e: IllegalArgumentException) {
                    // The manager refuses an edit or delete of somebody else's
                    // message. That is a rule, not a failure to handle here.
                    return@withSession Output.error("refused", e.message ?: "refused")
                }

            Output.emit(
                mapOf(
                    "gid" to gid,
                    // The envelope id is the message's identity (spec/02.md §7)
                    // — the cursor a fetch reports is not, so a script that
                    // needs to refer to this message later must use this.
                    "id" to envelope.id,
                    "kind" to envelope.kind,
                    "created_at" to envelope.createdAt,
                    "epoch" to scope.manager.group(gid)?.epoch,
                ),
            )
            0
        }
    }

    /**
     * `cordn fetch` — drain everything the cursor has not seen, and print it.
     *
     * Every outcome is reported, including the ones that are not messages. An
     * `undecryptable` payload especially: the cursor moves past it, because the
     * alternative is a stream that never advances, and a client that printed
     * nothing would be hiding a gap in a conversation rather than reporting one.
     */
    suspend fun fetch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        args.rejectUnknown("coordinator", "relay")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val messages = mutableListOf<Map<String, Any?>>()
            val epochs = mutableListOf<Map<String, Any?>>()
            val echoes = mutableListOf<Map<String, Any?>>()
            val undecryptable = mutableListOf<Map<String, Any?>>()

            val drained =
                scope.manager.catchUp { delivery ->
                    when (delivery) {
                        is CordnGroupManager.Delivery.Message ->
                            messages +=
                                mapOf(
                                    "gid" to delivery.gid,
                                    "cursor" to delivery.cursor,
                                    "id" to delivery.received.envelope.id,
                                    // What MLS authenticated, not what the
                                    // envelope claims: the envelope is unsigned
                                    // (spec/02.md), so its pubKey field is a
                                    // claim and this is the fact.
                                    "sender" to delivery.received.sender,
                                    "epoch" to delivery.received.epoch,
                                    "kind" to delivery.received.envelope.kind,
                                    "created_at" to delivery.received.envelope.createdAt,
                                    "content" to delivery.received.envelope.content,
                                )

                        is CordnGroupManager.Delivery.EpochAdvanced ->
                            epochs += mapOf("gid" to delivery.gid, "cursor" to delivery.cursor, "epoch" to delivery.epoch)

                        is CordnGroupManager.Delivery.Echo ->
                            echoes += mapOf("gid" to delivery.gid, "cursor" to delivery.cursor)

                        is CordnGroupManager.Delivery.Undecryptable ->
                            undecryptable +=
                                mapOf("gid" to delivery.gid, "cursor" to delivery.cursor, "reason" to delivery.reason)
                    }
                }

            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "drained" to drained,
                    "messages" to messages,
                    "epoch_changes" to epochs,
                    "echoes" to echoes,
                    "undecryptable" to undecryptable,
                ),
            )
            0
        }
    }

    private fun JoinRequest.toMap() =
        mapOf(
            "gid" to gid,
            "pubkey" to pubKey,
            "kp_ref" to keyPackageRef,
            "at" to at,
        )
}
