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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The staff write-key delivery riding a Grant (CORD-04 §3): promotion and key delivery
 * are one signed edition, opaque pairwise ciphertext to every other reader, and adopted
 * only after the derive-check — which fails closed.
 */
class ControlRootWrapTest {
    private val granter = NostrSignerInternal(KeyPair())
    private val member = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())

    private val communityId = ByteArray(32) { 0x33 }
    private val controlRoot = ByteArray(32) { 0x22 }
    private val epoch = 7L

    private fun controlPkAt(
        root: ByteArray = controlRoot,
        at: Long = epoch,
    ) = ConcordKeyDerivation.controlSignerKey(root, communityId, at).publicKeyHex

    @Test
    fun theWirePlaintextIsFortyBytesEpochThenSecret() {
        val plaintext = ControlRootWrap.encodePlaintext(epoch, controlRoot)
        assertEquals(ControlRootWrap.SIZE, plaintext.size)
        assertEquals(40, plaintext.size)

        val decoded = ControlRootWrap.decodePlaintext(plaintext)
        assertNotNull(decoded)
        assertEquals(epoch, decoded.epoch)
        assertContentEquals(controlRoot, decoded.controlRoot)

        // Any other width is malformed — the rekey-blob discipline (CORD-06 §1).
        assertNull(ControlRootWrap.decodePlaintext(ByteArray(39)))
        assertNull(ControlRootWrap.decodePlaintext(ByteArray(41)))
    }

    @Test
    fun thePromotedMemberOpensItAndNobodyElseCan() =
        runTest {
            val wrap = ControlRootWrap.build(granter, member.pubKey, epoch, controlRoot)

            val opened = ControlRootWrap.openOrNull(wrap, member, granter.pubKey)
            assertNotNull(opened)
            assertEquals(epoch, opened.epoch)
            assertContentEquals(controlRoot, opened.controlRoot)

            // Every other reader of the plane sees opaque bytes.
            assertNull(ControlRootWrap.openOrNull(wrap, stranger, granter.pubKey))
        }

    @Test
    fun theGranterCanReopenItsOwnDeliveryBecauseTheKeyIsPairwise() =
        runTest {
            // One ECDH either side can compute, so a NIP-46 bunker account opens it with a
            // single nip44_decrypt and a re-issuing staffer needs no stored copy.
            val wrap = ControlRootWrap.build(granter, member.pubKey, epoch, controlRoot)
            val opened = ControlRootWrap.openOrNull(wrap, granter, member.pubKey)
            assertNotNull(opened)
            assertContentEquals(controlRoot, opened.controlRoot)
        }

    @Test
    fun adoptionRequiresTheSecretToDeriveToTheHeldAddress() {
        assertTrue(ControlRootWrap.derivesTo(controlRoot, communityId, epoch, controlPkAt()))

        // A garbage secret is attributable griefing, nothing worse: it is dropped, never adopted.
        assertFalse(ControlRootWrap.derivesTo(ByteArray(32) { 0x77 }, communityId, epoch, controlPkAt()))

        // The epoch binds too — a secret for another epoch derives elsewhere.
        assertFalse(ControlRootWrap.derivesTo(controlRoot, communityId, epoch + 1, controlPkAt()))

        // And so does the community: the same secret in another Community is another plane.
        assertFalse(ControlRootWrap.derivesTo(controlRoot, ByteArray(32) { 0x44 }, epoch, controlPkAt()))
    }

    @Test
    fun aWrapMintedForAPriorEpochFailsTheCheckRatherThanBeingAdopted() =
        runTest {
            // Compaction re-wraps a Grant head verbatim across Refoundings, so a folded head can
            // carry a wrap minted for a prior epoch's key. Staleness is structural and harmless.
            val staleWrap = ControlRootWrap.build(granter, member.pubKey, epoch, controlRoot)
            val opened = ControlRootWrap.openOrNull(staleWrap, member, granter.pubKey)
            assertNotNull(opened)

            val currentEpoch = epoch + 1
            val currentControlRoot = ByteArray(32) { 0x55 }
            assertEquals(epoch, opened.epoch, "the epoch rides inside the ciphertext, not beside it")
            assertFalse(
                ControlRootWrap.derivesTo(opened.controlRoot, communityId, currentEpoch, controlPkAt(currentControlRoot, currentEpoch)),
                "a stale wrap must fail closed at the current epoch",
            )
        }

    @Test
    fun aGrantCarriesTheWrapThroughTheWireShapeAndSurvivesARoundTrip() =
        runTest {
            val wrap = ControlRootWrap.build(granter, member.pubKey, epoch, controlRoot)
            val grant = GrantEntity(member = member.pubKey, roleIds = listOf("ab".repeat(32)), controlWrap = wrap)

            val json = ConcordJson.instance.encodeToString(GrantEntity.serializer(), grant)
            assertTrue(json.contains("control_wrap"), "the wire field is snake_case (CORD-04 §2)")

            val decoded = ConcordJson.decodeOrNull<GrantEntity>(json)
            assertNotNull(decoded)
            assertEquals(wrap, decoded.controlWrap)

            // A plain grant carries none, and a reader must cope with its absence.
            val plain = ConcordJson.decodeOrNull<GrantEntity>("""{"member":"${member.pubKey}","role_ids":[]}""")
            assertNotNull(plain)
            assertNull(plain.controlWrap)
        }

    @Test
    fun theStaffBitsAreTheControlWritingPermissions() {
        // The six bits whose actions land as Control editions (CORD-04 §3).
        val staff = ConcordPermissions.STAFF_BITS
        assertTrue(staff.has(ConcordPermissions.MANAGE_ROLES))
        assertTrue(staff.has(ConcordPermissions.MANAGE_CHANNELS))
        assertTrue(staff.has(ConcordPermissions.MANAGE_METADATA))
        assertTrue(staff.has(ConcordPermissions.BAN))
        assertTrue(staff.has(ConcordPermissions.CREATE_INVITE))
        assertTrue(staff.has(ConcordPermissions.PIN_MESSAGES))

        // KICK writes to the Guestbook and MANAGE_MESSAGES to Chat planes; neither needs the key.
        assertFalse(staff.has(ConcordPermissions.KICK))
        assertFalse(staff.has(ConcordPermissions.MANAGE_MESSAGES))

        // PIN_MESSAGES claims the frozen bit 11 (CORD-04 §3 table).
        assertEquals(11, ConcordPermissions.PIN_MESSAGES)
    }
}
