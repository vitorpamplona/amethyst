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

class MediaCommand : CliktCommand(name = "media") {
    override fun commandHelp(context: Context) = "Encrypted media operations"

    init {
        subcommands(
            MediaUploadCommand(),
            MediaDownloadCommand(),
            MediaListCommand(),
        )
    }

    override fun run() = Unit
}

class MediaUploadCommand : CliktCommand(name = "upload") {
    override fun commandHelp(context: Context) = "Upload and encrypt media for a group"

    val groupId by argument(help = "Group ID")
    val filePath by argument(help = "File to upload")

    override fun run() {
        Output.error("media upload: not yet implemented")
    }
}

class MediaDownloadCommand : CliktCommand(name = "download") {
    override fun commandHelp(context: Context) = "Download and decrypt group media"

    val groupId by argument(help = "Group ID")
    val hash by argument(help = "Media hash")

    override fun run() {
        Output.error("media download: not yet implemented")
    }
}

class MediaListCommand : CliktCommand(name = "list") {
    override fun commandHelp(context: Context) = "List media in a group"

    val groupId by argument(help = "Group ID")

    override fun run() {
        Output.error("media list: not yet implemented")
    }
}
