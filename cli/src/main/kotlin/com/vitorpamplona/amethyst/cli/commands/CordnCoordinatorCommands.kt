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
import com.vitorpamplona.amethyst.commons.cordn.CoordinatorConfig

/**
 * `amy cordn coordinator …` and `amy cordn keypackage …`.
 *
 * ## Why a coordinator has to be remembered at all
 *
 * A coordinator has no address beyond its pubkey (`spec/00.md` §8.5), so
 * "which relays reach it" is knowledge the client holds and nothing on the
 * network can supply. Losing it does not degrade to a slow lookup — it
 * degrades to a coordinator you cannot reach and groups you cannot open.
 * That is why `coordinator add` exists as its own verb and why the list is
 * encrypted at rest alongside the group state.
 *
 * ## Why publishing a KeyPackage is a separate, explicit verb
 *
 * It is the one attributable thing a client does before anyone has invited it
 * anywhere (§8.4): a signed, re-servable record that this npub uses cordn on
 * this coordinator. Doing it implicitly — at `coordinator add`, say — would
 * make being told about a coordinator indistinguishable from announcing
 * yourself to it.
 */
internal object CordnCoordinatorCommands {
    suspend fun coordinator(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "cordn coordinator",
            tail,
            "cordn coordinator <add|list|info|forget>",
            help = CordnCommands.USAGE,
            routes =
                mapOf(
                    "add" to { rest -> add(dataDir, rest) },
                    "list" to { rest -> list(dataDir, rest) },
                    "info" to { rest -> info(dataDir, rest) },
                    "forget" to { rest -> forget(dataDir, rest) },
                ),
        )

    private suspend fun add(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        val pubKey = args.flag("coordinator") ?: return Output.error("bad_args", "cordn coordinator add needs --coordinator")
        val label = args.flag("label")
        args.rejectUnknown("relay")

        return Context.open(dataDir).use { ctx ->
            try {
                val relays = ctx.cordn.relayFlag(args)
                if (relays.isEmpty()) return@use Output.error("bad_args", "cordn coordinator add needs --relay URL[,URL…]")

                val config = CoordinatorConfig(pubKey = pubKey, relays = relays, label = label)
                ctx.cordn.remember(config)

                Output.emit(
                    mapOf(
                        "coordinator" to config.pubKey,
                        "relays" to config.relays.map { it.url },
                        "label" to config.label,
                        "known" to ctx.cordn.coordinators().size,
                        // Said here because this is where a person decides. The
                        // list is local knowledge; nothing has been told to the
                        // coordinator by adding it.
                        "announced" to false,
                    ),
                )
                0
            } catch (e: IllegalArgumentException) {
                Output.error("bad_args", e.message ?: "bad coordinator")
            }
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        args.rejectUnknown()

        return Context.open(dataDir).use { ctx ->
            Output.emit(
                mapOf(
                    "coordinators" to
                        ctx.cordn.coordinators().map {
                            mapOf(
                                "coordinator" to it.pubKey,
                                "relays" to it.relays.map { relay -> relay.url },
                                "label" to it.label,
                                "origin" to it.origin.name,
                            )
                        },
                ),
            )
            0
        }
    }

    /**
     * `cordn coordinator info` — the MCP `initialize` handshake.
     *
     * Every field it returns is a claim signed with nothing but the key that
     * was already signing the response (§8.5). Reported with the pubkey beside
     * it so the two are never confused: the pubkey is the identity, the name is
     * what the operator typed.
     */
    private suspend fun info(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        args.rejectUnknown("coordinator", "relay")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val info = scope.session.serverInfo()
            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "reachable" to (info != null),
                    "name" to info?.name,
                    "version" to info?.version,
                    "protocol_version" to info?.protocolVersion,
                    "capabilities" to info?.capabilities?.keys?.sorted(),
                    // The one field worth branching on, and the reason the rest
                    // are not: a coordinator answering a protocol this client
                    // does not implement is one whose later answers may not
                    // mean what they appear to.
                    "claims_are_unverified" to true,
                ),
            )
            if (info == null) 1 else 0
        }
    }

    private suspend fun forget(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        val pubKey = args.flag("coordinator") ?: return Output.error("bad_args", "cordn coordinator forget needs --coordinator")
        args.rejectUnknown()

        return Context.open(dataDir).use { ctx ->
            val forgotten = ctx.cordn.forget(pubKey)
            Output.emit(
                mapOf(
                    "coordinator" to pubKey,
                    "forgotten" to forgotten,
                    // Forgetting is local. The coordinator still holds every
                    // message of every group it served, and still holds any
                    // KeyPackage published to it — `keypackage withdraw` is
                    // the verb for that, and it is a different act.
                    "state_on_disk_kept" to true,
                ),
            )
            if (forgotten) 0 else 1
        }
    }

    suspend fun keyPackage(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "cordn keypackage",
            tail,
            "cordn keypackage <publish|list|withdraw>",
            help = CordnCommands.USAGE,
            routes =
                mapOf(
                    "publish" to { rest -> publish(dataDir, rest) },
                    "list" to { rest -> keyPackageList(dataDir, rest) },
                    "withdraw" to { rest -> withdraw(dataDir, rest) },
                ),
        )

    private suspend fun publish(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        val lastResort = "last-resort" in args.booleans
        val count = args.intFlag("count", 1)
        args.rejectUnknown("coordinator", "relay", "last-resort")

        if (count < 1) return Output.error("bad_args", "--count must be at least 1")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val published = (1..count).map { scope.keyPackages.publishNew(lastResort = lastResort) }
            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "published" to
                        published.map {
                            mapOf("kp_ref" to it.keyPackageRef, "last_resort" to it.lastResort, "at" to it.at)
                        },
                    // Worth printing every time, not once in a doc: this is the
                    // call that tells the coordinator this npub exists here.
                    "attributable" to true,
                ),
            )
            0
        }
    }

    private suspend fun keyPackageList(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        args.rejectUnknown("coordinator", "relay")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val onCoordinator = scope.keyPackages.listPublished()
            val held = scope.keyPackages.published.value

            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "on_coordinator" to
                        onCoordinator.map {
                            mapOf(
                                "kp_ref" to it.keyPackageRef,
                                "last_resort" to it.lastResort,
                                "at" to it.at,
                                // A package the coordinator will serve but we
                                // have no private half for cannot open the
                                // Welcome it produces. It belongs to another
                                // device of this account, or to a wiped one.
                                "openable_here" to (it.keyPackageRef in held),
                            )
                        },
                    "held_locally" to held.size,
                ),
            )
            0
        }
    }

    private suspend fun withdraw(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        val refs =
            args
                .flag("kp-ref")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
        args.rejectUnknown("coordinator", "relay", "all")

        return CordnRun.withSession(dataDir, args) { _, scope ->
            val target =
                refs
                    ?: if ("all" in args.booleans) {
                        scope.keyPackages.published.value
                            .toList()
                    } else {
                        return@withSession Output.error("bad_args", "cordn keypackage withdraw needs --kp-ref REF[,REF…] or --all")
                    }

            val removed = scope.keyPackages.withdraw(target)
            Output.emit(
                mapOf(
                    "coordinator" to scope.config.pubKey,
                    "requested" to target,
                    "removed" to removed,
                    // Withdrawing removes the package, not the record that it
                    // was once published: §8.4's exposure is a past event and
                    // no call undoes it.
                    "exposure_is_not_undone" to true,
                ),
            )
            0
        }
    }
}
