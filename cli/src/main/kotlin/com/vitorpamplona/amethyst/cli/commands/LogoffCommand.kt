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

import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.domain.nip46.RemoteSignerLogoutUseCase
import com.vitorpamplona.quartz.nip01Core.kite.KiteWriter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.store.fs.FsEventStore
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * `amy logoff [--yes] [--keep-events]` — log off an account and clear its
 * local data.
 *
 * "Logging off" a CLI with no server session means removing everything the
 * account left on this machine:
 *  - the identity file and any backend-held secret (keychain / ncryptsec /
 *    plaintext) — via [DataDir.deleteIdentity],
 *  - the rest of the per-account directory `~/.amy/<account>/` (run-state
 *    cursors, aliases, cashu counters, all MLS/Marmot state),
 *  - the active-account pin at `~/.amy/current`, if it points here,
 *  - and the account's events in the SHARED store at
 *    `~/.amy/shared/events-store/`.
 *
 * The event store is shared across every account on the machine, so this
 * does NOT wipe it wholesale — it deletes only the events that involve this
 * account: those it authored (`authors`) plus those addressed to it via a
 * `#p` tag (inbound gift wraps, nutzaps, reactions, mentions…). Other
 * accounts' cached events are untouched. Pass `--keep-events` to leave the
 * shared cache alone and only remove the identity + per-account state.
 *
 * The account is selected the normal way (the `--account` flag, the
 * `current` pin, or the sole account) — when more than one account exists
 * and none is pinned, [DataDir.resolve] already errors out asking the caller
 * to disambiguate, so logoff never guesses which account to destroy.
 *
 * Reads the public key straight from `identity.json` (never unlocking the
 * private key), so it needs no passphrase and pops no keychain prompt.
 *
 * A NIP-46 bunker account first tells its remote signer (`logout`, bounded,
 * best effort) so the signer can drop the session; this is the one step that
 * goes to the network and unlocks the transport key (never the user's key).
 * `--offline` skips it, and a key that can't be unlocked without a prompt only
 * skips it with a warning. The key is deleted either way, as NIP-46 requires.
 *
 * Requires `--yes` to execute, because it is destructive and cannot be
 * undone — the private key is gone with the identity file. Without `--yes`
 * the command reports what it would delete and exits with code 2.
 */
object LogoffCommand {
    val USAGE: String =
        """
        |amy logoff — log off: delete this account's key, per-account state, and its
        |events in the shared store
        |
        |  logoff [--yes] [--keep-events] [--offline]
        |                                    requires --yes; without it, prints a dry run
        |                                    (exit 2). --keep-events skips the cache purge.
        |                                    A bunker account tells its signer first
        |                                    (NIP-46 logout); --offline skips that.
        """.trimMargin()

    suspend fun run(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.firstOrNull() == "--help" || tail.firstOrNull() == "-h") {
            System.err.println(USAGE)
            return 0
        }
        val confirmed = tail.any { it == "--yes" || it == "-y" }
        val keepEvents = tail.any { it == "--keep-events" }
        val offline = tail.any { it == "--offline" }

        // Read the on-disk identity metadata only — no SecretStore round-trip,
        // so we never prompt for a passphrase or trip a keychain dialog just
        // to log off.
        val idFile =
            dataDir.loadIdentityFileOrNull()
                ?: return Output.error(
                    "no_account",
                    "no identity at ${dataDir.identityFile.absolutePath}; nothing to log off",
                )
        val pubkey = idFile.pubKeyHex
        val notifySigner = idFile.bunker != null && !offline

        val marker = File(DataDir.DEFAULT_ROOT, DataDir.CURRENT_MARKER_NAME)
        val isPinned = marker.isFile && marker.readText().trim() == dataDir.accountName

        // Everything the account touched in the shared store: authored by it,
        // or addressed to it via a #p tag (gift wraps, nutzaps, reactions…).
        val involvedFilters =
            listOf(
                Filter(authors = listOf(pubkey)),
                Filter(tags = mapOf("p" to listOf(pubkey))),
            )

        if (!confirmed) {
            val eventCount = if (keepEvents) 0 else withStore(dataDir) { it.count(involvedFilters) }
            Output.emit(
                mapOf(
                    "dry_run" to true,
                    "account" to dataDir.accountName,
                    "npub" to idFile.npub,
                    "pubkey" to pubkey,
                    "account_dir" to dataDir.root.absolutePath,
                    "pinned" to isPinned,
                    "events_to_purge" to eventCount,
                    "keep_events" to keepEvents,
                    "notify_signer" to notifySigner,
                    "detail" to "pass --yes to permanently delete this account's key, local state" +
                        (if (keepEvents) "" else ", and cached events"),
                ),
            )
            return 2
        }

        // 0. A bunker account tells its signer, while the transport key still exists.
        val signerNotified = if (notifySigner) tellSigner(dataDir) else null

        // 1. Purge the account's events from the shared store.
        var purged = 0
        if (!keepEvents) {
            withStore(dataDir) { store ->
                val before = store.count(involvedFilters)
                store.delete(involvedFilters)
                purged = (before - store.count(involvedFilters)).coerceAtLeast(0)
            }
        }

        // 2. Remove the identity file and any backend-held secret.
        dataDir.deleteIdentity()

        // 3. Wipe the rest of the per-account directory (run-state, aliases,
        //    cashu counters, Marmot/MLS state). The shared events-store lives
        //    outside this directory, so it is not affected.
        val dirFullyRemoved = dataDir.root.deleteRecursively()

        // 4. Drop the active-account pin if it pointed at this account.
        val clearedPin = isPinned && marker.delete()

        Output.emit(
            mapOf(
                "logoff" to true,
                "account" to dataDir.accountName,
                "npub" to idFile.npub,
                "events_purged" to purged,
                "removed_dir" to dataDir.root.absolutePath,
                "dir_fully_removed" to dirFullyRemoved,
                "cleared_pin" to clearedPin,
            ) + (if (signerNotified != null) mapOf("signer_notified" to signerNotified) else emptyMap()),
        )
        return 0
    }

    /**
     * NIP-46 `logout` to the account's remote signer. Whether the signer acknowledged it; false when
     * the transport key could not be unlocked here (a passphrase with no TTY) or the signer was silent.
     */
    private suspend fun tellSigner(dataDir: DataDir): Boolean {
        val ctx =
            try {
                Context.open(dataDir)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                System.err.println("warning: could not unlock the bunker transport key; the remote signer was not told: ${e.message}")
                return false
            }
        return ctx.use {
            val bunker = it.identity.bunker ?: return@use false
            it.client.connect()
            val relays = bunker.relays.mapNotNull { url -> RelayUrlNormalizer.normalizeOrNull(url) }.toSet()
            RemoteSignerLogoutUseCase
                .execute(NostrConnectURI.buildBunker(bunker.remotePubkey, relays, secret = null), bunker.clientPrivKeyHex, it.client)
                .also { acked -> if (!acked) System.err.println("warning: the remote signer did not acknowledge the logout") }
        }
    }

    /**
     * Open the shared [FsEventStore] directly — logoff needs the store but no
     * identity, signer, or relays, so it skips [com.vitorpamplona.amethyst.cli.Context.open]
     * (which requires a bootstrapped identity). Mirrors `StoreCommands.withStore`.
     */
    private inline fun <T> withStore(
        dataDir: DataDir,
        body: (FsEventStore) -> T,
    ): T {
        val store =
            FsEventStore(
                root = dataDir.eventsDir.toPath(),
                eventToJson = { KiteWriter.toPrettyJson(it) },
            )
        try {
            return body(store)
        } finally {
            store.close()
        }
    }
}
