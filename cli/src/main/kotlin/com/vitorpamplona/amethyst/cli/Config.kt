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
import com.vitorpamplona.amethyst.cli.secrets.IdentityFile
import com.vitorpamplona.amethyst.cli.secrets.IdentitySecret
import com.vitorpamplona.amethyst.cli.secrets.SecretStore
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.utils.Log
import java.io.File

private const val TAG = "Config"

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

        /**
         * Rebuild an in-memory identity after a load. Accepts the public
         * parts that live on disk and a private key resolved from the
         * backend (or null for read-only accounts). Re-derives `nsec` so
         * callers that print it (e.g. `amy init`) keep working.
         */
        fun fromDisk(
            pubKeyHex: String,
            npub: String,
            privKeyHex: String?,
        ): Identity =
            Identity(
                privKeyHex = privKeyHex,
                pubKeyHex = pubKeyHex,
                nsec = privKeyHex?.hexToByteArray()?.toNsec(),
                npub = npub,
            )
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
 *
 * [secrets] is the [SecretStore] that mediates private-key persistence.
 * Owning it here keeps the call sites that already thread [DataDir] from
 * having to learn about a second parameter.
 */
class DataDir(
    val root: File,
    val secrets: SecretStore,
) {
    val identityFile = File(root, "identity.json")
    val stateFile = File(root, "state.json")
    val marmotDir = File(root, "marmot")
    val groupsDir = File(marmotDir, "groups")
    val keyPackageBundleFile = File(marmotDir, "keypackages.bundle")

    /** Root of the file-backed Nostr event store (`FsEventStore`). */
    val eventsDir = File(root, "events-store")

    init {
        SecureFileIO.secureMkdirs(root)
        SecureFileIO.secureMkdirs(groupsDir)
        // Tighten perms on any data already on disk from an older, unhardened CLI.
        SecureFileIO.tighten(identityFile)
        SecureFileIO.tighten(stateFile)
        SecureFileIO.tighten(marmotDir)
        SecureFileIO.tighten(keyPackageBundleFile)
    }

    /**
     * Read the on-disk metadata without touching any backend. Safe to use
     * for "does an identity exist?" / "what's the npub?" checks that must
     * not pop a keychain prompt or ask for a passphrase.
     */
    fun loadIdentityFileOrNull(): IdentityFile? = if (identityFile.exists()) Output.mapper.readValue(identityFile.readText()) else null

    fun identityExists(): Boolean = identityFile.exists()

    /**
     * Load the identity from disk. Resolves the private key through the
     * configured [SecretStore] (prompting for a passphrase if needed) and
     * auto-migrates pre-secret-store files that still carry `privKeyHex`/
     * `nsec` at the top level — the migrated content is written back via
     * [saveIdentity] on the next explicit save, not eagerly on load.
     */
    fun loadIdentityOrNull(): Identity? {
        val file = loadIdentityFileOrNull() ?: return null
        val privHex: String? =
            when {
                file.secret != null -> secrets.resolve(file.secret)
                file.privKeyHex != null -> file.privKeyHex
                file.nsec != null -> file.nsec.bechToBytes().toHexKey()
                else -> null // read-only
            }
        return Identity.fromDisk(pubKeyHex = file.pubKeyHex, npub = file.npub, privKeyHex = privHex)
    }

    /**
     * Persist [id]. When [id] carries a private key, [SecretStore.store] is
     * called to push it to the selected backend (keychain / ncryptsec /
     * plaintext); only the resulting [IdentitySecret] reference is written
     * to disk. Read-only identities persist `secret: null`.
     */
    fun saveIdentity(id: Identity) {
        val secret: IdentitySecret? = id.privKeyHex?.let { secrets.store(id.pubKeyHex, it) }
        val file = IdentityFile(pubKeyHex = id.pubKeyHex, npub = id.npub, secret = secret)
        SecureFileIO.writeTextAtomic(identityFile, Output.mapper.writeValueAsString(file))
    }

    /** Remove the identity file and any backend-held secret. */
    fun deleteIdentity() {
        if (identityFile.exists()) {
            runCatching {
                val file = Output.mapper.readValue<IdentityFile>(identityFile.readText())
                file.secret?.let { secrets.delete(it) }
            }
            if (!identityFile.delete() && identityFile.exists()) {
                Log.w(TAG) { "Failed to delete identity file ${identityFile.absolutePath}" }
            }
        }
    }

    fun loadRunState(): RunState = if (stateFile.exists()) Output.mapper.readValue(stateFile.readText()) else RunState()

    fun saveRunState(s: RunState) {
        SecureFileIO.writeTextAtomic(stateFile, Output.mapper.writeValueAsString(s))
    }

    companion object {
        fun resolve(
            flag: String?,
            secrets: SecretStore,
        ): DataDir {
            val envPath = System.getenv("AMETHYST_CLI_DATA")
            val path = flag ?: envPath ?: "./amy"
            return DataDir(File(path).absoluteFile, secrets)
        }
    }
}
