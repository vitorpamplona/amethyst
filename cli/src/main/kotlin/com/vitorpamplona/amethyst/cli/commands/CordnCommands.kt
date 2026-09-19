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
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.cordn.CoordinatorConfig
import com.vitorpamplona.amethyst.commons.cordn.ExposureNote
import com.vitorpamplona.amethyst.commons.cordn.GroupExposure
import com.vitorpamplona.quartz.cordn.appGroupRef.CordnGroupRef
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * `amy cordn …` — the cordn (MLS-over-an-MCP-coordinator) surface.
 *
 * What is here is what works without a coordinator: the `cordn1…` group ref
 * codec, and the §8 metadata-exposure model. Both are what an interop script
 * actually needs — a ref is the one cordn artifact a human copies by hand, and
 * `exposure` is how a script (or a person) checks what a coordinator would
 * learn before joining anything.
 *
 * The verbs that drive a coordinator (`publish`, `invite`, `send`, `sync`) are
 * not here yet, and the reason is worth stating: there is nothing to test them
 * against. The reference coordinator is unlicensed — see
 * `quartz/plans/2026-09-17-cordn-interop.md` §7 — so Tier B cannot be built on
 * it, and shipping unexercised coordinator verbs would be shipping a guess.
 * [com.vitorpamplona.amethyst.commons.cordn.CordnGroupManager] holds that logic
 * and is covered against an in-memory coordinator in `commons`.
 */
object CordnCommands {
    val USAGE: String =
        """
        |cordn (MLS group chat over an MCP coordinator):
        |  cordn ref encode --gid GID [--coordinator PK]  build a cordn1… group reference
        |                   [--relay URL[,URL…]]
        |  cordn ref decode REF                           read one back
        |  cordn exposure --coordinator PK                what that coordinator would learn
        |                 [--groups N] [--published]      (spec/00.md §8)
        |
        |A group ref is a locator, not an invitation: holding one lets you ASK to
        |join, it does not make you a member. Relays say where to reach the
        |coordinator and are meaningless without --coordinator.
        """.trimMargin()

    suspend fun dispatch(tail: Array<String>): Int =
        route(
            "cordn",
            tail,
            "cordn <ref|exposure>",
            help = USAGE,
            routes =
                mapOf(
                    "ref" to { rest -> ref(rest) },
                    "exposure" to { rest -> exposure(rest) },
                ),
        )

    private suspend fun ref(tail: Array<String>): Int =
        route(
            "cordn ref",
            tail,
            "cordn ref <encode|decode>",
            help = USAGE,
            routes =
                mapOf(
                    "encode" to { rest -> encode(rest) },
                    "decode" to { rest -> decode(rest) },
                ),
        )

    private fun encode(tail: Array<String>): Int {
        val args = Args(tail)
        val gid = args.flag("gid") ?: return Output.error("bad_args", "cordn ref encode needs --gid")
        val coordinator = args.flag("coordinator")
        // The flag map collapses repeats, so several relays arrive comma-separated.
        val relays =
            args
                .flag("relay")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        args.rejectUnknown()

        val normalized =
            relays.map { url ->
                RelayUrlNormalizer.normalizeOrNull(url)
                    ?: return Output.error("bad_args", "not a relay URL: $url")
            }

        val ref =
            try {
                CordnGroupRef(gid, coordinator, normalized.map { it.url })
            } catch (e: IllegalArgumentException) {
                return Output.error("bad_args", e.message ?: "invalid group reference")
            }

        Output.emit(
            mapOf(
                "ref" to ref.encode(),
                "gid" to ref.gid,
                "coordinator" to ref.coordinatorPubKey,
                "relays" to ref.relays,
            ),
        )
        return 0
    }

    private fun decode(tail: Array<String>): Int {
        val args = Args(tail)
        val encoded = args.positional.firstOrNull() ?: return Output.error("bad_args", "cordn ref decode needs a cordn1… reference")
        args.rejectUnknown()

        val ref =
            try {
                CordnGroupRef.decode(encoded)
            } catch (e: IllegalArgumentException) {
                return Output.error("bad_ref", e.message ?: "not a cordn group reference")
            }

        Output.emit(
            mapOf(
                "gid" to ref.gid,
                "coordinator" to ref.coordinatorPubKey,
                "relays" to ref.relays,
                // A ref carrying no coordinator is legal (spec §2) and means the
                // recipient must already know who serves this group.
                "reachable" to (ref.coordinatorPubKey != null && ref.relays.isNotEmpty()),
            ),
        )
        return 0
    }

    private fun exposure(tail: Array<String>): Int {
        val args = Args(tail)
        val coordinator = args.flag("coordinator") ?: return Output.error("bad_args", "cordn exposure needs --coordinator")
        val groups = args.flag("groups")?.toIntOrNull() ?: 1
        val published = "published" in args.booleans
        val fromLink = "from-link" in args.booleans
        args.rejectUnknown("published", "from-link")

        if (groups < 1) return Output.error("bad_args", "--groups must be at least 1")

        val exposure =
            GroupExposure(
                coordinator = coordinator,
                linkedGroupCount = groups,
                joinedFromShareLink = fromLink,
                publishedKeyPackage = published,
                // Our transport pins CEP-4 encryption to REQUIRED and fails
                // closed (§8.6), so this is a property of the client, not a
                // setting a coordinator can talk us out of.
                encryptionPinned = true,
            )

        Output.emit(
            mapOf(
                "coordinator" to coordinator,
                "content" to exposure.content.name,
                "membership" to exposure.membership.name,
                "messaging" to exposure.messaging.name,
                "linked_groups" to exposure.linkedGroupCount,
                "differs_from_marmot" to exposure.differsFromMarmot(),
                "notes" to exposure.notes().map { it.name to describe(it) }.toMap(),
            ),
        )
        return 0
    }

    /** One line per note, for the human-readable half of the dual output. */
    private fun describe(note: ExposureNote): String =
        when (note) {
            ExposureNote.MEMBERSHIP_IS_IDENTIFIED ->
                "admission names real npubs on both ends, so the coordinator sees who is in this group"
            ExposureNote.GROUPS_LINKED_BY_SESSION ->
                "one throwaway key posts and fetches for every group you have here, linking them to each other"
            ExposureNote.SINGLE_OPERATOR_HOLDS_HISTORY ->
                "one operator holds the complete ordered history of every group it serves"
            ExposureNote.PUBLICATION_IS_A_SIGNED_RECORD ->
                "your published KeyPackage is a signed, re-servable record that this account uses cordn"
            ExposureNote.MESSAGE_SIZES_UNPADDED ->
                "sealed payloads are not padded, so message sizes are visible"
            ExposureNote.ENCRYPTION_NOT_PINNED ->
                "BUG: this client is not pinning transport encryption; report it"
        }

    /** The coordinator a ref points at, for callers wiring one up. */
    fun coordinatorOf(ref: CordnGroupRef): CoordinatorConfig? = CoordinatorConfig.from(ref)
}
