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
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.vitorpamplona.amethyst.cli.findWnCommand
import com.vitorpamplona.amethyst.cli.output.Output
import java.io.File

// --- Notifications ---

class NotificationsCommand : CliktCommand(name = "notifications") {
    override fun commandHelp(context: Context) = "Push notification management"

    init {
        subcommands(NotificationsSubscribeCommand())
    }

    override fun run() = Unit
}

class NotificationsSubscribeCommand : CliktCommand(name = "subscribe") {
    override fun commandHelp(context: Context) = "Subscribe to push notifications"

    override fun run() {
        Output.error("notifications subscribe: not yet implemented")
    }
}

// --- Settings ---

class SettingsCommand : CliktCommand(name = "settings") {
    override fun commandHelp(context: Context) = "Application settings"

    init {
        subcommands(
            SettingsShowCommand(),
            SettingsThemeCommand(),
            SettingsLanguageCommand(),
        )
    }

    override fun run() = Unit
}

class SettingsShowCommand : CliktCommand(name = "show") {
    override fun commandHelp(context: Context) = "Show current settings"

    override fun run() {
        Output.error("settings show: not yet implemented")
    }
}

class SettingsThemeCommand : CliktCommand(name = "theme") {
    override fun commandHelp(context: Context) = "Set theme"

    val theme by argument(help = "Theme name (light, dark, system)")

    override fun run() {
        Output.error("settings theme: not yet implemented")
    }
}

class SettingsLanguageCommand : CliktCommand(name = "language") {
    override fun commandHelp(context: Context) = "Set language"

    val language by argument(help = "Language code")

    override fun run() {
        Output.error("settings language: not yet implemented")
    }
}

// --- Daemon ---

class DaemonCommand : CliktCommand(name = "daemon") {
    override fun commandHelp(context: Context) = "Daemon management"

    init {
        subcommands(
            DaemonStartCommand(),
            DaemonStopCommand(),
            DaemonStatusCommand(),
        )
    }

    override fun run() = Unit
}

class DaemonStartCommand : CliktCommand(name = "start") {
    override fun commandHelp(context: Context) = "Start the background daemon"

    override fun run() {
        Output.error("daemon start: not yet implemented")
    }
}

class DaemonStopCommand : CliktCommand(name = "stop") {
    override fun commandHelp(context: Context) = "Stop the background daemon"

    override fun run() {
        Output.error("daemon stop: not yet implemented")
    }
}

class DaemonStatusCommand : CliktCommand(name = "status") {
    override fun commandHelp(context: Context) = "Show daemon status"

    override fun run() {
        Output.error("daemon status: not yet implemented")
    }
}

// --- Debug ---

class DebugCommand : CliktCommand(name = "debug") {
    override fun commandHelp(context: Context) = "Debug utilities"

    init {
        subcommands(
            DebugRelayControlStateCommand(),
            DebugHealthCommand(),
            DebugRatchetTreeCommand(),
        )
    }

    override fun run() = Unit
}

class DebugRelayControlStateCommand : CliktCommand(name = "relay-control-state") {
    override fun commandHelp(context: Context) = "Show relay connection state"

    override fun run() {
        Output.error("debug relay-control-state: not yet implemented")
    }
}

class DebugHealthCommand : CliktCommand(name = "health") {
    override fun commandHelp(context: Context) = "Show health status"

    override fun run() {
        Output.error("debug health: not yet implemented")
    }
}

class DebugRatchetTreeCommand : CliktCommand(name = "ratchet-tree") {
    override fun commandHelp(context: Context) = "Show ratchet tree for a group"

    val groupId by argument(help = "Group ID")

    override fun run() {
        Output.error("debug ratchet-tree: not yet implemented")
    }
}

// --- Reset ---

class ResetCommand : CliktCommand(name = "reset") {
    override fun commandHelp(context: Context) = "Delete all data"

    val confirm by option("--confirm", help = "Confirm data deletion").flag()

    override fun run() {
        if (!confirm) {
            Output.error("This will delete ALL data. Pass --confirm to proceed.")
            return
        }

        val dataDir = File(findWnCommand().dataDir)
        if (dataDir.exists()) {
            dataDir.deleteRecursively()
            Output.success("All data deleted from ${dataDir.absolutePath}")
        } else {
            Output.success("No data directory found at ${dataDir.absolutePath}")
        }
    }
}
