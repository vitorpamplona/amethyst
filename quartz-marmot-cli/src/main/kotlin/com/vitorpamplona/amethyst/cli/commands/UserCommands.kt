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
import com.vitorpamplona.amethyst.cli.output.Output

class UsersCommand : CliktCommand(name = "users") {
    override fun commandHelp(context: Context) = "User lookup"

    init {
        subcommands(
            UsersShowCommand(),
            UsersSearchCommand(),
        )
    }

    override fun run() = Unit
}

class UsersShowCommand : CliktCommand(name = "show") {
    override fun commandHelp(context: Context) = "Show user info"

    val pubkey by argument(help = "User pubkey or npub")

    override fun run() {
        Output.error("users show: not yet implemented")
    }
}

class UsersSearchCommand : CliktCommand(name = "search") {
    override fun commandHelp(context: Context) = "Search for users"

    val query by argument(help = "Search query")

    override fun run() {
        Output.error("users search: not yet implemented")
    }
}
