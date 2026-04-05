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
package com.vitorpamplona.amethyst.cli.engine

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import java.io.File

data class AccountInfo(
    val pubKeyHex: HexKey,
    val privKeyHex: HexKey,
    val npub: String,
    val nsec: String,
    val relays: List<String>,
)

class AccountStore(
    private val dataDir: File,
) {
    private val accountsDir = File(dataDir, "accounts").also { it.mkdirs() }
    private val configFile = File(dataDir, "config.json")

    fun createIdentity(): AccountInfo {
        val keyPair = KeyPair()
        val privKey = keyPair.privKey!!
        val pubKey = keyPair.pubKey
        val pubKeyHex = pubKey.toHexKey()
        val privKeyHex = privKey.toHexKey()

        val accountDir = File(accountsDir, pubKeyHex).also { it.mkdirs() }
        File(accountDir, "nsec").writeText(privKeyHex)

        val defaultRelays = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.nostr.band")
        File(accountDir, "relays.txt").writeText(defaultRelays.joinToString("\n"))

        setDefaultAccount(pubKeyHex)

        return AccountInfo(
            pubKeyHex = pubKeyHex,
            privKeyHex = privKeyHex,
            npub = pubKey.toNpub(),
            nsec = privKey.toNsec(),
            relays = defaultRelays,
        )
    }

    fun login(
        nsec: String,
        relays: List<String>,
    ): AccountInfo {
        val privKey =
            if (nsec.startsWith("nsec")) {
                val (_, data, _) = Bech32.decodeBytes(nsec)
                data
            } else {
                nsec.hexToByteArray()
            }

        val keyPair = KeyPair(privKey = privKey)
        val pubKey = keyPair.pubKey
        val pubKeyHex = pubKey.toHexKey()
        val privKeyHex = privKey.toHexKey()

        val accountDir = File(accountsDir, pubKeyHex).also { it.mkdirs() }
        File(accountDir, "nsec").writeText(privKeyHex)

        val effectiveRelays =
            relays.ifEmpty {
                listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.nostr.band")
            }
        File(accountDir, "relays.txt").writeText(effectiveRelays.joinToString("\n"))

        setDefaultAccount(pubKeyHex)

        return AccountInfo(
            pubKeyHex = pubKeyHex,
            privKeyHex = privKeyHex,
            npub = pubKey.toNpub(),
            nsec = privKey.toNsec(),
            relays = effectiveRelays,
        )
    }

    fun logout(pubkey: String): Boolean {
        val hex = resolvePubkey(pubkey) ?: return false
        val accountDir = File(accountsDir, hex)
        if (!accountDir.exists()) return false
        accountDir.deleteRecursively()

        if (getDefaultAccount() == hex) {
            val remaining = listAccounts()
            if (remaining.isNotEmpty()) {
                setDefaultAccount(remaining.first().pubKeyHex)
            } else {
                configFile.delete()
            }
        }
        return true
    }

    fun listAccounts(): List<AccountInfo> = accountsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { loadAccount(it.name) } ?: emptyList()

    fun loadAccount(pubKeyHex: HexKey): AccountInfo? {
        val accountDir = File(accountsDir, pubKeyHex)
        if (!accountDir.exists()) return null

        val privKeyHex = File(accountDir, "nsec").takeIf { it.exists() }?.readText()?.trim() ?: return null
        val privKey = privKeyHex.hexToByteArray()
        val pubKey = pubKeyHex.hexToByteArray()

        val relays =
            File(accountDir, "relays.txt")
                .takeIf { it.exists() }
                ?.readText()
                ?.lines()
                ?.filter { it.isNotBlank() }
                ?: emptyList()

        return AccountInfo(
            pubKeyHex = pubKeyHex,
            privKeyHex = privKeyHex,
            npub = pubKey.toNpub(),
            nsec = privKey.toNsec(),
            relays = relays,
        )
    }

    fun getDefaultAccount(): HexKey? = if (configFile.exists()) configFile.readText().trim() else null

    fun setDefaultAccount(pubKeyHex: HexKey) {
        configFile.writeText(pubKeyHex)
    }

    fun resolveAccount(accountFlag: String?): AccountInfo? {
        if (accountFlag != null) {
            val hex = resolvePubkey(accountFlag) ?: return null
            return loadAccount(hex)
        }

        val envAccount = System.getenv("WN_ACCOUNT")
        if (envAccount != null) {
            val hex = resolvePubkey(envAccount) ?: return null
            return loadAccount(hex)
        }

        val defaultHex = getDefaultAccount() ?: return null
        return loadAccount(defaultHex)
    }

    fun signerFor(account: AccountInfo): NostrSignerInternal {
        val keyPair = KeyPair(privKey = account.privKeyHex.hexToByteArray())
        return NostrSignerInternal(keyPair)
    }

    fun getRelays(pubKeyHex: HexKey): List<String> {
        val accountDir = File(accountsDir, pubKeyHex)
        val relayFile = File(accountDir, "relays.txt")
        if (!relayFile.exists()) return emptyList()
        return relayFile.readText().lines().filter { it.isNotBlank() }
    }

    fun setRelays(
        pubKeyHex: HexKey,
        relays: List<String>,
    ) {
        val accountDir = File(accountsDir, pubKeyHex)
        accountDir.mkdirs()
        File(accountDir, "relays.txt").writeText(relays.joinToString("\n"))
    }

    private fun resolvePubkey(input: String): HexKey? =
        if (input.startsWith("npub")) {
            try {
                val (_, data, _) = Bech32.decodeBytes(input)
                data.toHexKey()
            } catch (e: Exception) {
                null
            }
        } else {
            input
        }
}
