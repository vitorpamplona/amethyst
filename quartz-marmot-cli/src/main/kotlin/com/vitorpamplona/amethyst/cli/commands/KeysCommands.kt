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
import com.vitorpamplona.amethyst.cli.engine.MarmotEngine
import com.vitorpamplona.amethyst.cli.findWnCommand
import com.vitorpamplona.amethyst.cli.output.Output
import com.vitorpamplona.amethyst.cli.resolveAccount
import java.io.File

class KeysCommand : CliktCommand(name = "keys") {
    override fun commandHelp(context: Context) = "MLS KeyPackage management"

    init {
        subcommands(
            KeysListCommand(),
            KeysPublishCommand(),
            KeysDeleteCommand(),
            KeysDeleteAllCommand(),
            KeysCheckCommand(),
        )
    }

    override fun run() = Unit
}

class KeysListCommand : CliktCommand(name = "list") {
    override fun commandHelp(context: Context) = "List published KeyPackages"

    override fun run() {
        val account = resolveAccount()
        if (account == null) {
            Output.error("No account. Use 'create-identity' or 'login' first.")
            return
        }

        val store = accountStore()
        val kpDir = File(File(File(findWnCommand().dataDir), "accounts/${account.pubKeyHex}"), "keypackages")
        if (!kpDir.exists() || kpDir.listFiles().isNullOrEmpty()) {
            Output.success("No KeyPackages published. Use 'keys publish' to create one.")
            return
        }

        val entries =
            kpDir.listFiles()?.map { file ->
                listOf(file.nameWithoutExtension, file.length().toString() + " bytes")
            } ?: emptyList()

        Output.table(
            headers = listOf("slot", "size"),
            rows = entries,
        )
    }
}

class KeysPublishCommand : CliktCommand(name = "publish") {
    override fun commandHelp(context: Context) = "Generate and publish a new KeyPackage to relays"

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
            val kpInfo = engine.generateKeyPackage()
            val event = engine.buildKeyPackageEvent(kpInfo)
            engine.publishEvent(event)

            val kpDir =
                File(File(findWnCommand().dataDir), "accounts/${account.pubKeyHex}/keypackages").also {
                    it.mkdirs()
                }
            File(kpDir, "0.kp").writeText(kpInfo.base64)

            Output.keyValue(
                listOf(
                    "event_id" to event.id,
                    "ref" to kpInfo.ref,
                    "ciphersuite" to kpInfo.ciphersuite,
                    "published_to" to account.relays.joinToString(", "),
                ),
            )
        } finally {
            engine.shutdown()
        }
    }
}

class KeysDeleteCommand : CliktCommand(name = "delete") {
    override fun commandHelp(context: Context) = "Delete a KeyPackage by slot"

    override fun run() {
        Output.error("keys delete: not yet implemented")
    }
}

class KeysDeleteAllCommand : CliktCommand(name = "delete-all") {
    override fun commandHelp(context: Context) = "Delete all KeyPackages"

    override fun run() {
        Output.error("keys delete-all: not yet implemented")
    }
}

class KeysCheckCommand : CliktCommand(name = "check") {
    override fun commandHelp(context: Context) = "Check KeyPackage validity on relays"

    override fun run() {
        Output.error("keys check: not yet implemented")
    }
}
