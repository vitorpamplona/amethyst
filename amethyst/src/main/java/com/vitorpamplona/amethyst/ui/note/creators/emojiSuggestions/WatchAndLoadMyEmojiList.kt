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
package com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEventAndMap
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.addressables.taggedAddresses
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import kotlinx.collections.immutable.toImmutableList

@Composable
fun WatchAndLoadMyEmojiList(accountViewModel: AccountViewModel) {
    LoadAddressableNote(
        EmojiPackSelectionEvent.createAddress(accountViewModel.userProfile().pubkeyHex),
        accountViewModel,
    ) { emptyNote ->
        emptyNote?.let { usersEmojiList ->
            val collections by observeNoteEventAndMap(usersEmojiList) { event: EmojiPackSelectionEvent ->
                event.taggedAddresses().toImmutableList()
            }

            collections?.forEach { address ->
                LoadAddressableNote(address, accountViewModel) { note ->
                    if (note != null) {
                        EventFinderFilterAssemblerSubscription(note)
                    }
                }
            }
        }
    }
}
