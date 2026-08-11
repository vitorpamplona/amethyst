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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEditionBuilder
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Control Plane's write restriction (CORD-01 Write-Restricted Streams, CORD-02 §2/§5).
 *
 * Every member holds the derived `control_pk` to subscribe, verify and read under the
 * `community_root`-derived read key, but only the owner and staff hold the `control_root`
 * the signer derives from — so a member can read every edition and mint none.
 */
class ControlPlaneSplitTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val now = 1_700_000_000L

    private val communityRoot = ByteArray(32) { 0x11 }
    private val controlRoot = ByteArray(32) { 0x22 }
    private val communityId = ByteArray(32) { 0x33 }
    private val epoch = 0L

    private fun staffView() = ControlPlaneKeys.forStaff(communityRoot, communityId, epoch, controlRoot)

    private fun memberView() = ControlPlaneKeys.forMember(communityRoot, communityId, epoch, staffView().address)

    private suspend fun edition(createdAt: Long = now) =
        ControlEditionBuilder.rumor(
            authorPubKey = owner.pubKey,
            entityKind = ControlEntityKind.METADATA,
            entityId = communityId,
            version = 0,
            prevHash = null,
            content = """{"name":"Nostrichs"}""",
            createdAt = createdAt,
        )

    @Test
    fun theSignerAndTheReadKeyAreDifferentKeysUnderDifferentLabels() {
        val staff = staffView()
        val legacy = ControlPlaneKeys.legacy(communityRoot, communityId, epoch)

        // The address derives from the control_root, the read key from the community_root.
        assertEquals(ConcordKeyDerivation.controlSignerKey(controlRoot, communityId, epoch).publicKeyHex, staff.address)
        assertNotEquals(staff.address, staff.readKey.publicKeyHex)

        // The two schemes never collide: different labels, different addresses (CORD-02 §5).
        assertNotEquals(legacy.address, staff.address)

        // A legacy epoch is address, signer and read key at once — every member holds all three.
        assertTrue(legacy.legacy)
        assertTrue(legacy.canWrite)
        assertEquals(legacy.address, legacy.readKey.publicKeyHex)
    }

    @Test
    fun aMemberReadsEveryEditionButHoldsNoWriteKey() =
        runTest {
            val staff = staffView()
            val member = memberView()

            assertTrue(staff.canWrite)
            assertFalse(member.canWrite, "a member must never hold the Control Plane write key")
            // Same plane: same address to subscribe to, same conversation key to decrypt with.
            assertEquals(staff.address, member.address)
            assertContentEqualsHex(staff.readKey.conversationKey, member.readKey.conversationKey)

            val wrap = ConcordStreamEnvelope.wrap(edition(), staff, owner, encrypted = false, createdAt = now)
            assertEquals(staff.address, wrap.pubKey)

            val opened = ConcordStreamEnvelope.openOrNull(wrap, member)
            assertNotNull(opened, "a member must be able to read a staff-written edition")
            assertEquals(owner.pubKey, opened.author)
            assertNotNull(ControlEdition.fromRumor(opened.rumor))
        }

    @Test
    fun aMemberCannotMintAWrapThatVerifiesAtThePlaneAddress() =
        runTest {
            val member = memberView()

            // The only stream key a member holds is the community_root-derived read key. Signing
            // with it produces a wrap at the WRONG address — the spam gate the split exists for.
            val forged = ConcordStreamEnvelope.wrap(edition(), member.readKey, owner, encrypted = false, createdAt = now)
            assertNotEquals(member.address, forged.pubKey)
            assertNull(ConcordStreamEnvelope.openOrNull(forged, member), "a member-signed wrap must not open at the plane")
            assertNull(ConcordStreamEnvelope.openOrNull(forged, staffView()))
        }

    @Test
    fun aWrapFromAnUnrelatedKeyIsRefusedAtTheAddressCheck() =
        runTest {
            val staff = staffView()
            // A spammer who somehow learned the read key still cannot mint at the address: the
            // wrap's author must BE the address, and only control_root holders can produce it.
            val strangerSecret = RandomInstance.bytes(32)
            val stranger = ConcordKeyDerivation.groupKey("concord/whatever", strangerSecret, communityId, epoch)
            val forged = ConcordStreamEnvelope.wrapSeal(ConcordStreamEnvelope.seal(edition(), staff.readKey, owner, encrypted = false), stranger, staff.readKey.conversationKey, createdAt = now)

            assertNull(ConcordStreamEnvelope.openOrNull(forged, staff))
            assertNull(ConcordStreamEnvelope.openOrNull(forged, memberView()))
        }

    @Test
    fun wrappingWithoutTheWriteKeyIsRefusedRatherThanSilentlyMissigned() =
        runTest {
            val member = memberView()
            val seal = ConcordStreamEnvelope.seal(edition(), member.readKey, owner, encrypted = false)
            var threw = false
            try {
                ConcordStreamEnvelope.wrapSeal(seal, member)
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue(threw, "wrapping on a plane we cannot write to must fail loudly")
        }

    @Test
    fun aLegacyEpochStaysReadableAfterTheSplitExists() =
        runTest {
            // A Community minted before the split keyed its plane by the member-held derivation.
            // A client MUST retain that reading (CORD-02 §5) — the upgrade is the next Refounding.
            val legacy = ControlPlaneKeys.legacy(communityRoot, communityId, epoch)
            val wrap = ConcordStreamEnvelope.wrap(edition(), legacy, owner, encrypted = false, createdAt = now)

            val opened = ConcordStreamEnvelope.openOrNull(wrap, legacy)
            assertNotNull(opened)
            assertEquals(owner.pubKey, opened.author)

            // And it does not leak into the split scheme: the split plane refuses it.
            assertNull(ConcordStreamEnvelope.openOrNull(wrap, staffView()))
        }

    @Test
    fun heldSecretsSelectTheViewOfAnEpoch() {
        val staffAddress = staffView().address

        // Holding the secret: staff view, write key derived.
        val asStaff = ControlPlaneKeys.of(communityRoot, communityId, epoch, controlPk = staffAddress, controlRoot = controlRoot.toHex())
        assertTrue(asStaff.canWrite)
        assertEquals(staffAddress, asStaff.address)

        // Holding only the address: member view, read-only.
        val asMember = ControlPlaneKeys.of(communityRoot, communityId, epoch, controlPk = staffAddress)
        assertFalse(asMember.canWrite)
        assertEquals(staffAddress, asMember.address)

        // Holding neither: a legacy, pre-split epoch.
        val asLegacy = ControlPlaneKeys.of(communityRoot, communityId, epoch)
        assertTrue(asLegacy.legacy)
        assertNotEquals(staffAddress, asLegacy.address)
    }

    private fun assertContentEqualsHex(
        a: ByteArray,
        b: ByteArray,
    ) = assertEquals(a.toHex(), b.toHex())

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
