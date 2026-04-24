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

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import java.io.File

/**
 * Persisted identity.
 *
 * [privKeyHex] may be null for read-only accounts imported from an `npub`,
 * `nprofile` or NIP-05 — in that case [nsec] is also null and any CLI verb
 * that needs to sign will fail with a clear error. `keyPair()` materialises
 * a [KeyPair] with only `pubKey` set when no private key is available.
 */
data class Identity(
    val privKeyHex: String?,
    val pubKeyHex: String,
    val nsec: String?,
    val npub: String,
) {
    @get:com.fasterxml.jackson.annotation.JsonIgnore
    val hasPrivateKey: Boolean get() = privKeyHex != null

    fun keyPair(): KeyPair =
        if (privKeyHex != null) {
            KeyPair(privKey = privKeyHex.hexToByteArray(), pubKey = pubKeyHex.hexToByteArray())
        } else {
            KeyPair(pubKey = pubKeyHex.hexToByteArray())
        }

    companion object {
        fun create(): Identity = fromPrivateKey(KeyPair().privKey!!)

        fun fromNsec(nsec: String): Identity = fromPrivateKey(nsec.bechToBytes())

        fun fromPrivateKey(priv: ByteArray): Identity {
            val pub = KeyPair(privKey = priv).pubKey
            return Identity(
                privKeyHex = priv.toHexKey(),
                pubKeyHex = pub.toHexKey(),
                nsec = priv.toNsec(),
                npub = pub.toNpub(),
            )
        }

        /** Read-only identity (no private key). */
        fun fromPublicKeyHex(pubHex: String): Identity =
            Identity(
                privKeyHex = null,
                pubKeyHex = pubHex.lowercase(),
                nsec = null,
                npub = pubHex.hexToByteArray().toNpub(),
            )
    }
}

/**
 * On-disk relay configuration, bucketed by purpose (mirrors `wn relays add --type`).
 *
 *  - `nip65`: advertised read/write (kind:10002)
 *  - `inbox`: DM inbox / gift-wrap delivery (kind:10050)
 *  - `keyPackage`: where this account's KeyPackages (kind:30443) live
 */
data class RelayConfig(
    val nip65: MutableList<String> = mutableListOf(),
    val inbox: MutableList<String> = mutableListOf(),
    val keyPackage: MutableList<String> = mutableListOf(),
) {
    fun all(): Set<String> = (nip65 + inbox + keyPackage).toSet()

    fun add(
        type: String,
        url: String,
    ): Boolean {
        val list =
            when (type) {
                "nip65" -> nip65
                "inbox" -> inbox
                "key_package", "keyPackage" -> keyPackage
                else -> throw IllegalArgumentException("unknown relay type: $type")
            }
        if (list.contains(url)) return false
        list.add(url)
        return true
    }

    fun normalized(kind: String): Set<NormalizedRelayUrl> {
        val src =
            when (kind) {
                "nip65" -> nip65
                "inbox" -> inbox
                "key_package" -> keyPackage
                "all" -> all().toList()
                else -> throw IllegalArgumentException("unknown relay selector: $kind")
            }
        return src.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
    }
}

/** Opaque per-run state (subscription cursors, etc). Stored alongside identity. */
data class RunState(
    var giftWrapSince: Long? = null,
    val groupSince: MutableMap<String, Long> = mutableMapOf(),
)

/**
 * Root of the on-disk layout. Any absolute path chosen by `--data-dir` (or
 * `$AMETHYST_CLI_DATA`) — defaults to `./amy`.
 */
class DataDir(
    val root: File,
) {
    val identityFile = File(root, "identity.json")
    val relaysFile = File(root, "relays.json")
    val stateFile = File(root, "state.json")
    val marmotDir = File(root, "marmot")
    val groupsDir = File(marmotDir, "groups")
    val keyPackageBundleFile = File(marmotDir, "keypackages.bundle")

    init {
        SecureFileIO.secureMkdirs(root)
        SecureFileIO.secureMkdirs(groupsDir)
        // Tighten perms on any data already on disk from an older, unhardened CLI.
        SecureFileIO.tighten(identityFile)
        SecureFileIO.tighten(relaysFile)
        SecureFileIO.tighten(stateFile)
        SecureFileIO.tighten(marmotDir)
        SecureFileIO.tighten(keyPackageBundleFile)
    }

    fun loadIdentityOrNull(): Identity? = if (identityFile.exists()) Json.mapper.readValue<Identity>(identityFile.readText()) else null

    fun saveIdentity(id: Identity) {
        SecureFileIO.writeTextAtomic(identityFile, Json.mapper.writeValueAsString(id))
    }

    fun loadRelays(): RelayConfig = if (relaysFile.exists()) Json.mapper.readValue(relaysFile.readText()) else RelayConfig()

    fun saveRelays(r: RelayConfig) {
        SecureFileIO.writeTextAtomic(relaysFile, Json.mapper.writeValueAsString(r))
    }

    fun loadRunState(): RunState = if (stateFile.exists()) Json.mapper.readValue(stateFile.readText()) else RunState()

    fun saveRunState(s: RunState) {
        SecureFileIO.writeTextAtomic(stateFile, Json.mapper.writeValueAsString(s))
    }

    companion object {
        fun resolve(flag: String?): DataDir {
            val envPath = System.getenv("AMETHYST_CLI_DATA")
            val path = flag ?: envPath ?: "./amy"
            return DataDir(File(path).absoluteFile)
        }
    }
}
