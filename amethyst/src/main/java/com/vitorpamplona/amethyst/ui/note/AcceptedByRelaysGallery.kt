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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.ui.theme.WidthAuthorPictureModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample

/**
 * The "accepted by relays" line of the reaction gallery: the favicon of every relay
 * the note was seen on. This is the same relay-pill set that Complete UI Mode used to
 * paint below the author avatar; it now lives in the expanded reaction gallery so it
 * is available to everyone, not just Complete mode.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun WatchRelaysAndRenderGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    // Cold wrapper: `flow()` must be re-resolved on every collection start. A memory
    // trim destroys the NoteFlowSet while the lifecycle is stopped; a stateFlow
    // captured in remember would then be orphaned and never see another relay update.
    // Sampled at 500ms because relay arrivals churn while a note is actively fanning out.
    val flow =
        remember(baseNote) {
            flow { emitAll(baseNote.flow().relays.stateFlow) }
                .sample(500)
                .map { it.note.relays.toImmutableList() }
                .distinctUntilChanged()
        }

    val initial = remember(baseNote) { baseNote.relays.toImmutableList() }
    val relays by flow.collectAsStateWithLifecycle(initial)

    if (relays.isNotEmpty()) {
        RenderAcceptedByRelaysGallery(relays, nav, accountViewModel)
    }
}

@Composable
private fun RenderAcceptedByRelaysGallery(
    relays: ImmutableList<NormalizedRelayUrl>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Row(Modifier.fillMaxWidth()) {
        Box(modifier = WidthAuthorPictureModifier) {
            Icon(
                symbol = MaterialSymbols.Dns,
                contentDescription = stringRes(id = R.string.accepted_by_relays),
                modifier = Modifier.size(Size20dp).align(Alignment.TopEnd),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }

        RelayGallery(relays, nav, accountViewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelayGallery(
    relays: ImmutableList<NormalizedRelayUrl>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Column(modifier = StdStartPadding) {
        FlowRow {
            relays.forEach { relay ->
                RenderRelay(relay, accountViewModel, nav)
            }
        }
    }
}
