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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AddMemberScreen(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var searchInput by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isAdding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val userSuggestions =
        remember {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }

    DisposableEffect(Unit) {
        onDispose { userSuggestions.reset() }
    }

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = com.vitorpamplona.amethyst.R.string.add,
                onCancel = { nav.popBack() },
                onPost = {
                    val pubkey = selectedUser?.pubkeyHex
                    if (pubkey == null) {
                        statusMessage = "Error: No user selected"
                        return@ActionTopBar
                    }
                    isAdding = true
                    statusMessage = "Fetching KeyPackage..."
                    scope.launch(Dispatchers.IO) {
                        try {
                            val result =
                                accountViewModel.addMarmotGroupMember(nostrGroupId, pubkey)
                            statusMessage = result
                            if (result.startsWith("Success")) {
                                nav.popBack()
                            } else {
                                isAdding = false
                            }
                        } catch (e: Exception) {
                            statusMessage = "Error: ${e.message}"
                            isAdding = false
                        }
                    }
                },
                isActive = { !isAdding && selectedUser != null },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
        ) {
            // Selected user display
            if (selectedUser != null) {
                SelectedUserRow(
                    user = selectedUser!!,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onClear = {
                        selectedUser = null
                        statusMessage = null
                    },
                )
            }

            // Search field
            OutlinedTextField(
                value = searchInput,
                onValueChange = { newValue ->
                    searchInput = newValue
                    statusMessage = null
                    if (newValue.length > 2) {
                        userSuggestions.processCurrentWord(newValue)
                    } else {
                        userSuggestions.reset()
                    }
                },
                label = { Text("Search users") },
                placeholder = { Text("Name, npub, or NIP-05") },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                singleLine = true,
                enabled = selectedUser == null && !isAdding,
            )

            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (statusMessage!!.startsWith("Error")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // User suggestion list
            if (selectedUser == null && searchInput.length > 2) {
                ShowUserSuggestionList(
                    userSuggestions = userSuggestions,
                    onSelect = { user ->
                        selectedUser = user
                        searchInput = ""
                        userSuggestions.reset()
                    },
                    accountViewModel = accountViewModel,
                    modifier = SuggestionListDefaultHeightPage,
                    onEmpty = {
                        Text(
                            "They must have published a KeyPackage (kind:30443) to be added.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectedUserRow(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserPicture(
            userHex = user.pubkeyHex,
            size = 40.dp,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        ) {
            Text(
                text = user.toBestDisplayName(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${user.pubkeyHex.take(16)}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.TextButton(onClick = onClear) {
            Text("Clear")
        }
    }
}
