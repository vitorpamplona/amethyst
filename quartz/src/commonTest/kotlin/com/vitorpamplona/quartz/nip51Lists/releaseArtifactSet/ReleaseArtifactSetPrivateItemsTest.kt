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
package com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Kind 30063 is a NIP-51 set, so its `content` may hold private items encrypted to the author; a
 * NIP-82 release on the same kind keeps plaintext release notes there instead, which must never be
 * taken for private items.
 */
class ReleaseArtifactSetPrivateItemsTest {
    private val asset = "b".repeat(64)
    private val hiddenAsset = "c".repeat(64)

    private fun set(
        signer: NostrSignerInternal,
        tags: TagArray,
        content: String,
    ) = ReleaseArtifactSetEvent("a".repeat(64), signer.pubKey, 1790900000, tags, content, "0".repeat(128))

    private val plainTags = arrayOf(arrayOf("d", "amethyst-v1.0"), arrayOf("title", "Amethyst v1.0"), arrayOf("e", asset))

    @Test
    fun theAuthorReadsThePrivateItems() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val content = PrivateTagsInContent.encryptNip44(arrayOf(arrayOf("e", hiddenAsset)), signer)

            val private = assertNotNull(set(signer, plainTags, content).privateTags(signer))

            assertEquals(listOf(listOf("e", hiddenAsset)), private.map { it.toList() })
        }

    @Test
    fun anyoneElseGetsNull() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val content = PrivateTagsInContent.encryptNip44(arrayOf(arrayOf("e", hiddenAsset)), signer)

            assertNull(set(signer, plainTags, content).privateTags(NostrSignerInternal(KeyPair())))
        }

    @Test
    fun contentThatDoesNotDecryptIsNull() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())

            assertNull(set(signer, plainTags, "not a ciphertext").privateTags(signer))
        }

    @Test
    fun emptyContentIsAnEmptyListNotHiddenItems() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())

            assertEquals(0, set(signer, plainTags, "").privateTags(signer)?.size)
        }

    @Test
    fun nip82ReleaseNotesAreNotPrivateItems() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            // Content that would decrypt, so only the NIP-82 check can keep it from being read as items.
            val content = PrivateTagsInContent.encryptNip44(arrayOf(arrayOf("e", hiddenAsset)), signer)
            val release =
                set(
                    signer,
                    arrayOf(
                        arrayOf("d", "com.example.app@1.0.0"),
                        arrayOf("i", "com.example.app"),
                        arrayOf("version", "1.0.0"),
                        arrayOf("e", asset),
                    ),
                    content,
                )

            assertNull(release.privateTags(signer))
        }
}
