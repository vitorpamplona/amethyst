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

class FollowsCommand : CliktCommand(name = "follows") {
    override fun commandHelp(context: Context) = "Follow management"

    init {
        subcommands(
            FollowsListCommand(),
            FollowsAddCommand(),
            FollowsRemoveCommand(),
            FollowsCheckCommand(),
        )
    }

    override fun run() = Unit
}

class FollowsListCommand : CliktCommand(name = "list") {
    override fun commandHelp(context: Context) = "List follows"

    override fun run() {
        Output.error("follows list: not yet implemented")
    }
}

class FollowsAddCommand : CliktCommand(name = "add") {
    override fun commandHelp(context: Context) = "Follow a user"

    val pubkey by argument(help = "User pubkey or npub")

    override fun run() {
        Output.error("follows add: not yet implemented")
    }
}

class FollowsRemoveCommand : CliktCommand(name = "remove") {
    override fun commandHelp(context: Context) = "Unfollow a user"

    val pubkey by argument(help = "User pubkey or npub")

    override fun run() {
        Output.error("follows remove: not yet implemented")
    }
}

class FollowsCheckCommand : CliktCommand(name = "check") {
    override fun commandHelp(context: Context) = "Check if following a user"

    val pubkey by argument(help = "User pubkey or npub")

    override fun run() {
        Output.error("follows check: not yet implemented")
    }
}
