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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for KeyPackage lifecycle helpers (MIP-00).
 */
class KeyPackageUtilsTest {
    private val testPubKey = "a".repeat(64)
    private val testRef = "b".repeat(64)

    private fun makeKeyPackageEvent(
        dTag: String = "0",
        createdAt: Long = 1000,
        encoding: String = "base64",
        ciphersuite: String = "0x0001",
        keyPackageRef: String = testRef,
        content: String = "dGVzdA==", // base64("test")
    ): KeyPackageEvent =
        KeyPackageEvent(
            id = "e".repeat(64),
            pubKey = testPubKey,
            createdAt = createdAt,
            tags =
                arrayOf(
                    arrayOf("d", dTag),
                    arrayOf("encoding", encoding),
                    arrayOf("mls_ciphersuite", ciphersuite),
                    arrayOf("i", keyPackageRef),
                    arrayOf("mls_protocol_version", "1.0"),
                    arrayOf("mls_extensions", "0xf2ee", "0x000a"),
                    arrayOf("mls_proposals", "0x000a"),
                    arrayOf("relays", "wss://relay.example.com"),
                ),
            content = content,
            sig = "s".repeat(128),
        )

    // ===== isValid =====

    @Test
    fun testIsValid_ValidKeyPackage() {
        val kp = makeKeyPackageEvent()
        assertTrue(KeyPackageUtils.isValid(kp))
    }

    @Test
    fun testIsValid_WrongEncoding() {
        val kp = makeKeyPackageEvent(encoding = "raw")
        assertFalse(KeyPackageUtils.isValid(kp))
    }

    @Test
    fun testIsValid_EmptyContent() {
        val kp = makeKeyPackageEvent(content = "")
        assertFalse(KeyPackageUtils.isValid(kp))
    }

    @Test
    fun testIsValid_MissingRef() {
        val kp = makeKeyPackageEvent(keyPackageRef = "")
        assertFalse(KeyPackageUtils.isValid(kp))
    }

    // ===== selectBest =====

    @Test
    fun testSelectBest_EmptyList() {
        assertNull(KeyPackageUtils.selectBest(emptyList()))
    }

    @Test
    fun testSelectBest_SingleValid() {
        val kp = makeKeyPackageEvent()
        assertEquals(kp, KeyPackageUtils.selectBest(listOf(kp)))
    }

    @Test
    fun testSelectBest_PrefersNewest() {
        val old = makeKeyPackageEvent(dTag = "0", createdAt = 1000)
        val newer = makeKeyPackageEvent(dTag = "1", createdAt = 2000)

        val best = KeyPackageUtils.selectBest(listOf(old, newer))
        assertNotNull(best)
        assertEquals(2000, best.createdAt)
    }

    @Test
    fun testSelectBest_PrefersNonLastResort() {
        val lastResort = makeKeyPackageEvent(dTag = "lr", createdAt = 3000)
        val regular = makeKeyPackageEvent(dTag = "0", createdAt = 1000)

        // Even though lastResort is newer, regular is preferred
        val best = KeyPackageUtils.selectBest(listOf(lastResort, regular), lastResortDTag = "lr")
        assertNotNull(best)
        assertEquals("0", best.dTag())
    }

    @Test
    fun testSelectBest_FallsBackToLastResort() {
        val lastResort = makeKeyPackageEvent(dTag = "lr", createdAt = 3000)

        // Only last-resort available
        val best = KeyPackageUtils.selectBest(listOf(lastResort), lastResortDTag = "lr")
        assertNotNull(best)
        assertEquals("lr", best.dTag())
    }

    @Test
    fun testSelectBest_FiltersOutInvalid() {
        val invalid = makeKeyPackageEvent(dTag = "0", encoding = "raw")
        val valid = makeKeyPackageEvent(dTag = "1", createdAt = 500)

        val best = KeyPackageUtils.selectBest(listOf(invalid, valid))
        assertNotNull(best)
        assertEquals("1", best.dTag())
    }

    @Test
    fun testSelectBest_AllInvalid() {
        val invalid1 = makeKeyPackageEvent(encoding = "raw")
        val invalid2 = makeKeyPackageEvent(content = "")

        assertNull(KeyPackageUtils.selectBest(listOf(invalid1, invalid2)))
    }

    // ===== buildRotation =====

    @Test
    fun testBuildRotation() {
        val template =
            KeyPackageUtils.buildRotation(
                newKeyPackageBase64 = "bmV3IGtleXBhY2thZ2U=",
                dTagSlot = "0",
                newKeyPackageRef = testRef,
                relays = emptyList(),
            )

        assertEquals(KeyPackageEvent.KIND, template.kind)
        assertEquals("bmV3IGtleXBhY2thZ2U=", template.content)
    }

    // ===== migration helpers =====

    @Test
    fun testIsKeyPackageKind() {
        val addressable =
            Event(
                id = "e".repeat(64),
                pubKey = testPubKey,
                createdAt = 1000,
                kind = 30443,
                tags = emptyArray(),
                content = "",
                sig = "",
            )
        val legacy =
            Event(
                id = "e".repeat(64),
                pubKey = testPubKey,
                createdAt = 1000,
                kind = 443,
                tags = emptyArray(),
                content = "",
                sig = "",
            )
        val other =
            Event(
                id = "e".repeat(64),
                pubKey = testPubKey,
                createdAt = 1000,
                kind = 1,
                tags = emptyArray(),
                content = "",
                sig = "",
            )

        assertTrue(KeyPackageUtils.isKeyPackageKind(addressable))
        assertTrue(KeyPackageUtils.isKeyPackageKind(legacy))
        assertFalse(KeyPackageUtils.isKeyPackageKind(other))
    }

    @Test
    fun testMigrationKinds() {
        val kinds = KeyPackageUtils.migrationKinds()
        assertTrue(kinds.contains(30443))
        assertTrue(kinds.contains(443))
        assertEquals(2, kinds.size)
    }
}
