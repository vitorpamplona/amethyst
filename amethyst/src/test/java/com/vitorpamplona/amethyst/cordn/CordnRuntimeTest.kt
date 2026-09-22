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
package com.vitorpamplona.amethyst.cordn

import com.vitorpamplona.amethyst.commons.cordn.CoordinatorConfig
import com.vitorpamplona.amethyst.commons.cordn.CordnBlobCipher
import com.vitorpamplona.amethyst.commons.cordn.CordnCoordinatorLink
import com.vitorpamplona.amethyst.commons.cordn.CordnCoordinatorLinkFactory
import com.vitorpamplona.amethyst.commons.cordn.CordnStorageLayout
import com.vitorpamplona.amethyst.model.cordn.CordnRuntime
import com.vitorpamplona.quartz.cordn.spec00Coordinator.AvailableKeyPackage
import com.vitorpamplona.quartz.cordn.spec00Coordinator.ConsumedJoinRequestRef
import com.vitorpamplona.quartz.cordn.spec00Coordinator.ConsumedWelcomeRef
import com.vitorpamplona.quartz.cordn.spec00Coordinator.GroupMessage
import com.vitorpamplona.quartz.cordn.spec00Coordinator.ICoordinator
import com.vitorpamplona.quartz.cordn.spec00Coordinator.JoinRequest
import com.vitorpamplona.quartz.cordn.spec00Coordinator.PendingWelcome
import com.vitorpamplona.quartz.cordn.spec00Coordinator.PostedMessage
import com.vitorpamplona.quartz.cordn.spec00Coordinator.PublishedKeyPackage
import com.vitorpamplona.quartz.cordn.spec00Coordinator.TakenKeyPackage
import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules `CordnRuntime` holds, made executable.
 *
 * It is the assembly point for everything cordn: the KeyPackage pool policy,
 * purge, restore, the invitation fan-out. Every one of those was a sentence in
 * a commit message and none of them was a test, which is the combination that
 * makes a rule quietly stop being true.
 *
 * Two seams make this possible without a device or a relay. `cipher` was
 * always one, because the Android KeyStore cannot run in a JVM unit test.
 * `links` is the second: production opens a real ContextVM transport per
 * coordinator, so without it nothing in this class could be reached at all.
 * Both default to the real thing.
 */
class CordnRuntimeTest {
    /** Reversible and unmistakably not the input — the same trick the store tests use. */
    private class XorCipher : CordnBlobCipher {
        override fun encrypt(bytes: ByteArray) = ByteArray(bytes.size) { (bytes[it].toInt() xor 0x5A).toByte() }

        override fun decrypt(bytes: ByteArray) = encrypt(bytes)
    }

    private val signer = NostrSignerInternal(KeyPair())
    private val account: HexKey get() = signer.pubKey

    private val coordinatorA = StubCoordinator()
    private val coordinatorB = StubCoordinator()
    private val keyA = "a".repeat(64)
    private val keyB = "b".repeat(64)

    private val root =
        File.createTempFile("cordn-runtime", "").also {
            it.delete()
            it.mkdirs()
        }

    private val scopes = mutableListOf<CoroutineScope>()

    /**
     * Anything a runtime coroutine threw and nobody caught.
     *
     * A bare `CoroutineScope(Dispatchers.Default + Job())` has no handler, so
     * an exception escaping a `launch` goes to the THREAD's uncaught handler —
     * out of this test entirely and into the JVM the whole module shares. The
     * next test to call `runTest` then fails with
     * `UncaughtExceptionsBeforeTest`, blaming a test that did nothing wrong.
     * That is a miserable failure to chase: it is a race between a coroutine
     * outliving its test and the next one starting, so it moves between
     * classes and build variants and vanishes when run in isolation.
     *
     * Collecting them here keeps them inside this suite, and [cleanup] fails
     * this test rather than a stranger's.
     */
    private val leaked = java.util.concurrent.CopyOnWriteArrayList<Throwable>()

    private fun configFor(pubKey: HexKey) =
        CoordinatorConfig(
            pubKey = pubKey,
            relays = listOf(RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!),
        )

    /** Hands each coordinator pubkey its own stub, with no transport in between. */
    private val links =
        CordnCoordinatorLinkFactory { _, config ->
            object : CordnCoordinatorLink {
                override val coordinator = if (config.pubKey == keyA) coordinatorA else coordinatorB

                override suspend fun close() = Unit
            }
        }

    /**
     * A scope for the runtime, on real threads.
     *
     * Real rather than a `TestScope` dispatcher, and that is forced rather
     * than chosen: `maintainKeyPackages` reads the KeyPackage store, which
     * hops to `Dispatchers.IO`. Virtual time cannot reach across that, so no
     * amount of `advanceUntilIdle()` makes the upkeep deterministic — an
     * earlier version of this suite looked green while the work had simply not
     * run yet, which is the failure mode a test is supposed to prevent.
     *
     * Its own `Job`, so nothing waits on `CordnSyncLoop`, whose design is
     * never to return; cancelled in [cleanup].
     */
    private fun runtimeScope() =
        CoroutineScope(
            Dispatchers.Default + Job() +
                CoroutineExceptionHandler { _, throwable -> leaked += throwable },
        ).also { scopes += it }

    private fun runtime(scope: CoroutineScope) =
        CordnRuntime(
            accountSigner = signer,
            client = EmptyNostrClient(),
            filesDir = root,
            scope = scope,
            cipher = XorCipher(),
            links = links,
        )

    /**
     * Waits for [condition], or fails by name.
     *
     * A bounded poll rather than a barrier, for the reason above: the work is
     * on another dispatcher. The budget is orders of magnitude more than it
     * needs, so a timeout here is a real failure and not a slow machine.
     */
    private suspend fun awaitUntil(
        what: String,
        condition: () -> Boolean,
    ) {
        withTimeoutOrNull(AWAIT_BUDGET_MS) {
            while (!condition()) delay(POLL_MS)
        } ?: error("timed out waiting for $what")
    }

    /**
     * Gives the runtime's detached upkeep room to run, then returns.
     *
     * Only used before asserting something did NOT happen. Weaker than a
     * barrier by nature — the honest way to assert a negative about another
     * dispatcher is to give it far more time than it needs and look again.
     */
    private suspend fun settle() = delay(SETTLE_MS)

    @After
    fun cleanup() {
        scopes.forEach { it.cancel() }
        root.deleteRecursively()

        // Cancellation is not a leak; anything else is, and it would otherwise
        // have surfaced as an unrelated test failing somewhere else entirely.
        val real = leaked.filterNot { it is CancellationException }
        check(real.isEmpty()) { "a runtime coroutine leaked: ${real.joinToString { it.toString() }}" }
    }

    @Test
    fun `opening a coordinator with nothing published does not publish a key package`() =
        runBlocking {
            // §8.4: publishing tells a coordinator this account exists and is
            // invitable, permanently. Doing that on someone's behalf at login
            // is the decision this rule exists to refuse.
            val runtime = runtime(runtimeScope())
            runtime.session(configFor(keyA))
            settle()

            assertFalse("published without being asked", coordinatorA.published.isNotEmpty())
        }

    @Test
    fun `opening a coordinator that already has one tops the pool back up`() =
        runBlocking {
            // The other half of the rule. Once a package is published there the
            // coordinator already knows, and a drained pool fails the next
            // invitation for a reason the inviter sees and the invitee never
            // does.
            val runtime = runtime(runtimeScope())
            runtime.session(configFor(keyA))
            runtime.publishKeyPackage(keyA)
            val afterFirst = coordinatorA.published.size

            // A second runtime over the same files: a relaunch, with one
            // package already published and its private half on disk.
            runtime(runtimeScope()).session(configFor(keyA))

            awaitUntil("the pool to be topped up after a relaunch") { coordinatorA.published.size > afterFirst }
        }

    @Test
    fun `purging one coordinator leaves the other's groups on disk`() =
        runBlocking {
            val runtime = runtime(runtimeScope())
            runtime.createGroup(configFor(keyA), CordnGroupMetadata(name = "A"), gid = "shared-gid")
            runtime.createGroup(configFor(keyB), CordnGroupMetadata(name = "B"), gid = "shared-gid")

            runtime.purge(keyA)

            // A gid is unique only within one coordinator, so both hold
            // "shared-gid" as unrelated groups. Deleting a level too high takes
            // both, and the only visible symptom is a room that vanished.
            assertFalse(CordnStorageLayout.directoryFor(root, account, keyA).exists())
            assertTrue(CordnStorageLayout.directoryFor(root, account, keyB).exists())
            assertEquals(
                listOf(keyB),
                runtime.groups.all.value
                    .map { it.coordinatorPubKey },
            )
        }

    @Test
    fun `an archive from another account is refused`() =
        runBlocking {
            // Restoring one account's groups under another's key gives a device
            // MLS state whose credentials name somebody else; every Commit it
            // made would be rejected by the rest of the group.
            val runtime = runtime(runtimeScope())
            runtime.createGroup(configFor(keyA), CordnGroupMetadata(name = "Mine"), gid = "mine")
            val archive = runtime.exportArchive("pw")

            val stranger =
                CordnRuntime(
                    accountSigner = NostrSignerInternal(KeyPair()),
                    client = EmptyNostrClient(),
                    filesDir = root,
                    scope = runtimeScope(),
                    cipher = XorCipher(),
                    links = links,
                )

            val failure = runCatching { stranger.importArchive(archive, "pw") }.exceptionOrNull()
            assertTrue("expected a refusal, got $failure", failure is IllegalArgumentException)
        }

    @Test
    fun `restoring replaces this device's groups rather than merging them`() =
        runBlocking {
            // The rule the confirmation dialog states. A merge is what produces
            // two devices holding one group's state, and MLS does not recover
            // from the fork that follows.
            val runtime = runtime(runtimeScope())
            runtime.createGroup(configFor(keyA), CordnGroupMetadata(name = "In the backup"), gid = "kept")
            val archive = runtime.exportArchive("pw")

            runtime.createGroup(configFor(keyA), CordnGroupMetadata(name = "Added after"), gid = "dropped")
            assertEquals(
                setOf("kept", "dropped"),
                runtime.groups.all.value
                    .map { it.gid }
                    .toSet(),
            )

            runtime.importArchive(archive, "pw")

            assertEquals(
                setOf("kept"),
                runtime.groups.all.value
                    .map { it.gid }
                    .toSet(),
            )
        }

    @Test
    fun `a coordinator that will not answer is reported, not counted as empty`() =
        runBlocking {
            // "We could not ask" and "there is nothing for you" look identical
            // on screen and mean opposite things.
            val runtime = runtime(runtimeScope())
            runtime.session(configFor(keyA))
            coordinatorA.failEverything = true

            val invitations = runtime.invitations()

            assertTrue(invitations.pending.isEmpty())
            assertEquals(listOf(keyA), invitations.unreachable.map { it.coordinator.pubKey })
            assertFalse("an unreachable coordinator is not an empty inbox", invitations.isEmpty)
        }

    /**
     * Just enough coordinator to let the runtime run.
     *
     * Not a fidelity fixture — `CordnFixtureCoordinator` is that, and it sits
     * behind a transport because it answers `CvmRequest`s. What these tests
     * need is the `ICoordinator` surface the runtime talks to, with a way to
     * make it stop answering.
     */
    private class StubCoordinator : ICoordinator {
        val published = mutableMapOf<String, String>()
        var failEverything = false

        private var clock = 1L

        private fun guard() {
            if (failEverything) throw IllegalStateException("coordinator is down")
        }

        override suspend fun publishKeyPackage(
            keyPackageRef: String,
            keyPackageBase64: String,
        ): PublishedKeyPackage {
            guard()
            published[keyPackageRef] = keyPackageBase64
            return PublishedKeyPackage(keyPackageRef, false, clock++)
        }

        override suspend fun removeKeyPackages(keyPackageRefs: List<String>): List<String> {
            guard()
            return keyPackageRefs.filter { published.remove(it) != null }
        }

        override suspend fun listKeyPackages(): List<AvailableKeyPackage> {
            guard()
            return published.keys.map { AvailableKeyPackage(OWNER, it, false, clock) }
        }

        override suspend fun takeKeyPackage(id: String): TakenKeyPackage? {
            guard()
            return null
        }

        override suspend fun storeWelcome(
            targetPubKey: HexKey,
            keyPackageRef: String,
            welcomeBase64: String,
            after: Long?,
        ): Long {
            guard()
            return clock++
        }

        override suspend fun takeWelcomes(consumed: List<ConsumedWelcomeRef>): List<PendingWelcome> {
            guard()
            return emptyList()
        }

        override suspend fun storeJoinRequest(
            gid: String,
            keyPackageRef: String,
        ): Long {
            guard()
            return clock++
        }

        override suspend fun takeJoinRequests(
            gids: List<String>,
            consumed: List<ConsumedJoinRequestRef>,
        ): List<JoinRequest> {
            guard()
            return emptyList()
        }

        override suspend fun postMessage(
            gid: String,
            sealedBase64: String,
        ): PostedMessage {
            guard()
            return PostedMessage(gid, clock++, clock)
        }

        override suspend fun fetchMessages(cursors: Map<String, Long?>): List<GroupMessage> {
            guard()
            return emptyList()
        }

        override suspend fun subscribeMessages(
            cursors: Map<String, Long?>,
            timeoutMs: Long,
            onMessage: (GroupMessage) -> Unit,
        ) {
            guard()
            // Suspends for the whole window, like a real subscription: the
            // coordinator holds the call open and returns when it closes.
            // Returning immediately turns `CordnSyncLoop` into a spin, which
            // on a virtual-time dispatcher is an unbounded one.
            delay(timeoutMs)
        }

        private companion object {
            /** Whose packages these are. The runtime filters `kp_list` by account. */
            const val OWNER = "owner"
        }
    }

    private companion object {
        const val AWAIT_BUDGET_MS = 10_000L
        const val POLL_MS = 10L

        /** Long enough that upkeep which was going to happen already has. */
        const val SETTLE_MS = 1_000L
    }
}
