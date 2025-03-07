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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.gallery.UrlImageView
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.quartz.nip01Core.tags.addressables.taggedAddresses
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow

@Composable
fun WatchAndLoadMyEmojiList(accountViewModel: AccountViewModel) {
    LoadAddressableNote(
        EmojiPackSelectionEvent.createAddress(accountViewModel.userProfile().pubkeyHex),
        accountViewModel,
    ) { emptyNote ->
        emptyNote?.let { usersEmojiList ->
            val collections by usersEmojiList
                .live()
                .metadata
                .map { (it.note.event as? EmojiPackSelectionEvent)?.taggedAddresses()?.toImmutableList() }
                .distinctUntilChanged()
                .observeAsState((usersEmojiList.event as? EmojiPackSelectionEvent)?.taggedAddresses()?.toImmutableList())

            collections?.forEach {
                LoadAddressableNote(it, accountViewModel) {
                    it?.live()?.metadata?.observeAsState()
                }
            }
        }
    }
}

@Composable
fun ShowEmojiSuggestionList(
    emojiSuggestions: Flow<List<Account.EmojiMedia>>,
    onSelect: (Account.EmojiMedia) -> Unit,
    onFullSize: (Account.EmojiMedia) -> Unit,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier.heightIn(0.dp, 200.dp),
) {
    val suggestions by emojiSuggestions.collectAsStateWithLifecycle(emptyList())

    if (suggestions.isNotEmpty()) {
        LazyColumn(
            contentPadding = PaddingValues(top = 10.dp),
            modifier = modifier,
        ) {
            items(suggestions) {
                Row(
                    modifier =
                        Modifier.clickable { onSelect(it) }.padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 10.dp,
                            bottom = 10.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = spacedBy(Size10dp),
                ) {
                    Box(Modifier.size(40.dp)) {
                        UrlImageView(it.url, accountViewModel)
                    }
                    Text(it.code, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = {
                                onFullSize(it)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInFull,
                                contentDescription = stringRes(R.string.use_direct_url),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }
        }
    }
}
