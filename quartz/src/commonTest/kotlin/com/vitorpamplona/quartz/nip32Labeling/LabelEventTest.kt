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
package com.vitorpamplona.quartz.nip32Labeling

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip32Labeling.tags.LabelTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LabelEventTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val testEventId = "a".repeat(64)
    private val testPubKey = "b".repeat(64)

    @Test
    fun testBuildEventLabel() {
        val labels = listOf(LabelTag("approve", "nip28.moderation"))
        val template =
            LabelEvent.buildEventLabel(
                labeledEventId = testEventId,
                labels = labels,
                content = "Approved for channel",
            )

        assertEquals(1985, template.kind)
        assertEquals("Approved for channel", template.content)

        val hasLabelNamespace = template.tags.any { it[0] == "L" && it[1] == "nip28.moderation" }
        assertTrue(hasLabelNamespace, "Should have L namespace tag")

        val hasLabel = template.tags.any { it[0] == "l" && it[1] == "approve" && it[2] == "nip28.moderation" }
        assertTrue(hasLabel, "Should have l label tag")

        val hasEventRef = template.tags.any { it[0] == "e" && it[1] == testEventId }
        assertTrue(hasEventRef, "Should have e tag targeting labeled event")
    }

    @Test
    fun testBuildPubKeyLabel() {
        val labels = listOf(LabelTag("permies", "#t"))
        val template =
            LabelEvent.buildPubKeyLabel(
                labeledPubKey = testPubKey,
                labels = labels,
                content = "",
            )

        assertEquals(1985, template.kind)

        val hasPubKeyRef = template.tags.any { it[0] == "p" && it[1] == testPubKey }
        assertTrue(hasPubKeyRef, "Should have p tag targeting labeled pubkey")

        val hasNamespace = template.tags.any { it[0] == "L" && it[1] == "#t" }
        assertTrue(hasNamespace, "Should have L namespace tag")

        val hasLabel = template.tags.any { it[0] == "l" && it[1] == "permies" && it[2] == "#t" }
        assertTrue(hasLabel, "Should have l label tag")
    }

    @Test
    fun testBuildMultipleLabels() {
        val labels =
            listOf(
                LabelTag("IT-MI", "ISO-3166-2"),
                LabelTag("en", "ISO-639-1"),
            )
        val template = LabelEvent.build(labels = labels)

        val namespaces =
            template.tags
                .filter { it[0] == "L" }
                .map { it[1] }
                .toSet()
        assertEquals(setOf("ISO-3166-2", "ISO-639-1"), namespaces)

        val labelTags = template.tags.filter { it[0] == "l" }
        assertEquals(2, labelTags.size)
    }

    @Test
    fun testEventFactoryCreatesLabelEvent() {
        val event =
            EventFactory.create<Event>(
                id = testEventId,
                pubKey = testPubKey,
                createdAt = 1234567890L,
                kind = 1985,
                tags =
                    arrayOf(
                        arrayOf("L", "license"),
                        arrayOf("l", "MIT", "license"),
                        arrayOf("e", testEventId),
                    ),
                content = "",
                sig = "c".repeat(128),
            )

        assertIs<LabelEvent>(event)
        assertEquals(1985, event.kind)

        val labels = event.labels()
        assertEquals(1, labels.size)
        assertEquals("MIT", labels[0].label)
        assertEquals("license", labels[0].namespace)

        val namespaces = event.namespaces()
        assertEquals(1, namespaces.size)
        assertEquals("license", namespaces[0].namespace)
    }

    @Test
    fun testLabelsByNamespace() {
        val event =
            EventFactory.create<LabelEvent>(
                id = testEventId,
                pubKey = testPubKey,
                createdAt = 1234567890L,
                kind = 1985,
                tags =
                    arrayOf(
                        arrayOf("L", "license"),
                        arrayOf("L", "ISO-639-1"),
                        arrayOf("l", "MIT", "license"),
                        arrayOf("l", "en", "ISO-639-1"),
                        arrayOf("e", testEventId),
                    ),
                content = "",
                sig = "c".repeat(128),
            )

        val licenseLabels = event.labelsByNamespace("license")
        assertEquals(1, licenseLabels.size)
        assertEquals("MIT", licenseLabels[0].label)

        val languageLabels = event.labelsByNamespace("ISO-639-1")
        assertEquals(1, languageLabels.size)
        assertEquals("en", languageLabels[0].label)
    }

    @Test
    fun testLabeledTargets() {
        val event =
            EventFactory.create<LabelEvent>(
                id = testEventId,
                pubKey = testPubKey,
                createdAt = 1234567890L,
                kind = 1985,
                tags =
                    arrayOf(
                        arrayOf("L", "ugc"),
                        arrayOf("l", "funny", "ugc"),
                        arrayOf("e", testEventId),
                        arrayOf("p", testPubKey),
                        arrayOf("t", "bitcoin"),
                        arrayOf("r", "wss://relay.example.com"),
                    ),
                content = "",
                sig = "c".repeat(128),
            )

        assertEquals(listOf(testEventId), event.labeledEvents())
        assertEquals(listOf(testPubKey), event.labeledPubKeys())
        assertEquals(listOf("bitcoin"), event.labeledHashtags())
        assertEquals(listOf("wss://relay.example.com"), event.labeledRelayUrls())
    }
}
