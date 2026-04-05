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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.vitorpamplona.amethyst.cli.accountStore
import com.vitorpamplona.amethyst.cli.engine.MarmotEngine
import com.vitorpamplona.amethyst.cli.findWnCommand
import com.vitorpamplona.amethyst.cli.output.Output
import com.vitorpamplona.amethyst.cli.resolveAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

class MessagesCommand : CliktCommand(name = "messages") {
    override fun commandHelp(context: Context) = "Message operations"

    init {
        subcommands(
            MessagesSendCommand(),
            MessagesListCommand(),
            MessagesSubscribeCommand(),
            MessagesDeleteCommand(),
            MessagesRetryCommand(),
            MessagesSearchCommand(),
            MessagesReactCommand(),
            MessagesUnreactCommand(),
        )
    }

    override fun run() = Unit
}

class MessagesSendCommand : CliktCommand(name = "send") {
    override fun commandHelp(context: Context) = "Send a message to a group"

    val groupId by argument(help = "Group ID")
    val content by argument(help = "Message content")

    override fun run() {
        val account = resolveAccount()
        if (account == null) {
            Output.error("No account. Use 'create-identity' or 'login' first.")
            return
        }

        val store = accountStore()
        val engine =
            MarmotEngine(
                account = account,
                signer = store.signerFor(account),
                dataDir = File(findWnCommand().dataDir),
            )
        engine.init(connectRelays = true)

        try {
            val eventId = engine.sendMessage(groupId, content)
            if (eventId != null) {
                Output.success("Message sent. Event ID: $eventId")
            } else {
                Output.error("Failed to send message. Group not found: $groupId")
            }
        } finally {
            engine.shutdown()
        }
    }
}

class MessagesListCommand : CliktCommand(name = "list") {
    override fun commandHelp(context: Context) = "List messages in a group"

    val groupId by argument(help = "Group ID")
    val limit by option("--limit", "-l", help = "Maximum number of messages").int().default(50)

    override fun run() {
        Output.error("messages list: not yet implemented (requires relay query)")
    }
}

class MessagesSubscribeCommand : CliktCommand(name = "subscribe") {
    override fun commandHelp(context: Context) = "Subscribe to live messages in a group"

    val groupId by argument(help = "Group ID")

    override fun run() {
        val account = resolveAccount()
        if (account == null) {
            Output.error("No account. Use 'create-identity' or 'login' first.")
            return
        }

        val store = accountStore()
        val engine =
            MarmotEngine(
                account = account,
                signer = store.signerFor(account),
                dataDir = File(findWnCommand().dataDir),
            )
        engine.init(connectRelays = true)

        try {
            println("Subscribing to group $groupId... (Ctrl+C to stop)")
            engine.subscribeToGroup(groupId)

            runBlocking {
                while (true) {
                    delay(1000)
                }
            }
        } finally {
            engine.shutdown()
        }
    }
}

class MessagesDeleteCommand : CliktCommand(name = "delete") {
    override fun commandHelp(context: Context) = "Delete a message"

    val messageId by argument(help = "Message event ID")

    override fun run() {
        Output.error("messages delete: not yet implemented")
    }
}

class MessagesRetryCommand : CliktCommand(name = "retry") {
    override fun commandHelp(context: Context) = "Retry sending a failed message"

    val messageId by argument(help = "Message event ID")

    override fun run() {
        Output.error("messages retry: not yet implemented")
    }
}

class MessagesSearchCommand : CliktCommand(name = "search") {
    override fun commandHelp(context: Context) = "Search messages in a group"

    val groupId by argument(help = "Group ID")
    val query by argument(help = "Search query")

    override fun run() {
        Output.error("messages search: not yet implemented")
    }
}

class MessagesReactCommand : CliktCommand(name = "react") {
    override fun commandHelp(context: Context) = "React to a message"

    val messageId by argument(help = "Message event ID")
    val reaction by argument(help = "Reaction emoji or text")

    override fun run() {
        Output.error("messages react: not yet implemented")
    }
}

class MessagesUnreactCommand : CliktCommand(name = "unreact") {
    override fun commandHelp(context: Context) = "Remove a reaction from a message"

    val messageId by argument(help = "Message event ID")

    override fun run() {
        Output.error("messages unreact: not yet implemented")
    }
}
