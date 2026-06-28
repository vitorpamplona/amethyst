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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.authoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.grayText

/**
 * Editor for a Podcasting-2.0 value-for-value split: a card listing each recipient (name, lnaddress
 * vs node toggle, address, weight, optional fee) plus an "Add recipient" action. Each recipient's
 * share of incoming sats is shown live as a percentage of the total weight. Drives a
 * [V4VSplitEditorState]; the owning composer reads [V4VSplitEditorState.toPodcastValue] on save.
 */
@Composable
fun V4VSplitEditor(state: V4VSplitEditorState) {
    val total = state.totalSplit()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringRes(R.string.podcast_value_for_value),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (state.recipients.isEmpty()) {
            Text(
                text = stringRes(R.string.podcast_value_editor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        state.recipients.forEach { draft ->
            RecipientCard(
                draft = draft,
                total = total,
                onRemove = { state.remove(draft) },
            )
        }

        TextButton(onClick = { state.add() }, modifier = Modifier.fillMaxWidth()) {
            Icon(symbol = MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringRes(R.string.podcast_value_add_recipient), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun RecipientCard(
    draft: RecipientDraft,
    total: Int,
    onRemove: () -> Unit,
) {
    val isNode by draft.isNode
    val weight =
        draft.split.value
            .trim()
            .toIntOrNull() ?: 0
    val percent = if (total > 0 && weight > 0) weight * 100 / total else 0

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringRes(R.string.podcast_value_split_percent, percent),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(
                    symbol = MaterialSymbols.Delete,
                    contentDescription = stringRes(R.string.podcast_value_remove_recipient),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        OutlinedTextField(
            value = draft.name.value,
            onValueChange = { draft.name.value = it },
            label = { Text(stringRes(R.string.podcast_value_recipient_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isNode,
                onClick = { draft.isNode.value = false },
                label = { Text(stringRes(R.string.podcast_value_type_lnaddress)) },
            )
            FilterChip(
                selected = isNode,
                onClick = { draft.isNode.value = true },
                label = { Text(stringRes(R.string.podcast_value_type_node)) },
            )
        }

        OutlinedTextField(
            value = draft.address.value,
            onValueChange = { draft.address.value = it },
            label = {
                Text(
                    if (isNode) {
                        stringRes(R.string.podcast_value_node_pubkey)
                    } else {
                        stringRes(R.string.podcast_value_lnaddress)
                    },
                )
            },
            placeholder = {
                Text(if (isNode) stringRes(R.string.podcast_value_node_pubkey_hint) else stringRes(R.string.podcast_value_lnaddress_hint))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = draft.address.value.isBlank(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.split.value,
                onValueChange = { input -> draft.split.value = input.filter { it.isDigit() } },
                label = { Text(stringRes(R.string.podcast_value_weight)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = weight <= 0,
            )
            FilterChip(
                selected = draft.fee.value,
                onClick = { draft.fee.value = !draft.fee.value },
                label = { Text(stringRes(R.string.podcast_value_fee)) },
            )
        }
    }
}
