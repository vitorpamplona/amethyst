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
import com.vitorpamplona.amethyst.cli.stores.HttpCordnBlobStore
import com.vitorpamplona.amethyst.commons.cordn.CordnMigration
import com.vitorpamplona.amethyst.commons.cordn.CordnMigrationException
import com.vitorpamplona.amethyst.commons.cordn.CordnMigrationStores
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnHandoffCode

/**
 * `amy cordn migrate …` — the device handoff, drivable without a phone.
 *
 * Here because the whole point of the CLI is that a protocol flow can be
 * exercised end to end before any UI exists. A migration is two devices and a
 * scan; two `amy` homes and a string is the same thing with the camera taken
 * out.
 *
 * ## Why `export` does not stand the CLI down
 *
 * On a phone, exporting is a handoff: the device stops, because two devices
 * committing from one MLS leaf fork the ratchet tree irrecoverably. `amy` has
 * no sync loop to stop — each invocation is a process — so there is no
 * equivalent state to set, and `--and-stop` would be a flag that did nothing.
 * The check that matters lives on the runtime that actually keeps loops
 * running. Exporting from an `amy` home and then continuing to send from it is
 * the same mistake, and this verb says so rather than pretending to prevent it.
 */
internal object CordnMigrateCommands {
    suspend fun migrate(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "cordn migrate",
            tail,
            "cordn migrate <export|import>",
            help = CordnCommands.USAGE,
            routes =
                mapOf(
                    "export" to { rest -> export(dataDir, rest) },
                    "import" to { rest -> import(dataDir, rest) },
                ),
        )

    private suspend fun export(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        val servers =
            args
                .flag("server")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        args.rejectUnknown("relay", "server")

        if (servers.isEmpty()) {
            return Output.error("bad_args", "cordn migrate export needs --server URL[,URL…] to store the documents on")
        }

        return Context.open(dataDir).use { ctx ->
            val relays = ctx.cordn.relayFlag(args)
            if (relays.isEmpty()) {
                return@use Output.error("bad_args", "cordn migrate export needs --relay URL[,URL…] to publish the tip on")
            }

            try {
                val snapshot =
                    CordnMigrationStores.read(
                        ctx.cordn.migrationRoot,
                        ctx.identity.pubKeyHex,
                        ctx.cordn.blobCipher,
                        ctx.cordn.coordinators(),
                    )

                val code =
                    CordnMigration(ctx.client, ctx.signer, HttpCordnBlobStore(servers))
                        .publish(snapshot, relays.toSet())

                Output.emit(
                    mapOf(
                        "code" to code.encode(),
                        "groups" to snapshot.groups.size,
                        "key_packages" to snapshot.keyPackages.size,
                        "relays" to relays.map { it.url },
                        "servers" to servers,
                        // Said here because this is where a person decides. The
                        // sealed state is on someone else's disk now; a reader
                        // learns size and timing but not content.
                        "uploaded" to true,
                        // amy keeps no sync loop, so nothing was stood down —
                        // see the object KDoc.
                        "this_device_stopped" to false,
                    ),
                )
                0
            } catch (e: CordnMigrationException) {
                Output.error("migration_failed", e.message ?: "the handoff could not be published")
            } catch (e: IllegalArgumentException) {
                Output.error("bad_args", e.message ?: "bad migration")
            }
        }
    }

    private suspend fun import(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val args = Args(tail)
        val raw = args.flag("code") ?: args.positionalOrNull(0)
        args.rejectUnknown("code")

        if (raw == null) return Output.error("bad_args", "cordn migrate import needs a cordndev1… code")

        val code =
            CordnHandoffCode.decodeOrNull(raw)
                ?: return Output.error("bad_args", "that is not a cordndev1… handoff code")

        return Context.open(dataDir).use { ctx ->
            try {
                val snapshot =
                    CordnMigration(ctx.client, ctx.signer, HttpCordnBlobStore(emptyList()))
                        .fetch(code)

                // Replaces, never merges: two devices holding one group's state
                // and both committing fork the ratchet tree, and MLS does not
                // recover from that.
                val configs =
                    CordnMigrationStores.write(
                        ctx.cordn.migrationRoot,
                        ctx.identity.pubKeyHex,
                        ctx.cordn.blobCipher,
                        snapshot,
                    )
                ctx.cordn.replaceCoordinators(configs)

                Output.emit(
                    mapOf(
                        "groups" to snapshot.groups.size,
                        "key_packages" to snapshot.keyPackages.size,
                        "coordinators" to configs.map { it.pubKey },
                        "replaced_local_state" to true,
                    ),
                )
                0
            } catch (e: CordnMigrationException) {
                Output.error("migration_failed", e.message ?: "the handoff could not be read")
            } catch (e: IllegalArgumentException) {
                Output.error("bad_args", e.message ?: "bad migration")
            }
        }
    }
}
