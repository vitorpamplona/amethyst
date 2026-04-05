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
import com.vitorpamplona.amethyst.cli.accountStore
import com.vitorpamplona.amethyst.cli.output.Output

class AccountsCommand : CliktCommand(name = "accounts") {
    override fun commandHelp(context: Context) = "Account management"

    init {
        subcommands(AccountsListCommand())
    }

    override fun run() = Unit
}

class AccountsListCommand : CliktCommand(name = "list") {
    override fun commandHelp(context: Context) = "List all accounts"

    override fun run() {
        val store = accountStore()
        val accounts = store.listAccounts()
        val defaultHex = store.getDefaultAccount()

        if (accounts.isEmpty()) {
            Output.success("No accounts. Use 'create-identity' or 'login' to add one.")
            return
        }

        Output.table(
            headers = listOf("npub", "pubkey", "default"),
            rows =
                accounts.map { account ->
                    listOf(
                        account.npub,
                        account.pubKeyHex.take(16) + "...",
                        if (account.pubKeyHex == defaultHex) "*" else "",
                    )
                },
        )
    }
}
