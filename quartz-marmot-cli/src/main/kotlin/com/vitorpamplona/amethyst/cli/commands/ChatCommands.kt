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

class ChatsCommand : CliktCommand(name = "chats") {
    override fun commandHelp(context: Context) = "Chat management"

    init {
        subcommands(
            ChatsListCommand(),
            ChatsSubscribeCommand(),
            ChatsArchiveCommand(),
            ChatsUnarchiveCommand(),
            ChatsListArchivedCommand(),
            ChatsSubscribeArchivedCommand(),
            ChatsMuteCommand(),
            ChatsUnmuteCommand(),
        )
    }

    override fun run() = Unit
}

class ChatsListCommand : CliktCommand(name = "list") {
    override fun commandHelp(context: Context) = "List all chats"

    override fun run() {
        Output.error("chats list: not yet implemented")
    }
}

class ChatsSubscribeCommand : CliktCommand(name = "subscribe") {
    override fun commandHelp(context: Context) = "Subscribe to chat updates"

    override fun run() {
        Output.error("chats subscribe: not yet implemented")
    }
}

class ChatsArchiveCommand : CliktCommand(name = "archive") {
    override fun commandHelp(context: Context) = "Archive a chat"

    val groupId by argument(help = "Group ID to archive")

    override fun run() {
        Output.error("chats archive: not yet implemented")
    }
}

class ChatsUnarchiveCommand : CliktCommand(name = "unarchive") {
    override fun commandHelp(context: Context) = "Unarchive a chat"

    val groupId by argument(help = "Group ID to unarchive")

    override fun run() {
        Output.error("chats unarchive: not yet implemented")
    }
}

class ChatsListArchivedCommand : CliktCommand(name = "list-archived") {
    override fun commandHelp(context: Context) = "List archived chats"

    override fun run() {
        Output.error("chats list-archived: not yet implemented")
    }
}

class ChatsSubscribeArchivedCommand : CliktCommand(name = "subscribe-archived") {
    override fun commandHelp(context: Context) = "Subscribe to archived chat updates"

    override fun run() {
        Output.error("chats subscribe-archived: not yet implemented")
    }
}

class ChatsMuteCommand : CliktCommand(name = "mute") {
    override fun commandHelp(context: Context) = "Mute a chat"

    val groupId by argument(help = "Group ID to mute")

    override fun run() {
        Output.error("chats mute: not yet implemented")
    }
}

class ChatsUnmuteCommand : CliktCommand(name = "unmute") {
    override fun commandHelp(context: Context) = "Unmute a chat"

    val groupId by argument(help = "Group ID to unmute")

    override fun run() {
        Output.error("chats unmute: not yet implemented")
    }
}
