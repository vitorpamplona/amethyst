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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.vitorpamplona.amethyst.cli.accountStore
import com.vitorpamplona.amethyst.cli.output.Output
import com.vitorpamplona.amethyst.cli.resolveAccount
import kotlinx.serialization.json.put

class CreateIdentityCommand : CliktCommand(name = "create-identity") {
    override fun commandHelp(context: Context) = "Create a new Nostr identity"

    override fun run() {
        val store = accountStore()
        val info = store.createIdentity()

        Output.keyValue(
            listOf(
                "npub" to info.npub,
                "nsec" to info.nsec,
                "pubkey" to info.pubKeyHex,
            ),
        )
    }
}

class LoginCommand : CliktCommand(name = "login") {
    override fun commandHelp(context: Context) = "Log in with an nsec or hex private key"

    val nsec by argument(help = "nsec or hex private key")
    val relay by option("--relay", "-r", help = "Relay URL to use").multiple()

    override fun run() {
        val store = accountStore()
        val info = store.login(nsec, relay)

        Output.success(
            "Logged in as ${info.npub}",
            kotlinx.serialization.json.buildJsonObject {
                put("npub", info.npub)
                put("pubkey", info.pubKeyHex)
                put(
                    "relays",
                    kotlinx.serialization.json.buildJsonArray {
                        info.relays.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    },
                )
            },
        )
    }
}

class LogoutCommand : CliktCommand(name = "logout") {
    override fun commandHelp(context: Context) = "Log out an account"

    val pubkey by argument(help = "npub or hex pubkey to log out").optional()

    override fun run() {
        val store = accountStore()
        val target =
            pubkey ?: store.getDefaultAccount() ?: run {
                Output.error("No account specified and no default account set")
                return
            }

        if (store.logout(target)) {
            Output.success("Logged out $target")
        } else {
            Output.error("Account not found: $target")
        }
    }
}

class WhoamiCommand : CliktCommand(name = "whoami") {
    override fun commandHelp(context: Context) = "Show current account"

    override fun run() {
        val account = resolveAccount()
        if (account == null) {
            Output.error("No account logged in. Use 'create-identity' or 'login' first.")
            return
        }

        Output.keyValue(
            listOf(
                "npub" to account.npub,
                "pubkey" to account.pubKeyHex,
                "relays" to account.relays.joinToString(", "),
            ),
        )
    }
}

class ExportNsecCommand : CliktCommand(name = "export-nsec") {
    override fun commandHelp(context: Context) = "Export nsec for an account"

    val pubkey by argument(help = "npub or hex pubkey").optional()

    override fun run() {
        val store = accountStore()
        val target = pubkey ?: store.getDefaultAccount()

        if (target == null) {
            Output.error("No account specified")
            return
        }

        val account = resolveAccount()
        if (account == null) {
            Output.error("Account not found: $target")
            return
        }

        Output.keyValue(
            listOf(
                "nsec" to account.nsec,
                "pubkey" to account.pubKeyHex,
                "npub" to account.npub,
            ),
        )
    }
}
