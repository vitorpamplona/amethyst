/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fonfon.kgeohash.toGeoHash
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.CachedGeoLocations
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.screen.equalImmutableLists
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.encoders.ATag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    val decryptedContent by
        produceState(initialValue = accountViewModel.cachedDecrypt(note), key1 = note.event?.id()) {
            accountViewModel.decrypt(note) { value = it }
        }

    inner(decryptedContent)
}

@Composable
fun LoadAddressableNote(
    aTagHex: String,
    accountViewModel: AccountViewModel,
    content: @Composable (AddressableNote?) -> Unit,
) {
    var note by
        remember(aTagHex) {
            mutableStateOf<AddressableNote?>(accountViewModel.getAddressableNoteIfExists(aTagHex))
        }

    if (note == null) {
        LaunchedEffect(key1 = aTagHex) {
            accountViewModel.checkGetOrCreateAddressableNote(aTagHex) { newNote ->
                if (newNote != note) {
                    note = newNote
                }
            }
        }
    }

    content(note)
}

@Composable
fun LoadAddressableNote(
    aTag: ATag,
    accountViewModel: AccountViewModel,
    content: @Composable (AddressableNote?) -> Unit,
) {
    var note by
        remember(aTag) {
            mutableStateOf<AddressableNote?>(accountViewModel.getAddressableNoteIfExists(aTag.toTag()))
        }

    if (note == null) {
        LaunchedEffect(key1 = aTag) {
            accountViewModel.getOrCreateAddressableNote(aTag) { newNote ->
                if (newNote != note) {
                    note = newNote
                }
            }
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
    var statuses: ImmutableList<AddressableNote> by remember { mutableStateOf(persistentListOf()) }

    val userStatus by user.live().statuses.observeAsState()

    LaunchedEffect(key1 = userStatus) {
        accountViewModel.findStatusesForUser(userStatus?.user ?: user) { newStatuses ->
            if (!equalImmutableLists(statuses, newStatuses)) {
                statuses = newStatuses
            }
        }
    }

    content(statuses)
}

@Composable
fun LoadOts(
    note: Note,
    accountViewModel: AccountViewModel,
    whenConfirmed: @Composable (Long) -> Unit,
    whenPending: @Composable () -> Unit,
) {
    var earliestDate: GenericLoadable<Long> by remember { mutableStateOf(GenericLoadable.Loading()) }

    val noteStatus by note.live().innerOts.observeAsState()

    LaunchedEffect(key1 = noteStatus) {
        accountViewModel.findOtsEventsForNote(noteStatus?.note ?: note) { newOts ->
            earliestDate =
                if (newOts == null) {
                    GenericLoadable.Empty()
                } else {
                    GenericLoadable.Loaded(newOts)
                }
        }
    }

    (earliestDate as? GenericLoadable.Loaded)?.let {
        whenConfirmed(it.loaded)
    } ?: run {
        val account = accountViewModel.account.saveable.observeAsState()
        if (account.value?.account?.hasPendingAttestations(note) == true) {
            whenPending()
        }
    }
}

@Composable
fun LoadCityName(
    geohashStr: String,
    onLoading: (@Composable () -> Unit)? = null,
    content: @Composable (String) -> Unit,
) {
    var cityName by remember(geohashStr) { mutableStateOf(CachedGeoLocations.cached(geohashStr)) }

    if (cityName == null) {
        if (onLoading != null) {
            onLoading()
        }

        val context = LocalContext.current

        LaunchedEffect(key1 = geohashStr, context) {
            launch(Dispatchers.IO) {
                val geoHash = runCatching { geohashStr.toGeoHash() }.getOrNull()
                if (geoHash != null) {
                    val newCityName =
                        CachedGeoLocations.geoLocate(geohashStr, geoHash.toLocation(), context)
                            ?.ifBlank { null }
                    if (newCityName != null && newCityName != cityName) {
                        cityName = newCityName
                    }
                }
            }
        }
    } else {
        cityName?.let { content(it) }
    }
}

@Composable
fun LoadChannel(
    baseChannelHex: String,
    accountViewModel: AccountViewModel,
    content: @Composable (Channel) -> Unit,
) {
    var channel by
        remember(baseChannelHex) {
            mutableStateOf<Channel?>(accountViewModel.getChannelIfExists(baseChannelHex))
        }

    if (channel == null) {
        LaunchedEffect(key1 = baseChannelHex) {
            accountViewModel.checkGetOrCreateChannel(baseChannelHex) { newChannel ->
                launch(Dispatchers.Main) { channel = newChannel }
            }
        }
    }

    channel?.let { content(it) }
}
