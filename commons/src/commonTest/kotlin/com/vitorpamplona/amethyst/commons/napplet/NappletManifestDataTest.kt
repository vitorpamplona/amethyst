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
package com.vitorpamplona.amethyst.commons.napplet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletManifestDataTest {
    private val hash = "ab".repeat(32)

    private fun parse(
        kind: Int = 15129,
        content: String = "A test app",
        vararg tags: Array<String>,
    ) = NappletManifestData.parse(kind, arrayOf(*tags), content)

    @Test
    fun currentKindsUseTheArtifactHashAndDescriptionContent() {
        for (kind in listOf(5129, 15129, 35129)) {
            val tags = if (kind == 35129) arrayOf(arrayOf("d", "demo")) else emptyArray()
            val manifest = assertNotNull(parse(kind, "Plain <b>text</b>", arrayOf("x", hash), *tags))
            assertEquals(hash, manifest.artifactHash)
            assertEquals(hash, manifest.paths.single().hash)
            assertEquals("/index.html", manifest.paths.single().path)
            assertEquals("Plain <b>text</b>", manifest.description)
        }
    }

    @Test
    fun invalidExecutionMetadataIsRejected() {
        assertNull(parse())
        assertNull(parse(tags = arrayOf(arrayOf("x", hash), arrayOf("x", hash))))
        assertNull(parse(tags = arrayOf(arrayOf("x", hash.uppercase()))))
        assertNull(parse(tags = arrayOf(arrayOf("x", hash, "extra"))))
        assertNull(parse(content = " ", tags = arrayOf(arrayOf("x", hash))))
        assertNull(parse(35129, tags = arrayOf(arrayOf("x", hash))))
        assertNull(parse(tags = arrayOf(arrayOf("x", hash), arrayOf("d", "root"))))
        assertNull(parse(35129, tags = arrayOf(arrayOf("x", hash), arrayOf("d", "a"), arrayOf("d", "b"))))
        assertNull(parse(tags = arrayOf(arrayOf("x", hash), arrayOf("R", "NAP-RELAY"))))
        // The old format must not silently execute under a different identity.
        assertNull(parse(content = "", tags = arrayOf(arrayOf("path", "/index.html", hash), arrayOf("description", "Legacy"))))
    }

    @Test
    fun requiredAndOptionalDomainsRemainDistinct() {
        val manifest = assertNotNull(parse(tags = arrayOf(arrayOf("x", hash), arrayOf("R", "relay"), arrayOf("O", "theme"), arrayOf("O", "future"))))
        assertTrue(manifest.unsupportedRequired.isEmpty())
        assertEquals(listOf("relay", "theme"), manifest.exposedDomains)
        val unsupported = assertNotNull(parse(tags = arrayOf(arrayOf("x", hash), arrayOf("R", "future"))))
        assertEquals(listOf("future"), unsupported.unsupportedRequired)
    }

    @Test
    fun lineageIsSnapshotOnlyAndNeverChangesTheArtifact() {
        val parent = arrayOf("a", "35129:${"01".repeat(32)}:parent")
        val root = arrayOf("A", "15129:${"02".repeat(32)}:")
        val tags = arrayOf(arrayOf("x", hash), parent, root)
        assertEquals(hash, assertNotNull(parse(5129, tags = tags)).artifactHash)
        assertNull(parse(15129, tags = tags))
        assertNull(parse(5129, tags = arrayOf(*tags, parent)))
        assertNull(parse(5129, tags = arrayOf(*tags, root)))
    }

    @Test
    fun iconErrorsAreCosmetic() {
        val malformed = listOf(arrayOf("icon", "https://example.com/icon.png"), arrayOf("icon", hash, "image/svg+xml"), arrayOf("icon", hash.uppercase(), "image/png"))
        for (icon in malformed) assertNull(assertNotNull(parse(tags = arrayOf(arrayOf("x", hash), icon))).icon)
        assertNull(assertNotNull(parse(tags = arrayOf(arrayOf("x", hash), arrayOf("icon", hash, "image/png"), arrayOf("icon", hash, "image/png")))).icon)
        assertEquals(NappletManifestIcon(hash, "image/png"), assertNotNull(parse(tags = arrayOf(arrayOf("x", hash), arrayOf("icon", hash, "image/png")))).icon)
    }

    @Test
    fun sourceAcceptsRepositoryTransportsButNeverScriptsOrLists() {
        for (source in listOf("https://git.example/repo", "http://git.example/repo", "ssh://git@git.example/repo", "git://git.example/repo", "git@git.example:owner/repo.git", "git.example:owner/repo.git")) {
            assertNotNull(parse(tags = arrayOf(arrayOf("x", hash), arrayOf("source", source))))
        }
        assertNull(parse(tags = arrayOf(arrayOf("x", hash), arrayOf("source", "javascript:alert(1)"))))
        assertNull(parse(tags = arrayOf(arrayOf("x", hash), arrayOf("source", "https://one"), arrayOf("source", "https://two"))))
    }

    @Test
    fun writerRoundTripsWithoutLegacyTags() {
        val template = NappletManifestData.build(hash, "Description", "demo", listOf("relay"), listOf("theme"), listOf("https://blossom.example"), "Demo", icon = NappletManifestIcon(hash, "image/webp"), archetypes = listOf("feed"), intents = listOf(listOf("napplet:feed/edit", "filters")))
        val parsed = assertNotNull(NappletManifestData.parse(template.kind, template.tags, template.content))
        assertEquals("demo", parsed.identifier)
        assertEquals(listOf("feed"), parsed.archetypes)
        assertEquals(listOf(listOf("napplet:feed/edit", "filters")), parsed.intents)
        assertTrue(template.tags.none { it[0] in setOf("path", "requires", "description") })
    }

    @Test
    fun storageSeparatesManifestKindsAndSnapshotIdsEvenWithIdenticalArtifacts() {
        val identity = NappletIdentity("aa".repeat(32), "", hash)
        val root = identity.copy(manifestKind = 15129)
        val named = identity.copy(manifestKind = 35129)
        val first = identity.copy(manifestKind = 5129, manifestEventId = "01".repeat(32))
        val second = identity.copy(manifestKind = 5129, manifestEventId = "02".repeat(32))
        assertEquals(4, setOf(root.storageCoordinate, named.storageCoordinate, first.storageCoordinate, second.storageCoordinate).size)
        assertTrue(root.copy(authorPubKey = "bb".repeat(32)).storageCoordinate != root.storageCoordinate)
        assertTrue(root.copy(aggregateHash = "cd".repeat(32)).storageCoordinate != root.storageCoordinate)
        assertTrue(root.copy(instanceId = "one").instanceStorageCoordinate != root.copy(instanceId = "two").instanceStorageCoordinate)
    }
}
