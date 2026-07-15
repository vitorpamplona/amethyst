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

import com.vitorpamplona.amethyst.cli.DataDir

object GroupCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "group",
            tail,
            "group <create|list|show|…>",
            mapOf(
                "create" to { rest -> GroupCreateCommand.run(dataDir, rest) },
                "list" to { _ -> GroupReadCommands.list(dataDir) },
                "show" to { rest -> GroupReadCommands.show(dataDir, rest) },
                "members" to { rest -> GroupReadCommands.members(dataDir, rest) },
                "admins" to { rest -> GroupReadCommands.admins(dataDir, rest) },
                "add" to { rest -> GroupAddMemberCommand.run(dataDir, rest) },
                "rename" to { rest -> GroupMetadataCommands.rename(dataDir, rest) },
                "promote" to { rest -> GroupMetadataCommands.promote(dataDir, rest) },
                "demote" to { rest -> GroupMetadataCommands.demote(dataDir, rest) },
                "set-image" to { rest -> GroupMetadataCommands.setImage(dataDir, rest) },
                "clear-image" to { rest -> GroupMetadataCommands.clearImage(dataDir, rest) },
                "remove" to { rest -> GroupMembershipCommands.remove(dataDir, rest) },
                "leave" to { rest -> GroupMembershipCommands.leave(dataDir, rest) },
            ),
        )
}
