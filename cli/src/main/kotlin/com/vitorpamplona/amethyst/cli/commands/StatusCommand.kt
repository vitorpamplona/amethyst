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

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.RunState
import com.vitorpamplona.amethyst.cli.StoreStats
import com.vitorpamplona.amethyst.cli.secrets.IdentityFile
import com.vitorpamplona.amethyst.cli.secrets.IdentitySecret
import java.io.File

/**
 * `amy status` — a single at-a-glance overview of everything amy is
 * holding on disk under `~/.amy/`. Built for the returning user: "I
 * haven't run this in months — what accounts do I have, which one is
 * active, can they still sign, and how big is the local database?"
 *
 * Cross-account by design, so it dispatches *before* account resolution
 * (like `use`) and never fails on "zero accounts" or "ambiguous account".
 * It is strictly read-only and metadata-only: it parses the on-disk
 * `identity.json` / `state.json` / `aliases.json` and walks the shared
 * event store, but it never unlocks a private key (no keychain prompt,
 * no NIP-49 passphrase) and never touches the network.
 *
 * Per account it reports the npub, how the key is stored (local keychain
 * / ncryptsec / plaintext, a NIP-46 bunker, or read-only), whether it can
 * sign, and the local footprint that account has accumulated: aliases,
 * Marmot groups, a published KeyPackage bundle, a Cashu wallet, and the
 * sync cursors that tell catch-up commands where they left off.
 */
object StatusCommand {
    fun run(tail: Array<String>): Int {
        // `status` takes no positional args; tolerate an accidental one
        // rather than erroring — it's a read-only inspection command.
        val rootBase = DataDir.DEFAULT_ROOT

        val currentPin =
            File(rootBase, DataDir.CURRENT_MARKER_NAME)
                .takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.ifEmpty { null }

        val accountNames = DataDir.listAccounts(rootBase)
        val accounts = accountNames.map { accountRow(File(rootBase, it), it, it == currentPin) }

        // The event store is shared across every account.
        val store = StoreStats.of(File(rootBase, "shared/events-store").toPath())

        Output.emit(
            mapOf(
                "root" to rootBase.absolutePath,
                "current" to currentPin,
                "account_count" to accounts.size,
                "accounts" to accounts,
                "store" to
                    mapOf(
                        "events" to store.events,
                        "distinct_kinds" to store.distinctKinds,
                        "disk_bytes" to store.diskBytes,
                        "oldest_at" to store.oldestAt,
                        "newest_at" to store.newestAt,
                        "root" to store.root.toString(),
                    ),
            ),
        )
        return 0
    }

    private fun accountRow(
        accountRoot: File,
        name: String,
        isCurrent: Boolean,
    ): Map<String, Any?> {
        val identity = readIdentity(File(accountRoot, "identity.json"))
        val signer = classifySigner(identity)

        val marmotGroups =
            File(accountRoot, "marmot/groups")
                .listFiles { f -> f.name.endsWith(".state") }
                ?.size ?: 0
        val hasKeyPackage = File(accountRoot, "marmot/keypackages.bundle").isFile
        val hasCashuWallet = File(accountRoot, "cashu.json").isFile
        val aliasCount = readAliases(File(accountRoot, "aliases.json")).size
        val runState = readRunState(File(accountRoot, "state.json"))

        // LinkedHashMap so the text renderer prints fields in this order.
        val row = LinkedHashMap<String, Any?>()
        row["name"] = name
        row["current"] = isCurrent
        row["npub"] = identity?.npub
        row["hex"] = identity?.pubKeyHex
        row["signer"] = signer.kind
        row["key_storage"] = signer.storage
        row["can_sign"] = signer.canSign
        if (signer.bunkerRelays != null) row["bunker_relays"] = signer.bunkerRelays
        row["aliases"] = aliasCount
        row["marmot_groups"] = marmotGroups
        row["key_package_published"] = hasKeyPackage
        row["cashu_wallet"] = hasCashuWallet
        row["dm_cursor_at"] = runState.giftWrapSince
        row["marmot_group_cursors"] = runState.groupSince.size
        return row
    }

    /**
     * How this account can sign, derived purely from the on-disk
     * [IdentityFile] — never resolves the secret itself.
     *  - `local`     — an on-device private key ([storage] says where).
     *  - `bunker`    — a NIP-46 remote signer ([bunkerRelays] lists it).
     *  - `read-only` — imported from an npub/nprofile/NIP-05; cannot sign.
     */
    private data class SignerInfo(
        val kind: String,
        val storage: String?,
        val canSign: Boolean,
        val bunkerRelays: List<String>?,
    )

    private fun classifySigner(identity: IdentityFile?): SignerInfo {
        if (identity == null) return SignerInfo("unknown", null, false, null)
        identity.bunker?.let { bunker ->
            return SignerInfo("bunker", secretStorageLabel(identity.secret), true, bunker.relays)
        }
        val storage = secretStorageLabel(identity.secret)
        return when {
            identity.secret != null -> SignerInfo("local", storage, true, null)
            // Pre-secret-store data-dirs kept the key inline; still signable.
            identity.privKeyHex != null || identity.nsec != null -> SignerInfo("local", "legacy-plaintext", true, null)
            else -> SignerInfo("read-only", null, false, null)
        }
    }

    private fun secretStorageLabel(secret: IdentitySecret?): String? =
        when (secret) {
            is IdentitySecret.Keychain -> "keychain:${secret.backend}"
            is IdentitySecret.Ncryptsec -> "ncryptsec"
            is IdentitySecret.Plaintext -> "plaintext"
            null -> null
        }

    private fun readIdentity(file: File): IdentityFile? = if (file.isFile) runCatching { Output.mapper.readValue<IdentityFile>(file.readText()) }.getOrNull() else null

    private fun readAliases(file: File): Map<String, String> = if (file.isFile) runCatching { Output.mapper.readValue<Map<String, String>>(file.readText()) }.getOrElse { emptyMap() } else emptyMap()

    private fun readRunState(file: File): RunState = if (file.isFile) runCatching { Output.mapper.readValue<RunState>(file.readText()) }.getOrElse { RunState() } else RunState()
}
