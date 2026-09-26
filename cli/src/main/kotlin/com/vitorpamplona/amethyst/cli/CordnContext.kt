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

import com.vitorpamplona.amethyst.commons.cordn.CoordinatorConfig
import com.vitorpamplona.amethyst.commons.cordn.CordnBlobCipher
import com.vitorpamplona.amethyst.commons.cordn.CordnCoordinatorRegistry
import com.vitorpamplona.amethyst.commons.cordn.CordnLinks
import com.vitorpamplona.amethyst.commons.cordn.CordnSession
import com.vitorpamplona.amethyst.commons.cordn.CordnStorageLayout
import com.vitorpamplona.amethyst.commons.cordn.FileBackedCordnScopeFactory
import com.vitorpamplona.amethyst.commons.cordn.FileCordnCoordinatorStore
import com.vitorpamplona.amethyst.commons.cordn.KeyedCordnBlobCipher
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.io.File

/**
 * cordn wiring for the CLI, split out of [Context] the way [CashuContext] is
 * and built lazily, so a run that never says `cordn` opens no transport and
 * generates no blob key.
 *
 * Everything below this class is shared: the registry, the manager, the
 * stores, and `CordnLinks` are the same objects the Android app runs. What is
 * here is the three things a CLI has to answer differently.
 *
 * **Where the blob key comes from.** Android hides it in the KeyStore; a CLI
 * has nowhere comparable, so it is a 0600 file beside the blobs. See
 * [DataDir.cordnBlobKeyFile] for what that does and does not protect.
 *
 * **There is no sync loop.** Each invocation is a process: it opens a
 * session, does one thing, and exits. `CordnSyncLoop` is for a front end that
 * stays running, and a CLI that started one would hang. So a `fetch` verb
 * drains explicitly and the cursor on disk is what carries continuity between
 * runs — which is exactly the primitive `spec/02.md` §7 says a cursor is.
 *
 * **A coordinator has to be named.** The app has a screen listing them; here
 * `--coordinator` selects one, and is optional only while exactly one is
 * remembered. Guessing would be worse than asking: a `gid` means nothing
 * outside the coordinator that issued it (`spec/00.md` §4), so the wrong
 * coordinator is not a slower answer, it is an answer about a different
 * group.
 */
class CordnContext(
    private val ctx: Context,
) {
    private val accountPubKey: HexKey get() = ctx.identity.pubKeyHex

    private val cipher: CordnBlobCipher by lazy { KeyedCordnBlobCipher(blobKey()) }

    private val registry: CordnCoordinatorRegistry by lazy {
        CordnCoordinatorRegistry(
            accountPubKey = accountPubKey,
            scopes =
                FileBackedCordnScopeFactory(
                    root = ctx.dataDir.root,
                    cipher = cipher,
                    links = CordnLinks.over(ctx.signer, ctx.client),
                ),
        )
    }

    private val coordinatorStore by lazy {
        FileCordnCoordinatorStore(
            CordnStorageLayout.accountDirectoryFor(ctx.dataDir.root, accountPubKey),
            cipher,
        )
    }

    /** Every coordinator this account has been told about, in the order added. */
    suspend fun coordinators(): List<CoordinatorConfig> = coordinatorStore.load()

    /**
     * Adds [config], replacing any entry for the same pubkey.
     *
     * Keyed by pubkey because that IS the coordinator's identity (§8.5) — a
     * second entry for the same key with different relays is a corrected
     * address, not a second coordinator.
     */
    suspend fun remember(config: CoordinatorConfig) {
        val kept = coordinators().filterNot { it.pubKey == config.pubKey }
        coordinatorStore.save(kept + config)
    }

    suspend fun forget(coordinatorPubKey: HexKey): Boolean {
        val before = coordinators()
        val after = before.filterNot { it.pubKey == coordinatorPubKey }
        if (after.size == before.size) return false
        coordinatorStore.save(after)
        registry.forget(coordinatorPubKey)
        return true
    }

    /** The live session for [config], restored from disk. */
    suspend fun session(config: CoordinatorConfig): CordnSession =
        registry.session(config).also {
            it.manager.restore()
            it.keyPackages.restore()
        }

    /** Where the cordn tree lives, for the migration verbs. */
    val migrationRoot: File get() = ctx.dataDir.root

    /** The at-rest cipher, so a migration can read and write the same blobs. */
    val blobCipher: CordnBlobCipher get() = cipher

    /** Replaces the remembered coordinator list, after adopting a migration. */
    suspend fun replaceCoordinators(configs: List<CoordinatorConfig>) = coordinatorStore.save(configs)

    /**
     * Picks the coordinator a command should act on.
     *
     * `--coordinator` wins. With no flag and exactly one remembered, that one
     * is used; with none or several, the caller is asked rather than guessed
     * at. `--relay` is accepted alongside a pubkey so a first call can name a
     * coordinator that is not remembered yet — that is how `coordinator add`
     * and a one-shot `--coordinator … --relay …` are the same code path.
     */
    suspend fun resolve(args: Args): CoordinatorConfig {
        val requested = args.flag("coordinator")
        val relays = relayFlag(args)
        val known = coordinators()

        if (requested == null) {
            if (relays.isNotEmpty()) throw CordnArgException("--relay needs --coordinator to belong to")
            return when (known.size) {
                0 -> throw CordnArgException("no coordinator known yet: amy cordn coordinator add --coordinator PK --relay URL")
                1 -> known.single()
                else ->
                    throw CordnArgException(
                        "several coordinators known, name one with --coordinator: " +
                            known.joinToString(", ") { it.pubKey.take(8) },
                    )
            }
        }

        val remembered = known.firstOrNull { it.pubKey == requested }
        if (relays.isEmpty()) {
            return remembered
                ?: throw CordnArgException("coordinator $requested is not known here; add --relay to reach it")
        }
        // A coordinator named with relays is reachable whether or not it was
        // remembered, and the relays given now are the current ones.
        return (remembered ?: CoordinatorConfig(pubKey = requested, relays = relays)).copy(relays = relays)
    }

    /** `--relay URL[,URL…]`; the flag map collapses repeats, so they arrive comma-separated. */
    fun relayFlag(args: Args): List<NormalizedRelayUrl> =
        args
            .flag("relay")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
            .map { url ->
                RelayUrlNormalizer.normalizeOrNull(url) ?: throw CordnArgException("not a relay URL: $url")
            }

    suspend fun close() = registry.close()

    /**
     * Reads the blob key, generating one on first use.
     *
     * Not derived from the account key on purpose. A derivation would mean a
     * bunker-backed identity — which has no local private key at all — could
     * not use cordn, and it would tie every blob to a key the user may rotate.
     */
    private fun blobKey(): ByteArray {
        val file = ctx.dataDir.cordnBlobKeyFile
        if (file.exists()) {
            val bytes = file.readBytes()
            require(bytes.size == KeyedCordnBlobCipher.KEY_LENGTH) {
                "$file is ${bytes.size} bytes, expected ${KeyedCordnBlobCipher.KEY_LENGTH}"
            }
            return bytes
        }
        return KeyedCordnBlobCipher.newKey().also { SecureFileIO.writeBytesAtomic(file, it) }
    }
}

/** A bad-args failure a command turns into `Output.error("bad_args", …)`. */
class CordnArgException(
    message: String,
) : IllegalArgumentException(message)
