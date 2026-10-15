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
package com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.custom_emoji_picker_empty
import com.vitorpamplona.amethyst.commons.resources.custom_emoji_picker_no_match
import com.vitorpamplona.amethyst.commons.resources.custom_emoji_picker_search
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * A grid of the account's NIP-30 custom emoji (its emoji list and the packs it selected), with a
 * search field. The emoji picker (`EmojiPickerButton`) shows it as its custom tab.
 */
@Composable
fun CustomEmojiGrid(
    emojiPacks: EmojiPackState,
    modifier: Modifier = Modifier,
    onPick: (EmojiPackState.EmojiMedia) -> Unit,
) {
    val all by emojiPacks.myEmojis.collectAsStateWithLifecycle(emptyList())
    val search = rememberTextFieldState()
    val query =
        search.text
            .toString()
            .trim()
            .removePrefix(":")
            .removeSuffix(":")
    val shown = remember(all, query) { if (query.isEmpty()) all else all.filter { it.code.contains(query, ignoreCase = true) } }

    Column(modifier.padding(horizontal = 8.dp)) {
        if (all.isEmpty()) {
            Text(
                text = stringRes(Res.string.custom_emoji_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            return@Column
        }

        OutlinedTextField(
            state = search,
            placeholder = { Text(stringRes(Res.string.custom_emoji_picker_search)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )

        if (shown.isEmpty()) {
            Text(
                text = stringRes(Res.string.custom_emoji_picker_no_match),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            return@Column
        }

        // A fixed height: the menu scrolls its content, and a lazy grid needs a bounded one.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxWidth().height(260.dp),
        ) {
            items(shown, key = { it.link + it.code }) { emoji ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(emoji) }
                            .padding(6.dp),
                ) {
                    AsyncImage(model = emoji.link, contentDescription = emoji.code, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                }
            }
        }
    }
}
