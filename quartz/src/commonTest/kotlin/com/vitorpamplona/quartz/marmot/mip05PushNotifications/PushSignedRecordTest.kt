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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * `features/push-notifications.md`, "Canonical record bytes".
 *
 * The layout is asserted field by field against bytes assembled by hand,
 * because every alternative encoding this codebase already owns would look
 * correct locally and be wrong on the wire. The spec is explicit about it:
 * "Signers and verifiers MUST NOT substitute QUIC varints, TLS vectors, or a
 * serialization-library default."
 */
class PushSignedRecordTest {
    private val groupId = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
    private val member = "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
    private val server = "2f8bde4d1a07209355b4a7250a5c5128e88b84bddc619ab7cba8d569b240efe4"
    private val fingerprint = "sha256:000102030405060708090a0b"
    private val ownerTs = 1700000000000L
    private val token = ByteArray(PushSignedRecord.ENCRYPTED_TOKEN_BYTES) { (it % 251).toByte() }

    @Test
    fun aRemovalRecordIsExactlyTheFieldsInOrder() {
        val bytes =
            PushSignedRecord.encode(
                record = PushRecordKind.REMOVAL,
                groupIdHex = groupId,
                memberIdHex = member,
                leafIndex = 3,
                platform = PushPlatform.APNS,
                serverPubKeyHex = server,
                tokenFingerprint = fingerprint,
                ownerTsMillis = ownerTs,
            )

        val expected =
            "marmot-push-token-removal-v1".encodeToByteArray().toHexKey() +
                // group_id_len = 32, big-endian u16, NOT a varint
                "0020" + groupId +
                member +
                // leaf_index = 3 as u32
                "00000003" +
                // platform apns
                "01" +
                server +
                // token_fingerprint, the 12 bytes the sha256: prefix encodes
                "000102030405060708090a0b" +
                // owner_ts as u64 milliseconds
                "0000018bcfe56800" +
                // relay_hint_len = 0, and no encrypted_token at all
                "0000"

        assertEquals(expected, bytes.toHexKey())
    }

    @Test
    fun aTokenRecordAppendsTheHintAndTheWholeToken() {
        val bytes =
            PushSignedRecord.encode(
                record = PushRecordKind.TOKEN,
                groupIdHex = groupId,
                memberIdHex = member,
                leafIndex = 3,
                platform = PushPlatform.FCM,
                serverPubKeyHex = server,
                tokenFingerprint = fingerprint,
                ownerTsMillis = ownerTs,
                relayHint = "wss://relay.example.com",
                encryptedToken = token,
            )

        val hint = "wss://relay.example.com"
        val expected =
            "marmot-push-token-record-v1".encodeToByteArray().toHexKey() +
                "0020" + groupId +
                member +
                "00000003" +
                "02" +
                server +
                "000102030405060708090a0b" +
                "0000018bcfe56800" +
                // relay_hint_len = 23
                "0017" + hint.encodeToByteArray().toHexKey() +
                token.toHexKey()

        assertEquals(expected, bytes.toHexKey())
    }

    @Test
    fun aWhitespaceOnlyHintSignsAsAbsent() {
        // The signer and the verifier have to agree, and JSON round-trips can
        // pick up padding. Normalizing on both sides is the only way an entry
        // that means "no hint" verifies wherever it lands.
        val blank =
            PushSignedRecord.encode(
                PushRecordKind.TOKEN,
                groupId,
                member,
                0,
                PushPlatform.APNS,
                server,
                fingerprint,
                ownerTs,
                "   ",
                token,
            )
        val absent =
            PushSignedRecord.encode(
                PushRecordKind.TOKEN,
                groupId,
                member,
                0,
                PushPlatform.APNS,
                server,
                fingerprint,
                ownerTs,
                "",
                token,
            )
        assertEquals(absent.toHexKey(), blank.toHexKey())
    }

    @Test
    fun aRemovalAndATokenRecordNeverCollide() {
        // Distinct domain tags, a zeroed hint length and the missing token all
        // pull in the same direction: one signature can never be replayed as
        // the other shape.
        val removal =
            PushSignedRecord.encode(
                PushRecordKind.REMOVAL,
                groupId,
                member,
                0,
                PushPlatform.APNS,
                server,
                fingerprint,
                ownerTs,
            )
        val record =
            PushSignedRecord.encode(
                PushRecordKind.TOKEN,
                groupId,
                member,
                0,
                PushPlatform.APNS,
                server,
                fingerprint,
                ownerTs,
                "",
                token,
            )
        assertNotEquals(removal.toHexKey(), record.toHexKey())
    }

    @Test
    fun theFingerprintIsTheFirstTwelveBytesOfThePlatformPrefixedHash() {
        val deviceToken = "a-device-token".encodeToByteArray()
        val fingerprint = PushSignedRecord.fingerprintOf(PushPlatform.APNS, deviceToken)
        assertEquals(PushSignedRecord.FINGERPRINT_PREFIX, fingerprint.substring(0, 7))
        assertEquals(PushSignedRecord.FINGERPRINT_HEX_LENGTH, fingerprint.length - 7)
        // The platform byte is in the preimage, so the same raw token on two
        // platforms names two different records.
        assertNotEquals(fingerprint, PushSignedRecord.fingerprintOf(PushPlatform.FCM, deviceToken))
        assertEquals(
            fingerprint.substring(7).hexToByteArray().toHexKey(),
            PushSignedRecord.fingerprintBytes(fingerprint)?.toHexKey(),
        )
    }

    @Test
    fun aMalformedFingerprintIsNotBytes() {
        assertNull(PushSignedRecord.fingerprintBytes("000102030405060708090a0b"))
        assertNull(PushSignedRecord.fingerprintBytes("sha256:000102030405060708090a"))
        assertNull(PushSignedRecord.fingerprintBytes("sha256:000102030405060708090A0B"))
        assertNull(PushSignedRecord.fingerprintBytes("sha256:00010203040506070809zzzz"))
    }
}
