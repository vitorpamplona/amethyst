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
package com.vitorpamplona.amethyst.commons.model.privateChats

import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatPreviewTest {
    private val me = "aa".repeat(32)
    private val other = "bb".repeat(32)
    private val stranger = "cc".repeat(32)
    private val someSig = "dd".repeat(64)

    private val ciphertext = "0tyoSVovKSK9uDKLUVMs137TD0b+vz+fjoJN+gG3Bk4=?iv=6r6yPQ=="

    private fun privateDm(
        author: String = other,
        recipient: String = me,
    ) = PrivateDmEvent(
        id = "dm".padEnd(64, '0'),
        pubKey = author,
        createdAt = 1_000,
        tags = arrayOf(arrayOf("p", recipient)),
        content = ciphertext,
        sig = someSig,
    )

    private fun nip17Rumor() =
        ChatMessageEvent(
            id = "cm".padEnd(64, '0'),
            pubKey = other,
            createdAt = 1_000,
            tags = arrayOf(arrayOf("p", me)),
            content = "hello there",
            sig = someSig,
        )

    // ---- hasEncryptedContent -------------------------------------------------

    @Test
    fun nip04DmCarriesCiphertext() {
        assertTrue(privateDm().hasEncryptedContent())
    }

    @Test
    fun nip17RumorIsAlreadyPlaintext() {
        assertFalse(nip17Rumor().hasEncryptedContent())
    }

    // ---- chatPreviewOf -------------------------------------------------------

    @Test
    fun plaintextMessageRendersItsContent() {
        assertEquals(
            ChatPreview.Body("hello there"),
            chatPreviewOf(nip17Rumor(), decrypted = null, myPubKey = me, canDecrypt = true),
        )
    }

    @Test
    fun decryptedDmRendersThePlaintext() {
        assertEquals(
            ChatPreview.Body("hi!"),
            chatPreviewOf(privateDm(), decrypted = "hi!", myPubKey = me, canDecrypt = true),
        )
    }

    /** The regression under test: the raw base64 blob must never become the preview. */
    @Test
    fun pendingDmNeverFallsThroughToCiphertext() {
        val preview = chatPreviewOf(privateDm(), decrypted = null, myPubKey = me, canDecrypt = true)

        assertEquals(ChatPreview.Decrypting, preview)
        assertFalse(preview is ChatPreview.Body)
    }

    @Test
    fun readOnlyAccountCannotEverDecrypt() {
        assertEquals(
            ChatPreview.Undecryptable,
            chatPreviewOf(privateDm(), decrypted = null, myPubKey = me, canDecrypt = false),
        )
    }

    @Test
    fun dmBetweenOtherPeopleIsUndecryptableNotPending() {
        assertEquals(
            ChatPreview.Undecryptable,
            chatPreviewOf(
                privateDm(author = other, recipient = stranger),
                decrypted = null,
                myPubKey = me,
                canDecrypt = true,
            ),
        )
    }

    @Test
    fun dmIAuthoredIsStillDecryptableByMe() {
        assertEquals(
            ChatPreview.Decrypting,
            chatPreviewOf(
                privateDm(author = me, recipient = other),
                decrypted = null,
                myPubKey = me,
                canDecrypt = true,
            ),
        )
    }

    @Test
    fun missingEventReportsMissing() {
        assertEquals(
            ChatPreview.Missing,
            chatPreviewOf(null, decrypted = null, myPubKey = me, canDecrypt = true),
        )
    }
}
