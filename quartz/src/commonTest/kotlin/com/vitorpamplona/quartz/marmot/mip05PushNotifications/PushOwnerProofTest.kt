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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `features/push-notifications.md`, "Owner authentication".
 *
 * The spec publishes a complete removal fixture: the canonical NIP-01
 * serialization, the resulting event id, and the `owner_sig` a known secret
 * produces over it. Reproducing that id is what proves our tag order, arity and
 * value formatting match — get any of them wrong and the id changes, the
 * signature stops verifying, and every peer silently drops the record.
 */
class PushOwnerProofTest {
    private val member = "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
    private val groupId = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
    private val serverPubKey = "2f8bde4d1a07209355b4a7250a5c5128e88b84bddc619ab7cba8d569b240efe4"
    private val fingerprint = "sha256:000102030405060708090a0b"
    private val ownerTs = 1700000000000L

    private fun removalTags() =
        PushOwnerProof.tags(
            record = PushRecordKind.REMOVAL,
            groupIdHex = groupId,
            memberIdHex = member,
            leafIndex = 3,
            platform = "apns",
            serverPubKeyHex = serverPubKey,
            tokenFingerprint = fingerprint,
            ownerTsMillis = ownerTs,
            relayHint = "",
        )

    @Test
    fun matchesTheSpecPublishedRemovalEventId() {
        assertEquals(
            "be12f4d029d3cac4034251949d6c013ff18eae00870e199012c7a97e8960b7a2",
            PushOwnerProof.eventId(member, removalTags(), "").toHexKey(),
        )
    }

    @Test
    fun verifiesTheSpecPublishedSignature() {
        val ownerSig =
            (
                "04c3588a6533399aeaebb6c596fab896186dd0af1f9724f2926d984d2876490c" +
                    "76e1d149127e0fa697d7f19a0807aa373e942f0eb33edc63071567f274ce3bec"
            ).hexToByteArray()
        assertTrue(
            PushOwnerProof.verifyRecord(
                ownerSig = ownerSig,
                record = PushRecordKind.REMOVAL,
                groupIdHex = groupId,
                memberIdHex = member,
                leafIndex = 3,
                platform = "apns",
                serverPubKeyHex = serverPubKey,
                tokenFingerprint = fingerprint,
                ownerTsMillis = ownerTs,
                currentProfileGroup = true,
            ),
        )
    }

    /**
     * The point of binding all that context: a member who merely RELAYS a
     * record cannot move it, repoint it, or restamp it. Each of these changes
     * one signed field, so the id changes and the signature stops verifying.
     */
    @Test
    fun aRelayingMemberCannotRepointTheRecord() {
        val ownerSig =
            (
                "04c3588a6533399aeaebb6c596fab896186dd0af1f9724f2926d984d2876490c" +
                    "76e1d149127e0fa697d7f19a0807aa373e942f0eb33edc63071567f274ce3bec"
            ).hexToByteArray()

        fun verifyWith(
            gid: String = groupId,
            server: String = serverPubKey,
            ts: Long = ownerTs,
            leaf: Int = 3,
        ) = PushOwnerProof.verifyRecord(
            ownerSig = ownerSig,
            record = PushRecordKind.REMOVAL,
            groupIdHex = gid,
            memberIdHex = member,
            leafIndex = leaf,
            platform = "apns",
            serverPubKeyHex = server,
            tokenFingerprint = fingerprint,
            ownerTsMillis = ts,
            currentProfileGroup = true,
        )

        assertTrue(verifyWith(), "the unmodified record still verifies")
        assertFalse(verifyWith(gid = "ff".repeat(32)), "moved to another group")
        assertFalse(verifyWith(server = "ee".repeat(32)), "repointed at another server")
        assertFalse(verifyWith(ts = ownerTs + 1), "restamped")
        assertFalse(verifyWith(leaf = 4), "attributed to another leaf")
    }

    /**
     * A current-profile group refuses the legacy kind-450 form. Accepting it
     * would let anyone able to produce the weaker proof bypass the stronger
     * binding such a group already guarantees.
     */
    @Test
    fun aCurrentProfileGroupRefusesTheLegacyProofForm() {
        // Not a real legacy signature — the point is which kind is tried, and a
        // current-profile group must not fall back at all.
        val notASignature = ByteArray(64)
        assertFalse(
            PushOwnerProof.verifyRecord(
                ownerSig = notASignature,
                record = PushRecordKind.REMOVAL,
                groupIdHex = groupId,
                memberIdHex = member,
                leafIndex = 3,
                platform = "apns",
                serverPubKeyHex = serverPubKey,
                tokenFingerprint = fingerprint,
                ownerTsMillis = ownerTs,
                currentProfileGroup = true,
            ),
        )
    }

    /** A removal carries no relay hint and no token, whatever the caller passes. */
    @Test
    fun aLegacyGroupAcceptsTheRawDigestProofAndACurrentOneDoesNot() {
        // The oldest form: a signature straight over SHA-256(SignedRecord),
        // with no event around it. Verification-only and never produced — but a
        // legacy group can still hold a member that only ever made these, and
        // refusing them there would silently strand that member's routing.
        val priv = ByteArray(32).also { it[31] = 3 }
        val signedRecord = {
            PushSignedRecord.encode(
                record = PushRecordKind.REMOVAL,
                groupIdHex = groupId,
                memberIdHex = member,
                leafIndex = 3,
                platform = PushPlatform.APNS,
                serverPubKeyHex = serverPubKey,
                tokenFingerprint = fingerprint,
                ownerTsMillis = ownerTs,
            )
        }
        val ownerSig = Nip01Crypto.sign(sha256(signedRecord()), priv)

        fun verify(currentProfileGroup: Boolean) =
            PushOwnerProof.verifyRecord(
                ownerSig = ownerSig,
                record = PushRecordKind.REMOVAL,
                groupIdHex = groupId,
                memberIdHex = member,
                leafIndex = 3,
                platform = "apns",
                serverPubKeyHex = serverPubKey,
                tokenFingerprint = fingerprint,
                ownerTsMillis = ownerTs,
                currentProfileGroup = currentProfileGroup,
                signedRecord = signedRecord,
            )

        assertTrue(verify(currentProfileGroup = false))
        // In a group where every leaf already carries a 0x8009 identity proof,
        // accepting the weaker form would throw away a binding the group
        // otherwise guarantees.
        assertFalse(verify(currentProfileGroup = true))
        // And without the canonical bytes there is nothing to check it against,
        // so a caller that does not supply them simply gets a no.
        assertFalse(
            PushOwnerProof.verifyRecord(
                ownerSig = ownerSig,
                record = PushRecordKind.REMOVAL,
                groupIdHex = groupId,
                memberIdHex = member,
                leafIndex = 3,
                platform = "apns",
                serverPubKeyHex = serverPubKey,
                tokenFingerprint = fingerprint,
                ownerTsMillis = ownerTs,
                currentProfileGroup = false,
            ),
        )
    }

    @Test
    fun aRemovalAlwaysEncodesAnEmptyRelayHint() {
        val withHint =
            PushOwnerProof.tags(
                record = PushRecordKind.REMOVAL,
                groupIdHex = groupId,
                memberIdHex = member,
                leafIndex = 3,
                platform = "apns",
                serverPubKeyHex = serverPubKey,
                tokenFingerprint = fingerprint,
                ownerTsMillis = ownerTs,
                relayHint = "wss://relay.example.com",
            )
        assertEquals(listOf("relay_hint", ""), withHint.first { it[0] == "relay_hint" }.toList())
        assertFalse(withHint.any { it[0] == "encrypted_token_encoding" })
    }

    /** A token record adds the encoding tag; the order is fixed by the spec. */
    @Test
    fun aTokenRecordCarriesTheEncodingTagLast() {
        val tokenTags =
            PushOwnerProof.tags(
                record = PushRecordKind.TOKEN,
                groupIdHex = groupId,
                memberIdHex = member,
                leafIndex = 0,
                platform = "fcm",
                serverPubKeyHex = serverPubKey,
                tokenFingerprint = fingerprint,
                ownerTsMillis = ownerTs,
                relayHint = "wss://relay.example.com",
            )
        assertEquals(
            listOf(
                "d",
                "group_id",
                "member_id",
                "leaf_index",
                "platform",
                "server_pubkey",
                "token_fingerprint",
                "owner_ts",
                "relay_hint",
                "encrypted_token_encoding",
            ),
            tokenTags.map { it[0] },
        )
        assertEquals("marmot-push-token-record-v1", tokenTags[0][1])
        // Canonical decimal ASCII: zero is "0", never "00" or "".
        assertEquals("0", tokenTags.first { it[0] == "leaf_index" }[1])
    }
}
