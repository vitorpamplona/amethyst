/**
 * FollowListImporterTest.kt
 *
 * Tests for FollowListImporter covering all identifier types:
 * hex, npub, NIP-05, and Namecoin (.bit / d/ / id/).
 *
 * SPDX-License-Identifier: MIT
 */
package com.vitorpamplona.amethyst.service.followimport

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FollowListImporterTest {

    private val importer = FollowListImporter()

    // ── Hex pubkey resolution ──────────────────────────────────────────

    @Test
    fun `resolves 64-char hex pubkey`() = runBlocking {
        val hex = "b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9"
        val r = importer.resolveIdentifier(hex)
        assertNotNull(r)
        assertEquals(hex, r!!.pubkeyHex)
        assertNull(r.namecoinSource)
    }

    @Test
    fun `lowercases hex pubkey`() = runBlocking {
        val hex = "B0635D6A9851D3AED0CD6C495B282167ACF761729078D975FC341B22650B07B9"
        assertEquals(hex.lowercase(), importer.resolveIdentifier(hex)!!.pubkeyHex)
    }

    @Test
    fun `rejects short hex`() = runBlocking {
        assertNull(importer.resolveIdentifier("abcdef"))
    }

    // ── npub resolution ────────────────────────────────────────────────

    @Test
    fun `resolves valid npub`() = runBlocking {
        val npub = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
        val r = importer.resolveIdentifier(npub)
        assertNotNull(r)
        assertEquals(64, r!!.pubkeyHex.length)
        assertTrue(r.pubkeyHex.matches(Regex("^[0-9a-f]{64}$")))
        assertNull(r.namecoinSource)
    }

    @Test
    fun `rejects invalid npub`() = runBlocking {
        assertNull(importer.resolveIdentifier("npub1invalid"))
    }

    // ── NIP-05 resolution ──────────────────────────────────────────────

    @Test
    fun `delegates NIP-05 to callback`() = runBlocking {
        val expected = "aaaa000000000000000000000000000000000000000000000000000000000001"
        val r = importer.resolveIdentifier("[email protected]", resolveNip05 = { expected })
        assertNotNull(r)
        assertEquals(expected, r!!.pubkeyHex)
        assertNull(r.namecoinSource)
    }

    @Test
    fun `returns null for NIP-05 without resolver`() = runBlocking {
        assertNull(importer.resolveIdentifier("[email protected]"))
    }

    // ── nsec rejection ─────────────────────────────────────────────────

    @Test
    fun `rejects nsec private keys`() = runBlocking {
        assertNull(importer.resolveIdentifier(
            "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5",
            resolveNip05 = { "should-not-be-called" }
        ))
    }

    // ── Namecoin identifier detection ──────────────────────────────────

    @Test
    fun `identifies dot-bit as Namecoin`() {
        assertTrue(com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver.isNamecoinIdentifier("example.bit"))
        assertTrue(com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver.isNamecoinIdentifier("alice@example.bit"))
        assertTrue(com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver.isNamecoinIdentifier("_@example.bit"))
    }

    @Test
    fun `identifies d-slash as Namecoin`() {
        assertTrue(com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver.isNamecoinIdentifier("d/example"))
    }

    @Test
    fun `identifies id-slash as Namecoin`() {
        assertTrue(com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver.isNamecoinIdentifier("id/alice"))
    }

    @Test
    fun `rejects non-Namecoin identifiers`() {
        assertFalse(com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver.isNamecoinIdentifier("[email protected]"))
        assertFalse(com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver.isNamecoinIdentifier("npub1abc"))
        assertFalse(com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver.isNamecoinIdentifier(""))
    }

    // ── Kind 3 parsing ─────────────────────────────────────────────────

    @Test
    fun `parses kind 3 p-tags with relay hints and petnames`() = runBlocking {
        val target = "b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9"
        val followA = "aaaa000000000000000000000000000000000000000000000000000000000001"
        val followB = "bbbb000000000000000000000000000000000000000000000000000000000002"

        val result = importer.fetchFollowList(
            identifier = target,
            relayUrls = listOf("wss://test"),
            fetchEvent = { kind, author, _, onEvent ->
                assertEquals(3, kind)
                assertEquals(target, author)
                onEvent(Kind3EventData(
                    pTags = listOf(
                        listOf(followA, "wss://relay.example.com", "alice"),
                        listOf(followB, "", "bob"),
                    ),
                    createdAt = 1700000000L,
                ))
                AutoCloseable {}
            },
        )

        assertTrue(result is FollowListResult.Success)
        val s = result as FollowListResult.Success
        assertEquals(2, s.follows.size)
        assertEquals(followA, s.follows[0].pubkeyHex)
        assertEquals("wss://relay.example.com", s.follows[0].relayHint)
        assertEquals("alice", s.follows[0].petname)
        assertEquals(followB, s.follows[1].pubkeyHex)
        assertNull(s.follows[1].relayHint)
        assertEquals("bob", s.follows[1].petname)
        assertNull(s.resolvedViaNamecoin)
    }

    @Test
    fun `deduplicates follows`() = runBlocking {
        val pk = "aaaa000000000000000000000000000000000000000000000000000000000001"
        val result = importer.fetchFollowList(
            identifier = pk, relayUrls = listOf("wss://t"),
            fetchEvent = { _, _, _, onEvent ->
                onEvent(Kind3EventData(pTags = listOf(listOf(pk), listOf(pk)), createdAt = 1L))
                AutoCloseable {}
            },
        )
        assertEquals(1, (result as FollowListResult.Success).follows.size)
    }

    @Test
    fun `skips invalid pubkeys in p-tags`() = runBlocking {
        val result = importer.fetchFollowList(
            identifier = "aaaa000000000000000000000000000000000000000000000000000000000001",
            relayUrls = listOf("wss://t"),
            fetchEvent = { _, _, _, onEvent ->
                onEvent(Kind3EventData(
                    pTags = listOf(listOf("tooshort"), listOf(""), listOf("zzzz" + "0".repeat(60))),
                    createdAt = 1L,
                ))
                AutoCloseable {}
            },
        )
        assertEquals(0, (result as FollowListResult.Success).follows.size)
    }

    @Test
    fun `returns NoFollowList on timeout`() = runBlocking {
        val result = importer.fetchFollowList(
            identifier = "aaaa000000000000000000000000000000000000000000000000000000000001",
            relayUrls = listOf("wss://t"),
            fetchEvent = { _, _, _, _ -> AutoCloseable {} },
            timeoutMs = 200,
        )
        assertTrue(result is FollowListResult.NoFollowList)
    }

    @Test
    fun `returns Error on fetch exception`() = runBlocking {
        val result = importer.fetchFollowList(
            identifier = "aaaa000000000000000000000000000000000000000000000000000000000001",
            relayUrls = listOf("wss://t"),
            fetchEvent = { _, _, _, _ -> throw RuntimeException("Connection refused") },
        )
        assertTrue(result is FollowListResult.Error)
        assertTrue((result as FollowListResult.Error).message.contains("Connection refused"))
    }

    // ── Namecoin-specific error messages ───────────────────────────────

    @Test
    fun `gives Namecoin-specific error for dot-bit failure`() = runBlocking {
        // Namecoin resolution will fail because NamecoinNameService is not
        // configured with real servers in a test environment. The importer
        // should give a Namecoin-specific error message.
        val result = importer.fetchFollowList(
            identifier = "nonexistent.bit",
            relayUrls = listOf("wss://test"),
            fetchEvent = { _, _, _, _ -> AutoCloseable {} },
            timeoutMs = 500,
        )
        assertTrue(result is FollowListResult.InvalidIdentifier)
        assertTrue((result as FollowListResult.InvalidIdentifier).reason.contains("Namecoin"))
    }
}
