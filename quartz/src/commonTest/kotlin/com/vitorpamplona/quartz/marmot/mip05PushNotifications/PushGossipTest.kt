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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `features/push-notifications.md`, "Token gossip event shapes" and
 * "Validation".
 *
 * Everything push decodes is advisory, so the whole surface here is about
 * DROPPING things quietly and correctly: a bad entry goes, the rest of the
 * array stays, and the group message that carried it is never in question.
 */
class PushGossipTest {
    private val member = "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
    private val server = "2f8bde4d1a07209355b4a7250a5c5128e88b84bddc619ab7cba8d569b240efe4"
    private val fingerprint = "sha256:000102030405060708090a0b"
    private val sig = "11".repeat(64)
    private val token = PushBase64.encode(ByteArray(PushSignedRecord.ENCRYPTED_TOKEN_BYTES) { it.toByte() })

    private fun tokenJson(
        overrides: Map<String, String> = emptyMap(),
        drop: Set<String> = emptySet(),
    ): String {
        val members =
            linkedMapOf(
                "member_id_hex" to "\"$member\"",
                "leaf_index" to "3",
                "platform" to "\"apns\"",
                "token_fingerprint" to "\"$fingerprint\"",
                "server_pubkey_hex" to "\"$server\"",
                "encrypted_token" to "\"$token\"",
                "owner_ts" to "1735680000000",
                "owner_sig" to "\"$sig\"",
            )
        overrides.forEach { (k, v) -> members[k] = v }
        drop.forEach { members.remove(it) }
        val entry = members.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
        return """{"v":"marmot-push-v1","tokens":[{$entry}]}"""
    }

    @Test
    fun aWellFormedEntryRoundTrips() {
        val entries = PushGossip.decodeTokens(tokenJson())
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(member, entry.memberIdHex)
        assertEquals(3, entry.leafIndex)
        assertEquals(PushPlatform.APNS, entry.platform)
        assertEquals(1735680000000L, entry.ownerTsMillis)
        assertEquals(PushSignedRecord.ENCRYPTED_TOKEN_BYTES, entry.encryptedToken.size)
        assertEquals(entries, PushGossip.decodeTokens(PushGossip.encodeTokens(entries)))
    }

    @Test
    fun anAbsentHintSurvivesTheRoundTripAsAbsent() {
        // "" and omitted have to mean the same thing on both sides, because the
        // owner proof signed one of them and a verifier reconstructs the other.
        val withBlank = PushGossip.decodeTokens(tokenJson(mapOf("relay_hint" to "\"   \""))).single()
        assertEquals("", withBlank.relayHint)
        val reEncoded = PushGossip.encodeTokens(listOf(withBlank))
        assertFalse(reEncoded.contains("relay_hint"))
    }

    @Test
    fun onlyTheAdoptedVersionIsRead() {
        // The earlier exploratory shape is not a compatible predecessor, and
        // reading it would mean reading records that predate owner
        // authentication entirely.
        assertTrue(PushGossip.decodeTokens(tokenJson().replace("marmot-push-v1", "mip05-v1")).isEmpty())
        assertFalse(PushGossip.isSupportedVersion("""{"v":"mip05-v1","tokens":[]}"""))
        assertTrue(PushGossip.isSupportedVersion("""{"v":"marmot-push-v1"}"""))
    }

    @Test
    fun aMissingTokensMemberIsAnEmptyArray() {
        assertTrue(PushGossip.decodeTokens("""{"v":"marmot-push-v1"}""").isEmpty())
        // A present non-array member contributes no entries either.
        assertTrue(PushGossip.decodeTokens("""{"v":"marmot-push-v1","tokens":{}}""").isEmpty())
    }

    @Test
    fun anOversizedArrayIsInvalidInItsEntirety() {
        // Not "the first 32 apply" — the whole array goes, BEFORE any signature
        // is verified, so an oversized array cannot buy unbounded verification.
        val entry = tokenJson().substringAfter("\"tokens\":[").removeSuffix("]}")
        val thirtyThree = """{"v":"marmot-push-v1","tokens":[${List(33) { entry }.joinToString(",")}]}"""
        assertTrue(PushGossip.decodeTokens(thirtyThree).isEmpty())
    }

    @Test
    fun aMalformedEntryIsDroppedOnItsOwn() {
        val good = tokenJson().substringAfter("\"tokens\":[").removeSuffix("]}")
        val bad = good.replace("\"$member\"", "\"not-hex\"")
        val mixed = """{"v":"marmot-push-v1","tokens":[$bad,$good]}"""
        assertEquals(1, PushGossip.decodeTokens(mixed).size)
    }

    @Test
    fun everyPerFieldRuleRejectsItsOwnEntry() {
        // Uppercase hex is rejected: the spec says lowercase, and a
        // case-insensitive reader would derive a different `member_id_hex`
        // string for the same key.
        assertTrue(PushGossip.decodeTokens(tokenJson(mapOf("member_id_hex" to "\"${member.uppercase()}\""))).isEmpty())
        assertTrue(PushGossip.decodeTokens(tokenJson(mapOf("server_pubkey_hex" to "\"abc\""))).isEmpty())
        assertTrue(PushGossip.decodeTokens(tokenJson(mapOf("platform" to "\"web\""))).isEmpty())
        assertTrue(PushGossip.decodeTokens(tokenJson(mapOf("token_fingerprint" to "\"sha256:00\""))).isEmpty())
        assertTrue(PushGossip.decodeTokens(tokenJson(mapOf("owner_sig" to "\"${"11".repeat(63)}\""))).isEmpty())
        assertTrue(PushGossip.decodeTokens(tokenJson(mapOf("owner_ts" to "-1"))).isEmpty())
        // A token that decodes to the wrong length is not an EncryptedToken.
        assertTrue(PushGossip.decodeTokens(tokenJson(mapOf("encrypted_token" to "\"${PushBase64.encode(ByteArray(10))}\""))).isEmpty())
        assertTrue(PushGossip.decodeTokens(tokenJson(drop = setOf("leaf_index"))).isEmpty())
    }

    @Test
    fun aNumericStringIsNotANumber() {
        // `"leaf_index": "3"` is a different document from `"leaf_index": 3`.
        // Accepting both would let two senders agree on meaning and disagree on
        // the digest that breaks their ordering ties.
        assertTrue(PushGossip.decodeTokens(tokenJson(mapOf("leaf_index" to "\"3\""))).isEmpty())
        assertTrue(PushGossip.decodeTokens(tokenJson(mapOf("owner_ts" to "\"1735680000000\""))).isEmpty())
    }

    @Test
    fun unknownMembersAreIgnored() {
        assertEquals(1, PushGossip.decodeTokens(tokenJson(mapOf("something_new" to "\"whatever\""))).size)
    }

    @Test
    fun aRemovalCarriesNoHintAndNoToken() {
        val removals =
            PushGossip.decodeRemovals(
                """
                {"v":"marmot-push-v1","removals":[{
                  "member_id_hex":"$member","leaf_index":3,"platform":"fcm",
                  "token_fingerprint":"$fingerprint","server_pubkey_hex":"$server",
                  "owner_ts":1735680000000,"owner_sig":"$sig"}]}
                """.trimIndent(),
            )
        assertEquals(1, removals.size)
        val encoded = PushGossip.encodeRemovals(removals)
        assertFalse(encoded.contains("encrypted_token"))
        assertFalse(encoded.contains("relay_hint"))
        assertEquals(removals, PushGossip.decodeRemovals(encoded))
    }

    @Test
    fun garbageIsNotAnError() {
        assertTrue(PushGossip.decodeTokens("not json at all").isEmpty())
        assertTrue(PushGossip.decodeTokens("[]").isEmpty())
        assertTrue(PushGossip.decodeRemovals("").isEmpty())
        assertNull(null)
    }

    @Test
    fun anEmptyRequestIsAWellFormedEmptyArray() {
        val request = PushGossip.encodeRequest()
        assertTrue(PushGossip.isSupportedVersion(request))
        assertTrue(PushGossip.decodeTokens(request).isEmpty())
    }
}
