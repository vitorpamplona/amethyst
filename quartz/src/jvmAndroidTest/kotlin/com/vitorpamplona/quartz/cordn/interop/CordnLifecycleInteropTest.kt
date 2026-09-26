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
package com.vitorpamplona.quartz.cordn.interop

import com.vitorpamplona.quartz.cordn.appGroupRef.CordnGroupRef
import com.vitorpamplona.quartz.cordn.groups.CordnCredential
import com.vitorpamplona.quartz.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnApplicationMessage
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.quartz.cordn.spec03Payloads.SealedPayload
import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.framing.ContentType
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A full cordn group lifecycle, against fixtures **ts-mls** produced.
 *
 * Every other test in this module proves we agree with ourselves. This one
 * proves we agree with the implementation cordn's own client runs: Alice
 * creates a group in TypeScript, adds Bob, and sends a message, and we join as
 * Bob and read it.
 *
 * Each step is a separate test because each fails for a different reason, and
 * a single end-to-end assertion would tell you only that something in a chain
 * of six broke.
 */
class CordnLifecycleInteropTest {
    private val alice = TsMlsFixtures.text("alice.pk")
    private val bob = TsMlsFixtures.text("bob.pk")

    private fun bobBundle() = TsMlsPrivateKeyPackage.decode(TsMlsFixtures.bytes("bob-kp.bin"), TsMlsFixtures.bytes("bob-privkp.bin"))

    /** Bob's group at epoch 1, joined from the ts-mls Welcome. */
    private fun bobJoined(): MlsGroup = MlsGroup.processWelcome(TsMlsFixtures.b64("welcome.b64"), bobBundle(), CordnGroupPolicy)

    // ---- 1. their KeyPackage, our decoder ----------------------------------

    @Test
    fun `we read a ts-mls KeyPackage and compute the same KeyPackageRef`() {
        val keyPackage = MlsKeyPackage.decodeTls(TlsReader(TsMlsFixtures.bytes("bob-kp.bin")))

        assertEquals(
            bob,
            CordnCredential.identityOrNull(keyPackage.leafNode),
            "the credential must decode as cordn's 64-ASCII-hex identity",
        )
        // kp_ref is the coordinator's primary key for a KeyPackage. Disagreeing
        // here means every kp_take and every Welcome addressed to us misses.
        assertEquals(TsMlsFixtures.text("bob-kpref.hex"), keyPackage.reference().toHexKey())
    }

    @Test
    fun `we recognise a ts-mls last-resort KeyPackage`() {
        // cordn marks it with app_data_dictionary component 0x0004; Marmot's
        // MIP-era profile uses bare extension 0x000A. Our reader accepts both,
        // and this pins the cordn carrier against a real one.
        val lastResort = MlsKeyPackage.decodeTls(TlsReader(TsMlsFixtures.bytes("bob-lastresort-kp.bin")))
        assertTrue(lastResort.isLastResort())

        val ordinary = MlsKeyPackage.decodeTls(TlsReader(TsMlsFixtures.bytes("bob-kp.bin")))
        assertTrue(!ordinary.isLastResort(), "and must not see it where there is none")
    }

    @Test
    fun `the ts-mls private key package round-trips through our codec`() {
        val bundle = bobBundle()
        assertEquals(32, TsMlsPrivateKeyPackage.seedOf(bundle.signaturePrivateKey).size)
        assertContentEquals(
            TsMlsFixtures.bytes("bob-privkp.bin"),
            TsMlsPrivateKeyPackage.encode(bundle),
            "re-encoding must reproduce ts-mls's bytes, PKCS#8 wrapper included",
        )
    }

    // ---- 2. their seal, our exporter ---------------------------------------

    @Test
    fun `we unseal a ts-mls commit with the published epoch-0 exporter`() {
        // spec/03.md §5: a Commit is sealed under the epoch it transitions FROM,
        // so every member still at that epoch can read it before advancing.
        // Sealing it under the new epoch would lock out exactly the members it
        // is addressed to.
        val opened = SealedPayload.open(TsMlsFixtures.text("commit-add-sealed.b64"), TsMlsFixtures.hex("exporter-e0.hex"))
        assertContentEquals(TsMlsFixtures.b64("commit-add.b64"), opened)
    }

    @Test
    fun `we unseal a ts-mls application message with the epoch-1 exporter`() {
        val opened = SealedPayload.open(TsMlsFixtures.text("app-1-sealed.b64"), TsMlsFixtures.hex("exporter-e1.hex"))
        assertContentEquals(TsMlsFixtures.b64("app-1.b64"), opened)
    }

    // ---- 3. their Welcome, our join ---------------------------------------

    @Test
    fun `we join the group from the ts-mls Welcome`() {
        val group = bobJoined()

        assertEquals(1L, group.epoch, "the Welcome admits us at epoch 1")
        assertEquals(bob, CordnCredential.identityOrNull(group.members()[group.leafIndex].second))
        assertEquals(
            setOf(alice, bob),
            CordnCredential.memberIdentities(group),
            // Not group.currentMemberIdentities(): that hexes the credential
            // bytes, which for cordn's already-hex identity gives 128
            // characters of hex-of-hex. The trap is real enough that cordn has
            // its own accessor.
        )
    }

    @Test
    fun `our derived exporter matches theirs byte for byte`() {
        // The single strongest assertion available. The exporter sits at the
        // end of the whole key schedule, so agreeing on it means the tree, the
        // transcript hash, the commit secret and every epoch secret matched. A
        // one-bit divergence anywhere upstream produces 32 completely different
        // bytes here.
        val group = bobJoined()
        val exporter = CordnGroupPolicy.PAYLOAD_EXPORTER

        assertContentEquals(
            TsMlsFixtures.hex("exporter-e1.hex"),
            group.exporterSecret(exporter.label, exporter.context, exporter.length),
        )
    }

    @Test
    fun `we read the group metadata ts-mls put in the GroupContext`() {
        // Also proves the RFC 9420 §12.1.7 fix: the old engine refused any
        // extension type outside a hardcoded list, and 0xC04D is not in it.
        val metadata = CordnGroupMetadata.fromExtensions(bobJoined().extensions)

        assertEquals("Conformance", metadata?.name)
        assertEquals(listOf(alice), metadata?.adminPubkeys)
        assertEquals("🪜", metadata?.icon, "the emoji must survive as UTF-8")
        assertTrue(!metadata!!.isEgalitarian, "this group names an admin")
    }

    // ---- 4. their message, our reader -------------------------------------

    @Test
    fun `we open a ts-mls application message end to end`() {
        // The whole path: unseal, MLS-decrypt, read the authenticated sender out
        // of AAD, and hold the unsigned envelope to it.
        val received = CordnApplicationMessage.open(bobJoined(), TsMlsFixtures.text("app-1-sealed.b64"))

        assertEquals(alice, received.sender, "the sender comes from authenticated_data, not the envelope")
        assertEquals("hello from ts-mls", received.envelope.content)
        assertEquals(9, received.envelope.kind)
        assertEquals(bob, received.envelope.tags.single()[1], "the `p` tag names Bob")
    }

    @Test
    fun `the envelope id we recompute matches the one ts-mls wrote`() {
        // spec/02.md §4 makes the receiver recompute it. If our NIP-01
        // serialization differed by so much as a space, every message would be
        // rejected as tampered.
        val expected = CordnEnvelope.decode(TsMlsFixtures.bytes("envelope-1.json"), senderIdentity = alice)
        val received = CordnApplicationMessage.open(bobJoined(), TsMlsFixtures.text("app-1-sealed.b64"))

        assertEquals(expected.id, received.envelope.id)
        assertEquals(expected.id, received.envelope.computedId())
    }

    // ---- 5. their metadata commit, our epoch advance -----------------------

    /**
     * Bob at epoch 2, having processed ts-mls's metadata commit.
     *
     * The commit arrives PRIVATE-framed, not public. That is a cordn/Marmot
     * difference worth noticing: Marmot publishes commits as PublicMessage
     * inside a kind-445 event, while cordn seals everything to the coordinator
     * and frames handshake traffic privately, so `decrypt` is the entry point
     * and `processFramedCommit` is not.
     */
    private fun bobAtEpoch2(): MlsGroup {
        val group = bobJoined()
        val commit = SealedPayload.open(TsMlsFixtures.text("commit-meta-sealed.b64"), TsMlsFixtures.hex("exporter-e1.hex"))
        val decrypted = group.decrypt(commit)
        assertEquals(ContentType.COMMIT, decrypted.contentType, "cordn frames handshake messages privately")
        return group
    }

    @Test
    fun `we apply a ts-mls GroupContextExtensions commit that renames the group`() {
        // The strongest available check on the RFC 9420 §12.1.7 fix. Before it,
        // the engine refused any extension type outside a hardcoded list, so
        // this real commit -- carrying 0xC04D -- would have been rejected and
        // Bob would have sat at epoch 1 forever while everyone else moved on.
        val group = bobAtEpoch2()

        assertEquals(2L, group.epoch)
        assertEquals("Conformance (renamed)", CordnGroupMetadata.fromExtensions(group.extensions)?.name)
        assertEquals(
            "https://example.invalid/g.png",
            CordnGroupMetadata.fromExtensions(group.extensions)?.imageUrl,
        )
    }

    @Test
    fun `our exporter still matches theirs after the epoch advance`() {
        // Joining agreed at epoch 1; this proves the commit itself -- tree
        // mutation, transcript hash, key schedule -- agreed too.
        val group = bobAtEpoch2()
        val exporter = CordnGroupPolicy.PAYLOAD_EXPORTER

        assertContentEquals(
            TsMlsFixtures.hex("exporter-e2.hex"),
            group.exporterSecret(exporter.label, exporter.context, exporter.length),
        )
    }

    @Test
    fun `we read a threaded reply sent at epoch 2`() {
        val received = CordnApplicationMessage.open(bobAtEpoch2(), TsMlsFixtures.text("app-2-sealed.b64"))

        assertEquals(alice, received.sender)
        assertEquals(1111, received.envelope.kind, "NIP-22 threaded reply, per spec/02.md §6")
        assertEquals("reply at epoch 2 \u2728", received.envelope.content)
        assertEquals(
            CordnEnvelope.decode(TsMlsFixtures.bytes("envelope-2.json"), senderIdentity = alice).id,
            received.envelope.id,
        )
    }

    // ---- 6. their KeyPackage, our group ------------------------------------

    @Test
    fun `we can add a ts-mls member to a group we created`() {
        // The reverse direction, as far as it goes without running their client:
        // a group built by our engine under CordnGroupPolicy accepts a real
        // ts-mls KeyPackage and produces a Welcome for it. A capability or
        // required_capabilities mismatch between the two profiles would fail
        // exactly here.
        val group =
            MlsGroup.create(
                identity = CordnCredential.of(alice).identity,
                policy = CordnGroupPolicy,
                initialExtensions = listOf(CordnGroupMetadata(name = "from Kotlin").toExtension()),
            )

        val result = group.addMember(TsMlsFixtures.bytes("bob2-kp.bin"))

        assertEquals(1L, group.epoch)
        assertEquals(setOf(alice, bob), CordnCredential.memberIdentities(group))
        assertTrue(result.welcomeBytes != null, "a Welcome must be produced for the joiner")
    }

    // ---- 7. the delivery id ------------------------------------------------

    @Test
    fun `the gid survives a group-ref round trip`() {
        // spec/applications/group-ref.md §4.1: byte for byte, no normalisation.
        // This gid is a plain string rather than a UUID, which is exactly the
        // case a decoder that "tidied" its input would break.
        val gid = TsMlsFixtures.text("gid")
        assertEquals(gid, CordnGroupRef.decode(CordnGroupRef(gid).encode()).gid)
    }
}
