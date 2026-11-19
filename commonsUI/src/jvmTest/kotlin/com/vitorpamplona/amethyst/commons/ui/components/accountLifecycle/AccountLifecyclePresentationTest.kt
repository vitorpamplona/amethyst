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
package com.vitorpamplona.amethyst.commons.ui.components.accountLifecycle

import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleEvaluation
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleResult
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleScope
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleState
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleStatus
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.LifecycleEventClassification
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.LifecycleNameNamespace
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.NamecoinNameContext
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.SuccessorConfirmation
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.accountLifecycleEventClassification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AccountLifecyclePresentationTest {
    @Test
    fun nestedRowsSuppressOnlyTheSameAuthorWarning() {
        val old = "a".repeat(64)
        val reposter = "b".repeat(64)
        assertEquals(true, shouldRenderAccountLifecycleOnNote(false, false, old, emptySet()))
        assertEquals(false, shouldRenderAccountLifecycleOnNote(true, false, old, setOf(old)))
        assertEquals(false, shouldRenderAccountLifecycleOnNote(false, true, old, setOf(old)))
        assertEquals(true, shouldRenderAccountLifecycleOnNote(true, false, old, setOf(reposter)))
        assertEquals(true, shouldRenderAccountLifecycleOnNote(false, true, old, setOf(reposter)))
        assertEquals(true, shouldRenderAccountLifecycleOnNote(true, false, null, setOf(old)))
    }

    @Test
    fun compromiseHasPriorityAndFreshEvidenceBeatsStaleEvidence() {
        val staleCompromise = evaluation(AccountLifecycleState.COMPROMISED, AccountLifecycleStatus.STALE)
        val currentCompromise = evaluation(AccountLifecycleState.COMPROMISED)
        val currentRetirement = evaluation(AccountLifecycleState.RETIRED)

        assertSame(currentCompromise, primaryAccountLifecycleEvaluation(listOf(currentRetirement, staleCompromise, currentCompromise), false))
        assertSame(staleCompromise, primaryAccountLifecycleEvaluation(listOf(currentRetirement, staleCompromise), false))
    }

    @Test
    fun notesHideDiagnosticsButNeverHideAnAccountWarning() {
        val unavailable = evaluation(status = AccountLifecycleStatus.UNAVAILABLE)
        val warning = evaluation(AccountLifecycleState.RETIRED)

        assertNull(primaryAccountLifecycleEvaluation(listOf(unavailable), includeErrors = false))
        assertSame(unavailable, primaryAccountLifecycleEvaluation(listOf(unavailable), includeErrors = true))
        assertSame(warning, primaryAccountLifecycleEvaluation(listOf(unavailable, warning), includeErrors = false))
    }

    @Test
    fun directRecordClaimWithoutOldBacklinkIsNotAnAccountWarning() {
        val directClaim =
            evaluation(AccountLifecycleState.COMPROMISED).let {
                it.copy(result = it.result.copy(scope = AccountLifecycleScope.DIRECT_RECORD))
            }

        assertNull(primaryAccountLifecycleEvaluation(listOf(directClaim), includeErrors = false))
        assertNull(primaryAccountLifecycleEvaluation(listOf(directClaim), includeErrors = true))
    }

    @Test
    fun successorProfileGetsPredecessorLinkWithoutInheritingOldWarning() {
        val old = "a".repeat(64)
        val new = "b".repeat(64)
        val predecessor =
            evaluation(AccountLifecycleState.COMPROMISED).let {
                it.copy(
                    subjectPubkey = old,
                    result =
                        it.result.copy(
                            successorPubkey = new,
                            successorConfirmation = SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED,
                            confirmationAliases = listOf("current@example.bit"),
                        ),
                )
            }
        val newOwnResult =
            evaluation(status = AccountLifecycleStatus.NO_DECLARATION).copy(subjectPubkey = new)
        val evaluations = listOf(newOwnResult, predecessor)

        assertSame(predecessor, predecessorForSubject(evaluations, new))
        assertNull(primaryAccountLifecycleEvaluationForSubject(evaluations, new, includeErrors = false))
        assertSame(predecessor, primaryAccountLifecycleEvaluationForSubject(evaluations, old, includeErrors = false))
        assertNull(
            predecessorForSubject(
                listOf(
                    predecessor.let {
                        it.copy(result = it.result.copy(successorConfirmation = SuccessorConfirmation.WITHDRAWN))
                    },
                ),
                new,
            ),
        )
    }

    @Test
    fun eventTimestampNeverChangesWarningSemantics() {
        val since = 1_794_739_200L
        assertEquals(LifecycleEventClassification.UNCLASSIFIED, accountLifecycleEventClassification(since - 1, since))
        assertEquals(LifecycleEventClassification.UNCLASSIFIED, accountLifecycleEventClassification(since + 1, since))
    }

    private fun evaluation(
        state: AccountLifecycleState? = null,
        status: AccountLifecycleStatus = AccountLifecycleStatus.CURRENT,
    ) = AccountLifecycleEvaluation(
        result =
            AccountLifecycleResult(
                status = status,
                scope = if (state == null) AccountLifecycleScope.NONE else AccountLifecycleScope.ACCOUNT,
                state = state,
            ),
        context =
            NamecoinNameContext(
                namespace = LifecycleNameNamespace.D,
                name = "d/example",
                host = "example.bit",
                localPart = "_",
            ),
        subjectPubkey = "a".repeat(64),
        selectedRecord = null,
    )
}
