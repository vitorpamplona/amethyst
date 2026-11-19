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

import androidx.compose.runtime.staticCompositionLocalOf
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleEvaluation
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleScope
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleState
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleStatus
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.SuccessorConfirmation

/** Authors whose warnings already wrap this note, including quotes and boosts several levels deep. */
val LocalAccountLifecycleWarningAuthors = staticCompositionLocalOf<Set<String>> { emptySet() }

/** Nested rows suppress only an identical author warning already rendered by an outer row. */
fun shouldRenderAccountLifecycleOnNote(
    isQuotedNote: Boolean,
    isBoostedNote: Boolean,
    authorPubkey: String?,
    outerAuthorPubkeys: Set<String>,
): Boolean = (!isQuotedNote && !isBoostedNote) || authorPubkey == null || authorPubkey !in outerAuthorPubkeys

fun primaryAccountLifecycleEvaluationForSubject(
    evaluations: List<AccountLifecycleEvaluation>,
    subjectPubkey: String,
    includeErrors: Boolean,
): AccountLifecycleEvaluation? =
    primaryAccountLifecycleEvaluation(
        evaluations.filter { it.subjectPubkey == subjectPubkey },
        includeErrors,
    )

/** OLD's warning remains on OLD; NEW receives only this explicit, reciprocally confirmed link. */
fun predecessorForSubject(
    evaluations: List<AccountLifecycleEvaluation>,
    subjectPubkey: String,
): AccountLifecycleEvaluation? =
    evaluations.firstOrNull {
        it.subjectPubkey != subjectPubkey &&
            it.result.isAccountWarning &&
            it.result.successorPubkey == subjectPubkey &&
            it.result.successorConfirmation == SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED
    }

fun primaryAccountLifecycleEvaluation(
    evaluations: List<AccountLifecycleEvaluation>,
    includeErrors: Boolean,
): AccountLifecycleEvaluation? {
    val warnings = evaluations.filter { it.result.isAccountWarning }
    val warning =
        warnings.minWithOrNull(
            compareBy<AccountLifecycleEvaluation> {
                when (it.result.state) {
                    AccountLifecycleState.COMPROMISED -> 0
                    AccountLifecycleState.RETIRED -> 1
                    null -> 2
                }
            }.thenBy { it.result.status == AccountLifecycleStatus.STALE },
        )
    if (warning != null || !includeErrors) return warning
    return evaluations.firstOrNull {
        it.result.scope != AccountLifecycleScope.DIRECT_RECORD &&
            it.result.status in
            setOf(
                AccountLifecycleStatus.INVALID,
                AccountLifecycleStatus.UNSUPPORTED_STATE,
                AccountLifecycleStatus.UNSUPPORTED_TRAVERSAL,
                AccountLifecycleStatus.UNAVAILABLE,
            )
    }
}
