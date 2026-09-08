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
package com.vitorpamplona.quartz.marmot.protocolCore

/**
 * What convergence decided about one retained input
 * (`foundation/errors.md`, "Convergence dispositions").
 *
 * A disposition says WHAT happened; [ConvergenceCategory] says why.
 */
enum class ConvergenceDisposition {
    /** On, or consumed by, the selected canonical branch. */
    ACCEPTED,

    /**
     * No terminal classification yet; reconsidered when more input arrives.
     *
     * This is where valid commits on a NON-selected but still-eligible branch
     * sit. Losing one pass is not permanent ineligibility — the branch stays
     * eligible while its fork is inside the rollback horizon measured from the
     * live tip.
     */
    DEFERRED,

    /** Can no longer affect the group. */
    STALE,

    /**
     * An MLS application message that decrypts only on a losing branch. Its
     * app payload is WITHDRAWN from application output — the counterpart to
     * withdrawing state notifications from a superseded commit.
     */
    INVALIDATED,
}

/**
 * Why an input received its disposition. A `stale` or `deferred` input SHOULD
 * carry one.
 */
enum class ConvergenceCategory {
    DUPLICATE,
    UNKNOWN_GROUP,
    STALE_EPOCH,
    AUTHORIZATION_FAILED,
    MISSING_HISTORY,
    TRANSPORT_DEFERRED,
    RESOURCE_REFUSED,
}
