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
package com.vitorpamplona.amethyst.desktop.ui.chats.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import com.vitorpamplona.amethyst.desktop.ui.deck.LocalRelayManager
import com.vitorpamplona.amethyst.desktop.ui.media.AnimatedGifImage
import com.vitorpamplona.amethyst.desktop.ui.media.isAnimatedGifUrl
import kotlinx.coroutines.delay

/**
 * Nostr-native GIF search picker for the DM composer. Queries the configured
 * NIP-94 GIF relays (default GIF Buddy) via the app's own relay client and
 * inserts the chosen GIF's hosted URL into the message. No third-party GIF API.
 */
@Composable
fun GifPickerPanel(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val relayManager = LocalRelayManager.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GifResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    // Debounced search: re-run ~350ms after the query settles. A blank query
    // loads the newest GIFs from the relays.
    LaunchedEffect(query, relayManager) {
        val client = relayManager?.client
        if (client == null) {
            results = emptyList()
            return@LaunchedEffect
        }
        loading = true
        delay(350)
        results = searchGifs(client, DesktopPreferences.gifRelays, query)
        loading = false
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        modifier = modifier.size(width = 340.dp, height = 380.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search GIFs…") },
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            Box(modifier = Modifier.fillMaxSize().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    results.isEmpty() ->
                        Text(
                            "No GIFs found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else ->
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 96.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(results, key = { it.url }) { gif ->
                                val cellModifier =
                                    Modifier
                                        .padding(2.dp)
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { onPick(gif.url) }
                                // Animate GIFs (the whole grid is GIFs); fall back
                                // to a static frame for any non-.gif URL.
                                if (isAnimatedGifUrl(gif.url)) {
                                    AnimatedGifImage(
                                        url = gif.url,
                                        contentDescription = gif.description,
                                        modifier = cellModifier,
                                    )
                                } else {
                                    AsyncImage(
                                        model = gif.url,
                                        contentDescription = gif.description,
                                        modifier = cellModifier,
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}
