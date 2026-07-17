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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.ui.note.GitStatusPill
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.model.GitStatusIndex
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent

private fun GitStatusEvent.statusKind(): StatusKind =
    when (this) {
        is GitStatusAppliedEvent -> StatusKind.APPLIED
        is GitStatusClosedEvent -> StatusKind.CLOSED
        is GitStatusDraftEvent -> StatusKind.DRAFT
        is GitStatusOpenEvent -> StatusKind.OPEN
        else -> StatusKind.OPEN
    }

/**
 * Entry point: resolves the latest NIP-34 status for [targetIdHex] from the account's
 * [GitStatusIndex] and renders the shared commons [GitStatusPill]. Reading the index is
 * account/relay-bound state, so it stays native; the pill itself is pure commons UI.
 */
@Composable
fun GitStatusPill(
    targetIdHex: String,
    modifier: Modifier = Modifier,
    defaultIfMissing: StatusKind? = null,
) {
    val index by GitStatusIndex.latestByTarget.collectAsStateWithLifecycle()
    val map = index ?: return // hide pill until the initial scan completes — avoids a default-then-real flicker
    val kind = map[targetIdHex]?.statusKind() ?: defaultIfMissing ?: return

    GitStatusPill(kind, modifier)
}
