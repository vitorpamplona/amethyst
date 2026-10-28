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
import com.vitorpamplona.amethyst.commons.cordn.BlossomCordnBlobStore
import com.vitorpamplona.amethyst.commons.cordn.CordnMigrationStores
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.cordn.appMultiDevice.CordnHandoffCode
import com.vitorpamplona.cordn.appMultiDevice.CordnMigration
import com.vitorpamplona.cordn.appMultiDevice.CordnMigrationException
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * `amy cordn migrate …` — the device handoff, drivable without a phone.
 *
 * Here because the whole point of the CLI is that a protocol flow can be
 * exercised end to end before any UI exists. A migration is two devices and a
 * scan; two `amy` homes and a string is the same thing with the camera taken
 * out.
 *
 * ## Why `export` stands this home down
 *
 * On a phone, exporting is a handoff: the device stops, because two devices
 * committing from one MLS leaf fork the ratchet tree irrecoverably. `amy` has
 * no sync loop to stop (each invocation is a process), but the next run that
 * sends from this home would fork the group just the same. So `export` sets
 * the same handoff flag the app does, and every verb that opens a session
 * refuses with `handed_off` until `resume` takes the home back, which is safe
 * only while the other device has not imported. `import` clears it: a home
 * that just adopted state holds the newest copy.
 */
internal object CordnMigrateCommands {
    suspend fun migrate(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "cordn migrate",
            tail,
            "cordn migrate <export|import|resume>",
            help = CordnCommands.USAGE,
            routes =
                mapOf(
                    "export" to { rest -> export(dataDir, rest) },
                    "import" to { rest -> import(dataDir, rest) },
                    "resume" to { rest -> resume(dataDir, rest) },
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
                    CordnMigration(ctx.client, ctx.signer, blobStore(servers))
                        .publish(snapshot, relays.toSet())

                // Only once the handoff is stored and advertised: a failed
                // publish that had already stood down would leave the account
                // refusing to send from the only copy of its state.
                ctx.cordn.restoreHandoff().markHandedOff()

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
                        // No session opens here until `migrate resume`.
                        "this_device_stopped" to true,
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

    /**
     * cordn-kmp's migration blob store over plain OkHttp. Each `amy` run is its own process, so a
     * client per call costs nothing that matters.
     */
    private fun blobStore(servers: List<String>): BlossomCordnBlobStore {
        val http = OkHttpClient.Builder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
        return BlossomCordnBlobStore(servers) { BlossomClient(http) }
    }

    private const val CALL_TIMEOUT_SECONDS = 60L

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
                    CordnMigration(ctx.client, ctx.signer, blobStore(emptyList()))
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
                // Not handed off, even if it was before: it now holds the
                // newest copy.
                ctx.cordn.restoreHandoff().resume()

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

    /** Takes this home back after a handoff the other device never imported. */
    private suspend fun resume(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        Args(tail).rejectUnknown()
        return Context.open(dataDir).use { ctx ->
            val handoff = ctx.cordn.restoreHandoff()
            val wasHandedOff = handoff.handedOff.value
            handoff.resume()
            Output.emit(
                mapOf(
                    "was_handed_off" to wasHandedOff,
                    "this_device_stopped" to false,
                ),
            )
            0
        }
    }
}
