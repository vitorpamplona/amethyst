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
package com.vitorpamplona.amethyst.desktop.followpacks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.followpacks.FollowPackEditor
import com.vitorpamplona.amethyst.desktop.followpacks.FollowPacksState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent

/**
 * Search-all view for follow packs.
 * Search matches across: title, description, creator name/npub, `t` tag.
 */
@Composable
fun FollowPackBrowseAllScreen(
    state: FollowPacksState,
    cache: DesktopLocalCache,
    onOpenPack: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val packs by state.allPacks.collectAsState()

    @Suppress("UNUSED_VARIABLE")
    val metadataVersion by cache.metadataVersion.collectAsState()

    val results =
        remember(packs, query, metadataVersion) {
            if (query.isBlank()) packs else filter(packs, query.trim(), cache)
        }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        MaterialSymbols.AutoMirrored.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.padding(4.dp))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search packs by title, creator, or tag…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "${results.size} ${if (results.size == 1) "pack" else "packs"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text =
                        if (query.isBlank()) {
                            "No packs in cache yet. They'll appear as relays send them."
                        } else {
                            "No packs match \"$query\""
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = results, key = { FollowPackEditor.aTag(it) }) { pack ->
                    FollowPackRow(
                        pack = pack,
                        cache = cache,
                        onClick = { onOpenPack(FollowPackEditor.aTag(pack)) },
                    )
                }
            }
        }
    }
}

private fun filter(
    packs: List<FollowListEvent>,
    raw: String,
    cache: DesktopLocalCache,
): List<FollowListEvent> {
    val q = raw.lowercase()
    val asHex: HexKey? = decodePublicKeyAsHexOrNull(raw)
    return packs.filter { pack ->
        val title = pack.title()?.lowercase().orEmpty()
        val desc = pack.description()?.lowercase().orEmpty()
        val tags = pack.hashtags().map { it.lowercase() }
        val creator = cache.getUserIfExists(pack.pubKey)
        val creatorName = creator?.toBestDisplayName()?.lowercase().orEmpty()
        val creatorNpub = creator?.pubkeyNpub()?.lowercase().orEmpty()

        title.contains(q) ||
            desc.contains(q) ||
            tags.any { it.contains(q) } ||
            creatorName.contains(q) ||
            (q.length >= 4 && creatorNpub.startsWith(q)) ||
            (asHex != null && pack.pubKey.equals(asHex, ignoreCase = true))
    }
}
