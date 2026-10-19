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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Kind 30063 carries ciphertext in `content` when it is a NIP-51 set and plaintext release notes
 * when it is a NIP-82 release. The text search must match the notes and never the ciphertext.
 */
class ReleaseArtifactSetSearchTest {
    private val sig = "22".repeat(64)

    private fun set(
        idByte: String,
        signer: NostrSignerInternal,
        tags: Array<Array<String>>,
        content: String,
    ) = ReleaseArtifactSetEvent(idByte.repeat(32), signer.pubKey, 1_790_900_000L, tags, content, sig)

    @Test
    fun theCiphertextOfAPlainSetIsNotSearchedButReleaseNotesAre() =
        runTest {
            val cache = EventCache()
            val signer = NostrSignerInternal(KeyPair())

            val ciphertext = PrivateTagsInContent.encryptNip44(arrayOf(arrayOf("e", "cc".repeat(32))), signer)
            val plain = set("01", signer, arrayOf(arrayOf("d", "amethyst-v1.0"), arrayOf("title", "Amethyst v1.0")), ciphertext)
            val release =
                set(
                    "02",
                    signer,
                    arrayOf(arrayOf("d", "com.example.app@1.0.0"), arrayOf("i", "com.example.app"), arrayOf("version", "1.0.0")),
                    "Fixes the crash on startup.",
                )

            // The cache holds notes weakly: keep them reachable for the length of the test.
            val held = listOf(plain, release).map { cache.getOrCreateAddressableNote(it.address()) }
            listOf(plain, release).forEach { assertTrue(cache.justConsume(it, null, true)) }

            // A run of the base64 ciphertext, long enough that only the ciphertext itself contains it.
            val fragment = ciphertext.substring(10, 30)
            assertEquals(emptyList(), cache.findNotesStartingWith(fragment, LiveHiddenUsers.EMPTY).map { it.idHex })

            assertEquals(listOf(release.id), cache.findNotesStartingWith("crash on startup", LiveHiddenUsers.EMPTY).mapNotNull { it.event?.id })

            // The public title of the plain set is still found.
            assertEquals(listOf(plain.id), cache.findNotesStartingWith("Amethyst v1.0", LiveHiddenUsers.EMPTY).mapNotNull { it.event?.id })
            assertEquals(2, held.size)
        }
}
