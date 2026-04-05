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
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.vitorpamplona.amethyst.cli.accountStore
import com.vitorpamplona.amethyst.cli.output.Output
import com.vitorpamplona.amethyst.cli.resolveAccount

class RelaysCommand : CliktCommand(name = "relays") {
    override fun commandHelp(context: Context) = "Relay management"

    init {
        subcommands(
            RelaysListCommand(),
            RelaysAddCommand(),
            RelaysRemoveCommand(),
        )
    }

    override fun run() = Unit
}

class RelaysListCommand : CliktCommand(name = "list") {
    override fun commandHelp(context: Context) = "List configured relays"

    override fun run() {
        val account = resolveAccount()
        if (account == null) {
            Output.error("No account. Use 'create-identity' or 'login' first.")
            return
        }

        Output.table(
            headers = listOf("relay"),
            rows = account.relays.map { listOf(it) },
        )
    }
}

class RelaysAddCommand : CliktCommand(name = "add") {
    override fun commandHelp(context: Context) = "Add a relay"

    val url by argument(help = "Relay URL (wss://...)")

    override fun run() {
        val account = resolveAccount()
        if (account == null) {
            Output.error("No account. Use 'create-identity' or 'login' first.")
            return
        }

        val store = accountStore()
        val relays = store.getRelays(account.pubKeyHex).toMutableList()
        if (url in relays) {
            Output.error("Relay already configured: $url")
            return
        }
        relays.add(url)
        store.setRelays(account.pubKeyHex, relays)
        Output.success("Added relay: $url")
    }
}

class RelaysRemoveCommand : CliktCommand(name = "remove") {
    override fun commandHelp(context: Context) = "Remove a relay"

    val url by argument(help = "Relay URL to remove")

    override fun run() {
        val account = resolveAccount()
        if (account == null) {
            Output.error("No account. Use 'create-identity' or 'login' first.")
            return
        }

        val store = accountStore()
        val relays = store.getRelays(account.pubKeyHex).toMutableList()
        if (relays.remove(url)) {
            store.setRelays(account.pubKeyHex, relays)
            Output.success("Removed relay: $url")
        } else {
            Output.error("Relay not found: $url")
        }
    }
}
