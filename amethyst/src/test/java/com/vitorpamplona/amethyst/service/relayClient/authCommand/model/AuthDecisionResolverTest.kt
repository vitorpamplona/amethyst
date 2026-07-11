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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthDecisionResolverTest {
    /** A prompt that must never be called; fails the test if the resolver asks the user. */
    private val neverPrompt: suspend () -> UserAuthChoice = { error("prompt() should not be called") }

    @Test
    fun noVerdictsAutoAuthenticatesWithoutPrompting() =
        runTest {
            val outcome = AuthDecisionResolver.resolve(emptyList(), neverPrompt)
            assertTrue(outcome.shouldAuth)
            assertNull(outcome.remember)
        }

    @Test
    fun anyAllowAuthenticatesWithoutPrompting() =
        runTest {
            val outcome =
                AuthDecisionResolver.resolve(
                    listOf(RelayAuthVerdict.DENY, RelayAuthVerdict.ALLOW, RelayAuthVerdict.ASK),
                    neverPrompt,
                )
            assertTrue(outcome.shouldAuth)
            assertNull(outcome.remember)
        }

    @Test
    fun allDenyDoesNotAuthenticateAndDoesNotPrompt() =
        runTest {
            val outcome =
                AuthDecisionResolver.resolve(
                    listOf(RelayAuthVerdict.DENY, RelayAuthVerdict.DENY),
                    neverPrompt,
                )
            assertFalse(outcome.shouldAuth)
            assertNull(outcome.remember)
        }

    @Test
    fun askAllowOnceAuthenticatesButRemembersNothing() =
        runTest {
            val outcome =
                AuthDecisionResolver.resolve(listOf(RelayAuthVerdict.ASK)) { UserAuthChoice.ALLOW_ONCE }
            assertTrue(outcome.shouldAuth)
            assertNull(outcome.remember)
        }

    @Test
    fun askAlwaysAllowAuthenticatesAndRemembersAllow() =
        runTest {
            val outcome =
                AuthDecisionResolver.resolve(listOf(RelayAuthVerdict.ASK)) { UserAuthChoice.ALWAYS_ALLOW }
            assertTrue(outcome.shouldAuth)
            assertEquals(RelayAuthDecision.ALLOW, outcome.remember)
        }

    @Test
    fun askBlockDoesNotAuthenticateAndRemembersDeny() =
        runTest {
            val outcome =
                AuthDecisionResolver.resolve(listOf(RelayAuthVerdict.ASK)) { UserAuthChoice.BLOCK }
            assertFalse(outcome.shouldAuth)
            assertEquals(RelayAuthDecision.DENY, outcome.remember)
        }

    @Test
    fun askDismissDoesNotAuthenticateAndRemembersNothing() =
        runTest {
            val outcome =
                AuthDecisionResolver.resolve(listOf(RelayAuthVerdict.ASK)) { UserAuthChoice.DISMISS }
            assertFalse(outcome.shouldAuth)
            assertNull(outcome.remember)
        }

    @Test
    fun askIsOnlyReachedWhenNoAccountAllows() =
        runTest {
            // ALLOW present alongside ASK must short-circuit to auth without prompting.
            var prompted = false
            val outcome =
                AuthDecisionResolver.resolve(listOf(RelayAuthVerdict.ASK, RelayAuthVerdict.ALLOW)) {
                    prompted = true
                    UserAuthChoice.BLOCK
                }
            assertTrue(outcome.shouldAuth)
            assertFalse(prompted)
        }
}
