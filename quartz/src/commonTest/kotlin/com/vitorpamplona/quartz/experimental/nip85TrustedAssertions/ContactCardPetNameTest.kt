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
package com.vitorpamplona.quartz.experimental.nip85TrustedAssertions

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.PetNameTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.SummaryTag
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactCardPetNameTest {
    val signer = NostrSignerInternal(KeyPair())
    val targetUser = "e88a691e98d9987c964521dff60025f60700378a4879180dcbbb4a5027850411"

    private suspend fun ContactCardEvent.privatePetName() = privateTags(signer)?.firstNotNullOfOrNull(PetNameTag::parse)

    private suspend fun ContactCardEvent.privateSummary() = privateTags(signer)?.firstNotNullOfOrNull(SummaryTag::parse)

    @Test
    fun createKeepsPetNameAndSummaryEncrypted() =
        runTest {
            val card =
                ContactCardEvent.create(
                    targetUser = targetUser,
                    petName = "Bob from work",
                    summary = "Met at the conference",
                    signer = signer,
                )

            assertEquals(targetUser, card.aboutUser())

            // never in the public tags
            assertNull(card.petName())
            assertNull(card.summary())

            // always in the encrypted content
            assertEquals("Bob from work", card.privatePetName())
            assertEquals("Met at the conference", card.privateSummary())
        }

    @Test
    fun updateReplacesPetNameAndKeepsOtherPrivateTags() =
        runTest {
            val card =
                ContactCardEvent.create(
                    targetUser = targetUser,
                    petName = "Bob",
                    summary = "old summary",
                    signer = signer,
                    privateInitializer = { add(arrayOf("t", "friend")) },
                    publicInitializer = { add(arrayOf("n", "follow")) },
                )

            val updated =
                ContactCardEvent.updatePetNameAndSummary(
                    earlierVersion = card,
                    petName = "Bobby",
                    summary = "new summary",
                    signer = signer,
                )

            assertEquals(targetUser, updated.aboutUser())
            assertEquals("Bobby", updated.privatePetName())
            assertEquals("new summary", updated.privateSummary())

            // other tags survive on both sides
            assertTrue(updated.privateTags(signer)!!.any { it.size > 1 && it[0] == "t" && it[1] == "friend" })
            assertTrue(updated.tags.any { it.size > 1 && it[0] == "n" && it[1] == "follow" })

            // still nothing leaked publicly
            assertNull(updated.petName())
            assertNull(updated.summary())
        }

    @Test
    fun updateWithNullsClearsBothFields() =
        runTest {
            val card =
                ContactCardEvent.create(
                    targetUser = targetUser,
                    petName = "Bob",
                    summary = "summary",
                    signer = signer,
                )

            val cleared =
                ContactCardEvent.updatePetNameAndSummary(
                    earlierVersion = card,
                    petName = null,
                    summary = null,
                    signer = signer,
                )

            assertNull(cleared.privatePetName())
            assertNull(cleared.privateSummary())
        }

    @Test
    fun updateStripsLegacyPublicCopies() =
        runTest {
            // a card that (incorrectly) carries public petname/summary tags
            val card =
                ContactCardEvent.create(
                    targetUser = targetUser,
                    signer = signer,
                    publicInitializer = {
                        add(PetNameTag.assemble("public bob"))
                        add(SummaryTag.assemble("public summary"))
                    },
                )
            assertEquals("public bob", card.petName())

            val updated =
                ContactCardEvent.updatePetNameAndSummary(
                    earlierVersion = card,
                    petName = "private bob",
                    signer = signer,
                )

            assertNull(updated.petName())
            assertNull(updated.summary())
            assertEquals("private bob", updated.privatePetName())
        }
}
