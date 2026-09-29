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
package com.vitorpamplona.quartz.nip09Deletions

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeletionIndexByIdTest {
    private val alice = NostrSignerInternal(KeyPair())
    private val mallory = NostrSignerInternal(KeyPair())

    @Test
    fun aDeleteIsFoundByIdAndAuthorAloneAndOnlyForItsAuthor() =
        runTest {
            val message = alice.sign(ChatEvent.build("hello", createdAt = 1))
            val index = DeletionIndex()
            assertFalse(index.hasBeenDeleted(message.id, alice.pubKey))

            // A stranger's delete names the id but is not the author's own.
            index.add(mallory.sign(DeletionRequestEvent.build(listOf(message), createdAt = 2)), wasVerified = false)
            assertFalse(index.hasBeenDeleted(message.id, alice.pubKey))
            assertTrue(index.hasBeenDeleted(message.id, mallory.pubKey))

            index.add(alice.sign(DeletionRequestEvent.build(listOf(message), createdAt = 3)), wasVerified = false)
            assertTrue(index.hasBeenDeleted(message.id, alice.pubKey), "no loaded event needed: the id and its author suffice")
        }
}
