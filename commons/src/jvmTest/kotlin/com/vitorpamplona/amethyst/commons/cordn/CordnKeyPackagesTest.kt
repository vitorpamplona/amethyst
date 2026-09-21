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

import com.vitorpamplona.quartz.cordn.groups.CordnCredential
import com.vitorpamplona.quartz.cordn.spec00Coordinator.KeyPackagePublication
import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.messages.KeyPackageBundleCodec
import com.vitorpamplona.quartz.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Publishing KeyPackages so other people can invite this account.
 *
 * Driven over the real transport rather than a fake coordinator, because two of
 * the things worth checking only exist on the wire: that `kp_ref` is the RFC
 * 9420 KeyPackageRef the coordinator will key on, and that the publication
 * payload a `kp_take` serves back verifies — which it can only do if the signed
 * request event round-tripped (`spec/00.md` §7).
 */
@OptIn(ExperimentalEncodingApi::class)
class CordnKeyPackagesTest : CordnTransportHarness() {
    @Test
    fun `a published ref is the RFC 9420 KeyPackageRef`() =
        runTest {
            // The coordinator's primary key and the address a Welcome is
            // delivered to. Computing it differently from everyone else fails
            // silently: every kp_take misses and no group ever forms.
            val alice = Account()
            val published = driving { alice.keyPackages.publishNew() }

            val bundle = assertNotNull(alice.keyPackages.bundleFor(published.keyPackageRef))
            assertEquals(bundle.keyPackage.reference().toHexKey(), published.keyPackageRef)
        }

    @Test
    fun `what the coordinator serves back verifies as ours`() =
        runTest {
            // §9: the client re-runs every check even though §8 makes the
            // coordinator run them too. That is only meaningful if the signed
            // request event really is what comes back.
            val alice = Account()
            val published = driving { alice.keyPackages.publishNew() }

            val taken = assertNotNull(driving { alice.coordinatorClient.takeKeyPackage(published.keyPackageRef) })
            val verified = KeyPackagePublication.verify(taken.publicationEvent)

            assertEquals(alice.pubKey, verified.pubKey)
            assertEquals(
                published.keyPackageRef,
                MlsKeyPackage.decodeTls(TlsReader(verified.bytes)).reference().toHexKey(),
            )
            assertEquals(alice.pubKey, CordnCredential.identityOrNull(verified.keyPackage.leafNode))
        }

    @Test
    fun `the private half survives a restart`() =
        runTest {
            val alice = Account()
            val published = driving { alice.keyPackages.publishNew() }
            val before = assertNotNull(alice.keyPackages.bundleFor(published.keyPackageRef))

            // Same store, new manager — what a process restart looks like.
            val restarted = CordnKeyPackages(alice.pubKey, alice.coordinatorClient, alice.keyPackageStore)
            restarted.restore()

            assertEquals(setOf(published.keyPackageRef), restarted.published.value)
            val after = assertNotNull(restarted.bundleFor(published.keyPackageRef))
            assertContentEquals(before.initPrivateKey, after.initPrivateKey)
            assertContentEquals(before.signaturePrivateKey, after.signaturePrivateKey)
            assertContentEquals(before.encryptionPrivateKey, after.encryptionPrivateKey)
        }

    @Test
    fun `a failed publish leaves nothing behind`() =
        runTest {
            // The half that matters: a bundle we kept for a package the
            // coordinator never took is dead weight, and accumulating one per
            // failed attempt is how a key store grows without bound.
            val alice = Account()
            coordinator.rejectPublication = true

            assertFailsWith<Exception> { driving { alice.keyPackages.publishNew() } }

            assertTrue(
                alice.keyPackages.published.value
                    .isEmpty(),
            )
            assertTrue(alice.keyPackageStore.list().isEmpty())
        }

    @Test
    fun `a last-resort package is published once and reused`() =
        runTest {
            val alice = Account()
            val first = driving { alice.keyPackages.ensureLastResort() }
            val second = driving { alice.keyPackages.ensureLastResort() }

            assertEquals(first, second, "a second call must not publish another reusable package")

            val bundle = assertNotNull(alice.keyPackages.bundleFor(assertNotNull(first)))
            assertTrue(bundle.keyPackage.isLastResort(), "it must actually carry the marker")
        }

    @Test
    fun `a second device publishes its own last-resort package`() =
        runTest {
            // kp_list shows the account's packages, not this device's, so a
            // second device sees the first device's reusable package and could
            // mistake it for its own. Reusing it would send every fallback
            // Welcome to a KeyPackage only the other device can open — the
            // invite looks accepted and the group never forms here.
            val alice = Account()
            val first = assertNotNull(driving { alice.keyPackages.ensureLastResort() })

            // Same account and coordinator, empty key store: a new install.
            val secondDevice = CordnKeyPackages(alice.pubKey, alice.coordinatorClient, InMemoryCordnKeyPackageStore())
            val second = assertNotNull(driving { secondDevice.ensureLastResort() })

            assertTrue(first != second, "it must not adopt a key it has no private half for")
            assertNotNull(secondDevice.bundleFor(second))

            // And it settles: a second call on that device reuses its own.
            assertEquals(second, driving { secondDevice.ensureLastResort() })
        }

    @Test
    fun `an ordinary package is not marked last resort`() =
        runTest {
            val alice = Account()
            val published = driving { alice.keyPackages.publishNew() }
            val bundle = assertNotNull(alice.keyPackages.bundleFor(published.keyPackageRef))
            assertTrue(!bundle.keyPackage.isLastResort())
        }

    @Test
    fun `top-up counts what the coordinator holds, not what we published`() =
        runTest {
            // kp_take consumes a single-use package, so the coordinator's count
            // falls as people invite us while ours does not. Counting ours
            // would let the pool silently empty.
            val alice = Account()
            driving { alice.keyPackages.topUp(minimum = 3) }
            assertEquals(3, coordinator.availableCount(alice.pubKey))

            // Already at the floor: nothing more to do.
            val none = driving { alice.keyPackages.topUp(minimum = 3) }
            assertTrue(none.isEmpty())

            // Somebody invites us, consuming one.
            driving { alice.coordinatorClient.takeKeyPackage(alice.pubKey) }
            val refilled = driving { alice.keyPackages.topUp(minimum = 3) }
            assertEquals(1, refilled.size, "the pool must refill to the floor")
        }

    @Test
    fun `the last-resort package does not count toward the single-use floor`() =
        runTest {
            // Startup calls both, so this is the ordinary state, not a corner.
            // The reusable package is the fallback for when the pool is drained;
            // letting it fill a slot in the pool means every account ships one
            // fewer single-use package than it asked for, and the fallback gets
            // used a round earlier than it should.
            val alice = Account()
            driving { alice.keyPackages.ensureLastResort() }
            driving { alice.keyPackages.topUp(minimum = 3) }

            assertEquals(3, coordinator.availableCount(alice.pubKey), "the pool is the single-use packages alone")
            assertEquals(4, coordinator.keyPackagesOf(alice.pubKey).size, "plus the one reusable package")
        }

    @Test
    fun `withdrawing tells the coordinator before forgetting the key`() =
        runTest {
            // Order matters: dropping the private half while the coordinator
            // still serves the package means somebody invites us and we cannot
            // open the Welcome.
            val alice = Account()
            val published = driving { alice.keyPackages.publishNew() }

            val removed = driving { alice.keyPackages.withdraw(listOf(published.keyPackageRef)) }

            assertEquals(listOf(published.keyPackageRef), removed)
            assertNull(alice.keyPackages.bundleFor(published.keyPackageRef))
            assertTrue(
                alice.keyPackages.published.value
                    .isEmpty(),
            )
            assertNull(driving { alice.coordinatorClient.takeKeyPackage(published.keyPackageRef) })
        }

    @Test
    fun `a withdrawal the coordinator refuses keeps the key we still need`() =
        runTest {
            // The other half of the ordering rule, and the only half a working
            // coordinator can show. While the coordinator still serves the
            // package, somebody can still invite us with it — so the private
            // half has to survive a removal that did not happen. Forgetting it
            // first would leave an invite we can never open.
            val alice = Account()
            val published = driving { alice.keyPackages.publishNew() }
            coordinator.rejectRemoval = true

            assertFailsWith<Exception> { driving { alice.keyPackages.withdraw(listOf(published.keyPackageRef)) } }

            assertNotNull(alice.keyPackages.bundleFor(published.keyPackageRef))
            assertEquals(setOf(published.keyPackageRef), alice.keyPackages.published.value)

            // And the invite it can still back really does open.
            coordinator.rejectRemoval = false
            val taken = assertNotNull(driving { alice.coordinatorClient.takeKeyPackage(published.keyPackageRef) })
            assertEquals(published.keyPackageRef, taken.keyPackageRef)
        }

    @Test
    fun `a bundle we cannot decode sends the Welcome back to the inbox`() =
        runTest {
            // Rather than throwing: another device of the same account, or a
            // later version of this one, may be able to handle it.
            val alice = Account()
            alice.keyPackageStore.save("corrupt", byteArrayOf(9, 9, 9))
            assertNull(alice.keyPackages.bundleFor("corrupt"))
        }

    @Test
    fun `the bundle codec round-trips every private half`() =
        runTest {
            val alice = Account()
            val published = driving { alice.keyPackages.publishNew() }
            val bundle = assertNotNull(alice.keyPackages.bundleFor(published.keyPackageRef))

            val again = KeyPackageBundleCodec.decode(KeyPackageBundleCodec.encode(bundle))

            assertContentEquals(bundle.keyPackage.toTlsBytes(), again.keyPackage.toTlsBytes())
            assertContentEquals(bundle.initPrivateKey, again.initPrivateKey)
            assertContentEquals(bundle.encryptionPrivateKey, again.encryptionPrivateKey)
            assertContentEquals(bundle.signaturePrivateKey, again.signaturePrivateKey)
        }

    @Test
    fun `an unknown bundle layout is refused, not guessed at`() {
        // A misread bundle yields key material that is silently wrong, which
        // surfaces much later as a Welcome that will not open.
        val forwardVersion = byteArrayOf(0x00, 0x63) + ByteArray(32)
        assertFailsWith<IllegalArgumentException> { KeyPackageBundleCodec.decode(forwardVersion) }
    }

    @Test
    fun `publishing names the account, by design`() =
        runTest {
            // §8.4: kp_publish rides the stable identity and the coordinator
            // keeps the signed event to re-serve. That is the disclosure the
            // exposure card reports; it must not quietly become ephemeral.
            val alice = Account()
            driving { alice.keyPackages.publishNew() }

            val publish = assertNotNull(coordinator.calls.lastOrNull { it.method == "kp_publish" })
            assertEquals(alice.pubKey, publish.callerPubKey)
        }

    @Test
    fun `whether a key package is published is read from the store, not from this session`() =
        runTest {
            val alice = Account()
            val store = InMemoryCordnKeyPackageStore()
            val first = CordnKeyPackages(alice.pubKey, alice.coordinatorClient, store)
            assertFalse(first.hasPublished(), "nothing published yet")

            driving { first.publishNew() }

            // A second instance over the same store -- which is what a relaunch
            // is. The coordinator still holds the KeyPackage, so an exposure
            // card reporting "not published" here would understate what it
            // knows, and would do it on every launch after the first.
            val second = CordnKeyPackages(alice.pubKey, alice.coordinatorClient, store)
            assertTrue(second.hasPublished(), "before restore()")
            second.restore()
            assertTrue(second.hasPublished(), "after restore()")
        }
}
