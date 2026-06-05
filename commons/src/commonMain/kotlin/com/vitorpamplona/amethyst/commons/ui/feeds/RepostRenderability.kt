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
package com.vitorpamplona.amethyst.commons.ui.feeds

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.BaseRepostEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * A repost (kind 6 / kind 16) wraps another event. When that inner kind has no
 * typed Quartz class — e.g. a Ditto kind-16767 profile theme wrapped in a
 * generic repost — Amethyst can neither parse nor render it, so the repost would
 * show as a permanently blank card. Feeds use this predicate in their acceptance
 * allow-list to drop such reposts, mirroring how regular unknown-kind events are
 * never displayed (no UI component renders a bare [Event]).
 *
 * Returns true only for reposts whose boosted content is displayable, so it can
 * replace the `is RepostEvent || is GenericRepostEvent` clause in a feed's
 * acceptance allow-list. Non-reposts return false (they are admitted by the
 * other clauses).
 *
 * Conservative: a repost that declares no boosted `k` kind is assumed renderable
 * — we only hide when we can positively prove the inner kind is unknown.
 *
 * The `returns(true) implies non-null` contract lets it stand in for the two
 * `is` checks in an allow-list without losing the chain's non-null smart-cast
 * (the `&& filterParams.match(noteEvent, …)` tail relies on it).
 */
@OptIn(ExperimentalContracts::class)
fun Event?.isRenderableRepost(): Boolean {
    contract { returns(true) implies (this@isRenderableRepost != null) }
    if (this !is BaseRepostEvent) return false
    val boostedKind = boostedKind()
    return boostedKind == null || EventFactory.isKnownKind(boostedKind)
}
