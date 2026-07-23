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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A reusable "add a person" dialog: a typeahead over the local user cache (name / NIP-05 / npub
 * prefix, or a pasted npub/hex). Tapping a result that isn't already in the target invokes [onAdd];
 * members already present are shown with an "Added" hint and aren't tappable.
 *
 * Context-agnostic — the caller supplies [isAlreadyIn] (membership predicate) and [onAdd] (the
 * actual add, e.g. a channel kind-9000 put-user or a community kind-9030 admin-add). Used by both
 * the channel members screen and the Buzz community view.
 */
@Composable
fun BuzzAddPeopleDialog(
    title: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    isAlreadyIn: (HexKey) -> Boolean,
    onAdd: (HexKey) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<HexKey>>(emptyList()) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        results =
            withContext(Dispatchers.IO) {
                LocalCache
                    .findUsersStartingWith(query.trim(), accountViewModel.account)
                    .map { it.pubkeyHex }
                    .take(15)
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(symbol = MaterialSymbols.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text(stringRes(R.string.buzz_dm_add_hint)) },
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    items(results, key = { it }) { hex ->
                        val alreadyIn = isAlreadyIn(hex)
                        AddPersonRow(hex, alreadyIn, accountViewModel, nav) {
                            if (!alreadyIn) {
                                onAdd(hex)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) } },
    )
}

@Composable
private fun AddPersonRow(
    hex: HexKey,
    alreadyIn: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: () -> Unit,
) {
    val user = remember(hex) { accountViewModel.checkGetOrCreateUser(hex) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !alreadyIn, onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserPicture(hex, Size35dp, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f)) {
            if (user != null) {
                UsernameDisplay(user, accountViewModel = accountViewModel)
            } else {
                Text(hex.take(8), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (alreadyIn) {
            Text(
                text = stringRes(R.string.buzz_import_added),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
