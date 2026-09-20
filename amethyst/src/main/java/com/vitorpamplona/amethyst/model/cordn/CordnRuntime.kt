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
package com.vitorpamplona.amethyst.model.cordn

import com.vitorpamplona.amethyst.commons.cordn.CoordinatorConfig
import com.vitorpamplona.amethyst.commons.cordn.CordnBlobCipher
import com.vitorpamplona.amethyst.commons.cordn.CordnCoordinatorLink
import com.vitorpamplona.amethyst.commons.cordn.CordnCoordinatorLinkFactory
import com.vitorpamplona.amethyst.commons.cordn.CordnCoordinatorRegistry
import com.vitorpamplona.amethyst.commons.cordn.CordnGroupManager
import com.vitorpamplona.amethyst.commons.cordn.CordnSession
import com.vitorpamplona.amethyst.commons.cordn.CordnSyncLoop
import com.vitorpamplona.amethyst.commons.cordn.FileBackedCordnScopeFactory
import com.vitorpamplona.amethyst.commons.cordn.KeyStoreCordnBlobCipher
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupList
import com.vitorpamplona.quartz.contextvm.cep04Encryption.CvmGiftWrap
import com.vitorpamplona.quartz.contextvm.mcp.CvmMcpClient
import com.vitorpamplona.quartz.contextvm.transport.CvmTransport
import com.vitorpamplona.quartz.contextvm.transport.DualSigner
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorClient
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * One account's cordn feature, assembled.
 *
 * This is the wiring Stage A left open on purpose: every piece below it is
 * shared KMP code that knows nothing about Amethyst, and this class supplies
 * the two things only the running app has — a relay pool and the account's
 * signer.
 *
 * ## The ephemeral signer is created here, once, and never persisted
 *
 * `spec/00.md` §8 splits the identity a coordinator sees: the account key
 * signs what must be attributable (publishing a KeyPackage, posting to a
 * group), and a throwaway key signs everything else, so the coordinator cannot
 * link a session's reads to an account. Which key signs which call is fixed by
 * `CoordinatorMethod` and not a choice made here; what IS decided here is that
 * the throwaway key lives as long as this runtime and no longer. Persisting it
 * would quietly undo the split — a "session" key reused across launches is
 * just a second account key with worse ergonomics.
 */
class CordnRuntime(
    private val accountSigner: NostrSigner,
    private val client: INostrClient,
    private val filesDir: File,
    private val scope: CoroutineScope,
    private val cipher: CordnBlobCipher = KeyStoreCordnBlobCipher(),
) {
    /** See the class KDoc: per-runtime, never written to disk. */
    private val ephemeralSigner = NostrSignerInternal(KeyPair())

    private val links =
        CordnCoordinatorLinkFactory { _, config ->
            val transport =
                CvmTransport(
                    relays = NostrClientCvmRelayPool(client, config.relays.toSet()),
                    signers = DualSigner(accountSigner, ephemeralSigner),
                    serverPubKey = config.pubKey,
                    // Left at its default, which is REQUIRED (§8.6). Passing
                    // anything else here is the one line that would silently
                    // downgrade every coordinator call to plaintext.
                    crypto = CvmGiftWrap(),
                )

            object : CordnCoordinatorLink {
                override val coordinator = CoordinatorClient(CvmMcpClient(transport))

                override suspend fun close() {
                    // Nothing to release. CvmTransport opens a subscription
                    // per request and closes it in the same call — kind 25910
                    // is ephemeral, so there is no long-lived stream to tear
                    // down and no connection of its own. The relays it used
                    // belong to the shared client, which outlives this link.
                }
            }
        }

    private val registry =
        CordnCoordinatorRegistry(
            accountPubKey = accountSigner.pubKey,
            scopes = FileBackedCordnScopeFactory(filesDir, cipher, links),
        )

    /** Every cordn room this account is in, for the inbox and the screens. */
    val groups = CordnGroupList()

    private val loops = mutableMapOf<HexKey, CordnSyncLoop>()
    private val lock = Mutex()

    val coordinators = registry.coordinators

    /** The session for [config], opening and starting it if it is new. */
    suspend fun session(config: CoordinatorConfig): CordnSession {
        val session = registry.session(config)
        lock.withLock {
            if (loops[config.pubKey] == null) {
                val loop =
                    CordnSyncLoop(
                        source = session.manager,
                        onDelivery = { file(config.pubKey, it) },
                    )
                loops[config.pubKey] = loop
                loop.start(scope)
            }
        }
        // Whatever the store already held, so a relaunch shows its rooms
        // before the first message of the session arrives.
        session.manager.gids.value
            .forEach { groups.getOrCreate(config.pubKey, it) }
        return session
    }

    fun sessionOrNull(coordinatorPubKey: HexKey): CordnSession? = registry.sessionOrNull(coordinatorPubKey)

    /** Opens every configured coordinator and starts syncing. Call at login. */
    suspend fun start(configs: List<CoordinatorConfig>) {
        configs.forEach {
            try {
                session(it)
            } catch (e: Exception) {
                // One unreachable coordinator must not stop the others: they
                // are independent authorities and a user with two of them has
                // two unrelated sets of groups.
                Log.w(TAG, "could not open coordinator ${it.pubKey.take(8)}…: ${e.message}", e)
            }
        }
    }

    /** Stops every loop and closes every transport. Call at logout. */
    suspend fun stop() {
        lock.withLock {
            loops.values.forEach { it.stop() }
            loops.clear()
        }
        registry.close()
        groups.clear()
    }

    /** Drops one coordinator, leaving its stored groups on disk. */
    suspend fun forget(coordinatorPubKey: HexKey) {
        lock.withLock {
            loops.remove(coordinatorPubKey)?.stop()
        }
        registry.forget(coordinatorPubKey)
    }

    private fun file(
        coordinatorPubKey: HexKey,
        delivery: CordnGroupManager.Delivery,
    ) {
        when (delivery) {
            is CordnGroupManager.Delivery.Message ->
                groups.add(
                    coordinatorPubKey,
                    delivery.gid,
                    CordnDeliveredMessage(delivery.received.envelope, delivery.cursor),
                )

            // The rest still get a room. A group whose history we cannot
            // read, or that only moved an epoch, is a group we are in —
            // showing nothing would read as a bug rather than as the
            // forward secrecy it actually is.
            is CordnGroupManager.Delivery.Undecryptable,
            is CordnGroupManager.Delivery.EpochAdvanced,
            is CordnGroupManager.Delivery.Echo,
            -> groups.getOrCreate(coordinatorPubKey, delivery.gid)
        }
    }

    companion object {
        private const val TAG = "CordnRuntime"
    }
}
