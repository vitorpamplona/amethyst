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
import com.vitorpamplona.amethyst.cli.CommitBarrier
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.SecureFileIO
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceLinkPrompt
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevices
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesSnapshot
import com.vitorpamplona.amethyst.commons.model.marmot.devices.NewDeviceLinkState
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SasSymbol
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotChatSetting
import com.vitorpamplona.marmot.features.accountSync.SmallStateItem
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.File

/**
 * `amy marmot devices …`: Marmot device groups, one account's devices linked with the seven-emoji
 * comparison (`features/device-pairing.md`). Thin over commons [MarmotDevices]; the pairing secrets
 * live only in the process, so a link is one long-running `link` run per side.
 */
object MarmotDevicesCommands {
    private const val USAGE =
        "marmot devices <enable|disable|list|sync|link|move|remove|rename|chats|read|pin|mute|archive|sign-in>\n" +
            "  enable | disable                          the device-group switch (off by default)\n" +
            "  list                                      this account's devices and any link in progress\n" +
            "  sync [--timeout SECS]                     one pass: pairing, group catch-up, device-group duties\n" +
            "  link --new [--confirm yes|no|stdin] [--code-file PATH] [--timeout SECS] [--settle SECS]\n" +
            "  link --existing [--label L] [--confirm yes|no|stdin] [--code-file PATH] [--timeout SECS] [--settle SECS]\n" +
            "  move [--confirm yes|no|stdin] [--code-file PATH] [--timeout SECS] [--settle SECS]\n" +
            "                                            leave this device group for the one of the device that answers\n" +
            "                                            (it runs `link --existing`); done once the move is made\n" +
            "  remove SLOT                               remove a linked device\n" +
            "  rename SLOT LABEL                         rename a device, this one included (its slot or `self`)\n" +
            "  chats                                     each Marmot chat's pin, mute, archive and synced read position\n" +
            "  read GROUP                                share that this account read GROUP up to its newest message\n" +
            "  pin|mute|archive GROUP [on|off]           change one, on every linked device\n" +
            "  sign-in SLOT mine|not-me                  answer a sign-in: That was me, or This wasn't me"

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            name = "marmot devices",
            tail = tail,
            usage = USAGE,
            routes =
                mapOf(
                    "enable" to { _ -> setSwitch(dataDir, true) },
                    "disable" to { _ -> disable(dataDir) },
                    "list" to { rest -> list(dataDir, rest) },
                    "sync" to { rest -> sync(dataDir, rest) },
                    "link" to { rest -> link(dataDir, rest) },
                    "move" to { rest -> link(dataDir, arrayOf("--new") + rest, move = true) },
                    "remove" to { rest -> remove(dataDir, rest) },
                    "rename" to { rest -> rename(dataDir, rest) },
                    "chats" to { rest -> chats(dataDir, rest) },
                    "read" to { rest -> markRead(dataDir, rest) },
                    "pin" to { rest -> chatSetting(dataDir, MarmotChatSetting.PIN, rest) },
                    "mute" to { rest -> chatSetting(dataDir, MarmotChatSetting.MUTE, rest) },
                    "archive" to { rest -> chatSetting(dataDir, MarmotChatSetting.ARCHIVE, rest) },
                    "sign-in" to { rest -> signIn(dataDir, rest) },
                ),
        )

    private fun setSwitch(
        dataDir: DataDir,
        on: Boolean,
    ): Int {
        val file = dataDir.deviceGroupSwitchFile
        if (on) {
            SecureFileIO.secureMkdirs(checkNotNull(file.parentFile))
            SecureFileIO.writeTextAtomic(file, "1\n")
        } else if (file.exists() && !file.delete()) {
            return Output.error("io", "could not delete ${file.absolutePath}")
        }
        Output.emit(mapOf("device_groups" to on))
        return 0
    }

    /**
     * Off only while this device is not linked: with the switch off, the next run would treat the
     * device group as a chat and the siblings would keep this device.
     */
    private suspend fun disable(dataDir: DataDir): Int {
        if (dataDir.deviceGroupSwitchFile.exists()) {
            val linked =
                withDevices(dataDir) { _, devices ->
                    devices.start(announce = false)
                    devices.state.value.linked
                }
            if (linked == true) return Output.error("linked", "this device is linked: remove it from another device first")
        }
        return setSwitch(dataDir, false)
    }

    private suspend fun <T> withDevices(
        dataDir: DataDir,
        block: suspend (Context, MarmotDevices) -> T,
    ): T? =
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val devices = ctx.marmotDevices ?: return null
            block(ctx, devices)
        }

    private fun disabled() = Output.error("device_groups_off", "run `amy marmot devices enable` first")

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        Args(rest).rejectUnknown()
        return withDevices(dataDir) { _, devices ->
            Output.emit(render(devices.state.value))
            0
        } ?: disabled()
    }

    private suspend fun sync(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        return withDevices(dataDir) { ctx, devices ->
            devices.start(announce = true)
            pass(ctx, devices, timeoutMs)
            Output.emit(render(devices.state.value))
            0
        } ?: disabled()
    }

    private suspend fun remove(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val slot = args.positionalOrNull(0) ?: return Output.error("bad_args", "marmot devices remove SLOT")
        args.rejectUnknown()
        return withDevices(dataDir) { ctx, devices ->
            ctx.syncIncoming(4_000)
            if (devices.state.value.devices
                    .none { it.slot == slot && !it.isThisDevice }
            ) {
                return@withDevices Output.error("not_found", "no linked device in slot $slot")
            }
            if (!devices.removeDevice(slot)) {
                return@withDevices Output.error("not_confirmed", "no relay confirmed the removal of $slot")
            }
            Output.emit(render(devices.state.value) + mapOf("removed" to slot))
            0
        } ?: disabled()
    }

    private suspend fun rename(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val target = args.positionalOrNull(0) ?: return Output.error("bad_args", "marmot devices rename SLOT LABEL")
        val label = args.positionalOrNull(1) ?: return Output.error("bad_args", "marmot devices rename SLOT LABEL")
        args.rejectUnknown()
        return withDevices(dataDir) { ctx, devices ->
            ctx.syncIncoming(4_000)
            val slot = if (target == "self") devices.state.value.ownSlot else target
            if (slot == null ||
                devices.state.value.devices
                    .none { it.slot == slot }
            ) {
                return@withDevices Output.error("not_found", "no linked device in slot $target")
            }
            // An interop test racing two renames holds this one's Commit until both are staged.
            ctx.commitBarrier = CommitBarrier.fromEnv()
            if (!devices.renameDevice(slot, label)) return@withDevices Output.error("not_confirmed", "no relay confirmed the rename of $slot")
            Output.emit(render(devices.state.value) + mapOf("renamed" to slot))
            0
        } ?: disabled()
    }

    /** Each chat this account is in, with its settings and the read position the device group holds. */
    private suspend fun chats(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        Args(rest).rejectUnknown()
        return Context.open(dataDir).use { ctx ->
            ctx.prepare()
            Output.emit(mapOf("chats" to chatRows(ctx)))
            0
        }
    }

    private suspend fun chatRows(ctx: Context): List<Map<String, Any?>> {
        val devices = ctx.marmotDevices
        val settings = ctx.marmotChatSettings.state.value
        val small = devices?.groups?.takeIf { it.trust().trustsDeviceGroup }?.smallState()
        return ctx.marmot
            .activeGroupIds()
            .filter { devices?.isDeviceGroup(it) != true }
            .sorted()
            .map { id ->
                val read = ctx.marmot.mlsGroupIdHex(id)?.let { mls -> small?.held("$mls/read") as? SmallStateItem.Read }
                mapOf(
                    "group_id" to id,
                    "pinned" to settings.isPinned(id),
                    "muted" to settings.isMuted(id),
                    "archived" to settings.isArchived(id),
                    "read_at" to read?.at,
                    "read_id" to read?.id,
                )
            }
    }

    /** `read GROUP`: the read position of the newest message this device holds, sent to the linked devices. */
    private suspend fun markRead(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val group = args.positionalOrNull(0) ?: return Output.error("bad_args", "marmot devices read GROUP")
        args.rejectUnknown()
        return withDevices(dataDir) { ctx, devices ->
            ctx.syncIncoming(4_000)
            val id = ctx.resolveGroupId(group)
            if (!ctx.marmot.isMember(id)) return@withDevices Output.error("not_member", "not a member of $group")
            val epochs = ctx.marmot.storedEpochs(id)
            // An edit names no read position (`account-sync.md` "Read positions").
            val newest =
                ctx.marmot
                    .loadStoredMessages(id)
                    .mapNotNull { Event.fromJsonOrNull(it) }
                    .filter { it.kind != 1009 }
                    .mapNotNull { e -> epochs[e.id]?.let { Triple(it, e.createdAt, e.id) } }
                    .maxWithOrNull(compareBy<Triple<Long, Long, String>> { it.first }.thenBy { it.second }.thenBy { it.third })
                    ?: return@withDevices Output.error("no_messages", "no message of $group to mark read")
            devices.markRead(id, newest.first, newest.second, newest.third)
            Output.emit(mapOf("group_id" to id, "epoch" to newest.first, "read_at" to newest.second, "read_id" to newest.third))
            0
        } ?: disabled()
    }

    /** `pin|mute|archive GROUP [on|off]`: here, and on the account's linked devices when this one is linked. */
    private suspend fun chatSetting(
        dataDir: DataDir,
        setting: MarmotChatSetting,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val group = args.positionalOrNull(0) ?: return Output.error("bad_args", "marmot devices ${setting.wireName} GROUP [on|off]")
        val on =
            when (args.positionalOrNull(1) ?: "on") {
                "on", "yes", "true" -> true
                "off", "no", "false" -> false
                else -> return Output.error("bad_args", "the value is on or off")
            }
        args.rejectUnknown()
        return Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val id = ctx.resolveGroupId(group)
            if (!ctx.marmot.isMember(id) || ctx.marmotDevices?.isDeviceGroup(id) == true) return@use Output.error("not_member", "not a member of $group")
            // An archived chat is never pinned (MDK): unarchive it first.
            if (setting == MarmotChatSetting.PIN && on &&
                ctx.marmotChatSettings.state.value
                    .isArchived(id)
            ) {
                return@use Output.error("archived", "an archived chat cannot be pinned; unarchive it first")
            }
            // Archiving also unpins: every change goes to the linked devices.
            val changes = ctx.marmotChatSettings.setAll(id, setting, on)
            val devices = ctx.marmotDevices
            val shared = devices != null && devices.state.value.linked
            if (devices != null && shared) changes.forEach { (changed, value) -> devices.changeSetting(id, changed, value) }
            val settings = ctx.marmotChatSettings.state.value
            Output.emit(
                mapOf(
                    "group_id" to id,
                    setting.wireName to on,
                    "pinned" to settings.isPinned(id),
                    "archived" to settings.isArchived(id),
                    "shared_with_devices" to shared,
                ),
            )
            0
        }
    }

    /** `sign-in SLOT mine|not-me`: "That was me" is kept on this device; "This wasn't me" goes to the roster. */
    private suspend fun signIn(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val slot = args.positionalOrNull(0) ?: return Output.error("bad_args", "marmot devices sign-in SLOT mine|not-me")
        val answer = args.positionalOrNull(1)
        args.rejectUnknown()
        if (answer != "mine" && answer != "not-me") return Output.error("bad_args", "the answer is mine or not-me")
        return withDevices(dataDir) { _, devices ->
            if (answer == "mine") {
                if (!devices.acceptSignIn(slot)) return@withDevices Output.error("io", "could not save the answer")
            } else {
                devices.rejectSignIn(slot)
            }
            Output.emit(render(devices.state.value) + mapOf("answered" to slot))
            0
        } ?: disabled()
    }

    /**
     * `link --new` / `link --existing`, and `move`: a linked device linking again as the new device,
     * which moves it to the answering device's group (`device-group.md` "Moving between device
     * groups"); it ends once the move is made, then settles while the old group hears `moved`.
     */
    private suspend fun link(
        dataDir: DataDir,
        rest: Array<String>,
        move: Boolean = false,
    ): Int {
        val args = Args(rest, booleanFlags = setOf("new", "existing"))
        val asNew = args.bool("new")
        val asExisting = args.bool("existing")
        val confirm = args.flag("confirm", "stdin")
        val codeFile = args.flag("code-file")?.let(::File)
        val label = args.flag("label", "amy") ?: "amy"
        val timeoutMs = args.timeoutMs(600)
        val settleMs = args.longFlag("settle", 20) * 1000
        args.rejectUnknown()
        if (asNew == asExisting) return Output.error("bad_args", "pass exactly one of --new or --existing")
        if (confirm !in setOf("yes", "no", "stdin")) return Output.error("bad_args", "--confirm is yes, no or stdin")
        // Approving without comparing the seven emoji links whichever device asked: tests only.
        if (asExisting && confirm == "yes") {
            if (System.getenv(AUTO_CONFIRM_ENV) != "1") {
                return Output.error("bad_args", "--existing --confirm yes approves any request unseen; compare the code (--confirm stdin), or set $AUTO_CONFIRM_ENV=1 in a test")
            }
            System.err.println("warning: $AUTO_CONFIRM_ENV=1: approving the link request without comparing the code")
        }
        return withDevices(dataDir) { ctx, devices ->
            devices.start(announce = true)
            if (move && !devices.state.value.linked) return@withDevices Output.error("not_linked", "this device is in no device group: link it with `link --new`")
            val deadline = System.currentTimeMillis() + timeoutMs
            // Read on a daemon thread, outside the coroutine scope: a blocking read is not
            // cancellable, so a reader inside the scope kept the run alive after the link ended.
            val lines = if (confirm == "stdin") stdinLines() else null
            coroutineScope {
                var shown: List<SasSymbol>? = null

                fun decision(code: List<SasSymbol>): Boolean? {
                    if (shown != code) {
                        shown = code
                        showCode(code, codeFile)
                        // An answer typed before this code was shown is not an answer to it.
                        while (lines?.tryReceive()?.isSuccess == true) Unit
                    }
                    return when (confirm) {
                        "yes" -> true
                        "no" -> false
                        else ->
                            lines
                                ?.tryReceive()
                                ?.getOrNull()
                                ?.trim()
                                ?.lowercase()
                                ?.let { it == "yes" || it == "y" }
                    }
                }

                if (asNew) devices.startLinkAsNewDevice()
                var decided = false
                var linkedAt: Long? = null
                while (System.currentTimeMillis() < deadline) {
                    pass(ctx, devices, 3_000)
                    val s = devices.state.value
                    if (asNew) {
                        when (val st = s.newDevice) {
                            is NewDeviceLinkState.Comparing ->
                                if (!decided) {
                                    decision(st.code)?.let { ok ->
                                        decided = true
                                        if (ok) devices.confirmMatch() else devices.rejectMatch()
                                    }
                                }
                            is NewDeviceLinkState.Ended -> return@coroutineScope Output.error("link_ended", st.reason.name.lowercase())
                            // A move is done once it is made: the destination's roster holds this device's first Commit.
                            NewDeviceLinkState.Linked -> if (linkedAt == null && !s.moving) linkedAt = System.currentTimeMillis()
                            else -> {}
                        }
                    } else {
                        val prompt = s.prompts.lastOrNull { it.state !is DeviceLinkPrompt.State.Ended } ?: s.prompts.lastOrNull()
                        when (val st = prompt?.state) {
                            is DeviceLinkPrompt.State.Comparing ->
                                if (!decided && st.canLink) {
                                    decision(st.code)?.let { ok ->
                                        decided = true
                                        if (ok) devices.approveLink(prompt.sessionId, label) else devices.rejectLink(prompt.sessionId)
                                    }
                                }
                            is DeviceLinkPrompt.State.Ended -> if (decided) return@coroutineScope Output.error("link_ended", st.reason.name.lowercase())
                            DeviceLinkPrompt.State.Linked -> if (linkedAt == null && s.devices.count { it.status.name == "ACTIVE" } >= 2) linkedAt = System.currentTimeMillis()
                            else -> {}
                        }
                    }
                    // Linked: keep running a little, so private KeyPackages and the first chat Adds go out.
                    linkedAt?.let { if (System.currentTimeMillis() - it >= settleMs) break }
                    delay(POLL_MS)
                }
                lines?.cancel()
                val s = devices.state.value
                if (linkedAt == null) return@coroutineScope Output.error("timeout", "the link did not complete in ${timeoutMs / 1000}s")
                Output.emit(
                    render(s) +
                        mapOf(
                            "role" to
                                if (move) {
                                    "moved"
                                } else if (asNew) {
                                    "new"
                                } else {
                                    "existing"
                                },
                            "code" to shown?.map { it.emoji },
                            "code_numbers" to shown?.map { it.number },
                        ),
                )
                0
            }
        } ?: disabled()
    }

    /** Lines of stdin, read on a daemon thread that never keeps the process alive. */
    private fun stdinLines(): Channel<String> {
        val lines = Channel<String>(Channel.UNLIMITED)
        val reader =
            Thread({
                try {
                    while (true) lines.trySend(readlnOrNull() ?: break)
                } finally {
                    lines.close()
                }
            }, "amy-stdin")
        reader.isDaemon = true
        reader.start()
        return lines
    }

    private fun showCode(
        code: List<SasSymbol>,
        file: File?,
    ) {
        System.err.println("code: " + code.joinToString(" ") { it.emoji } + "   (" + code.joinToString(", ") { it.englishDescription } + ")")
        System.err.println("Compare all seven emoji on both devices before confirming.")
        file?.let { f ->
            val json = code.joinToString(",", "[", "]") { "{\"number\":${it.number},\"emoji\":\"${it.emoji}\",\"description\":\"${it.englishDescription}\"}" }
            SecureFileIO.writeTextAtomic(f, "{\"code\":$json}\n")
        }
    }

    /** One device-group pass: own pairing wraps, the Marmot catch-up, then the device-group duties. */
    private suspend fun pass(
        ctx: Context,
        devices: MarmotDevices,
        timeoutMs: Long,
    ) {
        val me = ctx.identity.pubKeyHex
        // Gift wraps carry a created_at up to two days in the past (NIP-59), so look back past that.
        val since = TimeUtils.now() - PAIRING_LOOKBACK_SECS
        val filter = Filter(kinds = listOf(GiftWrapEvent.KIND), tags = mapOf("p" to listOf(me)), since = since)
        ctx.drain(ctx.inboxRelays().associateWith { listOf(filter) }, idleTimeoutMs = timeoutMs).forEach { (_, event) ->
            if (event is GiftWrapEvent) devices.onGiftWrap(event)
        }
        ctx.syncIncoming(timeoutMs)
        devices.tick()
    }

    fun render(s: MarmotDevicesSnapshot): Map<String, Any?> =
        mapOf(
            "this_slot" to s.ownSlot,
            "linked" to s.linked,
            "unlinked" to s.unlinked,
            "devices" to
                s.devices.map {
                    mapOf(
                        "slot" to it.slot,
                        "label" to it.label,
                        "client" to it.client,
                        "platform" to it.platform,
                        "bring_history" to it.bringHistory,
                        "is_this_device" to it.isThisDevice,
                        "status" to it.status.name.lowercase(),
                        "linked_at" to it.linkedAt,
                        "linked_by" to it.linkedBy,
                        "add_to_all_chats" to it.addToAllChats,
                        "last_active_at" to it.lastActiveAt,
                    )
                },
            "new_device" to s.newDevice?.let(::stateName),
            "prompts" to s.prompts.map { mapOf("session_id" to it.sessionId, "slot" to it.slot, "state" to it.state::class.simpleName?.lowercase()) },
            "remaining_chats" to s.remainingChats,
            "moving" to s.moving,
            "owed_histories" to s.owedHistories,
            "kept_deliveries" to s.keptDeliveries,
            "sign_ins" to s.signIns.map { mapOf("slot" to it.slot, "published_at" to it.publishedAt, "kind" to it.kind.name.lowercase(), "confirmed" to it.confirmed) },
        )

    private fun stateName(s: NewDeviceLinkState): String =
        when (s) {
            NewDeviceLinkState.WaitingForAnswer -> "waiting_for_answer"
            is NewDeviceLinkState.Comparing -> "comparing"
            NewDeviceLinkState.WaitingForOtherDevice -> "waiting_for_other_device"
            NewDeviceLinkState.Joining -> "joining"
            NewDeviceLinkState.Linked -> "linked"
            is NewDeviceLinkState.Ended -> "ended:" + s.reason.name.lowercase()
        }

    private const val POLL_MS = 1_500L

    /** Lets `link --existing --confirm yes` approve without comparing, for interop tests only. */
    const val AUTO_CONFIRM_ENV = "AMY_TEST_AUTO_CONFIRM"
    private const val PAIRING_LOOKBACK_SECS = 2 * 24 * 60 * 60L + 15 * 60L
}
