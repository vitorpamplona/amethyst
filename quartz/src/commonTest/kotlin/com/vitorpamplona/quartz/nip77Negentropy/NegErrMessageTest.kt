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
package com.vitorpamplona.quartz.nip77Negentropy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A stated cap is acted on — it sizes the next NEG-OPEN — so reading one out of
 * a refusal that is not about size is worse than reading none at all: a quota or
 * rate limit does not shrink when the window shrinks, so a client that mistook
 * one for a cap would shrink its windows forever against a relay that has no
 * size limit.
 */
class NegErrMessageTest {
    @Test
    fun capComesFromTheWireField() {
        assertEquals(1_000_000L, NegErrMessage("s", "blocked: too many query results", 1_000_000L).statedCap)
    }

    @Test
    fun capComesFromStrfrysProseWhenTheFieldIsAbsent() {
        val msg = NegErrMessage("s", "blocked: query matches too many records (2431002 > 1000000)")
        assertEquals(1_000_000L, msg.statedCap)
    }

    @Test
    fun theWireFieldWinsOverTheProse() {
        val msg = NegErrMessage("s", "blocked: too many records (5 > 10)", 1_000L)
        assertEquals(1_000L, msg.statedCap)
    }

    @Test
    fun anOverflowWithNoNumberStatesNothing() {
        assertNull(NegErrMessage("s", "blocked: too many query results").statedCap)
    }

    @Test
    fun aRateLimitIsNotACapHoweverManyNumbersItCarries() {
        assertFalse(NegErrMessage.isOverflow("rate-limited: too many requests (30 > 10)"))
        assertNull(NegErrMessage("s", "rate-limited: too many requests (30 > 10)", 10L).statedCap)
    }

    @Test
    fun refusalsThatAreNotAboutSizeStateNothing() {
        listOf(
            "auth-required: we only serve negentropy to authenticated users",
            "blocked: pubkey is banned",
            "error: negentropy disabled",
            "closed: unknown subscription handle",
        ).forEach {
            assertFalse(NegErrMessage.isOverflow(it), "read as an overflow: $it")
            assertNull(NegErrMessage("s", it, 42L).statedCap, "read a cap from: $it")
        }
    }

    @Test
    fun theWordingsThatDoMeanOverflow() {
        listOf(
            "blocked: query matches too many records (5 > 1)",
            "blocked: too many query results",
            "error: result set too large",
            "blocked: results too large",
            "blocked: max_sync_events exceeded",
        ).forEach { assertTrue(NegErrMessage.isOverflow(it), "not read as an overflow: $it") }
    }

    @Test
    fun aNonsensicalCapIsRefused() {
        // Zero would wedge a client at a window that can never fit.
        assertNull(NegErrMessage("s", "blocked: too many query results", 0L).statedCap)
        assertNull(NegErrMessage("s", "blocked: too many records (5 > 0)").statedCap)
        assertNull(NegErrMessage("s", "blocked: too many query results", -1L).statedCap)
    }
}
