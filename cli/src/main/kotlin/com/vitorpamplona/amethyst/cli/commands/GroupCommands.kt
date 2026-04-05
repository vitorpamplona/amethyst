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
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.vitorpamplona.amethyst.cli.accountStore
import com.vitorpamplona.amethyst.cli.engine.MarmotEngine
import com.vitorpamplona.amethyst.cli.findWnCommand
import com.vitorpamplona.amethyst.cli.output.Output
import com.vitorpamplona.amethyst.cli.resolveAccount
import java.io.File

class GroupsCommand : CliktCommand(name = "groups") {
    override fun commandHelp(context: Context) = "Group management"

    init {
        subcommands(
            GroupsListCommand(),
            GroupsCreateCommand(),
            GroupsShowCommand(),
            GroupsAddMembersCommand(),
            GroupsRemoveMembersCommand(),
            GroupsMembersCommand(),
            GroupsAdminsCommand(),
            GroupsRelaysCommand(),
            GroupsLeaveCommand(),
            GroupsRenameCommand(),
            GroupsInvitesCommand(),
            GroupsAcceptCommand(),
            GroupsDeclineCommand(),
            GroupsPromoteCommand(),
            GroupsDemoteCommand(),
            GroupsSelfDemoteCommand(),
        )
    }

    override fun run() = Unit
}

class GroupsListCommand : CliktCommand(name = "list") {
    override fun commandHelp(context: Context) = "List all groups"

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

        try {
            val groups = engine.listGroups()
            if (groups.isEmpty()) {
                Output.success("No groups. Use 'groups create' to create one.")
                return
            }

            Output.table(
                headers = listOf("group_id", "name", "members", "epoch"),
                rows =
                    groups.map {
                        listOf(
                            it.nostrGroupId.take(16) + "...",
                            it.name,
                            it.memberCount.toString(),
                            it.epoch.toString(),
                        )
                    },
            )
        } finally {
            engine.shutdown()
        }
    }
}

class GroupsCreateCommand : CliktCommand(name = "create") {
    override fun commandHelp(context: Context) = "Create a new group"

    val name by option("--name", "-n", help = "Group name").default("New Group")
    val description by option("--description", "-d", help = "Group description").default("")
    val relay by option("--relay", "-r", help = "Relay URL for the group").multiple()

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
        engine.init()

        try {
            val group = engine.createGroup(name, description, relay)

            Output.keyValue(
                listOf(
                    "group_id" to group.nostrGroupId,
                    "name" to group.name,
                    "epoch" to group.epoch.toString(),
                    "members" to group.memberCount.toString(),
                    "relays" to group.relays.joinToString(", "),
                ),
            )
        } finally {
            engine.shutdown()
        }
    }
}

class GroupsShowCommand : CliktCommand(name = "show") {
    override fun commandHelp(context: Context) = "Show group details"

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

        try {
            val group = engine.showGroup(groupId)
            if (group == null) {
                Output.error("Group not found: $groupId")
                return
            }

            Output.keyValue(
                listOf(
                    "group_id" to group.nostrGroupId,
                    "name" to group.name,
                    "description" to group.description,
                    "epoch" to group.epoch.toString(),
                    "members" to group.memberCount.toString(),
                    "admins" to group.adminPubkeys.joinToString(", "),
                    "relays" to group.relays.joinToString(", "),
                ),
            )
        } finally {
            engine.shutdown()
        }
    }
}

class GroupsAddMembersCommand : CliktCommand(name = "add-members") {
    override fun commandHelp(context: Context) = "Add members to a group by their KeyPackage"

    val groupId by argument(help = "Group ID")
    val keyPackages by argument(help = "Base64-encoded KeyPackages").multiple()

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
        engine.init()

        try {
            for (kp in keyPackages) {
                val result = engine.addMember(groupId, kp)
                Output.keyValue(
                    listOf(
                        "commit" to result.commitBytes.take(32) + "...",
                        "welcome" to (result.welcomeBytes?.take(32)?.plus("...") ?: "none"),
                    ),
                )
            }
        } finally {
            engine.shutdown()
        }
    }
}

class GroupsRemoveMembersCommand : CliktCommand(name = "remove-members") {
    override fun commandHelp(context: Context) = "Remove members from a group by leaf index"

    val groupId by argument(help = "Group ID")
    val leafIndex by argument(help = "Leaf index of member to remove").int()

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
        engine.init()

        try {
            val commitB64 = engine.removeMember(groupId, leafIndex)
            Output.success("Member removed. Commit: ${commitB64.take(32)}...")
        } finally {
            engine.shutdown()
        }
    }
}

class GroupsMembersCommand : CliktCommand(name = "members") {
    override fun commandHelp(context: Context) = "List group members"

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

        try {
            val members = engine.groupMembers(groupId)
            if (members == null) {
                Output.error("Group not found: $groupId")
                return
            }

            Output.table(
                headers = listOf("leaf_index", "pubkey"),
                rows =
                    members.map {
                        listOf(it.leafIndex.toString(), it.pubkey)
                    },
            )
        } finally {
            engine.shutdown()
        }
    }
}

class GroupsAdminsCommand : CliktCommand(name = "admins") {
    override fun commandHelp(context: Context) = "List group admins"

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

        try {
            val group = engine.showGroup(groupId)
            if (group == null) {
                Output.error("Group not found: $groupId")
                return
            }

            Output.table(
                headers = listOf("pubkey"),
                rows = group.adminPubkeys.map { listOf(it) },
            )
        } finally {
            engine.shutdown()
        }
    }
}

class GroupsRelaysCommand : CliktCommand(name = "relays") {
    override fun commandHelp(context: Context) = "List group relays"

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

        try {
            val group = engine.showGroup(groupId)
            if (group == null) {
                Output.error("Group not found: $groupId")
                return
            }

            Output.table(
                headers = listOf("relay"),
                rows = group.relays.map { listOf(it) },
            )
        } finally {
            engine.shutdown()
        }
    }
}

class GroupsLeaveCommand : CliktCommand(name = "leave") {
    override fun commandHelp(context: Context) = "Leave a group"

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
        engine.init()

        try {
            val proposalB64 = engine.leaveGroup(groupId)
            Output.success("Left group $groupId. SelfRemove proposal: ${proposalB64.take(32)}...")
        } finally {
            engine.shutdown()
        }
    }
}

class GroupsRenameCommand : CliktCommand(name = "rename") {
    override fun commandHelp(context: Context) = "Rename a group"

    val groupId by argument(help = "Group ID")
    val name by argument(help = "New group name")

    override fun run() {
        Output.error("groups rename: not yet implemented")
    }
}

class GroupsInvitesCommand : CliktCommand(name = "invites") {
    override fun commandHelp(context: Context) = "List pending invites"

    override fun run() {
        Output.error("groups invites: not yet implemented")
    }
}

class GroupsAcceptCommand : CliktCommand(name = "accept") {
    override fun commandHelp(context: Context) = "Accept a group invite"

    val groupId by argument(help = "Group ID to accept")

    override fun run() {
        Output.error("groups accept: not yet implemented")
    }
}

class GroupsDeclineCommand : CliktCommand(name = "decline") {
    override fun commandHelp(context: Context) = "Decline a group invite"

    val groupId by argument(help = "Group ID to decline")

    override fun run() {
        Output.error("groups decline: not yet implemented")
    }
}

class GroupsPromoteCommand : CliktCommand(name = "promote") {
    override fun commandHelp(context: Context) = "Promote a member to admin"

    val groupId by argument(help = "Group ID")
    val pubkey by argument(help = "Member pubkey to promote")

    override fun run() {
        Output.error("groups promote: not yet implemented")
    }
}

class GroupsDemoteCommand : CliktCommand(name = "demote") {
    override fun commandHelp(context: Context) = "Demote an admin to member"

    val groupId by argument(help = "Group ID")
    val pubkey by argument(help = "Admin pubkey to demote")

    override fun run() {
        Output.error("groups demote: not yet implemented")
    }
}

class GroupsSelfDemoteCommand : CliktCommand(name = "self-demote") {
    override fun commandHelp(context: Context) = "Demote yourself from admin"

    val groupId by argument(help = "Group ID")

    override fun run() {
        Output.error("groups self-demote: not yet implemented")
    }
}
