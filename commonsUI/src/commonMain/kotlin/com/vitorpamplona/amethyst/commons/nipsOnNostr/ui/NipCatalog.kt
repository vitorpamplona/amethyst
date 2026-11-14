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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipCatalogFilter
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipCatalogViewModel
import com.vitorpamplona.amethyst.commons.nipsOnNostr.OfficialNipEntry
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_all
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_empty
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_official
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_official_failed
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_on_nostr
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_search
import com.vitorpamplona.amethyst.commons.resources.nip_number
import com.vitorpamplona.amethyst.commons.resources.nip_unrecommended
import com.vitorpamplona.amethyst.commons.resources.try_again
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The NIPs tab: one searchable list of every NIP, the ones published on Nostr (drawn as their
 * feed cards, with author and reactions) and the official ones from the nips repo (a line each,
 * opening the in-app reader). The search matches a number (`44`, `nip-7d`), words of the title,
 * or a kind a NIP defines.
 */
@Composable
fun NipCatalog(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel = rememberViewModel(key = "NipCatalog") { NipCatalogViewModel(accountViewModel.account, accountViewModel.httpClientBuilder) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    // Fills the page: the pager centers a page vertically, so a short result list would float.
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = rememberFeedContentPadding(FeedPadding)) {
        item(key = "controls") {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringRes(Res.string.nip_catalog_search)) },
                    leadingIcon = { Icon(MaterialSymbols.Search, contentDescription = null) },
                )
                val options = NipCatalogFilter.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = filter == option,
                            onClick = { viewModel.filter.value = option },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) {
                            Text(
                                stringRes(
                                    when (option) {
                                        NipCatalogFilter.ALL -> Res.string.nip_catalog_all
                                        NipCatalogFilter.OFFICIAL -> Res.string.nip_catalog_official
                                        NipCatalogFilter.ON_NOSTR -> Res.string.nip_catalog_on_nostr
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }

        // While searching, a section with no match is left out instead of drawn as an empty heading.
        val searching = query.isNotBlank()
        if (filter != NipCatalogFilter.OFFICIAL && !(searching && state.onNostr.isEmpty() && !state.loadingOnNostr)) {
            item(key = "nostr-title") { CatalogSection(stringRes(Res.string.nip_catalog_on_nostr), state.onNostr.size, state.loadingOnNostr) }
            items(state.onNostr, key = { "nostr-" + it.idHex }) { note ->
                NoteCompose(baseNote = note, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        if (filter != NipCatalogFilter.ON_NOSTR && !(searching && state.official.isEmpty() && !state.loadingOfficial)) {
            item(key = "official-title") { CatalogSection(stringRes(Res.string.nip_catalog_official), state.official.size, state.loadingOfficial) }
            if (state.officialFailed && state.official.isEmpty()) {
                item(key = "official-failed") {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringRes(Res.string.nip_catalog_official_failed), color = MaterialTheme.colorScheme.grayText, modifier = Modifier.weight(1f))
                        Text(
                            stringRes(Res.string.try_again),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.refresh() }.padding(8.dp),
                        )
                    }
                }
            }
            items(state.official, key = { "official-" + it.number }) { entry ->
                OfficialNipRow(entry) { nav.nav(Route.Nip(entry.number)) }
            }
        }

        if (state.official.isEmpty() && state.onNostr.isEmpty() && !state.loadingOfficial && !state.loadingOnNostr && query.isNotBlank()) {
            item(key = "empty") {
                Text(
                    stringRes(Res.string.nip_catalog_empty),
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CatalogSection(
    title: String,
    count: Int,
    loading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count > 0) {
            Text(" · $count", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.grayText)
        }
        Spacer(Modifier.weight(1f))
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/** `NIP-44  Encrypted Payloads (Versioned)`, struck through with its reason when unrecommended. */
@Composable
private fun OfficialNipRow(
    entry: OfficialNipEntry,
    onClick: () -> Unit,
) {
    val unrecommended = entry.unrecommended != null
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NipPill(
            label = stringRes(Res.string.nip_number, entry.number),
            container = if (unrecommended) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
            content = if (unrecommended) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
            bold = true,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (unrecommended) TextDecoration.LineThrough else null,
                color = if (unrecommended) MaterialTheme.colorScheme.grayText else MaterialTheme.colorScheme.onSurface,
            )
            if (unrecommended) {
                val reason = entry.unrecommended?.ifBlank { null }
                Text(
                    stringRes(Res.string.nip_unrecommended) + (reason?.let { ": $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.grayText)
    }
}
