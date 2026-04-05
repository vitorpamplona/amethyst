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
package com.vitorpamplona.amethyst.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.vitorpamplona.amethyst.cli.commands.AccountsCommand
import com.vitorpamplona.amethyst.cli.commands.ChatsCommand
import com.vitorpamplona.amethyst.cli.commands.CreateIdentityCommand
import com.vitorpamplona.amethyst.cli.commands.DaemonCommand
import com.vitorpamplona.amethyst.cli.commands.DebugCommand
import com.vitorpamplona.amethyst.cli.commands.ExportNsecCommand
import com.vitorpamplona.amethyst.cli.commands.FollowsCommand
import com.vitorpamplona.amethyst.cli.commands.GroupsCommand
import com.vitorpamplona.amethyst.cli.commands.KeysCommand
import com.vitorpamplona.amethyst.cli.commands.LoginCommand
import com.vitorpamplona.amethyst.cli.commands.LogoutCommand
import com.vitorpamplona.amethyst.cli.commands.MediaCommand
import com.vitorpamplona.amethyst.cli.commands.MessagesCommand
import com.vitorpamplona.amethyst.cli.commands.NotificationsCommand
import com.vitorpamplona.amethyst.cli.commands.ProfileCommand
import com.vitorpamplona.amethyst.cli.commands.RelaysCommand
import com.vitorpamplona.amethyst.cli.commands.ResetCommand
import com.vitorpamplona.amethyst.cli.commands.SettingsCommand
import com.vitorpamplona.amethyst.cli.commands.UsersCommand
import com.vitorpamplona.amethyst.cli.commands.WhoamiCommand
import com.vitorpamplona.amethyst.cli.engine.AccountStore
import com.vitorpamplona.amethyst.cli.output.Output
import java.io.File

class WnCommand : CliktCommand(name = "wn") {
    override fun commandHelp(context: Context) = "Marmot Protocol CLI - MLS-based E2E encrypted group messaging over Nostr"

    val jsonOutput by option("--json", help = "Output as JSON").flag()
    val account by option("--account", help = "Account to use (npub or hex pubkey)")
    val dataDir by
        option("--data-dir", help = "Data directory path")
            .default(
                System.getenv("WN_DATA_DIR")
                    ?: "${System.getProperty("user.home")}/.marmot",
            )

    override fun run() {
        Output.jsonMode = jsonOutput
    }
}

fun main(args: Array<String>) {
    WnCommand()
        .subcommands(
            // Identity
            CreateIdentityCommand(),
            LoginCommand(),
            LogoutCommand(),
            WhoamiCommand(),
            ExportNsecCommand(),
            // Account management
            AccountsCommand(),
            // Core Marmot operations
            KeysCommand(),
            GroupsCommand(),
            MessagesCommand(),
            ChatsCommand(),
            // Social
            FollowsCommand(),
            ProfileCommand(),
            UsersCommand(),
            // Infrastructure
            RelaysCommand(),
            MediaCommand(),
            NotificationsCommand(),
            SettingsCommand(),
            // System
            DaemonCommand(),
            DebugCommand(),
            ResetCommand(),
        ).main(args)
}

fun CliktCommand.accountStore(): AccountStore {
    val wn = findWnCommand()
    return AccountStore(File(wn.dataDir))
}

fun CliktCommand.findWnCommand(): WnCommand {
    var cmd: CliktCommand? = this
    while (cmd != null) {
        if (cmd is WnCommand) return cmd
        cmd = cmd.currentContext.parent?.command as? CliktCommand
    }
    error("WnCommand not found in command hierarchy")
}

fun CliktCommand.resolveAccount(): com.vitorpamplona.amethyst.cli.engine.AccountInfo? {
    val wn = findWnCommand()
    val store = AccountStore(File(wn.dataDir))
    return store.resolveAccount(wn.account)
}
