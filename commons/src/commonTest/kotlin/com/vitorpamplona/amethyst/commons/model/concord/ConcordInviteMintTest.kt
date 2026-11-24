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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.notices.ConcordNotice
import com.vitorpamplona.amethyst.commons.notices.SignerNotice
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** An invite-link tap that makes no link says why, and nothing it throws escapes the screen (audit 3). */
class ConcordInviteMintTest {
    private val heard = mutableListOf<UserNotice>()

    @Test
    fun aMintedLinkComesBackWithNothingToSay() =
        runTest {
            assertEquals("https://a/b", mintConcordInviteReporting({ heard += it }, writeable = true) { "https://a/b" })
            assertEquals(emptyList(), heard)
        }

    @Test
    fun aMintThatComesBackEmptyIsReported() =
        runTest {
            assertNull(mintConcordInviteReporting({ heard += it }, writeable = true) { null })
            assertEquals(listOf<UserNotice>(ConcordNotice.InviteNotMinted), heard)
        }

    @Test
    fun aReadOnlyAccountHearsSoAndNeverMints() =
        runTest {
            assertNull(mintConcordInviteReporting({ heard += it }, writeable = false) { error("minted") })
            assertEquals(listOf<UserNotice>(SignerNotice.ReadOnly), heard)
        }

    @Test
    fun aRelayFailureIsReportedInsteadOfThrown() =
        runTest {
            assertNull(mintConcordInviteReporting({ heard += it }, writeable = true) { throw RuntimeException("socket closed") })
            assertEquals(listOf<UserNotice>(ConcordNotice.InviteNotMinted), heard)
        }

    @Test
    fun aSignerRefusalKeepsItsOwnNotice() =
        runTest {
            assertNull(mintConcordInviteReporting({ heard += it }, writeable = true) { throw SignerExceptions.ReadOnlyException() })
            assertEquals(listOf<UserNotice>(SignerNotice.ReadOnly), heard)
        }

    @Test
    fun cancellationStillCancels() =
        runTest {
            assertFailsWith<CancellationException> { mintConcordInviteReporting({ heard += it }, writeable = true) { throw CancellationException("gone") } }
        }
}
