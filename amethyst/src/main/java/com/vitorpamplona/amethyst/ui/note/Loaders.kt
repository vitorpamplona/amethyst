/**
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteOts
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserStatuses
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@Composable
fun LoadDecryptedContent(
    note: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (String) -> Unit,
) {
    var decryptedContent by
        remember(note.event) {
            mutableStateOf(
                accountViewModel.cachedDecrypt(note),
            )
        }

    decryptedContent?.let { inner(it) }
        ?: run {
            LaunchedEffect(key1 = decryptedContent) {
                accountViewModel.decrypt(note) { decryptedContent = it }
            }
        }
}

@Composable
fun LoadDecryptedContentOrNull(
    note: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (String?) -> Unit,
) {
    var decryptedContent by remember(note.event?.id) { mutableStateOf(accountViewModel.cachedDecrypt(note)) }

    LaunchedEffect(note.event?.id) {
        accountViewModel.decrypt(note) {
            if (decryptedContent != it) {
                decryptedContent = it
            }
        }
    }

    inner(decryptedContent)
}

@Composable
fun LoadAddressableNote(
    address: Address,
    accountViewModel: AccountViewModel,
    content: @Composable (AddressableNote?) -> Unit,
) {
    val note by produceState(
        accountViewModel.getAddressableNoteIfExists(address),
        address,
    ) {
        val newNote = accountViewModel.getOrCreateAddressableNote(address)
        if (newNote != value) {
            value = newNote
        }
    }

    content(note)
}

@Composable
fun LoadStatuses(
    user: User,
    accountViewModel: AccountViewModel,
    content: @Composable (ImmutableList<AddressableNote>) -> Unit,
) {
    val userStatuses by observeUserStatuses(user, accountViewModel)

    content(userStatuses)
}

@Composable
fun LoadOts(
    note: Note,
    accountViewModel: AccountViewModel,
    whenConfirmed: @Composable (Long) -> Unit,
    whenPending: @Composable () -> Unit,
) {
    var earliestDate: GenericLoadable<Long> by remember { mutableStateOf(GenericLoadable.Loading()) }

    val noteStatus by observeNoteOts(note, accountViewModel)

    LaunchedEffect(key1 = noteStatus) {
        val newOts =
            withContext(Dispatchers.IO) {
                LocalCache.findEarliestOtsForNote(
                    note = noteStatus?.note ?: note,
                    otsVerifCache = Amethyst.instance.otsVerifCache,
                )
            }

        earliestDate =
            if (newOts == null) {
                GenericLoadable.Empty()
            } else {
                GenericLoadable.Loaded(newOts)
            }
    }

    (earliestDate as? GenericLoadable.Loaded)?.let {
        whenConfirmed(it.loaded)
    } ?: run {
        val pendingAttestations by accountViewModel.account.settings.pendingAttestations
            .collectAsStateWithLifecycle()
        val id = note.event?.id ?: note.idHex

        if (pendingAttestations[id] != null) {
            whenPending()
        }
    }
}

@Composable
fun LoadPublicChatChannel(
    id: String,
    accountViewModel: AccountViewModel,
    content: @Composable (PublicChatChannel) -> Unit,
) {
    val channel =
        produceStateIfNotNull(accountViewModel.getPublicChatChannelIfExists(id), id) {
            value = accountViewModel.checkGetOrCreatePublicChatChannel(id)
        }

    channel.value?.let { content(it) }
}

@Composable
fun LoadEphemeralChatChannel(
    id: RoomId,
    accountViewModel: AccountViewModel,
    content: @Composable (EphemeralChatChannel) -> Unit,
) {
    val channel =
        produceStateIfNotNull(accountViewModel.getEphemeralChatChannelIfExists(id), id) {
            value = accountViewModel.checkGetOrCreateEphemeralChatChannel(id)
        }

    channel.value?.let { content(it) }
}

@Composable
fun LoadLiveActivityChannel(
    id: Address,
    accountViewModel: AccountViewModel,
    content: @Composable (LiveActivitiesChannel) -> Unit,
) {
    val channel =
        produceStateIfNotNull(accountViewModel.getLiveActivityChannelIfExists(id), id) {
            value = accountViewModel.checkGetOrCreateLiveActivityChannel(id)
        }

    channel.value?.let { content(it) }
}

@Composable
fun <T> produceStateIfNotNull(
    initialValue: T,
    key1: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = remember(key1) { mutableStateOf(initialValue) }
    if (result.value == null) {
        LaunchedEffect(key1) { ProduceStateScopeImpl(result, coroutineContext).producer() }
    }
    return result
}

class ProduceStateScopeImpl<T>(
    state: MutableState<T>,
    override val coroutineContext: CoroutineContext,
) : ProduceStateScope<T>,
    MutableState<T> by state {
    override suspend fun awaitDispose(onDispose: () -> Unit): Nothing {
        try {
            suspendCancellableCoroutine<Nothing> {}
        } finally {
            onDispose()
        }
    }
}
