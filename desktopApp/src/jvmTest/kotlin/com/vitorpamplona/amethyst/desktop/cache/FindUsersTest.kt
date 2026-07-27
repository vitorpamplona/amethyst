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
package com.vitorpamplona.amethyst.desktop.cache

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FindUsersTest {
    private fun createCache() = DesktopLocalCache()

    /**
     * [DesktopLocalCache] backs its user store with [com.vitorpamplona.amethyst.commons.model.cache.LargeSoftCache],
     * which holds each [User] via a `WeakReference`. Unlike the production app — where Notes,
     * the Account, and follow lists keep authors strongly reachable — these tests populate the
     * cache and hold no other reference to the created users, so a GC between `consumeMetadata`
     * and `findUsersStartingWith` could collect them and make the search return fewer results
     * (flaky failure at line 115). Every test keeps its users in a strong-reference list that
     * stays reachable through the assertions to pin them in the cache.
     */
    private val retained = mutableListOf<User?>()

    private fun DesktopLocalCache.retainUser(pubkey: String): User? = getUserIfExists(pubkey).also { retained.add(it) }

    private fun fakeMetadata(
        pubKey: String,
        name: String,
        displayName: String = name,
    ): MetadataEvent =
        MetadataEvent(
            id = (pubKey.take(16) + "meta").padEnd(64, '0'),
            pubKey = pubKey,
            createdAt = System.currentTimeMillis() / 1000,
            tags = emptyArray(),
            content = """{"name":"$name","display_name":"$displayName"}""",
            sig = "0".repeat(128),
        )

    @Test
    fun userWithoutMetadataNotFoundByName() {
        val cache = createCache()
        val pubkey = KeyPair().pubKey.toHexKey()

        retained.add(cache.getOrCreateUser(pubkey))

        val results = cache.findUsersStartingWith("test", 10)
        assertTrue(results.isEmpty(), "User without metadata should not match name search")
    }

    @Test
    fun userWithMetadataFoundByDisplayName() {
        val cache = createCache()
        val pubkey = KeyPair().pubKey.toHexKey()

        cache.consumeMetadata(fakeMetadata(pubkey, "vitor", "Vitor Pamplona"))
        cache.retainUser(pubkey)

        val results = cache.findUsersStartingWith("Vitor", 10)
        assertEquals(1, results.size, "Should find user by display name")
        assertEquals(pubkey, results[0].pubkeyHex)
    }

    @Test
    fun userWithMetadataFoundByName() {
        val cache = createCache()
        val pubkey = KeyPair().pubKey.toHexKey()

        cache.consumeMetadata(fakeMetadata(pubkey, "vitor"))
        cache.retainUser(pubkey)

        val results = cache.findUsersStartingWith("vit", 10)
        assertEquals(1, results.size, "Should find user by name prefix")
    }

    @Test
    fun userWithMetadataFoundCaseInsensitive() {
        val cache = createCache()
        val pubkey = KeyPair().pubKey.toHexKey()

        cache.consumeMetadata(fakeMetadata(pubkey, "Vitor", "Vitor Pamplona"))
        cache.retainUser(pubkey)

        val lower = cache.findUsersStartingWith("vitor", 10)
        assertEquals(1, lower.size, "Should find case-insensitively (lowercase)")

        val upper = cache.findUsersStartingWith("VITOR", 10)
        assertEquals(1, upper.size, "Should find case-insensitively (uppercase)")
    }

    @Test
    fun userWithoutMetadataFoundByPubkey() {
        val cache = createCache()
        val pubkey = KeyPair().pubKey.toHexKey()

        retained.add(cache.getOrCreateUser(pubkey))

        val results = cache.findUsersStartingWith(pubkey.take(8), 10)
        assertEquals(1, results.size, "Should find user by pubkey prefix")
    }

    @Test
    fun multipleUsersWithMetadata() {
        val cache = createCache()

        listOf("alice", "bob", "alex").forEach { name ->
            val pubkey = KeyPair().pubKey.toHexKey()
            cache.consumeMetadata(fakeMetadata(pubkey, name))
            cache.retainUser(pubkey)
        }

        val results = cache.findUsersStartingWith("al", 10)
        assertEquals(2, results.size, "Should find alice and alex")
    }

    @Test
    fun usersFromNotesWithoutMetadataNotMatchNameSearch() {
        val cache = createCache()

        // Simulate users created from kind 1 notes (no metadata)
        repeat(10) { retained.add(cache.getOrCreateUser(KeyPair().pubKey.toHexKey())) }

        assertEquals(10, cache.userCount())

        val results = cache.findUsersStartingWith("test", 10)
        assertEquals(0, results.size, "Users without metadata should not match name search")
    }

    @Test
    fun verifyMetadataIsActuallyParsed() {
        val cache = createCache()
        val pubkey = KeyPair().pubKey.toHexKey()

        cache.consumeMetadata(fakeMetadata(pubkey, "testuser", "Test User"))

        val user = cache.retainUser(pubkey)
        val metadata = user?.metadataOrNull()

        assertNotNull(metadata, "Metadata should exist after consumeMetadata")
        assertTrue(
            metadata.anyNameOrAddressContains(
                listOf(
                    com.vitorpamplona.quartz.utils
                        .DualCase("test", "TEST"),
                ),
            ),
            "Metadata should match 'test' search",
        )
    }
}
