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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.SubPurposeLabels
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Explains why the app is holding the number of subscriptions it currently holds.
 *
 * Pivoted on purpose rather than relay, because the relay-shaped view hides exactly the thing worth
 * finding: a probe holding hundreds of filters across hundreds of relays looks like one ordinary
 * entry repeated on every row, while here it is a single line that dwarfs everything under it.
 *
 * Account is the outer grouping — several are normally logged in, they do not share relay sets, and
 * a mixed total cannot be acted on.
 */
@Composable
fun ActiveSubscriptionsScreen(viewModel: ActiveSubscriptionsViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.startPolling() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    countsLine(state.totalFilters, state.totalRelays),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state.untaggedFilters > 0) {
                    // Stated rather than hidden: an untagged filter is a subscription this screen
                    // cannot explain, and pretending the total is fully attributed would be a lie.
                    Text(
                        pluralStringResource(R.plurals.active_subs_untagged, state.untaggedFilters, state.untaggedFilters),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }

        items(state.accounts, key = { it.accountPubKey ?: "unattributed" }) { account ->
            AccountSection(account)
            HorizontalDivider()
        }
    }
}

@Composable
private fun AccountSection(account: SubscriptionAccountRow) {
    val name = account.accountPubKey?.let { displayNameOf(it) } ?: stringRes(R.string.active_subs_unattributed)

    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        account.purposes.forEach { PurposeSection(it) }
    }
}

@Composable
private fun PurposeSection(purposeRow: SubscriptionPurposeRow) {
    var expanded by rememberSaveable(purposeRow.purpose) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringRes(SubPurposeLabels.labelOf(purposeRow.purpose)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = countsLine(purposeRow.filterCount, purposeRow.relays.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            SubPurposeLabels.explainerOf(purposeRow.purpose)?.let {
                Text(
                    text = stringRes(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
            purposeRow.entities.forEach { entity ->
                val label =
                    entity.entityId?.let { displayNameOf(it) }
                        ?: entity.detail
                        ?: stringRes(R.string.active_subs_no_entity)
                Text(
                    text = stringRes(R.string.active_subs_pair, label, pluralStringResource(R.plurals.active_subs_relays, entity.relays.size, entity.relays.size)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                )
            }
        }
    }
}

/** "N filters · M relays", each noun pluralised on its own count. */
@Composable
private fun countsLine(
    filters: Int,
    relays: Int,
): String =
    stringRes(
        R.string.active_subs_pair,
        pluralStringResource(R.plurals.active_subs_filters, filters, filters),
        pluralStringResource(R.plurals.active_subs_relays, relays, relays),
    )

/**
 * Resolves an id to whatever name is loaded right now, falling back to a short id.
 *
 * Deliberately at render time rather than baked into the filter: names arrive after the subscription
 * that needed them, so a name captured at filter-construction would usually be missing and would go
 * stale when the user renames.
 */
@Composable
private fun displayNameOf(id: HexKey): String {
    val cached =
        remember(id) {
            LocalCache.getUserIfExists(id)?.toBestDisplayName()
                ?: LocalCache.getNoteIfExists(id)?.event?.let { LocalCache.getUserIfExists(it.pubKey)?.toBestDisplayName() }
        }
    return cached ?: id.take(8)
}
