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
package com.vitorpamplona.amethyst.commons.relayClient.mavenReleases

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipFCMavenRepositories.MavenModule

/**
 * Keeps a NIP-FC card's library loading its other releases from relays while the card is on screen.
 *
 * The REQ goes to the signer's outbox, which is often not loaded when the card first draws: re-plan
 * it when the signer's relay list (NIP-65) arrives or changes, or it would stay on the relays the
 * card's event happened to come from.
 */
@Composable
fun MavenVersionsFilterAssemblerSubscription(
    note: Note,
    signer: HexKey,
    module: MavenModule,
    filterAssembler: MavenVersionsFilterAssembler,
) {
    val state = remember(note, signer, module) { MavenVersionsQueryState(note, signer, module) }
    LifecycleAwareKeyDataSourceSubscription(state, filterAssembler)

    val relayList by remember(signer) {
        LocalCache
            .getOrCreateUser(signer)
            .nip65RelayListNote
            .flow()
            .metadata.stateFlow
    }.collectAsStateWithLifecycle()
    val relayListId = relayList.note.event?.id
    LaunchedEffect(relayListId) {
        if (relayListId != null) filterAssembler.invalidateFilters()
    }
}
