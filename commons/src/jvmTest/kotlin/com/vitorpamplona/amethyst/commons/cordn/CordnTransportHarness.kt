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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.contextvm.cep04Encryption.CvmGiftWrap
import com.vitorpamplona.quartz.contextvm.fixture.CvmFixtureServer
import com.vitorpamplona.quartz.contextvm.fixture.InMemoryRelayPool
import com.vitorpamplona.quartz.contextvm.mcp.CvmMcpClient
import com.vitorpamplona.quartz.contextvm.transport.CvmTransport
import com.vitorpamplona.quartz.contextvm.transport.DualSigner
import com.vitorpamplona.quartz.cordn.fixture.CordnFixtureCoordinator
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorClient
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield

/**
 * Accounts wired to a coordinator through the **real** ContextVM stack.
 *
 * `CvmTransport` → `InMemoryRelayPool` → `CvmFixtureServer` →
 * `CordnFixtureCoordinator`. The only stand-ins are the relay (in memory) and
 * the coordinator (ours, because the reference one is unlicensed — see
 * `quartz/plans/2026-09-17-cordn-interop.md` §7). Everything between an account
 * and the relay is the shipped code.
 *
 * **Encryption is left at its default**, which is `EncryptionMode.REQUIRED`
 * (§8.6). Passing `DISABLED` for convenience would test a transport we do not
 * ship, and it is the kind of convenience that survives into the one test that
 * was supposed to catch a downgrade.
 */
abstract class CordnTransportHarness {
    protected val relays = InMemoryRelayPool()
    protected val serverSigner = NostrSignerInternal(KeyPair())
    protected val coordinator = CordnFixtureCoordinator()

    /**
     * A second, unrelated coordinator.
     *
     * Present because several rules are only visible with two — a `gid` is
     * unique within one coordinator (`spec/00.md` §4), so "these are two
     * different groups" cannot be stated at all against a single fixture.
     */
    protected val secondServerSigner = NostrSignerInternal(KeyPair())
    protected val secondCoordinator = CordnFixtureCoordinator()

    protected val secondCoordinatorPubKey: HexKey get() = secondServerSigner.pubKey

    protected val config =
        CoordinatorConfig(
            pubKey = serverSigner.pubKey,
            relays = listOf(RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!),
        )

    /** One account: a stable identity, a per-session ephemeral one, and its state. */
    protected inner class Account {
        val stable = NostrSignerInternal(KeyPair())
        val ephemeral = NostrSignerInternal(KeyPair())
        val pubKey: HexKey get() = stable.pubKey

        /**
         * The identity split of `spec/00.md` §8, wired as production does it.
         * Which key signs which call is fixed by `CoordinatorMethod`, so a test
         * cannot accidentally widen it — only this pairing can be wrong.
         */
        val coordinatorClient = clientFor(serverSigner.pubKey)

        /** This account's client for whichever coordinator [serverPubKey] names. */
        fun clientFor(serverPubKey: HexKey) =
            CoordinatorClient(
                CvmMcpClient(
                    CvmTransport(
                        relays = relays,
                        signers = DualSigner(stable, ephemeral),
                        serverPubKey = serverPubKey,
                        crypto = CvmGiftWrap(),
                    ),
                ),
            )

        val groupStore = InMemoryCordnGroupStore()
        val keyPackageStore = InMemoryCordnKeyPackageStore()

        val keyPackages = CordnKeyPackages(stable.pubKey, coordinatorClient, keyPackageStore)

        val manager =
            CordnGroupManager(
                accountPubKey = stable.pubKey,
                config = config,
                coordinator = coordinatorClient,
                store = groupStore,
                clock = { 1_757_000_000L },
            )
    }

    private fun serve() =
        listOf(serverSigner to coordinator, secondServerSigner to secondCoordinator).map { (signer, fixture) ->
            CvmFixtureServer(
                relays = relays,
                signer = signer,
                crypto = CvmGiftWrap(),
                // CEP-16: how the coordinator learns who is calling, and what every
                // identity-split assertion rests on.
                injectClientPubkey = true,
                handler = fixture::handle,
            ).also { it.start() }
        }

    /**
     * Runs [block] while pumping the server.
     *
     * Explicit because a relay callback cannot suspend. The spin guard turns a
     * call nobody answers into a named failure instead of a test that looks
     * merely slow.
     */
    protected suspend fun <T> driving(block: suspend () -> T): T =
        coroutineScope {
            val servers = serve()
            val work = async { block() }
            var spins = 0
            while (!work.isCompleted) {
                yield()
                servers.forEach { it.pump() }
                yield()
                check(++spins < 100_000) { "the fixture never answered — something is not replying" }
            }
            work.await()
        }
}
