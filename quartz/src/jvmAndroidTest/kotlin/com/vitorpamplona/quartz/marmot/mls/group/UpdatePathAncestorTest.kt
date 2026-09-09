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
package com.vitorpamplona.quartz.marmot.mls.group

import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9420 §7.6 does not say "the committer encrypts the path secret to your
 * leaf". It says the committer encrypts one secret per node in the copath
 * RESOLUTION, and each member decrypts at whichever of those nodes it holds a
 * private key for. A merged subtree resolves to its parent, so from three
 * members on the ciphertext meant for us is addressed at an ANCESTOR.
 *
 * Keeping only our leaf key therefore worked for two members and failed for
 * three, which is exactly how it survived every same-implementation test:
 * `UpdatePath at common ancestor carries no ciphertext for us (my_leaf=1,
 * my_node=2, resolution=[1], encrypted_path_secrets=1)`. Node 1 was the parent
 * we had held a key for since the commit that merged us, and we never stored
 * it.
 */
class UpdatePathAncestorTest {
    private fun bundleFor(seed: Byte): KeyPackageBundle =
        MlsGroup
            .create(identity = ByteArray(32) { seed })
            .createKeyPackage(identity = ByteArray(32) { seed }, signingKey = ByteArray(32) { seed })

    @Test
    fun aThirdPartyCommitReachesUsAtAMergedAncestor() {
        // Alice creates, adds Bob, then adds Carol. After Bob's own commit the
        // {alice, bob} subtree is merged, so a later commit from Carol
        // resolves that subtree to its parent rather than to Bob's leaf.
        val alice = MlsGroup.create(identity = ByteArray(32) { 0x0a })
        val bobBundle = bundleFor(0x0b)
        val carolBundle = bundleFor(0x0c)

        alice.proposeAdd(bobBundle.keyPackage.toTlsBytes())
        val addBob = alice.commit()
        val bob = MlsGroup.processWelcome(assertNotNull(addBob.welcomeBytes), bobBundle)

        alice.proposeAdd(carolBundle.keyPackage.toTlsBytes())
        val addCarol = alice.commit()
        bob.processFramedCommit(addCarol.framedCommitBytes)
        val carol = MlsGroup.processWelcome(assertNotNull(addCarol.welcomeBytes), carolBundle)

        // Bob commits: this merges Bob's direct path and hands him the parent
        // keys the next committer will address him at.
        val bobCommit = bob.commit()
        alice.processFramedCommit(bobCommit.framedCommitBytes)
        carol.processFramedCommit(bobCommit.framedCommitBytes)

        // Carol commits. Bob is now inside a merged subtree, so Carol's
        // UpdatePath addresses him at a parent node, not at his leaf.
        val carolCommit = carol.commit()
        alice.processFramedCommit(carolCommit.framedCommitBytes)
        bob.processFramedCommit(carolCommit.framedCommitBytes)

        assertEquals(carol.epoch, bob.epoch)
        assertEquals(carol.epoch, alice.epoch)
        assertContentEquals(
            carol.exporterSecret("marmot", "group-event".encodeToByteArray(), 32),
            bob.exporterSecret("marmot", "group-event".encodeToByteArray(), 32),
        )
        assertContentEquals(
            carol.exporterSecret("marmot", "group-event".encodeToByteArray(), 32),
            alice.exporterSecret("marmot", "group-event".encodeToByteArray(), 32),
        )
    }

    /**
     * The keys are useless if a restart drops them: the group would keep
     * working until the next commit addressed us at an ancestor and then stop
     * dead, with nothing in the log tying the failure to the relaunch.
     */
    @Test
    fun theAncestorKeysSurviveASaveAndRestore() {
        val alice = MlsGroup.create(identity = ByteArray(32) { 0x0a })
        val bobBundle = bundleFor(0x0b)
        val carolBundle = bundleFor(0x0c)

        alice.proposeAdd(bobBundle.keyPackage.toTlsBytes())
        val addBob = alice.commit()
        var bob = MlsGroup.processWelcome(assertNotNull(addBob.welcomeBytes), bobBundle)

        alice.proposeAdd(carolBundle.keyPackage.toTlsBytes())
        val addCarol = alice.commit()
        bob.processFramedCommit(addCarol.framedCommitBytes)
        val carol = MlsGroup.processWelcome(assertNotNull(addCarol.welcomeBytes), carolBundle)

        val bobCommit = bob.commit()
        alice.processFramedCommit(bobCommit.framedCommitBytes)
        carol.processFramedCommit(bobCommit.framedCommitBytes)

        // Round-trip Bob through the persisted blob, exactly as a relaunch does.
        val saved = bob.saveState()
        assertTrue(saved.pathPrivateKeys.isNotEmpty(), "a committer holds keys for its own direct path")
        bob = MlsGroup.restore(MlsGroupStateCodec.roundTrip(saved))

        val carolCommit = carol.commit()
        bob.processFramedCommit(carolCommit.framedCommitBytes)

        assertEquals(carol.epoch, bob.epoch)
        assertContentEquals(
            carol.exporterSecret("marmot", "group-event".encodeToByteArray(), 32),
            bob.exporterSecret("marmot", "group-event".encodeToByteArray(), 32),
        )
    }
}

private object MlsGroupStateCodec {
    fun roundTrip(state: MlsGroupState): MlsGroupState = MlsGroupState.decodeTls(state.encodeTls())
}
