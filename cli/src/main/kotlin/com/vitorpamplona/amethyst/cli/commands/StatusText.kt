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

import com.vitorpamplona.amethyst.cli.Ansi
import com.vitorpamplona.amethyst.cli.Output
import java.io.File

/**
 * The terminal rendering of [StatusCommand] — the one command whose answer
 * the generic key/value renderer buries.
 *
 * One block per account — three lines of identity, then one line per
 * thing the account has saved:
 *
 * ```
 * alice (current)
 *   Alice Jones · alice@example.com
 *   npub1hje47kz5qeneyqrxc9nzgmz06ml6l9lguqv0qtsz4rkwqkmf636qvg4sz3
 *   local key, in the login keychain
 *   saved: 128 events (newest 2h ago)
 *          3 contacts
 *          2 Marmot groups
 * ```
 *
 * The footprint gets a line per item rather than one `·`-joined run: a
 * busy account lists six or seven of them, and a column of short lines
 * scans in one pass where a wrapped sentence does not.
 *
 * The rule that keeps it short: **absent is silent**. A profile the store
 * hasn't seen, a footprint an account doesn't have — those lines simply
 * don't print, instead of printing as `(none)` / `no` / `0`. Only the npub
 * and the signer line are unconditional, because those two ARE the status.
 *
 * The `--json` shape is the public contract and lives in [StatusReport];
 * this file is free to change with taste.
 */
internal object StatusText {
    private const val DOT = " · "

    /** Hanging indent for the `saved:` list — the visible width of `"  saved: "`. */
    private val SAVED_INDENT = " ".repeat("  saved: ".length)

    fun render(
        rootBase: File,
        accounts: List<StatusReport.AccountReport>,
        color: Ansi,
    ): String {
        if (accounts.isEmpty()) {
            return buildString {
                append(color.dim("No accounts under ${rootBase.absolutePath}"))
                append("\n\n")
                append("Create one with `amy --account <name> init`")
            }
        }

        val out = StringBuilder()
        for (account in accounts) {
            if (out.isNotEmpty()) out.append('\n')
            appendAccount(out, account, color)
        }
        out.append('\n').append(footer(rootBase, accounts, color))
        return out.toString()
    }

    private fun appendAccount(
        out: StringBuilder,
        account: StatusReport.AccountReport,
        color: Ansi,
    ) {
        out.append(color.bold(account.name))
        if (account.isCurrent) out.append(' ').append(color.green("(current)"))
        out.append('\n')

        // Line 1 — the human, when the local store knows one. A profile amy
        // has never fetched simply doesn't get a line.
        val who = listOfNotNull(account.profileName, account.nip05)
        if (who.isNotEmpty()) out.append("  ").append(who.joinToString(DOT)).append('\n')

        // Line 2 — the identifier, whenever there is one to print.
        account.npub?.let { out.append("  ").append(it).append('\n') }

        // Line 3 — can this account act, and with what key. For a directory
        // with no usable identity.json this line IS the whole story, which is
        // why the npub above and the footprint below both drop out.
        out.append("  ").append(signerLine(account.name, account.signer, color)).append('\n')
        if (account.npub == null) return

        // Then the footprint — one line per item, hanging off the label — or
        // one honest word when there isn't one.
        val label = "  " + color.dim("saved:") + " "
        if (account.saved.isEmpty) {
            out.append(label).append(color.dim("nothing yet")).append('\n')
        } else {
            savedParts(account.saved).forEachIndexed { i, part ->
                out.append(if (i == 0) label else SAVED_INDENT).append(part).append('\n')
            }
        }
    }

    /**
     * Plain-English "how do I sign", because `signer: local` +
     * `key_storage: keychain:login` + `can_sign: yes` on three lines was
     * three facts where users only ever wanted one sentence.
     */
    private fun signerLine(
        name: String,
        signer: StatusReport.SignerInfo,
        color: Ansi,
    ): String =
        when (signer.kind) {
            "local" ->
                when (val storage = signer.storage) {
                    "ncryptsec" -> "local key, passphrase-encrypted"
                    // Worth a warning colour: the key is readable by anything
                    // running as this user.
                    "plaintext" -> "local key, " + color.yellow("stored unencrypted")
                    "legacy-plaintext" -> "local key, " + color.yellow("stored unencrypted") + " (pre-secret-store format)"
                    null -> "local key"
                    else -> "local key, in the ${storage.removePrefix("keychain:")} keychain"
                }

            "bunker" -> {
                val via = signer.bunkerRelays?.firstOrNull()
                val more = (signer.bunkerRelays?.size ?: 0) - 1
                val suffix =
                    when {
                        via == null -> ""
                        more > 0 -> " via $via (+$more more)"
                        else -> " via $via"
                    }
                "remote signer (NIP-46)$suffix"
            }

            "read-only" -> color.dim("read-only — public key only, cannot sign")
            "unreadable" -> color.red("identity.json is unreadable — check the file before re-running `init`")
            // No identity.json at all: the directory exists but `init` never ran.
            else -> color.red("no identity — run `amy --account $name init`")
        }

    /**
     * The footprint, most-interesting first, absent items omitted. One
     * self-contained phrase per entry, since each gets its own line.
     */
    private fun savedParts(saved: StatusReport.Saved): List<String> {
        val parts = mutableListOf<String>()
        if (saved.events > 0) {
            val age = saved.newestEventAt?.let { " (newest ${ago(it)})" } ?: ""
            parts += "${plural(saved.events, "event")}$age"
        }
        if (saved.contacts > 0) parts += plural(saved.contacts, "contact")
        if (saved.marmotGroups > 0) parts += plural(saved.marmotGroups, "Marmot group")
        if (saved.keyPackage) parts += "a published key package"
        if (saved.concordCommunities > 0) parts += plural(saved.concordCommunities, "Concord community", "Concord communities")
        if (saved.cashuWallet) parts += "a Cashu wallet"
        if (saved.dmCursorAt != null) parts += "DMs synced ${ago(saved.dmCursorAt)}"
        return parts
    }

    private fun footer(
        rootBase: File,
        accounts: List<StatusReport.AccountReport>,
        color: Ansi,
    ): String {
        val head = "${plural(accounts.size, "account")} under ${rootBase.absolutePath}"
        // Only nag about switching when there is somewhere to switch to.
        val hint = if (accounts.size > 1) DOT + "switch with `amy use <name>`" else ""
        return color.dim(head + hint)
    }

    private fun ago(epochSeconds: Long): String = Output.relativeTime(System.currentTimeMillis() / 1000 - epochSeconds)

    private fun plural(
        n: Int,
        singular: String,
        plural: String = singular + "s",
    ): String = if (n == 1) "$n $singular" else "$n $plural"
}
